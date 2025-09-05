package com.trading.order.infrastructure.monitoring

import com.trading.common.monitoring.ModuleSagaHealthIndicator
import com.trading.common.monitoring.SagaMetrics
import com.trading.order.domain.saga.OrderSagaRepository
import com.trading.order.domain.saga.OrderSagaState
import org.springframework.stereotype.Component

@Component
class OrderSagaHealthIndicator(
    sagaRepository: OrderSagaRepository,
    sagaMetrics: SagaMetrics
) : ModuleSagaHealthIndicator<OrderSagaState>(
    sagaRepository = sagaRepository,
    sagaMetrics = sagaMetrics,
    moduleName = "order"
)