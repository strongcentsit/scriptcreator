package config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages default and custom column value overrides for INSERT and UPDATE operations.
 */
public class ColumnOverrideConfig {

    private static final Map<String, Object> GLOBAL_OVERRIDES = new LinkedHashMap<>();

    static {
        // Global Default Rules (Applied to ALL INSERTS/UPDATES across tables)
        GLOBAL_OVERRIDES.put("LAST_MODIFIED_TIME", "SYSDATE");   // Raw SQL function
        GLOBAL_OVERRIDES.put("LAST_MODIFIED_USER", "8778");      // Static user ID / value
        GLOBAL_OVERRIDES.put("LST_MODIFIED_USE", "8778");       // Alias variation
        GLOBAL_OVERRIDES.put("LAST_MODIFIED_BY", "8778");
        GLOBAL_OVERRIDES.put("MODIFIED_BY", "8778");
        GLOBAL_OVERRIDES.put("CREATED_USER", "8778");
        GLOBAL_OVERRIDES.put("ENTERED_BY", "8778");
        GLOBAL_OVERRIDES.put("LAST_MODIFIED_DATE", "SYSTIMESTAMP");
        GLOBAL_OVERRIDES.put("CREATED_DATE", "SYSTIMESTAMP");
        GLOBAL_OVERRIDES.put("LAST_MODIFIED", "SYSDATE");
        GLOBAL_OVERRIDES.put("CREATED_BY", "codegen");
        GLOBAL_OVERRIDES.put("LAST_MOD_DATE", "SYSDATE");
        GLOBAL_OVERRIDES.put("LAST_UPDATED", "SYSDATE");
        GLOBAL_OVERRIDES.put("MODIFIED_ON", "SYSTIMESTAMP");
    }

    private ColumnOverrideConfig() {}

    public static Map<String, Object> getGlobalOverrides() {
        return Collections.unmodifiableMap(GLOBAL_OVERRIDES);
    }
}