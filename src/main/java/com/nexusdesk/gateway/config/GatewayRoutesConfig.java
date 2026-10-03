package com.nexusdesk.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración programática determinista de rutas para API Gateway.
 * Garantiza el registro incondicional de los 7 microservicios bajo Spring Cloud Gateway 5 (Webflux).
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r.path("/api/auth/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("authCircuitBreaker").setFallbackUri("forward:/fallback/default")))
                        .uri("lb://auth-service"))
                .route("customer-service", r -> r.path("/api/customers/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("customerCircuitBreaker").setFallbackUri("forward:/fallback/customer")))
                        .uri("lb://customer-service"))
                .route("helpdesk-service", r -> r.path("/api/helpdesk/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("helpdeskCircuitBreaker").setFallbackUri("forward:/fallback/helpdesk")))
                        .uri("lb://helpdesk-service"))
                .route("operations-service", r -> r.path("/api/operations/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("operationsCircuitBreaker").setFallbackUri("forward:/fallback/operations")))
                        .uri("lb://operations-service"))
                .route("crm-service", r -> r.path("/api/crm/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("crmCircuitBreaker").setFallbackUri("forward:/fallback/crm")))
                        .uri("lb://crm-service"))
                .route("billing-service", r -> r.path("/api/billing/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("billingCircuitBreaker").setFallbackUri("forward:/fallback/billing")))
                        .uri("lb://billing-service"))
                .route("notification-service", r -> r.path("/api/notifications/**")
                        .filters(f -> f.circuitBreaker(c -> c.setName("notificationCircuitBreaker").setFallbackUri("forward:/fallback/notification")))
                        .uri("lb://notification-service"))
                .build();
    }

    @Bean
    public org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder() {
        return org.springframework.web.reactive.function.client.WebClient.builder();
    }
}
