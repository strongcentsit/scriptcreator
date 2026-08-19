package com.strongcentsit.scriptcreator.config;

import java.util.*;

/**
 * Two related global registries, both applied once per run (in
 * {@code SetupScriptGenerator.generateAllSetups}) before any setup's SQL is
 * generated, regardless of which setups are active:
 * <ol>
 *   <li>{@link #register}: business keys for lookup tables that aren't a setup's own
 *       main table, used to align a source row to its target row by something more
 *       stable than a raw ID (e.g. PRODUCT_COMBINATION's NAME) and build a
 *       source-PK -> target-PK map.</li>
 *   <li>{@link #registerChildMapping}: declares that a child table's column holds a
 *       code from one of those lookup tables, so its value should be rewritten from
 *       the source-side code to the target-side code -- using the PK map from (1) --
 *       before any setup reads that child table's data. E.g.
 *       RES_SETUP_ASSIGNMENTS.PRODUCT_COMBINATION holds a PRODUCT_COMBINATION code
 *       that isn't a declared foreign key (see fk.xlsx) and can differ between
 *       environments for the same logical value.</li>
 * </ol>
 * Both apply as a common, unconditional pre-processing step for the whole batch --
 * unlike {@code SetupConfig.globalFkMappings}, which only takes effect for the
 * specific setups that declare it.
 */
public class GlobalBusinessKeyConfig {

    private static final Map<String, List<String>> GLOBAL_BUSINESS_KEYS = new LinkedHashMap<>();

    // Map<"CHILD_TABLE.CHILD_COLUMN", parentTable>
    private static final Map<String, String> CHILD_COLUMN_MAPPINGS = new LinkedHashMap<>();

    private GlobalBusinessKeyConfig() {}

    static {
        register("RES_ADV_NOTE_TYPE", List.of("DESCRIPTION"));
        register("PRODUCT_COMBINATION", List.of("NAME"));

        registerChildMapping("RES_SETUP_ASSIGNMENTS", "PRODUCT_COMBINATION", "PRODUCT_COMBINATION");
    }

    public static void register(String tableName, List<String> businessKeys) {
        if (tableName != null && businessKeys != null && !businessKeys.isEmpty()) {
            GLOBAL_BUSINESS_KEYS.put(tableName.toUpperCase().trim(), Collections.unmodifiableList(businessKeys));
        }
    }

    public static List<String> getBusinessKeys(String tableName) {
        if (tableName == null) return Collections.emptyList();
        return GLOBAL_BUSINESS_KEYS.getOrDefault(tableName.toUpperCase().trim(), Collections.emptyList());
    }

    public static Map<String, List<String>> getAllGlobalBusinessKeys() {
        return Collections.unmodifiableMap(GLOBAL_BUSINESS_KEYS);
    }

    /**
     * Registers that {@code childTable.childColumn} holds a code from {@code
     * parentTable}, to be remapped from the source-side value to the target-side
     * value before any setup runs. {@code parentTable} must also be registered via
     * {@link #register} (with whichever column identifies "the same row" across
     * environments) -- without a business key, there's no way to know which source
     * row corresponds to which target row.
     */
    public static void registerChildMapping(String childTable, String childColumn, String parentTable) {
        if (childTable != null && childColumn != null && parentTable != null) {
            String key = childTable.toUpperCase().trim() + "." + childColumn.toUpperCase().trim();
            CHILD_COLUMN_MAPPINGS.put(key, parentTable.toUpperCase().trim());
        }
    }

    public static Map<String, String> getAllChildColumnMappings() {
        return Collections.unmodifiableMap(CHILD_COLUMN_MAPPINGS);
    }
}
