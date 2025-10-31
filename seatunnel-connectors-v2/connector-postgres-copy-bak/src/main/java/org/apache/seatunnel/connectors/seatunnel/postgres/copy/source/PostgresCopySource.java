// PostgresCopySource.java
package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import org.apache.seatunnel.shade.com.fasterxml.jackson.core.type.TypeReference;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.source.Boundedness;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceReader;
import org.apache.seatunnel.api.source.SourceSplitEnumerator;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.factory.TableFactoryContext;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.utils.JdbcUrlUtil;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.psql.PostgresCatalog;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.config.PostgresCopyOptions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TypeReference
public class PostgresCopySource
        implements SeaTunnelSource<SeaTunnelRow, PostgresCopySourceSplit, Serializable> {

    private final ReadonlyConfig config;
    private final SeaTunnelRowType rowType;
    private final CatalogTable catalogTable;

    //    Config
    public PostgresCopySource(TableFactoryContext context) {
        this.config = context.getOptions();
        this.rowType = buildRowTypeFromConfig(config);
        this.catalogTable = buildCatalogTableFromConfig(config, rowType);
    }

    @Override
    public String getPluginName() {
        return "PostgresCopySource";
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        return Collections.singletonList(catalogTable);
    }

    @Override
    public SourceReader<SeaTunnelRow, PostgresCopySourceSplit> createReader(
            SourceReader.Context readerContext) throws Exception {
        return new PostgresCopySourceReader(readerContext, config, rowType);
    }

    @Override
    public SourceSplitEnumerator<PostgresCopySourceSplit, Serializable> createEnumerator(
            SourceSplitEnumerator.Context<PostgresCopySourceSplit> context) {
        return new PostgresCopySplitEnumerator(context, config);
    }

    @Override
    public SourceSplitEnumerator<PostgresCopySourceSplit, Serializable> restoreEnumerator(
            SourceSplitEnumerator.Context<PostgresCopySourceSplit> context,
            Serializable checkpointState) {
        return new PostgresCopySplitEnumerator(context, config, checkpointState);
    }

    //    @Override
    //    public SeaTunnelDataType<SeaTunnelRow> getProducedType() {
    //        return rowType;
    //    }

    private CatalogTable buildCatalogTableFromConfig(
            ReadonlyConfig config, SeaTunnelRowType rowType) {
        // 1. 构建表标识符
        TableIdentifier tableIdentifier = buildTableIdentifier(config);

        // 2. 构建表结构
        TableSchema tableSchema = buildTableSchema(rowType);

        // 3. 构建表选项
        Map<String, String> options = buildTableOptions(config);

        // 4. 构建分区键
        List<String> partitionKeys = buildPartitionKeys(config);

        // 5. 构建注释
        String comment =
                config.getOptional(new Option<>("comment", new TypeReference<String>() {}, null))
                        .orElse("PostgreSQL COPY source table");

        return CatalogTable.of(tableIdentifier, tableSchema, options, partitionKeys, comment);
    }

    // 构建表标识符 - 使用正确的 Option 构造方式
    private TableIdentifier buildTableIdentifier(ReadonlyConfig config) {
        String catalogName =
                config.getOptional(
                                new Option<>(
                                        "catalog",
                                        new TypeReference<String>() {},
                                        "postgres_copy_catalog"))
                        .orElse("postgres_copy_catalog");

        String databaseName =
                config.getOptional(
                                new Option<>("database", new TypeReference<String>() {}, "public"))
                        .orElse("public");

        String tableName =
                config.getOptional(
                                new Option<>(
                                        "table",
                                        new TypeReference<String>() {},
                                        "postgres_copy_table"))
                        .orElse("postgres_copy_table");

        return TableIdentifier.of(catalogName, databaseName, tableName);
    }

    // 构建表结构
    private TableSchema buildTableSchema(SeaTunnelRowType rowType) {
        List<Column> columns = new ArrayList<>();
        String[] fieldNames = rowType.getFieldNames();
        SeaTunnelDataType<?>[] fieldTypes = rowType.getFieldTypes();

        for (int i = 0; i < fieldNames.length; i++) {
            Column column =
                    PhysicalColumn.of(
                            fieldNames[i],
                            fieldTypes[i],
                            getColumnLength(fieldTypes[i]), // 根据类型确定长度
                            true, // 是否可为空
                            null, // 默认值
                            "Column " + fieldNames[i]);
            columns.add(column);
        }

        return TableSchema.builder().columns(columns).build();
    }

    // 根据数据类型获取合适的长度
    private Integer getColumnLength(SeaTunnelDataType<?> dataType) {
        if (dataType.equals(BasicType.STRING_TYPE)) {
            return 65535; // 字符串默认长度
        }
        return null; // 其他类型不需要指定长度
    }

    // 构建表选项
    private Map<String, String> buildTableOptions(ReadonlyConfig config) {
        Map<String, String> options = new HashMap<>();

        // 使用正确的 TypeReference 方式
        config.getOptional(new Option<>("copy-format", new TypeReference<String>() {}, "csv"))
                .ifPresent(format -> options.put("copy.format", format));

        config.getOptional(new Option<>("delimiter", new TypeReference<String>() {}, ","))
                .ifPresent(delim -> options.put("copy.delimiter", delim));

        config.getOptional(new Option<>("null-string", new TypeReference<String>() {}, "\\N"))
                .ifPresent(nullStr -> options.put("copy.null_string", nullStr));

        return options;
    }

    // 构建分区键
    private List<String> buildPartitionKeys(ReadonlyConfig config) {
        return config.getOptional(
                        new Option<>(
                                "partition-keys",
                                new TypeReference<List<String>>() {},
                                Collections.emptyList()))
                .orElse(Collections.emptyList());
    }

    //    private SeaTunnelRowType buildRowTypeFromConfig(ReadonlyConfig config) {
    //        Option<Object> schemaOption = new Option<>("schema", new TypeReference<Object>() {},
    // null);
    //        Optional<Object> schemaOptional = config.getOptional(schemaOption);
    //
    //        if (!schemaOptional.isPresent()) {
    ////             默认：单列字符串
    ////            return new SeaTunnelRowType(
    ////                    new String[] {"line"}, new SeaTunnelDataType[] {BasicType.STRING_TYPE});
    ////            List<String> columns = config.get(PostgresCopyOptions.COLUMNS);
    //
    //        }
    //
    //        Object schemaValue = schemaOptional.get();
    //        List<Map<String, Object>> fields;
    //
    //        // 处理不同的配置格式
    //        if (schemaValue instanceof List) {
    //            fields = (List<Map<String, Object>>) schemaValue;
    //        } else if (schemaValue instanceof Map) {
    //            Map<String, Object> schemaMap = (Map<String, Object>) schemaValue;
    //            if (schemaMap.containsKey("fields")) {
    //                Object fieldsObj = schemaMap.get("fields");
    //                if (fieldsObj instanceof Map) {
    //                    // 转换为列表格式
    //                    Map<String, Object> fieldMap = (Map<String, Object>) fieldsObj;
    //                    fields = new ArrayList<>();
    //                    for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
    //                        Map<String, Object> field = new HashMap<>();
    //                        field.put("name", entry.getKey());
    //                        field.put("type", entry.getValue().toString());
    //                        fields.add(field);
    //                    }
    //                } else if (fieldsObj instanceof List) {
    //                    fields = (List<Map<String, Object>>) fieldsObj;
    //                } else {
    //                    throw new IllegalArgumentException(
    //                            "Unsupported schema format: fields should be either Map or List");
    //                }
    //            } else {
    //                throw new IllegalArgumentException(
    //                        "Schema configuration should contain 'fields' property");
    //            }
    //        } else {
    //            throw new IllegalArgumentException(
    //                    "Unsupported schema format: " + schemaValue.getClass().getSimpleName());
    //        }
    //
    //        String[] fieldNames = new String[fields.size()];
    //        SeaTunnelDataType<?>[] fieldTypes = new SeaTunnelDataType<?>[fields.size()];
    //
    //        for (int i = 0; i < fields.size(); i++) {
    //            Map<String, Object> field = fields.get(i);
    //
    //            // 验证字段配置
    //            if (!field.containsKey("name")) {
    //                throw new IllegalArgumentException(
    //                        "Field configuration missing 'name' property at index " + i);
    //            }
    //            if (!field.containsKey("type")) {
    //                throw new IllegalArgumentException(
    //                        "Field configuration missing 'type' property at index " + i);
    //            }
    //
    //            fieldNames[i] = field.get("name").toString();
    //            String type = field.get("type").toString().toUpperCase().trim();
    //
    //            switch (type) {
    //                    // 字符串类型
    //                case "STRING":
    //                case "VARCHAR":
    //                case "TEXT":
    //                case "CHAR":
    //                case "CHARACTER":
    //                case "CHARACTER VARYING":
    //                case "NAME":
    //                case "BPCHAR":
    //                case "CITEXT":
    //                    fieldTypes[i] = BasicType.STRING_TYPE;
    //                    break;
    //
    //                    // 整数类型
    //                case "INT":
    //                case "INTEGER":
    //                case "INT4":
    //                case "SERIAL":
    //                    fieldTypes[i] = BasicType.INT_TYPE;
    //                    break;
    //
    //                case "BIGINT":
    //                case "LONG":
    //                case "INT8":
    //                case "BIGSERIAL":
    //                    fieldTypes[i] = BasicType.LONG_TYPE;
    //                    break;
    //
    //                case "SMALLINT":
    //                case "SHORT":
    //                case "INT2":
    //                case "SMALLSERIAL":
    //                    fieldTypes[i] = BasicType.SHORT_TYPE;
    //                    break;
    //
    //                case "TINYINT":
    //                case "BYTE":
    //                    fieldTypes[i] = BasicType.BYTE_TYPE;
    //                    break;
    //
    //                    // 浮点数类型
    //                case "DOUBLE":
    //                case "FLOAT8":
    //                case "DOUBLE PRECISION":
    //                    fieldTypes[i] = BasicType.DOUBLE_TYPE;
    //                    break;
    //
    //                case "FLOAT":
    //                case "REAL":
    //                case "FLOAT4":
    //                    fieldTypes[i] = BasicType.FLOAT_TYPE;
    //                    break;
    //
    //                    // 布尔类型
    //                case "BOOLEAN":
    //                case "BOOL":
    //                    fieldTypes[i] = BasicType.BOOLEAN_TYPE;
    //                    break;
    //
    //                    // 二进制类型
    //                case "BYTEA":
    //                    fieldTypes[i] = BasicType.BYTE_TYPE;
    //                    break;
    //
    //                    // 时间日期类型
    //                case "DATE":
    //                    fieldTypes[i] = LocalTimeType.LOCAL_DATE_TYPE;
    //                    break;
    //
    //                case "TIME":
    //                case "TIME WITHOUT TIME ZONE":
    //                    fieldTypes[i] = LocalTimeType.LOCAL_TIME_TYPE;
    //                    break;
    //
    //                case "TIMESTAMP":
    //                case "TIMESTAMP WITHOUT TIME ZONE":
    //                    fieldTypes[i] = LocalTimeType.LOCAL_DATE_TIME_TYPE;
    //                    break;
    //
    //                case "TIMESTAMP WITH TIME ZONE":
    //                case "TIMESTAMPTZ":
    //                    fieldTypes[i] = LocalTimeType.LOCAL_DATE_TIME_TYPE;
    //                    break;
    //
    //                    // 数值类型
    //                case "NUMERIC":
    //                case "DECIMAL":
    //                    // 默认精度和小数位
    //                    int precision = 28;
    //                    int scale = 18;
    //                    // 如果字段配置中指定了精度和小数位，则使用指定值
    //                    if (field.containsKey("precision")) {
    //                        precision = Integer.parseInt(field.get("precision").toString());
    //                    }
    //                    if (field.containsKey("scale")) {
    //                        scale = Integer.parseInt(field.get("scale").toString());
    //                    }
    //                    fieldTypes[i] = new DecimalType(precision, scale);
    //                    break;
    //
    //                default:
    //                    throw new UnsupportedOperationException(
    //                            String.format(
    //                                    "Unsupported field type: %s for field: %s",
    //                                    type, fieldNames[i]));
    //            }
    //        }
    //
    //        return new SeaTunnelRowType(fieldNames, fieldTypes);
    //    }

    private SeaTunnelRowType buildRowTypeFromConfig(ReadonlyConfig config) {
        // 1. 检查auto_schema配置
        boolean autoSchema = config.get(PostgresCopyOptions.AUTO_SCHEMA);

        if (autoSchema) {
            // 使用PostgresCatalog动态获取列类型
            String url = config.get(PostgresCopyOptions.JDBC_URL);
            String user = config.get(PostgresCopyOptions.USERNAME);
            String password = config.get(PostgresCopyOptions.PASSWORD);
            String table = config.get(PostgresCopyOptions.TABLE);
            String database = config.get(PostgresCopyOptions.DATABASE);
            // 解析schema.table格式
            String[] parts = table.split("\\.");
            String schemaName = parts.length > 1 ? parts[0] : "public";
            String tableName = parts.length > 1 ? parts[1] : table;

            String defaultCatalogName = "postgres-copy";
            String defaultDriveClass = "org.postgresql.Driver";
            try (PostgresCatalog catalog =
                    new PostgresCatalog(
                            defaultCatalogName,
                            user,
                            password,
                            JdbcUrlUtil.getUrlInfo(url),
                            schemaName,
                            defaultDriveClass)) {
                catalog.open();

                TablePath tablePath = TablePath.of(database, schemaName, tableName);
                TableSchema tableSchema = catalog.getTable(tablePath).getTableSchema();

                // 转换成SeaTunnelRowType
                List<Column> columns = tableSchema.getColumns();
                String[] fieldNames = new String[columns.size()];
                SeaTunnelDataType<?>[] fieldTypes = new SeaTunnelDataType<?>[columns.size()];

                for (int i = 0; i < columns.size(); i++) {
                    Column column = columns.get(i);
                    fieldNames[i] = column.getName();
                    fieldTypes[i] = column.getDataType();
                }

                return new SeaTunnelRowType(fieldNames, fieldTypes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get table schema from database", e);
            }
        } else {
            // 从schema.fields配置中解析
            Map<String, String> schemaFields = config.get(PostgresCopyOptions.SCHEMA_FIELDS);
            if (schemaFields == null || schemaFields.isEmpty()) {
                throw new IllegalArgumentException(
                        "schema.fields must be specified when auto_schema is false");
            }

            String[] fieldNames = new String[schemaFields.size()];
            SeaTunnelDataType<?>[] fieldTypes = new SeaTunnelDataType<?>[schemaFields.size()];

            int i = 0;
            for (Map.Entry<String, String> entry : schemaFields.entrySet()) {
                fieldNames[i] = entry.getKey();
                fieldTypes[i] = convertToSeaTunnelType(entry.getValue());
                i++;
            }

            return new SeaTunnelRowType(fieldNames, fieldTypes);
        }
    }

    private SeaTunnelDataType<?> convertToSeaTunnelType(String typeStr) {
        switch (typeStr.toUpperCase()) {
                // 字符串类型
            case "STRING":
            case "VARCHAR":
            case "TEXT":
            case "CHAR":
            case "CHARACTER":
            case "CHARACTER VARYING":
            case "NAME":
            case "BPCHAR":
            case "CITEXT":
                return BasicType.STRING_TYPE;

                // 整数类型
            case "INT":
            case "INTEGER":
            case "INT4":
            case "SERIAL":
                return BasicType.INT_TYPE;

            case "BIGINT":
            case "LONG":
            case "INT8":
            case "BIGSERIAL":
                return BasicType.LONG_TYPE;

            case "SMALLINT":
            case "SHORT":
            case "INT2":
            case "SMALLSERIAL":
                return BasicType.SHORT_TYPE;

            case "TINYINT":
            case "BYTE":
                return BasicType.BYTE_TYPE;

                // 浮点数类型
            case "DOUBLE":
            case "FLOAT8":
            case "DOUBLE PRECISION":
                return BasicType.DOUBLE_TYPE;

            case "FLOAT":
            case "REAL":
            case "FLOAT4":
                return BasicType.FLOAT_TYPE;

                // 布尔类型
            case "BOOLEAN":
            case "BOOL":
                return BasicType.BOOLEAN_TYPE;

                // 二进制类型
            case "BYTEA":
                return BasicType.BYTE_TYPE;

                // 时间日期类型
            case "DATE":
                return LocalTimeType.LOCAL_DATE_TYPE;

            case "TIME":
            case "TIME WITHOUT TIME ZONE":
                return LocalTimeType.LOCAL_TIME_TYPE;

            case "TIMESTAMP":
            case "TIMESTAMP WITHOUT TIME ZONE":
            case "TIMESTAMP WITH TIME ZONE":
            case "TIMESTAMPTZ":
                return LocalTimeType.LOCAL_DATE_TIME_TYPE;

                // 数值类型
            case "NUMERIC":
            case "DECIMAL":
                //                暂时只支持默认精度
                // 默认精度和小数位(
                int precision = 28;
                int scale = 18;
                // 如果字段配置中指定了精度和小数位，则使用指定值
                //                if (field.containsKey("precision")) {
                //                    precision =
                // Integer.parseInt(field.get("precision").toString());
                //                }
                //                if (field.containsKey("scale")) {
                //                    scale = Integer.parseInt(field.get("scale").toString());
                //                }
                return new DecimalType(precision, scale);

            default:
                throw new IllegalArgumentException("Unsupported type: " + typeStr);
        }
    }
}
