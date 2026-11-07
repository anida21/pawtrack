# pawtrack-pet – agent notes

Pets of a household and their profile photos. Layout copied from `../pawtrack-identity` (the reference); keep it in sync.

- Contract (source of truth): `../pawtrack-development/contracts/pet.yaml` (1.1.0), events: `../pawtrack-development/contracts/events.md`.
  Do not read other service folders.
- Stack: Spring Boot 4.1, Java 17, Postgres + Liquibase, MapStruct + Lombok, springdoc, RabbitMQ, MinIO (S3), OAuth2 resource server (Keycloak).
- Packages: `com.pawtrack.pet.{config,controller,dto,entity,exception,mapper,messaging,repository,service}`.
- DB schema: `src/main/resources/db/changelog/changes/` – never edit an applied changeSet, add a new file.
- `ddl-auto: validate` – entities must match Liquibase exactly.
- Current user: `@AuthenticationPrincipal Jwt jwt` → `UUID.fromString(jwt.getSubject())`.
- Access: `service.HouseholdAccess` (`requireRead` / `requireWrite`) asks identity `GET /api/v1/me/households`
  through `config.IdentityClient`, forwarding the caller's token. `RestIdentityClient` caches per user for 60 s
  (`pawtrack.identity.membership-cache-ttl`). SITTER = read only.
- Errors: throw `NotFoundException / ForbiddenException / PhotoTooLargeException / UnsupportedPhotoTypeException /
  DependencyUnavailableException` → ProblemDetail via `GlobalExceptionHandler`.
- Events: publish envelope `{eventId,type,occurredAt,producer:"pawtrack-pet",data}` to exchange `pawtrack.events`,
  routing key = type, **after commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`).
- CI: `../.github/workflows/pet.yml` (runs only when `pawtrack-pet/**` changes; pushes the image on `main`).
- Tests: `mvn verify` (needs Docker). Controller tests: `@SpringBootTest` + `MockMvc` + `.with(jwt().jwt(j -> j.subject(...)))`.

## Status
- [x] Skeleton: security, OpenAPI, ProblemDetail, Liquibase schema, Testcontainers smoke test, Dockerfile, CI
- [x] Entity + repositories + MapStruct mapper (`ageInMonths`, `hasPhoto` computed)
- [x] Household access checks via identity (token forwarded, 60 s cache, 503 if identity is unreachable)
- [x] `GET/POST /api/v1/pets` (filters, paging, sorted by name)
- [x] `GET/PUT/DELETE /api/v1/pets/{petId}` (soft delete)
- [x] `PUT/GET /api/v1/pets/{petId}/photo` (MinIO bucket `pet-photos`, 5 MB, jpeg/png/webp, 302 to 10-min pre-signed URL)
- [x] Event publishing (`pet.created`, `pet.deleted`) after commit
- [x] `household.deleted` consumer: queue `pet.household-events` + DLQ, idempotent (`processed_events`), soft-deletes pets
- [x] Tests for every endpoint incl. 400/401/403/404/413/415/503, consumer, identity client

## Conventions established here (copy them)
- Services take the `Jwt` and return DTOs, so mapping runs inside the transaction (`open-in-view: false`).
- Creation timestamps are set in entity constructors (not `@CreationTimestamp`, which only fills at flush).
- Events: `PetEvent` records → `EventPublisher.publish(...)` → `RabbitEventRelay` (AFTER_COMMIT).
- Consumers: listener takes the raw `Message` and parses the envelope itself (no dependency on the producer's
  `__TypeId__` header); unreadable → `AmqpRejectAndDontRequeueException` (straight to DLQ); other failures are
  retried 3× (`spring.rabbitmq.listener.simple.retry`), then dead-lettered (`default-requeue-rejected: false`).
  Idempotency: insert the `eventId` into `processed_events` in the same transaction as the change.
- DLX: `pawtrack.events.dlx` is a **direct** exchange, routing key = DLQ name (events.md). Declare it the same way everywhere.
- Calls to other services: blocking `RestClient` built with `RestClient.builder()` + `SimpleClientHttpRequestFactory`
  and explicit timeouts (see "Local machine note" below for why not Boot's builder / JDK HttpClient).
- Tests extend `support.IntegrationTest` (shared Testcontainers context: Postgres, RabbitMQ, MinIO).
  Identity is replaced by `support.FakeIdentity` (`identity.grant(user, householdId, role)`), uncached;
  `RestIdentityClientTest` covers the real HTTP client with `MockRestServiceServer`.
  RabbitMQ 4 rejects transient non-exclusive queues: declare test queues durable.
- MinIO Testcontainer uses `cgr.dev/chainguard/minio:latest` (run as root) because `minio/minio` is no longer on Docker Hub.

## Behaviour beyond the contract text
- `pet.deleted` is also published for each pet soft-deleted by `household.deleted` (documented in events.md).
- `GET /pets?householdId=<not a member>` → empty page (not 403).
- `PUT` changing `householdId` moves the pet; needs OWNER/MEMBER in both households. No event is published for
  updates (events.md has no `pet.updated`), so health/notification keep the old name/household until a contract adds one.
- `birthDate` must not be in the future (400). `name` must not be blank.
- Photo object key is `pets/{petId}` (one per pet, overwritten on upload); a soft-deleted pet keeps its object.
- Photo format comes from the file's leading bytes, not the part's Content-Type.

## Local machine note
On this Windows machine `java.nio.channels.Pipe.open()` fails ("Unable to establish loopback connection") because AF_UNIX
sockets cannot be created in the user temp folder. Anything that opens an NIO selector or JDK `HttpClient` breaks
(Boot's `RestClient.Builder`, Tomcat at runtime). Tests avoid both. To run the service locally, add
`-Djdk.net.unixdomain.tmpdir=C:\Temp\jdkuds` (any short, writable folder) to the JVM options.

Done = all boxes checked, `mvn verify` green, endpoints match `pet.yaml`.
