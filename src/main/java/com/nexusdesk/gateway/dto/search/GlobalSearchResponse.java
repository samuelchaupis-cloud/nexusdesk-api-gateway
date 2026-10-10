package com.nexusdesk.gateway.dto.search;

import java.util.List;
import java.util.Map;

/**
 * Respuesta envolvente unificada para el Command Palette.
 * Retorna las entidades categorizadas y métricas de ejecución.
 */
public record GlobalSearchResponse(
        String query,
        long tookMs,
        int total,
        Map<String, List<SearchResultItem>> results
) {}
