package util;

import metadata.ForeignKeyMetadata;
import metadata.TableSchemaMetadata;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

public class MetadataUtils {

    private MetadataUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated.");
    }

    public static Map<String, TableSchemaMetadata> loadSchemaMetadata(
            String pkPath,
            String fkPath,
            String colsPath,
            String triggersPath) {

        Map<String, TableSchemaMetadata> metadataMap = new LinkedHashMap<>();

        // 1. Load Column Metadata
        loadColumns(colsPath, metadataMap);

        // 2. Load Primary Keys
        loadPrimaryKeys(pkPath, metadataMap);

        // 3. Load Foreign Keys
        loadForeignKeys(fkPath, metadataMap);

        // 4. Load Triggers (Optional)
        if (triggersPath != null && !triggersPath.isBlank()) {
            loadTriggers(triggersPath, metadataMap);
        }

        return metadataMap;
    }

    private static void loadColumns(String path, Map<String, TableSchemaMetadata> metadataMap) {
        DataFormatter formatter = new DataFormatter();
        try (InputStream is = getInputStream(path); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> it = sheet.iterator();
            if (it.hasNext()) it.next();

            while (it.hasNext()) {
                Row row = it.next();
                String tableName = sanitize(formatter.formatCellValue(row.getCell(0)));
                String colName = sanitize(formatter.formatCellValue(row.getCell(1)));

                if (!tableName.isEmpty() && !colName.isEmpty()) {
                    TableSchemaMetadata meta = metadataMap.computeIfAbsent(tableName, TableSchemaMetadata::new);
                    meta.addColumn(colName);
                }
            }
        } catch (Exception e) {
            System.err.println("[WARNING] Could not load columns metadata (" + path + "): " + e.getMessage());
        }
    }

    private static void loadPrimaryKeys(String path, Map<String, TableSchemaMetadata> metadataMap) {
        DataFormatter formatter = new DataFormatter();
        try (InputStream is = getInputStream(path); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> it = sheet.iterator();
            if (it.hasNext()) it.next();

            while (it.hasNext()) {
                Row row = it.next();
                String tableName = sanitize(formatter.formatCellValue(row.getCell(0)));
                String pkCol = sanitize(formatter.formatCellValue(row.getCell(1)));

                if (!tableName.isEmpty() && !pkCol.isEmpty()) {
                    TableSchemaMetadata meta = metadataMap.computeIfAbsent(tableName, TableSchemaMetadata::new);
                    meta.addPrimaryKey(pkCol);
                }
            }
        } catch (Exception e) {
            System.err.println("[WARNING] Could not load PK metadata (" + path + "): " + e.getMessage());
        }
    }

    private static void loadForeignKeys(String path, Map<String, TableSchemaMetadata> metadataMap) {
        DataFormatter formatter = new DataFormatter();
        try (InputStream is = getInputStream(path); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> it = sheet.iterator();
            if (it.hasNext()) it.next();

            Map<String, ForeignKeyMetadata> fkMap = new LinkedHashMap<>();

            while (it.hasNext()) {
                Row row = it.next();
                String childTable  = sanitize(formatter.formatCellValue(row.getCell(0))); // Col 0
                String childCol    = sanitize(formatter.formatCellValue(row.getCell(1))); // Col 1
                String parentTable = sanitize(formatter.formatCellValue(row.getCell(2))); // Col 2
                String parentCol   = sanitize(formatter.formatCellValue(row.getCell(3))); // Col 3
                String fkName      = sanitize(formatter.formatCellValue(row.getCell(4))); // Col 4

                if (!childTable.isEmpty() && !parentTable.isEmpty()) {
                    String fkKey = parentTable + "->" + childTable + ":" + fkName;

                    // FIXED: Constructor order is (fkName, childTable, parentTable)
                    ForeignKeyMetadata fk = fkMap.computeIfAbsent(fkKey,
                            k -> new ForeignKeyMetadata(fkName, childTable, parentTable));

                    // FIXED: addColumnMapping order is (childColumn, parentColumn)
                    fk.addColumnMapping(childCol, parentCol);
                }
            }

            Collection<ForeignKeyMetadata> fkList = fkMap.values();
//            addCountomRelations( fkList );

            for (ForeignKeyMetadata fk : fkList) {
                // Parent table receives INCOMING FK from child table
                TableSchemaMetadata parentMeta = metadataMap.computeIfAbsent(fk.getParentTable(), TableSchemaMetadata::new);
                parentMeta.addIncomingForeignKey(fk);

                // Child table sends OUTGOING FK pointing to parent table
                TableSchemaMetadata childMeta = metadataMap.computeIfAbsent(fk.getChildTable(), TableSchemaMetadata::new);
                childMeta.addOutgoingForeignKey(fk);
            }

        } catch (Exception e) {
            System.err.println("[WARNING] Could not load FK metadata (" + path + "): " + e.getMessage());
        }
    }


    private static void loadTriggers(String path, Map<String, TableSchemaMetadata> metadataMap) {
        DataFormatter formatter = new DataFormatter();
        try (InputStream is = getInputStream(path); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> it = sheet.iterator();
            if (it.hasNext()) it.next();

            while (it.hasNext()) {
                Row row = it.next();
                String tableName   = sanitize(formatter.formatCellValue(row.getCell(0)));
                String triggerName = sanitize(formatter.formatCellValue(row.getCell(1)));
                String status      = sanitize(formatter.formatCellValue(row.getCell(4)));

                if ("ENABLED".equalsIgnoreCase(status) && !tableName.isEmpty() && !triggerName.isEmpty()) {
                    TableSchemaMetadata meta = metadataMap.computeIfAbsent(tableName, TableSchemaMetadata::new);
                    meta.addEnabledTrigger(triggerName);
                }
            }
        } catch (Exception e) {
            System.err.println("[WARNING] Could not load triggers metadata (" + path + "): " + e.getMessage());
        }
    }

    private static String sanitize(String val) {
        return val == null ? "" : val.trim().toUpperCase();
    }

    private static InputStream getInputStream(String path) throws Exception {
        File file = new File(path);
        if (file.exists()) return new FileInputStream(file);
        return MetadataUtils.class.getClassLoader().getResourceAsStream(path);
    }
}