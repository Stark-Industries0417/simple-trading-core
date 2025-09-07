CREATE DATABASE IF NOT EXISTS trading_db_test;
USE trading_db_test;

GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'cdc_user'@'%';
FLUSH PRIVILEGES;

CREATE TABLE IF NOT EXISTS order_saga_states (
    saga_id         VARCHAR(255) PRIMARY KEY,
    trade_id        VARCHAR(255) NOT NULL,
    order_id        VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    symbol          VARCHAR(50) NOT NULL,
    order_type      VARCHAR(50) NOT NULL,
    state           VARCHAR(50) NOT NULL,
    event_type      VARCHAR(100) NOT NULL DEFAULT 'OrderCreatedEvent',
    event_payload   JSON NOT NULL DEFAULT '{}',
    topic           VARCHAR(100) NOT NULL DEFAULT 'order.events',
    started_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at    TIMESTAMP(6),
    timeout_at      TIMESTAMP(6) NOT NULL,
    last_modified_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    metadata        TEXT,
    version         BIGINT DEFAULT 0,
    
    INDEX idx_saga_order (order_id),
    INDEX idx_saga_user (user_id),
    INDEX idx_saga_state (state),
    INDEX idx_saga_event_type (event_type),
    INDEX idx_saga_last_modified (last_modified_at),
    INDEX idx_saga_state_modified (state, last_modified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert sample saga states for testing
INSERT INTO order_saga_states (
    saga_id, trade_id, order_id, user_id, symbol, order_type, state,
    event_type, event_payload, timeout_at
)
VALUES 
(
    'test-saga-001',
    'test-trade-001',
    'order-001',
    'user-001',
    'AAPL',
    'LIMIT',
    'STARTED',
    'OrderCreatedEvent',
    JSON_OBJECT(
        'eventId', 'test-event-001',
        'aggregateId', 'order-001',
        'traceId', 'trace-001',
        'occurredAt', NOW(),
        'order', JSON_OBJECT(
            'orderId', 'order-001',
            'userId', 'user-001',
            'symbol', 'AAPL',
            'side', 'BUY',
            'orderType', 'LIMIT',
            'quantity', 100,
            'price', 150.00,
            'status', 'PENDING'
        )
    ),
    TIMESTAMPADD(SECOND, 30, CURRENT_TIMESTAMP)
),
(
    'test-saga-002',
    'test-trade-002',
    'order-002',
    'user-001',
    'GOOGL',
    'MARKET',
    'COMPENSATING',
    'OrderCancelledEvent',
    JSON_OBJECT(
        'eventId', 'test-event-002',
        'aggregateId', 'order-002',
        'traceId', 'trace-002',
        'occurredAt', NOW(),
        'orderId', 'order-002',
        'userId', 'user-001',
        'reason', 'User requested cancellation'
    ),
    TIMESTAMPADD(SECOND, 30, CURRENT_TIMESTAMP)
);

CREATE TABLE IF NOT EXISTS order_outbox_events (
    event_id       VARCHAR(50) PRIMARY KEY,
    aggregate_id   VARCHAR(50)  NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at   TIMESTAMP(6) NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    order_id       VARCHAR(50)  NOT NULL,
    user_id        VARCHAR(50)  NOT NULL,
    saga_id        VARCHAR(255),
    trade_id       VARCHAR(255),
    topic          VARCHAR(100) DEFAULT 'order.events',

    INDEX idx_outbox_status (status),
    INDEX idx_outbox_created (created_at),
    INDEX idx_outbox_aggregate (aggregate_id),
    INDEX idx_outbox_saga (saga_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;