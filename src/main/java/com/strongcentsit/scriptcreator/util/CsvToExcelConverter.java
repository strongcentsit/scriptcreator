package com.strongcentsit.scriptcreator.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class CsvToExcelConverter {

    private static final int MAX_SHEET_NAME_LENGTH = 31;

    public static void convertCsvFolderToExcel(String folderPath, String outputExcelPath) {
        Path dir = Paths.get(folderPath);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("Invalid directory path: " + folderPath);
            return;
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            List<Path> csvFiles = Files.list(dir)
                    .filter(path -> path.toString().toLowerCase().endsWith(".csv"))
                    .collect(Collectors.toList());

            if (csvFiles.isEmpty()) {
                System.out.println("No CSV files found in directory: " + folderPath);
                return;
            }

            Set<String> usedSheetNames = new HashSet<>();

            for (Path csvPath : csvFiles) {
                String fileName = csvPath.getFileName().toString();
                String rawName = fileName.replaceAll("(?i)\\.csv$", "");

                // Generate a unique valid Excel sheet name (max 31 characters)
                String sheetName = createUniqueSheetName(rawName, usedSheetNames);
                usedSheetNames.add(sheetName.toLowerCase());

                Sheet sheet = workbook.createSheet(sheetName);
                System.out.println("Processing: " + fileName + " -> Sheet: " + sheetName);

                populateSheetFromCsv(sheet, csvPath);
            }

            try (FileOutputStream fos = new FileOutputStream(outputExcelPath)) {
                workbook.write(fos);
                System.out.println("\nExcel file created successfully at: " + outputExcelPath);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String createUniqueSheetName(String baseName, Set<String> usedNames) {
        // Sanitize invalid Excel sheet characters: : \ / ? * [ ]
        String sanitized = baseName.replaceAll("[:\\\\/?*\\[\\]]", "_");

        String candidate = sanitized;
        if (candidate.length() > MAX_SHEET_NAME_LENGTH) {
            candidate = candidate.substring(0, MAX_SHEET_NAME_LENGTH);
        }

        // If unique, return candidate
        if (!usedNames.contains(candidate.toLowerCase())) {
            return candidate;
        }

        // If duplicate prefix exists, append a numerical suffix (_1, _2, etc.)
        int counter = 1;
        while (true) {
            String suffix = "_" + counter;
            int maxBaseLen = MAX_SHEET_NAME_LENGTH - suffix.length();
            String prefix = sanitized.length() > maxBaseLen ? sanitized.substring(0, maxBaseLen) : sanitized;
            candidate = prefix + suffix;

            if (!usedNames.contains(candidate.toLowerCase())) {
                return candidate;
            }
            counter++;
        }
    }

    private static void populateSheetFromCsv(Sheet sheet, Path csvPath) {
        int rowIndex = 0;

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (values != null && values.length > 0) {
                    if (values[0].startsWith("SQL>")||values[0].contains("rows selected")) {
                        continue;
                    }
                }
                Row row = sheet.createRow(rowIndex++);

                for (int colIndex = 0; colIndex < values.length; colIndex++) {
                    Cell cell = row.createCell(colIndex);
                    String val = values[colIndex].trim();

                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1).replace("\"\"", "\"");
                    }

                    if (isNumeric(val)) {
                        cell.setCellValue(Double.parseDouble(val));
                    } else {
                        cell.setCellValue(val);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading CSV file " + csvPath + ": " + e.getMessage());
        }
    }

    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // Preserve leading zeroes (e.g. "0123") as string
        if (str.length() > 1 && str.startsWith("0") && !str.startsWith("0.")) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        String inputFolder = "F:\\data migration\\prod_to_stg\\PROD";
        String outputFile = "F:\\data migration\\prod_to_stg\\prod.xlsx";

        convertCsvFolderToExcel(inputFolder, outputFile);
    }
}