package promo;

import java.util.concurrent.atomic.AtomicInteger;

public class AppConfig {
    // Constant Configurations
    public static final String MODIFIED_BY = "codegen";
    public static final String PROMOTED_BY = "codegen";
    public static final String SOURCE_ENV_CODE = "STAGE";
    public static final String TARGET_ENV_CODE = "UAT";
    public static final int DEFAULT_PROMO_STATE_ID = 4;
    public static final int DEFAULT_STATUS_ID = 4;
    public static final String DEFAULT_RETRY_STATUS = "NOT_STARTED";

    // Dynamic Sequences
    public static final AtomicInteger QUEUE_ID_SEQUENCE = new AtomicInteger(1000);
    public static final AtomicInteger BATCH_ID_SEQUENCE = new AtomicInteger(1000);
}