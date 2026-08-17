package config;

public enum SyncMode {
    /**
     * FULL DATA SYNC (Source -> Target):
     * 1. EXISTING RECORDS (R1): Retain Target ID (11). Update main record to source values.
     *    Delete old target children (ID=11), re-insert source children mapped to Target ID (11).
     * 2. NEW RECORDS (R2): Generate next available Target ID (32).
     *    Insert main record and child records using new ID (32).
     * 3. ORPHANED TARGET RECORDS (R3): Delete from Target (children first, then main record).
     */
    FULL_SYNC,

    /**
     * UPSERT / MERGE ONLY (Source -> Target):
     * Updates existing records and inserts new ones with generated IDs.
     * Leaves orphaned records in Target (R3) untouched.
     */
    UPSERT_ONLY,

    /**
     * DELETE REMOVED ONLY:
     * Only deletes records from Target that no longer exist in Source (R3).
     */
    DELETE_ORPHANS_ONLY,

    /**
     * SIMPLE INSERT MISSING RECORDS ONLY:
     * Compares source and target records for the configured table using business keys (or PKs).
     * Generates standard INSERT statements for records present in source but missing in target.
     * Keeps original source PK values intact (no auto-incrementing / re-indexing) and ignores child tables.
     */
    INSERT_MISSING_ONLY
}