-- Create database if not exists (테스트용)
CREATE DATABASE IF NOT EXISTS trading_db_test;
USE trading_db_test;

-- Grant necessary privileges to CDC user
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'cdc_user'@'%';
FLUSH PRIVILEGES;

-- Create Order Outbox table if not exists
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

    INDEX idx_outbox_status (status),
    INDEX idx_outbox_created (created_at),
    INDEX idx_outbox_aggregate (aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert sample outbox events for testing
INSERT INTO order_outbox_events (event_id, aggregate_id, aggregate_type, event_type, payload, order_id, user_id)
VALUES 
(
    'test-event-001',
    'order-001',
    'Order',
    'OrderCreated',
    JSON_OBJECT(
        'eventId', 'test-event-001',
        'orderId', 'order-001',
        'userId', 'user-001',
        'symbol', 'AAPL',
        'side', 'BUY',
        'orderType', 'LIMIT',
        'quantity', 100,
        'price', 150.00,
        'occurredAt', NOW()
    ),
    'order-001',
    'user-001'
),
(
    'test-event-002',
    'order-002',
    'Order',
    'OrderCancelled',
    JSON_OBJECT(
        'eventId', 'test-event-002',
        'orderId', 'order-002',
        'userId', 'user-001',
        'reason', 'User requested cancellation',
        'occurredAt', NOW()
    ),
    'order-002',
    'user-001'
);