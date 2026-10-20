package com.nexusdesk.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias y Adversariales para JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "TechDeskProCloudEnterpriseSecurityKeyHS512SecretKeyVeryLongStringForCibertecAcademicProject2026SecureHash9876543210";
    private JwtAuthenticationFilter filter;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(TEST_SECRET);
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken(String subject, String role, String email, String name, Long clienteId, long expiryOffsetMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryOffsetMs);

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry);

        if (role != null) builder.claim("rol", role);
        if (email != null) builder.claim("email", email);
        if (name != null) builder.claim("nombre", name);
        if (clienteId != null) builder.claim("clienteId", clienteId);

        return builder.signWith(signingKey, Jwts.SIG.HS512).compact();
    }

    private String generateTokenWithForeignSecret(String subject, String role) {
        String foreignSecret = "DifferentSecretKeyForAdversarialTestingPurposesOnlyWhichIsLongEnoughHS512123456789";
        SecretKey foreignKey = Keys.hmacShaKeyFor(foreignSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("rol", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(foreignKey, Jwts.SIG.HS512)
                .compact();
    }

    @Test
    @DisplayName("Sin cabecera Authorization debe responder 401 Unauthorized")
    void testMissingAuthorizationHeaderReturns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        GatewayFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainInvoked.get(), "La cadena de filtros no debe ejecutarse si no hay token");
    }

    @Test
    @DisplayName("Cabecera Authorization no-Bearer o vacía debe responder 401 Unauthorized")
    void testMalformedAuthorizationHeaderReturns401() {
        String[] malformedHeaders = {
                "Basic dXNlcjpwYXNz",
                "Bearer",
                "Bearer ",
                "   ",
                "Token abcdef12345"
        };

        for (String malformedHeader : malformedHeaders) {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/1")
                    .header(HttpHeaders.AUTHORIZATION, malformedHeader)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

            GatewayFilterChain chain = ex -> {
                chainInvoked.set(true);
                return Mono.empty();
            };

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode(),
                    "Debe ser 401 para header: " + malformedHeader);
            assertFalse(chainInvoked.get());
        }
    }

    @Test
    @DisplayName("Token JWT con firma adulterada/clave ajena debe responder 401 Unauthorized")
    void testInvalidSignatureTokenReturns401() {
        String foreignToken = generateTokenWithForeignSecret("100", "Administrador");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        GatewayFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainInvoked.get());
    }

    @Test
    @DisplayName("Token JWT expirado debe responder 401 Unauthorized")
    void testExpiredTokenReturns401() {
        String expiredToken = generateToken("100", "Administrador", "admin@techdesk.com", "Admin", null, -60000);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        GatewayFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainInvoked.get());
    }

    @Test
    @DisplayName("Token JWT malformado debe responder 401 Unauthorized")
    void testMalformedJwtReturns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt.string")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        GatewayFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainInvoked.get());
    }

    @Test
    @DisplayName("Token válido propaga cabeceras X-User-Id, X-User-Email, X-User-Roles downstream")
    void testValidTokenPropagatesSecureDownstreamHeaders() {
        String token = generateToken("42", "Supervisor", "supervisor@techdesk.com", "Jane Doe", 100L, 3600000);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> mutatedExchangeRef = new AtomicReference<>();

        GatewayFilterChain chain = ex -> {
            mutatedExchangeRef.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertNotNull(mutatedExchangeRef.get(), "La cadena debió ejecutarse");
        HttpHeaders downstreamHeaders = mutatedExchangeRef.get().getRequest().getHeaders();

        assertEquals("42", downstreamHeaders.getFirst("X-User-Id"));
        assertEquals("supervisor@techdesk.com", downstreamHeaders.getFirst("X-User-Email"));
        assertEquals("Supervisor", downstreamHeaders.getFirst("X-User-Roles"));
        assertEquals("Supervisor", downstreamHeaders.getFirst("X-User-Role"));
        assertEquals("Jane Doe", downstreamHeaders.getFirst("X-User-Name"));
        assertEquals("100", downstreamHeaders.getFirst("X-User-ClientId"));
    }

    @Test
    @DisplayName("Anti-Spoofing: Sobrescribe cabeceras X-User inyectadas previamente por clientes maliciosos")
    void testAntiSpoofingOverwritesInjectedHeaders() {
        String token = generateToken("7", "Soporte", "tech@techdesk.com", "Tech User", null, 3600000);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/customers/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "999999") // Intento de spoofing
                .header("X-User-Role", "Administrador") // Intento de spoofing
                .header("X-User-Email", "hacker@evil.com") // Intento de spoofing
                .header("X-User-ClientId", "666") // Intento de spoofing
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> mutatedExchangeRef = new AtomicReference<>();

        GatewayFilterChain chain = ex -> {
            mutatedExchangeRef.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertNotNull(mutatedExchangeRef.get());
        HttpHeaders downstreamHeaders = mutatedExchangeRef.get().getRequest().getHeaders();

        // Valores deben corresponder estrictamente a los claims del JWT
        assertEquals("7", downstreamHeaders.getFirst("X-User-Id"));
        assertEquals(1, downstreamHeaders.get("X-User-Id").size());

        assertEquals("Soporte", downstreamHeaders.getFirst("X-User-Roles"));
        assertEquals("Soporte", downstreamHeaders.getFirst("X-User-Role"));
        assertEquals(1, downstreamHeaders.get("X-User-Role").size());

        assertEquals("tech@techdesk.com", downstreamHeaders.getFirst("X-User-Email"));
        assertEquals(1, downstreamHeaders.get("X-User-Email").size());

        // ClientId era null en el token -> no debe propagar el valor spoofed 666
        assertNull(downstreamHeaders.getFirst("X-User-ClientId"));
    }

    @Test
    @DisplayName("Endpoints públicos deben permitir acceso sin requerir cabecera Authorization")
    void testPublicEndpointsBypassAuthentication() {
        String[] publicPaths = {
                "/api/auth/login",
                "/api/auth/roles",
                "/actuator/health",
                "/v3/api-docs/swagger-config"
        };

        for (String path : publicPaths) {
            MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

            GatewayFilterChain chain = ex -> {
                chainInvoked.set(true);
                return Mono.empty();
            };

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertTrue(chainInvoked.get(), "Endpoint público debe invocar la cadena: " + path);
            assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }

        // GET /api/auth/company-config es público
        MockServerHttpRequest companyConfigRequest = MockServerHttpRequest.method(HttpMethod.GET, "/api/auth/company-config").build();
        MockServerWebExchange companyConfigExchange = MockServerWebExchange.from(companyConfigRequest);
        AtomicReference<Boolean> companyConfigInvoked = new AtomicReference<>(false);

        StepVerifier.create(filter.filter(companyConfigExchange, ex -> {
            companyConfigInvoked.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertTrue(companyConfigInvoked.get());
    }

    @Test
    @DisplayName("RBAC: Rechaza con 403 Forbidden operaciones restringidas para roles no autorizados")
    void testRbacForbiddenScenarios() {
        // 1. Cliente intenta acceder a CRM -> 403
        String clienteToken = generateToken("1", "Cliente", "cli@empresa.com", "Cliente", 50L, 3600000);
        MockServerHttpRequest req1 = MockServerHttpRequest.get("/api/crm/leads")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clienteToken)
                .build();
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);

        StepVerifier.create(filter.filter(ex1, ex -> Mono.empty()))
                .verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, ex1.getResponse().getStatusCode());

        // 2. Soporte intenta anular factura -> 403
        String soporteToken = generateToken("2", "Soporte", "soporte@techdesk.com", "Soporte", null, 3600000);
        MockServerHttpRequest req2 = MockServerHttpRequest.put("/api/billing/invoices/10/anular")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + soporteToken)
                .build();
        MockServerWebExchange ex2 = MockServerWebExchange.from(req2);

        StepVerifier.create(filter.filter(ex2, ex -> Mono.empty()))
                .verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, ex2.getResponse().getStatusCode());

        // 3. Supervisor intenta crear usuario (POST /api/auth/users) -> 403 (solo Admin puede POST)
        String supervisorToken = generateToken("3", "Supervisor", "sup@techdesk.com", "Supervisor", null, 3600000);
        MockServerHttpRequest req3 = MockServerHttpRequest.post("/api/auth/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                .build();
        MockServerWebExchange ex3 = MockServerWebExchange.from(req3);

        StepVerifier.create(filter.filter(ex3, ex -> Mono.empty()))
                .verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, ex3.getResponse().getStatusCode());

        // 4. Admin intenta crear usuario (POST /api/auth/users) -> 200 OK (pasa el filtro)
        String adminToken = generateToken("4", "Administrador", "admin@techdesk.com", "Admin", null, 3600000);
        MockServerHttpRequest req4 = MockServerHttpRequest.post("/api/auth/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .build();
        MockServerWebExchange ex4 = MockServerWebExchange.from(req4);
        AtomicReference<Boolean> adminInvoked = new AtomicReference<>(false);

        StepVerifier.create(filter.filter(ex4, ex -> {
            adminInvoked.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertTrue(adminInvoked.get());
    }
}
