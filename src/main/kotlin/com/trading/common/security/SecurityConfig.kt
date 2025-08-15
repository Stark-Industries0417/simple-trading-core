package com.trading.common.security

import com.trading.common.logging.TraceIdFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication
class SecurityConfig(
    private val traceIdFilter: TraceIdFilter
) {
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .headers { headers ->
                headers
                    .frameOptions().deny()
                    .contentTypeOptions().and()
                    .httpStrictTransportSecurity { hstsConfig ->
                        hstsConfig.maxAgeInSeconds(31536000)
                    }
                    .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                    .and()
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers("/api/test/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
    
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("http://localhost:*", "https://api.simple-trading.com")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("X-Trace-Id")
            allowCredentials = true
            maxAge = 3600L
        }
        
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}