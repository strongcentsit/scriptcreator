-- ======================================================================
-- SETUP NAME : Finance - Amd Cnx rules
-- MAIN TABLE : RES_AMDCNX_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      ROLLBACK SCRIPT                                 
-- ======================================================================
-- Run these statements IF YOU NEED TO UNDO the applied changes:

-- Step 1: Clean up current modified tables (children first)
DELETE FROM RES_AMD_CNXRULE_OPTION_STATUS;
DELETE FROM RES_AMDCNX_CHARGE;
DELETE FROM RES_SETUP_ASSIGNMENTS;
DELETE FROM RES_AMDCNX_RULE;

-- Step 2: Restore data from backup tables
INSERT INTO RES_AMDCNX_RULE SELECT * FROM B_RES_AMDCNX_RULE;
INSERT INTO RES_SETUP_ASSIGNMENTS SELECT * FROM B_RES_SETUP_ASSIGNMENTS;
INSERT INTO RES_AMDCNX_CHARGE SELECT * FROM B_RES_AMDCNX_CHARGE;
INSERT INTO RES_AMD_CNXRULE_OPTION_STATUS SELECT * FROM B_RES_AMD_CNXRULE_OPTION_STATU;

-- Step 3: Drop backup tables after restoration
DROP TABLE B_RES_AMDCNX_RULE PURGE;
DROP TABLE B_RES_SETUP_ASSIGNMENTS PURGE;
DROP TABLE B_RES_AMDCNX_CHARGE PURGE;
DROP TABLE B_RES_AMD_CNXRULE_OPTION_STATU PURGE;
