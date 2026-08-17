package com.strongcentsit.scriptcreator.util;

import com.strongcentsit.scriptcreator.config.TableNameMapper;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Utility class to read Excel workbooks into Java data structures.
 */
public class ExcelUtils {

    // Sheet names to explicitly ignore/skip
    private static final Set<String> IGNORED_SHEETS = Set.of("SQL");

    private ExcelUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated.");
    }

    /**
     * Reads an Excel file and loads each valid sheet into a table structure map.
     *
     * @param fileNameOrPath The path or resource name of the Excel file.
     * @return Map where Key = Cleaned Table Name, Value = List of Row Maps.
     */
    public static Map<String, List<Map<String, Object>>> loadExcelToMap(String fileNameOrPath) {
        Map<String, List<Map<String, Object>>> dbStructure = new LinkedHashMap<>();

        try (InputStream inputStream = getFileInputStream(fileNameOrPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            DataFormatter formatter = new DataFormatter();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String rawSheetName = sheet.getSheetName();

                // 1. Skip sheet if it matches ignored names (e.g. "SQL")
                if (shouldSkipSheet(rawSheetName)) {
                    continue;
                }

                // 2. Clean table name by removing DB schema prefixes (e.g., "dbo.CALC_SCHEME" -> "CALC_SCHEME")
                String tableName = sanitizeTableName(rawSheetName);
                List<Map<String, Object>> tableRows = new ArrayList<>();

                Iterator<Row> rowIterator = sheet.iterator();
                if (!rowIterator.hasNext()) {
                    dbStructure.put(tableName, tableRows);
                    continue;
                }

                // First row contains column headers
                Row headerRow = rowIterator.next();
                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) {
                    headers.add(formatter.formatCellValue(cell).trim());
                }

                // Process remaining rows
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    Map<String, Object> rowData = new LinkedHashMap<>();
                    boolean isRowEmpty = true;

                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String headerName = headers.get(c);

                        if (cell != null) {
                            Object value = parseCellValue(cell, formatter);
                            if ( (value != null && !value.toString().trim().isEmpty()) && !(value.toString()).endsWith("rows selected.")) {
                                isRowEmpty = false;
                            }
                            rowData.put(headerName, value);
                        } else {
                            rowData.put(headerName, null);
                        }
                    }

                    if (!isRowEmpty) {
                        tableRows.add(rowData);
                    }
                }

                dbStructure.put(tableName, tableRows);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to read Excel file: " + fileNameOrPath, e);
        }

        return dbStructure;
    }

    /**
     * Checks if a sheet name should be skipped.
     */
    private static boolean shouldSkipSheet(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return true;
        }
        return IGNORED_SHEETS.contains(sheetName.trim().toUpperCase());
    }

    /**
     * Strips schema prefixes like "DBO.", "PUBLIC.", "SCHEMA_NAME." from table names.
     * E.g., "dbo.CALC_SCHEME" -> "CALC_SCHEME"
     */
    private static String sanitizeTableName(String rawSheetName) {
        return TableNameMapper.getTableName(rawSheetName);
    }

    private static InputStream getFileInputStream(String path) throws Exception {
        File file = new File(path);
        if (file.exists()) {
            return new FileInputStream(file);
        }

        InputStream resourceStream = ExcelUtils.class.getClassLoader().getResourceAsStream(path);
        if (resourceStream != null) {
            return resourceStream;
        }

        throw new IllegalArgumentException("File not found on system path or classpath: " + path);
    }

    private static Object parseCellValue(Cell cell, DataFormatter formatter) {
        switch (cell.getCellType()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    double doubleVal = cell.getNumericCellValue();
                    if (doubleVal == (long) doubleVal) {
                        return (long) doubleVal;
                    }
                    return doubleVal;
                }
            case STRING:
                return cell.getStringCellValue();
            case FORMULA:
                return formatter.formatCellValue(cell);
            default:
                return null;
        }
    }
}