
SET foreign_key_checks = 0;

DROP TABLE IF EXISTS trades;
DROP TABLE IF EXISTS stock_holdings;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS accounts;

SET foreign_key_checks = 1;

-- 사용자 계정 테이블
CREATE TABLE accounts
(
    user_id        VARCHAR(50) PRIMARY KEY,
    cash_balance   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    available_cash DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6)            DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6)            DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 주식 보유 테이블
CREATE TABLE stock_holdings
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            VARCHAR(50)    NOT NULL,
    symbol             VARCHAR(20)    NOT NULL,
    quantity           DECIMAL(19, 8) NOT NULL DEFAULT 0,
    available_quantity DECIMAL(19, 8) NOT NULL DEFAULT 0,
    average_price      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at         TIMESTAMP(6)            DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         TIMESTAMP(6)            DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_holdings_user FOREIGN KEY (user_id) REFERENCES accounts (user_id),
    UNIQUE KEY unique_user_symbol (user_id, symbol),
    INDEX              idx_holdings_user_id (user_id),
    INDEX              idx_holdings_symbol (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 주문 테이블
CREATE TABLE orders
(
    id                  VARCHAR(50)    NOT NULL PRIMARY KEY,
    user_id             VARCHAR(50)    NOT NULL,
    symbol              VARCHAR(20)    NOT NULL,
    order_type          VARCHAR(10)    NOT NULL,
    side                VARCHAR(4)     NOT NULL,
    quantity            DECIMAL(19, 8) NOT NULL,
    price               DECIMAL(19, 2),
    status              VARCHAR(20)    NOT NULL,
    created_at          TIMESTAMP(6)   NOT NULL,
    updated_at          TIMESTAMP(6)   NOT NULL,
    trace_id            VARCHAR(36)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    filled_quantity     DECIMAL(19, 8)          DEFAULT 0,
    cancellation_reason VARCHAR(500),
    INDEX               idx_user_created (user_id, created_at),
    INDEX               idx_symbol_status (symbol, status),
    INDEX               idx_trace_id (trace_id),
    INDEX               idx_created_at (created_at),
    INDEX               idx_orders_user_id (user_id),
    INDEX               idx_orders_symbol (symbol),
    INDEX               idx_orders_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 거래 테이블
CREATE TABLE trades
(
    trade_id      VARCHAR(100) PRIMARY KEY,
    buy_order_id  VARCHAR(36)    NOT NULL,
    sell_order_id VARCHAR(36)    NOT NULL,
    symbol        VARCHAR(20)    NOT NULL,
    quantity      DECIMAL(19, 8) NOT NULL,
    price         DECIMAL(19, 4) NOT NULL,
    buy_user_id   VARCHAR(50)    NOT NULL,
    sell_user_id  VARCHAR(50)    NOT NULL,
    executed_at   TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_trades_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders (id),
    CONSTRAINT fk_trades_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders (id),
    INDEX         idx_trades_symbol (symbol),
    INDEX         idx_trades_executed_at (executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 테스트 데이터 삽입
INSERT INTO accounts (user_id, cash_balance, available_cash)
VALUES ('user-001', 100000.0000, 100000.0000),
       ('user-002', 50000.0000, 50000.0000),
       ('user-003', 75000.0000, 75000.0000) ON DUPLICATE KEY
UPDATE cash_balance=
VALUES (cash_balance);