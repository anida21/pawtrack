# pawtrack-notification

Notification service of **PawTrack**: reminders for vaccinations, medications and custom tasks, sent by email.
Reminders are generated from RabbitMQ events (health, pet, identity); a scheduler emails due reminders every minute.

Part of the PawTrack platform – see [pawtrack-development](../pawtrack-development) for the architecture and local environment.

## Tech
Spring Boot 4 · Java 17 · PostgreSQL · Liquibase · RabbitMQ · Spring Mail (Mailpit locally) · Keycloak (OAuth2 resource server) · MapStruct · Testcontainers

## Run locally
```bash
# infrastructure (from pawtrack-development)
docker compose up -d
# service
mvn spring-boot:run
```
- Swagger UI: http://localhost:8084/swagger-ui.html
- Health: http://localhost:8084/actuator/health
- Sent emails: http://localhost:8025 (Mailpit)

## Test
```bash
mvn verify   # requires Docker for Testcontainers (Postgres, RabbitMQ, Mailpit)
```
