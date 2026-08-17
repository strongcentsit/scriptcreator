package promo;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import promo.model.ExcelItem;
import promo.model.PromoType;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        String sourceFilePath = "input\\promo\\source_products.xlsx";
        String targetFilePath = "input\\promo\\target_products.xlsx";
        String outputSqlPath = "output\\promo\\generated_promo_queue_inserts.sql";

        // Read source Excel items
        List<ExcelItem> sourceItems = readExcelFile(sourceFilePath);

        // Read target codes into a Set for fast lookup
        Set<String> targetCodes = readTargetCodes(targetFilePath);

        if (!sourceItems.isEmpty()) {
            SqlGeneratorService generatorService = new SqlGeneratorService();
            generatorService.generateSqlStatements(sourceItems, targetCodes, PromoType.ACCOM_SUPPLIERS, outputSqlPath);
        } else {
            System.out.println("No records found in source Excel file.");
        }
    }

    private static List<ExcelItem> readExcelFile(String filePath) {
        List<ExcelItem> items = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(); // Safe string conversion for all cell types

        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row == null || row.getRowNum() == 0) continue; // Skip header and empty rows

                Cell codeCell = row.getCell(0);
                Cell nameCell = row.getCell(1);

                String code = formatter.formatCellValue(codeCell).trim();
                String name = formatter.formatCellValue(nameCell).trim();

                if (!code.isEmpty()) {
                    items.add(new ExcelItem(code, name));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
        return items;
    }

    private static Set<String> readTargetCodes(String filePath) {
        Set<String> codes = new HashSet<>();
        DataFormatter formatter = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row == null || row.getRowNum() == 0) continue; // Skip header and empty rows

                Cell codeCell = row.getCell(0);
                String code = formatter.formatCellValue(codeCell).trim();

                if (!code.isEmpty()) {
                    codes.add(code);
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading target file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
        return codes;
    }
}