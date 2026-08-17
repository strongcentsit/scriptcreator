package util;

import config.SetupConfig;
import metadata.EntityNode;
import metadata.TableSchemaMetadata;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class SqlScriptUtils {

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private SqlScriptUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated.");
    }

    // =========================================================================
    // 1. INSERT SCRIPT GENERATION
    // =========================================================================

    public static String generateInsertScript(EntityNode node, SetupConfig config) {
        StringBuilder sql = new StringBuilder();
        generateInsertRecursive(node, config, sql);
        return sql.toString();
    }

    private static void generateInsertRecursive(EntityNode node, SetupConfig config, StringBuilder sql) {
        if (node == null) return;

        String tableName = node.getTableName();

        if (config == null || !config.isOperationSkipped(tableName, "INSERT")) {
            Map<String, Object> data = node.getData();
            if (data != null && !data.isEmpty()) {
                List<String> columns = new ArrayList<>();
                List<String> values = new ArrayList<>();

                Map<String, Object> overrides = (config != null) ? config.getColumnOverrides() : Collections.emptyMap();

                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String col = entry.getKey();
                    Object val = entry.getValue();

                    Object finalVal = getOverrideValueIfPresent(col, val, overrides);

                    if (finalVal != null) {
                        columns.add(col);
                        values.add(formatSqlValue(finalVal));
                    }
                }

                sql.append("INSERT INTO ").append(tableName)
                        .append(" (").append(String.join(", ", columns)).append(")\n")
                        .append("VALUES (").append(String.join(", ", values)).append(");\n\n");
            }
        }

        node.getChildrenMap().forEach((childTable, childNodes) -> {
            for (EntityNode child : childNodes) {
                generateInsertRecursive(child, config, sql);
            }
        });
    }

    // =========================================================================
    // 2. UPDATE SCRIPT GENERATION
    // =========================================================================

    public static String generateUpdateScript(EntityNode node, Map<String, TableSchemaMetadata> metadataMap, SetupConfig config) {
        StringBuilder sql = new StringBuilder();
        generateUpdateRecursive(node, metadataMap, config, sql);
        return sql.toString();
    }

    private static void generateUpdateRecursive(EntityNode node, Map<String, TableSchemaMetadata> metadataMap, SetupConfig config, StringBuilder sql) {
        if (node == null) return;

        String tableName = node.getTableName();

        if (config == null || !config.isOperationSkipped(tableName, "UPDATE")) {
            Map<String, Object> data = node.getData();
            List<String> pkColumns = getPrimaryKeys(tableName, metadataMap);

            if (data != null && !data.isEmpty() && !pkColumns.isEmpty()) {
                List<String> setClauses = new ArrayList<>();
                Map<String, Object> overrides = (config != null) ? config.getColumnOverrides() : Collections.emptyMap();

                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String col = entry.getKey();
                    if (!containsIgnoreCase(pkColumns, col)) {
                        Object val = entry.getValue();
                        Object finalVal = getOverrideValueIfPresent(col, val, overrides);

                        setClauses.add(col + " = " + formatSqlValue(finalVal));
                    }
                }

                if (!setClauses.isEmpty()) {
                    String whereClause = buildWhereClause(data, pkColumns);
                    sql.append("UPDATE ").append(tableName).append("\n")
                            .append("SET ").append(String.join(", ", setClauses)).append("\n")
                            .append("WHERE ").append(whereClause).append(";\n\n");
                }
            }
        }

        node.getChildrenMap().forEach((childTable, childNodes) -> {
            for (EntityNode child : childNodes) {
                generateUpdateRecursive(child, metadataMap, config, sql);
            }
        });
    }

    // =========================================================================
    // 3. TARGET-ONLY RECORD UPDATE (DEACTIVATION / SOFT DELETE)
    // =========================================================================

    public static String generateTargetOnlyUpdateScript(
            EntityNode node,
            Map<String, TableSchemaMetadata> metadataMap,
            Map<String, Object> targetOnlyProperties,
            SetupConfig config) {

        if (node == null || targetOnlyProperties == null || targetOnlyProperties.isEmpty()) {
            return "";
        }

        String tableName = node.getTableName();
        if (config != null && config.isOperationSkipped(tableName, "UPDATE")) {
            return "";
        }

        StringBuilder sql = new StringBuilder();
        Map<String, Object> data = node.getData();
        List<String> pkColumns = getPrimaryKeys(tableName, metadataMap);

        if (data != null && !data.isEmpty() && !pkColumns.isEmpty()) {
            List<String> setClauses = new ArrayList<>();

            for (Map.Entry<String, Object> entry : targetOnlyProperties.entrySet()) {
                setClauses.add(entry.getKey() + " = " + formatSqlValue(entry.getValue()));
            }

            if (!setClauses.isEmpty()) {
                String whereClause = buildWhereClause(data, pkColumns);
                sql.append("UPDATE ").append(tableName).append("\n")
                        .append("SET ").append(String.join(", ", setClauses)).append("\n")
                        .append("WHERE ").append(whereClause).append(";\n\n");
            }
        }

        return sql.toString();
    }

    // =========================================================================
    // 4. DELETE SCRIPT GENERATION
    // =========================================================================

    public static String generateDeleteScript(EntityNode node, Map<String, TableSchemaMetadata> metadataMap, SetupConfig config) {
        StringBuilder sql = new StringBuilder();
        generateDeleteRecursive(node, metadataMap, config, sql);
        return sql.toString();
    }

    private static void generateDeleteRecursive(EntityNode node, Map<String, TableSchemaMetadata> metadataMap, SetupConfig config, StringBuilder sql) {
        if (node == null) return;

        node.getChildrenMap().forEach((childTable, childNodes) -> {
            for (EntityNode child : childNodes) {
                generateDeleteRecursive(child, metadataMap, config, sql);
            }
        });

        String tableName = node.getTableName();

        if (config == null || !config.isOperationSkipped(tableName, "DELETE")) {
            Map<String, Object> data = node.getData();
            List<String> pkColumns = getPrimaryKeys(tableName, metadataMap);

            if (data != null && !data.isEmpty() && !pkColumns.isEmpty()) {
                String whereClause = buildWhereClause(data, pkColumns);
                sql.append("DELETE FROM ").append(tableName)
                        .append(" WHERE ").append(whereClause).append(";\n\n");
            }
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static Object getOverrideValueIfPresent(String colName, Object originalVal, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) return originalVal;

        for (Map.Entry<String, Object> override : overrides.entrySet()) {
            if (override.getKey().equalsIgnoreCase(colName)) {
                return override.getValue();
            }
        }
        return originalVal;
    }


    // =========================================================================
    // FLAT SINGLE-TABLE INSERT (NO CHILDREN)
    // =========================================================================

    public static String generateFlatInsertScript(
            String tableName,
            Map<String, Object> rowData,
            SetupConfig config) {

        if (rowData == null || rowData.isEmpty()) return "";

        // Wrap row in a standalone node with no children attached
        EntityNode singleNode = new EntityNode(tableName, rowData);
        StringBuilder sql = new StringBuilder();

        Map<String, Object> overrides = (config != null) ? config.getColumnOverrides() : Collections.emptyMap();

        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : singleNode.getData().entrySet()) {
            String col = entry.getKey();
            Object val = entry.getValue();

            Object finalVal = getOverrideValueIfPresent(col, val, overrides);

            if (finalVal != null) {
                columns.add(col);
                values.add(formatSqlValue(finalVal));
            }
        }

        if (!columns.isEmpty()) {
            sql.append("INSERT INTO ").append(tableName)
                    .append(" (").append(String.join(", ", columns)).append(")\n")
                    .append("VALUES (").append(String.join(", ", values)).append(");\n\n");
        }

        return sql.toString();
    }


    private static List<String> getPrimaryKeys(String tableName, Map<String, TableSchemaMetadata> metadataMap) {
        TableSchemaMetadata meta = metadataMap.get(tableName.toUpperCase());
        if (meta != null && meta.getPrimaryKeys() != null && !meta.getPrimaryKeys().isEmpty()) {
            return meta.getPrimaryKeys().stream().map(String::toUpperCase).collect(Collectors.toList());
        }
        return List.of("ID");
    }

    private static String buildWhereClause(Map<String, Object> data, List<String> pkColumns) {
        List<String> whereConditions = new ArrayList<>();
        for (String pk : pkColumns) {
            Object val = getCaseInsensitiveValue(data, pk);
            whereConditions.add(pk + " = " + formatSqlValue(val));
        }
        return String.join(" AND ", whereConditions);
    }

    private static Object getCaseInsensitiveValue(Map<String, Object> data, String key) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        return list.stream().anyMatch(item -> item.equalsIgnoreCase(value));
    }

    private static String formatSqlValue(Object value) {
        if (value == null) return "NULL";

        String strVal = value.toString().trim();

        if (isRawSqlExpression(strVal)) {
            return strVal.toUpperCase();
        }

        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return ((Boolean) value) ? "1" : "0";
        if (value instanceof Date) return "TO_DATE('" + DATE_FORMATTER.format((Date) value) + "', 'YYYY-MM-DD HH24:MI:SS')";

        String valStr = strVal.replace("'", "''");
        return "'" + valStr + "'";
    }

    private static boolean isRawSqlExpression(String val) {
        if (val == null) return false;
        String upper = val.toUpperCase();
        return upper.equals("SYSDATE")
                || upper.equals("SYSTIMESTAMP")
                || upper.equals("CURRENT_TIMESTAMP")
                || upper.equals("NULL")
                || upper.startsWith("TO_DATE(");
    }
}