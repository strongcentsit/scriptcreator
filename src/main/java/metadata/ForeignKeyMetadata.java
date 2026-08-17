package metadata;

import java.util.*;

/**
 * Represents a single Foreign Key constraint between two tables.
 * Supports single-column and composite (multi-column) foreign keys.
 */
public class ForeignKeyMetadata {
    private final String fkName;
    private final String childTable;
    private final String parentTable;
    private final List<String> childColumns = new ArrayList<>();
    private final List<String> parentColumns = new ArrayList<>();

    public ForeignKeyMetadata(String fkName, String childTable, String parentTable) {
        this.fkName = fkName;
        this.childTable = childTable;
        this.parentTable = parentTable;
    }

    public void addColumnMapping(String childColumn, String parentColumn) {
        this.childColumns.add(childColumn);
        this.parentColumns.add(parentColumn);
    }

    public String getFkName() { return fkName; }
    public String getChildTable() { return childTable; }
    public String getParentTable() { return parentTable; }
    public List<String> getChildColumns() { return childColumns; }
    public List<String> getParentColumns() { return parentColumns; }

    /**
     * Helper to get column mapping as Map<ChildColumn, ParentColumn>
     */
    public Map<String, String> getColumnMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (int i = 0; i < childColumns.size(); i++) {
            mappings.put(childColumns.get(i), parentColumns.get(i));
        }
        return mappings;
    }

    /**
     * Build query conditions from a parent row map for composite join.
     */
    public Map<String, Object> buildQueryCondition(Map<String, Object> parentRow) {
        Map<String, Object> condition = new LinkedHashMap<>();
        for (int i = 0; i < childColumns.size(); i++) {
            String childCol = childColumns.get(i);
            String parentCol = parentColumns.get(i);

            // Case-insensitive retrieval from parent row
            Object parentVal = getCaseInsensitiveValue(parentRow, parentCol);
            condition.put(childCol, parentVal);
        }
        return condition;
    }

    private Object getCaseInsensitiveValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}