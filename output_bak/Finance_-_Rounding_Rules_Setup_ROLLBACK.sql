-- ======================================================================
-- SETUP NAME : Finance - Rounding Rules Setup
-- MAIN TABLE : ROUNDING_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      ROLLBACK SCRIPT                                 
-- ======================================================================
-- Run these statements IF YOU NEED TO UNDO the applied changes:

-- Step 1: Clean up current modified tables (children first)
DELETE FROM ROUNDING_RULE;

-- Step 2: Restore data from backup tables
INSERT INTO ROUNDING_RULE SELECT * FROM B_ROUNDING_RULE;

-- Step 3: Drop backup tables after restoration
DROP TABLE B_ROUNDING_RULE PURGE;
