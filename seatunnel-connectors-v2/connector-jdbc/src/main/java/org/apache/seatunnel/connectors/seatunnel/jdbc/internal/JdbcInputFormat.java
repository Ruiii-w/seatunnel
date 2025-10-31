/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcSourceConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.JdbcConnectionProvider;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialect;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialectLoader;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.CopyManagerProxy;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.util.PerformanceLogger;
import org.apache.seatunnel.connectors.seatunnel.jdbc.source.ChunkSplitter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.source.JdbcSourceSplit;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * InputFormat to read data from a database and generate Rows. The InputFormat has to be configured
 * using the supplied InputFormatBuilder. A valid RowTypeInfo must be properly configured in the
 * builder
 */
public class JdbcInputFormat implements Serializable {

    private static final long serialVersionUID = 2L;
    private static final Logger LOG = LoggerFactory.getLogger(JdbcInputFormat.class);

    private final JdbcDialect jdbcDialect;
    private final JdbcRowConverter jdbcRowConverter;
    private final Map<TablePath, CatalogTable> tables;
    private final ChunkSplitter chunkSplitter;

    // created by wjr 2025.10.23
    private final JdbcSourceConfig config;
    private final boolean useCopyStatement;
    private final boolean useBinary;

    private transient String splitTableId;
    private transient TableSchema splitTableSchema;
    private transient PreparedStatement statement;
    private transient ResultSet resultSet;
    private volatile boolean hasNext;

    // created by wjr 2025.10.23
    private transient CopyManagerProxy copyManagerProxy;
    private transient CSVParser csvParser;
    private transient Iterator<CSVRecord> csvIterator;
    private transient Iterator<SeaTunnelRow> binaryIterator;

    // created by wjr 2025.10.30
    private transient ByteBuffer streamingBuffer; // Binary 拼接缓冲
    private transient boolean binaryHeaderParsed; // Binary 头是否解析
    private transient SeaTunnelDataType<?>[] binaryFieldTypes; // Binary 字段类型
    private transient Deque<SeaTunnelRow> streamQueue; // 通用行队列

    private transient PipedInputStream csvPipeIn; // CSV 流式
    private transient PipedOutputStream csvPipeOut;
    private transient BufferedReader csvReader;
    private transient Thread csvCopyThread;

    // created by wjr 2025.10.27
    private long rowsRead = 0L;

    public JdbcInputFormat(JdbcSourceConfig config, Map<TablePath, CatalogTable> tables) {
        this.jdbcDialect =
                JdbcDialectLoader.load(
                        config.getJdbcConnectionConfig().getUrl(), config.getCompatibleMode());
        this.chunkSplitter = ChunkSplitter.create(config);
        this.jdbcRowConverter = jdbcDialect.getRowConverter();
        this.tables = tables;

        // created by wjr 2025.10.28
        this.config = config;
        this.useCopyStatement = config.isUseCopyStatement();
        this.useBinary = config.isBinary();
    }

    public void openInputFormat() {}

    public void closeInputFormat() throws IOException {
        close();

        if (chunkSplitter != null) {
            chunkSplitter.close();
        }
    }

    /**
     * Connects to the source database and executes the query
     *
     * @param inputSplit which is ignored if this InputFormat is executed as a non-parallel source,
     *     a "hook" to the query parameters otherwise (using its <i>parameterId</i>)
     * @throws IOException if there's an error during the execution of the query
     */
    //    public void open(JdbcSourceSplit inputSplit) throws IOException {
    //        try {
    //            splitTableSchema = tables.get(inputSplit.getTablePath()).getTableSchema();
    //            splitTableId = inputSplit.getTablePath().toString();
    //            statement = chunkSplitter.generateSplitStatement(inputSplit, splitTableSchema);
    //            resultSet = statement.executeQuery();
    //            hasNext = resultSet.next();
    //        } catch (SQLException se) {
    //            throw new JdbcConnectorException(
    //                    JdbcConnectorErrorCode.CONNECT_DATABASE_FAILED,
    //                    "open() failed." + se.getMessage(),
    //                    se);
    //        }
    //    }

