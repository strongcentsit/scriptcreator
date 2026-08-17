package config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Global mapping configuration for truncated Excel sheet names -> Full Database Table names.
 */
public class TableNameMapper {

    private static final Map<MapKey, String> TABLE_MAPPINGS = new HashMap<>();

    private TableNameMapper() {}

    /**
     * Helper key class to support exact schema/environment targeting if needed,
     * or fallback to table-only mapping.
     */
    private static class MapKey {
        private final String sheetName;

        public MapKey(String sheetName) {
            this.sheetName = sheetName.toUpperCase().trim();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MapKey)) return false;
            MapKey mapKey = (MapKey) o;
            return sheetName.equals(mapKey.sheetName);
        }

        @Override
        public int hashCode() {
            return sheetName.hashCode();
        }
    }

    static {
        register("CALC_SCHEME_MARKUP_CROSS_COMPON", "CALC_SCHEME_MARKUP_CROSS_COMPONENT_CONDITION");
        register("CALC_SCHEME_MARKUP_CROSS_COMP_1", "CALC_SCHEME_MARKUP_CROSS_COMPONENT_PAX_RATE");
        register("CALC_SCHEME_MARKUP_CROSS_COMP_2",  "CALC_SCHEME_MARKUP_CROSS_COMPONENT_RATE");
    }

    /**
     * Add or override a table mapping dynamically.
     */
    public static void register(String sheetName, String actualTableName) {
        if (sheetName != null && actualTableName != null) {
            TABLE_MAPPINGS.put(new MapKey(sheetName), actualTableName.toUpperCase().trim());
        }
    }

    /**
     * Resolves the actual database table name.
     * If mapping exists -> Returns the mapped full table name.
     * If mapping NOT found -> Falls back to returning the sheet name itself.
     */
    public static String getTableName(String rawSheetName) {
        if (rawSheetName == null || rawSheetName.isBlank()) {
            return "";
        }

        // 1. Strip schema prefixes if present (e.g., "TBX.MY_SHEET_NAME" -> "MY_SHEET_NAME")
        String sanitizedSheet = rawSheetName.trim();
        if (sanitizedSheet.contains(".")) {
            sanitizedSheet = sanitizedSheet.substring(sanitizedSheet.lastIndexOf('.') + 1);
        }
        sanitizedSheet = sanitizedSheet.toUpperCase().trim();

        // 2. Check if explicit global mapping exists
        String mappedTableName = TABLE_MAPPINGS.get(new MapKey(sanitizedSheet));

        // 3. Fallback: If not found, treat the sheet name as the table name
        return (mappedTableName != null) ? mappedTableName : sanitizedSheet;
    }

    public static Map<MapKey, String> getAllMappings() {
        return Collections.unmodifiableMap(TABLE_MAPPINGS);
    }
}