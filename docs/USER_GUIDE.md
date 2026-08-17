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

| File | Columns (by position) |
|---|---|
| `input/columns.xlsx` | A: Table Name · B: Column Name |
| `input/pk.xlsx` | A: Table Name · B: Primary Key Column Name |
| `input/fk.xlsx` | A: Child Table · B: Child Column · C: Parent Table · D: Parent Column · E: FK Constraint Name |
| `input/triggers.xlsx` (optional) | A: Table Name · B: Trigger Name · E: Status (only rows with status `ENABLED` are used) |
| `input/SourceData.xlsx` / `input/TargetData.xlsx` | One sheet per table; sheet name = table name; row 1 = column headers matching `columns.xlsx` |
| `input/promo/source_products.xlsx` | A: Code · B: Name |
| `input/promo/target_products.xlsx` | A: Code |

**Sheet naming for `SourceData.xlsx` / `TargetData.xlsx`:** Excel sheet names are capped at 31 characters, so long table names get truncated. The tool tries to auto-resolve truncated names by matching ≥80% of the sheet's headers against a known table's columns, and logs a `[WARNING] Truncated sheet detected: ... -> Auto-corrected to: ...` when it does. Three known-truncated names are pre-registered in [`TableNameMapper`](../src/main/java/com/strongcentsit/scriptcreator/config/TableNameMapper.java) for `CALC_SCHEME_MARKUP_CROSS_COMPONENT_*` tables — if you add a table with a long name, you may need to register it there too, or rename its sheet manually. A sheet named exactly `SQL` is always ignored (used for a raw query dump if present).

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
    .tableOperationExclusions(Map.of(
        "RES_DEPOSIT_RULE", Set.of("DELETE"),
        "RES_DEPOSIT_CURRENCY_RULE", Set.of("DELETE")
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
| `.tableOperationExclusions(Map<String, Set<String>>)` | Suppress specific SQL operations (`INSERT`, `UPDATE`, `DELETE`, or `ALL`) for a table, e.g. never delete `RES_DEPOSIT_RULE` rows. |
| `.globalFkMappings(Map<String, Set<String>>)` | Declares that a foreign key on a child table (`"ChildTable.Column"`) points at a table registered in `GlobalBusinessKeyConfig`, so its value gets remapped from the source PK to the target PK before syncing. |
| `.enabled(boolean)` | Present on the builder but **not currently checked anywhere** — has no effect. Use `ACTIVE_SETUPS` in `SetupRegistry` to enable/disable a setup instead. |

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
| 6 | Finance - Amd Cnx rules | `RES_AMDCNX_RULE` | FULL_SYNC | New IDs start at 1800; deletes suppressed on 3 tables |
| 7 | Finance - Deposit Rules | `RES_DEPOSIT_RULE` | FULL_SYNC | New IDs start at 1900; deletes suppressed on 3 tables |
| 8 | Finance - Option Rules | `RES_OPTION_RULE` | FULL_SYNC | New IDs start at 1300; deletes suppressed on 2 tables |
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

## Known limitations / dead code

Documented here so they're not mistaken for bugs in behavior you're relying on:

- `GenerationMode` (`MERGE_EXISTING` / `DELETE_ONLY` / `INSERT_ONLY`) is defined but never referenced by any code — `SyncMode` is what actually controls behavior.
- `SetupConfig.Builder.enabled(boolean)` has no effect — only `SetupRegistry.ACTIVE_SETUPS` controls which setups run.
- `CsvToExcelConverter.main` has hardcoded, machine-specific paths and must be edited before use — see [Feature 3](#feature-3-csv--excel-converter).
- `PromoQueueGeneratorApp` hardcodes `PromoType.ACCOM_SUPPLIERS` — there's no way to generate for another promo type without editing the source, and only `ACCOM_SUPPLIERS` has its workflow steps configured in `PromoConfigRegistry` anyway.
- The `output/final/` folder and any `Sequence_update.sql` file you may see referenced in old output are **not** generated by this tool — they reflect a manual review/finalization step some users do by hand before running scripts against a real target database. Treat the generator's direct output (in `output/`) as a draft to review, not a final deliverable.

Two previously-dead classes (an empty no-op `Main` class and an unused `TriggerUtils`, whose logic was always duplicated inside `MetadataUtils`) have been removed as part of the project's restructuring — they're mentioned here only so old notes referencing them make sense.
