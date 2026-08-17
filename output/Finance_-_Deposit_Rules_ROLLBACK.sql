-- ======================================================================
-- SETUP NAME : Finance - Deposit Rules
-- MAIN TABLE : RES_DEPOSIT_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      ROLLBACK SCRIPT                                 
-- ======================================================================
-- Run these statements IF YOU NEED TO UNDO the applied changes:

-- Step 1: Clean up current modified tables (children first)
DELETE FROM RES_DEPOSIT_CURRENCY_RULE_TYPE;
DELETE FROM RES_DEPOSIT_CURRENCY_RULE;
DELETE FROM RES_SETUP_ASSIGNMENTS;
DELETE FROM RES_DEPOSIT_RULE;

-- Step 2: Restore data from backup tables
INSERT INTO RES_DEPOSIT_RULE SELECT * FROM B_RES_DEPOSIT_RULE;
INSERT INTO RES_SETUP_ASSIGNMENTS SELECT * FROM B_RES_SETUP_ASSIGNMENTS;
INSERT INTO RES_DEPOSIT_CURRENCY_RULE SELECT * FROM B_RES_DEPOSIT_CURRENCY_RULE;
INSERT INTO RES_DEPOSIT_CURRENCY_RULE_TYPE SELECT * FROM B_RES_DEPOSIT_CURRENCY_RULE_TY;

-- Step 3: Drop backup tables after restoration
DROP TABLE B_RES_DEPOSIT_RULE PURGE;
DROP TABLE B_RES_SETUP_ASSIGNMENTS PURGE;
DROP TABLE B_RES_DEPOSIT_CURRENCY_RULE PURGE;
DROP TABLE B_RES_DEPOSIT_CURRENCY_RULE_TY PURGE;
