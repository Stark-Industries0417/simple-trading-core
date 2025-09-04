CREATE DATABASE IF NOT EXISTS trading_core;
USE trading_core;

-- ========================================
-- CDC User Permissions
-- ========================================
-- Root user already has all privileges, just flush to ensure
FLUSH PRIVILEGES;

-- ========================================
-- Order Module Tables
-- ========================================

-- Orders Table
CREATE TABLE IF NOT EXISTS orders (
    order_id       VARCHAR(50) PRIMARY KEY,
    user_id        VARCHAR(50)    NOT NULL,
    symbol         VARCHAR(10)    NOT NULL,
    order_type     VARCHAR(10)    NOT NULL,  -- MARKET, LIMIT
    side           VARCHAR(10)    NOT NULL,  -- BUY, SELL
    quantity       BIGINT         NOT NULL,
    price          DECIMAL(19, 4) NULL,      -- NULL for market orders
    filled_quantity BIGINT        NOT NULL DEFAULT 0,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',  -- PENDING, FILLED, PARTIALLY_FILLED, CANCELLED
    created_at     TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT         NOT NULL DEFAULT 0,
    
    INDEX idx_user_id (user_id),
    INDEX idx_symbol (symbol),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ========================================
-- Account Module Tables
-- ========================================

-- Accounts Table
CREATE TABLE IF NOT EXISTS accounts (
    account_id      VARCHAR(50) PRIMARY KEY,
    user_id         VARCHAR(50)    NOT NULL UNIQUE,
    balance         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    frozen_balance  DECIMAL(19, 4) NOT NULL DEFAULT 0,  -- 주문 진행중인 금액
    total_assets    DECIMAL(19, 4) NOT NULL DEFAULT 0,  -- 총 자산 (현금 + 주식 평가액)
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         BIGINT         NOT NULL DEFAULT 0,
    
    INDEX idx_account_user_id (user_id),
    CHECK (balance >= 0),
    CHECK (frozen_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stock Holdings Table
CREATE TABLE IF NOT EXISTS stock_holdings (
    holding_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id      VARCHAR(50)  NOT NULL,
    symbol          VARCHAR(10)  NOT NULL,
    quantity        BIGINT       NOT NULL DEFAULT 0,
    frozen_quantity BIGINT       NOT NULL DEFAULT 0,  -- 매도 주문 진행중인 수량
    avg_price       DECIMAL(19, 4) NOT NULL DEFAULT 0,  -- 평균 매수 단가
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         BIGINT       NOT NULL DEFAULT 0,
    
    UNIQUE KEY uk_account_symbol (account_id, symbol),
    FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE,
    INDEX idx_symbol (symbol),
    CHECK (quantity >= 0),
    CHECK (frozen_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Transaction Logs Table (거래 내역)
CREATE TABLE IF NOT EXISTS transaction_logs (
    log_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id      VARCHAR(50)    NOT NULL,
    transaction_id  VARCHAR(50)    NOT NULL UNIQUE,
    type            VARCHAR(20)    NOT NULL,  -- DEPOSIT, WITHDRAWAL, BUY, SELL, FEE
    amount          DECIMAL(19, 4) NULL,
    symbol          VARCHAR(10)    NULL,
    quantity        BIGINT         NULL,
    price           DECIMAL(19, 4) NULL,
    description     VARCHAR(255)   NOT NULL,
    balance_after   DECIMAL(19, 4) NOT NULL,  -- 거래 후 잔액
    created_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    
    FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE,
    INDEX idx_account_id (account_id),
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Account Reconciliation Table (계좌 대사 기록)
CREATE TABLE IF NOT EXISTS account_reconciliations (
    reconciliation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id        VARCHAR(50)    NOT NULL,
    expected_balance  DECIMAL(19, 4) NOT NULL,
    actual_balance    DECIMAL(19, 4) NOT NULL,
    difference        DECIMAL(19, 4) NOT NULL,
    status            VARCHAR(20)    NOT NULL,  -- MATCHED, MISMATCHED, RESOLVED
    resolved_at       TIMESTAMP(6)   NULL,
    notes             TEXT           NULL,
    created_at        TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    
    FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE,
    INDEX idx_account_id (account_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ========================================
-- Market Data Tables (시세 및 종목 정보)
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

-- Market Data Table (실시간 시세 - Optional)
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

-- 테스트용 계정 생성
INSERT INTO accounts (account_id, user_id, balance) VALUES
    ('ACC001', 'USER001', 1000000.0000),
    ('ACC002', 'USER002', 500000.0000)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6);

-- Grant permissions
GRANT ALL PRIVILEGES ON trading_core.* TO 'trading_user'@'%';
FLUSH PRIVILEGES;