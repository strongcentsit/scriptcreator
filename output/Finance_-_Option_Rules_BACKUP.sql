-- ======================================================================
-- SETUP NAME : Finance - Option Rules
-- MAIN TABLE : RES_OPTION_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      BACKUP GENERATION SCRIPT                         
-- ======================================================================
-- Run these statements BEFORE applying changes to create table backups:

CREATE TABLE B_RES_OPTION_RULE AS SELECT * FROM RES_OPTION_RULE;
CREATE TABLE B_RES_SETUP_ASSIGNMENTS AS SELECT * FROM RES_SETUP_ASSIGNMENTS;
CREATE TABLE B_RES_OPTION_DATE_RULE AS SELECT * FROM RES_OPTION_DATE_RULE;
