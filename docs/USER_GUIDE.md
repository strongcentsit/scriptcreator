# Script Creator — User Guide

This guide covers everything needed to run and configure Script Creator, even if you've never touched the codebase before. It documents what exists today, including a few rough edges (dead/reserved code, one hardcoded-path helper) so you don't get tripped up by them.

## Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Project layout](#project-layout)
- [Running the tool](#running-the-tool)
- [Feature 1: Setup Script Generator](#feature-1-setup-script-generator)
- [Feature 2: Promo Queue Generator](#feature-2-promo-queue-generator)
- [Feature 3: CSV → Excel Converter](#feature-3-csv--excel-converter)
- [Input files](#input-files)
- [Regenerating the metadata files from Oracle](#regenerating-the-metadata-files-from-oracle)
- [Output files](#output-files)
- [Configuration reference](#configuration-reference)
- [Registered setups (reference table)](#registered-setups-reference-table)
- [Choosing which setups run](#choosing-which-setups-run)
- [Adding a new setup](#adding-a-new-setup)
- [Troubleshooting](#troubleshooting)
- [Known limitations / dead code](#known-limitations--dead-code)

## Overview

Script Creator is built for teams that promote business configuration data (pricing rules, deposit rules, advisory notes, location reference data, etc.) between database environments — e.g. Staging → Production — and want repeatable, reviewable SQL instead of manual scripting or direct database-to-database tools.

Instead of connecting to a live database, it works entirely from **Excel exports**:

- Schema metadata (which columns exist, which are primary/foreign keys, which triggers are enabled) comes from `pk.xlsx`, `fk.xlsx`, `columns.xlsx`, `triggers.xlsx`.
- The actual rows to compare come from `SourceData.xlsx` (the environment you're promoting *from*) and `TargetData.xlsx` (the environment you're promoting *to*), one sheet per table.

Given those, it walks a catalog of ~24 named **setups** (e.g. "Finance - Deposit Rules", "Accounts - Single Use CC Eligibility"), compares source vs. target row-by-row using a configurable business key, and writes out ready-to-review SQL — plus a backup script to run first and a rollback script to run if you need to undo it. This is the tool's **main feature**, run via `SetupScriptGeneratorApp`.

A second, unrelated feature (`PromoQueueGeneratorApp`) generates SQL for a promotion *job queue* table rather than the business data itself.

## Requirements

- **Java 11+**
- **Maven** (the project has one dependency: `org.apache.poi:poi-ooxml:5.2.5`, for reading/writing `.xlsx` files)

## Project layout

All source lives under a single root package, `com.strongcentsit.scriptcreator`:

```
src/main/java/com/strongcentsit/scriptcreator/
  setupgenerator/
    SetupScriptGeneratorApp.java   Entry point — Setup Script Generator (the tool's main feature)
  promo/
    PromoQueueGeneratorApp.java    Entry point — Promo Queue Generator
    AppConfig.java                  Queue ID / batch ID counters
    PromoConfigRegistry.java        Step/sub-step config per PromoType
    SqlGeneratorService.java        Builds the promo queue INSERT statements
    model/                          ExcelItem, PromoStep, PromoSubStep, PromoType
  config/
    SetupRegistry.java              Catalog of all 24 setups + the active-setup switch
    SetupConfig.java                Per-setup configuration object (builder pattern)
    SetupConfigConstants.java       PK offset, identifier length limit, default output folder
    ColumnOverrideConfig.java       Global column-value overrides (audit columns)
    GlobalBusinessKeyConfig.java    Cross-setup FK remapping keys
    TableNameMapper.java            Excel sheet name → real table name mapping
    SyncMode.java                   The 4 sync strategies
    GenerationMode.java             Reserved/unused enum — ignore (see Known limitations)
  metadata/
    TableSchemaMetadata.java, ColumnMetadata.java, ForeignKeyMetadata.java, EntityNode.java
    In-memory model of table schemas built from pk/fk/columns/triggers Excel files
  util/
    MetadataUtils.java         Loads pk.xlsx / fk.xlsx / columns.xlsx / triggers.xlsx
    ExcelUtils.java             Generic Excel → Map<table, rows> loader
    DataSheetValidator.java     Validates SourceData/TargetData sheets against schema metadata
    DataQueryUtils.java         Row matching by business key, FK tree walking
    SqlScriptUtils.java         Builds the actual INSERT/UPDATE/DELETE SQL text
    SetupScriptGenerator.java   Orchestrates the whole setup pipeline
    CsvToExcelConverter.java    Standalone CSV → Excel helper (see Feature 3)
```

Two dead classes that existed before this cleanup — an empty no-op `Main` class and an unused `TriggerUtils` (its logic was always duplicated inside `MetadataUtils`) — have been removed entirely; there's nothing to avoid using anymore.

## Running the tool

There's no single CLI entry point — this is a source project with two independent, purpose-built `main` methods (plus one standalone helper, see Feature 3). Run whichever one you need with the project root as the working directory, so relative paths like `input/...` resolve correctly.

**Option A — Maven (command line)**, from the project root:
```bash
# Setup Script Generator (the main feature — this is the default target)
mvn compile exec:java

# Promo Queue Generator
mvn compile exec:java -Dexec.mainClass=com.strongcentsit.scriptcreator.promo.PromoQueueGeneratorApp
```

**Option B — from an IDE**: open the project, right-click the target class, and Run:
- [`SetupScriptGeneratorApp`](../src/main/java/com/strongcentsit/scriptcreator/setupgenerator/SetupScriptGeneratorApp.java) for the Setup Script Generator
- [`PromoQueueGeneratorApp`](../src/main/java/com/strongcentsit/scriptcreator/promo/PromoQueueGeneratorApp.java) for the Promo Queue Generator

Set the working directory to the project root in your run configuration if your IDE defaults elsewhere.

Neither entry point takes command-line arguments — all file paths are hardcoded in each `main` method (see each feature section below for the exact paths).

## Feature 1: Setup Script Generator

**Entry point:** [`SetupScriptGeneratorApp`](../src/main/java/com/strongcentsit/scriptcreator/setupgenerator/SetupScriptGeneratorApp.java) — the tool's main feature.

### What it reads
- `input/pk.xlsx`, `input/fk.xlsx`, `input/columns.xlsx`, `input/triggers.xlsx` — schema metadata
- `input/SourceData.xlsx`, `input/TargetData.xlsx` — the data to compare, one sheet per table

### What it does
For every setup returned by `SetupRegistry.getAllActive()` (see [Choosing which setups run](#choosing-which-setups-run)):

1. Filters the setup's main table rows by its configured `conditions` (a simple equality filter, e.g. `SCHEME_TYPE = 'D'`).
2. Matches source rows to target rows using the setup's `businessKeyColumns` (an exact match on one or more columns — can reference a related table via `ChildTable.Column`).
3. Depending on the setup's [`SyncMode`](#syncmode):
   - Updates matched records (keeping the target's existing ID),
   - Inserts unmatched source records (assigning new sequential IDs),
   - Deletes or soft-deletes target records with no matching source record.
4. Recursively applies the same logic to related child-table records (found by walking foreign keys), so a setup's "child rows" get synced along with its main row.
5. Wraps the whole script in `ALTER TRIGGER ... DISABLE` / `ENABLE` statements for any enabled trigger on an affected table, so business-rule triggers don't fire while the script runs.
6. Applies a global set of column overrides to every insert/update — audit columns like `CREATED_BY`, `LAST_MODIFIED_DATE`, etc. are always set to fixed values (`codegen`, `SYSDATE`) rather than copied from source. See [ColumnOverrideConfig](#columnoverrideconfig).
7. Writes three files per setup into `output/` (see [Output files](#output-files)).

### `SyncMode`
Each setup picks one of four strategies:

| Mode | Behavior |
|---|---|
| `FULL_SYNC` | Update matched records, insert new ones, and delete/deactivate orphaned target records. The default, full three-way sync. |
| `UPSERT_ONLY` | Update matched + insert new, but leave orphaned target records untouched (no deletes). |
| `DELETE_ORPHANS_ONLY` | Only deletes/deactivates target records that no longer exist in source — no inserts or updates. |
| `INSERT_MISSING_ONLY` | Simple single-table insert of source records missing from target, using the original source primary key as-is. No child-table handling, no ID remapping. Used for flat reference-data tables like `REGION`, `COUNTRY`, `CITY`. |

## Feature 2: Promo Queue Generator

**Entry point:** [`PromoQueueGeneratorApp`](../src/main/java/com/strongcentsit/scriptcreator/promo/PromoQueueGeneratorApp.java)

### What it reads
- `input\promo\source_products.xlsx` — column A = code, column B = name
- `input\promo\target_products.xlsx` — column A = code (only the codes are used, to check what already exists in the target)

### What it does
This feature does **not** move business data directly. Instead it generates `INSERT` statements that enqueue a promotion **job** for each source product into a workflow queue table (`pp_promo_queue`), which some other system presumably picks up and executes. For every source product it writes:

- one row into `pp_promo_queue` (with an auto-incrementing `QUEUE_ID` starting at 1000, `SOURCE_ENV_CODE = 'STAGE'`, `TARGET_ENV_CODE = 'UAT'`)
- one row into `pp_promo_queue_info`, flagging whether the product already exists in the target (`EXIST_IN_TARGET = 0` or `1`)
- one row per configured workflow step into `pp_promo_queue_step_states`
- one row per configured workflow sub-step into `pp_promo_queue_sub_step_states`

The job type is hardcoded in `PromoQueueGeneratorApp` to `PromoType.ACCOM_SUPPLIERS` — there's no way to switch it without editing the source. Out of the four defined `PromoType` values (`ACCOM`, `ACCOM_SUPPLIERS`, `CONTENT_PROFILE`, `ACCOM_PRODUCT_CATALOGUE`), only `ACCOM_SUPPLIERS` has its workflow steps/sub-steps actually configured in `PromoConfigRegistry`; the others would generate queue rows with no step/sub-step entries if ever wired up.

### Output
A single file: `output\promo\generated_promo_queue_inserts.sql`, with plain `INSERT` statements grouped into "NEW RECORDS" and "EXISTING RECORDS" sections. No backup/rollback files are generated for this feature.

## Feature 3: CSV → Excel Converter

**Class:** [`CsvToExcelConverter`](../src/main/java/com/strongcentsit/scriptcreator/util/CsvToExcelConverter.java) (has its own `main` method, but is **not** called by either of the two entry points above — it's a standalone helper)

### What it does
Converts a folder of `.csv` files (e.g. output from an Oracle `SQL*Plus` query, saved as CSV) into a single multi-sheet `.xlsx` workbook — one sheet per CSV file. Useful for preparing the `input/*.xlsx` files this tool expects, if your source data currently comes out of the database as CSV.

- Sheet names are sanitized (illegal characters `: \ / ? * [ ]` stripped) and truncated/uniquified to Excel's 31-character sheet-name limit.
- Lines that look like `SQL*Plus` noise (starting with `SQL>`, or containing `rows selected`) are automatically skipped.
- Numeric-looking values are written as numbers, except values with a leading zero (e.g. `"0123"`), which are kept as text so codes aren't corrupted.

### Running it
Unlike the other two entry points, this one has **hardcoded, machine-specific input/output paths** in its `main` method — you must edit them (or call `convertCsvFolderToExcel(inputFolder, outputFile)` from your own small script) before running:

```java
String inputFolder = "F:\\data migration\\prod_to_stg\\PROD";
String outputFile = "F:\\data migration\\prod_to_stg\\prod.xlsx";
```

## Input files

All files are `.xlsx`, read with Apache POI. Unless noted, the header row is row 1 and is skipped when reading data.

### Schema metadata files (`pk.xlsx`, `fk.xlsx`, `columns.xlsx`, `triggers.xlsx`)

These four describe the target database's schema and are typically exported straight from Oracle's data dictionary (see [Regenerating the metadata files from Oracle](#regenerating-the-metadata-files-from-oracle) below for ready-to-run queries). Each sheet's columns below are exactly what the generator reads — nothing more. Earlier versions of these files carried extra Oracle metadata (`COLUMN_ID`, `DATA_TYPE`, `DATA_LENGTH`, `DATA_PRECISION`, `DATA_SCALE`, `NULLABLE`, `POSITION`, `CONSTRAINT_NAME`, `TRIGGER_TYPE`, `TRIGGERING_EVENT`) that no code path in this project ever reads; those columns have been removed from the checked-in files to keep them focused on exactly what the tool consumes.

| File | Columns (by position) |
|---|---|
| `input/columns.xlsx` | A: `TABLE_NAME` · B: `COLUMN_NAME` |
| `input/pk.xlsx` | A: `TABLE_NAME` · B: `COLUMN_NAME` (the primary key column) |
| `input/fk.xlsx` | A: `CHILD_TABLE` · B: `CHILD_COLUMN` · C: `PARENT_TABLE` · D: `PARENT_COLUMN` · E: `FK_NAME` |
| `input/triggers.xlsx` (optional) | A: `TABLE_NAME` · B: `TRIGGER_NAME` · C: `STATUS` (only rows with status `ENABLED` are used) |

> **Bug found and fixed while auditing these files' column usage:** `columns.xlsx` previously carried `COLUMN_ID` in column B and the real `COLUMN_NAME` in column C, but [`MetadataUtils.loadColumns`](../src/main/java/com/strongcentsit/scriptcreator/util/MetadataUtils.java) read column B expecting the column name — so every table's "known columns" set was actually populated with numeric column-order IDs, not real column names. The only consumer of that data, [`DataSheetValidator`](../src/main/java/com/strongcentsit/scriptcreator/util/DataSheetValidator.java)'s ≥80%-header-overlap fuzzy matching for truncated `SourceData.xlsx`/`TargetData.xlsx` sheet names, could therefore never actually match anything — a truncated sheet name always fell through to `[ERROR] Sheet '<name>' does not match schema metadata!` instead of being auto-corrected. Removing the unused `COLUMN_ID` column (so `COLUMN_NAME` now sits in column B, where the code already expected it) fixes this as a side effect of the column cleanup — no separate code change was needed for `columns.xlsx`. `triggers.xlsx` did need a code change: after dropping `TRIGGER_TYPE`/`TRIGGERING_EVENT`, `STATUS` moved from column E to column C, so `MetadataUtils.loadTriggers` was updated to read `getCell(2)` instead of `getCell(4)`.

If you're maintaining these files locally, the pre-cleanup originals (with every Oracle column intact) are kept at `input/_backup_before_column_cleanup/` for reference — that folder isn't read by any code path.

### Business/reference data files

| File | Columns (by position) |
|---|---|
| `input/SourceData.xlsx` / `input/TargetData.xlsx` | One sheet per table; sheet name = table name; row 1 = column headers matching `columns.xlsx` |
| `input/promo/source_products.xlsx` | A: Code · B: Name |
| `input/promo/target_products.xlsx` | A: Code |

**Sheet naming for `SourceData.xlsx` / `TargetData.xlsx`:** Excel sheet names are capped at 31 characters, so long table names get truncated. The tool tries to auto-resolve truncated names by matching ≥80% of the sheet's headers against a known table's columns, and logs a `[WARNING] Truncated sheet detected: ... -> Auto-corrected to: ...` when it does. Three known-truncated names are pre-registered in [`TableNameMapper`](../src/main/java/com/strongcentsit/scriptcreator/config/TableNameMapper.java) for `CALC_SCHEME_MARKUP_CROSS_COMPONENT_*` tables — if you add a table with a long name, you may need to register it there too, or rename its sheet manually. A sheet named exactly `SQL` is always ignored (used for a raw query dump if present).

## Regenerating the metadata files from Oracle

`pk.xlsx`, `fk.xlsx`, `columns.xlsx`, and `triggers.xlsx` describe whichever Oracle schema you're migrating *from* (the source environment) — the schema is assumed identical enough between source and target that one metadata export covers both. Run these against the source database, export each result set as a single-sheet `.xlsx` with the exact header row shown above, and drop it into `input/`.

### 1. Which tables to include

Rather than exporting the whole schema, scope every query below to just the tables these setups actually touch. The 20 distinct **main tables** registered across all 24 setups in [`SetupRegistry.java`](../src/main/java/com/strongcentsit/scriptcreator/config/SetupRegistry.java) are:

```
SUCC_ELIGIBILITY_RULE, CALC_SCHEME, ROUNDING_RULE, RATE_TOLERANCE_RULE, RES_AMDCNX_RULE,
RES_DEPOSIT_RULE, RES_OPTION_RULE, H2H_SUP_BOARD_BASIS_MAPPING, MARKUP_VERSION,
RES_ADV_NOTE_TYPE, RES_ADV_NOTE, CALC_RULE_EXPRESSION, REGION, COUNTRY, STATE, CITY,
RESORT, AIRPORT, TOURIST_REGION, WS_FLIGHT_PRIORITY
```

Plus the **related tables** referenced explicitly in `SetupRegistry.java` via business keys, exclusion lists, target-only overrides, or FK remappings:

```
CALC_DOCUMENT_SCHEME, CALC_SCHEME_FEES_AND_TAXES, RES_AMDCNX_CHARGE, RES_AMD_CNXRULE_OPTION_STATUS,
RES_DEPOSIT_CURRENCY_RULE, RES_DEPOSIT_CURRENCY_RULE_TYPE, RES_OPTION_DATE_RULE, CALC_SCHEME_MARKUP,
CALC_RULE_GROUP, RES_ADV_NOTE_TEXT, RES_ADV_NOTE_HISTORY
```

That's not necessarily the *complete* set, though — at runtime, the generator also walks every table reachable from a setup's main table via `fk.xlsx` (e.g. `RES_SETUP_ASSIGNMENTS` under `RES_DEPOSIT_RULE`), and that full descendant tree isn't visible just by reading `SetupRegistry.java`. To discover it directly from the database, run this starting from the main-table list above (replace `<SCHEMA_OWNER>` with your schema):

```sql
WITH seed_tables (table_name) AS (
    SELECT 'SUCC_ELIGIBILITY_RULE' FROM dual UNION ALL
    SELECT 'CALC_SCHEME' FROM dual UNION ALL
    SELECT 'ROUNDING_RULE' FROM dual UNION ALL
    SELECT 'RATE_TOLERANCE_RULE' FROM dual UNION ALL
    SELECT 'RES_AMDCNX_RULE' FROM dual UNION ALL
    SELECT 'RES_DEPOSIT_RULE' FROM dual UNION ALL
    SELECT 'RES_OPTION_RULE' FROM dual UNION ALL
    SELECT 'H2H_SUP_BOARD_BASIS_MAPPING' FROM dual UNION ALL
    SELECT 'MARKUP_VERSION' FROM dual UNION ALL
    SELECT 'RES_ADV_NOTE_TYPE' FROM dual UNION ALL
    SELECT 'RES_ADV_NOTE' FROM dual UNION ALL
    SELECT 'CALC_RULE_EXPRESSION' FROM dual UNION ALL
    SELECT 'REGION' FROM dual UNION ALL
    SELECT 'COUNTRY' FROM dual UNION ALL
    SELECT 'STATE' FROM dual UNION ALL
    SELECT 'CITY' FROM dual UNION ALL
    SELECT 'RESORT' FROM dual UNION ALL
    SELECT 'AIRPORT' FROM dual UNION ALL
    SELECT 'TOURIST_REGION' FROM dual UNION ALL
    SELECT 'WS_FLIGHT_PRIORITY' FROM dual
)
SELECT DISTINCT LEVEL AS depth, PRIOR pk.table_name AS parent_table, fk.table_name AS child_table
FROM all_constraints fk
JOIN all_constraints pk
  ON fk.r_constraint_name = pk.constraint_name
 AND fk.r_owner = pk.owner
WHERE fk.constraint_type = 'R'
  AND fk.owner = '<SCHEMA_OWNER>'
START WITH pk.table_name IN (SELECT table_name FROM seed_tables)
CONNECT BY NOCYCLE PRIOR fk.table_name = pk.table_name
ORDER BY depth, parent_table, child_table;
```

Union the `child_table` results with the seed list above to get the complete table list — that's what should go into every `IN (...)` clause below.

### 2. `columns.xlsx`

```sql
SELECT table_name  AS "TABLE_NAME",
       column_name AS "COLUMN_NAME"
FROM all_tab_columns
WHERE owner = '<SCHEMA_OWNER>'
  AND table_name IN (<table list from step 1>)
ORDER BY table_name, column_id;
```

### 3. `pk.xlsx`

```sql
SELECT acc.table_name  AS "TABLE_NAME",
       acc.column_name AS "COLUMN_NAME"
FROM all_constraints ac
JOIN all_cons_columns acc
  ON ac.constraint_name = acc.constraint_name
 AND ac.owner = acc.owner
WHERE ac.constraint_type = 'P'
  AND ac.owner = '<SCHEMA_OWNER>'
  AND ac.table_name IN (<table list from step 1>)
ORDER BY acc.table_name, acc.position;
```

### 4. `fk.xlsx`

```sql
SELECT child_cons.table_name     AS "CHILD_TABLE",
       child_cols.column_name    AS "CHILD_COLUMN",
       parent_cons.table_name    AS "PARENT_TABLE",
       parent_cols.column_name   AS "PARENT_COLUMN",
       child_cons.constraint_name AS "FK_NAME"
FROM all_constraints child_cons
JOIN all_cons_columns child_cols
  ON child_cons.constraint_name = child_cols.constraint_name
 AND child_cons.owner = child_cols.owner
JOIN all_constraints parent_cons
  ON child_cons.r_constraint_name = parent_cons.constraint_name
 AND child_cons.r_owner = parent_cons.owner
JOIN all_cons_columns parent_cols
  ON parent_cons.constraint_name = parent_cols.constraint_name
 AND parent_cons.owner = parent_cols.owner
 AND parent_cols.position = child_cols.position
WHERE child_cons.constraint_type = 'R'
  AND child_cons.owner = '<SCHEMA_OWNER>'
  AND child_cons.table_name IN (<table list from step 1>)
ORDER BY child_cons.table_name, child_cons.constraint_name, child_cols.position;
```

### 5. `triggers.xlsx`

```sql
SELECT table_name   AS "TABLE_NAME",
       trigger_name AS "TRIGGER_NAME",
       status       AS "STATUS"
FROM all_triggers
WHERE table_owner = '<SCHEMA_OWNER>'
  AND table_name IN (<table list from step 1>)
ORDER BY table_name, trigger_name;
```

### Updating the sheets

1. Run each query in your Oracle client (SQL Developer, SQL*Plus, etc.) against the source environment.
2. Export each result set to a single-sheet `.xlsx` with the header row exactly as shown (`TABLE_NAME`, `COLUMN_NAME`, etc. — the generator matches by position, not header text, but keeping real headers makes the file self-documenting).
3. Replace the corresponding file in `input/` (`columns.xlsx`, `pk.xlsx`, `fk.xlsx`, `triggers.xlsx`).
4. Re-run `SetupScriptGeneratorApp` (or `mvn compile exec:java`) — no code changes are needed as long as the column order in each file matches the table above.

## Output files

**Setup pipeline** (`SetupScriptGeneratorApp`) writes into `output/` (created automatically), three files per active setup:

| File | Purpose |
|---|---|
| `<Setup_Name>.sql` | The actual sync script — run this to apply the change. |
| `<Setup_Name>_BACKUP.sql` | Run this **first**. Creates `B_<table>` backup tables (`CREATE TABLE ... AS SELECT * FROM ...`) for every table the main script touches. |
| `<Setup_Name>_ROLLBACK.sql` | Run this only if you need to undo the main script. Restores every affected table from its `B_<table>` backup, then drops the backup tables. |

Setup names are sanitized into filenames by replacing anything that isn't a letter, digit, `-`, or `_` with `_` — e.g. `"Finance - Local Fee Schemes"` → `Finance_-_Local_Fee_Schemes.sql`.

**Promo pipeline** writes a single file: `output/promo/generated_promo_queue_inserts.sql` (no backup/rollback).

> **Recommended run order:** review the `_BACKUP.sql`, run it → review the main `.sql`, run it → keep the `_ROLLBACK.sql` on hand in case you need to revert.

`output/` (like `input/`) is gitignored, so generated SQL never gets committed to this repo — treat it as local/disposable and copy anything you need to keep elsewhere before re-running the generator.

## Configuration reference

All configuration lives in source code under `src/main/java/com/strongcentsit/scriptcreator/config/` — there is no external config file. To change behavior, edit the relevant class and rebuild.

### `SetupRegistry`
[`SetupRegistry.java`](../src/main/java/com/strongcentsit/scriptcreator/config/SetupRegistry.java) — the catalog of every setup the tool knows about, plus the switch that decides which ones actually run (see [Choosing which setups run](#choosing-which-setups-run)).

### `SetupConfig` / `SetupConfig.Builder`
The per-setup configuration object. Each setup in `SetupRegistry` is built like this:

```java
new SetupConfig.Builder("Finance - Deposit Rules", "RES_DEPOSIT_RULE")
    .businessKeyColumns(List.of("CODE"))
    .syncMode(SyncMode.FULL_SYNC)
    .nextStartId(1900L)
    .orphanDeleteExclusions(Set.of(
        "RES_DEPOSIT_RULE",
        "RES_DEPOSIT_CURRENCY_RULE",
        "RES_DEPOSIT_CURRENCY_RULE_TYPE"
    ))
    .build()
```

Available builder options:

| Option | Purpose |
|---|---|
| `.conditions(Map<String,Object>)` | Equality filter applied to the main table before diffing, e.g. `Map.of("SCHEME_TYPE", "D")`. |
| `.businessKeyColumns(List<String>)` | Column(s) used to match a source row to a target row. Use a plain column name for the main table, or `"ChildTable.Column"` to match via a related table. |
| `.syncMode(SyncMode)` | One of the four modes described above. Defaults to `FULL_SYNC`. |
| `.nextStartId(Long)` | Starting primary key for newly inserted records. If omitted, it's computed as `max(existing target PK) + 100`. |
| `.customOverrides(Map<String,Object>)` | Extra column-value overrides for this setup only, layered on top of the global overrides below. |
| `.targetOnlyOverrides(Map<String, Map<String,Object>>)` | Instead of hard-deleting an orphaned target record, update it with these column values (a soft delete) — e.g. `Map.of("CALC_SCHEME", Map.of("ACTIVE", 0))`. |
| `.tableOperationExclusions(Map<String, Set<String>>)` | Suppress specific SQL operations (`INSERT`, `UPDATE`, `DELETE`, or `ALL`) for a table, **in every scenario the generator emits SQL for it** — matched-record updates included. See the warning below before using this for a DELETE-only exclusion. |
| `.orphanDeleteExclusions(Set<String>)` | Spares these tables from physical `DELETE` **only** when removing an orphaned (target-only) record tree — e.g. "disable" a rule by deleting just its `RES_SETUP_ASSIGNMENTS` row instead of the whole rule. Does not affect the delete-then-reinsert refresh of a matched record's children on `UPDATE`. See below. |
| `.globalFkMappings(Map<String, Set<String>>)` | Declares that a foreign key on a child table (`"ChildTable.Column"`) points at a table registered in `GlobalBusinessKeyConfig`, so its value gets remapped from the source PK to the target PK before syncing. |
| `.enabled(boolean)` | Present on the builder but **not currently checked anywhere** — has no effect. Use `ACTIVE_SETUPS` in `SetupRegistry` to enable/disable a setup instead. |

> **`tableOperationExclusions` vs. `orphanDeleteExclusions` — use the right one.** A `DELETE` entry in `tableOperationExclusions` suppresses that table's `DELETE` statements *everywhere*, including the delete-then-reinsert refresh a matched (`FULL_SYNC`/`UPSERT_ONLY`) record's child rows go through on `UPDATE`. If you put a table there to implement a "disable instead of delete" behavior for orphaned (target-only) records, you will also silently skip the cleanup delete on ordinary updates — the following `INSERT` then fails with a primary-key collision, because the old rows for that record were never removed. Use `orphanDeleteExclusions` instead: it only takes effect for orphan removal (Step 1), so matched-record refreshes on `UPDATE` still delete and reinsert those tables correctly. Reserve `tableOperationExclusions` for tables a setup should *never* touch at all (e.g. `Set.of("ALL")` because another setup, or a separate process, owns them).

### `ColumnOverrideConfig`
[`ColumnOverrideConfig.java`](../src/main/java/com/strongcentsit/scriptcreator/config/ColumnOverrideConfig.java) — column names that get their value replaced automatically on every insert/update, across every setup, regardless of what's in the source data. Mostly audit columns:

```
LAST_MODIFIED_TIME, LAST_MODIFIED_DATE, LAST_MODIFIED, LAST_MOD_DATE, LAST_UPDATED, MODIFIED_ON → SYSDATE / SYSTIMESTAMP
LAST_MODIFIED_USER, LST_MODIFIED_USE, LAST_MODIFIED_BY, MODIFIED_BY, CREATED_USER, ENTERED_BY → '8778'
CREATED_DATE → SYSTIMESTAMP
CREATED_BY → 'codegen'
```
To point these at a different user ID or change the environment tag, edit this file directly.

### `GlobalBusinessKeyConfig`
[`GlobalBusinessKeyConfig.java`](../src/main/java/com/strongcentsit/scriptcreator/config/GlobalBusinessKeyConfig.java) — registers business keys for tables that aren't a setup's own main table, but that other setups' data has foreign keys into (so those FK values can be remapped from source IDs to target IDs). Currently only `RES_ADV_NOTE_TYPE` (business key: `DESCRIPTION`) is registered.

### `TableNameMapper`
[`TableNameMapper.java`](../src/main/java/com/strongcentsit/scriptcreator/config/TableNameMapper.java) — maps a truncated Excel sheet name to its real table name, for tables whose name exceeds Excel's 31-character sheet-name limit. Register a new mapping here if you add a long-named table and the automatic fuzzy-matching (see [Input files](#input-files)) doesn't resolve it correctly.

### `SetupConfigConstants`
[`SetupConfigConstants.java`](../src/main/java/com/strongcentsit/scriptcreator/config/SetupConfigConstants.java) — `PK_SEQUENCE_OFFSET` (gap above the max target ID used when auto-computing a new starting ID, default 100), `MAX_SQL_IDENTIFIER_LENGTH` (30, Oracle's identifier length limit, used when naming `B_<table>` backup tables), and `DEFAULT_OUTPUT_FOLDER` (`"output/"`, used by `SetupScriptGeneratorApp`).

## Registered setups (reference table)

All 24 setups currently registered in `SetupRegistry`, in registration order. Only the ones listed in `ACTIVE_SETUPS` (see next section) are actually generated on a given run.

| # | Setup Name | Main Table | Sync Mode | Notes |
|---|---|---|---|---|
| 1 | Accounts - Single Use CC Eligibility | `SUCC_ELIGIBILITY_RULE` | FULL_SYNC | |
| 2 | Document - Document Rules | `CALC_SCHEME` | FULL_SYNC | Orphans soft-deleted (`ACTIVE=0`), not removed |
| 3 | Finance - Rounding Rules Setup | `ROUNDING_RULE` | FULL_SYNC | |
| 4 | Reservation - Tolerance setup | `RATE_TOLERANCE_RULE` | FULL_SYNC | |
| 5 | Finance - Local Fee Schemes | `CALC_SCHEME` | FULL_SYNC | Orphans soft-deleted (`ACTIVE=0`) |
| 6 | Finance - Amd Cnx rules | `RES_AMDCNX_RULE` | FULL_SYNC | New IDs start at 1800; orphans disabled via assignment removal on 3 tables (`orphanDeleteExclusions`) |
| 7 | Finance - Deposit Rules | `RES_DEPOSIT_RULE` | FULL_SYNC | New IDs start at 1900; orphans disabled via assignment removal on 3 tables (`orphanDeleteExclusions`) |
| 8 | Finance - Option Rules | `RES_OPTION_RULE` | FULL_SYNC | New IDs start at 1300; orphans disabled via assignment removal on 2 tables (`orphanDeleteExclusions`) |
| 9 | Calculation Scheme - Type X | `CALC_SCHEME` | FULL_SYNC | |
| 10 | H2H setup - H2H Board Basis Mapping | `H2H_SUP_BOARD_BASIS_MAPPING` | FULL_SYNC | |
| 11 | Markup - Rates | `MARKUP_VERSION` | FULL_SYNC | New IDs start at 68900; orphans soft-deleted |
| 12 | Markup - Markup Discount scheme setup | `CALC_SCHEME` | FULL_SYNC | New IDs start at 49000 |
| 13 | Markup - Discount scheme setup | `CALC_SCHEME` | FULL_SYNC | |
| 14 | Reservation - Advisory notes type | `RES_ADV_NOTE_TYPE` | UPSERT_ONLY | New IDs start at 700; feeds `GlobalBusinessKeyConfig` |
| 15 | Reservation - Advisory notes | `RES_ADV_NOTE` | FULL_SYNC | New IDs start at 20600; orphans soft-deleted |
| 16 | Reservation - Rule Expression | `CALC_RULE_EXPRESSION` | UPSERT_ONLY | |
| 17 | region | `REGION` | INSERT_MISSING_ONLY | Flat reference data |
| 18 | country | `COUNTRY` | INSERT_MISSING_ONLY | Flat reference data |
| 19 | state | `STATE` | INSERT_MISSING_ONLY | Flat reference data |
| 20 | city | `CITY` | INSERT_MISSING_ONLY | Flat reference data |
| 21 | resort | `RESORT` | INSERT_MISSING_ONLY | Flat reference data |
| 22 | airport | `AIRPORT` | INSERT_MISSING_ONLY | Flat reference data |
| 23 | tourist_region | `TOURIST_REGION` | INSERT_MISSING_ONLY | Flat reference data |
| 24 | Holiday Setup - Flight Priority Search Setup | `WS_FLIGHT_PRIORITY` | FULL_SYNC | |

For full detail on any setup's conditions, business keys, and exclusions, read its `Builder(...)` block in [`SetupRegistry.java`](../src/main/java/com/strongcentsit/scriptcreator/config/SetupRegistry.java) — the setup names in that file match this table exactly.

## Choosing which setups run

Open [`SetupRegistry.java`](../src/main/java/com/strongcentsit/scriptcreator/config/SetupRegistry.java) and find:

```java
private static final Set<String> ACTIVE_SETUPS = Set.of(
//            "Finance - Option Rules"
        "Finance - Deposit Rules"
//            "Finance - Amd Cnx rules"
);
```

- List the exact setup name(s) (matching the "Setup Name" column above) you want to generate SQL for.
- Comment/uncomment or add/remove names as needed — this is a plain Java `Set.of(...)` literal, so keep the comma-separated syntax valid.
- **If `ACTIVE_SETUPS` is left empty, every one of the 24 registered setups will be generated** — useful for a full run, but make sure that's intentional before running against real data.
- Rebuild (`mvn compile`) after editing, then re-run `SetupScriptGeneratorApp`.

## Adding a new setup

1. Make sure the table(s) involved are present in `input/pk.xlsx`, `input/fk.xlsx`, and `input/columns.xlsx`.
2. Open [`SetupRegistry.java`](../src/main/java/com/strongcentsit/scriptcreator/config/SetupRegistry.java) and add a new `SETUP_CATALOG.put(...)` entry (or equivalent registration call) using `new SetupConfig.Builder("Your Setup Name", "MAIN_TABLE")` with whatever options from the [configuration reference](#configuration-reference) you need.
3. Add the new setup's name to `ACTIVE_SETUPS` so it actually gets generated.
4. If the table has a long name that would get truncated as an Excel sheet name, register it in [`TableNameMapper`](../src/main/java/com/strongcentsit/scriptcreator/config/TableNameMapper.java).
5. Rebuild and run.

## Troubleshooting

- **`[ERROR] Sheet '<name>' does not match schema metadata!`** — the sheet name in `SourceData.xlsx`/`TargetData.xlsx` doesn't match a known table and the automatic fuzzy header-matching (≥80% column overlap) failed too. Either rename the sheet to match the table name exactly, add a mapping in `TableNameMapper`, or check that the table's columns are present in `columns.xlsx`. The tool logs this and continues — it does not stop the run, so double-check that setup's output before trusting it.
- **`[WARNING] Truncated sheet detected: ... -> Auto-corrected to: ...`** — informational only; the tool matched a truncated sheet name to a table via header overlap. Verify the match is correct.
- **Nothing gets generated / output folder is empty** — check `ACTIVE_SETUPS` in `SetupRegistry.java`; if it lists setup names that don't exactly match the catalog, those setups are silently skipped.
- **Wrong starting ID for new records** — check whether the setup has an explicit `.nextStartId(...)`; if not, it's computed as `max(target PK) + 100`, which depends entirely on what's in `TargetData.xlsx` at generation time.
- **A trigger fires unexpectedly when running the generated script** — the disable/enable bracketing only covers triggers marked `ENABLED` in `triggers.xlsx` at generation time; if `triggers.xlsx` is stale or missing that trigger, it won't be included.
- **`mvn exec:java` fails to find a main class** — pass it explicitly, e.g. `-Dexec.mainClass=com.strongcentsit.scriptcreator.promo.PromoQueueGeneratorApp`, or run from an IDE instead (see [Running the tool](#running-the-tool)).
- **Generated `UPDATE EXISTING RECORD` block deletes a child table's rows fine but then fails to re-insert them (unique/primary-key constraint violation)** — a table listed in `.tableOperationExclusions(..., Set.of("DELETE"))` was meant to be spared only when *removing an orphaned (target-only) record*, but that exclusion also suppresses the delete-then-reinsert refresh a matched record's children go through on `UPDATE`, so stale rows are left behind before the `INSERT` runs. Move that table from `.tableOperationExclusions` to `.orphanDeleteExclusions` instead — see the warning under [Configuration reference](#configuration-reference). This was the root cause of exactly this failure in the `Finance - Deposit Rules` / `Finance - Amd Cnx rules` / `Finance - Option Rules` setups and has been fixed by switching them to `orphanDeleteExclusions`.

## Known limitations / dead code

Documented here so they're not mistaken for bugs in behavior you're relying on:

- `GenerationMode` (`MERGE_EXISTING` / `DELETE_ONLY` / `INSERT_ONLY`) is defined but never referenced by any code — `SyncMode` is what actually controls behavior.
- `SetupConfig.Builder.enabled(boolean)` has no effect — only `SetupRegistry.ACTIVE_SETUPS` controls which setups run.
- `CsvToExcelConverter.main` has hardcoded, machine-specific paths and must be edited before use — see [Feature 3](#feature-3-csv--excel-converter).
- `PromoQueueGeneratorApp` hardcodes `PromoType.ACCOM_SUPPLIERS` — there's no way to generate for another promo type without editing the source, and only `ACCOM_SUPPLIERS` has its workflow steps configured in `PromoConfigRegistry` anyway.
- The `output/final/` folder and any `Sequence_update.sql` file you may see referenced in old output are **not** generated by this tool — they reflect a manual review/finalization step some users do by hand before running scripts against a real target database. Treat the generator's direct output (in `output/`) as a draft to review, not a final deliverable.

Two previously-dead classes (an empty no-op `Main` class and an unused `TriggerUtils`, whose logic was always duplicated inside `MetadataUtils`) have been removed as part of the project's restructuring — they're mentioned here only so old notes referencing them make sense.
