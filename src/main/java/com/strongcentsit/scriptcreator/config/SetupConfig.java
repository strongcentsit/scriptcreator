package com.strongcentsit.scriptcreator.config;

import java.util.*;

public class SetupConfig {

    private final String setupName;
    private final String mainTable;
    private final Map<String, Object> conditions;
    private final List<String> businessKeyColumns;
    private final SyncMode syncMode;
    private final Long nextStartId;
    private final boolean enabled;
    private final Map<String, Object> columnOverrides;
    private final Map<String, Map<String, Object>> targetOnlyOverrides;
    private final Map<String, Set<String>> tableOperationExclusions;

    // NEW: Foreign key remappings defined at SetupConfig level
    // Map<ParentTableName, Set<ChildTableAndColumn>> e.g., "RES_ADV_NOTE_TYPE" -> Set("RES_ADV_NOTE.TYPE")
    private final Map<String, Set<String>> globalFkMappings;

    private SetupConfig(Builder builder) {
        this.setupName = builder.setupName;
        this.mainTable = builder.mainTable;
        this.conditions = builder.conditions != null ? builder.conditions : Map.of();
        this.businessKeyColumns = builder.businessKeyColumns != null ? builder.businessKeyColumns : List.of();
        this.syncMode = builder.syncMode != null ? builder.syncMode : SyncMode.FULL_SYNC;
        this.nextStartId = builder.nextStartId;
        this.enabled = builder.enabled;

        // Merge global overrides with custom overrides
        Map<String, Object> mergedOverrides = new LinkedHashMap<>(ColumnOverrideConfig.getGlobalOverrides());
        if (builder.customOverrides != null) {
            mergedOverrides.putAll(builder.customOverrides);
        }
        this.columnOverrides = Collections.unmodifiableMap(mergedOverrides);

        // Target-only overrides
        Map<String, Map<String, Object>> normalizedTargetOnly = new LinkedHashMap<>();
        if (builder.targetOnlyOverrides != null) {
            builder.targetOnlyOverrides.forEach((table, map) -> {
                if (table != null && map != null) {
                    normalizedTargetOnly.put(table.toUpperCase().trim(), map);
                }
            });
        }
        this.targetOnlyOverrides = Collections.unmodifiableMap(normalizedTargetOnly);

        // Table Operation Exclusions
        Map<String, Set<String>> normalizedExclusions = new LinkedHashMap<>();
        if (builder.tableOperationExclusions != null) {
            builder.tableOperationExclusions.forEach((table, operations) -> {
                if (table != null && operations != null) {
                    Set<String> upperOps = new HashSet<>();
                    for (String op : operations) {
                        if (op != null) upperOps.add(op.toUpperCase().trim());
                    }
                    normalizedExclusions.put(table.toUpperCase().trim(), Collections.unmodifiableSet(upperOps));
                }
            });
        }
        this.tableOperationExclusions = Collections.unmodifiableMap(normalizedExclusions);

        // FK Remappings
        Map<String, Set<String>> normalizedFkMappings = new LinkedHashMap<>();
        if (builder.globalFkMappings != null) {
            builder.globalFkMappings.forEach((parentTable, mappings) -> {
                if (parentTable != null && mappings != null) {
                    Set<String> cleanMappings = new LinkedHashSet<>();
                    for (String m : mappings) {
                        if (m != null && m.contains(".")) {
                            cleanMappings.add(m.toUpperCase().trim());
                        }
                    }
                    if (!cleanMappings.isEmpty()) {
                        normalizedFkMappings.put(parentTable.toUpperCase().trim(), Collections.unmodifiableSet(cleanMappings));
                    }
                }
            });
        }
        this.globalFkMappings = Collections.unmodifiableMap(normalizedFkMappings);
    }