    // created by wjr 2025.10.23
    public void open(JdbcSourceSplit inputSplit) throws IOException {

        //        PerformanceLogger.Span openSpan =
        //                PerformanceLogger.span("open")
        //                        .field("table", inputSplit.getTablePath().getSchemaAndTableName())
        //                        .field("split_id", inputSplit.splitId())
        //                        .field("mode", useCopyStatement ? "copy" : "select");

        try {
            splitTableSchema = tables.get(inputSplit.getTablePath()).getTableSchema();
            splitTableId = inputSplit.getTablePath().toString();

            // PGCopySql
            if (useCopyStatement) {
                long sqlBuildMs;
                String selectSqlLocal;
                {
                    //                    PerformanceLogger.Span buildSQL =
                    //                            PerformanceLogger.span("build_sql")
                    //                                    .field(
                    //                                            "table",
                    //
                    // inputSplit.getTablePath().getSchemaAndTableName())
                    //                                    .field("split_id", inputSplit.splitId())
                    //                                    .field("mode", useCopyStatement ? "copy" :
                    // "select");

                    // SelectSql
                    selectSqlLocal =
                            chunkSplitter.generateSplitQuerySQL(inputSplit, splitTableSchema);

                    //                    sqlBuildMs = buildSQL.end(null, null, selectSqlLocal);
                    //                    openSpan.field("sql_build_ms", sqlBuildMs);
                }

                //                PerformanceLogger.Span execCopy =
                //                        PerformanceLogger.span("execute_copy")
                //                                .field("table",
                // inputSplit.getTablePath().getSchemaAndTableName())
                //                                .field("split_id", inputSplit.splitId());

                // CopySQL
                String copyOutSql =
                        String.format(
                                "COPY (%s) TO STDOUT WITH %s",
                                selectSqlLocal, useBinary ? "BINARY" : "CSV");

                // Jbdc Connection
                JdbcConnectionProvider provider =
                        jdbcDialect.getJdbcConnectionProvider(config.getJdbcConnectionConfig());
                Connection connection = provider.getOrEstablishConnection();

                long bytes = 0L;
                long parseMs;
                LOG.info(
                        "Open split id={} with COPY mode, selectSql={}, copyOutSql={}",
                        inputSplit.splitId(),
                        selectSqlLocal,
                        copyOutSql);

                // Run CopySQL
                this.copyManagerProxy = new CopyManagerProxy(connection);
                if (useBinary) {
                    byte[] binaryData = this.copyManagerProxy.copyOutAsBytes(copyOutSql);
                    bytes = binaryData == null ? 0 : binaryData.length;

                    //                    PerformanceLogger.Span parseBinary =
                    //                            PerformanceLogger.span("parse_binary")
                    //                                    .field(
                    //                                            "table",
                    //
                    // inputSplit.getTablePath().getSchemaAndTableName())
                    //                                    .field("split_id", inputSplit.splitId());

                    java.util.List<SeaTunnelRow> rows =
                            parseBinaryRows(binaryData, splitTableSchema);
                    this.binaryIterator = rows.iterator();

                    //                    parseMs = parseBinary.end(null, bytes, null);
                    //                    openSpan.field("parse_binary_ms", parseMs);

                    hasNext = binaryIterator.hasNext();
                } else {
                    String csvText = this.copyManagerProxy.copyOutAsString(copyOutSql);

                    bytes = csvText == null ? 0 : csvText.length();
                    PerformanceLogger.Span parseCSV =
                            PerformanceLogger.span("parse_csv")
                                    .field(
                                            "table",
                                            inputSplit.getTablePath().getSchemaAndTableName())
                                    .field("split_id", inputSplit.splitId());

                    // Parse csv from PGCopy
                    this.csvParser = CSVParser.parse(csvText, CSVFormat.POSTGRESQL_CSV);
                    this.csvIterator = csvParser.iterator();

                    parseMs = parseCSV.end(null, bytes, null);
                    //                    openSpan.field("parse_csv_ms", parseMs);

                    hasNext = csvIterator.hasNext();
                }

                //                execCopy.end(null, bytes, copyOutSql);
                //                openSpan.end(null, null, selectSqlLocal);
            } else {
                statement = chunkSplitter.generateSplitStatement(inputSplit, splitTableSchema);
                resultSet = statement.executeQuery();
                hasNext = resultSet.next();
            }
        } catch (SQLException se) {
            throw new JdbcConnectorException(
                    JdbcConnectorErrorCode.CONNECT_DATABASE_FAILED,
                    "open() failed." + se.getMessage(),
                    se);
        } catch (ReflectiveOperationException | IOException e) {
            throw new JdbcConnectorException(
                    JdbcConnectorErrorCode.NO_SUPPORT_OPERATION_FAILED,
                    "OPEN COPY manager failed: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Closes all resources used.
     *
     * @throws IOException Indicates that a resource could not be closed.
     */
    //    public void close() throws IOException {
    //        if (resultSet != null) {
    //            try {
    //                resultSet.close();
    //            } catch (SQLException e) {
    //                LOG.info("ResultSet couldn't be closed - " + e.getMessage());
    //            }
    //        }
    //        if (statement != null) {
    //            try {
    //                statement.close();
    //            } catch (SQLException e) {
    //                LOG.info("Statement couldn't be closed - " + e.getMessage());
    //            }
    //        }
    //    }

    // by wjr 2025.10.23
    public void close() throws IOException {
        if (useCopyStatement) {
            try {
                if (csvParser != null) {
                    csvParser.close();
                }
                //                PerformanceLogger.span("close").field("rows_total",
                // rowsRead).end();
            } catch (Exception e) {
                LOG.info("CSVParser couldn't be closed - " + e.getMessage());
            } finally {
                csvParser = null;
                csvIterator = null;
                binaryIterator = null;
                copyManagerProxy = null;
            }
            return;
        }
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                LOG.info("ResultSet couldn't be closed - " + e.getMessage());
            }
        }
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                LOG.info("Statement couldn't be closed - " + e.getMessage());
            }
        }
    }

    /**
     * Checks whether all data has been read.
     *
     * @return boolean value indication whether all data has been read.
     */
    public boolean reachedEnd() {
        return !hasNext;
    }

    /** Convert a row of data to seatunnelRow */
    //    public SeaTunnelRow nextRecord() {
    //        try {
    //            if (!hasNext) {
    //                return null;
    //            }
    //            SeaTunnelRow seaTunnelRow = jdbcRowConverter.toInternal(resultSet,
    // splitTableSchema);
    //            seaTunnelRow.setTableId(splitTableId);
    //            seaTunnelRow.setRowKind(RowKind.INSERT);
    //
    //            // update hasNext after we've read the record
    //            hasNext = resultSet.next();
    //            return seaTunnelRow;
    //        } catch (SQLException se) {
    //            throw new JdbcConnectorException(
    //                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
    //                    "Couldn't read data - " + se.getMessage(),
    //                    se);
    //        } catch (NullPointerException npe) {
    //            throw new JdbcConnectorException(
    //                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
    //                    "Couldn't access resultSet",
    //                    npe);
    //        }
    //    }

    // by wjr 2025.10.23
    public SeaTunnelRow nextRecord() throws IOException {
        try {
            if (!hasNext) {
                return null;
            }
            if (useCopyStatement) {
                if (useBinary) {
                    SeaTunnelRow seaTunnelRow = binaryIterator.next();
                    seaTunnelRow.setTableId(splitTableId);
                    seaTunnelRow.setRowKind(RowKind.INSERT);
                    hasNext = binaryIterator.hasNext();
                    rowsRead++;
                    return seaTunnelRow;
                } else {
                    CSVRecord record = csvIterator.next();
                    SeaTunnelRow seaTunnelRow = fromCsvRecord(record, splitTableSchema);
                    seaTunnelRow.setTableId(splitTableId);
                    seaTunnelRow.setRowKind(RowKind.INSERT);
                    hasNext = csvIterator.hasNext();
                    rowsRead++;
                    return seaTunnelRow;
                }

            } else {
                SeaTunnelRow seaTunnelRow =
                        jdbcRowConverter.toInternal(resultSet, splitTableSchema);
                seaTunnelRow.setTableId(splitTableId);
                seaTunnelRow.setRowKind(RowKind.INSERT);
                hasNext = resultSet.next();
                return seaTunnelRow;
            }
        } catch (SQLException se) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    "Couldn't read data - " + se.getMessage(),
                    se);
        } catch (NullPointerException npe) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    "Couldn't access resultSet",
                    npe);
        }
    }

    // 解析 PG Binary COPY 输出为 SeaTunnelRow 列表
    private java.util.List<SeaTunnelRow> parseBinaryRows(byte[] data, TableSchema schema) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        List<SeaTunnelRow> rows = new ArrayList<>();
        // 头部：11字节签名 + 4字节 flags + 4字节扩展长度 + 扩展内容
        if (buffer.remaining() < 19) {
            return rows;
        }
        byte[] signature = new byte[11];
        buffer.get(signature);
        // 用字节级比较替代字符串比较
        byte[] expected =
                new byte[] {'P', 'G', 'C', 'O', 'P', 'Y', '\n', (byte) 0xFF, '\r', '\n', 0};
        for (int i = 0; i < expected.length; i++) {
            if (signature[i] != expected[i]) {
                throw new JdbcConnectorException(
                        CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                        "Invalid COPY BINARY signature at index "
                                + i
                                + " expected="
                                + (expected[i] & 0xFF)
                                + " got="
                                + (signature[i] & 0xFF));
            }
        }
        buffer.getInt(); // flags
        int headerExtLen = buffer.getInt();
        if (headerExtLen > 0) {
            if (buffer.remaining() < headerExtLen) {
                throw new JdbcConnectorException(
                        CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                        "Invalid header extension length");
            }
            buffer.position(buffer.position() + headerExtLen);
        }

        // 行解析
        SeaTunnelRowType rowType = schema.toPhysicalRowDataType();
        while (buffer.remaining() >= 2) {
            short numFields = buffer.getShort();
            if (numFields == -1) {
                // 文件尾
                break;
            }
            if (numFields != rowType.getTotalFields()) {
                throw new JdbcConnectorException(
                        CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                        "Column count mismatch: expected "
                                + rowType.getTotalFields()
                                + " got "
                                + numFields);
            }

            Object[] fields = new Object[numFields];
            boolean enough = true;
            for (int i = 0; i < numFields; i++) {
                if (buffer.remaining() < 4) {
                    enough = false;
                    break;
                }
                int len = buffer.getInt();
                if (len == -1) {
                    fields[i] = null;
                    continue;
                }
                if (buffer.remaining() < len) {
                    enough = false;
                    break;
                }
                byte[] fieldBytes = new byte[len];
                buffer.get(fieldBytes);
                SeaTunnelDataType<?> type = rowType.getFieldType(i);
                fields[i] = parseBinaryField(fieldBytes, type);
            }

            if (!enough) {
                // 数据不完整则结束（这里一次性内存中解析，不做跨块拼接）
                break;
            }

            rows.add(new SeaTunnelRow(fields));
        }

        return rows;
    }

    // Binary 字段解析（按 PG Binary 约定：大端序，日期/时间以 2000-01-01 为 epoch）
    private Object parseBinaryField(
            byte[] fieldData, org.apache.seatunnel.api.table.type.SeaTunnelDataType<?> type) {
        if (fieldData == null) {
            return null;
        }

        ByteBuffer buf = ByteBuffer.wrap(fieldData).order(java.nio.ByteOrder.BIG_ENDIAN);
        switch (type.getSqlType()) {
            case STRING:
                return new String(fieldData, java.nio.charset.StandardCharsets.UTF_8);
            case BOOLEAN:
                return fieldData[0] != 0;
            case TINYINT:
                return fieldData.length > 0 ? fieldData[0] : (byte) 0;
            case SMALLINT:
                return buf.getShort();
            case INT:
                return buf.getInt();
            case BIGINT:
                return buf.getLong();
            case FLOAT:
                return buf.getFloat();
            case DOUBLE:
                return buf.getDouble();
            case DECIMAL:
                return decodePgNumeric(fieldData);
            case DATE:
                {
                    int pgDays = buf.getInt();
                    LocalDate base = LocalDate.of(2000, 1, 1);
                    return base.plusDays(pgDays);
                }
            case TIME:
                {
                    long micros = buf.getLong();
                    return java.time.LocalTime.ofNanoOfDay(micros * 1000L);
                }
            case TIMESTAMP:
                {
                    long pgMicros = buf.getLong();
                    LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
                    return base.plusNanos(pgMicros * 1000L);
                }
            case BYTES:
                return fieldData;
            case NULL:
                return null;
            case MAP:
            case ARRAY:
            case ROW:
            default:
                throw new org.apache.seatunnel.connectors.seatunnel.jdbc.exception
                        .JdbcConnectorException(
                        org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated
                                .UNSUPPORTED_DATA_TYPE,
                        "Unexpected value in binary parse: " + type);
        }
    }

    /**
     * 解析 PostgreSQL Numeric 的二进制格式为 BigDecimal。
     *
     * <p>结构（全为大端序 int16）： int16 ndigits — base-100 digit count int16 weight — digits to left of
     * decimal - 1 (base-100) int16 sign — 0x0000=POS, 0x4000=NEG, 0xC000=NaN int16 dscale — decimal
     * scale (十进制位数) int16[ndigits] digits — base-100 digits (每个 0..99)
     *
     * <p>PostgreSQL 文档参考：
     * https://github.com/postgres/postgres/blob/master/src/backend/utils/adt/numeric.c
     */
    private java.math.BigDecimal decodePgNumeric(byte[] fieldData) {
        java.nio.ByteBuffer buf =
                java.nio.ByteBuffer.wrap(fieldData).order(java.nio.ByteOrder.BIG_ENDIAN);

        if (buf.remaining() < 8) {
            throw new org.apache.seatunnel.connectors.seatunnel.jdbc.exception
                    .JdbcConnectorException(
                    org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated
                            .UNSUPPORTED_OPERATION,
                    "Invalid PG numeric binary length: " + fieldData.length);
        }

        final int ndigits = buf.getShort() & 0xFFFF;
        final int weight = buf.getShort();
        final int sign = buf.getShort() & 0xFFFF;
        final int dscale = buf.getShort() & 0xFFFF;

        // 检查 NaN
        if (sign == 0xC000) {
            throw new org.apache.seatunnel.connectors.seatunnel.jdbc.exception
                    .JdbcConnectorException(
                    org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated
                            .UNSUPPORTED_OPERATION,
                    "PG numeric NaN is not supported");
        }

        // 读取 digits（base-100）
        int[] digits = new int[ndigits];
        for (int i = 0; i < ndigits; i++) {
            if (buf.remaining() < 2) {
                throw new org.apache.seatunnel.connectors.seatunnel.jdbc.exception
                        .JdbcConnectorException(
                        org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated
                                .UNSUPPORTED_OPERATION,
                        "Invalid PG numeric digit data: insufficient bytes");
            }
            digits[i] = buf.getShort() & 0xFFFF;
        }

        // -------------------------------
        // 构造十进制字符串
        // -------------------------------
        // PostgreSQL 每个 digit 表示两位十进制数
        // 小数点左边的 base-100 数量 = weight + 1
        int intBase100Count = weight + 1;

        // 提前估算字符串长度（防扩容）
        int estimatedLength = ndigits * 2 + 10;
        StringBuilder sb = new StringBuilder(estimatedLength);

        // 符号处理
        if (sign == 0x4000) {
            sb.append('-');
        }

        // 如果没有 digits，直接返回 0
        if (ndigits == 0) {
            return java.math.BigDecimal.ZERO.setScale(dscale);
        }

        // 整数部分
        int idx = 0;
        if (intBase100Count <= 0) {
            sb.append("0");
        } else {
            while (idx < Math.min(ndigits, intBase100Count)) {
                int v = digits[idx++];
                if (idx == 1) {
                    sb.append(v);
                } else {
                    if (v < 10) sb.append('0');
                    sb.append(v);
                }
            }
            // 如果 weight 超出已有 digits，补 00
            while (idx < intBase100Count) {
                sb.append("00");
                idx++;
            }
        }

        // 小数部分
        StringBuilder frac = new StringBuilder();
        if (intBase100Count < 0) {
            // 小数点在 digits 左边
            int padZeros = -intBase100Count;
            for (int i = 0; i < padZeros; i++) {
                frac.append("00");
            }
            for (int v : digits) {
                if (v < 10) frac.append('0');
                frac.append(v);
            }
        } else {
            for (int j = intBase100Count; j < ndigits; j++) {
                int v = digits[j];
                if (v < 10) frac.append('0');
                frac.append(v);
            }
        }

        // 拼接小数点
        if (frac.length() > 0) {
            sb.append('.');
            sb.append(frac);
        }

        // 精确修剪到 dscale 十进制位
        if (dscale > 0) {
            int currentScale = frac.length();
            if (currentScale > dscale) {
                sb.setLength(sb.length() - (currentScale - dscale));
            } else if (currentScale < dscale) {
                for (int i = 0; i < dscale - currentScale; i++) {
                    sb.append('0');
                }
            }
        } else if (frac.length() > 0) {
            // 无小数位时去掉小数点
            int dotIdx = sb.indexOf(".");
            if (dotIdx != -1) {
                sb.setLength(dotIdx);
            }
        }

        // 清理多余末尾小数点
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
            sb.setLength(sb.length() - 1);
        }

        try {
            return new java.math.BigDecimal(sb.toString());
        } catch (NumberFormatException e) {
            throw new org.apache.seatunnel.connectors.seatunnel.jdbc.exception
                    .JdbcConnectorException(
                    org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated
                            .UNSUPPORTED_OPERATION,
                    "Failed to parse PG numeric value from: " + sb,
                    e);
        }
    }

    private SeaTunnelRow fromCsvRecord(CSVRecord record, TableSchema schema) {
        SeaTunnelRowType rowType = schema.toPhysicalRowDataType();
        Object[] fields = new Object[rowType.getTotalFields()];
        for (int i = 0; i < rowType.getTotalFields(); i++) {
            SeaTunnelDataType<?> type = rowType.getFieldType(i);
            String raw = record.get(i);
            if (raw == null || raw.isEmpty() || "\\N".equals(raw)) {
                fields[i] = null;
                continue;
            }
            switch (type.getSqlType()) {
                case STRING:
                    fields[i] = raw;
                    break;
                case BOOLEAN:
                    fields[i] = Boolean.parseBoolean(raw);
                    break;
                case TINYINT:
                    fields[i] = Byte.valueOf(raw);
                    break;
                case SMALLINT:
                    fields[i] = Short.valueOf(raw);
                    break;
                case INT:
                    fields[i] = Integer.valueOf(raw);
                    break;
                case BIGINT:
                    fields[i] = Long.valueOf(raw);
                    break;
                case FLOAT:
                    fields[i] = Float.valueOf(raw);
                    break;
                case DOUBLE:
                    fields[i] = Double.valueOf(raw);
                    break;
                case DECIMAL:
                    fields[i] = new BigDecimal(raw);
                    break;
                case DATE:
                    fields[i] = Date.valueOf(raw).toLocalDate();
                    break;
                case TIME:
                    fields[i] = Time.valueOf(raw).toLocalTime();
                    break;
                case TIMESTAMP:
                    fields[i] = Timestamp.valueOf(raw).toLocalDateTime();
                    break;
                case BYTES:
                    fields[i] = Base64.decodeBase64(raw);
                    break;
                case NULL:
                    fields[i] = null;
                    break;
                case MAP:
                case ARRAY:
                case ROW:
                default:
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.UNSUPPORTED_DATA_TYPE,
                            "Unexpected value: " + type);
            }
        }
        return new SeaTunnelRow(fields);
    }
}
