-- ======================================================================
-- SETUP NAME : Document - Document Rules
-- MAIN TABLE : CALC_SCHEME
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      BACKUP GENERATION SCRIPT                         
-- ======================================================================
-- Run these statements BEFORE applying changes to create table backups:

CREATE TABLE B_CALC_SCHEME AS SELECT * FROM CALC_SCHEME;
CREATE TABLE B_CALC_DOCUMENT_SCHEME AS SELECT * FROM CALC_DOCUMENT_SCHEME;
CREATE TABLE B_CALC_RULE_BKG_ITEM_STATUS AS SELECT * FROM CALC_RULE_BKG_ITEM_STATUS;
CREATE TABLE B_CALC_RULE_BKG_STATUS AS SELECT * FROM CALC_RULE_BKG_STATUS;
CREATE TABLE B_CALC_RULE_COMPANY AS SELECT * FROM CALC_RULE_COMPANY;
CREATE TABLE B_CALC_RULE_OPTION_STATUS AS SELECT * FROM CALC_RULE_OPTION_STATUS;
CREATE TABLE B_CALC_SCHEME_RULE_PRIORITY AS SELECT * FROM CALC_SCHEME_RULE_PRIORITY;
CREATE TABLE B_CALC_RULE_PROD_COMBINATION AS SELECT * FROM CALC_RULE_PROD_COMBINATION;
CREATE TABLE B_CALC_RULE_LINK AS SELECT * FROM CALC_RULE_LINK;
