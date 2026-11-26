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

package org.apache.seatunnel.connectors.seatunnel.jdbc.source;

import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcConnectionConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcSourceConfig;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FixedChunkSplitterTest {

    private JdbcSourceConfig config() {
        return JdbcSourceConfig.builder()
                .jdbcConnectionConfig(
                        JdbcConnectionConfig.builder()
                                .url("jdbc:postgresql://localhost:5432/test")
                                .driverName("org.postgresql.Driver")
                                .build())
                .build();
    }

    private TableSchema tableSchema() {
        return TableSchema.builder()
                .columns(
                        Arrays.asList(
                                PhysicalColumn.builder()
                                        .name("id")
                                        .sourceType("int4")
                                        .dataType(BasicType.INT_TYPE)
                                        .build()))
                .build();
    }

    @Test
    public void testLastSplitUsesInclusiveUpperBound() {
        FixedChunkSplitter splitter = new FixedChunkSplitter(config());
        JdbcSourceSplit split =
                new JdbcSourceSplit(
                        TablePath.of("db1", "schema1", "table1"),
                        "split-last",
                        null,
                        "id",
                        BasicType.INT_TYPE,
                        new BigDecimal("991"),
                        new BigDecimal("1000"));

        String splitQuerySQL = splitter.createSplitQuerySQL(split, tableSchema());

        assertEquals(
                "SELECT * FROM \"db1\".\"schema1\".\"table1\" WHERE \"id\" >= 991 AND \"id\" <= 1000",
                splitQuerySQL);
    }

    @Test
    public void testCustomQueryLastSplitUsesInclusiveUpperBound() {
        FixedChunkSplitter splitter = new FixedChunkSplitter(config());
        TableSchema schema =
                TableSchema.builder()
                        .columns(
                                Arrays.asList(
                                        PhysicalColumn.builder()
                                                .name("txn_seq_num")
                                                .sourceType("int8")
                                                .dataType(BasicType.LONG_TYPE)
                                                .build()))
                        .build();
        JdbcSourceSplit split =
                new JdbcSourceSplit(
                        TablePath.of("fxjsadb", "pub", "t03_crdt_txn_evt"),
                        "split-last",
                        "select * from pub.t03_crdt_txn_evt where etl_dt = '2026-07-15'",
                        "txn_seq_num",
                        BasicType.LONG_TYPE,
                        900001L,
                        1000000L);

        String splitQuerySQL = splitter.createSplitQuerySQL(split, schema);

        assertEquals(
                "SELECT * FROM (select * from pub.t03_crdt_txn_evt where etl_dt = '2026-07-15') "
                        + "st_jdbc_splitter WHERE \"txn_seq_num\" >= 900001 AND \"txn_seq_num\" <= 1000000",
                splitQuerySQL);
    }

    @Test
    public void testGeneratedLastSplitCoversMaxValue() throws Exception {
        FixedChunkSplitter splitter = new FixedChunkSplitter(config());
        JdbcSourceTable table =
                JdbcSourceTable.builder()
                        .tablePath(TablePath.of("db1", "schema1", "table1"))
                        .partitionColumn("id")
                        .partitionNumber(10)
                        .partitionStart(BigDecimal.ONE)
                        .partitionEnd(new BigDecimal("1000"))
                        .build();

        Collection<JdbcSourceSplit> splits =
                splitter.createSplits(
                        table,
                        new SeaTunnelRowType(
                                new String[] {"id"}, new SeaTunnelDataType[] {BasicType.INT_TYPE}));

        JdbcSourceSplit lastSplit =
                splits.stream()
                        .max(Comparator.comparing(split -> (BigDecimal) split.getSplitEnd()))
                        .get();

        assertEquals(new BigDecimal("1000"), lastSplit.getSplitEnd());
        String splitQuerySQL = splitter.createSplitQuerySQL(lastSplit, tableSchema());
        assertTrue(splitQuerySQL.contains("<= 1000"), splitQuerySQL);
    }
}
