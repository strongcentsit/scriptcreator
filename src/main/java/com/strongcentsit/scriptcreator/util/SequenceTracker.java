package com.strongcentsit.scriptcreator.util;

import com.strongcentsit.scriptcreator.config.SequenceConfig;

import java.util.*;

/**
 * Accumulates, across a batch of setup generations, the highest value seen for any
 * (table, column) that {@link SequenceConfig} maps to a sequence -- both the value
 * already present in the target table and every value the generated SQL writes into
 * that column (freshly assigned or copied through from source) -- so the batch can
 * end with a script that restarts each affected sequence past all of it.
 *
 * A sequence only appears in the output if a setup in this run actually touched its
 * table; see {@link #seedForTables}.
 */
public class SequenceTracker {

    private final Map<String, Long> maxValueBySequence = new LinkedHashMap<>();

    /**
     * Seeds the tracker with whatever's already in the target table, for every
     * configured sequence whose table appears in {@code tables}. Call this once per
     * setup with that setup's affected-tables set; it's cheap and idempotent to call
     * repeatedly (safe if multiple setups share a table, e.g. RES_SETUP_ASSIGNMENTS).
     */
    public void seedForTables(Set<String> tables, Map<String, List<Map<String, Object>>> targetDataMap) {
        if (tables == null || tables.isEmpty()) return;

        for (Map.Entry<String, String> entry : SequenceConfig.getAll().entrySet()) {
            String[] tableAndColumn = entry.getKey().split("\\.", 2);
            String table = tableAndColumn[0];
            if (!tables.contains(table)) continue;

            List<Map<String, Object>> rows = targetDataMap.get(table);
            if (rows == null) continue;

            for (Map<String, Object> row : rows) {
                observe(table, tableAndColumn[1], getCaseInsensitiveValue(row, tableAndColumn[1]));
            }
        }
    }

    /** Records a value about to be written into (table, column) by generated SQL, if a sequence is configured for it. */
    public void observe(String table, String column, Object value) {
        String sequenceName = SequenceConfig.getSequenceName(table, column);
        if (sequenceName == null || value == null) return;

        Long numeric = toLong(value);
        if (numeric == null) return;

        maxValueBySequence.merge(sequenceName, numeric, Math::max);
    }

    /**
     * Allocates a fresh value for the sequence backing (table, column) -- one past
     * whatever's the highest value seen for it so far, whether that's existing target
     * data (via {@link #seedForTables}) or another value already observed/allocated
     * earlier in this same run -- and remembers it as the new running max, so the next
     * allocation (for this or any other row sharing the sequence) starts past it too.
     * Returns null if no sequence is configured for (table, column); callers should
     * fall back to their normal behavior (e.g. copying the source value through) in
     * that case.
     */
    public Long nextValue(String table, String column) {
        String sequenceName = SequenceConfig.getSequenceName(table, column);
        if (sequenceName == null) return null;

        long next = maxValueBySequence.getOrDefault(sequenceName, 0L) + 1;
        maxValueBySequence.put(sequenceName, next);
        return next;
    }

    public boolean isEmpty() {
        return maxValueBySequence.isEmpty();
    }

    /** Builds `ALTER SEQUENCE ... RESTART START WITH ...;` for every sequence this run actually touched. */
    public String buildRestartScript() {
        if (maxValueBySequence.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("-- ======================================================================\n")
                .append("--                      SEQUENCE RESTART SCRIPT                          \n")
                .append("-- ======================================================================\n")
                .append("-- Run this AFTER the setup scripts above, so each sequence's NEXTVAL\n")
                .append("-- can't collide with a value this run inserted -- freshly allocated\n")
                .append("-- (via SequenceTracker.nextValue) or otherwise written by the generator.\n\n");

        for (Map.Entry<String, Long> entry : maxValueBySequence.entrySet()) {
            sb.append("ALTER SEQUENCE ").append(entry.getKey())
                    .append(" RESTART START WITH ").append(entry.getValue() + 1).append(";\n");
        }
        return sb.toString();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getCaseInsensitiveValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }
}
