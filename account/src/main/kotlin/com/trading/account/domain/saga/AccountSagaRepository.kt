package com.trading.account.domain.saga

import com.trading.common.domain.saga.SagaStateRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountSagaRepository : SagaStateRepository<AccountSagaState>