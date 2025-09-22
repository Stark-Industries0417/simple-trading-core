INSERT INTO accounts (user_id, cash_balance, available_cash, version, created_at, updated_at) VALUES
    ('USER001', 1000000.0000, 1000000.0000, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)), -- BUY 테스트용 자금
    ('USER002', 100000.0000, 100000.0000, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))    -- SELL 테스트용 자금
    ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- ========================================
-- 테스트용 주식 보유 데이터 (최소 설정)
-- ========================================

-- USER001: 주식 미보유 (BUY 전용)
-- USER002: SELL 테스트용 기본 보유 주식
INSERT INTO stock_holdings (user_id, symbol, quantity, available_quantity, average_price, version, created_at, updated_at) VALUES
    ('USER002', 'AAPL', 100.0000, 100.0000, 145.0000, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)), -- AAPL 100주 보유
    ('USER002', 'GOOGL', 50.0000, 50.0000, 2800.0000, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)), -- GOOGL 50주 보유
    ('USER002', 'MSFT', 75.0000, 75.0000, 380.0000, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))    -- MSFT 75주 보유
    ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);