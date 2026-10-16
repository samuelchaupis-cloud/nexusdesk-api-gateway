package com.nexusdesk.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusdesk.gateway.dto.search.GlobalSearchResponse;
import com.nexusdesk.gateway.dto.search.SearchResultItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Servicio Agregador Reactivo BFF para Búsqueda Global.
 * Orquesta consultas paralelas asíncronas no bloqueantes hacia los microservicios downstream
 * con resiliencia ante caídas y tiempos de respuesta garantizados.
 */
@Service
public class GlobalSearchAggregatorService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${services.helpdesk.url:http://localhost:8083}")
    private String helpdeskServiceUrl = "http://localhost:8083";

    @Value("${services.customer.url:http://localhost:8082}")
    private String customerServiceUrl = "http://localhost:8082";

    @Value("${services.operations.url:http://localhost:8084}")
    private String operationsServiceUrl = "http://localhost:8084";

    private static final List<SearchResultItem> QUICK_ACTIONS = List.of(
            new SearchResultItem(1L, "ACT-01", "Crear Nuevo Ticket", "Registrar incidencia o requerimiento de soporte", "Acción", "brand", "/tickets/crear", "action"),
            new SearchResultItem(2L, "ACT-02", "Bandeja de Tickets", "Ver todos los tickets de soporte", "Navegación", "primary", "/tickets", "action"),
            new SearchResultItem(3L, "ACT-03", "Directorio de Clientes", "Gestionar empresas y contactos", "Navegación", "info", "/clients", "action"),
            new SearchResultItem(4L, "ACT-04", "Inventario de Activos TI", "Equipos, servidores y garantías", "Navegación", "secondary", "/assets", "action"),
            new SearchResultItem(5L, "ACT-05", "Órdenes de Servicio", "Visitas técnicas y mantenimiento", "Navegación", "warning", "/orders", "action"),
            new SearchResultItem(6L, "ACT-06", "Comprobantes y Facturación", "Facturas electrónicas y notas de venta", "Navegación", "success", "/billing", "action"),
            new SearchResultItem(7L, "ACT-07", "Base de Conocimientos", "Guías, manuales y soluciones frecuentes", "Navegación", "dark", "/knowledge", "action"),
            new SearchResultItem(8L, "ACT-08", "Configuración del Sistema", "Empresa, usuarios y ajustes de plataforma", "Navegación", "danger", "/settings", "action")
    );

    public GlobalSearchAggregatorService(ObjectMapper objectMapper) {
        this.webClient = WebClient.builder().build();
        this.objectMapper = objectMapper;
    }

    public Mono<GlobalSearchResponse> executeSearch(String rawQuery, String userRole, Long userClientId, int limit) {
        long startTime = System.currentTimeMillis();
        String q = rawQuery == null ? "" : rawQuery.trim();
        int safeLimit = Math.max(1, Math.min(limit, 20));

        // Si la búsqueda está vacía, retornar acciones rápidas inmediatamente (0ms latencia)
        if (q.isEmpty()) {
            Map<String, List<SearchResultItem>> emptyResults = new LinkedHashMap<>();
            emptyResults.put("actions", QUICK_ACTIONS.subList(0, Math.min(5, QUICK_ACTIONS.size())));
            return Mono.just(new GlobalSearchResponse("", System.currentTimeMillis() - startTime, emptyResults.get("actions").size(), emptyResults));
        }

        String encodedQ = URLEncoder.encode(q, StandardCharsets.UTF_8);

        Mono<List<SearchResultItem>> ticketsMono = searchTickets(encodedQ, userRole, userClientId, safeLimit);
        Mono<List<SearchResultItem>> clientsMono = searchClients(encodedQ, userRole, safeLimit);
        Mono<List<SearchResultItem>> assetsMono = searchAssets(encodedQ, userRole, userClientId, safeLimit);
        Mono<List<SearchResultItem>> ordersMono = searchOrders(encodedQ, userRole, userClientId, safeLimit);

        return Mono.zip(ticketsMono, clientsMono, assetsMono, ordersMono)
                .map(tuple -> {
                    List<SearchResultItem> tickets = tuple.getT1();
                    List<SearchResultItem> clients = tuple.getT2();
                    List<SearchResultItem> assets = tuple.getT3();
                    List<SearchResultItem> orders = tuple.getT4();

                    List<SearchResultItem> actions = filterQuickActions(q);

                    Map<String, List<SearchResultItem>> resultsMap = new LinkedHashMap<>();
                    if (!tickets.isEmpty()) resultsMap.put("tickets", tickets);
                    if (!clients.isEmpty()) resultsMap.put("clients", clients);
                    if (!assets.isEmpty()) resultsMap.put("assets", assets);
                    if (!orders.isEmpty()) resultsMap.put("orders", orders);
                    if (!actions.isEmpty()) resultsMap.put("actions", actions);

                    int totalFound = tickets.size() + clients.size() + assets.size() + orders.size() + actions.size();
                    long duration = System.currentTimeMillis() - startTime;

                    return new GlobalSearchResponse(q, duration, totalFound, resultsMap);
                });
    }

