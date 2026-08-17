package com.strongcentsit.scriptcreator.config;

/**
 * Defines how SQL scripts should be generated for a setup.
 */
public enum GenerationMode {
    /**
     * Generates DELETE statements first (for children and main table),
     * followed by INSERT statements. Ensures clean re-insertion of records.
     */
    MERGE_EXISTING,

    /**
     * Generates DELETE statements only (bottom-up from children to main table).
     */
    DELETE_ONLY,

    /**
     * Generates INSERT statements only for new setups.
     */
    INSERT_ONLY
}