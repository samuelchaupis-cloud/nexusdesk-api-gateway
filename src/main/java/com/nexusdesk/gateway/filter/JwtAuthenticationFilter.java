package com.nexusdesk.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final String jwtSecret;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/auth/login",
            "/api/auth/roles",
            "/actuator",
            "/v3/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() != null ? request.getMethod().name() : "";

        if ("/api/auth/company-config".equals(path) && "GET".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }

        for (String publicEndpoint : PUBLIC_ENDPOINTS) {
            if (path.startsWith(publicEndpoint)) {
                return chain.filter(exchange);
            }
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.length() <= 7) {
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String role = claims.get("rol", String.class);
            if (role == null || role.isBlank()) {
                Object rolesObj = claims.get("roles");
                if (rolesObj != null) {
                    role = rolesObj.toString();
                }
            }
            if (role == null) {
                role = "";
            }

            if (!isAuthorized(path, method, role)) {
                return onError(exchange, HttpStatus.FORBIDDEN);
            }

            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                Object idObj = claims.get("id");
                if (idObj == null) {
                    idObj = claims.get("userId");
                }
                userId = idObj != null ? String.valueOf(idObj) : "";
            }

            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                Object usuarioObj = claims.get("usuario");
                if (usuarioObj == null) {
                    usuarioObj = claims.get("username");
                }
                if (usuarioObj != null) {
                    email = String.valueOf(usuarioObj);
                } else if (userId.contains("@")) {
                    email = userId;
                } else {
                    email = "";
                }
            }

            String name = claims.get("nombre", String.class);
            if (name == null || name.isBlank()) {
                name = claims.get("name", String.class);
            }

            Object clientIdObj = claims.get("clienteId");

            final String resolvedUserId = userId;
            final String resolvedEmail = email;
            final String resolvedRole = role;
            final String resolvedName = name;

            ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.set("X-User-Id", resolvedUserId);
                        headers.set("X-User-Email", resolvedEmail);
                        headers.set("X-User-Roles", resolvedRole);
                        headers.set("X-User-Role", resolvedRole);
                        if (resolvedName != null && !resolvedName.isBlank()) {
                            headers.set("X-User-Name", resolvedName);
                        } else {
                            headers.remove("X-User-Name");
                        }
                        if (clientIdObj != null) {
                            headers.set("X-User-ClientId", String.valueOf(clientIdObj));
                        } else {
                            headers.remove("X-User-ClientId");
                        }
                    });

            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        } catch (SecurityException | MalformedJwtException | ExpiredJwtException |
                 UnsupportedJwtException | IllegalArgumentException e) {
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isAuthorized(String path, String method, String role) {
        if (role == null) {
            return false;
        }
        boolean isAdmin = "Administrador".equalsIgnoreCase(role) || role.toUpperCase().contains("ADMIN");
        boolean isSupervisor = "Supervisor".equalsIgnoreCase(role) || role.toUpperCase().contains("SUPERVISOR");
        boolean isComercial = "Comercial".equalsIgnoreCase(role) || role.toUpperCase().contains("COMERCIAL");

        if (path.startsWith("/api/auth/users")) {
            return "GET".equalsIgnoreCase(method) ? (isAdmin || isSupervisor) : isAdmin;
        }
        if (path.contains("/api/billing/invoices") && path.endsWith("/anular")) {
            return isAdmin;
        }
        if (path.startsWith("/api/billing/reports")) {
            return isAdmin || isSupervisor;
        }
        if (path.startsWith("/api/crm")) {
            return isAdmin || isSupervisor || isComercial;
        }
        return true;
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
