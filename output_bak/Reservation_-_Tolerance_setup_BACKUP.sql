-- ======================================================================
-- SETUP NAME : Reservation - Tolerance setup
-- MAIN TABLE : RATE_TOLERANCE_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      BACKUP GENERATION SCRIPT                         
-- ======================================================================
-- Run these statements BEFORE applying changes to create table backups:

CREATE TABLE B_RATE_TOLERANCE_RULE AS SELECT * FROM RATE_TOLERANCE_RULE;
