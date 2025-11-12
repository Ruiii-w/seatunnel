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
package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

// by wjr 2025.10.23
// make CopyManagerProxy public
public class CopyManagerProxy {
    private static final Logger LOG = LoggerFactory.getLogger(CopyManagerProxy.class);
    Object connection;
    Object copyManager;
    Class<?> connectionClazz;
    Class<?> copyManagerClazz;
    Method getCopyAPIMethod;
    Method copyInMethod;

    // created by wjr 2025.10.23
    Method copyOutWriterMethod;
    Method copyOutOutputStreamMethod;

    public CopyManagerProxy(Connection connection)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
                    SQLException {
        LOG.info("Proxy connection class: {}", connection.getClass().getName());
        this.connection = connection.unwrap(Connection.class);
        LOG.info("Proxy unwrap connection class: {}", this.connection.getClass().getName());
        if (Proxy.isProxyClass(this.connection.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(this.connection);
            this.connection = getConnectionFromInvocationHandler(handler);
            if (null == this.connection) {
                throw new InvocationTargetException(
                        new NullPointerException("Proxy Connection is null."));
            }
            LOG.info("Proxy connection class: {}", this.connection.getClass().getName());
            this.connectionClazz = this.connection.getClass();
        } else {
            this.connectionClazz = this.connection.getClass();
        }
        this.getCopyAPIMethod = this.connectionClazz.getMethod("getCopyAPI");
        this.copyManager = this.getCopyAPIMethod.invoke(this.connection);
        this.copyManagerClazz = this.copyManager.getClass();
        this.copyInMethod = this.copyManagerClazz.getMethod("copyIn", String.class, Reader.class);

        // by wjr 2025.10.23
        try {
            this.copyOutWriterMethod =
                    this.copyManagerClazz.getMethod("copyOut", String.class, Writer.class);
        } catch (NoSuchMethodException e) {
            LOG.info("copyOut(String, Writer) not found, will try OutputStream");
        }
        try {
            this.copyOutOutputStreamMethod =
                    this.copyManagerClazz.getMethod("copyOut", String.class, OutputStream.class);
        } catch (NoSuchMethodException e) {
            LOG.info("copyOut(String, OutputStream) not found");
        }
    }

    long doCopy(String sql, Reader reader)
            throws InvocationTargetException, IllegalAccessException {
        return (long) this.copyInMethod.invoke(this.copyManager, sql, reader);
    }

    // created by wjr 2025.10.30 - 添加流式 copyOut 方法
    public void copyOut(String sql, OutputStream outputStream)
            throws InvocationTargetException, IllegalAccessException {
        if (this.copyOutOutputStreamMethod != null) {
            this.copyOutOutputStreamMethod.invoke(this.copyManager, sql, outputStream);
        } else {
            throw new InvocationTargetException(
                    new NoSuchMethodException(
                            "copyOut(String, OutputStream) method not found on CopyManager"));
        }
    }

    // created by wjr 2025.10.28
    // 以字节数组形式获取 COPY OUT 内容（用于 Binary 或需要原始字节的场景）
    public byte[] copyOutAsBytes(String sql)
            throws InvocationTargetException, IllegalAccessException, java.io.IOException {
        if (this.copyOutOutputStreamMethod != null) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            this.copyOutOutputStreamMethod.invoke(this.copyManager, sql, baos);
            baos.flush();
            return baos.toByteArray();
        } else if (this.copyOutWriterMethod != null) {
            java.io.StringWriter writer = new java.io.StringWriter();
            this.copyOutWriterMethod.invoke(this.copyManager, sql, writer);
            writer.flush();
            return writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            throw new InvocationTargetException(
                    new NoSuchMethodException("No copyOut method found on CopyManager"));
        }
    }

    // created by wjr 2025.10.23
    public String copyOutAsString(String sql)
            throws InvocationTargetException, IllegalAccessException, java.io.IOException {
        if (this.copyOutWriterMethod != null) {
            StringWriter writer = new StringWriter();
            this.copyOutWriterMethod.invoke(this.copyManager, sql, writer);
            writer.flush();
            return writer.toString();
        } else if (this.copyOutOutputStreamMethod != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            this.copyOutOutputStreamMethod.invoke(this.copyManager, sql, baos);
            baos.flush();
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } else {
            throw new InvocationTargetException(
                    new NoSuchMethodException("No copyOut method found on CopyManager"));
        }
    }

    // created by wjr 2025.11.03 - 为 COPY OUT 提供 InputStream（首选 PGCopyInputStream，回退为内存流）
    public InputStream copyOutAsStream(String sql)
            throws InvocationTargetException, IllegalAccessException, IOException {
        try {
            // 优先尝试使用 PG JDBC 的 PGCopyInputStream（真正流式）
            Class<?> pgConnInterface = Class.forName("org.postgresql.PGConnection");
            Class<?> pgCopyInputStreamClazz =
                    Class.forName("org.postgresql.copy.PGCopyInputStream");

            Object pgConn;
            if (pgConnInterface.isInstance(this.connection)) {
                pgConn = this.connection;
            } else {
                // 某些驱动实现可能是 BaseConnection，依旧尝试直接传入
                pgConn = this.connection;
            }

            try {
                java.lang.reflect.Constructor<?> ctor =
                        pgCopyInputStreamClazz.getConstructor(pgConnInterface, String.class);
                return (InputStream) ctor.newInstance(pgConn, sql);
            } catch (NoSuchMethodException e) {
                // 兼容不同驱动实现（比如 BaseConnection）
                java.lang.reflect.Constructor<?> ctor =
                        pgCopyInputStreamClazz.getConstructor(this.connectionClazz, String.class);
                return (InputStream) ctor.newInstance(pgConn, sql);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException e) {
            // PGCopyInputStream 不可用时，回退到一次性拷贝并返回内存流（不具备真正流式特性）
            if (this.copyOutOutputStreamMethod != null) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                this.copyOutOutputStreamMethod.invoke(this.copyManager, sql, baos);
                baos.flush();
                return new java.io.ByteArrayInputStream(baos.toByteArray());
            } else if (this.copyOutWriterMethod != null) {
                java.io.StringWriter writer = new java.io.StringWriter();
                this.copyOutWriterMethod.invoke(this.copyManager, sql, writer);
                writer.flush();
                byte[] bytes = writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new java.io.ByteArrayInputStream(bytes);
            } else {
                throw new InvocationTargetException(
                        new NoSuchMethodException("No copyOut method found on CopyManager"),
                        e.getMessage());
            }
        }
    }

    private static Object getConnectionFromInvocationHandler(InvocationHandler handler)
            throws IllegalAccessException {
        Class<?> handlerClass = handler.getClass();
        LOG.info("InvocationHandler class: {}", handlerClass.getName());
        for (Field declaredField : handlerClass.getDeclaredFields()) {
            boolean tempAccessible = declaredField.isAccessible();
            if (!tempAccessible) {
                declaredField.setAccessible(true);
            }
            Object handlerObject = declaredField.get(handler);
            if (handlerObject instanceof Connection) {
                if (!tempAccessible) {
                    declaredField.setAccessible(tempAccessible);
                }
                return handlerObject;
            } else {
                if (!tempAccessible) {
                    declaredField.setAccessible(tempAccessible);
                }
            }
        }
        return null;
    }
}
