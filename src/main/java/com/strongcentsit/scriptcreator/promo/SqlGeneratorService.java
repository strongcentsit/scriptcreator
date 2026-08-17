package com.strongcentsit.scriptcreator.promo;

import com.strongcentsit.scriptcreator.promo.model.ExcelItem;
import com.strongcentsit.scriptcreator.promo.model.PromoStep;
import com.strongcentsit.scriptcreator.promo.model.PromoSubStep;
import com.strongcentsit.scriptcreator.promo.model.PromoType;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SqlGeneratorService {

    /**
     * Matches source items against target codes and generates ordered SQL statements.
     *
     * @param sourceItems     List of Excel items read from the source environment sheet
     * @param targetCodes     Set of product codes read from the target environment sheet
     * @param targetPromoType PromoType enum configuration to resolve steps and sub-steps
     * @param outputFilePath  Destination path for the generated .sql file
     */
    public void generateSqlStatements(List<ExcelItem> sourceItems, Set<String> targetCodes, PromoType targetPromoType, String outputFilePath) {
        // Step 1: Flag source records that exist in target
        for (ExcelItem item : sourceItems) {
            if (targetCodes.contains(item.getCode())) {
                item.setExistsInTarget(true);
            }
        }

        // Step 2: Partition source records into New (0) vs Existing (1)
        List<ExcelItem> newRecords = sourceItems.stream()
                .filter(item -> !item.isExistsInTarget())
                .collect(Collectors.toList());

        List<ExcelItem> existingRecords = sourceItems.stream()
                .filter(ExcelItem::isExistsInTarget)
                .collect(Collectors.toList());

        PromoConfigRegistry.StepConfig config = PromoConfigRegistry.getConfig(targetPromoType);

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFilePath))) {

            // Stage 1: Generate SQL for NEW records (EXIST_IN_TARGET = 0)
            writer.println("-- ============================================");
            writer.println("-- 1. NEW RECORDS (EXIST_IN_TARGET = 0)");
            writer.println("-- ============================================\n");
            for (ExcelItem item : newRecords) {
                writeRecordSql(writer, item, targetPromoType, config, 0);
            }

            // Stage 2: Generate SQL for EXISTING records (EXIST_IN_TARGET = 1)
            writer.println("-- ============================================");
            writer.println("-- 2. EXISTING RECORDS (EXIST_IN_TARGET = 1)");
            writer.println("-- ============================================\n");
            for (ExcelItem item : existingRecords) {
                writeRecordSql(writer, item, targetPromoType, config, 1);
            }

            System.out.println("SQL script generated successfully: " + outputFilePath);
            System.out.println("Summary -> New Records: " + newRecords.size() + " | Existing Records: " + existingRecords.size());

        } catch (IOException e) {
            System.err.println("Error writing SQL script file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void writeRecordSql(PrintWriter writer, ExcelItem item, PromoType targetPromoType,
                                PromoConfigRegistry.StepConfig config, int existInTarget) {

        int queueId = AppConfig.QUEUE_ID_SEQUENCE.getAndIncrement();
        int batchId = AppConfig.BATCH_ID_SEQUENCE.get();
        String transactionId = AppConfig.SOURCE_ENV_CODE + "-" + queueId;

        // 1. Insert into pp_promo_queue
        String insertQueue = String.format(
                "INSERT INTO pp_promo_queue (" +
                        "QUEUE_ID, RETRY_COUNT, PROMO_TYPE_ID, CODE, NAME, PROMO_STATE_ID, " +
                        "MODIFIED_BY, PROMOTED_BY, QUEUED_DATE_TIME, BATCH_ID, SOURCE_ENV_CODE, " +
                        "TARGET_ENV_CODE, TRANSACTION_IDENTIFIER, LOCKED) " +
                        "VALUES (%d, 1, %d, '%s', '%s', %d, '%s', '%s', SYSDATE, %d, '%s', '%s', '%s', 0);",
                queueId, targetPromoType.getId(), escapeSql(item.getCode()), escapeSql(item.getName()),
                AppConfig.DEFAULT_PROMO_STATE_ID, AppConfig.MODIFIED_BY, AppConfig.PROMOTED_BY,
                batchId, AppConfig.SOURCE_ENV_CODE, AppConfig.TARGET_ENV_CODE, transactionId
        );
        writer.println(insertQueue);

        // 2. Insert into pp_promo_queue_info (EXIST_IN_TARGET set dynamically to 0 or 1)
        String insertInfo = String.format(
                "INSERT INTO pp_promo_queue_info (" +
                        "QUEUE_ID, RETRY_ID, RETRY_DATE_TIME, RESPONF_DATE_TIME, EXIST_IN_TARGET, RETRY_STATUS) " +
                        "VALUES (%d, 1, SYSDATE, SYSDATE, %d, '%s');",
                queueId, existInTarget, AppConfig.DEFAULT_RETRY_STATUS
        );
        writer.println(insertInfo);

        // 3. Insert into pp_promo_queue_step_states
        for (PromoStep step : config.getSteps()) {
            String insertStep = String.format(
                    "INSERT INTO pp_promo_queue_step_states (QUEUE_ID, RETRY_ID, STEP_ID, STATUS_ID) " +
                            "VALUES (%d, 1, %d, %d);",
                    queueId, step.getId(), AppConfig.DEFAULT_STATUS_ID
            );
            writer.println(insertStep);
        }

        // 4. Insert into pp_promo_queue_sub_step_states
        for (PromoSubStep subStep : config.getSubSteps()) {
            String insertSubStep = String.format(
                    "INSERT INTO pp_promo_queue_sub_step_states (" +
                            "QUEUE_ID, RETRY_ID, STEP_ID, SUB_STEP_ID, STATUS_ID) " +
                            "VALUES (%d, 1, %d, %d, %d);",
                    queueId, subStep.getParentStepId(), subStep.getSubStepId(), AppConfig.DEFAULT_STATUS_ID
            );
            writer.println(insertSubStep);
        }

        writer.println(); // Blank line between record groups
    }

    private String escapeSql(String input) {
        return input == null ? "" : input.replace("'", "''");
    }
}