package com.nexusdesk.gateway.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador raíz para API Gateway (:8080).
 * Provee respuesta informativa tanto en JSON (para clientes API) como en HTML amigable
 * con enlace directo al Frontend Angular (:4300) ante accesos desde el navegador.
 */
@RestController
public class RootController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> rootInfo() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "NexusDesk Cloud - API Gateway");
        body.put("status", "UP");
        body.put("frontend", "http://localhost:4300");
        body.put("eureka", "http://localhost:8761");
        body.put("message", "API Gateway operativo. Para usar la plataforma abra el frontend en http://localhost:4300");
        return Mono.just(ResponseEntity.ok(body));
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> rootHtml() {
        String html = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="refresh" content="3;url=http://localhost:4300">
                <title>NexusDesk Cloud - API Gateway</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0C0C0E; color: #F4F4F5; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
                    .card { background: #18191E; padding: 2.5rem; border-radius: 1rem; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.6); text-align: center; max-width: 480px; border: 1px solid #27282F; }
                    h1 { color: #E56A44; margin-bottom: 0.5rem; font-size: 1.75rem; font-weight: 800; }
                    p { color: #A1A1AA; font-size: 1rem; line-height: 1.5; }
                    .btn { display: inline-block; background: #E56A44; color: white; padding: 0.75rem 1.75rem; border-radius: 0.5rem; text-decoration: none; font-weight: 600; margin-top: 1rem; transition: background 0.2s; }
                    .btn:hover { background: #D45A36; }
                    .badge { display: inline-block; background: #065f46; color: #34d399; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.85rem; font-weight: bold; margin-bottom: 1rem; }
                </style>
            </head>
            <body>
                <div class="card">
                    <span class="badge">● API Gateway Operativo</span>
                    <h1>NexusDesk Cloud</h1>
                    <p>El puerto <strong>:8080</strong> es el enrutador de microservicios backend.</p>
                    <p>La aplicación visual para usuarios y técnicos se encuentra en el <strong>Frontend Angular</strong>.</p>
                    <a href="http://localhost:4300" class="btn">Abrir NexusDesk Frontend (Puerto 4300)</a>
                </div>
            </body>
            </html>
            """;
        return Mono.just(ResponseEntity.ok(html));
    }
}
