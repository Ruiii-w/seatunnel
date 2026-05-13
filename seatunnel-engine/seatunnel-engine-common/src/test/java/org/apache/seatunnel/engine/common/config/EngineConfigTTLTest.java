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

package org.apache.seatunnel.engine.common.config;

import org.apache.seatunnel.engine.common.config.server.ServerConfigOptions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Functional tests for the finishedTaskContextTTLMinutes configuration.
 *
 * <p>Verifies:
 * <ol>
 *   <li>Default TTL value is correct (30 minutes)</li>
 *   <li>Setter works with valid values (including 0 to disable)</li>
 *   <li>Setter rejects negative values</li>
 *   <li>Config option is properly declared in ServerConfigOptions</li>
 * </ol>
 */
public class EngineConfigTTLTest {

    @Test
    public void testDefaultTtlValueIs30Minutes() {
        EngineConfig config = new EngineConfig();
        Assertions.assertEquals(30, config.getFinishedTaskContextTTLMinutes(),
                "Default TTL should be 30 minutes");
    }

    @Test
    public void testDefaultTtlValueMatchesConfigOption() {
        Assertions.assertEquals(30, ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES.defaultValue(),
                "ServerConfigOptions default should match EngineConfig default");
    }

    @Test
    public void testConfigOptionHasValidKey() {
        Assertions.assertEquals("finished-task-context-ttl-minutes",
                ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES.key(),
                "Config key should follow kebab-case convention");
    }

    @Test
    public void testConfigOptionHasDescription() {
        Assertions.assertNotNull(
                ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES.getDescription(),
                "Config option should have a description");
        Assertions.assertFalse(
                ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES.getDescription().isEmpty(),
                "Config option description should not be empty");
    }

    @Test
    public void testSetterWithPositiveValue() {
        EngineConfig config = new EngineConfig();
        config.setFinishedTaskContextTTLMinutes(60);
        Assertions.assertEquals(60, config.getFinishedTaskContextTTLMinutes(),
                "Should accept positive TTL value");
    }

    @Test
    public void testSetterWithZeroToDisable() {
        EngineConfig config = new EngineConfig();
        config.setFinishedTaskContextTTLMinutes(0);
        Assertions.assertEquals(0, config.getFinishedTaskContextTTLMinutes(),
                "Should accept 0 to disable TTL cleanup");
    }

    @Test
    public void testSetterRejectsNegativeValue() {
        EngineConfig config = new EngineConfig();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> config.setFinishedTaskContextTTLMinutes(-1),
                "Setter should reject negative values");
    }

    @Test
    public void testMultipleConfigInstancesAreIndependent() {
        EngineConfig config1 = new EngineConfig();
        EngineConfig config2 = new EngineConfig();

        config1.setFinishedTaskContextTTLMinutes(45);
        config2.setFinishedTaskContextTTLMinutes(90);

        Assertions.assertEquals(45, config1.getFinishedTaskContextTTLMinutes());
        Assertions.assertEquals(90, config2.getFinishedTaskContextTTLMinutes());
    }

    @Test
    public void testTtlConfigIsNotConfusedWithHistoryJobExpireMinutes() {
        EngineConfig config = new EngineConfig();
        // These are different config options
        Assertions.assertNotEquals(
                ServerConfigOptions.HISTORY_JOB_EXPIRE_MINUTES.key(),
                ServerConfigOptions.FINISHED_TASK_CONTEXT_TTL_MINUTES.key(),
                "TTL config key should differ from history job expire key");

        config.setHistoryJobExpireMinutes(100);
        config.setFinishedTaskContextTTLMinutes(200);

        Assertions.assertNotEquals(
                config.getHistoryJobExpireMinutes(),
                config.getFinishedTaskContextTTLMinutes(),
                "Two config options should be independently settable");
    }
}
