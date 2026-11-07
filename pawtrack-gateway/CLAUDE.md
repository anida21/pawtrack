# pawtrack-gateway – agent notes

Single entry point (port 8080) for the Angular UI and the E2E tests. Validates the Keycloak JWT, applies CORS
and proxies by path to the services. No business logic, no database. Pom/Dockerfile/CI shape copied from
`../pawtrack-identity` (the reference).

- Path prefixes come from `../pawtrack-development/contracts/*.yaml` and the routing table in
  `../pawtrack-development/docs/ARCHITECTURE.md`. Do not read other service folders. A new contract path prefix
  needs a route in `application.yml` **and** a row in `RoutingTest`.
- Stack: Spring Boot 4.1, Java 17, **Spring Cloud Gateway Server Web MVC** (servlet, not WebFlux, so it matches the
  services and MockMvc works), OAuth2 resource server, springdoc (Swagger UI only), Apache HttpClient 5 for proxying.
- Spring Cloud release: `2025.1.4` (Gateway 5.0.4). The Boot 4.1 line `2026.0.x` was only at RC2 when this was
  written; 2025.1.4 runs on Boot 4.1.2 without compatibility warnings and is fully tested here.
  Bump `spring-cloud.version` to `2026.0.x` once it is GA.
- Packages: `com.pawtrack.gateway.{config,exception}`.
- CI: `../.github/workflows/gateway.yml` (runs only when `pawtrack-gateway/**` changes; pushes the image on `main`).

## Routes (`src/main/resources/application.yml`, order matters)
| order | id | paths | target env (default) |
|---|---|---|---|
| 10 | identity | `/api/v1/me/**`, `/api/v1/households/**`, `/api/v1/invitations/**` | `IDENTITY_URL` (`http://localhost:8081`) |
| 20 | health | `/api/v1/pets/*/{vaccinations,vet-visits,medications,weights,summary}/**` | `HEALTH_URL` (`http://localhost:8083`) |
| 30 | pet | `/api/v1/pets/**` | `PET_URL` (`http://localhost:8082`) |
| 40 | notification | `/api/v1/reminders/**` | `NOTIFICATION_URL` (`http://localhost:8084`) |
| 100 | `<svc>-api-docs` | `/api-docs/<svc>` → `<svc>/v3/api-docs` (public) | same as above |

`/**` also matches the bare prefix (`/api/v1/pets`). Paths are forwarded unchanged (no prefix stripping).

## Security / CORS
- Every route needs a valid JWT (signature, `exp`, issuer = `KEYCLOAK_ISSUER_URI`); otherwise 401 and the request
  never reaches a service. The `Authorization` header is relayed unchanged; services do their own authorization.
- Public: `/actuator/health/**`, `/actuator/info`, `/swagger-ui*`, `/v3/api-docs/**` (Swagger UI config),
  `/api-docs/**` (service docs). `/actuator/prometheus` needs a token.
- CORS (`pawtrack.cors.*`): origins from `CORS_ALLOWED_ORIGINS` (comma-separated, default `http://localhost:4200`),
  methods GET/POST/PUT/PATCH/DELETE/OPTIONS, `Location` exposed, no credentials, max-age 1 h. Preflights are answered
  by the gateway (no token needed); 401s still carry CORS headers.
- Unreachable service → 503 `application/problem+json` (`GlobalExceptionHandler`). Timeouts: connect 2 s, read 30 s.

## Tests
- `mvn verify` (no Docker needed). `support.IntegrationTest`: `@SpringBootTest` + MockMvc; the four services are
  `support.StubBackend`s (JDK `HttpServer` on random ports, records requests) wired through the same env vars
  docker-compose uses. Keycloak is replaced by `support.TestTokens` (RS256 key pair per JVM, real signed JWTs) and
  `TestJwtDecoderConfig`, so tests send real `Authorization` headers and can check the relay.
- Do not use `.with(jwt())` here: it bypasses the bearer-token filter and adds no header to relay.

## Status
- [x] Skeleton: pom (Spring Cloud BOM), application, Dockerfile, CI workflow, `.gitignore`
- [x] Routes for identity, pet, health, notification from env vars with local defaults (health before pet)
- [x] OAuth2 resource server against Keycloak, token relayed unchanged
- [x] Public actuator health + Swagger UI aggregating the services' `/v3/api-docs`
- [x] CORS for the UI, configurable via `CORS_ALLOWED_ORIGINS`
- [x] 503 ProblemDetail when a service is down
- [x] Tests: every contract path routed, relay, query/body/method kept, 404, 401 (none/expired/foreign key/other
      issuer/garbage), CORS preflight allowed/rejected, env overrides, 503

## Local machine note
On this Windows machine NIO pipes fail ("Unable to establish loopback connection", AF_UNIX in %TEMP%), which breaks
Tomcat and the JDK `HttpServer` used by the stubs. The pom has a `windows-loopback` profile (auto-active on Windows
only) that sets `-Djdk.net.unixdomain.tmpdir=${project.build.directory}` for surefire and `spring-boot:run`.
For `java -jar`, pass the flag yourself (any short, writable folder). Not needed on Linux / in Docker / in CI.

Done = all boxes checked, `mvn verify` green, every contract path prefix routed.
