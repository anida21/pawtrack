# pawtrack-health

Health service of **PawTrack**: vaccinations, vet visits, medications and weight history of a pet, plus a summary
for the dashboard card. Whether the caller may see a pet is decided by the pet service.

Part of the PawTrack platform – see [pawtrack-development](../pawtrack-development) for the architecture and local environment.

## Tech
Spring Boot 4 · Java 17 · PostgreSQL · Liquibase · RabbitMQ · Keycloak (OAuth2 resource server) · MapStruct · Testcontainers

## API
Contract: [`health.yaml`](../pawtrack-development/contracts/health.yaml) – all paths below `/api/v1/pets/{petId}`.

| Method | Path | |
|---|---|---|
| GET / POST | `/vaccinations` | newest first / create, publishes `health.vaccination.recorded` |
| PUT / DELETE | `/vaccinations/{id}` | full update / delete |
| GET / POST | `/vet-visits` | newest first / create (currency defaults to BAM) |
| PUT / DELETE | `/vet-visits/{id}` | full update / delete |
| GET / POST | `/medications` | `activeOnly` filter / create, publishes `health.medication.scheduled` |
| PUT / DELETE | `/medications/{id}` | update (publishes `health.medication.scheduled` again) / delete (publishes `health.medication.cancelled`) |
| GET / POST | `/weights` | oldest first, `from`/`to` (inclusive) / record a weight |
| GET | `/summary` | latest weight, last vet visit, next/overdue vaccinations, active medications |

Access: the caller's token is forwarded to pet `GET /api/v1/pets/{petId}`; 200 = allowed (read and write),
404/403 are passed on, pet unreachable = 503. Consumes `pet.created` / `pet.deleted` (queue `health.pet-events`)
to keep a local `petId → householdId, name` copy and to soft-delete the records of deleted pets.

## Run locally
```bash
# infrastructure (from pawtrack-development)
docker compose up -d
# service (pet must be running on :8082 for access checks)
mvn spring-boot:run
```
- Swagger UI: http://localhost:8083/swagger-ui.html
- Health: http://localhost:8083/actuator/health

| Env var | Default |
|---|---|
| `PET_URL` | `http://localhost:8082` |
| `DB_HOST` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | local docker-compose values (`health_db`) |
| `RABBITMQ_HOST` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | local docker-compose values |
| `KEYCLOAK_ISSUER_URI` | `http://keycloak.localhost:8180/realms/pawtrack` |

## Test
```bash
mvn verify   # requires Docker for Testcontainers (Postgres, RabbitMQ)
```
