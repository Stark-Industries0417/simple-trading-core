package com.trading.common.monitoring
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
@Configuration
class MetricsConfig {

    @Bean
    fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config()
                .commonTags("application", "simple-trading-core")
                .commonTags("service", "trading-platform")
                .meterFilter(MeterFilter.deny { id ->
                    val name = id.name
                    name.contains("password") ||
                    name.contains("secret") ||
                    name.contains("key") ||
                    name.contains("token")
                })
                .meterFilter(MeterFilter.accept())
        }
    }
}
