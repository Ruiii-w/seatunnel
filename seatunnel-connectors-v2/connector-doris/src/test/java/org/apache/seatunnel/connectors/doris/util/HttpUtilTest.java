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

import org.apache.http.impl.client.CloseableHttpClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Unit tests for shared HttpClient pool (NEW-1 fix). */
public class HttpUtilTest {

    @AfterEach
    public void tearDown() {
        HttpUtil.closeSharedHttpClient();
    }

    @Test
    public void testSingletonSameInstance() {
        CloseableHttpClient c1 = HttpUtil.getSharedHttpClient();
        CloseableHttpClient c2 = HttpUtil.getSharedHttpClient();
        Assertions.assertNotNull(c1);
        Assertions.assertSame(c1, c2, "Should return same singleton instance");
    }

    @Test
    public void testCloseDoesNotDestroyPool() throws Exception {
        CloseableHttpClient client = HttpUtil.getSharedHttpClient();
        client.close(); // Simulate Writer.close()
        CloseableHttpClient after = HttpUtil.getSharedHttpClient();
        Assertions.assertNotNull(after, "Pool should survive close()");
        Assertions.assertSame(client, after, "Should be same instance after no-op close()");
    }

    @Test
    public void testBulkCloseResilience() throws Exception {
        CloseableHttpClient client = HttpUtil.getSharedHttpClient();
        for (int i = 0; i < 100; i++) {
            client.close(); // 100 Writers closing
        }
        Assertions.assertNotNull(HttpUtil.getSharedHttpClient());
    }

    @Test
    public void testMultiThreadAccess() throws Exception {
        final CloseableHttpClient[] clients = new CloseableHttpClient[10];
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            threads[i] =
                    new Thread(
                            () -> {
                                clients[idx] = HttpUtil.getSharedHttpClient();
                                try {
                                    clients[idx].close();
                                } catch (Exception ignored) {
                                }
                            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        for (int i = 1; i < 10; i++) {
            Assertions.assertSame(clients[0], clients[i], "All threads must receive same instance");
        }
    }

    @Test
    public void testExplicitCloseThenRecreate() {
        HttpUtil.getSharedHttpClient();
        HttpUtil.closeSharedHttpClient();
        CloseableHttpClient newClient = HttpUtil.getSharedHttpClient();
        Assertions.assertNotNull(newClient, "Should create new client after explicit close");
    }

    @Test
    public void testDeprecatedMethodDelegates() {
        CloseableHttpClient fromDeprecated = new HttpUtil().getHttpClient();
        CloseableHttpClient fromShared = HttpUtil.getSharedHttpClient();
        Assertions.assertSame(fromShared, fromDeprecated);
    }

    @Test
    public void testLogStatsNoThrow() {
        Assertions.assertDoesNotThrow(HttpUtil::logConnectionPoolStats);
        HttpUtil.getSharedHttpClient();
        Assertions.assertDoesNotThrow(HttpUtil::logConnectionPoolStats);
    }
}
