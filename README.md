# Script Creator

A Java/Maven command-line tool that generates ready-to-run Oracle SQL scripts for synchronizing business "setup" data (deposit rules, fee schemes, advisory notes, location reference data, etc.) between environments — for example, promoting configuration from Staging to Production — without connecting directly to a database. All input and output goes through Excel (`.xlsx`) files, and every generated change comes with matching backup and rollback scripts.

It also includes a second, independent generator for building SQL that enqueues environment-promotion jobs into a `pp_promo_queue` workflow table.

For a full walkthrough of every feature, every input file format, and how to add or change what gets generated, see the **[User Guide](docs/USER_GUIDE.md)**.

## What it does

- **Setup Script Generator** (`SetupScriptGeneratorApp`) — the tool's main feature. Diffs a source dataset against a target dataset (both exported to Excel) for ~24 pre-registered business "setups", and generates:
  - an `INSERT`/`UPDATE`/`DELETE` script per setup
  - a matching `_VALIDATE.sql` script — the same DML with no trigger `DISABLE`/`ENABLE` and no `COMMIT`, for reviewing before running the real thing
  - a matching `_BACKUP.sql` script (snapshot the affected tables before running the main script)
  - a matching `_ROLLBACK.sql` script (restore from the backup and undo the change)
  - one combined `Sequence_update.sql` per run, restarting every configured Oracle sequence past whatever the run inserted
  - automatic remapping of lookup-table codes (e.g. a "product combination" ID) that differ between environments, before generating any SQL
- **Promo Queue Generator** (`PromoQueueGeneratorApp`) — reads a list of source/target "products" and generates `INSERT` statements to enqueue a promotion job (with its steps and sub-steps) into `pp_promo_queue`.
- **CSV → Excel converter** (`CsvToExcelConverter`) — a standalone helper for turning a folder of `SQL*Plus`-exported `.csv` files into the single multi-sheet `.xlsx` format the tool expects as input.

See the [User Guide](docs/USER_GUIDE.md) for the complete list of registered setups, sync modes, configuration options, and input/output file formats.

## Requirements

- Java 11+
- Maven

## Quick start

1. Clone the repo and build it:
   ```bash
   mvn compile
   ```
2. Create an `input/` folder at the project root (see [User Guide § Input Files](docs/USER_GUIDE.md#input-files) for exact structure) containing:
   - `pk.xlsx`, `fk.xlsx`, `columns.xlsx`, `triggers.xlsx` — schema metadata exported from Oracle for the tables the setups migrate (see [User Guide § Regenerating the metadata files from Oracle](docs/USER_GUIDE.md#regenerating-the-metadata-files-from-oracle) for ready-to-run queries scoped to exactly those tables)
   - `SourceData.xlsx`, `TargetData.xlsx` — the actual data to sync (one sheet per table)
3. Open [`SetupRegistry.java`](src/main/java/com/strongcentsit/scriptcreator/config/SetupRegistry.java) and edit `ACTIVE_SETUPS` to list the setup name(s) you want to generate (see [User Guide § Choosing which setups run](docs/USER_GUIDE.md#choosing-which-setups-run)).
4. Run the main feature:
   ```bash
   mvn compile exec:java
   ```
   (this runs `SetupScriptGeneratorApp` by default — see the [User Guide](docs/USER_GUIDE.md#running-the-tool) for running the promo generator instead, or running from an IDE).
5. Find the generated `.sql`, `_VALIDATE.sql`, `_BACKUP.sql`, and `_ROLLBACK.sql` files in `output/`.

`input/` and `output/` are gitignored — they hold local data and generated artifacts and are never committed.

## Project layout

```
src/main/java/com/strongcentsit/scriptcreator/
  setupgenerator/SetupScriptGeneratorApp.java   Entry point: Setup Script Generator (main feature)
  promo/PromoQueueGeneratorApp.java             Entry point: Promo Queue Generator
  promo/                                        Promo queue config & SQL generation
  config/                                       Setup registry & configuration knobs
  metadata/                                     Schema metadata model (tables, PKs, FKs, columns)
  util/                                         Excel I/O, SQL generation, validation helpers
```

## Documentation

- **[docs/USER_GUIDE.md](docs/USER_GUIDE.md)** — full guide: every feature, every registered setup, config options, input/output file formats, and known limitations.
