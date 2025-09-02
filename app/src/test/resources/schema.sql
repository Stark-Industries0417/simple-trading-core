-- H2 compatible schema for testing

DROP TABLE IF EXISTS transaction_logs;
DROP TABLE IF EXISTS trades;
DROP TABLE IF EXISTS stock_holdings;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS accounts;

-- 사용자 계정 테이블
CREATE TABLE accounts
(
    user_id        VARCHAR(50) PRIMARY KEY,
    cash_balance   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    available_cash DECIMAL(19, 4) NOT NULL DEFAULT 0,
    version        BIGINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 거래 로그 테이블 (TransactionLog 엔티티용)
CREATE TABLE transaction_logs
(
    transaction_id   VARCHAR(50) PRIMARY KEY,
    user_id         VARCHAR(50) NOT NULL,
    trade_id        VARCHAR(100) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    symbol          VARCHAR(10) NOT NULL,
    quantity        DECIMAL(19, 4) NOT NULL,
    price           DECIMAL(19, 4) NOT NULL,
    amount          DECIMAL(19, 4) NOT NULL,
    balance_before  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    balance_after   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    description     VARCHAR(500),
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transaction_logs_user_id (user_id),
    INDEX idx_transaction_logs_trade_id (trade_id),
    INDEX idx_transaction_logs_created_at (created_at)
);

-- 주식 보유 테이블
CREATE TABLE IF NOT EXISTS stock_holdings
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            VARCHAR(50)    NOT NULL,
    symbol             VARCHAR(20)    NOT NULL,
    quantity           DECIMAL(19, 8) NOT NULL DEFAULT 0,
    available_quantity DECIMAL(19, 8) NOT NULL DEFAULT 0,
    average_price      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_holdings_user FOREIGN KEY (user_id) REFERENCES accounts (user_id),
    UNIQUE KEY unique_user_symbol (user_id, symbol)
);

-- 주문 테이블
CREATE TABLE IF NOT EXISTS orders
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
    filled_quantity     DECIMAL(19, 8) DEFAULT 0,
    cancellation_reason VARCHAR(500)
);

-- 거래 테이블
CREATE TABLE IF NOT EXISTS trades
(
    trade_id      VARCHAR(100) PRIMARY KEY,
    buy_order_id  VARCHAR(36)    NOT NULL,
    sell_order_id VARCHAR(36)    NOT NULL,
    symbol        VARCHAR(20)    NOT NULL,
    quantity      DECIMAL(19, 8) NOT NULL,
    price         DECIMAL(19, 4) NOT NULL,
    buy_user_id   VARCHAR(50)    NOT NULL,
    sell_user_id  VARCHAR(50)    NOT NULL,
    executed_at   TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trades_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders (id),
    CONSTRAINT fk_trades_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders (id)
);