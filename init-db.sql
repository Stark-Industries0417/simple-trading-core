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
-- 테스트 계정 데이터 (최소한의 설정)
-- ========================================

-- USER001: BUY 테스트용 (충분한 자금, 주식 미보유)
-- USER002: SELL 테스트용 (일부 자금, 주식 보유)
INSERT INTO accounts (user_id, cash_balance, available_cash) VALUES
    ('USER001', 1000000.0000, 1000000.0000),   -- BUY 테스트용 자금
    ('USER002', 100000.0000, 100000.0000)      -- SELL 테스트용 자금
    ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- ========================================
-- 테스트용 주식 보유 데이터 (최소 설정)
-- ========================================

-- USER001: 주식 미보유 (BUY 전용)
-- USER002: SELL 테스트용 기본 보유 주식
INSERT INTO stock_holdings (user_id, symbol, quantity, available_quantity, average_price) VALUES
    ('USER002', 'AAPL', 100.0000, 100.0000, 145.0000),    -- AAPL 100주 보유
    ('USER002', 'GOOGL', 50.0000, 50.0000, 2800.0000),    -- GOOGL 50주 보유
    ('USER002', 'MSFT', 75.0000, 75.0000, 380.0000)       -- MSFT 75주 보유
    ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- 예약 정보와 거래 내역은 실제 테스트 중에 생성되므로 초기 데이터 없음
-- 극단적인 테스트 케이스도 제거하여 깨끗한 상태 유지