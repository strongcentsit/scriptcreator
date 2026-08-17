package config;

import java.util.*;

public class SetupRegistry {

    private static final Map<String, SetupConfig> SETUP_CATALOG = new LinkedHashMap<>();

    // Specify the exact setups you want to execute here.
    // If empty, getAllActive() will return ALL registered setups!
    private static final Set<String> ACTIVE_SETUPS = Set.of(
//            "Finance - Option Rules"
            "Finance - Deposit Rules"
//            "Finance - Amd Cnx rules"
    );

    static {
        // --- ALL SETUPS DEFINED HERE (NO COMMENTED-OUT CODE NEEDED) ---

        register(new SetupConfig.Builder("Accounts - Single Use CC Eligibility", "SUCC_ELIGIBILITY_RULE")
                .businessKeyColumns(List.of("NAME"))
                .syncMode(SyncMode.FULL_SYNC)
                .build());

        register(new SetupConfig.Builder("Document - Document Rules", "CALC_SCHEME")
                .conditions(Map.of("ACTIVE", 1, "SCHEME_TYPE", "D"))
                .businessKeyColumns(List.of("CALC_DOCUMENT_SCHEME.DOC_TYPE", "CALC_DOCUMENT_SCHEME.DOC_TEMPLATE"))
                .syncMode(SyncMode.FULL_SYNC)
                .targetOnlyOverrides(Map.of("CALC_SCHEME", Map.of("ACTIVE", 0)))
                .build());

        register(new SetupConfig.Builder("Finance - Rounding Rules Setup", "ROUNDING_RULE")
                .businessKeyColumns(List.of("RULE_ID"))
                .syncMode(SyncMode.FULL_SYNC)
                .build());

        register(new SetupConfig.Builder("Reservation - Tolerance setup", "RATE_TOLERANCE_RULE")
                .businessKeyColumns(List.of("RULE_ID"))
                .syncMode(SyncMode.FULL_SYNC)
                .build());

        register(new SetupConfig.Builder("Finance - Local Fee Schemes", "CALC_SCHEME")
                .conditions(Map.of("SCHEME_TYPE", "L"))
                .businessKeyColumns(List.of("CALC_SCHEME_FEES_AND_TAXES.FEE_TYPE", "CALC_SCHEME_FEES_AND_TAXES.FEE_CATEGORY"))
                .syncMode(SyncMode.FULL_SYNC)
                .targetOnlyOverrides(Map.of("CALC_SCHEME", Map.of("ACTIVE", 0)))
                .build());

        register(new SetupConfig.Builder("Finance - Amd Cnx rules", "RES_AMDCNX_RULE")
                .businessKeyColumns(List.of("RULE_REF", "DESCRIPTION"))
                .syncMode(SyncMode.FULL_SYNC)
                .tableOperationExclusions(Map.of(
                        "RES_AMDCNX_CHARGE", Set.of("DELETE"),
                        "RES_AMD_CNXRULE_OPTION_STATUS", Set.of("DELETE"),
                        "RES_AMDCNX_RULE", Set.of("DELETE")
                ))
                .nextStartId(1800L)
                .build());

        register(new SetupConfig.Builder("Finance - Deposit Rules", "RES_DEPOSIT_RULE")
                .businessKeyColumns(List.of("CODE"))
                .syncMode(SyncMode.FULL_SYNC)
                .tableOperationExclusions(Map.of(
                        "RES_DEPOSIT_RULE", Set.of("DELETE"),
                        "RES_DEPOSIT_CURRENCY_RULE", Set.of("DELETE"),
                        "RES_DEPOSIT_CURRENCY_RULE_TYPE", Set.of("DELETE")
                ))
                .nextStartId(1900L)
                .build());

        register(new SetupConfig.Builder("Finance - Option Rules", "RES_OPTION_RULE")
                .businessKeyColumns(List.of("REFERENCE"))
                .syncMode(SyncMode.FULL_SYNC)
                .tableOperationExclusions(Map.of(
                        "RES_OPTION_DATE_RULE", Set.of("DELETE"),
                        "RES_OPTION_RULE", Set.of("DELETE")
                ))
                .nextStartId(1300L)
                .build());

        register(new SetupConfig.Builder("Calculation Scheme - Type X", "CALC_SCHEME")
                .conditions(Map.of("ACTIVE", 1, "SCHEME_TYPE", "X"))
                .businessKeyColumns(List.of("SCHME_NAME", "SCHEME_TYPE"))
                .syncMode(SyncMode.FULL_SYNC)
                .build());

        register(new SetupConfig.Builder("H2H setup - H2H Board Basis Mapping", "H2H_SUP_BOARD_BASIS_MAPPING")
                .businessKeyColumns(List.of("H2H_ID", "H2H_BOARD_BASIS_CODE", "SUPPLIER_CODE"))
                .syncMode(SyncMode.FULL_SYNC)
                .build());

        register(new SetupConfig.Builder("Markup - Rates", "MARKUP_VERSION")
                .businessKeyColumns(List.of("MARKUP"))
                .syncMode(SyncMode.FULL_SYNC)
                .targetOnlyOverrides(Map.of(
                        "MARKUP", Map.of("ACTIVE", 0)
                )).nextStartId(68900L)
                .tableOperationExclusions(Map.of(
                        "CALC_SCHEME_MARKUP", Set.of("ALL")
                ))
                .build());

        register(new SetupConfig.Builder("Markup - Markup Discount scheme setup", "CALC_SCHEME")
                .conditions(Map.of( "SCHEME_TYPE", "I"))
                .businessKeyColumns(List.of("SCHME_NAME", "SCHEME_TYPE"))
                .syncMode(SyncMode.FULL_SYNC)
                .targetOnlyOverrides(Map.of(
                        "CALC_SCHEME", Map.of("STATUS_ID", 623,"STAGE_NO",60363)
                ))
                .nextStartId(49000L)
                .build());

        register(new SetupConfig.Builder("Markup - Discount scheme setup", "CALC_SCHEME")
                .conditions(Map.of( "SCHEME_TYPE", "P"))
                .businessKeyColumns(List.of("SCHME_NAME", "SCHEME_TYPE"))
                .syncMode(SyncMode.FULL_SYNC)
                .targetOnlyOverrides(Map.of(
                        "CALC_SCHEME_MARKUP", Map.of("ACTIVE", 0)
                ))
                .build());

        register(new SetupConfig.Builder("Reservation - Advisory notes type", "RES_ADV_NOTE_TYPE")
                .businessKeyColumns(List.of("DESCRIPTION"))
                .syncMode(SyncMode.UPSERT_ONLY)
                .nextStartId(700L)
                .tableOperationExclusions(Map.of(
                        "CALC_RULE_EXPRESSION", Set.of("ALL"),
                        "RES_ADV_NOTE", Set.of("ALL"),
                        "RES_ADV_NOTE_TEXT", Set.of("ALL"),
                        "CALC_RULE_GROUP", Set.of("ALL"),
                        "RES_ADV_NOTE_HISTORY", Set.of("ALL")
                ))
                .build());

        register(new SetupConfig.Builder("Reservation - Advisory notes", "RES_ADV_NOTE")
                .businessKeyColumns(List.of("DESCRIPTION"))
                .syncMode(SyncMode.FULL_SYNC)
                .nextStartId(20600L)
                .tableOperationExclusions(Map.of(
                        "RES_ADV_NOTE_HISTORY", Set.of("ALL")
                ))
                .targetOnlyOverrides(Map.of(
                        "RES_ADV_NOTE", Map.of("ACTIVE", 0)
                ))
                .globalFkMappings(Map.of(
                        "RES_ADV_NOTE_TYPE", Set.of("RES_ADV_NOTE.TYPE")
                ))
                .build());

        register(new SetupConfig.Builder("Reservation - Rule Expression", "CALC_RULE_EXPRESSION")
                .syncMode(SyncMode.UPSERT_ONLY)
                .tableOperationExclusions(Map.of(
                        "RES_ADV_NOTE_HISTORY", Set.of("ALL")
                ))
                .targetOnlyOverrides(Map.of(
                        "RES_ADV_NOTE", Map.of("ACTIVE", 0)
                ))
                .globalFkMappings(Map.of(
                        "RES_ADV_NOTE_TYPE", Set.of("RES_ADV_NOTE.TYPE")
                ))
                .build());

        register(new SetupConfig.Builder("region", "REGION")
                .businessKeyColumns(List.of("CODE")) // or PK column list e.g. List.of("ID")
                .syncMode(SyncMode.INSERT_MISSING_ONLY)
                .build());

        register(new SetupConfig.Builder("country", "COUNTRY")
                .businessKeyColumns(List.of("CODE")) // or PK column list e.g. List.of("ID")
                .syncMode(SyncMode.INSERT_MISSING_ONLY)
                .build());

        register(new SetupConfig.Builder("state", "STATE")
                .businessKeyColumns(List.of("CODE")) // or PK column list e.g. List.of("ID")
                .syncMode(SyncMode.INSERT_MISSING_ONLY)
                .build());

        register(new SetupConfig.Builder("city", "CITY")
                .businessKeyColumns(List.of("CODE")) // or PK column list e.g. List.of("ID")
                .syncMode(SyncMode.INSERT_MISSING_ONLY)
                .build());

        register(new SetupConfig.Builder("resort", "RESORT")
                .businessKeyColumns(List.of("CODE")) // or PK column list e.g. List.of("ID")
                .syncMode(SyncMode.INSERT_MISSING_ONLY)
                .build());

        register(new SetupConfig.Builder("airport", "AIRPORT")
                .businessKeyColumns(List.of("CODE")) // or PK column list e.g. List.of("ID")
                .syncMode(SyncMode.INSERT_MISSING_ONLY)
                .build());

        register(new SetupConfig.Builder("tourist_region", "TOURIST_REGION")
                .businessKeyColumns(List.of("CODE")) // or PK column list e.g. List.of("ID")
                .syncMode(SyncMode.INSERT_MISSING_ONLY)
                .build());



        register(new SetupConfig.Builder("Holiday Setup - Flight Priority Search Setup", "WS_FLIGHT_PRIORITY")
                .syncMode(SyncMode.FULL_SYNC)
                .build());


    }

    public static void register(SetupConfig config) {
        SETUP_CATALOG.put(config.getSetupName(), config);
    }

    public static SetupConfig get(String setupName) {
        return SETUP_CATALOG.get(setupName);
    }

    /**
     * Returns ONLY the active setups specified in ACTIVE_SETUPS.
     * If ACTIVE_SETUPS is empty, returns ALL registered setups.
     */
    public static Collection<SetupConfig> getAllActive() {
        if (ACTIVE_SETUPS == null || ACTIVE_SETUPS.isEmpty()) {
            return SETUP_CATALOG.values();
        }

        List<SetupConfig> activeConfigs = new ArrayList<>();
        for (String activeName : ACTIVE_SETUPS) {
            SetupConfig cfg = SETUP_CATALOG.get(activeName);
            if (cfg != null) {
                activeConfigs.add(cfg);
            } else {
                System.err.println("[WARNING] Configured active setup not found in registry: " + activeName);
            }
        }
        return activeConfigs;
    }

    public static Collection<SetupConfig> getAllCatalog() {
        return SETUP_CATALOG.values();
    }
}