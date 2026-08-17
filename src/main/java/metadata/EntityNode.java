package metadata;

import java.util.*;

/**
 * A user-friendly wrapper for a database record and its child records.
 */
public class EntityNode {
    private final String tableName;
    private final Map<String, Object> data;
    private final Map<String, List<EntityNode>> children = new LinkedHashMap<>();

    public EntityNode(String tableName, Map<String, Object> data) {
        this.tableName = tableName;
        this.data = data;
    }

    public String getTableName() {
        return tableName;
    }

    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Get a specific column value from this record.
     */
    public Object get(String columnName) {
        return data.get(columnName);
    }

    /**
     * Get all child records for a specific child table name (e.g., "MARKUP_VERSION").
     */
    public List<EntityNode> getChildren(String childTableName) {
        return children.getOrDefault(childTableName, Collections.emptyList());
    }

    /**
     * Add a child node under its child table name.
     */
    public void addChild(String childTableName, EntityNode childNode) {
        children.computeIfAbsent(childTableName, k -> new ArrayList<>()).add(childNode);
    }

    /**
     * Get all child tables attached to this record.
     */
    public Map<String, List<EntityNode>> getChildrenMap() {
        return children;
    }

    /**
     * Utility method to print the complete record hierarchy nicely formatted.
     */
    public void printTree(String indent) {
        System.out.println(indent + "[" + tableName + "] -> " + data);
        children.forEach((childTable, nodes) -> {
            System.out.println(indent + "  ├── Child Table: " + childTable);
            for (EntityNode child : nodes) {
                child.printTree(indent + "  │   ");
            }
        });
    }
}