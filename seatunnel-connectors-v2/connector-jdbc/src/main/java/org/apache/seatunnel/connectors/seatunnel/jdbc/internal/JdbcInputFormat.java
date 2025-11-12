package org.apache.seatunnel.connectors.seatunnel.jdbc.internal;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcSourceConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.copy.PgCopyInput;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialect;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialectLoader;
import org.apache.seatunnel.connectors.seatunnel.jdbc.source.ChunkSplitter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.source.JdbcSourceSplit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    private final Map<TablePath, CatalogTable> tables;
    private final ChunkSplitter chunkSplitter;

    private final JdbcSourceConfig config;
    private final boolean useCopyStatement;

    private transient String splitTableId;
    private transient TableSchema splitTableSchema;
    private transient PreparedStatement statement;
    private transient ResultSet resultSet;
    private volatile boolean hasNext;

    // 封装后的 PG COPY 输入对象
    private transient PgCopyInput pgCopyInput;

    public JdbcInputFormat(JdbcSourceConfig config, Map<TablePath, CatalogTable> tables) {
        this.jdbcDialect =
                JdbcDialectLoader.load(
                        config.getJdbcConnectionConfig().getUrl(), config.getCompatibleMode());
        this.chunkSplitter = ChunkSplitter.create(config);
        this.tables = tables;

        this.config = config;
        this.useCopyStatement = config.isUseCopyStatement();
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
    public void open(JdbcSourceSplit inputSplit) throws IOException {
        try {
            splitTableSchema = tables.get(inputSplit.getTablePath()).getTableSchema();
            splitTableId = inputSplit.getTablePath().toString();

            if (useCopyStatement) {
                // COPY 模式
                pgCopyInput =
                        new PgCopyInput(
                                config, jdbcDialect, chunkSplitter, splitTableSchema, splitTableId);
                pgCopyInput.open(inputSplit);
                hasNext = pgCopyInput.hasNext();
            } else {
                // 传统 JDBC 模式
                statement = chunkSplitter.generateSplitStatement(inputSplit, splitTableSchema);
                resultSet = statement.executeQuery();
                hasNext = resultSet.next();
            }
        } catch (SQLException se) {
            throw new JdbcConnectorException(
                    JdbcConnectorErrorCode.CONNECT_DATABASE_FAILED,
                    "open() failed." + se.getMessage(),
                    se);
        }
    }

    /**
     * Closes all resources used.
     *
     * @throws IOException Indicates that a resource could not be closed.
     */
    public void close() throws IOException {
        if (useCopyStatement) {
            try {
                if (pgCopyInput != null) {
                    pgCopyInput.close();
                }
            } catch (Exception e) {
                LOG.info("PG COPY resources couldn't be closed - " + e.getMessage());
            } finally {
                pgCopyInput = null;
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
    public SeaTunnelRow nextRecord() throws IOException {
        try {
            if (useCopyStatement) {
                // PG COPY 模式
                SeaTunnelRow row = pgCopyInput.next();
                hasNext = pgCopyInput.hasNext();
                return row;
            } else {
                SeaTunnelRow seaTunnelRow =
                        jdbcDialect.getRowConverter().toInternal(resultSet, splitTableSchema);
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
}
