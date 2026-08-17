-- ======================================================================
-- SETUP NAME : Calculation Scheme - Type X
-- MAIN TABLE : CALC_SCHEME
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      BACKUP GENERATION SCRIPT                         
-- ======================================================================
-- Run these statements BEFORE applying changes to create table backups:

CREATE TABLE B_CALC_SCHEME AS SELECT * FROM CALC_SCHEME;
CREATE TABLE B_CALC_RULE_BRAND AS SELECT * FROM CALC_RULE_BRAND;
CREATE TABLE B_CALC_RULE_COMPANY AS SELECT * FROM CALC_RULE_COMPANY;
CREATE TABLE B_CALC_RULE_DIST_CHANNEL AS SELECT * FROM CALC_RULE_DIST_CHANNEL;
CREATE TABLE B_CALC_RULE_DIVISION AS SELECT * FROM CALC_RULE_DIVISION;
CREATE TABLE B_CALC_SCHEME_RULE_PRIORITY AS SELECT * FROM CALC_SCHEME_RULE_PRIORITY;
CREATE TABLE B_CALC_RULE_PROD_COMBINATION AS SELECT * FROM CALC_RULE_PROD_COMBINATION;
CREATE TABLE B_CALC_RULE_SOURCE_MARKET AS SELECT * FROM CALC_RULE_SOURCE_MARKET;
CREATE TABLE B_CALC_RULE_CITY AS SELECT * FROM CALC_RULE_CITY;
