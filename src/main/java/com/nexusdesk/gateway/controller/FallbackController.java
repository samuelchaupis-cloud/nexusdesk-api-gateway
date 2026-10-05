package com.nexusdesk.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador reactivo de Fallback para Circuit Breakers en API Gateway.
 * Implementa endpoints RFC 7807 ante fallas o timeout en microservicios downstream.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/customer")
    public Mono<ResponseEntity<Map<String, Object>>> customerFallback() {
        return Mono.just(createFallbackResponse(
                "customer-service",
                "El servicio de gestión de clientes no está disponible temporalmente. Intente más tarde."));
    }

    @RequestMapping("/helpdesk")
    public Mono<ResponseEntity<Map<String, Object>>> helpdeskFallback() {
        return Mono.just(createFallbackResponse(
                "helpdesk-service",
                "El servicio de helpdesk y soporte no está disponible temporalmente. Intente más tarde."));
    }

    @RequestMapping("/operations")
    public Mono<ResponseEntity<Map<String, Object>>> operationsFallback() {
        return Mono.just(createFallbackResponse(
                "operations-service",
                "El servicio de operaciones no está disponible temporalmente. Intente más tarde."));
    }

    @RequestMapping("/crm")
    public Mono<ResponseEntity<Map<String, Object>>> crmFallback() {
        return Mono.just(createFallbackResponse(
                "crm-service",
                "El servicio de CRM no está disponible temporalmente. Intente más tarde."));
    }

    @RequestMapping("/billing")
    public Mono<ResponseEntity<Map<String, Object>>> billingFallback() {
        return Mono.just(createFallbackResponse(
                "billing-service",
                "El servicio de facturación no está disponible temporalmente. Intente más tarde."));
    }

    @RequestMapping("/notification")
    public Mono<ResponseEntity<Map<String, Object>>> notificationFallback() {
        return Mono.just(createFallbackResponse(
                "notification-service",
                "El servicio de notificaciones no está disponible temporalmente. Intente más tarde."));
    }

    @RequestMapping("/default")
    public Mono<ResponseEntity<Map<String, Object>>> defaultFallback() {
        return Mono.just(createFallbackResponse(
                "gateway-service",
                "El servicio solicitado no está disponible temporalmente. Intente más tarde."));
    }

    private ResponseEntity<Map<String, Object>> createFallbackResponse(String service, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        body.put("message", message);
        body.put("service", service);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
