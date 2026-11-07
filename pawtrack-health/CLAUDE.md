# pawtrack-health – agent notes

Vaccinations, vet visits, medications and weight history of a pet, plus the dashboard summary.
Layout copied from `../pawtrack-identity` (the reference); consumer / HTTP client / test patterns from `../pawtrack-pet`.

- Contract (source of truth): `../pawtrack-development/contracts/health.yaml` (1.2.0), events: `../pawtrack-development/contracts/events.md`.
  Do not read other service folders.
- Stack: Spring Boot 4.1, Java 17, Postgres + Liquibase, MapStruct + Lombok, springdoc, RabbitMQ, OAuth2 resource server (Keycloak).
- Packages: `com.pawtrack.health.{config,controller,dto,entity,exception,mapper,messaging,repository,service}`.
- DB schema: `src/main/resources/db/changelog/changes/` – never edit an applied changeSet, add a new file.
- `ddl-auto: validate` – entities must match Liquibase exactly.
- Current user: `@AuthenticationPrincipal Jwt jwt` → `UUID.fromString(jwt.getSubject())` (stored as `created_by`).
- Access (health.yaml): `service.PetAccess` asks pet `GET /api/v1/pets/{petId}` through `config.PetClient`
  (`RestPetClient`), forwarding the caller's token, on every request (no cache). 200 = allowed for **read and write**
  (so SITTERs may add records — the contract says "same rule"); pet 404 → 404, 403 → 403, anything else → 503.
  A pet the local copy knows as deleted is 404 without asking pet.
- Errors: throw `NotFoundException / ForbiddenException / BadRequestException / DependencyUnavailableException`
  → ProblemDetail via `GlobalExceptionHandler`.
- Events: publish envelope `{eventId,type,occurredAt,producer:"pawtrack-health",data}` to exchange `pawtrack.events`,
  routing key = type, **after commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`). Optional data fields
  (`?` in events.md) are omitted when empty.
- CI: `../.github/workflows/health.yml` (runs only when `pawtrack-health/**` changes; pushes the image on `main`).
- Tests: `mvn verify` (needs Docker). Controller tests: `@SpringBootTest` + `MockMvc` + `.with(jwt().jwt(j -> j.subject(...)))`.

## Status
- [x] Skeleton: security, OpenAPI, ProblemDetail, Liquibase schema, Testcontainers smoke test, Dockerfile, CI
- [x] Entities + repositories + MapStruct mapper (`active` computed, cost/weight rounding, currency default BAM)
- [x] Access checks via pet (token forwarded, 403/404 passed on, 503 if pet is unreachable)
- [x] `GET/POST /vaccinations`, `PUT/DELETE /vaccinations/{id}`
- [x] `GET/POST /vet-visits`, `PUT/DELETE /vet-visits/{id}`
- [x] `GET/POST /medications` (`activeOnly`), `PUT/DELETE /medications/{id}`
- [x] `GET/POST /weights` (`from`/`to`)
- [x] `GET /summary`
- [x] Event publishing (`health.vaccination.recorded` on create and update, `health.medication.scheduled`, `health.medication.cancelled`) after commit
- [x] `pet.created` / `pet.deleted` consumer: queue `health.pet-events` + DLQ, idempotent (`processed_events`),
      keeps the local pet copy, soft-deletes records of deleted pets
- [x] Tests for every endpoint incl. 400/401/403/404/503, consumer, events, pet client
- [x] `pet.updated` consumed as an upsert of the local copy; backfill: `messaging/ReplayRequester` publishes
      `replay.requested` (pet.updated) on startup (`pawtrack.events.replay-on-startup`, events.md)

## Conventions established here (copy them)
- Services take the `Jwt` and return DTOs, so mapping runs inside the transaction (`open-in-view: false`).
- Creation timestamps are set in entity constructors (not `@CreationTimestamp`, which only fills at flush).
- Records are soft-deleted (`deleted_at`) both by `DELETE` and by `pet.deleted`; repositories only read active rows.
- Events: `HealthEvent` records → `EventPublisher.publish(...)` → `RabbitEventRelay` (AFTER_COMMIT).
- Consumer: `messaging/PetEventListener` parses the raw `Message` (no `__TypeId__` dependency); unreadable/invalid
  → `AmqpRejectAndDontRequeueException` (straight to DLQ); other failures retried 3×, then dead-lettered.
  Idempotency: `processed_events` row in the same transaction as the change (`service/PetCopyService`).
- Local pet copy (`pets` table) is written only by native upserts in `PetCopyRepository` (safe against the consumer
  and request threads racing); once deleted it stays deleted. Every write request also refreshes it from pet's answer.
- No `MessageConverter` bean (JSON only via `RabbitTemplateCustomizer`): a bean would also reach the listeners and
  deserialize by the sender's `__TypeId__`, dead-lettering every real event. Consumer tests send that header on purpose.
- DLX: `pawtrack.events.dlx` is a **direct** exchange, routing key = DLQ name (events.md). Declare it the same way everywhere.
- Calls to pet: blocking `RestClient` built with `RestClient.builder()` + `SimpleClientHttpRequestFactory`
  and explicit timeouts (see "Local machine note").
- "Today" is the UTC date (`Clock` bean in `PetClientConfig`).
- Tests extend `support.IntegrationTest` (shared Testcontainers context: Postgres, RabbitMQ). Pet is replaced by
  `support.FakePets` (`pets.create(owner, name)`, `pets.grant(user, petId)`, `pets.setDown(true)`);
  `RestPetClientTest` covers the real HTTP client with `MockRestServiceServer`. `AccessRulesTest` runs the
  401/403/404/503/400 checks for all 15 operations. RabbitMQ 4 rejects transient non-exclusive queues: declare test queues durable.

## Behaviour beyond the contract text (also written into health.yaml)
- Sort orders: vaccinations by `administeredOn` desc, vet visits by `visitDate` desc, medications by `startDate` desc,
  weights by `measuredOn` asc.
- Validation: `administeredOn`/`measuredOn` not in the future, `nextDueDate >= administeredOn`, `endDate >= startDate`,
  weights `from <= to`, `cost <= 1e9`. `visitDate` may be in the future (planned visit).
- `cost` stored with 2 decimals, `weightKg` with 3 (rounded half-up).
- Medication `active` = not finished (endDate today or later / no endDate; ONCE without endDate ends on startDate).
- Summary: per vaccine name (case-insensitive) only the latest administration counts for next due / overdue;
  `lastVetVisit` ignores future visits.
- `PUT` on a vaccination publishes `health.vaccination.recorded` again (same `vaccinationId`, health.yaml 1.2.0);
  notification replaces that vaccination's reminders (and cancels them when `nextDueDate` is gone).
- `pet.deleted` soft-deletes records but sends no `health.medication.cancelled`: notification cancels reminders
  from `pet.deleted` itself (events.md).

## Local machine note
On this Windows machine `java.nio.channels.Pipe.open()` fails ("Unable to establish loopback connection") because AF_UNIX
sockets cannot be created in the user temp folder. Anything that opens an NIO selector or JDK `HttpClient` breaks
(Boot's `RestClient.Builder`, Tomcat at runtime). Tests avoid both. To run the service locally, add
`-Djdk.net.unixdomain.tmpdir=C:\Temp\jdkuds` (any short, writable folder) to the JVM options.

Done = all boxes checked, `mvn verify` green, endpoints match `health.yaml`.
