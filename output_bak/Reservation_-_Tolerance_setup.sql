-- ======================================================================
-- SETUP NAME : Reservation - Tolerance setup
-- MAIN TABLE : RATE_TOLERANCE_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      DISABLE AFFECTED TRIGGERS                        
-- ======================================================================
ALTER TRIGGER AUD_RATE_TOLERANCE_RULE DISABLE;

-- --- [1] DELETE REMOVED RECORD (Target Only): (RULE_ID=25) ---
DELETE FROM RATE_TOLERANCE_RULE WHERE RULE_ID = 25;

-- --- [1] DELETE REMOVED RECORD (Target Only): (RULE_ID=26) ---
DELETE FROM RATE_TOLERANCE_RULE WHERE RULE_ID = 26;

-- --- [1] DELETE REMOVED RECORD (Target Only): (RULE_ID=23) ---
DELETE FROM RATE_TOLERANCE_RULE WHERE RULE_ID = 23;

-- --- [3] INSERT NEW RECORD: (RULE_ID=15) (Assigned New PK: 127) ---
INSERT INTO RATE_TOLERANCE_RULE (RULE_ID, H2H_ID, COMPANY, DIVISION, BRAND, DISTRIBUTION_CHANNEL, FROM_DATE, TO_DATE, DEP_FROM_DATE, DEP_TO_DATE, SELLING_CURRENCY, AMOUNT, PERCENTAGE, PRIORITY, LAST_MODIFIED_TIME, DESTINATION_CODE, DESTINATION_TYPE)
VALUES (127, 84, 'ALL', 'ALL', 'ALL', 'A', '08-OCT-25', '29-JUL-26', '08-OCT-25', '29-JUL-26', 'ALL', 1000, 99, 1, SYSDATE, 'ALL', 'AIRPORT');

-- --- [3] INSERT NEW RECORD: (RULE_ID=19) (Assigned New PK: 128) ---
INSERT INTO RATE_TOLERANCE_RULE (RULE_ID, COMPANY, DIVISION, BRAND, DISTRIBUTION_CHANNEL, FROM_DATE, TO_DATE, DEP_FROM_DATE, DEP_TO_DATE, SELLING_CURRENCY, AMOUNT, PERCENTAGE, PRIORITY, LAST_MODIFIED_TIME, DESTINATION_CODE, DESTINATION_TYPE, PRODUCT_COMBINATION, PRODUCT_TYPE)
VALUES (128, 'BAH', 'BA', 'B3', 'A', '01-NOV-25', '31-DEC-26', '01-NOV-25', '31-DEC-26', 'ALL', 130, 5, 99, SYSDATE, 'ALL', 'ALL', 1, 'PKG');

-- ======================================================================
--                      RE-ENABLE TRIGGERS                              
-- ======================================================================
ALTER TRIGGER AUD_RATE_TOLERANCE_RULE ENABLE;

