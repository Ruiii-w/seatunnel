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

package org.apache.seatunnel.engine.common.utils;

import org.apache.seatunnel.common.utils.SeaTunnelException;
import org.apache.seatunnel.engine.common.exception.SeaTunnelEngineException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.OperationTimeoutException;

import java.io.IOException;

/**
 * Functional tests for {@link ExceptionUtil#isOperationNeedRetryException(Throwable)}.
 *
 * <p>Verifies that the enhanced retry logic correctly handles SeaTunnelException and
 * SeaTunnelEngineException wrappers that were previously causing cleanup chain failures.
 */
public class ExceptionUtilRetryTest {

    // ==================== Directly retriable exceptions (no wrapper) ====================

    @Test
    public void testDirectOperationTimeoutExceptionShouldRetry() {
        boolean retry = ExceptionUtil.isOperationNeedRetryException(
                new OperationTimeoutException("op timed out"));
        Assertions.assertTrue(retry,
                "Direct OperationTimeoutException should be retried");
    }

    @Test
    public void testDirectHazelcastInstanceNotActiveExceptionShouldRetry() {
        boolean retry = ExceptionUtil.isOperationNeedRetryException(
                new HazelcastInstanceNotActiveException("instance not active"));
        Assertions.assertTrue(retry,
                "Direct HazelcastInstanceNotActiveException should be retried");
    }

    @Test
    public void testDirectInterruptedExceptionShouldRetry() {
        boolean retry = ExceptionUtil.isOperationNeedRetryException(
                new InterruptedException("interrupted"));
        Assertions.assertTrue(retry,
                "Direct InterruptedException should be retried");
    }

    // ==================== Non-retriable exceptions ====================

    @Test
    public void testIOExceptionShouldNotRetry() {
        boolean retry = ExceptionUtil.isOperationNeedRetryException(
                new IOException("io error"));
        Assertions.assertFalse(retry,
                "IOException should NOT be retried");
    }

    @Test
    public void testRuntimeExceptionShouldNotRetry() {
        boolean retry = ExceptionUtil.isOperationNeedRetryException(
                new RuntimeException("runtime error"));
        Assertions.assertFalse(retry,
                "RuntimeException should NOT be retried");
    }

    // ==================== SeaTunnelException wrapping retriable ====================

    @Test
    public void testSeaTunnelExceptionWrappingOperationTimeoutShouldRetry() {
        SeaTunnelException wrapper = new SeaTunnelException(
                new OperationTimeoutException("metrics timeout"));
        boolean retry = ExceptionUtil.isOperationNeedRetryException(wrapper);
        Assertions.assertTrue(retry,
                "SeaTunnelException wrapping OperationTimeoutException should be retried");
    }

    @Test
    public void testSeaTunnelEngineExceptionWrappingOperationTimeoutShouldRetry() {
        SeaTunnelEngineException wrapper = new SeaTunnelEngineException(
                new OperationTimeoutException("clean timeout"));
        boolean retry = ExceptionUtil.isOperationNeedRetryException(wrapper);
        Assertions.assertTrue(retry,
                "SeaTunnelEngineException wrapping OperationTimeoutException should be retried");
    }

    @Test
    public void testSeaTunnelExceptionWrappingHazelcastInstanceNotActiveShouldRetry() {
        SeaTunnelException wrapper = new SeaTunnelException(
                new HazelcastInstanceNotActiveException("not active"));
        boolean retry = ExceptionUtil.isOperationNeedRetryException(wrapper);
        Assertions.assertTrue(retry,
                "SeaTunnelException wrapping HazelcastInstanceNotActiveException should be retried");
    }

    @Test
    public void testSeaTunnelEngineExceptionWrappingInterruptedExceptionShouldRetry() {
        SeaTunnelEngineException wrapper = new SeaTunnelEngineException(
                new InterruptedException("interrupted"));
        boolean retry = ExceptionUtil.isOperationNeedRetryException(wrapper);
        Assertions.assertTrue(retry,
                "SeaTunnelEngineException wrapping InterruptedException should be retried");
    }

    // ==================== SeaTunnelException wrapping NON-retriable ====================

    @Test
    public void testSeaTunnelExceptionWrappingIOExceptionShouldNotRetry() {
        SeaTunnelException wrapper = new SeaTunnelException(
                new IOException("io error"));
        boolean retry = ExceptionUtil.isOperationNeedRetryException(wrapper);
        Assertions.assertFalse(retry,
                "SeaTunnelException wrapping IOException should NOT be retried");
    }

    @Test
    public void testSeaTunnelEngineExceptionWrappingRuntimeExceptionShouldNotRetry() {
        SeaTunnelEngineException wrapper = new SeaTunnelEngineException(
                new RuntimeException("runtime error"));
        boolean retry = ExceptionUtil.isOperationNeedRetryException(wrapper);
        Assertions.assertFalse(retry,
                "SeaTunnelEngineException wrapping RuntimeException should NOT be retried");
    }

    // ==================== SeaTunnelException WITHOUT cause ====================

    @Test
    public void testSeaTunnelExceptionMessageOnlyShouldNotRetry() {
        SeaTunnelEngineException messageOnly = new SeaTunnelEngineException("some error message");
        boolean retry = ExceptionUtil.isOperationNeedRetryException(messageOnly);
        Assertions.assertFalse(retry,
                "SeaTunnelEngineException with message only (no cause) should NOT be retried");
    }

    @Test
    public void testSeaTunnelExceptionMessageOnlyShouldNotRetry2() {
        SeaTunnelException messageOnly = new SeaTunnelException("some error message");
        boolean retry = ExceptionUtil.isOperationNeedRetryException(messageOnly);
        Assertions.assertFalse(retry,
                "SeaTunnelException with message only (no cause) should NOT be retried");
    }

    // ==================== Deeply nested exceptions ====================

    @Test
    public void testDeeplyNestedSeaTunnelExceptionWrappingRetriableShouldRetry() {
        RuntimeException outer = new RuntimeException(
                new SeaTunnelEngineException(
                        new OperationTimeoutException("deep timeout")));
        boolean retry = ExceptionUtil.isOperationNeedRetryException(outer);
        Assertions.assertTrue(retry,
                "Deeply nested: RuntimeException -> SeaTunnelEngineException -> "
                        + "OperationTimeoutException should be retried");
    }

    @Test
    public void testSeaTunnelExceptionChainMixedShouldRetry() {
        SeaTunnelException wrapper = new SeaTunnelException(
                new RuntimeException(
                        new OperationTimeoutException("timeout")));
        boolean retry = ExceptionUtil.isOperationNeedRetryException(wrapper);
        Assertions.assertTrue(retry,
                "SeaTunnelException -> RuntimeException -> OperationTimeoutException should be retried");
    }
}
