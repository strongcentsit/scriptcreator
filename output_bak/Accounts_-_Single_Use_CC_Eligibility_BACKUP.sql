-- ======================================================================
-- SETUP NAME : Accounts - Single Use CC Eligibility
-- MAIN TABLE : SUCC_ELIGIBILITY_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      BACKUP GENERATION SCRIPT                         
-- ======================================================================
-- Run these statements BEFORE applying changes to create table backups:

CREATE TABLE B_SUCC_ELIGIBILITY_RULE AS SELECT * FROM SUCC_ELIGIBILITY_RULE;
CREATE TABLE B_SUCC_ELIGIBILITY_RULE_DETAIL AS SELECT * FROM SUCC_ELIGIBILITY_RULE_DETAIL;
CREATE TABLE B_SUCC_ELIGIBILITY_RULE_VALUE AS SELECT * FROM SUCC_ELIGIBILITY_RULE_VALUE;
CREATE TABLE B_SUCC_H2H_SOURCES AS SELECT * FROM SUCC_H2H_SOURCES;
