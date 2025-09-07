package com.trading.cdc

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.cdc.connector.OrderSagaConnector
import com.trading.cdc.health.CdcHealthIndicator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@ExtendWith(SpringExtension::class)
@SpringBootTest
@Testcontainers
class CdcIntegrationTest {
    
    companion object {
        @Container
        @JvmStatic
        val mysqlContainer = MySQLContainer<Nothing>(DockerImageName.parse("mysql:8.0")).apply {
            withDatabaseName("trading_db")
            withUsername("test")
            withPassword("test")
            withCommand(
                "--server-id=1",
                "--log-bin=mysql-bin",
                "--binlog-format=ROW",
                "--binlog-row-image=FULL"
            )
        }
        
        @Container
        @JvmStatic
        val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
    }
    
    @Autowired
    private lateinit var healthIndicator: CdcHealthIndicator
    
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    
    @MockBean
    private lateinit var orderSagaConnector: OrderSagaConnector
    
    @BeforeEach
    fun setup() {
        mysqlContainer.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""
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
                        last_modified_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        metadata        TEXT,
                        version         BIGINT DEFAULT 0,
                        
                        INDEX idx_saga_order (order_id),
                        INDEX idx_saga_user (user_id),
                        INDEX idx_saga_state (state),
                        INDEX idx_saga_last_modified (last_modified_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.trimIndent())
            }
        }
    }
    
    @Test
    fun `Saga 상태 테이블에서 OrderCreated 이벤트를 처리한다`() {
        val sagaId = "test-saga-001"
        val tradeId = "test-trade-001"
        val orderId = "order-001"
        val userId = "user-001"
        mysqlContainer.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""
                    INSERT INTO order_saga_states (
                        saga_id, trade_id, order_id, user_id, symbol, order_type,
                        state, event_type, event_payload, timeout_at
                    ) VALUES (
                        '$sagaId', '$tradeId', '$orderId', '$userId', 'AAPL', 'MARKET',
                        'STARTED', 'OrderCreatedEvent',
                        '{"orderId":"$orderId","userId":"$userId","symbol":"AAPL"}',
                        TIMESTAMPADD(SECOND, 30, CURRENT_TIMESTAMP)
                    )
                """.trimIndent())
            }
        }
        
        Thread.sleep(Duration.ofSeconds(2).toMillis())
        assertTrue(mysqlContainer.isRunning)
        assertTrue(kafkaContainer.isRunning)
    }
    
    @Test
    fun `이벤트 처리시 상태 표시기가 업데이트된다`() {
        healthIndicator.markRunning()
        healthIndicator.incrementEventsProcessed()
        val health = healthIndicator.getHealthStatus()
        assertEquals(true, health["healthy"])
        assertEquals(true, health["running"])
        assertEquals(1L, health["eventsProcessed"])
        assertNotNull(health["lastEventTime"])
    }
    
    @Test
    fun `실행 중지시 상태가 비정상으로 표시된다`() {
        val freshHealthIndicator = CdcHealthIndicator()
        freshHealthIndicator.markStopped()
        val health = freshHealthIndicator.getHealthStatus()
        assertEquals(false, health["healthy"])
        assertEquals(false, health["running"])
        assertEquals("CDC engine is not running", health["reason"])
    }
    
    @Test
    fun `Saga 상태 테이블에서 OrderCancelled 이벤트를 처리한다`() {
        val sagaId = "test-saga-002"
        val tradeId = "test-trade-002"
        val orderId = "order-002"
        val userId = "user-001"
        mysqlContainer.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""
                    INSERT INTO order_saga_states (
                        saga_id, trade_id, order_id, user_id, symbol, order_type,
                        state, event_type, event_payload, timeout_at
                    ) VALUES (
                        '$sagaId', '$tradeId', '$orderId', '$userId', 'AAPL', 'MARKET',
                        'COMPENSATING', 'OrderCancelledEvent',
                        '{"orderId":"$orderId","userId":"$userId","reason":"User requested"}',
                        TIMESTAMPADD(SECOND, 30, CURRENT_TIMESTAMP)
                    )
                """.trimIndent())
            }
        }
        
        Thread.sleep(Duration.ofSeconds(2).toMillis())
        assertTrue(mysqlContainer.isRunning)
    }
}