    public String getSetupName() { return setupName; }
    public String getMainTable() { return mainTable; }
    public Map<String, Object> getConditions() { return conditions; }
    public List<String> getBusinessKeyColumns() { return businessKeyColumns; }
    public SyncMode getSyncMode() { return syncMode; }
    public Long getNextStartId() { return nextStartId; }
    public boolean isEnabled() { return enabled; }
    public Map<String, Object> getColumnOverrides() { return columnOverrides; }
    public Map<String, Map<String, Object>> getTargetOnlyOverrides() { return targetOnlyOverrides; }
    public Map<String, Set<String>> getTableOperationExclusions() { return tableOperationExclusions; }
    public Map<String, Set<String>> getGlobalFkMappings() { return globalFkMappings; }

    public Map<String, Object> getTargetOnlyOverridesForTable(String tableName) {
        if (tableName == null) return Collections.emptyMap();
        return targetOnlyOverrides.getOrDefault(tableName.toUpperCase().trim(), Collections.emptyMap());
    }

    public boolean isOperationSkipped(String tableName, String operation) {
        if (tableName == null || operation == null) return false;

        Set<String> excludedOps = tableOperationExclusions.get(tableName.toUpperCase().trim());
        if (excludedOps == null || excludedOps.isEmpty()) return false;

        String opUpper = operation.toUpperCase().trim();
        return excludedOps.contains("ALL") || excludedOps.contains(opUpper);
    }

    public String getOutputFileName() {
        return setupName.replaceAll("[^a-zA-Z0-9\\-_]", "_").replaceAll("_+", "_") + ".sql";
    }

    public static class Builder {
        private final String setupName;
        private final String mainTable;
        private Map<String, Object> conditions = Map.of();
        private List<String> businessKeyColumns = List.of();
        private SyncMode syncMode = SyncMode.FULL_SYNC;
        private Long nextStartId;
        private boolean enabled = true;
        private Map<String, Object> customOverrides = Map.of();
        private Map<String, Map<String, Object>> targetOnlyOverrides = Map.of();
        private Map<String, Set<String>> tableOperationExclusions = Map.of();
        private Map<String, Set<String>> globalFkMappings = Map.of();

        public Builder(String setupName, String mainTable) {
            if (setupName == null || setupName.isBlank()) {
                throw new IllegalArgumentException("setupName cannot be null or blank");
            }
            if (mainTable == null || mainTable.isBlank()) {
                throw new IllegalArgumentException("mainTable cannot be null or blank");
            }
            this.setupName = setupName;
            this.mainTable = mainTable;
        }

        public Builder conditions(Map<String, Object> conditions) {
            if (conditions != null) this.conditions = conditions;
            return this;
        }

        public Builder businessKeyColumns(List<String> businessKeyColumns) {
            if (businessKeyColumns != null) this.businessKeyColumns = businessKeyColumns;
            return this;
        }

        public Builder syncMode(SyncMode syncMode) {
            if (syncMode != null) this.syncMode = syncMode;
            return this;
        }

        public Builder nextStartId(Long nextStartId) {
            this.nextStartId = nextStartId;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder customOverrides(Map<String, Object> customOverrides) {
            if (customOverrides != null) this.customOverrides = customOverrides;
            return this;
        }

        public Builder targetOnlyOverrides(Map<String, Map<String, Object>> targetOnlyOverrides) {
            if (targetOnlyOverrides != null) this.targetOnlyOverrides = targetOnlyOverrides;
            return this;
        }

        public Builder tableOperationExclusions(Map<String, Set<String>> tableOperationExclusions) {
            if (tableOperationExclusions != null) this.tableOperationExclusions = tableOperationExclusions;
            return this;
        }

        /**
         * Configures foreign key remappings for this setup.
         *
         * @param globalFkMappings Map of Parent Table -> Set of "CHILD_TABLE.COLUMN_NAME" references
         * E.g. Map.of("RES_ADV_NOTE_TYPE", Set.of("RES_ADV_NOTE.TYPE"))
         */
        public Builder globalFkMappings(Map<String, Set<String>> globalFkMappings) {
            if (globalFkMappings != null) this.globalFkMappings = globalFkMappings;
            return this;
        }

        public SetupConfig build() {
            return new SetupConfig(this);
        }
    }
}