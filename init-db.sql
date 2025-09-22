-- ========================================
-- Simple Trading Core - Database Schema
-- Updated to match JPA Entity definitions
-- ========================================

CREATE DATABASE IF NOT EXISTS trading_core;
USE trading_core;

-- ========================================
-- Order Module Tables
-- ========================================

-- Orders Table (matches Order.kt Entity)
CREATE TABLE IF NOT EXISTS orders (
    id             VARCHAR(50) PRIMARY KEY,
    user_id        VARCHAR(50)    NOT NULL,
    symbol         VARCHAR(20)    NOT NULL,
    order_type     VARCHAR(10)    NOT NULL,  -- MARKET, LIMIT
    side           VARCHAR(4)     NOT NULL,  -- BUY, SELL
    quantity       DECIMAL(19, 8) NOT NULL,
    price          DECIMAL(19, 2) NULL,      -- NULL for market orders
    status         VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    created_at     TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT         NOT NULL DEFAULT 0,
    filled_quantity DECIMAL(19, 8) NOT NULL DEFAULT 0,
    cancellation_reason VARCHAR(500) NULL,
    filled_at      TIMESTAMP(6)   NULL,

    INDEX idx_user_created (user_id, created_at),
    INDEX idx_symbol_status (symbol, status),
    INDEX idx_created_at (created_at desc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Order Outbox Events Table (matches OrderOutboxEvent.kt Entity)
CREATE TABLE IF NOT EXISTS order_outbox_events (
    event_id        VARCHAR(255) PRIMARY KEY,
    saga_id         VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    order_id        VARCHAR(50)  NOT NULL,
    user_id         VARCHAR(50)  NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    order_type      VARCHAR(20)  NOT NULL,
    side            VARCHAR(10)  NOT NULL,
    quantity        DECIMAL(19, 8) NOT NULL,
    price           DECIMAL(19, 8) NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_order_outbox_saga (saga_id),
    INDEX idx_order_outbox_created (created_at),
    INDEX idx_order_outbox_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- Account Module Tables
-- ========================================

-- Accounts Table (matches Account.kt Entity)
CREATE TABLE IF NOT EXISTS accounts (
    user_id         VARCHAR(50) PRIMARY KEY,
    cash_balance    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    available_cash  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CHECK (cash_balance >= 0),
    CHECK (available_cash >= 0),
    CHECK (available_cash <= cash_balance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stock Holdings Table (matches StockHolding.kt Entity)
CREATE TABLE IF NOT EXISTS stock_holdings (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(50)    NOT NULL,
    symbol              VARCHAR(10)    NOT NULL,
    quantity            DECIMAL(19, 4) NOT NULL DEFAULT 0,
    available_quantity  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    average_price       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_user_symbol (user_id, symbol),
    INDEX idx_stock_holdings_user_symbol (user_id, symbol),
    CHECK (quantity >= 0),
    CHECK (available_quantity >= 0),
    CHECK (available_quantity <= quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Transaction Logs Table (matches TransactionLog.kt Entity)
CREATE TABLE IF NOT EXISTS transaction_logs (
    transaction_id  VARCHAR(50) PRIMARY KEY,
    user_id         VARCHAR(50)    NOT NULL,
    trade_id        VARCHAR(50)    NOT NULL,
    type            VARCHAR(20)    NOT NULL,  -- BUY, SELL, DEPOSIT, WITHDRAWAL, ROLLBACK
    symbol          VARCHAR(10)    NOT NULL,
    quantity        DECIMAL(19, 4) NOT NULL,
    price           DECIMAL(19, 4) NOT NULL,
    amount          DECIMAL(19, 4) NOT NULL,
    balance_before  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    balance_after   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    description     VARCHAR(500)   NULL,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_transaction_logs_user_id (user_id),
    INDEX idx_transaction_logs_trade_id (trade_id),
    INDEX idx_transaction_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Reservation Info Table (matches ReservationInfo.kt Entity)
CREATE TABLE IF NOT EXISTS reservation_info (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        VARCHAR(50)    NOT NULL UNIQUE,
    user_id         VARCHAR(50)    NOT NULL,
    symbol          VARCHAR(20)    NOT NULL,
    side            VARCHAR(4)     NOT NULL,  -- BUY, SELL
    quantity        DECIMAL(19, 8) NOT NULL,
    price           DECIMAL(19, 2) NULL,
    reserved_amount DECIMAL(19, 4) NULL,      -- BUY 주문의 경우 예약 금액
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, CONFIRMED, RELEASED, EXPIRED
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    INDEX idx_reservation_order_id (order_id),
    INDEX idx_reservation_user_id (user_id),
    INDEX idx_reservation_status (status),
    INDEX idx_reservation_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Account Outbox Events Table (matches AccountOutboxEvent.kt Entity)
CREATE TABLE IF NOT EXISTS account_outbox_events (
    event_id        VARCHAR(255) PRIMARY KEY,
    saga_id         VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    trade_id        VARCHAR(50)  NOT NULL,
    order_id        VARCHAR(50)  NOT NULL,
    buy_user_id     VARCHAR(50)  NOT NULL,
    sell_user_id    VARCHAR(50)  NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    amount          DECIMAL(19, 8) NOT NULL,
    quantity        DECIMAL(19, 8) NOT NULL,
    buyer_new_balance  DECIMAL(19, 8) NULL,
    seller_new_balance DECIMAL(19, 8) NULL,
    failure_type    VARCHAR(50)  NULL,
    reason          VARCHAR(500) NULL,
    should_retry    BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSED, FAILED, RETRY
    processed_at    TIMESTAMP(6) NULL,
    error_message   VARCHAR(500) NULL,
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_account_outbox_saga (saga_id),
    INDEX idx_account_outbox_created (created_at),
    INDEX idx_account_outbox_trade (trade_id),
    INDEX idx_account_outbox_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- Matching Module Tables
-- ========================================

-- Matchings Table (matches Matching.kt Entity)
CREATE TABLE IF NOT EXISTS matchings (
    id              VARCHAR(50) PRIMARY KEY,
    trade_id        VARCHAR(50)    NOT NULL UNIQUE,
    buy_order_id    VARCHAR(50)    NOT NULL,
    sell_order_id   VARCHAR(50)    NOT NULL,
    buy_user_id     VARCHAR(50)    NOT NULL,
    sell_user_id    VARCHAR(50)    NOT NULL,
    symbol          VARCHAR(20)    NOT NULL,
    quantity        DECIMAL(19, 8) NOT NULL,
    price           DECIMAL(19, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSED, FAILED, RETRY
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    processed_at    TIMESTAMP(6)   NULL,
    error_message   VARCHAR(500)   NULL,
    retry_count     INT            NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,

    INDEX idx_matching_trade_id (trade_id),
    INDEX idx_matching_buy_order (buy_order_id),
    INDEX idx_matching_sell_order (sell_order_id),
    INDEX idx_matching_symbol (symbol),
    INDEX idx_matching_status (status),
    INDEX idx_matching_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Matching Outbox Events Table (matches MatchingOutboxEvent.kt Entity)
CREATE TABLE IF NOT EXISTS matching_outbox_events (
    event_id        VARCHAR(255) PRIMARY KEY,
    saga_id         VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    buy_order_id    VARCHAR(50)  NOT NULL,
    sell_order_id   VARCHAR(50)  NOT NULL,
    buy_user_id     VARCHAR(50)  NOT NULL,
    sell_user_id    VARCHAR(50)  NOT NULL,
    symbol          VARCHAR(20)  NOT NULL,
    quantity DECIMAL(19, 8) NOT NULL,
    price   DECIMAL(19, 8) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    processed_at    TIMESTAMP(6) NULL,
    error_message   VARCHAR(500) NULL,
    retry_count     INT          NOT NULL DEFAULT 0,
    trade_id        VARCHAR(255) NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_matching_outbox_saga (saga_id),
    INDEX idx_matching_outbox_created (created_at),
    INDEX idx_matching_outbox_trade (trade_id),
    INDEX idx_matching_outbox_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- Market Data Tables (Optional)
-- ========================================

-- Stock Info Table (종목 정보)
CREATE TABLE IF NOT EXISTS stock_info (
    symbol          VARCHAR(10) PRIMARY KEY,
    name            VARCHAR(100)   NOT NULL,
    market_cap      DECIMAL(19, 4) NULL,
    sector          VARCHAR(50)    NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, SUSPENDED, DELISTED
    listing_date    DATE           NULL,
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    INDEX idx_status (status),
    INDEX idx_sector (sector)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Market Data Table (실시간 시세)
CREATE TABLE IF NOT EXISTS market_data (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol      VARCHAR(10)    NOT NULL,
    price       DECIMAL(19, 4) NOT NULL,
    volume      BIGINT         NOT NULL,
    high        DECIMAL(19, 4) NOT NULL,
    low         DECIMAL(19, 4) NOT NULL,
    open        DECIMAL(19, 4) NOT NULL,
    close       DECIMAL(19, 4) NOT NULL,
    timestamp   TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_symbol_timestamp (symbol, timestamp),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- 초기 데이터 삽입
-- ========================================

-- 기본 종목 정보 삽입
INSERT INTO stock_info (symbol, name, sector, status) VALUES
    ('AAPL', 'Apple Inc.', 'Technology', 'ACTIVE'),
    ('GOOGL', 'Alphabet Inc.', 'Technology', 'ACTIVE'),
    ('MSFT', 'Microsoft Corporation', 'Technology', 'ACTIVE'),
    ('AMZN', 'Amazon.com Inc.', 'E-Commerce', 'ACTIVE'),
    ('TSLA', 'Tesla Inc.', 'Automotive', 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- ========================================
-- 테스트 계정 데이터 (다양한 시나리오 지원)
-- ========================================

-- USER001: 충분한 자금과 주식 보유 (일반 거래 테스트)
-- USER002: 중간 수준 자금과 일부 주식 보유 (혼합 테스트)
-- USER003: 대량 자금, 주식 미보유 (BUY 전용 테스트)
-- USER004: 소액 자금, 다양한 주식 보유 (자금 부족 테스트)
-- USER005: 제로 자금, 소량 주식 보유 (SELL 전용 테스트)
INSERT INTO accounts (user_id, cash_balance, available_cash) VALUES
    ('USER001', 1000000.0000, 950000.0000),    -- 일부 자금 예약 상태
    ('USER002', 500000.0000, 450000.0000),     -- 일부 자금 예약 상태
    ('USER003', 2000000.0000, 2000000.0000),   -- 전액 사용 가능
    ('USER004', 10000.0000, 5000.0000),        -- 소액, 일부 예약
    ('USER005', 0.0000, 0.0000)                -- 자금 없음 (SELL만 가능)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- ========================================
-- 테스트용 주식 보유 데이터 (다양한 시나리오)
-- ========================================

-- USER001: 다양한 종목 보유 (일반 거래 테스트)
INSERT INTO stock_holdings (user_id, symbol, quantity, available_quantity, average_price) VALUES
    ('USER001', 'AAPL', 100.0000, 90.0000, 150.0000),     -- 일부 예약
    ('USER001', 'GOOGL', 50.0000, 50.0000, 2800.0000),    -- 전량 사용 가능
    ('USER001', 'MSFT', 75.0000, 70.0000, 380.0000),      -- 일부 예약
    ('USER001', 'TSLA', 30.0000, 30.0000, 250.0000)       -- 전량 사용 가능
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- USER002: 일부 종목만 보유 (혼합 테스트)
INSERT INTO stock_holdings (user_id, symbol, quantity, available_quantity, average_price) VALUES
    ('USER002', 'GOOGL', 20.0000, 15.0000, 2850.0000),    -- 일부 예약
    ('USER002', 'AMZN', 10.0000, 10.0000, 180.0000),      -- 전량 사용 가능
    ('USER002', 'AAPL', 200.0000, 180.0000, 145.0000)     -- 대량 보유, 일부 예약
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- USER003: 주식 미보유 (BUY 전용 테스트)
-- 의도적으로 주식 보유 없음

-- USER004: 소량 주식 보유 (자금 부족 테스트)
INSERT INTO stock_holdings (user_id, symbol, quantity, available_quantity, average_price) VALUES
    ('USER004', 'AAPL', 5.0000, 5.0000, 155.0000),        -- 소량 보유
    ('USER004', 'MSFT', 3.0000, 3.0000, 390.0000),        -- 소량 보유
    ('USER004', 'TSLA', 1.0000, 0.0000, 260.0000)         -- 전량 예약 (SELL 불가)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- USER005: 다양한 주식 보유, 자금 없음 (SELL 전용)
INSERT INTO stock_holdings (user_id, symbol, quantity, available_quantity, average_price) VALUES
    ('USER005', 'AAPL', 50.0000, 50.0000, 140.0000),
    ('USER005', 'GOOGL', 10.0000, 10.0000, 2750.0000),
    ('USER005', 'MSFT', 25.0000, 25.0000, 370.0000),
    ('USER005', 'AMZN', 15.0000, 15.0000, 175.0000),
    ('USER005', 'TSLA', 20.0000, 20.0000, 240.0000)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- ========================================
-- 예약 정보 테스트 데이터 (진행 중인 주문 시뮬레이션)
-- ========================================

-- USER001의 진행 중인 BUY 주문 예약
INSERT INTO reservation_info (order_id, user_id, symbol, side, quantity, price, reserved_amount, status) VALUES
    ('ORDER-001-PENDING', 'USER001', 'AAPL', 'BUY', 10.0000, 145.00, 1450.0000, 'ACTIVE'),
    ('ORDER-002-PENDING', 'USER001', 'MSFT', 'BUY', 20.0000, 375.00, 7500.0000, 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- USER001의 진행 중인 SELL 주문 예약
INSERT INTO reservation_info (order_id, user_id, symbol, side, quantity, price, reserved_amount, status) VALUES
    ('ORDER-003-PENDING', 'USER001', 'AAPL', 'SELL', 10.0000, 155.00, NULL, 'ACTIVE'),
    ('ORDER-004-PENDING', 'USER001', 'MSFT', 'SELL', 5.0000, 385.00, NULL, 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- USER002의 진행 중인 주문 예약
INSERT INTO reservation_info (order_id, user_id, symbol, side, quantity, price, reserved_amount, status) VALUES
    ('ORDER-005-PENDING', 'USER002', 'GOOGL', 'SELL', 5.0000, 2900.00, NULL, 'ACTIVE'),
    ('ORDER-006-PENDING', 'USER002', 'AAPL', 'SELL', 20.0000, 150.00, NULL, 'ACTIVE'),
    ('ORDER-007-PENDING', 'USER002', 'TSLA', 'BUY', 10.0000, 245.00, 2450.0000, 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- USER004의 자금 부족 시나리오를 위한 예약
INSERT INTO reservation_info (order_id, user_id, symbol, side, quantity, price, reserved_amount, status) VALUES
    ('ORDER-008-PENDING', 'USER004', 'GOOGL', 'BUY', 2.0000, 2800.00, 5600.0000, 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- ========================================
-- 초기 거래 내역 (히스토리 데이터)
-- ========================================

-- 성공한 거래 내역
INSERT INTO transaction_logs (transaction_id, user_id, trade_id, type, symbol, quantity, price, amount, balance_before, balance_after, description) VALUES
    ('TXN-001', 'USER001', 'TRADE-001', 'BUY', 'AAPL', 10.0000, 150.0000, 1500.0000, 1001500.0000, 1000000.0000, 'Buy 10 AAPL at 150.00'),
    ('TXN-002', 'USER001', 'TRADE-002', 'SELL', 'GOOGL', 5.0000, 2800.0000, 14000.0000, 1000000.0000, 1014000.0000, 'Sell 5 GOOGL at 2800.00'),
    ('TXN-003', 'USER002', 'TRADE-003', 'BUY', 'AMZN', 10.0000, 180.0000, 1800.0000, 501800.0000, 500000.0000, 'Buy 10 AMZN at 180.00')
ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP(6);

-- ========================================
-- 실패 시나리오 테스트를 위한 특별 데이터
-- ========================================

-- 극단적인 테스트 케이스를 위한 추가 사용자
INSERT INTO accounts (user_id, cash_balance, available_cash) VALUES
    ('USER_EDGE_01', 0.0100, 0.0100),              -- 최소 자금
    ('USER_EDGE_02', 99999999.9999, 99999999.9999), -- 최대 자금
    ('USER_LOCKED', 100000.0000, 0.0000)           -- 전액 예약 상태
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- 극단적인 주식 보유 케이스
INSERT INTO stock_holdings (user_id, symbol, quantity, available_quantity, average_price) VALUES
    ('USER_EDGE_01', 'AAPL', 0.0001, 0.0001, 150.0000),    -- 최소 수량
    ('USER_EDGE_02', 'GOOGL', 99999.9999, 99999.9999, 2800.0000) -- 최대 수량
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);