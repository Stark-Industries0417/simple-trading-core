package com.trading.order.domain.saga

import com.trading.common.domain.saga.SagaStateRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderSagaRepository : SagaStateRepository<OrderSagaState>