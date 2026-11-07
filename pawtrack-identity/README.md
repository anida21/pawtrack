# pawtrack-identity

Identity service of **PawTrack**: users (provisioned from Keycloak JWTs), households, memberships and invitations.

Part of the PawTrack platform – see [pawtrack-development](../pawtrack-development) for the architecture and local environment.

## Tech
Spring Boot 4 · Java 17 · PostgreSQL · Liquibase · RabbitMQ · Keycloak (OAuth2 resource server) · MapStruct · Testcontainers

## Run locally
```bash
# infrastructure (from pawtrack-development)
docker compose up -d
# service
mvn spring-boot:run
```
- Swagger UI: http://localhost:8081/swagger-ui.html
- Health: http://localhost:8081/actuator/health

## Test
```bash
mvn verify   # requires Docker for Testcontainers
```
