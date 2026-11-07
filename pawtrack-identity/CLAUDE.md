# pawtrack-identity – agent notes

**This repo is the reference layout for all PawTrack Spring services.** Keep it clean.

- Contract (source of truth): `../pawtrack-development/contracts/identity.yaml`, events: `../pawtrack-development/contracts/events.md`.
  Do not read other service repos.
- Stack: Spring Boot 4.1, Java 17, Postgres + Liquibase, MapStruct + Lombok, springdoc, RabbitMQ, OAuth2 resource server (Keycloak).
- Packages: `com.pawtrack.identity.{config,controller,dto,entity,exception,mapper,messaging,repository,service}`.
- DB schema: `src/main/resources/db/changelog/changes/` – never edit an applied changeSet, add a new file.
- `ddl-auto: validate` – entities must match Liquibase exactly.
- Current user: `@AuthenticationPrincipal Jwt jwt` → `UUID.fromString(jwt.getSubject())`, email = `email` claim,
  name = `name` (fallback `preferred_username`).
- Errors: throw `NotFoundException / ForbiddenException / ConflictException` → ProblemDetail via `GlobalExceptionHandler`.
- Events: publish envelope `{eventId,type,occurredAt,producer:"pawtrack-identity",data}` to exchange `pawtrack.events`,
  routing key = type, **after commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`).
- CI: `../.github/workflows/identity.yml` (runs only when `pawtrack-identity/**` changes; pushes the image on `main`).
- Tests: `mvn verify` (needs Docker). Controller tests: `@SpringBootTest` + `MockMvc` + `.with(jwt().jwt(j -> j.subject(...)))`.

## Status
- [x] Skeleton: security, OpenAPI, ProblemDetail, Liquibase schema, Testcontainers smoke test, Dockerfile, CI
- [x] Entities + repositories + MapStruct mappers
- [x] `GET/PATCH /api/v1/me` (auto-provision user + default household "My pets", OWNER)
- [x] `GET /api/v1/me/households`
- [x] Households CRUD + member removal rules (last OWNER cannot leave → 409)
- [x] Invitations (secure random code, 7 days, email must match accepting user, 410 if expired)
- [x] Event publishing (`household.*`, `user.updated`)
- [x] Tests for every endpoint incl. 401/403/404/409

## Conventions established here (copy them)
- Services take the `Jwt`, call `UserService.currentUser(jwt)` (provisions on first use) and return DTOs,
  so mapping runs inside the transaction (`open-in-view: false`).
- Creation timestamps are set in entity constructors (not `@CreationTimestamp`, which only fills at flush).
- Events: `IdentityEvent` records → `EventPublisher.publish(...)` → `RabbitEventRelay` (AFTER_COMMIT).
- Tests extend `support.IntegrationTest` (shared Testcontainers context, `TestUser.jwt()`, `awaitEvent(...)`).
  RabbitMQ 4 rejects transient non-exclusive queues: declare test queues durable.

## Behaviour beyond the contract text
- Provisioning publishes `user.updated` and `household.member.added` (OWNER of "My pets");
  creating a household publishes `household.member.added` for its OWNER.
- Accept: email mismatch → 403, already-used code → 410, already a member → 200 and the code is consumed.
- Token without `email` claim → 403 on first call (user cannot be provisioned).

Done = all boxes checked, `mvn verify` green, endpoints match `identity.yaml`.
