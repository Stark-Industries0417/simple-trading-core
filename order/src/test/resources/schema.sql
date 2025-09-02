-- Order 테이블 생성
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    order_type VARCHAR(10) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity DECIMAL(19, 8) NOT NULL,
    price DECIMAL(19, 2),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    trace_id VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    filled_quantity DECIMAL(19, 8) DEFAULT 0,
    cancellation_reason VARCHAR(500)
);

-- 인덱스 생성
CREATE INDEX idx_user_created ON orders(user_id, created_at);
CREATE INDEX idx_symbol_status ON orders(symbol, status);
CREATE INDEX idx_trace_id ON orders(trace_id);
CREATE INDEX idx_created_at ON orders(created_at);