package com.lovable.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutingConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r.path("/api/auth/**")
                        .uri("http://localhost:8081"))

                .route("project-service", r -> r.path("/api/projects/**")
                        .uri("http://localhost:8082"))

                .route("ai-service", r -> r.path("/api/ai/**", "/api/chat/**")

                        .uri("http://localhost:8083"))

                .route("chat-service", r -> r.path("/ws/**")
                        .uri("ws://localhost:8084")) // WebSocket scheme!

                .route("preview-service", r -> r.path("/api/previews/**")
                        .filters(f -> f.dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST"))
                        .uri("http://localhost:8085"))
                .build();
    }
}