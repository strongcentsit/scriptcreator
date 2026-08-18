package com.strongcentsit.scriptcreator.config;

import java.util.*;

/**
 * Registers which Oracle sequence feeds a given (table, column) primary key, so the
 * generator can work out how far each sequence needs to be restarted past whatever
 * this run inserts -- whether that value was freshly assigned (e.g. a main table's
 * new records) or copied straight through from source (e.g. RES_SETUP_ASSIGNMENTS'
 * own ASSIGNMENT_ID, which is never remapped).
 *
 * This is schema-level metadata, true regardless of which setups happen to be active,
 * so it lives here as a single global registry rather than being repeated per setup.
 */
public class SequenceConfig {

    // Map<"TABLE.COLUMN", sequenceName>
    private static final Map<String, String> SEQUENCES = new LinkedHashMap<>();

    static {
        register("RES_SETUP_ASSIGNMENTS", "ASSIGNMENT_ID", "RES_ASSIGNMENT_ID");
        register("RES_AMDCNX_RULE", "RULE_ID", "RES_AMDCNX_RULE_ID");
        register("RES_DEPOSIT_RULE", "RULE_ID", "RES_DEPOSIT_RULE_ID");
    }

    private SequenceConfig() {}

    public static void register(String table, String column, String sequenceName) {
        SEQUENCES.put(key(table, column), sequenceName);
    }

    public static String getSequenceName(String table, String column) {
        if (table == null || column == null) return null;
        return SEQUENCES.get(key(table, column));
    }

    public static Map<String, String> getAll() {
        return Collections.unmodifiableMap(SEQUENCES);
    }

    private static String key(String table, String column) {
        return table.toUpperCase().trim() + "." + column.toUpperCase().trim();
    }
}
