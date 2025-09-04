package com.trading.cdc.runner

import com.trading.cdc.health.CdcHealthIndicator
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.RecordChangeEvent
import org.apache.kafka.connect.source.SourceRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import jakarta.annotation.PreDestroy

@Component
class DebeziumEngineRunner(
    private val debeziumEngine: DebeziumEngine<RecordChangeEvent<SourceRecord>>,
    private val healthIndicator: CdcHealthIndicator
) : ApplicationRunner {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "debezium-engine-thread")
    }
    
    override fun run(args: ApplicationArguments?) {
        logger.info("Starting Debezium Engine Runner...")
        
        executor.execute {
            try {
                logger.info("Debezium Engine started successfully")
                healthIndicator.markRunning()
                debeziumEngine.run()
            } catch (e: Exception) {
                logger.error("Debezium Engine encountered an error", e)
                healthIndicator.markStopped()
            }
        }
        
        logger.info("Debezium Engine Runner initialization completed")
    }
    
    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down Debezium Engine...")
        healthIndicator.markStopped()
        
        try {
            debeziumEngine.close()
            logger.info("Debezium Engine closed")
        } catch (e: Exception) {
            logger.error("Error closing Debezium Engine", e)
        }
        
        executor.shutdown()
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow()
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Executor did not terminate")
                }
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        logger.info("Debezium Engine Runner shutdown completed")
    }
}