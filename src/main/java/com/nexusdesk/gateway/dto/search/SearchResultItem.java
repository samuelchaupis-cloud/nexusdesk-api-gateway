package com.nexusdesk.gateway.dto.search;

/**
 * Elemento atómico de resultado de búsqueda global para Command Palette.
 * Inmutable, fuertemente tipado.
 */
public record SearchResultItem(
        Long id,
        String code,
        String title,
        String subtitle,
        String badge,
        String badgeColor,
        String url,
        String type
) {}
