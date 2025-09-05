package com.trading.matching.domain.saga

import com.trading.common.domain.saga.SagaStateRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchingSagaRepository : SagaStateRepository<MatchingSagaState>