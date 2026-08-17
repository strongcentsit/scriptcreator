package util;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

public class TriggerUtils {

    private TriggerUtils() {}

    public static class TriggerInfo {
        private final String tableName;
        private final String triggerName;
        private final String status;

        public TriggerInfo(String tableName, String triggerName, String status) {
            this.tableName = tableName;
            this.triggerName = triggerName;
            this.status = status;
        }

        public String getTableName() { return tableName; }
        public String getTriggerName() { return triggerName; }
        public String getStatus() { return status; }
    }

    /**
     * Loads active triggers from triggers.xlsx.
     * Only includes triggers where STATUS = 'ENABLED'.
     */
    public static Map<String, List<TriggerInfo>> loadEnabledTriggers(String filePath) {
        Map<String, List<TriggerInfo>> triggerMap = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream is = getInputStream(filePath); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) return triggerMap;

            rowIterator.next(); // Skip header
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                String tableName = sanitize(formatter.formatCellValue(row.getCell(0)));
                String triggerName = sanitize(formatter.formatCellValue(row.getCell(1)));
                String status = sanitize(formatter.formatCellValue(row.getCell(4)));

                if ("ENABLED".equalsIgnoreCase(status) && !tableName.isEmpty() && !triggerName.isEmpty()) {
                    triggerMap.computeIfAbsent(tableName, k -> new ArrayList<>())
                            .add(new TriggerInfo(tableName, triggerName, status));
                }
            }

        } catch (Exception e) {
            System.err.println("[WARNING] Could not load triggers file (" + filePath + "): " + e.getMessage());
        }

        return triggerMap;
    }

    private static String sanitize(String val) {
        return val == null ? "" : val.trim().toUpperCase();
    }

    private static InputStream getInputStream(String path) throws Exception {
        File file = new File(path);
        if (file.exists()) return new FileInputStream(file);
        return TriggerUtils.class.getClassLoader().getResourceAsStream(path);
    }
}