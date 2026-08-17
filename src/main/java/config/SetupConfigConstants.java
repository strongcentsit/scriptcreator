package config;

/**
 * Holds global configuration constants for the setup generator project.
 */
public class SetupConfigConstants {

    private SetupConfigConstants() {
        throw new UnsupportedOperationException("Configuration constants class should not be instantiated.");
    }

    /**
     * Offset added to the maximum target Primary Key value when generating new IDs.
     * E.g., if max Target PK = 332, next generated ID will start at 332 + 100 + 1 = 433.
     */
    public static final long PK_SEQUENCE_OFFSET = 100L;

    /**
     * Maximum allowed Oracle SQL identifier length (30 characters for standard Oracle versions).
     */
    public static final int MAX_SQL_IDENTIFIER_LENGTH = 30;

    /**
     * Default output directory path for generated SQL files.
     */
    public static final String DEFAULT_OUTPUT_FOLDER = "output/";
}