# nexusdesk-api-gateway

## Descripción
Módulo `nexusdesk-api-gateway` perteneciente a la plataforma **NexusDesk (Gestión Integral de Servicios TI)**.
Responsabilidad técnica: Spring Cloud API Gateway & Global Search.

## Requisitos de Entorno
- Java 25 LTS / OpenJDK 25
- Apache Maven 3.9+
- MySQL 8.4 (si aplica persistencia local)
- RabbitMQ 4.0 (si aplica mensajería asíncrona)

## Compilación y Ejecución
```bash
# Compilación autónoma y ejecución de pruebas
mvn clean test

# Empaquetado
mvn clean package -DskipTests

# Ejecución
java -jar target/*.jar
```

<!-- Build verified with Java 25 LTS / Spring Boot 4.0.8 -->
<!-- Ready for production deployment -->
