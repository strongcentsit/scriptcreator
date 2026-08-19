package com.strongcentsit.scriptcreator.config;

import java.util.*;

/**
 * Registers which Oracle sequence feeds a given (table, column) primary key. This
 * serves two purposes in the generator:
 * <ol>
 *   <li>{@link com.strongcentsit.scriptcreator.util.SequenceTracker} uses it to work
 *       out how far each sequence needs to be restarted past whatever a run inserts.</li>
 *   <li>{@code SetupScriptGenerator.remapAndInsertChild} uses it to decide whether a
 *       child record's own primary key should be freshly allocated (via {@link
 *       com.strongcentsit.scriptcreator.util.SequenceTracker#nextValue}) instead of
 *       carried through from source as-is -- e.g. RES_SETUP_ASSIGNMENTS.ASSIGNMENT_ID,
 *       which has no other remapping mechanism and would otherwise risk colliding with
 *       an unrelated row that already has the same ID in the target table.</li>
 * </ol>
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
