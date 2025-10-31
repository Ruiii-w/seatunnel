package org.apache.seatunnel.connectors.seatunnel.postgres.copy.config;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.config.PostgresCopyOptions;

import lombok.Data;

import java.io.Serializable;

@Data
public class PostgresCopySourceConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String table;
    private final String[] columns;
    private final String where;
    private final String copySql;
    private final String splitMode;
    private final String splitBy;
    private final int splitSize;
    private final int hashBuckets;

    // 采样分片相关配置
    private final double splitEvenDistributionFactorUpperBound;
    private final double splitEvenDistributionFactorLowerBound;
    private final int splitSampleShardingThreshold;
    private final double splitInverseSamplingRate;

    public PostgresCopySourceConfig(ReadonlyConfig config) {
        this.jdbcUrl = config.get(PostgresCopyOptions.JDBC_URL);
        this.username = config.get(PostgresCopyOptions.USERNAME);
        this.password = config.get(PostgresCopyOptions.PASSWORD);
        this.table = config.get(PostgresCopyOptions.TABLE);
        this.columns = config.get(PostgresCopyOptions.COLUMNS);
        this.where = config.get(PostgresCopyOptions.WHERE);
        this.copySql = config.get(PostgresCopyOptions.COPY_SQL);
        this.splitMode = config.get(PostgresCopyOptions.SPLIT_MODE);
        this.splitBy = config.get(PostgresCopyOptions.SPLIT_BY);
        this.splitSize = config.get(PostgresCopyOptions.SPLIT_SIZE);
        this.hashBuckets = config.get(PostgresCopyOptions.HASH_BUCKETS);

        // 采样分片配置，使用默认值
        this.splitEvenDistributionFactorUpperBound = config.getOptional(PostgresCopyOptions.SPLIT_EVEN_DISTRIBUTION_FACTOR_UPPER_BOUND).orElse(100.0);
        this.splitEvenDistributionFactorLowerBound = config.getOptional(PostgresCopyOptions.SPLIT_EVEN_DISTRIBUTION_FACTOR_LOWER_BOUND).orElse(0.05);
        this.splitSampleShardingThreshold = config.getOptional(PostgresCopyOptions.SPLIT_SAMPLE_SHARDING_THRESHOLD).orElse(1000);
        this.splitInverseSamplingRate = config.getOptional(PostgresCopyOptions.SPLIT_INVERSE_SAMPLING_RATE).orElse(1000.0);
    }
}