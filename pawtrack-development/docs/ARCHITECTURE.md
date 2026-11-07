# PawTrack – Architecture

```
 Angular UI ──(OIDC PKCE)──► Keycloak (realm: pawtrack)
     │  Bearer JWT
     ▼
 pawtrack-gateway :8080   routes by path (order matters):
     /api/v1/me/**, /api/v1/households/**, /api/v1/invitations/**          → identity
     /api/v1/pets/*/{vaccinations,vet-visits,medications,weights,summary}/** → health
     /api/v1/pets/**                                                       → pet
     /api/v1/reminders/**                                                  → notification
     │
     ├── identity :8081 ── identity_db   users, households, memberships, invitations
     ├── pet      :8082 ── pet_db        pets, photos (MinIO bucket `pet-photos`)
     ├── health   :8083 ── health_db     vaccinations, vet visits, medications, weights
     └── notification :8084 ── notification_db   reminders, email log → Mailpit (SMTP)

 RabbitMQ topic exchange `pawtrack.events`  (see contracts/events.md)
   identity ─► household.member.*      pet ─► pet.created / pet.deleted
   health   ─► health.vaccination.recorded / health.medication.scheduled
   notification consumes everything it needs to schedule reminders
```

## Key decisions

| Topic | Decision | Why |
|---|---|---|
| Repos | One repo per service + `pawtrack-development` | Independent build/deploy, mirrors real microservice teams |
| Auth | Keycloak issues JWTs; every service is an OAuth2 **resource server** | Standard, no hand-rolled crypto |
| User identity | `sub` claim of the JWT = `userId` (UUID) everywhere | Single key across services |
| Ownership | Pets belong to a **household**, not a user. Users join households | Sharing a pet with partner / pet-sitter |
| Access checks | pet: asks identity `GET /api/v1/me/households` (forwarded token, cached 60 s). health: asks pet `GET /api/v1/pets/{id}` (forwarded token) | Simple, no shared DB |
| Data | Database per service (Postgres), migrations with **Liquibase** | Service autonomy |
| Async | RabbitMQ topic exchange, JSON events with envelope (see events.md) | Decoupled reminders |
| Mapping | MapStruct + Lombok, DTOs never expose entities | |
| API docs | springdoc; contracts in `contracts/*.yaml` are the source of truth | Contract-first, agents work per service |
| Tests | JUnit 5 + Testcontainers (Postgres, RabbitMQ) in services; Jest E2E in `pawtrack-development/e2e` | |
| CI | GitHub Actions per repo: build, test, Docker image to GHCR | |

## Standard service layout (Spring)

```
src/main/java/com/pawtrack/<svc>/
  config/        security, OpenAPI, RabbitMQ, clients
  controller/    REST controllers (thin)
  dto/           request/response records
  entity/        JPA entities
  exception/     domain exceptions + @RestControllerAdvice (RFC 7807 ProblemDetail)
  mapper/        MapStruct mappers
  messaging/     event publishers / listeners
  repository/    Spring Data JPA
  service/       business logic
src/main/resources/
  application.yml, application-local.yml
  db/changelog/db.changelog-master.yaml + changes/
```

## Local URLs

| What | URL |
|---|---|
| Keycloak admin | http://keycloak.pawtrack.local:8180 (admin / admin) |
| RabbitMQ UI | http://localhost:15672 (pawtrack / pawtrack) |
| MinIO console | http://localhost:9001 (pawtrack / pawtrack123) |
| Mailpit | http://localhost:8025 |
| Swagger (per service) | http://localhost:808x/swagger-ui.html |
