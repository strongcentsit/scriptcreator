-- ======================================================================
-- SETUP NAME : Finance - Rounding Rules Setup
-- MAIN TABLE : ROUNDING_RULE
-- SYNC MODE  : FULL_SYNC
-- ======================================================================

-- ======================================================================
--                      DISABLE AFFECTED TRIGGERS                        
-- ======================================================================
ALTER TRIGGER AUD_ROUNDING_RULE DISABLE;
ALTER TRIGGER LMT_ROUNDING_RULE DISABLE;

-- --- [2] UPDATE EXISTING RECORD: (RULE_ID=3) (Target PK: 3) ---
UPDATE ROUNDING_RULE
SET COMPANY = 'BAH', DIVISION = 'BA', BRAND = '--Any--', DISTRIBUTION_CHANNEL = '--Any--', SELLING_CURRENCY = '--Any--', PRODUCT_TYPE = '--Any--', PRODUCT_SOURCE = -1, ROUNDING_VALUE = 1, ROUNDING_TYPE = NULL, ROUNDING_DIRECTION = 'UP', LAST_MODIFIED_TIME = SYSDATE
WHERE RULE_ID = 3;

-- ======================================================================
--                      RE-ENABLE TRIGGERS                              
-- ======================================================================
ALTER TRIGGER AUD_ROUNDING_RULE ENABLE;
ALTER TRIGGER LMT_ROUNDING_RULE ENABLE;

