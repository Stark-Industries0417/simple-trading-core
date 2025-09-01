package com.trading.order.domain

import com.trading.common.dto.order.OrderDTO

/**
 * Order 엔티티를 OrderDTO로 변환하는 확장 함수
 */
fun Order.toDTO(): OrderDTO = OrderDTO(
    orderId = this.id,
    userId = this.userId,
    symbol = this.symbol,
    orderType = this.orderType,
    side = this.side,
    quantity = this.quantity,
    price = this.price,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    status = this.status,
    traceId = this.traceId,
    version = this.version
)