    private Mono<List<SearchResultItem>> searchTickets(String encodedQ, String userRole, Long userClientId, int limit) {
        String uri = helpdeskServiceUrl + "/api/helpdesk/tickets?q=" + encodedQ + "&size=" + limit;

        return webClient.get()
                .uri(uri)
                .headers(headers -> {
                    if (userRole != null) headers.set("X-User-Role", userRole);
                    if (userClientId != null) headers.set("X-User-ClientId", String.valueOf(userClientId));
                })
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(800))
                .map(this::parseTicketsResponse)
                .onErrorReturn(Collections.emptyList());
    }

    private Mono<List<SearchResultItem>> searchClients(String encodedQ, String userRole, int limit) {
        // Los usuarios con rol Cliente no deben listar clientes ajenos en la búsqueda global
        if ("Cliente".equalsIgnoreCase(userRole)) {
            return Mono.just(Collections.emptyList());
        }

        String uri = customerServiceUrl + "/api/customers/clients?q=" + encodedQ + "&size=" + limit;

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(800))
                .map(this::parseClientsResponse)
                .onErrorReturn(Collections.emptyList());
    }

    private Mono<List<SearchResultItem>> searchAssets(String encodedQ, String userRole, Long userClientId, int limit) {
        StringBuilder uriBuilder = new StringBuilder(customerServiceUrl)
                .append("/api/customers/assets?q=")
                .append(encodedQ)
                .append("&size=")
                .append(limit);

        if ("Cliente".equalsIgnoreCase(userRole) && userClientId != null) {
            uriBuilder.append("&clienteId=").append(userClientId);
        }

        return webClient.get()
                .uri(uriBuilder.toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(800))
                .map(this::parseAssetsResponse)
                .onErrorReturn(Collections.emptyList());
    }

    private static final Map<Integer, String> CANONICAL_CLIENTS = Map.ofEntries(
            Map.entry(1, "Clínica San Gabriel"),
            Map.entry(2, "Distribuidora Andina S.A.C."),
            Map.entry(3, "Colegio Santa Rosa"),
            Map.entry(4, "Estudio Contable Valdivia"),
            Map.entry(5, "Constructora Pacífico"),
            Map.entry(6, "Hotel Mirador del Sol"),
            Map.entry(7, "Transportes Inca Express"),
            Map.entry(8, "Farmacias Vida Plus"),
            Map.entry(9, "Empresa Automatizada SAC Test"),
            Map.entry(10, "Clínica Santa Elena"),
            Map.entry(11, "Empresa Peruana S.A.C."),
            Map.entry(12, "Corporación Aceros del Sur")
    );

    private String sanitizeClientDisplay(String clientRaw) {
        if (clientRaw == null || clientRaw.isBlank()) return "";
        if (clientRaw.toLowerCase().contains("cliente #")) {
            try {
                String numStr = clientRaw.replaceAll("[^0-9]", "");
                if (!numStr.isEmpty()) {
                    int id = Integer.parseInt(numStr);
                    return CANONICAL_CLIENTS.getOrDefault(id, "Empresa Cliente " + id);
                }
            } catch (Exception ignored) {}
        }
        return clientRaw;
    }

    private List<SearchResultItem> parseTicketsResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.has("content") ? root.get("content") : root;
            if (!content.isArray()) return Collections.emptyList();

            List<SearchResultItem> list = new ArrayList<>();
            for (JsonNode node : content) {
                Long id = node.path("id").asLong();
                String code = node.path("codigo").asText("TK-" + id);
                String title = node.path("asunto").asText("Sin Asunto");
                String client = sanitizeClientDisplay(node.path("clienteNombre").asText(""));
                String priority = node.path("prioridad").asText("Normal");
                String state = node.path("estado").asText("Abierto");

                String subtitle = client.isBlank() ? "Prioridad: " + priority : client + " · " + priority;
                String badgeColor = mapStateBadgeColor(state);

                list.add(new SearchResultItem(
                        id,
                        code,
                        title,
                        subtitle,
                        state,
                        badgeColor,
                        "/tickets?id=" + id + "&q=" + code,
                        "ticket"
                ));
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<SearchResultItem> parseClientsResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.has("content") ? root.get("content") : root;
            if (!content.isArray()) return Collections.emptyList();

            List<SearchResultItem> list = new ArrayList<>();
            for (JsonNode node : content) {
                Long id = node.path("id").asLong();
                String name = node.path("razonSocial").asText("Sin Nombre");
                String ruc = node.path("ruc").asText("");
                String rubro = node.path("rubro").asText("");
                String email = node.path("email").asText("");
                String plan = node.path("plan").asText("Estándar");

                String subtitle = !rubro.isBlank() ? rubro : email;

                list.add(new SearchResultItem(
                        id,
                        ruc,
                        name,
                        subtitle,
                        plan,
                        "info",
                        "/clients?q=" + (ruc.isBlank() ? name : ruc),
                        "client"
                ));
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<SearchResultItem> parseAssetsResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.has("content") ? root.get("content") : root;
            if (!content.isArray()) return Collections.emptyList();

            List<SearchResultItem> list = new ArrayList<>();
            for (JsonNode node : content) {
                Long id = node.path("id").asLong();
                String code = node.path("codigo").asText("ACT-" + id);
                String tipo = node.path("tipo").asText("Equipo");
                String marca = node.path("marca").asText("");
                String modelo = node.path("modelo").asText("");
                String serie = node.path("serie").asText("");
                String cliente = sanitizeClientDisplay(node.path("clienteRazonSocial").asText(""));
                String estado = node.path("estado").asText("Operativo");

                String title = (marca + " " + modelo).trim();
                if (title.isBlank()) title = tipo;

                String subtitle = (serie.isBlank() ? "" : "S/N: " + serie + " · ") + cliente;

                list.add(new SearchResultItem(
                        id,
                        code,
                        title,
                        subtitle,
                        estado,
                        "secondary",
                        "/assets?q=" + code,
                        "asset"
                ));
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<SearchResultItem> filterQuickActions(String query) {
        String lowerQ = query.toLowerCase(Locale.ROOT);
        List<SearchResultItem> matches = new ArrayList<>();
        for (SearchResultItem action : QUICK_ACTIONS) {
            if (action.title().toLowerCase(Locale.ROOT).contains(lowerQ)
                    || action.subtitle().toLowerCase(Locale.ROOT).contains(lowerQ)
                    || action.url().toLowerCase(Locale.ROOT).contains(lowerQ)) {
                matches.add(action);
            }
        }
        return matches;
    }

    private Mono<List<SearchResultItem>> searchOrders(String encodedQ, String userRole, Long userClientId, int limit) {
        StringBuilder uriBuilder = new StringBuilder(operationsServiceUrl)
                .append("/api/operations/orders?q=")
                .append(encodedQ)
                .append("&size=")
                .append(limit);

        if ("Cliente".equalsIgnoreCase(userRole) && userClientId != null) {
            uriBuilder.append("&clienteId=").append(userClientId);
        }

        return webClient.get()
                .uri(uriBuilder.toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(800))
                .map(this::parseOrdersResponse)
                .onErrorReturn(Collections.emptyList());
    }

    private List<SearchResultItem> parseOrdersResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.has("content") ? root.get("content") : root;
            if (!content.isArray()) return Collections.emptyList();

            List<SearchResultItem> list = new ArrayList<>();
            for (JsonNode node : content) {
                Long id = node.path("id").asLong();
                String code = node.path("codigo").asText("OS-" + id);
                String tipo = node.path("tipo").asText("correctiva");
                String estado = node.path("estado").asText("pendiente");
                String desc = node.path("descripcionTrabajo").asText("Orden de servicio");

                String title = code + " · " + formatOrderType(tipo);
                String subtitle = desc.length() > 70 ? desc.substring(0, 67) + "..." : desc;
                String badgeColor = mapOrderStateBadgeColor(estado);

                list.add(new SearchResultItem(
                        id,
                        code,
                        title,
                        subtitle,
                        estado,
                        badgeColor,
                        "/orders?id=" + id + "&q=" + code,
                        "order"
                ));
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String formatOrderType(String tipo) {
        if (tipo == null || tipo.isBlank()) return "General";
        return switch (tipo.toLowerCase(Locale.ROOT)) {
            case "correctiva" -> "Correctivo";
            case "preventiva" -> "Preventivo";
            case "instalacion" -> "Instalación";
            case "diagnostico" -> "Diagnóstico";
            case "garantia" -> "Garantía";
            default -> tipo.substring(0, 1).toUpperCase(Locale.ROOT) + tipo.substring(1);
        };
    }

    private String mapOrderStateBadgeColor(String state) {
        if (state == null) return "secondary";
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "pendiente", "borrador" -> "warning";
            case "en progreso", "en_proceso", "iniciada" -> "primary";
            case "completada", "finalizada" -> "success";
            case "cancelada", "anulada" -> "danger";
            default -> "secondary";
        };
    }

    private String mapStateBadgeColor(String state) {
        if (state == null) return "secondary";
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "abierto", "nuevo" -> "warning";
            case "en progreso", "asignado", "en_proceso" -> "primary";
            case "espera cliente", "en espera" -> "info";
            case "resuelto", "cerrado", "finalizado" -> "success";
            case "cancelado", "anulado" -> "danger";
            default -> "secondary";
        };
    }
}
