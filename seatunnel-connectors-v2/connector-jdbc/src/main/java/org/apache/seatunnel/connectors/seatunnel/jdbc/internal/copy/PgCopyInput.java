package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.copy;

import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcSourceConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.JdbcConnectionProvider;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialect;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.CopyManagerProxy;
import org.apache.seatunnel.connectors.seatunnel.jdbc.source.ChunkSplitter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.source.JdbcSourceSplit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/** Unified entry for PostgreSQL COPY input (CSV or BINARY). */
public final class PgCopyInput implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PgCopyInput.class);

    private final JdbcSourceConfig config;
    private final JdbcDialect dialect;
    private final ChunkSplitter chunkSplitter;
    private final TableSchema tableSchema;
    private final String tableId;

    private final boolean useBinary;

    private boolean hasNext;

    private transient CopyManagerProxy copyManagerProxy;
    private transient InputStream copyStream;
    private transient PgCopyReader reader;
    private transient JdbcConnectionProvider connectionProvider;
    private transient Connection copyConnection;

    public PgCopyInput(
            JdbcSourceConfig config,
            JdbcDialect dialect,
            ChunkSplitter chunkSplitter,
            TableSchema tableSchema,
            String tableId) {
        this.config = config;
        this.dialect = dialect;
        this.chunkSplitter = chunkSplitter;
        this.tableSchema = tableSchema;
        this.tableId = tableId;
        this.useBinary = config.isBinary();
    }

    /** Open a COPY stream for a given split. */
    public void open(JdbcSourceSplit split) {
        try {
            String selectSql = chunkSplitter.generateSplitQuerySQL(split, tableSchema);
            String copySql =
                    String.format(
                            "COPY (%s) TO STDOUT WITH %s", selectSql, useBinary ? "BINARY" : "CSV");

            Connection conn = getConnection();
            LOG.info("Open PG COPY split={}, sql={}", split.splitId(), copySql);

            copyManagerProxy = new CopyManagerProxy(conn);
            copyStream = copyManagerProxy.copyOutAsStream(copySql);

            reader = createReader(copyStream);
            hasNext = reader.hasNext();
        } catch (Exception e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    "Failed to open PG COPY stream: " + e.getMessage(),
                    e);
        }
    }

    private Connection getConnection() throws SQLException, ClassNotFoundException {
        connectionProvider = dialect.getJdbcConnectionProvider(config.getJdbcConnectionConfig());
        copyConnection = connectionProvider.getOrEstablishConnection();
        return copyConnection;
    }

    private PgCopyReader createReader(InputStream stream) throws Exception {
        if (useBinary) {
            return new PgCopyBinaryReader(stream, tableSchema, config.getPgCopyBufferSize());
        } else {
            return new PgCopyCsvReader(stream, tableSchema);
        }
    }

    public boolean hasNext() {
        return hasNext;
    }

    public SeaTunnelRow next() {
        if (reader == null) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    "COPY reader not initialized. Did you call open()?");
        }
        if (!hasNext) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    "No more data available in PG COPY stream");
        }

        SeaTunnelRow row = reader.next();
        if (row == null) {
            hasNext = false;
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    "Unexpected end of PG COPY stream");
        }
        hasNext = reader.hasNext();
        return row;
    }

    @Override
    public void close() {
        List<Object> resources =
                Arrays.asList(
                        reader, copyStream, copyManagerProxy, copyConnection, connectionProvider);
        for (Object r : resources) {
            PgCopyUtils.closeQuietly(r);
        }
        reader = null;
        copyStream = null;
        copyManagerProxy = null;
        copyConnection = null;
        connectionProvider = null;
    }
}
