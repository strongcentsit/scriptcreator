package com.strongcentsit.scriptcreator.metadata;

import java.util.*;

public class TableSchemaMetadata {
    private final String tableName;
    private final List<String> primaryKeys = new ArrayList<>();
    private final Set<String> columns = new LinkedHashSet<>();
    private final List<ForeignKeyMetadata> incomingForeignKeys = new ArrayList<>();
    private final List<ForeignKeyMetadata> outgoingForeignKeys = new ArrayList<>(); // Foreign keys pointing outward
    private final List<String> enabledTriggers = new ArrayList<>();

    public TableSchemaMetadata(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public Set<String> getColumns() {
        return columns;
    }

    public List<ForeignKeyMetadata> getIncomingForeignKeys() {
        return incomingForeignKeys;
    }

    public List<ForeignKeyMetadata> getOutgoingForeignKeys() {
        return outgoingForeignKeys;
    }

    public List<String> getEnabledTriggers() {
        return enabledTriggers;
    }

    public void addPrimaryKey(String pkColumn) {
        if (pkColumn != null && !pkColumn.isBlank() && !primaryKeys.contains(pkColumn.toUpperCase())) {
            primaryKeys.add(pkColumn.toUpperCase());
        }
    }

    public void addColumn(String column) {
        if (column != null && !column.isBlank()) {
            columns.add(column.toUpperCase());
        }
    }

    public void addIncomingForeignKey(ForeignKeyMetadata fk) {
        if (fk != null) {
            incomingForeignKeys.add(fk);
        }
    }

    public void addOutgoingForeignKey(ForeignKeyMetadata fk) {
        if (fk != null) {
            outgoingForeignKeys.add(fk);
        }
    }

    public void addEnabledTrigger(String triggerName) {
        if (triggerName != null && !triggerName.isBlank() && !enabledTriggers.contains(triggerName.toUpperCase())) {
            enabledTriggers.add(triggerName.toUpperCase());
        }
    }
}