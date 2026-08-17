-- ======================================================================
-- SETUP NAME : Finance - Deposit Rules
-- MAIN TABLE : RES_DEPOSIT_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      BACKUP GENERATION SCRIPT                         
-- ======================================================================
-- Run these statements BEFORE applying changes to create table backups:

CREATE TABLE B_RES_DEPOSIT_RULE AS SELECT * FROM RES_DEPOSIT_RULE;
CREATE TABLE B_RES_SETUP_ASSIGNMENTS AS SELECT * FROM RES_SETUP_ASSIGNMENTS;
CREATE TABLE B_RES_DEPOSIT_CURRENCY_RULE AS SELECT * FROM RES_DEPOSIT_CURRENCY_RULE;
CREATE TABLE B_RES_DEPOSIT_CURRENCY_RULE_TY AS SELECT * FROM RES_DEPOSIT_CURRENCY_RULE_TYPE;
