package util;

import metadata.EntityNode;
import metadata.ForeignKeyMetadata;
import metadata.TableSchemaMetadata;

import java.util.*;

public class DataQueryUtils {

    private DataQueryUtils() {}

    public static Map<Map<String, Object>, Map<String, Object>> matchEntries(
            List<Map<String, Object>> primaryEntries,
            List<Map<String, Object>> candidateEntries,
            List<String> businessKeyColumns,
            Map<String, List<Map<String, Object>>> primaryDataMap,
            Map<String, List<Map<String, Object>>> candidateDataMap,
            Map<String, TableSchemaMetadata> metadataMap,
            String mainTable) {

        Map<Map<String, Object>, Map<String, Object>> matchedMap = new LinkedHashMap<>();

        for (Map<String, Object> primaryRow : primaryEntries) {
            Map<String, Object> primaryKeyValues = extractCompositeBusinessKey(
                    primaryRow, businessKeyColumns, primaryDataMap, metadataMap, mainTable);

            Map<String, Object> bestMatch = null;

            for (Map<String, Object> candidateRow : candidateEntries) {
                Map<String, Object> candidateKeyValues = extractCompositeBusinessKey(
                        candidateRow, businessKeyColumns, candidateDataMap, metadataMap, mainTable);

                if (!primaryKeyValues.isEmpty() && primaryKeyValues.equals(candidateKeyValues)) {
                    bestMatch = candidateRow;
                    break;
                }
            }

            matchedMap.put(primaryRow, bestMatch);
        }

        return matchedMap;
    }

    public static Map<String, Object> extractCompositeBusinessKey(
            Map<String, Object> parentRow,
            List<String> businessKeyColumns,
            Map<String, List<Map<String, Object>>> dataMap,
            Map<String, TableSchemaMetadata> metadataMap,
            String parentTable) {

        Map<String, Object> keyValues = new LinkedHashMap<>();
        if (businessKeyColumns == null || businessKeyColumns.isEmpty()) return keyValues;

        for (String keyCol : businessKeyColumns) {
            if (keyCol.contains(".")) {
                String[] parts = keyCol.split("\\.");
                String childTable = parts[0].trim();
                String targetColumn = parts[1].trim();

                Object value = fetchValueFromChildTable(parentRow, parentTable, childTable, targetColumn, dataMap, metadataMap);
                keyValues.put(keyCol, value);
            } else {
                Object value = getCaseInsensitiveValue(parentRow, keyCol);
                keyValues.put(keyCol, value);
            }
        }

        return keyValues;
    }

    private static Object fetchValueFromChildTable(
            Map<String, Object> parentRow,
            String parentTable,
            String targetTable,
            String targetColumn,
            Map<String, List<Map<String, Object>>> dataMap,
            Map<String, TableSchemaMetadata> metadataMap) {

        TableSchemaMetadata parentMeta = metadataMap.get(parentTable);
        if (parentMeta == null) return null;

        // 1. INCOMING FK: Target Table is a CHILD/DETAIL of Parent Table
        // E.g., Parent = CALC_SCHEME, Target = CALC_DOCUMENT_SCHEME
        for (ForeignKeyMetadata fk : parentMeta.getIncomingForeignKeys()) {
            if (fk.getChildTable().equalsIgnoreCase(targetTable)) {
                Map<String, Object> condition = fk.buildQueryCondition(parentRow);
                List<Map<String, Object>> childRows = query(dataMap, targetTable, condition);

                if (!childRows.isEmpty()) {
                    return getCaseInsensitiveValue(childRows.get(0), targetColumn);
                }
            }
        }

        // 2. OUTGOING FK: Target Table is a PARENT/LOOKUP of Parent Table
        // E.g., Parent = CALC_SCHEME, Target = STAGE
        for (ForeignKeyMetadata fk : parentMeta.getOutgoingForeignKeys()) {
            if (fk.getParentTable().equalsIgnoreCase(targetTable)) {
                Map<String, Object> condition = new LinkedHashMap<>();
                for (Map.Entry<String, String> colMap : fk.getColumnMappings().entrySet()) {
                    String childColInParentRow = colMap.getKey();   // Column in Parent Row (child table)
                    String targetPkCol = colMap.getValue();         // Column in Target Table (parent table)

                    Object fkValue = getCaseInsensitiveValue(parentRow, childColInParentRow);
                    condition.put(targetPkCol, fkValue);
                }

                List<Map<String, Object>> targetRows = query(dataMap, targetTable, condition);
                if (!targetRows.isEmpty()) {
                    return getCaseInsensitiveValue(targetRows.get(0), targetColumn);
                }
            }
        }

        return null;
    }

    public static List<Map<String, Object>> query(
            Map<String, List<Map<String, Object>>> dataMap,
            String tableName,
            Map<String, Object> conditions) {

        List<Map<String, Object>> tableRows = dataMap.get(tableName);
        if (tableRows == null || tableRows.isEmpty() || conditions == null || conditions.isEmpty()) {
            return tableRows == null ? Collections.emptyList() : tableRows;
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : tableRows) {
            boolean matches = true;
            for (Map.Entry<String, Object> cond : conditions.entrySet()) {
                Object rowVal = getCaseInsensitiveValue(row, cond.getKey());
                if (rowVal == null || !rowVal.toString().equals(cond.getValue().toString())) {
                    matches = false;
                    break;
                }
            }
            if (matches) filtered.add(row);
        }
        return filtered;
    }

    public static List<EntityNode> getNestedTree(
            Map<String, List<Map<String, Object>>> dataMap,
            List<Map<String, Object>> parentEntries,
            Map<String, TableSchemaMetadata> metadataMap,
            String parentTable) {

        List<EntityNode> rootNodes = new ArrayList<>();
        if (parentEntries == null || parentEntries.isEmpty() || metadataMap == null) return rootNodes;

        TableSchemaMetadata parentMetadata = metadataMap.get(parentTable);
        List<ForeignKeyMetadata> incomingFKs = (parentMetadata != null)
                ? parentMetadata.getIncomingForeignKeys() : Collections.emptyList();

        for (Map<String, Object> parentRow : parentEntries) {
            EntityNode parentNode = new EntityNode(parentTable, parentRow);

            for (ForeignKeyMetadata fk : incomingFKs) {
                String childTable = fk.getChildTable();
                Map<String, Object> compositeCondition = fk.buildQueryCondition(parentRow);

                if (!compositeCondition.containsValue(null) && dataMap.containsKey(childTable)) {
                    List<Map<String, Object>> childRows = query(dataMap, childTable, compositeCondition);
                    if (!childRows.isEmpty()) {
                        List<EntityNode> childNodes = getNestedTree(dataMap, childRows, metadataMap, childTable);
                        for (EntityNode childNode : childNodes) {
                            parentNode.addChild(childTable, childNode);
                        }
                    }
                }
            }
            rootNodes.add(parentNode);
        }
        return rootNodes;
    }

    private static Object getCaseInsensitiveValue(Map<String, Object> row, String key) {
        if (row == null) return null;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}