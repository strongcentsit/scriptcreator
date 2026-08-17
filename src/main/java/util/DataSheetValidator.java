package util;

import metadata.TableSchemaMetadata;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

public class DataSheetValidator {

    private static final double COLUMN_MATCH_THRESHOLD = 0.80; // 80% column match threshold

    private DataSheetValidator() {}

    public static Map<String, List<Map<String, Object>>> validateAndLoadExcel(
            String filePath,
            Map<String, TableSchemaMetadata> metadataMap) {

        System.out.println("--- [VALIDATOR] Validating file: " + filePath + " ---");

        Map<String, List<Map<String, Object>>> rawDataMap = ExcelUtils.loadExcelToMap(filePath);
        Map<String, List<Map<String, Object>>> validatedDataMap = new LinkedHashMap<>();

        DataFormatter formatter = new DataFormatter();

        try (InputStream is = getInputStream(filePath); Workbook wb = WorkbookFactory.create(is)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                String rawSheetName = sheet.getSheetName();

                if (rawSheetName.equalsIgnoreCase("SQL")) continue;

                String sanitizedName = config.TableNameMapper.getTableName(rawSheetName);;

                // Direct match check
                if (metadataMap.containsKey(sanitizedName)) {
                    validatedDataMap.put(sanitizedName, rawDataMap.getOrDefault(sanitizedName, Collections.emptyList()));
                    continue;
                }

                // Truncated/Unrecognized sheet -> Validate headers
                List<String> sheetHeaders = extractSheetHeaders(sheet, formatter);
                String bestMatchedTable = findBestMatchingTable(sheetHeaders, metadataMap);

                if (bestMatchedTable != null) {
                    System.out.printf("  [WARNING] Truncated sheet detected: '%s' -> Auto-corrected to: '%s'\n", rawSheetName, bestMatchedTable);
                    validatedDataMap.put(bestMatchedTable, rawDataMap.getOrDefault(sanitizedName, Collections.emptyList()));
                } else {
                    System.err.printf("  [ERROR] Sheet '%s' does not match schema metadata!\n", rawSheetName);
                    validatedDataMap.put(sanitizedName, rawDataMap.getOrDefault(sanitizedName, Collections.emptyList()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Validation failed for: " + filePath, e);
        }

        return validatedDataMap;
    }

    private static List<String> extractSheetHeaders(Sheet sheet, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        Iterator<Row> iterator = sheet.iterator();
        if (iterator.hasNext()) {
            Row headerRow = iterator.next();
            for (Cell cell : headerRow) {
                if (cell != null) {
                    String val = formatter.formatCellValue(cell).trim();
                    if (!val.isEmpty()) headers.add(val.toUpperCase());
                }
            }
        }
        return headers;
    }

    private static String findBestMatchingTable(List<String> sheetHeaders, Map<String, TableSchemaMetadata> metadataMap) {
        if (sheetHeaders.isEmpty()) return null;

        String bestMatch = null;
        double maxScore = 0.0;

        for (Map.Entry<String, TableSchemaMetadata> entry : metadataMap.entrySet()) {
            String tableName = entry.getKey();
            TableSchemaMetadata meta = entry.getValue();

            // FIX HERE: meta.getColumns() already returns Set<String>
            Set<String> knownColumns = meta.getColumns();
            if (knownColumns == null || knownColumns.isEmpty()) continue;

            long matchingCount = sheetHeaders.stream()
                    .filter(h -> knownColumns.stream().anyMatch(c -> c.equalsIgnoreCase(h)))
                    .count();

            double score = (double) matchingCount / sheetHeaders.size();

            if (score >= COLUMN_MATCH_THRESHOLD && score > maxScore) {
                maxScore = score;
                bestMatch = tableName;
            }
        }

        return bestMatch;
    }

    private static InputStream getInputStream(String path) throws Exception {
        File file = new File(path);
        if (file.exists()) return new FileInputStream(file);
        return DataSheetValidator.class.getClassLoader().getResourceAsStream(path);
    }
}