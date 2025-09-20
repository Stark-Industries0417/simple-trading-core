package com.trading.common.outbox

/**
 * Outbox 이벤트 타입 정의
 * CDC가 Kafka로 발행할 때 사용되는 이벤트 타입 상수
 *
 * 명명 규칙:
 * - 도메인_동작_상태 형식
 * - PascalCase 사용
 * - 명확하고 일관된 네이밍
 */
object EventTypes {

    /**
     * Order 도메인 이벤트
     */
    object Order {
        const val CREATED = "OrderCreated"
        const val CANCELLED = "OrderCancelled"
        const val MATCHED = "OrderMatched"
        const val PARTIALLY_FILLED = "OrderPartiallyFilled"
        const val COMPLETED = "OrderCompleted"
        const val EXPIRED = "OrderExpired"
        const val REJECTED = "OrderRejected"
    }

    /**
     * Trade/Matching 도메인 이벤트
     */
    object Trade {
        const val CREATED = "TradeCreated"
        const val EXECUTED = "TradeExecuted"
        const val FAILED = "TradeFailed"
        const val ROLLBACK = "TradeRollback"
        const val SETTLED = "TradeSettled"
        const val NO_MATCH = "TradeNoMatch"  // 매칭 없음 (정상 상황)
    }

    /**
     * Account 도메인 이벤트
     */
    object Account {
        const val UPDATED = "AccountUpdated"
        const val UPDATE_FAILED = "AccountUpdateFailed"
        const val ROLLBACK = "AccountRollback"
        const val BALANCE_RESERVED = "AccountBalanceReserved"
        const val BALANCE_RELEASED = "AccountBalanceReleased"
        const val POSITION_UPDATED = "AccountPositionUpdated"
    }

    /**
     * Saga 오케스트레이션 이벤트
     */
    object Saga {
        const val STARTED = "SagaStarted"
        const val COMPLETED = "SagaCompleted"
        const val FAILED = "SagaFailed"
        const val COMPENSATING = "SagaCompensating"
        const val COMPENSATED = "SagaCompensated"
        const val TIMEOUT = "SagaTimeout"
    }

    /**
     * Market Data 이벤트 (시세)
     */
    object Market {
        const val PRICE_UPDATED = "MarketPriceUpdated"
        const val ORDERBOOK_UPDATED = "MarketOrderbookUpdated"
        const val TRADE_OCCURRED = "MarketTradeOccurred"
        const val HALTED = "MarketHalted"
        const val RESUMED = "MarketResumed"
    }
}

/**
 * 이벤트 타입 유틸리티
 */
object EventTypeUtils {

    /**
     * 이벤트 타입에서 도메인 추출
     * 예: "OrderCreated" -> "Order"
     */
    fun extractDomain(eventType: String): String {
        return when {
            eventType.startsWith("Order") -> "Order"
            eventType.startsWith("Trade") -> "Trade"
            eventType.startsWith("Account") -> "Account"
            eventType.startsWith("Saga") -> "Saga"
            eventType.startsWith("Market") -> "Market"
            else -> "Unknown"
        }
    }

    /**
     * 이벤트 타입에서 Kafka 토픽 결정
     */
    fun determineTopicName(eventType: String): String {
        return when (extractDomain(eventType)) {
            "Order" -> "order.events"
            "Trade" -> "trade.events"
            "Account" -> "account.events"
            "Saga" -> "saga.events"
            "Market" -> "market.data"
            else -> "dead.letter"
        }
    }

    /**
     * 보상 이벤트 여부 확인
     */
    fun isCompensationEvent(eventType: String): Boolean {
        return eventType.contains("Rollback") ||
               eventType.contains("Compensat") ||
               eventType.contains("Failed") ||
               eventType.contains("Cancelled")
    }
}