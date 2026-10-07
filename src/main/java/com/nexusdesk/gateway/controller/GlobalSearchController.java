package com.nexusdesk.gateway.controller;

import com.nexusdesk.gateway.dto.search.GlobalSearchResponse;
import com.nexusdesk.gateway.service.GlobalSearchAggregatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Controlador REST Reactivo para Búsqueda Global en API Gateway (:8080).
 * Punto único de entrada para el Command Palette (Ctrl+K) del frontend.
 */
@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {

    private final GlobalSearchAggregatorService aggregatorService;

    public GlobalSearchController(GlobalSearchAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("/global")
    public Mono<ResponseEntity<GlobalSearchResponse>> globalSearch(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-ClientId", required = false) Long userClientId) {

        // Sanitización defensiva de entrada contra caracteres nulos y longitudes anómalas
        String sanitizedQuery = query != null ? query.replace("\0", "").trim() : "";
        if (sanitizedQuery.length() > 100) {
            sanitizedQuery = sanitizedQuery.substring(0, 100);
        }

        int boundedLimit = Math.max(1, Math.min(limit, 20));

        return aggregatorService.executeSearch(sanitizedQuery, userRole, userClientId, boundedLimit)
                .map(ResponseEntity::ok);
    }
}
