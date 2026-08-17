-- ======================================================================
-- SETUP NAME : Accounts - Single Use CC Eligibility
-- MAIN TABLE : SUCC_ELIGIBILITY_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      ROLLBACK SCRIPT                                 
-- ======================================================================
-- Run these statements IF YOU NEED TO UNDO the applied changes:

-- Step 1: Clean up current modified tables (children first)
DELETE FROM SUCC_H2H_SOURCES;
DELETE FROM SUCC_ELIGIBILITY_RULE_VALUE;
DELETE FROM SUCC_ELIGIBILITY_RULE_DETAIL;
DELETE FROM SUCC_ELIGIBILITY_RULE;

-- Step 2: Restore data from backup tables
INSERT INTO SUCC_ELIGIBILITY_RULE SELECT * FROM B_SUCC_ELIGIBILITY_RULE;
INSERT INTO SUCC_ELIGIBILITY_RULE_DETAIL SELECT * FROM B_SUCC_ELIGIBILITY_RULE_DETAIL;
INSERT INTO SUCC_ELIGIBILITY_RULE_VALUE SELECT * FROM B_SUCC_ELIGIBILITY_RULE_VALUE;
INSERT INTO SUCC_H2H_SOURCES SELECT * FROM B_SUCC_H2H_SOURCES;

-- Step 3: Drop backup tables after restoration
DROP TABLE B_SUCC_ELIGIBILITY_RULE PURGE;
DROP TABLE B_SUCC_ELIGIBILITY_RULE_DETAIL PURGE;
DROP TABLE B_SUCC_ELIGIBILITY_RULE_VALUE PURGE;
DROP TABLE B_SUCC_H2H_SOURCES PURGE;
