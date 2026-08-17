-- ======================================================================
-- SETUP NAME : Finance - Local Fee Schemes
-- MAIN TABLE : CALC_SCHEME
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      BACKUP GENERATION SCRIPT                         
-- ======================================================================
-- Run these statements BEFORE applying changes to create table backups:

CREATE TABLE B_CALC_SCHEME AS SELECT * FROM CALC_SCHEME;
CREATE TABLE B_CALC_RULE_CITY AS SELECT * FROM CALC_RULE_CITY;
CREATE TABLE B_CALC_RULE_STAR_RATING AS SELECT * FROM CALC_RULE_STAR_RATING;
CREATE TABLE B_CALC_SCHEME_FEES_AND_TAXES AS SELECT * FROM CALC_SCHEME_FEES_AND_TAXES;
CREATE TABLE B_CALC_SCHEME_RULE_PRIORITY AS SELECT * FROM CALC_SCHEME_RULE_PRIORITY;
CREATE TABLE B_CALC_RULE_BRAND AS SELECT * FROM CALC_RULE_BRAND;
CREATE TABLE B_CALC_RULE_STATE AS SELECT * FROM CALC_RULE_STATE;
CREATE TABLE B_CALC_RULE_SUPPLIER AS SELECT * FROM CALC_RULE_SUPPLIER;
CREATE TABLE B_CALC_RULE_LINK AS SELECT * FROM CALC_RULE_LINK;
