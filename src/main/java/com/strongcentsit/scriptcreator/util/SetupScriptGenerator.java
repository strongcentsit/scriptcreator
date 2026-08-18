package com.strongcentsit.scriptcreator.util;

import com.strongcentsit.scriptcreator.config.GlobalBusinessKeyConfig;
import com.strongcentsit.scriptcreator.config.SetupConfig;
import com.strongcentsit.scriptcreator.config.SetupConfigConstants;
import com.strongcentsit.scriptcreator.config.SyncMode;
import com.strongcentsit.scriptcreator.metadata.EntityNode;
import com.strongcentsit.scriptcreator.metadata.TableSchemaMetadata;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class SetupScriptGenerator {

    private SetupScriptGenerator() {
        throw new UnsupportedOperationException("Utility class should not be instantiated.");
    }

    // =========================================================================
    // MAIN GENERATION ENTRY POINTS
    // =========================================================================

    public static void generateAllSetups(
            Collection<SetupConfig> configs,
            Map<String, List<Map<String, Object>>> sourceDataMap,
            Map<String, List<Map<String, Object>>> targetDataMap,
            Map<String, TableSchemaMetadata> metadataMap,
            String outputFolderPath) {

        if (configs == null || configs.isEmpty()) return;

        // PRE-PASS 1: Build Source-to-Target PK Mapping using Global Business Keys
        Map<String, Map<Object, Object>> globalPkMap = alignSourcePrimaryKeysWithTarget(sourceDataMap, targetDataMap, metadataMap);

        // PRE-PASS 2: Remap Foreign Key columns using rules defined in SetupConfigs
        applyGlobalFkRemappings(configs, sourceDataMap, globalPkMap);

        SequenceTracker sequenceTracker = new SequenceTracker();

        for (SetupConfig config : configs) {
            generateScriptForSetup(config, sourceDataMap, targetDataMap, metadataMap, outputFolderPath, sequenceTracker);
        }

        writeSequenceUpdateScript(outputFolderPath, sequenceTracker);
    }

    public static void generateScriptForSetup(
            SetupConfig config,
            Map<String, List<Map<String, Object>>> sourceDataMap,
            Map<String, List<Map<String, Object>>> targetDataMap,
            Map<String, TableSchemaMetadata> metadataMap,
            String outputFolderPath,
            SequenceTracker sequenceTracker) {

        System.out.println("Processing setup: [" + config.getSetupName() + "]...");

        List<Map<String, Object>> sourceEntries = DataQueryUtils.query(sourceDataMap, config.getMainTable(), config.getConditions());
        List<Map<String, Object>> targetEntries = DataQueryUtils.query(targetDataMap, config.getMainTable(), config.getConditions());

        TableSchemaMetadata mainTableMeta = metadataMap.get(config.getMainTable());
        String pkColumn = (mainTableMeta != null && !mainTableMeta.getPrimaryKeys().isEmpty())
                ? mainTableMeta.getPrimaryKeys().get(0) : "ID";

        // Determine starting Primary Key sequence
        long maxId;
        if (config.getNextStartId() != null) {
            maxId = config.getNextStartId() - 1;
        } else {
            maxId = findMaxTargetId(targetDataMap, config.getMainTable(), pkColumn) + SetupConfigConstants.PK_SEQUENCE_OFFSET;
        }

        // Setup-Level Entry Matching
        Map<Map<String, Object>, Map<String, Object>> sourceToTarget = DataQueryUtils.matchEntries(
                sourceEntries, targetEntries, config.getBusinessKeyColumns(), sourceDataMap, targetDataMap, metadataMap, config.getMainTable());

        Map<Map<String, Object>, Map<String, Object>> targetToSource = DataQueryUtils.matchEntries(
                targetEntries, sourceEntries, config.getBusinessKeyColumns(), targetDataMap, sourceDataMap, metadataMap, config.getMainTable());

        StringBuilder mainSqlBuilder = new StringBuilder();
        Set<String> affectedTables = new LinkedHashSet<>();
        affectedTables.add(config.getMainTable());

        appendHeader(mainSqlBuilder, config);

        preCollectAffectedTables(config, sourceEntries, targetEntries, sourceDataMap, targetDataMap, metadataMap, affectedTables);

        if (sequenceTracker != null) sequenceTracker.seedForTables(affectedTables, targetDataMap);

        // Collect and disable triggers
        List<String> triggersToToggle = collectTriggersForTables(affectedTables, metadataMap);
        appendDisableTriggersSection(mainSqlBuilder, triggersToToggle);

        // =====================================================================
        // MODE: INSERT_MISSING_ONLY (Flat Single-Table Sync, Original PKs Preserved)
        // =====================================================================
        if (config.getSyncMode() == SyncMode.INSERT_MISSING_ONLY) {
            int missingCount = 0;

            for (Map.Entry<Map<String, Object>, Map<String, Object>> entry : sourceToTarget.entrySet()) {
                Map<String, Object> sourceRow = entry.getKey();
                Map<String, Object> targetRow = entry.getValue();

                if (targetRow == null) {
                    mainSqlBuilder.append("-- --- INSERT MISSING RECORD: ")
                            .append(buildKeySummary(sourceRow, config, sourceDataMap, metadataMap))
                            .append(" ---\n");

                    mainSqlBuilder.append(SqlScriptUtils.generateFlatInsertScript(
                            config.getMainTable(), sourceRow, config, sequenceTracker));
                    missingCount++;
                }
            }

            System.out.printf("  [INSERT_MISSING_ONLY] Setup '%s' (%s): Generated %d insert statements.\n",
                    config.getSetupName(), config.getMainTable(), missingCount);

            appendEnableTriggersSection(mainSqlBuilder, triggersToToggle);
            writeGeneratedFiles(outputFolderPath, config, mainSqlBuilder.toString(), affectedTables);
            return;
        }

        // =====================================================================
        // STEP 1: DELETE OR DISABLE REMOVED TARGET RECORDS
        // =====================================================================
        if (config.getSyncMode() == SyncMode.FULL_SYNC || config.getSyncMode() == SyncMode.DELETE_ORPHANS_ONLY) {
            targetToSource.forEach((targetRow, sourceRow) -> {
                if (sourceRow == null) {
                    Map<String, Object> targetOnlyProps = config.getTargetOnlyOverridesForTable(config.getMainTable());

                    if (!targetOnlyProps.isEmpty()) {
                        mainSqlBuilder.append("-- --- [1] DEACTIVATE / UPDATE REMOVED RECORD (Target Only): ")
                                .append(buildKeySummary(targetRow, config, targetDataMap, metadataMap)).append(" ---\n");

                        List<EntityNode> targetTrees = DataQueryUtils.getNestedTree(targetDataMap, List.of(targetRow), metadataMap, config.getMainTable());
                        if (!targetTrees.isEmpty()) {
                            mainSqlBuilder.append(SqlScriptUtils.generateTargetOnlyUpdateScript(targetTrees.get(0), metadataMap, targetOnlyProps, config));
                        }
                    } else {
                        mainSqlBuilder.append("-- --- [1] DELETE REMOVED RECORD (Target Only): ")
                                .append(buildKeySummary(targetRow, config, targetDataMap, metadataMap)).append(" ---\n");

                        List<EntityNode> targetTrees = DataQueryUtils.getNestedTree(targetDataMap, List.of(targetRow), metadataMap, config.getMainTable());
                        if (!targetTrees.isEmpty()) {
                            mainSqlBuilder.append(SqlScriptUtils.generateOrphanDeleteScript(targetTrees.get(0), metadataMap, config));
                        }
                    }
                }
            });
        }

        if (config.getSyncMode() == SyncMode.DELETE_ORPHANS_ONLY) {
            appendEnableTriggersSection(mainSqlBuilder, triggersToToggle);
            writeGeneratedFiles(outputFolderPath, config, mainSqlBuilder.toString(), affectedTables);
            return;
        }

        // =====================================================================
        // STEP 2: PROCESS EXISTING AND NEW RECORDS
        // =====================================================================
        for (Map.Entry<Map<String, Object>, Map<String, Object>> entry : sourceToTarget.entrySet()) {
            Map<String, Object> sourceRow = entry.getKey();
            Map<String, Object> targetRow = entry.getValue();

            if (targetRow != null) {
                // --- CASE A: UPDATE EXISTING RECORD ---
                Object originalSourcePk = sourceRow.get(pkColumn);
                Object targetPkValue = targetRow.get(pkColumn);

                mainSqlBuilder.append("-- --- [2] UPDATE EXISTING RECORD: ")
                        .append(buildKeySummary(sourceRow, config, sourceDataMap, metadataMap))
                        .append(" (Target PK: ").append(targetPkValue).append(") ---\n");

                List<EntityNode> targetTrees = DataQueryUtils.getNestedTree(targetDataMap, List.of(targetRow), metadataMap, config.getMainTable());
                if (!targetTrees.isEmpty()) {
                    EntityNode targetTree = targetTrees.get(0);
                    targetTree.getChildrenMap().forEach((childTable, childNodes) -> {
                        for (EntityNode child : childNodes) {
                            mainSqlBuilder.append(SqlScriptUtils.generateDeleteScript(child, metadataMap, config));
                        }
                    });
                }

                List<EntityNode> sourceTrees = DataQueryUtils.getNestedTree(sourceDataMap, List.of(sourceRow), metadataMap, config.getMainTable());
                if (!sourceTrees.isEmpty()) {
                    EntityNode sourceTree = sourceTrees.get(0);

                    Map<String, Object> remappedMainData = new LinkedHashMap<>(sourceRow);
                    remappedMainData.put(pkColumn, targetPkValue);
                    EntityNode remappedMainNode = new EntityNode(config.getMainTable(), remappedMainData);

                    mainSqlBuilder.append(SqlScriptUtils.generateUpdateScript(remappedMainNode, metadataMap, config));

                    sourceTree.getChildrenMap().forEach((childTable, childNodes) -> {
                        for (EntityNode child : childNodes) {
                            remapAndInsertChild(child, pkColumn, originalSourcePk, targetPkValue, mainSqlBuilder, config, sequenceTracker);
                        }
                    });
                }

            } else {
                // --- CASE B: INSERT NEW RECORD ---
                Object originalSourcePk = sourceRow.get(pkColumn);
                maxId++;
                Object newPkValue = maxId;

                mainSqlBuilder.append("-- --- [3] INSERT NEW RECORD: ")
                        .append(buildKeySummary(sourceRow, config, sourceDataMap, metadataMap))
                        .append(" (Assigned New PK: ").append(newPkValue).append(") ---\n");

                List<EntityNode> sourceTrees = DataQueryUtils.getNestedTree(sourceDataMap, List.of(sourceRow), metadataMap, config.getMainTable());
                if (!sourceTrees.isEmpty()) {
                    EntityNode sourceTree = sourceTrees.get(0);

                    Map<String, Object> newMainData = new LinkedHashMap<>(sourceRow);
                    newMainData.put(pkColumn, newPkValue);
                    EntityNode newMainNode = new EntityNode(config.getMainTable(), newMainData);

                    mainSqlBuilder.append(SqlScriptUtils.generateInsertScript(newMainNode, config, sequenceTracker));

                    sourceTree.getChildrenMap().forEach((childTable, childNodes) -> {
                        for (EntityNode child : childNodes) {
                            remapAndInsertChild(child, pkColumn, originalSourcePk, newPkValue, mainSqlBuilder, config, sequenceTracker);
                        }
                    });
                }
            }
        }

        appendEnableTriggersSection(mainSqlBuilder, triggersToToggle);
        writeGeneratedFiles(outputFolderPath, config, mainSqlBuilder.toString(), affectedTables);
    }

    // =========================================================================
    // PRE-PASS PK ALIGNMENT & GLOBAL FK REMAPPING METHODS
    // =========================================================================

    /**
     * Globally matches source records against target records using GlobalBusinessKeyConfig
     * and builds a Primary Key mapping (tableName -> Map<sourcePK, targetPK>).
     */
    private static Map<String, Map<Object, Object>> alignSourcePrimaryKeysWithTarget(
            Map<String, List<Map<String, Object>>> sourceDataMap,
            Map<String, List<Map<String, Object>>> targetDataMap,
            Map<String, TableSchemaMetadata> metadataMap) {

        Map<String, Map<Object, Object>> pkMappingPerTable = new LinkedHashMap<>();

        Map<String, List<String>> globalBusinessKeys = GlobalBusinessKeyConfig.getAllGlobalBusinessKeys();
        if (globalBusinessKeys.isEmpty()) {
            return pkMappingPerTable;
        }

        System.out.println("--- [PRE-PASS] Building Source-to-Target PK Mapping ---");

        globalBusinessKeys.forEach((tableName, businessKeyColumns) -> {
            List<Map<String, Object>> sourceRows = sourceDataMap.get(tableName);
            List<Map<String, Object>> targetRows = targetDataMap.get(tableName);

            if (sourceRows != null && !sourceRows.isEmpty() && targetRows != null && !targetRows.isEmpty()) {
                TableSchemaMetadata meta = metadataMap.get(tableName);
                String pkColumn = (meta != null && !meta.getPrimaryKeys().isEmpty())
                        ? meta.getPrimaryKeys().get(0) : "ID";

                Map<Map<String, Object>, Map<String, Object>> sourceToTarget = DataQueryUtils.matchEntries(
                        sourceRows, targetRows, businessKeyColumns, sourceDataMap, targetDataMap, metadataMap, tableName);

                Map<Object, Object> tablePkMap = new LinkedHashMap<>();

                for (Map.Entry<Map<String, Object>, Map<String, Object>> entry : sourceToTarget.entrySet()) {
                    Map<String, Object> sourceRow = entry.getKey();
                    Map<String, Object> targetRow = entry.getValue();

                    if (targetRow != null) {
                        Object sourcePkVal = getCaseInsensitiveValue(sourceRow, pkColumn);
                        Object targetPkVal = getCaseInsensitiveValue(targetRow, pkColumn);

                        if (sourcePkVal != null && targetPkVal != null) {
                            tablePkMap.put(sourcePkVal, targetPkVal);
                        }
                    }
                }

                if (!tablePkMap.isEmpty()) {
                    pkMappingPerTable.put(tableName, tablePkMap);
                    System.out.printf("  [MAPPING] Table '%s': Mapped %d source PKs to target PKs.\n", tableName, tablePkMap.size());
                }
            }
        });

        System.out.println("--- [PRE-PASS] Primary Key Mapping Completed ---\n");

        return pkMappingPerTable;
    }

    /**
     * Collects all globalFkMappings defined across provided SetupConfigs and updates
     * Foreign Key columns in sourceDataMap using globalPkMap.
     */
    private static void applyGlobalFkRemappings(
            Collection<SetupConfig> configs,
            Map<String, List<Map<String, Object>>> sourceDataMap,
            Map<String, Map<Object, Object>> globalPkMap) {

        if (globalPkMap.isEmpty()) return;

        Map<String, Set<String>> mergedFkConfig = new LinkedHashMap<>();
        for (SetupConfig config : configs) {
            Map<String, Set<String>> configFkMap = config.getGlobalFkMappings();
            if (configFkMap != null && !configFkMap.isEmpty()) {
                configFkMap.forEach((parentTable, childMappings) -> {
                    mergedFkConfig.computeIfAbsent(parentTable, k -> new LinkedHashSet<>()).addAll(childMappings);
                });
            }
        }

        if (mergedFkConfig.isEmpty()) return;

        System.out.println("--- [PRE-PASS] Remapping Foreign Keys in Source Data ---");

        mergedFkConfig.forEach((parentTable, childMappings) -> {
            Map<Object, Object> pkMapForParent = globalPkMap.get(parentTable);
            if (pkMapForParent != null && !pkMapForParent.isEmpty()) {

                for (String mapping : childMappings) {
                    String[] parts = mapping.split("\\.");
                    String childTable = parts[0];
                    String fkColumn = parts[1];

                    List<Map<String, Object>> childRows = sourceDataMap.get(childTable);
                    if (childRows != null && !childRows.isEmpty()) {
                        int remappedCount = 0;

                        for (Map<String, Object> row : childRows) {
                            Object currentFkVal = getCaseInsensitiveValue(row, fkColumn);

                            if (currentFkVal != null && pkMapForParent.containsKey(currentFkVal)) {
                                Object targetPkVal = pkMapForParent.get(currentFkVal);
                                putCaseInsensitiveValue(row, fkColumn, targetPkVal);
                                remappedCount++;
                            }
                        }

                        if (remappedCount > 0) {
                            System.out.printf("  [FK REMAP] Table '%s', Column '%s': Remapped %d foreign key references to target PKs of '%s'.\n",
                                    childTable, fkColumn, remappedCount, parentTable);
                        }
                    }
                }
            }
        });

        System.out.println("--- [PRE-PASS] Foreign Key Remapping Completed ---\n");
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static void remapAndInsertChild(
            EntityNode node,
            String pkColumn,
            Object oldPk,
            Object newPk,
            StringBuilder sql,
            SetupConfig config,
            SequenceTracker sequenceTracker) {

        Map<String, Object> remappedData = new LinkedHashMap<>(node.getData());

        remappedData.forEach((col, val) -> {
            if (val != null && val.equals(oldPk)) {
                remappedData.put(col, newPk);
            }
            if (col.equalsIgnoreCase(pkColumn) || col.equalsIgnoreCase("RULE_ID")) {
                remappedData.put(col, newPk);
            }
        });

        EntityNode remappedNode = new EntityNode(node.getTableName(), remappedData);
        sql.append(SqlScriptUtils.generateInsertScript(remappedNode, config, sequenceTracker));

        node.getChildrenMap().forEach((childTable, childNodes) -> {
            for (EntityNode child : childNodes) {
                remapAndInsertChild(child, pkColumn, oldPk, newPk, sql, config, sequenceTracker);
            }
        });
    }

    private static void writeGeneratedFiles(
            String outputFolderPath,
            SetupConfig config,
            String mainScriptSql,
            Set<String> affectedTables) {

        String baseFileName = config.getOutputFileName();
        if (baseFileName.endsWith(".sql")) {
            baseFileName = baseFileName.substring(0, baseFileName.length() - 4);
        }

        writeSqlToFile(outputFolderPath, baseFileName + ".sql", mainScriptSql);

        String backupSql = buildBackupScript(config, affectedTables);
        writeSqlToFile(outputFolderPath, baseFileName + "_BACKUP.sql", backupSql);

        String rollbackSql = buildRollbackScript(config, affectedTables);
        writeSqlToFile(outputFolderPath, baseFileName + "_ROLLBACK.sql", rollbackSql);
    }

    /**
     * Writes one combined sequence-restart script for the whole batch (not per setup),
     * since a sequence tied to a shared table (e.g. RES_SETUP_ASSIGNMENTS) can be
     * touched by several setups in the same run and only needs restarting once, past
     * the highest value any of them wrote.
     */
    private static void writeSequenceUpdateScript(String outputFolderPath, SequenceTracker sequenceTracker) {
        if (sequenceTracker == null || sequenceTracker.isEmpty()) return;
        writeSqlToFile(outputFolderPath, "Sequence_update.sql", sequenceTracker.buildRestartScript());
    }

    private static String buildBackupScript(SetupConfig config, Set<String> affectedTables) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, config);
        sb.append("-- ======================================================================\n")
                .append("--                      BACKUP GENERATION SCRIPT                         \n")
                .append("-- ======================================================================\n")
                .append("-- Run these statements BEFORE applying changes to create table backups:\n\n");

        for (String table : affectedTables) {
            String backupTableName = generateBackupTableName(table);
            sb.append("CREATE TABLE ").append(backupTableName).append(" AS SELECT * FROM ").append(table).append(";\n");
        }
        return sb.toString();
    }

    private static String buildRollbackScript(SetupConfig config, Set<String> affectedTables) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, config);
        sb.append("-- ======================================================================\n")
                .append("--                      ROLLBACK SCRIPT                                 \n")
                .append("-- ======================================================================\n")
                .append("-- Run these statements IF YOU NEED TO UNDO the applied changes:\n\n");

        List<String> tableList = new ArrayList<>(affectedTables);
        List<String> reversedTableList = new ArrayList<>(tableList);
        Collections.reverse(reversedTableList);

        sb.append("-- Step 1: Clean up current modified tables (children first)\n");
        for (String table : reversedTableList) {
            sb.append("DELETE FROM ").append(table).append(";\n");
        }
        sb.append("\n-- Step 2: Restore data from backup tables\n");
        for (String table : tableList) {
            String backupTableName = generateBackupTableName(table);
            sb.append("INSERT INTO ").append(table).append(" SELECT * FROM ").append(backupTableName).append(";\n");
        }
        sb.append("\n-- Step 3: Drop backup tables after restoration\n");
        for (String table : tableList) {
            String backupTableName = generateBackupTableName(table);
            sb.append("DROP TABLE ").append(backupTableName).append(" PURGE;\n");
        }
        return sb.toString();
    }

    private static long findMaxTargetId(Map<String, List<Map<String, Object>>> targetDataMap, String mainTable, String pkCol) {
        long max = 0;
        List<Map<String, Object>> allTargetRows = targetDataMap.get(mainTable);
        if (allTargetRows != null) {
            for (Map<String, Object> row : allTargetRows) {
                Object val = getCaseInsensitiveValue(row, pkCol);
                if (val instanceof Number) {
                    max = Math.max(max, ((Number) val).longValue());
                } else if (val != null) {
                    try {
                        max = Math.max(max, Long.parseLong(val.toString().trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return max;
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

    private static void putCaseInsensitiveValue(Map<String, Object> row, String key, Object value) {
        if (row == null) return;
        String matchedKey = key;
        for (String k : row.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                matchedKey = k;
                break;
            }
        }
        row.put(matchedKey, value);
    }

    private static void preCollectAffectedTables(
            SetupConfig config,
            List<Map<String, Object>> sourceEntries,
            List<Map<String, Object>> targetEntries,
            Map<String, List<Map<String, Object>>> sourceDataMap,
            Map<String, List<Map<String, Object>>> targetDataMap,
            Map<String, TableSchemaMetadata> metadataMap,
            Set<String> affectedTables) {

        List<EntityNode> sourceTrees = DataQueryUtils.getNestedTree(sourceDataMap, sourceEntries, metadataMap, config.getMainTable());
        List<EntityNode> targetTrees = DataQueryUtils.getNestedTree(targetDataMap, targetEntries, metadataMap, config.getMainTable());

        for (EntityNode node : sourceTrees) collectAffectedTables(node, affectedTables);
        for (EntityNode node : targetTrees) collectAffectedTables(node, affectedTables);
    }

    private static void collectAffectedTables(EntityNode node, Set<String> affectedTables) {
        if (node == null) return;
        affectedTables.add(node.getTableName());
        node.getChildrenMap().forEach((childTable, childNodes) -> {
            affectedTables.add(childTable);
            for (EntityNode child : childNodes) {
                collectAffectedTables(child, affectedTables);
            }
        });
    }

    private static List<String> collectTriggersForTables(
            Set<String> affectedTables,
            Map<String, TableSchemaMetadata> metadataMap) {

        List<String> triggers = new ArrayList<>();
        for (String table : affectedTables) {
            TableSchemaMetadata meta = metadataMap.get(table);
            if (meta != null && meta.getEnabledTriggers() != null) {
                triggers.addAll(meta.getEnabledTriggers());
            }
        }
        return triggers;
    }

    private static void appendDisableTriggersSection(StringBuilder sb, List<String> triggers) {
        if (triggers.isEmpty()) return;
        sb.append("-- ======================================================================\n")
                .append("--                      DISABLE AFFECTED TRIGGERS                        \n")
                .append("-- ======================================================================\n");
        for (String trg : triggers) {
            sb.append("ALTER TRIGGER ").append(trg).append(" DISABLE;\n");
        }
        sb.append("\n");
    }

    private static void appendEnableTriggersSection(StringBuilder sb, List<String> triggers) {
        if (triggers.isEmpty()) return;
        sb.append("\n");
        sb.append("COMMIT;");
        sb.append("\n");
        sb.append("-- ======================================================================\n")
                .append("--                      RE-ENABLE TRIGGERS                              \n")
                .append("-- ======================================================================\n");
        for (String trg : triggers) {
            sb.append("ALTER TRIGGER ").append(trg).append(" ENABLE;\n");
        }
        sb.append("\n");
    }

    private static String generateBackupTableName(String tableName) {
        String prefix = "B_";
        int maxTableLength = SetupConfigConstants.MAX_SQL_IDENTIFIER_LENGTH - prefix.length();
        String trimmedTable = (tableName.length() > maxTableLength) ? tableName.substring(0, maxTableLength) : tableName;
        return prefix + trimmedTable;
    }

    private static void appendHeader(StringBuilder sb, SetupConfig config) {
        sb.append("-- ======================================================================\n")
                .append("-- SETUP NAME : ").append(config.getSetupName()).append("\n")
                .append("-- MAIN TABLE : ").append(config.getMainTable()).append("\n")
                .append("-- SYNC MODE  : ").append(config.getSyncMode()).append("\n")
                .append("-- ======================================================================\n\n");
    }

    private static String buildKeySummary(
            Map<String, Object> row,
            SetupConfig config,
            Map<String, List<Map<String, Object>>> dataMap,
            Map<String, TableSchemaMetadata> metadataMap) {

        Map<String, Object> keyValues = DataQueryUtils.extractCompositeBusinessKey(
                row, config.getBusinessKeyColumns(), dataMap, metadataMap, config.getMainTable());

        StringBuilder sb = new StringBuilder("(");
        keyValues.forEach((k, v) -> sb.append(k).append("=").append(v).append(", "));
        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        return sb.append(")").toString();
    }

    private static void writeSqlToFile(String folderPath, String fileName, String content) {
        File dir = new File(folderPath);
        if (!dir.exists()) dir.mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(dir, fileName)))) {
            writer.print(content);
            System.out.println("  --> Generated: " + folderPath + fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}