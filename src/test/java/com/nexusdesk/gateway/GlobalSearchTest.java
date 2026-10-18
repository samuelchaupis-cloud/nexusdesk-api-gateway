package com.nexusdesk.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusdesk.gateway.controller.GlobalSearchController;
import com.nexusdesk.gateway.dto.search.GlobalSearchResponse;
import com.nexusdesk.gateway.service.GlobalSearchAggregatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias y Adversariales para el Buscador Global BFF")
class GlobalSearchTest {

    private GlobalSearchAggregatorService aggregatorService;
    private GlobalSearchController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        aggregatorService = new GlobalSearchAggregatorService(objectMapper);
        controller = new GlobalSearchController(aggregatorService);
    }

    @Test
    @DisplayName("Búsqueda vacía debe retornar acciones rápidas de navegación con 0ms de bloqueo")
    void testEmptyQueryReturnsQuickActions() {
        StepVerifier.create(controller.globalSearch("", 5, "Administrador", null))
                .assertNext(response -> {
                    assertNotNull(response.getBody());
                    GlobalSearchResponse body = response.getBody();
                    assertEquals("", body.query());
                    assertTrue(body.total() > 0);
                    assertTrue(body.results().containsKey("actions"));
                    assertFalse(body.results().get("actions").isEmpty());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Sanitización defensiva: debe eliminar caracteres nulos y recortar queries mayores a 100 caracteres")
    void testQuerySanitization() {
        String dangerousQuery = "laptop\0malicious" + "a".repeat(150);

        StepVerifier.create(controller.globalSearch(dangerousQuery, 5, "Administrador", null))
                .assertNext(response -> {
                    assertNotNull(response.getBody());
                    GlobalSearchResponse body = response.getBody();
                    assertFalse(body.query().contains("\0"));
                    assertTrue(body.query().length() <= 100);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Aislamiento Multi-Tenant: Usuario con rol Cliente no debe recibir clientes externos")
    void testClienteRoleIsolation() {
        StepVerifier.create(aggregatorService.executeSearch("empresa", "Cliente", 99L, 5))
                .assertNext(response -> {
                    assertNotNull(response);
                    // Clientes nunca debe estar presente para rol Cliente
                    assertFalse(response.results().containsKey("clients"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Caos Adversarial: Payloads de inyección SQL y traversal no deben lanzar excepción no controlada")
    void testAdversarialPayloadResilience() {
        String attackPayload = "' OR '1'='1'; DROP TABLE users; -- ../../../etc/passwd \uD83D\uDCA3";

        StepVerifier.create(controller.globalSearch(attackPayload, 10, "Administrador", null))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(200, response.getStatusCode().value());
                    assertNotNull(response.getBody());
                    assertTrue(response.getBody().tookMs() >= 0);
                })
                .verifyComplete();
    }
}
