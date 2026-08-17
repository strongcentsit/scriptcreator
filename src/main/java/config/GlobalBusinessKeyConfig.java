package config;

import java.util.*;

public class GlobalBusinessKeyConfig {

    private static final Map<String, List<String>> GLOBAL_BUSINESS_KEYS = new LinkedHashMap<>();

    private GlobalBusinessKeyConfig() {}

    static {
        register("RES_ADV_NOTE_TYPE", List.of("DESCRIPTION"));
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
}