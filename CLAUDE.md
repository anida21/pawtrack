# PawTrack workspace

PawTrack is a pet-care platform (pet profiles, health records, reminders, shared households).
This folder is a **monorepo**: one git repository, one folder per service. Each service still builds,
tests and ships on its own (own `pom.xml`/`package.json`, own Dockerfile, own CI workflow in
`.github/workflows/<service>.yml` filtered by path, own image `ghcr.io/anida21/pawtrack-<service>`).

| Folder | Kind | Port (local) | Host alias |
|---|---|---|---|
| `pawtrack-development` | local env (docker-compose), contracts, E2E tests | – | – |
| `pawtrack-gateway` | Spring Cloud Gateway | 8080 | gateway.pawtrack.local |
| `pawtrack-identity` | Spring Boot – users, households, members | 8081 | identity.pawtrack.local |
| `pawtrack-pet` | Spring Boot – pets, photos | 8082 | pet.pawtrack.local |
| `pawtrack-health` | Spring Boot – vaccinations, vet visits, medications, weight | 8083 | health.pawtrack.local |
| `pawtrack-notification` | Spring Boot – reminders, email | 8084 | notification.pawtrack.local |
| `pawtrack-ui` | Angular | 4200 | localhost |

Infra (from `pawtrack-development/docker-compose.yml`): Postgres 5432, Keycloak 8180
(`keycloak.pawtrack.local`), RabbitMQ 5672/15672, MinIO 9000/9001, Mailpit 1025/8025.

## Rules for agents (read before working)

1. **Work in one service folder at a time.** Do not read other service folders to learn the API —
   the source of truth is `pawtrack-development/contracts/` (OpenAPI + `events.md`).
   If a contract must change, change the contract file first and say so.
2. **Follow `pawtrack-identity` as the reference layout** for every Spring service
   (pom, packages, config, Dockerfile, and CI in `.github/workflows/identity.yml`). Copy, rename, don't redesign.
3. Architecture overview: `pawtrack-development/docs/ARCHITECTURE.md`.
4. Every service folder has its own `CLAUDE.md` with its scope and done-criteria.
5. Never commit secrets. Local-only test values live in `pawtrack-development/.env.example`
   and the Keycloak realm seed.
6. Build/test: `mvn verify` (Spring), `npm test` (UI / E2E), run inside the service folder.
   Tests must pass before commit. Keep commits scoped to one service where possible
   (a contract change plus the service that implements it is fine in one commit).
