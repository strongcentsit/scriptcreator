-- ======================================================================
-- SETUP NAME : Reservation - Tolerance setup
-- MAIN TABLE : RATE_TOLERANCE_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      ROLLBACK SCRIPT                                 
-- ======================================================================
-- Run these statements IF YOU NEED TO UNDO the applied changes:

-- Step 1: Clean up current modified tables (children first)
DELETE FROM RATE_TOLERANCE_RULE;

-- Step 2: Restore data from backup tables
INSERT INTO RATE_TOLERANCE_RULE SELECT * FROM B_RATE_TOLERANCE_RULE;

-- Step 3: Drop backup tables after restoration
DROP TABLE B_RATE_TOLERANCE_RULE PURGE;
