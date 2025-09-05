package com.trading.common.domain.saga

enum class SagaStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED,
    TIMEOUT
}