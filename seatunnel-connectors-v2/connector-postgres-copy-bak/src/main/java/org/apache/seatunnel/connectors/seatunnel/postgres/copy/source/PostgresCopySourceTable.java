package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
public class PostgresCopySourceTable implements Serializable {
    private static final long serialVersionUID = 1L;

    private final TablePath tablePath;
    private final String query;
    private final String partitionColumn;
    private final Integer partitionNumber;
    private final BigDecimal partitionStart;
    private final BigDecimal partitionEnd;
    private final Boolean useSelectCount;
    private final Boolean skipAnalyze;
    private final CatalogTable catalogTable;
    private final String jdbcUrl;
    private final String username;
    private final String password;
}