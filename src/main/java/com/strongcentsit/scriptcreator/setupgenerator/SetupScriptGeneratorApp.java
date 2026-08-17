package com.strongcentsit.scriptcreator.setupgenerator;

import com.strongcentsit.scriptcreator.config.SetupConfigConstants;
import com.strongcentsit.scriptcreator.config.SetupRegistry;
import com.strongcentsit.scriptcreator.metadata.TableSchemaMetadata;
import com.strongcentsit.scriptcreator.util.DataSheetValidator;
import com.strongcentsit.scriptcreator.util.MetadataUtils;
import com.strongcentsit.scriptcreator.util.SetupScriptGenerator;

import java.util.List;
import java.util.Map;

/**
 * Entry point for the Setup Script Generator: the tool's main feature.
 * Reads schema metadata plus source/target data from Excel, then generates
 * INSERT/UPDATE/DELETE (plus backup/rollback) SQL scripts for every setup
 * returned by {@link SetupRegistry#getAllActive()}.
 */
public class SetupScriptGeneratorApp {
    public static void main(String[] args) {

        System.out.println("==================================================");
        System.out.println("          STARTING SETUP SCRIPT GENERATOR         ");
        System.out.println("==================================================\n");

        // 1. Load All Schema Metadata (PKs, FKs, Columns, and Triggers)
        Map<String, TableSchemaMetadata> metadataMap = MetadataUtils.loadSchemaMetadata(
                "input/pk.xlsx",
                "input/fk.xlsx",
                "input/columns.xlsx",
                "input/triggers.xlsx"
        );

        // 2. Validate and Load Source & Target Datasets
        String sourceFileName = "input/SourceData.xlsx";
        String targetFileName = "input/TargetData.xlsx";

        Map<String, List<Map<String, Object>>> sourceDataMap = DataSheetValidator.validateAndLoadExcel(sourceFileName, metadataMap);
        Map<String, List<Map<String, Object>>> targetDataMap = DataSheetValidator.validateAndLoadExcel(targetFileName, metadataMap);

        String outputFolderPath = SetupConfigConstants.DEFAULT_OUTPUT_FOLDER;

        // 3. Generate SQL files for all ACTIVE setups
        System.out.println("\n--------------------------------------------------");
        System.out.println("Generating SQL Scripts for Active Registered Setups");
        System.out.println("--------------------------------------------------");

        SetupScriptGenerator.generateAllSetups(
                SetupRegistry.getAllActive(), // <-- Updated to fetch only active/enabled setups
                sourceDataMap,
                targetDataMap,
                metadataMap,
                outputFolderPath
        );

        System.out.println("\n==================================================");
        System.out.println("            SCRIPT GENERATION COMPLETE            ");
        System.out.println("==================================================");
    }
}