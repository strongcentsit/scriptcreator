-- ======================================================================
-- SETUP NAME : Finance - Option Rules
-- MAIN TABLE : RES_OPTION_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      ROLLBACK SCRIPT                                 
-- ======================================================================
-- Run these statements IF YOU NEED TO UNDO the applied changes:

-- Step 1: Clean up current modified tables (children first)
DELETE FROM RES_OPTION_DATE_RULE;
DELETE FROM RES_SETUP_ASSIGNMENTS;
DELETE FROM RES_OPTION_RULE;

-- Step 2: Restore data from backup tables
INSERT INTO RES_OPTION_RULE SELECT * FROM B_RES_OPTION_RULE;
INSERT INTO RES_SETUP_ASSIGNMENTS SELECT * FROM B_RES_SETUP_ASSIGNMENTS;
INSERT INTO RES_OPTION_DATE_RULE SELECT * FROM B_RES_OPTION_DATE_RULE;

-- Step 3: Drop backup tables after restoration
DROP TABLE B_RES_OPTION_RULE PURGE;
DROP TABLE B_RES_SETUP_ASSIGNMENTS PURGE;
DROP TABLE B_RES_OPTION_DATE_RULE PURGE;
