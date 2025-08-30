package com.trading.matching.performance

import com.trading.common.dto.*
import com.trading.common.event.EventPublisher
import org.slf4j.LoggerFactory
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.domain.OrderBook
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import com.trading.matching.infrastructure.resilience.BackpressureMonitor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import io.mockk.mockk
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import kotlin.system.measureTimeMillis



@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class EvolutionTests {
    
    companion object {
        private val logger = LoggerFactory.getLogger(EvolutionTests::class.java)
        private const val TEST_ORDER_COUNT = 10_000
        private const val MULTI_SYMBOL_COUNT = 5
    }
    
    private val eventPublisher: EventPublisher = mockk()
    private val backpressureMonitor = BackpressureMonitor()
    private val uuidGenerator = UUIDv7Generator()
    
    @Test
    @Order(1)
    @DisplayName("V1 vs V2 vs V3 - Real Business Logic Performance Comparison")
    fun testEvolutionPerformance() {

        val multiSymbolOrders = generateMultiSymbolOrders(
            symbols = listOf("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"),
            ordersPerSymbol = TEST_ORDER_COUNT / MULTI_SYMBOL_COUNT
        )
        val v1Result = measureConcurrentPerformance("V1-Synchronized-Real", 10) {

            val v1Engines = mapOf(
                "AAPL" to SynchronizedOrderBookEngine("AAPL"),
                "GOOGL" to SynchronizedOrderBookEngine("GOOGL"),
                "MSFT" to SynchronizedOrderBookEngine("MSFT"),
                "AMZN" to SynchronizedOrderBookEngine("AMZN"),
                "TSLA" to SynchronizedOrderBookEngine("TSLA")
            )
            val executor = Executors.newFixedThreadPool(10)
            val latch = CountDownLatch(multiSymbolOrders.size)
            val latencies = ConcurrentLinkedQueue<Long>()

            val totalProcessingTime = measureTimeMillis {
                multiSymbolOrders.forEach { order ->
                    executor.submit {
                        val latencyMs = measureTimeMillis {
                            v1Engines[order.symbol]?.processOrder(order)
                        }
                        latencies.offer(latencyMs)
                        latch.countDown()
                    }
                }
            }

            latch.await(60, TimeUnit.SECONDS)
            executor.shutdown()

            LatencyMetrics(
                latencies = latencies.toList(),
                totalDuration = totalProcessingTime,
                totalOperations = multiSymbolOrders.size
            )
        }
        

        val v2Result = measureSequentialPerformance("V2-SingleThread-Real") {
            val v2Engines = mapOf(
                "AAPL" to SingleThreadOrderBookEngine("AAPL"),
                "GOOGL" to SingleThreadOrderBookEngine("GOOGL"),
                "MSFT" to SingleThreadOrderBookEngine("MSFT"),
                "AMZN" to SingleThreadOrderBookEngine("AMZN"),
                "TSLA" to SingleThreadOrderBookEngine("TSLA")
            )
            val latencies = mutableListOf<Long>()

            val totalProcessingTime = measureTimeMillis {
                multiSymbolOrders.forEach { order ->
                    val latencyMs = measureTimeMillis {
                        v2Engines[order.symbol]?.processOrder(order)
                    }
                    latencies.add(latencyMs)
                }
            }

            LatencyMetrics(
                latencies = latencies,
                totalDuration = totalProcessingTime,
                totalOperations = multiSymbolOrders.size
            )
        }
        

        val v3Result = measurePartitionedPerformance("V3-Production-Real") {
            val v3Manager = MatchingEngineManager(
                threadPoolSize = 16,
                eventPublisher = eventPublisher,
                backpressureMonitor = backpressureMonitor,
                uuidGenerator = uuidGenerator
            )
            v3Manager.initialize()

            val multiSymbolOrders = generateMultiSymbolOrders(
                symbols = listOf("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"),
                ordersPerSymbol = TEST_ORDER_COUNT / MULTI_SYMBOL_COUNT
            )
            val latencies = ConcurrentLinkedQueue<Long>()
            val totalProcessingTime = measureTimeMillis {
                multiSymbolOrders.forEach { order ->
                    val submitLatencyMs = measureTimeMillis {
                        v3Manager.submitOrder(order)
                    }
                    latencies.offer(submitLatencyMs)
                }
                

                v3Manager.waitForAllCompletion(30, TimeUnit.SECONDS)
            }

            v3Manager.shutdown()
            

            LatencyMetrics(
                latencies = latencies.toList(),
                totalDuration = totalProcessingTime,
                totalOperations = multiSymbolOrders.size
            )
        }

        logger.info("=== Real Business Logic Performance Results ===")
        logger.info("V1 (Synchronized OrderBook): P50=${v1Result.p50}ms, P99=${v1Result.p99}ms, P99.9=${v1Result.p999}ms, Throughput=${v1Result.throughput} ops/sec")
        logger.info("V2 (Single Thread OrderBook): P50=${v2Result.p50}ms, P99=${v2Result.p99}ms, P99.9=${v2Result.p999}ms, Throughput=${v2Result.throughput} ops/sec")
        logger.info("V3 (Production Engine): P50=${v3Result.p50}ms, P99=${v3Result.p99}ms, P99.9=${v3Result.p999}ms, Throughput=${v3Result.throughput} ops/sec")
        

        logger.info("DEBUG - V1: throughput=${v1Result.throughput} ops/sec, avg=${v1Result.avg}ms")
        logger.info("DEBUG - V2: throughput=${v2Result.throughput} ops/sec, avg=${v2Result.avg}ms")
        logger.info("DEBUG - V3: throughput=${v3Result.throughput} ops/sec, avg=${v3Result.avg}ms")
        

        println("\n=== PERFORMANCE TEST RESULTS ===")
        println("V1 (Synchronized): Throughput=${v1Result.throughput} ops/sec, P99=${v1Result.p99}ms")
        println("V2 (Single Thread): Throughput=${v2Result.throughput} ops/sec, P99=${v2Result.p99}ms")
        println("V3 (Production Engine): Throughput=${v3Result.throughput} ops/sec, P99=${v3Result.p99}ms")
        println("V3 P99.9 Latency: ${v3Result.p999}ms (target < 100ms)")
        println("================================\n")
        
        logger.info("=== Performance Comparison ===")
        logger.info("V1 vs V2 P99 Latency: V1=${v1Result.p99}ms, V2=${v2Result.p99}ms")
        logger.info("Throughput Comparison: V1=${v1Result.throughput}, V2=${v2Result.throughput}, V3=${v3Result.throughput}")
        logger.info("V3 P99.9 Latency: ${v3Result.p999}ms (target < 100ms)")
        


        assertThat(v2Result.p99)
            .describedAs("V2 Single Thread should have lower P99 latency than synchronized V1")
            .isLessThan(v1Result.p99)
        

        assertThat(v3Result.throughput)
            .describedAs("V3 should achieve minimum production throughput of 10000 ops/sec")
            .isGreaterThan(10000.0)
        

        assertThat(v3Result.p999)
            .describedAs("V3 P99.9 latency should be under 100ms for production readiness")
            .isLessThan(100.0)
        

        logger.info("""
            Performance Trade-offs Analysis:
            - V2 (${String.format("%.0f", v2Result.throughput)} ops/sec): Fastest but single-threaded, not scalable
            - V1 (${String.format("%.0f", v1Result.throughput)} ops/sec): Multi-threaded but has lock contention
            - V3 (${String.format("%.0f", v3Result.throughput)} ops/sec): Event-driven production system, lower throughput but better scalability and fault tolerance
        """.trimIndent())
        

        generateDetailedEvolutionReport(listOf(
            DetailedPerformanceResult("V1-Synchronized", v1Result),
            DetailedPerformanceResult("V2-SingleThread", v2Result),
            DetailedPerformanceResult("V3-Production", v3Result)
        ))
    }
    
    @Test
    @Order(2)
    @DisplayName("Production-like Environment Simulation Test - 100+ concurrent users, multiple symbols")
    fun testProductionEnvironmentSimulation() {
        logger.info("=== Starting Production Environment Simulation ===")
        val productionSymbols = listOf("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", 
                                      "META", "NVDA", "NFLX", "SPY", "QQQ")
        val concurrentUsers = 100
        val ordersPerUser = 50
        val totalOrders = concurrentUsers * ordersPerUser
        

        val manager = MatchingEngineManager(
            threadPoolSize = 16,
            eventPublisher = eventPublisher,
            backpressureMonitor = backpressureMonitor,
            uuidGenerator = uuidGenerator
        )
        manager.initialize()
        

        val userExecutor = Executors.newFixedThreadPool(concurrentUsers)
        val allOrderLatencies = ConcurrentLinkedQueue<Long>()
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(concurrentUsers)
        

        repeat(concurrentUsers) { userId ->
            userExecutor.submit {
                try {
                    startLatch.await()
                    

                    repeat(ordersPerUser) { orderIdx ->
                        val symbol = productionSymbols.random()
                        val orderType = if (ThreadLocalRandom.current().nextBoolean()) 
                            OrderType.MARKET else OrderType.LIMIT
                        
                        val order = createProductionOrder(
                            userId = userId.toString(),
                            symbol = symbol,
                            orderType = orderType,
                            orderIndex = orderIdx
                        )
                        
                        val latencyMs = measureTimeMillis {
                            manager.submitOrder(order)
                        }
                        allOrderLatencies.offer(latencyMs)
                        

                        Thread.sleep(ThreadLocalRandom.current().nextLong(0, 10))
                    }
                } finally {
                    completionLatch.countDown()
                }
            }
        }
        
        val testStartTime = System.currentTimeMillis()
        startLatch.countDown()
        
        val completed = completionLatch.await(120, TimeUnit.SECONDS)
        assertThat(completed).describedAs("All users should complete within 2 minutes").isTrue()
        
        manager.waitForAllCompletion(30, TimeUnit.SECONDS)
        val totalDuration = System.currentTimeMillis() - testStartTime
        manager.shutdown()
        userExecutor.shutdown()
        

        val metrics = LatencyMetrics(
            latencies = allOrderLatencies.toList(),
            totalDuration = totalDuration,
            totalOperations = totalOrders
        )
        val throughput = metrics.throughput
        

        logger.info("=" * 80)
        logger.info("PRODUCTION ENVIRONMENT SIMULATION RESULTS")
        logger.info("=" * 80)
        logger.info("Configuration:")
        logger.info("  - Concurrent Users: $concurrentUsers")
        logger.info("  - Orders per User: $ordersPerUser")
        logger.info("  - Total Orders: $totalOrders")
        logger.info("  - Symbols: ${productionSymbols.size}")
        logger.info("  - Thread Pool Size: 16")
        logger.info("  - Test Duration: ${totalDuration}ms")
        logger.info("")
        logger.info("Performance Metrics:")
        logger.info("  - Throughput: ${String.format("%.0f", throughput)} orders/sec")
        logger.info("  - P50 Latency: ${String.format("%.2f", metrics.p50)}ms")
        logger.info("  - P90 Latency: ${String.format("%.2f", metrics.p90)}ms")
        logger.info("  - P99 Latency: ${String.format("%.2f", metrics.p99)}ms")
        logger.info("  - P99.9 Latency: ${String.format("%.2f", metrics.p999)}ms")
        logger.info("  - Min Latency: ${metrics.min}ms")
        logger.info("  - Max Latency: ${metrics.max}ms")
        logger.info("  - Avg Latency: ${String.format("%.2f", metrics.avg)}ms")
        logger.info("=" * 80)
        

        assertThat(metrics.p99)
            .describedAs("P99 latency should be under 50ms for production")
            .isLessThan(50.0)
        
        assertThat(metrics.p999)
            .describedAs("P99.9 latency should be under 100ms for production")
            .isLessThan(100.0)
        
        assertThat(throughput)
            .describedAs("System should handle at least 1000 orders/sec")
            .isGreaterThan(1000.0)
        
        logger.info("✅ Production Environment Test Passed!")
    }
    
    /**
     * 운영 환경용 주문 생성 헬퍼
     */
    private fun createProductionOrder(
        userId: String,
        symbol: String,
        orderType: OrderType,
        orderIndex: Int
    ): OrderDTO {
        val orderId = "${userId}_${orderIndex}_${System.currentTimeMillis()}"
        val side = if (ThreadLocalRandom.current().nextBoolean()) OrderSide.BUY else OrderSide.SELL
        val quantity = BigDecimal(ThreadLocalRandom.current().nextInt(10, 1000))
        val price = when (orderType) {
            OrderType.MARKET -> null
            OrderType.LIMIT -> BigDecimal(ThreadLocalRandom.current().nextInt(100, 200))
        }
        
        return OrderDTO(
            orderId = orderId,
            userId = userId,
            symbol = symbol,
            orderType = orderType,
            side = side,
            quantity = quantity,
            price = price,
            status = OrderStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            traceId = UUID.randomUUID().toString()
        )
    }
    
    @Test
    @Order(3)
    @DisplayName("Real Trading Scenarios Performance Test")
    fun testRealScenarios() {
        val manager = MatchingEngineManager(
            threadPoolSize = 16,
            eventPublisher = eventPublisher,
            backpressureMonitor = backpressureMonitor,
            uuidGenerator = uuidGenerator
        )
        manager.initialize()
        
        val scenarios = listOf(
            TestScenario("Simple Matching", generateSimpleMatchableOrders(1000)),
            TestScenario("Partial Fill", generatePartialFillOrders(1000)),
            TestScenario("Mixed Scenario", generateMixedScenario(1000))
        )
        
        scenarios.forEach { scenario ->
            val result = measureScenarioPerformance(manager, scenario)
            
            when (scenario.name) {
                "Simple Matching" -> {
                    assertThat(result.successRate).isGreaterThan(0.95)
                    assertThat(result.avgLatency).isLessThan(10.0)
                }
                "Partial Fill" -> {
                    assertThat(result.successRate).isGreaterThan(0.90)
                    assertThat(result.avgLatency).isLessThan(20.0)
                }
                "Mixed Scenario" -> {
                    assertThat(result.successRate).isGreaterThan(0.85)
                    assertThat(result.avgLatency).isLessThan(25.0)
                }
            }
            
            logger.info(
                "Scenario completed",
                mapOf(
                    "scenario" to scenario.name,
                    "orders" to scenario.orders.size,
                    "successRate" to String.format("%.2f%%", result.successRate * 100),
                    "avgLatencyMs" to result.avgLatency
                )
            )
        }
        
        manager.shutdown()
    }
    
    @Test
    @Order(3)
    @DisplayName("Concurrency and Backpressure Test - Graceful Degradation Under Load")
    fun testConcurrencyAndBackpressure() {
        val manager = MatchingEngineManager(
            threadPoolSize = 16,
            eventPublisher = eventPublisher,
            backpressureMonitor = backpressureMonitor,
            uuidGenerator = uuidGenerator
        )
        manager.initialize()
        

        val concurrentThreads = 100
        val ordersPerThread = 1000
        val expectedTotalOrders = concurrentThreads * ordersPerThread
        
        val latch = CountDownLatch(concurrentThreads)
        val executors = Executors.newFixedThreadPool(concurrentThreads)
        val successCount = AtomicLong(0)
        val failCount = AtomicLong(0)
        
        logger.info("Starting backpressure test with $concurrentThreads threads, $ordersPerThread orders each")
        
        val testStartTime = System.currentTimeMillis()
        

        repeat(concurrentThreads) { threadId ->
            executors.submit {
                try {
                    repeat(ordersPerThread) { orderId ->
                        val order = createRandomOrder("AAPL", threadId, orderId)
                        if (manager.submitOrder(order)) {
                            successCount.incrementAndGet()
                        } else {
                            failCount.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(60, TimeUnit.SECONDS)
        assertThat(completed)
            .describedAs("All threads should complete within 60 seconds")
            .isTrue()

        manager.waitForAllCompletion(30, TimeUnit.SECONDS)
        
        val testDuration = System.currentTimeMillis() - testStartTime
        val metrics = manager.getMetrics()
        val totalSubmitted = successCount.get() + failCount.get()
        val successRate = successCount.get().toDouble() / totalSubmitted
        val rejectionRate = failCount.get().toDouble() / totalSubmitted
        

        logger.info("=" * 60)
        logger.info("BACKPRESSURE TEST RESULTS")
        logger.info("=" * 60)
        logger.info("Test Configuration:")
        logger.info("  - Concurrent Threads: $concurrentThreads")
        logger.info("  - Orders per Thread: $ordersPerThread")
        logger.info("  - Expected Total Orders: $expectedTotalOrders")
        logger.info("  - Thread Pool Size: 16")
        logger.info("  - Test Duration: ${testDuration}ms")
        logger.info("")
        logger.info("Submission Results:")
        logger.info("  - Total Submitted: $totalSubmitted")
        logger.info("  - Successfully Accepted: ${successCount.get()} (${String.format("%.2f%%", successRate * 100)})")
        logger.info("  - Rejected by Backpressure: ${failCount.get()} (${String.format("%.2f%%", rejectionRate * 100)})")
        logger.info("")
        logger.info("Processing Results:")
        logger.info("  - Orders Processed: ${metrics.totalOrdersProcessed}")
        logger.info("  - Orders Rejected (Internal): ${metrics.totalOrdersRejected}")
        logger.info("  - Effective Throughput: ${String.format("%.0f", (successCount.get() * 1000.0) / testDuration)} orders/sec")
        logger.info("=" * 60)
        


        assertThat(successCount.get())
            .describedAs("System should successfully process at least 1000 orders under load")
            .isGreaterThan(1000L)

        assertThat(failCount.get())
            .describedAs("Backpressure should reject some orders to protect the system")
            .isGreaterThan(0L)

        assertThat(metrics.totalOrdersProcessed)
            .describedAs("System should process orders even under extreme load")
            .isGreaterThan(100L)

        assertThat(successRate)
            .describedAs("Success rate should be between 5% and 60% under extreme load")
            .isBetween(0.05, 0.60)

        assertThat(totalSubmitted)
            .describedAs("All orders should be attempted")
            .isEqualTo(expectedTotalOrders.toLong())
        
        logger.info("✅ Backpressure test passed - System gracefully degraded under load")
        
        executors.shutdown()
        manager.shutdown()
    }
    

    
    private fun generateMixedOrders(count: Int): List<OrderDTO> {
        return (1..count).map {
            OrderDTO(
                orderId = UUID.randomUUID().toString(),
                userId = "user-${it % 10}",
                symbol = "AAPL",
                orderType = if (it % 3 == 0) OrderType.MARKET else OrderType.LIMIT,
                side = if (it % 2 == 0) OrderSide.BUY else OrderSide.SELL,
                quantity = BigDecimal(100 + (it % 100)),
                price = if (it % 3 == 0) null else BigDecimal(150 + (it % 10)),
                status = OrderStatus.PENDING,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                traceId = UUID.randomUUID().toString()
            )
        }
    }
    
    private fun generateMultiSymbolOrders(symbols: List<String>, ordersPerSymbol: Int): List<OrderDTO> {
        return symbols.flatMap { symbol ->
            (1..ordersPerSymbol).map { i ->
                OrderDTO(
                    orderId = UUID.randomUUID().toString(),
                    userId = "user-${i % 10}",
                    symbol = symbol,
                    orderType = if (i % 3 == 0) OrderType.MARKET else OrderType.LIMIT,
                    side = if (i % 2 == 0) OrderSide.BUY else OrderSide.SELL,
                    quantity = BigDecimal(100 + (i % 100)),
                    price = if (i % 3 == 0) null else BigDecimal(150 + (i % 10)),
                    status = OrderStatus.PENDING,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    traceId = UUID.randomUUID().toString()
                )
            }
        }
    }
    
    private fun generateSimpleMatchableOrders(count: Int): List<OrderDTO> {
        return (1..count).map { i ->
            OrderDTO(
                orderId = UUID.randomUUID().toString(),
                userId = "user-$i",
                symbol = "TEST",
                orderType = OrderType.LIMIT,
                side = if (i % 2 == 0) OrderSide.BUY else OrderSide.SELL,
                quantity = BigDecimal(100),
                price = BigDecimal(100),
                status = OrderStatus.PENDING,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                traceId = UUID.randomUUID().toString()
            )
        }
    }
    
    private fun generatePartialFillOrders(count: Int): List<OrderDTO> {
        return (1..count).map { i ->
            OrderDTO(
                orderId = UUID.randomUUID().toString(),
                userId = "user-$i",
                symbol = "TEST",
                orderType = OrderType.LIMIT,
                side = if (i % 2 == 0) OrderSide.BUY else OrderSide.SELL,
                quantity = BigDecimal(50 + (i % 100)),
                price = BigDecimal(100),
                status = OrderStatus.PENDING,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                traceId = UUID.randomUUID().toString()
            )
        }
    }
    
    private fun generateMixedScenario(count: Int) = generateMixedOrders(count)
    private fun createRandomOrder(symbol: String, threadId: Int, orderId: Int): OrderDTO {
        return OrderDTO(
            orderId = "order-$threadId-$orderId",
            userId = "user-$threadId",
            symbol = symbol,
            orderType = if (orderId % 3 == 0) OrderType.MARKET else OrderType.LIMIT,
            side = if (orderId % 2 == 0) OrderSide.BUY else OrderSide.SELL,
            quantity = BigDecimal(100),
            price = if (orderId % 3 == 0) null else BigDecimal(150 + (orderId % 10)),
            status = OrderStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            traceId = UUID.randomUUID().toString()
        )
    }
    
    private fun measurePerformance(name: String, block: () -> Unit): PerformanceResult {
        val duration = measureTimeMillis { block() }
        val throughput = (TEST_ORDER_COUNT * 1000.0) / duration
        val avgLatency = duration.toDouble() / TEST_ORDER_COUNT
        
        return PerformanceResult(
            name = name,
            duration = duration,
            throughput = throughput,
            avgLatency = avgLatency,
            orderCount = TEST_ORDER_COUNT
        )
    }
    
    /**
     * 멀티스레드 환경에서 성능을 측정 (V1: 락 경합 시뮬레이션)
     */
    private fun measureConcurrentPerformance(
        name: String, 
        threadCount: Int, 
        block: () -> LatencyMetrics
    ): LatencyMetrics {

        logger.info("Measuring concurrent performance: $name with $threadCount threads")

        var metrics: LatencyMetrics
        val duration = measureTimeMillis {
            metrics = block()
        }
        logger.info("$name completed in ${duration}ms")


        return if (metrics.totalDuration != null) {
            metrics
        } else {
            LatencyMetrics(
                latencies = metrics.latencies,
                totalDuration = duration,
                totalOperations = metrics.latencies.size
            )
        }
    }

    /**
     * 단일 스레드 환경에서 성능을 측정 (V2: Lock-Free)
     */
    private fun measureSequentialPerformance(
        name: String,
        block: () -> LatencyMetrics
    ): LatencyMetrics {
        logger.info("Measuring sequential performance: $name")
        val startTime = System.currentTimeMillis()

        val metrics = block()

        val duration = System.currentTimeMillis() - startTime
        logger.info("$name completed in ${duration}ms")


        return if (metrics.totalDuration != null) {
            metrics
        } else {
            LatencyMetrics(
                latencies = metrics.latencies,
                totalDuration = duration,
                totalOperations = metrics.latencies.size
            )
        }
    }

    /**
     * 파티션된 멀티스레드 환경에서 성능을 측정 (V3: Kafka-style)
     */
    private fun measurePartitionedPerformance(
        name: String,
        block: () -> LatencyMetrics
    ): LatencyMetrics {
        logger.info("Measuring partitioned performance: $name")
        val startTime = System.currentTimeMillis()

        val metrics = block()

        val duration = System.currentTimeMillis() - startTime
        logger.info("$name completed in ${duration}ms with symbol-based partitioning")


        return if (metrics.totalDuration != null) {
            metrics
        } else {
            LatencyMetrics(
                latencies = metrics.latencies,
                totalDuration = duration,
                totalOperations = metrics.latencies.size
            )
        }
    }
    
    private fun measureScenarioPerformance(
        manager: MatchingEngineManager,
        scenario: TestScenario
    ): ScenarioResult {
        val startTime = System.currentTimeMillis()
        var successCount = 0
        
        scenario.orders.forEach { order ->
            if (manager.submitOrder(order)) {
                successCount++
            }
        }
        
        manager.waitForAllCompletion(10, TimeUnit.SECONDS)
        
        val duration = System.currentTimeMillis() - startTime
        val avgLatency = duration.toDouble() / scenario.orders.size
        val successRate = successCount.toDouble() / scenario.orders.size
        
        return ScenarioResult(
            name = scenario.name,
            successRate = successRate,
            avgLatency = avgLatency
        )
    }
    
    private fun generateEvolutionReport(results: List<PerformanceResult>) {
        logger.info("=" * 60)
        logger.info("EVOLUTION TEST RESULTS")
        logger.info("=" * 60)
        
        results.forEach { result ->
            logger.info(
                "${result.name}: " +
                "duration=${result.duration}ms, " +
                "throughput=${String.format("%.0f", result.throughput)} orders/sec, " +
                "avgLatency=${String.format("%.2f", result.avgLatency)}ms, " +
                "orders=${result.orderCount}"
            )
        }
        

        if (results.size >= 2) {
            val v1 = results[0]
            val v2 = results[1]
            val improvement = v2.throughput / v1.throughput
            
            logger.info(
                "Performance Improvement V2 vs V1: " +
                "improvement=${String.format("%.1fx", improvement)}, " +
                "latencyReduction=${String.format("%.1f%%", (1 - v2.avgLatency/v1.avgLatency) * 100)}"
            )
        }
        
        if (results.size >= 3) {
            val v2 = results[1]
            val v3 = results[2]
            val improvement = v3.throughput / v2.throughput
            
            logger.info(
                "Performance Improvement V3 vs V2: " +
                "improvement=${String.format("%.1fx", improvement)}, " +
                "latencyReduction=${String.format("%.1f%%", (1 - v3.avgLatency/v2.avgLatency) * 100)}"
            )
        }
        
        logger.info("=" * 60)
    }
    
    /**
     * 백분위수를 포함한 상세한 성능 리포트 생성
     * 실제 비즈니스 로직 기반 성능 비교
     */
    private fun generateDetailedEvolutionReport(results: List<DetailedPerformanceResult>) {
        logger.info("=" * 80)
        logger.info("REAL BUSINESS LOGIC PERFORMANCE TEST RESULTS")
        logger.info("=" * 80)
        

        logger.info(String.format(
            "%-20s | %8s | %8s | %8s | %8s | %8s | %12s",
            "Implementation", "P50(ms)", "P90(ms)", "P99(ms)", "P99.9(ms)", "Avg(ms)", "Throughput"
        ))
        logger.info("-" * 80)
        

        results.forEach { result ->
            val m = result.metrics
            logger.info(String.format(
                "%-20s | %8.2f | %8.2f | %8.2f | %8.2f | %8.2f | %12.0f ops/s",
                result.name, m.p50, m.p90, m.p99, m.p999, m.avg, m.throughput
            ))
        }
        
        logger.info("=" * 80)
        
        // 실제 성능 개선 비율 계산
        if (results.size >= 2) {
            logger.info("\nREAL PERFORMANCE IMPROVEMENTS:")
            logger.info("-" * 40)
            
            val v1 = results[0].metrics
            val v2 = results[1].metrics
            
            logger.info("V2 (Single Thread) vs V1 (Synchronized):")
            logger.info("  - P50 Latency: ${if (v2.p50 < v1.p50) "✅" else "❌"} ${String.format("%.1f%%", (1 - v2.p50/v1.p50) * 100)} reduction")
            logger.info("  - P99 Latency: ${if (v2.p99 < v1.p99) "✅" else "❌"} ${String.format("%.1f%%", (1 - v2.p99/v1.p99) * 100)} reduction")
            logger.info("  - Throughput: ${String.format("%.1fx", v2.throughput/v1.throughput)}")
            logger.info("  - Lock Overhead Eliminated: ${if (v2.p99 < v1.p99) "YES" else "NO"}")
        }
        
        if (results.size >= 3) {
            val v2 = results[1].metrics
            val v3 = results[2].metrics
            
            logger.info("\nV3 (Production Engine) vs V2 (Single Thread):")
            logger.info("  - Parallel Processing Gain: ${String.format("%.1fx", v3.throughput/v2.throughput)}")
            logger.info("  - P99 Latency: ${String.format("%+.1f%%", (v3.p99/v2.p99 - 1) * 100)}")
            logger.info("  - Scalability: ${if (v3.throughput > v2.throughput) "✅ Better" else "❌ Worse"}")
        }
        
        logger.info("=" * 80)
        
        logger.info("\nBUSINESS REQUIREMENTS VALIDATION:")
        results.forEach { result ->
            val m = result.metrics
            logger.info("\n${result.name}:")
            logger.info("  - Order Processing: ${if (m.p99 < 10.0) "✅" else "⚠️"} P99 = ${String.format("%.2f", m.p99)}ms (Target < 10ms)")
            logger.info("  - Throughput: ${if (m.throughput > 1000) "✅" else "⚠️"} ${String.format("%.0f", m.throughput)} ops/s (Target > 1000)")
            logger.info("  - Stability: ${if (m.p999 < 50.0) "✅" else "⚠️"} P99.9 = ${String.format("%.2f", m.p999)}ms (Target < 50ms)")
        }
        
        logger.info("=" * 80)
    }
    

    private class SynchronizedOrderBookEngine(private val symbol: String) {
        private val orderBook = OrderBook(symbol)
        private var totalMatches = 0
        private var totalOrders = 0
        
        @Synchronized
        fun processOrder(order: OrderDTO) {
            val trades = when (order.orderType) {
                OrderType.MARKET -> orderBook.processMarketOrder(order)
                OrderType.LIMIT -> orderBook.processLimitOrder(order)
            }
            
            totalMatches += trades.size
            totalOrders++
        }
        
        @Synchronized
        fun getMatchedOrdersCount() = totalMatches
        
        @Synchronized
        fun getTotalOrdersProcessed() = totalOrders
    }
    
    private class SingleThreadOrderBookEngine(private val symbol: String) {
        private val orderBook = OrderBook(symbol)
        private var totalMatches = 0
        private var totalOrders = 0
        
        fun processOrder(order: OrderDTO) {
            // Same business logic without synchronization
            val trades = when (order.orderType) {
                OrderType.MARKET -> orderBook.processMarketOrder(order)
                OrderType.LIMIT -> orderBook.processLimitOrder(order)
            }
            
            totalMatches += trades.size
            totalOrders++
        }
        
        fun getMatchedOrdersCount() = totalMatches
        fun getTotalOrdersProcessed() = totalOrders
    }

    /**
     * 지연시간 측정 결과를 백분위수로 계산하는 데이터 클래스
     */
    private data class LatencyMetrics(
        val latencies: List<Long>,  // 지연시간 목록 (밀리초)
        val totalDuration: Long? = null,  // 전체 실행 시간 (밀리초)
        val totalOperations: Int? = null  // 전체 처리된 작업 수
    ) {
        val sorted: List<Long> = latencies.sorted()
        val count: Int = latencies.size
        
        // 백분위수 계산
        val p50: Double = percentile(50.0)
        val p90: Double = percentile(90.0)
        val p99: Double = percentile(99.0)
        val p999: Double = percentile(99.9)
        
        // 평균 및 처리량 계산
        val avg: Double = if (count > 0) latencies.average() else 0.0
        val min: Long = sorted.firstOrNull() ?: 0
        val max: Long = sorted.lastOrNull() ?: 0
        
        // 처리량 계산: 전체 실행 시간이 있으면 그것을 사용, 없으면 평균 지연시간 기반
        val throughput: Double = when {
            totalDuration != null && totalDuration > 0 && totalOperations != null -> 
                (totalOperations * 1000.0) / totalDuration  // ops/sec based on total time
            avg > 0 -> 
                1000.0 / avg  // ops/sec based on average latency (fallback)
            else -> 0.0
        }
        
        private fun percentile(p: Double): Double {
            if (count == 0) return 0.0
            val index = ((p / 100.0) * (count - 1)).toInt()
            return sorted[index].toDouble()
        }
    }
    
    /**
     * 상세한 성능 측정 결과
     */
    private data class DetailedPerformanceResult(
        val name: String,
        val metrics: LatencyMetrics
    )
    
    private data class PerformanceResult(
        val name: String,
        val duration: Long,
        val throughput: Double,
        val avgLatency: Double,
        val orderCount: Int
    )
    
    private data class TestScenario(
        val name: String,
        val orders: List<OrderDTO>
    )
    
    private data class ScenarioResult(
        val name: String,
        val successRate: Double,
        val avgLatency: Double
    )
}

private operator fun String.times(count: Int): String = this.repeat(count)