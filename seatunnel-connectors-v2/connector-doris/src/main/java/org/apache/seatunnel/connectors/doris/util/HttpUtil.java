/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.doris.util;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.RequestContent;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** util to build http client. */
@Slf4j
public class HttpUtil {

    // ===== Global shared connection pool =====
    // Parameters are configurable via system properties for high-concurrency tuning.
    private static final int MAX_TOTAL_CONNECTIONS =
            Math.max(200, Integer.getInteger("doris.http.pool.maxTotal", 500));

    private static final int MAX_PER_ROUTE =
            Math.max(50, Integer.getInteger("doris.http.pool.maxPerRoute", 300));

    private static final int CONNECTION_TTL_SECONDS = 300;

    // Validate connections after 10s of inactivity to detect stale connections
    private static final int VALIDATE_AFTER_INACTIVITY_MS = 10000;

    private static volatile PoolingHttpClientConnectionManager SHARED_CONNECTION_MANAGER;
    private static volatile CloseableHttpClient SHARED_HTTP_CLIENT;

    private static final HttpClientBuilder HTTP_CLIENT_BUILDER =
            HttpClients.custom()
                    .setRedirectStrategy(
                            new DefaultRedirectStrategy() {
                                @Override
                                protected boolean isRedirectable(String method) {
                                    return true;
                                }
                            })
                    .addInterceptorLast(new RequestContent(true));

    static {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(HttpUtil::closeSharedHttpClient, "doris-http-pool-shutdown"));
    }

    /**
     * Get the global shared CloseableHttpClient. All Doris Sink Writers and Committers share a
     * single connection pool, avoiding connection pool fragmentation and reducing Netty DirectArena
     * instances.
     *
     * <p>The returned HttpClient is wrapped to prevent accidental pool destruction when individual
     * writers/committers are closed.
     */
    public static CloseableHttpClient getSharedHttpClient() {
        if (SHARED_HTTP_CLIENT == null) {
            synchronized (HttpUtil.class) {
                if (SHARED_HTTP_CLIENT == null) {
                    SHARED_CONNECTION_MANAGER =
                            new PoolingHttpClientConnectionManager(
                                    CONNECTION_TTL_SECONDS, TimeUnit.SECONDS);
                    SHARED_CONNECTION_MANAGER.setMaxTotal(MAX_TOTAL_CONNECTIONS);
                    SHARED_CONNECTION_MANAGER.setDefaultMaxPerRoute(MAX_PER_ROUTE);
                    SHARED_CONNECTION_MANAGER.setValidateAfterInactivity(
                            VALIDATE_AFTER_INACTIVITY_MS);

                    CloseableHttpClient actualClient =
                            HTTP_CLIENT_BUILDER
                                    .setConnectionManager(SHARED_CONNECTION_MANAGER)
                                    .setConnectionManagerShared(true)
                                    .build();

                    // Wrap with no-op close to prevent accidental pool destruction
                    SHARED_HTTP_CLIENT = new UncloseableHttpClient(actualClient);

                    log.info(
                            "Created shared Doris HTTP connection pool: maxTotal={}, maxPerRoute={}, connTTL={}s",
                            MAX_TOTAL_CONNECTIONS,
                            MAX_PER_ROUTE,
                            CONNECTION_TTL_SECONDS);
                }
            }
        }
        return SHARED_HTTP_CLIENT;
    }

    /** Log current connection pool statistics for monitoring. */
    public static void logConnectionPoolStats() {
        if (SHARED_CONNECTION_MANAGER != null) {
            org.apache.http.pool.PoolStats stats = SHARED_CONNECTION_MANAGER.getTotalStats();
            log.info(
                    "Doris HTTP Pool Stats: leased={}, pending={}, available={}, max={}",
                    stats.getLeased(),
                    stats.getPending(),
                    stats.getAvailable(),
                    stats.getMax());
        }
    }

    /** Close the shared connection pool. Called by JVM shutdown hook or framework lifecycle. */
    public static synchronized void closeSharedHttpClient() {
        if (SHARED_HTTP_CLIENT != null) {
            try {
                if (SHARED_HTTP_CLIENT instanceof UncloseableHttpClient) {
                    ((UncloseableHttpClient) SHARED_HTTP_CLIENT).closeRealClient();
                }
            } catch (IOException e) {
                log.warn("Failed to close shared Doris HTTP client", e);
            }
            SHARED_HTTP_CLIENT = null;
            SHARED_CONNECTION_MANAGER = null;
            log.info("Doris shared HTTP connection pool closed");
        }
    }

    /**
     * @deprecated Use {@link #getSharedHttpClient()} instead to avoid connection pool
     *     fragmentation.
     */
    @Deprecated
    public CloseableHttpClient getHttpClient() {
        return getSharedHttpClient();
    }

    /**
     * A CloseableHttpClient wrapper whose {@link #close()} is a no-op. This prevents individual
     * writers/committers from accidentally destroying the shared connection pool when they are
     * closed.
     */
    private static class UncloseableHttpClient extends CloseableHttpClient {
        private final CloseableHttpClient delegate;

        UncloseableHttpClient(CloseableHttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        protected CloseableHttpResponse doExecute(
                HttpHost target, HttpRequest request, HttpContext context) throws IOException {
            return delegate.execute(target, request, context);
        }

        @Override
        public void close() {
            // No-op: shared pool lifecycle is managed by HttpUtil
        }

        @Override
        @Deprecated
        public org.apache.http.conn.ClientConnectionManager getConnectionManager() {
            return delegate.getConnectionManager();
        }

        @Override
        @Deprecated
        public org.apache.http.params.HttpParams getParams() {
            return delegate.getParams();
        }

        void closeRealClient() throws IOException {
            delegate.close();
        }
    }
}
