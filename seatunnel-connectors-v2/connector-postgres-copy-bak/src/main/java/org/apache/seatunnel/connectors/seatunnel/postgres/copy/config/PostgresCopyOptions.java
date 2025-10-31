package org.apache.seatunnel.connectors.seatunnel.postgres.copy.config;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.util.OptionRule;

import java.util.Map;

public class PostgresCopyOptions {

    public static final Option<String> JDBC_URL =
            Options.key("jdbc_url")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("JDBC url, e.g. jdbc:postgresql://host:5432/db");

    public static final Option<String> USERNAME =
            Options.key("username").stringType().noDefaultValue().withDescription("DB user");

    public static final Option<String> PASSWORD =
            Options.key("password").stringType().noDefaultValue().withDescription("DB password");

    public static final Option<String> TABLE =
            Options.key("table")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Table name, schema qualified if needed");

    public static final Option<String> DATABASE =
            Options.key("database").stringType().noDefaultValue().withDescription("DB name");

    public static final Option<String> COLUMNS =
            Options.key("columns")
                    .stringType()
                    .defaultValue("*")
                    .withDescription("Comma-separated column list, default *");

    public static final Option<String> WHERE =
            Options.key("where")
                    .stringType()
                    .defaultValue("")
                    .withDescription("Optional WHERE filter");

    public static final Option<String> COPY_SQL =
            Options.key("copy_sql")
                    .stringType()
                    .defaultValue("")
                    .withDescription(
                            "Custom COPY SQL, overrides table/columns/where; must start with COPY (SELECT ...) TO STDOUT ...");

    public static final Option<Boolean> CSV_HEADER =
            Options.key("csv_header")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("COPY WITH HEADER");

    public static final Option<String> DELIMITER =
            Options.key("delimiter")
                    .stringType()
                    .defaultValue(",")
                    .withDescription("CSV delimiter");

    public static final Option<String> NULL_AS =
            Options.key("null_as")
                    .stringType()
                    .defaultValue("")
                    .withDescription("NULL AS setting for COPY");

    public static final Option<String> QUOTE =
            Options.key("quote").stringType().defaultValue("\"").withDescription("CSV quote char");

    public static final Option<String> ESCAPE =
            Options.key("escape")
                    .stringType()
                    .defaultValue("\"")
                    .withDescription("CSV escape char");

    // 并行切分
    public static final Option<String> SPLIT_MODE =
            Options.key("split_mode")
                    .stringType()
                    .defaultValue("none") // range | hash | none
                    .withDescription("Split strategy: range/hash/none");

    public static final Option<String> SPLIT_BY =
            Options.key("split_by")
                    .stringType()
                    .defaultValue("")
                    .withDescription("Split column (numeric/integer recommended for range)");

    public static final Option<Integer> SPLIT_SIZE =
            Options.key("split_size")
                    .intType()
                    .defaultValue(1000000)
                    .withDescription("Approx rows per split in range mode");

    public static final Option<Integer> HASH_BUCKETS =
            Options.key("hash_buckets")
                    .intType()
                    .defaultValue(0)
                    .withDescription(
                            "Number of buckets in hash mode (defaults to parallelism if 0)");

    public static final Option<Boolean> BINARY =
            Options.key("binary")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Use COPY ... BINARY (requires custom decoder)");

    public static final Option<Map<String, String>> SCHEMA_FIELDS =
            Options.key("schema.fields")
                    .mapType()
                    .noDefaultValue()
                    .withDescription(
                            "Table schema fields definition, e.g. {\"id\": \"bigint\", \"name\": \"string\"}");

    public static final Option<Boolean> AUTO_SCHEMA =
            Options.key("auto_schema")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether to automatically detect schema from database");

    public static final Option<Integer> HASH_BUCKETS =
            Options.key("hash_buckets")
                    .intType()
                    .defaultValue(16)
                    .withDescription("Hash buckets for hash split mode");

    // 新增采样分片相关配置选项
    public static final Option<Double> SPLIT_EVEN_DISTRIBUTION_FACTOR_UPPER_BOUND =
            Options.key("split.even-distribution.factor.upper-bound")
                    .doubleType()
                    .defaultValue(100.0)
                    .withDescription("Upper bound of the distribution factor for even split");

    public static final Option<Double> SPLIT_EVEN_DISTRIBUTION_FACTOR_LOWER_BOUND =
            Options.key("split.even-distribution.factor.lower-bound")
                    .doubleType()
                    .defaultValue(0.05)
                    .withDescription("Lower bound of the distribution factor for even split");

    public static final Option<Integer> SPLIT_SAMPLE_SHARDING_THRESHOLD =
            Options.key("split.sample-sharding.threshold")
                    .intType()
                    .defaultValue(1000)
                    .withDescription("Threshold for using sample sharding strategy");

    public static final Option<Double> SPLIT_INVERSE_SAMPLING_RATE =
            Options.key("split.inverse-sampling.rate")
                    .doubleType()
                    .defaultValue(1000.0)
                    .withDescription("Inverse of the sampling rate for sample sharding");

    public static OptionRule optionRule() {
        return OptionRule.builder()
                .required(JDBC_URL, USERNAME, PASSWORD)
                .optional(
                        TABLE,
                        COLUMNS,
                        WHERE,
                        COPY_SQL,
                        CSV_HEADER,
                        DELIMITER,
                        NULL_AS,
                        QUOTE,
                        ESCAPE,
                        SPLIT_MODE,
                        SPLIT_BY,
                        SPLIT_SIZE,
                        HASH_BUCKETS,
                        BINARY,
                        SCHEMA_FIELDS,
                        AUTO_SCHEMA)
                .build();
    }
}
