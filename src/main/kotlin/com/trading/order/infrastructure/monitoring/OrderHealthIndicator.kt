package com.trading.order.infrastructure.monitoring

import com.trading.order.application.OrderMetrics
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource




@Component("orderHealth")
class OrderHealthIndicator(
    private val dataSource: DataSource,
    private val orderMetrics: OrderMetrics
) : HealthIndicator {

    companion object {
        private const val MAX_VALIDATION_FAILURE_RATE = 0.1
        private const val MAX_ERROR_RATE = 0.05
        private const val MAX_AVG_RESPONSE_TIME_MS = 100
        private const val HEALTH_CHECK_TIMEOUT_SECONDS = 5L
        private const val MIN_RECENT_ACTIVITY_THRESHOLD = 1L
    }

    override fun health(): Health {
        val healthData = mutableMapOf<String, Any>()
        var isHealthy = true
        val issues = mutableListOf<String>()
        val startTime = Instant.now()

        try {
            val dbHealth = checkDatabaseConnectivity()
            healthData["database"] = dbHealth.first
            if (!dbHealth.second) {
                isHealthy = false
                issues.add("Database connectivity issue")
            }

            val metricsHealth = checkMetricsHealth()
            healthData.putAll(metricsHealth.first)
            if (!metricsHealth.second) {
                isHealthy = false
                issues.addAll(metricsHealth.third)
            }

            val activityHealth = checkRecentActivity()
            healthData["recentActivity"] = activityHealth.first
            if (!activityHealth.second) {
                healthData["activityWarning"] = "Low recent activity detected"
            }

            val resourceHealth = checkResourceHealth()
            healthData["resources"] = resourceHealth.first
            if (!resourceHealth.second) {
                isHealthy = false
                issues.add("System resource issue")
            }

            val duration = Duration.between(startTime, Instant.now())
            healthData["healthCheckDurationMs"] = duration.toMillis()

            if (duration.toMillis() > HEALTH_CHECK_TIMEOUT_SECONDS * 1000) {
                isHealthy = false
                issues.add("Health check timeout")
            }

            return if (isHealthy) {
                Health.up()
                    .withDetails(healthData)
                    .build()
            } else {
                Health.down()
                    .withDetail("issues", issues)
                    .withDetails(healthData)
                    .build()
            }

        } catch (ex: Exception) {
            return Health.down()
                .withDetail("error", ex.message)
                .withDetail("errorType", ex.javaClass.simpleName)
                .withDetail("healthCheckDurationMs", Duration.between(startTime, Instant.now()).toMillis())
                .build()
        }
    }


    private fun checkDatabaseConnectivity(): Pair<Map<String, Any>, Boolean> {
        return try {
            var responseTimeMs = 0L

            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT 1").use { stmt ->
                            responseTimeMs = measureTimeMillis {
                        stmt.executeQuery().use { rs ->
                            if (!rs.next()) {
                                throw IllegalStateException("Database test query failed")
                            }
                        }
                    }
                }
            }

            val dbData = mapOf<String, Any>(
                "status" to "UP",
                "connectionTest" to "PASSED",
                "responseTimeMs" to responseTimeMs  // 실제 측정된 시간
            )

            val isHealthy = responseTimeMs < 1000

            Pair(dbData, isHealthy)

        } catch (ex: Exception) {
            val dbData = mapOf<String, Any>(
                "status" to "DOWN",
                "error" to (ex.message ?: "Unknown error"),
                "connectionTest" to "FAILED"
            )
            Pair(dbData, false)
        }
    }

    private fun checkRecentActivity(): Pair<Map<String, Any>, Boolean> {
        return try {
            val metricsSummary = (orderMetrics as? com.trading.order.application.OrderMetricsImpl)
                ?.getMetricsSummary() ?: emptyMap()

            val recentOrdersCreated = metricsSummary["ordersCreatedLastMinute"] as? Long ?: 0L
            val recentOrdersCancelled = metricsSummary["ordersCancelledLastMinute"] as? Long ?: 0L
            val recentEvents = metricsSummary["eventsPublishedLastMinute"] as? Long ?: 0L

            val activityData = mapOf<String, Any>(
                "ordersCreatedLastMinute" to recentOrdersCreated,
                "ordersCancelledLastMinute" to recentOrdersCancelled,
                "eventsPublishedLastMinute" to recentEvents,
                "totalRecentActivity" to (recentOrdersCreated + recentOrdersCancelled + recentEvents)
            )

            val hasActivity = recentOrdersCreated >= MIN_RECENT_ACTIVITY_THRESHOLD ||
                    recentEvents >= MIN_RECENT_ACTIVITY_THRESHOLD

            Pair(activityData, hasActivity)

        } catch (ex: Exception) {
            val activityData = mapOf<String, Any>(
                "error" to (ex.message ?: "Unable to retrieve activity metrics")
            )
            Pair(activityData, true)
        }
    }

    private fun checkMetricsHealth(): Triple<Map<String, Any>, Boolean, List<String>> {
        val issues = mutableListOf<String>()
        var isHealthy = true
        val metricsData = mutableMapOf<String, Any>()

        try {
            val metricsSummary = (orderMetrics as? com.trading.order.application.OrderMetricsImpl)
                ?.getMetricsSummary() ?: emptyMap()

            metricsData.putAll(metricsSummary.filterKeys {
                it !in setOf("ordersCreatedLastMinute", "ordersCancelledLastMinute", "eventsPublishedLastMinute")
            })

            val recentValidationFailures = metricsSummary["validationFailuresLastMinute"] as? Number ?: 0
            val recentOrdersCreated = metricsSummary["ordersCreatedLastMinute"] as? Number ?: 1
            val validationFailureRate = if (recentOrdersCreated.toDouble() > 0) {
                recentValidationFailures.toDouble() / recentOrdersCreated.toDouble()
            } else 0.0

            metricsData["validationFailureRate"] = validationFailureRate

            if (validationFailureRate > MAX_VALIDATION_FAILURE_RATE) {
                isHealthy = false
                issues.add("High validation failure rate: ${String.format("%.2f%%", validationFailureRate * 100)}")
            }

            val recentErrors = (metricsSummary["errorsLastMinute"] as? Number ?: 0).toDouble()
            val errorRate = if (recentOrdersCreated.toDouble() > 0) {
                recentErrors / recentOrdersCreated.toDouble()
            } else 0.0

            metricsData["errorRate"] = errorRate

            if (errorRate > MAX_ERROR_RATE) {
                isHealthy = false
                issues.add("High error rate: ${String.format("%.2f%%", errorRate * 100)}")
            }

            val avgResponseTime = metricsSummary["averageCreationTimeMs"] as? Number ?: 0
            if (avgResponseTime.toDouble() > MAX_AVG_RESPONSE_TIME_MS) {
                isHealthy = false
                issues.add("High average response time: ${avgResponseTime}ms")
            }

            return Triple(metricsData, isHealthy, issues)

        } catch (ex: Exception) {
            metricsData["metricsError"] = ex.message ?: "Unknown error"
            issues.add("Metrics collection failed")
            return Triple(metricsData, false, issues)
        }
    }

    private fun checkResourceHealth(): Pair<Map<String, Any>, Boolean> {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val memoryUsageRatio = usedMemory.toDouble() / maxMemory.toDouble()

            val resourceData = mapOf<String, Any>(
                "memoryUsedMB" to (usedMemory / (1024 * 1024)),
                "memoryMaxMB" to (maxMemory / (1024 * 1024)),
                "memoryUsageRatio" to String.format("%.2f", memoryUsageRatio),
                "availableProcessors" to runtime.availableProcessors()
            )

            val isHealthy = memoryUsageRatio < 0.9

            Pair(resourceData, isHealthy)

        } catch (ex: Exception) {
            val resourceData = mapOf<String, Any>(
                "error" to (ex.message ?: "Unknown error"),
                "status" to "UNKNOWN"
            )
            Pair(resourceData, false)
        }
    }

    private inline fun measureTimeMillis(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }
}