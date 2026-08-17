-- ======================================================================
-- SETUP NAME : Finance - Amd Cnx rules, Finance - Deposit Rules, Finance - Option Rules
-- MAIN TABLE : RES_AMDCNX_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      BACKUP GENERATION SCRIPT                         
-- ======================================================================
-- Run these statements BEFORE applying changes to create table backups:

CREATE TABLE B_RES_AMDCNX_RULE AS SELECT * FROM RES_AMDCNX_RULE;
CREATE TABLE B_RES_SETUP_ASSIGNMENTS AS SELECT * FROM RES_SETUP_ASSIGNMENTS;
CREATE TABLE B_RES_AMDCNX_CHARGE AS SELECT * FROM RES_AMDCNX_CHARGE;
CREATE TABLE B_RES_AMD_CNXRULE_OPTION_STATU AS SELECT * FROM RES_AMD_CNXRULE_OPTION_STATUS;
CREATE TABLE B_RES_OPTION_RULE AS SELECT * FROM RES_OPTION_RULE;
CREATE TABLE B_RES_OPTION_DATE_RULE AS SELECT * FROM RES_OPTION_DATE_RULE;
CREATE TABLE B_RES_DEPOSIT_RULE AS SELECT * FROM RES_DEPOSIT_RULE;
CREATE TABLE B_RES_DEPOSIT_CURRENCY_RULE AS SELECT * FROM RES_DEPOSIT_CURRENCY_RULE;
CREATE TABLE B_RES_DEPOSIT_CURRENCY_RULE_TY AS SELECT * FROM RES_DEPOSIT_CURRENCY_RULE_TYPE;