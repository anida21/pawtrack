# pawtrack-development

Local development environment, API contracts and end-to-end tests for **PawTrack** –
a microservice platform for pet care (pet profiles, health records, reminders, shared households).

| Folder | Description |
|---|---|
| [pawtrack-identity](../pawtrack-identity) | Users, households, invitations |
| pawtrack-pet | Pet profiles and photos |
| pawtrack-health | Vaccinations, vet visits, medications, weight |
| pawtrack-notification | Reminders and email |
| pawtrack-gateway | API gateway |
| pawtrack-ui | Angular frontend |

Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · Contracts: [contracts/](contracts)

## Prerequisites

- Docker (Docker Desktop or Rancher Desktop)
- JDK 17+, Maven 3.9+ (services), Node 20+ (UI, E2E)
- The PawTrack monorepo cloned; every service is a sibling folder of this one:
  ```
  PawTrack/
    pawtrack-development/
    pawtrack-identity/
    pawtrack-pet/ ...
  ```

## Setup

1. Add host aliases (once, as Administrator):
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   .\scripts\setup-hosts.ps1
   ```
   Linux/macOS: `./scripts/setup-hosts.sh`
2. Start infrastructure only (run services from your IDE):
   ```
   docker compose up -d
   ```
3. Or start everything, building the services and the UI from the sibling folders, then open
   http://localhost:4200:
   ```
   docker compose --profile services up -d --build
   ```
   (If you develop the UI with `npm start`, which also uses port 4200, start the backend only:
   `docker compose --profile services up -d --build identity pet health notification gateway`.)
4. Stop: `docker compose --profile services down` (add `-v` to wipe databases).

| What | URL | Login (local only) |
|---|---|---|
| Keycloak | http://keycloak.localhost:8180 | admin / admin |
| Test users | realm `pawtrack` | alice@pawtrack.local / alice, bob@pawtrack.local / bob |
| RabbitMQ | http://localhost:15672 | pawtrack / pawtrack |
| MinIO | http://localhost:9001 | pawtrack / pawtrack123 |
| Mailpit | http://localhost:8025 | – |
| Gateway | http://localhost:8080 | Bearer token |

> Postgres init scripts run only on an empty volume. After changing
> `docker-mounts/postgres/*.sql`, run `docker compose down -v`.

## Get a token (for curl / Postman)

```bash
curl -s -X POST http://keycloak.localhost:8180/realms/pawtrack/protocol/openid-connect/token \
  -d grant_type=password -d client_id=pawtrack-e2e \
  -d username=alice@pawtrack.local -d password=alice
```

## E2E tests

`e2e/` – Jest + TypeScript API tests that run through the gateway against the running stack, as the seeded
users alice and bob. Request/response types are generated from `contracts/*.yaml` before every run, so a
contract change that breaks a flow fails at compile time.

```bash
docker compose --profile services up -d --build
cd e2e
npm install
npm test          # writes junit.xml
```

| Suite | Covers |
|---|---|
| `tests/identity` | profile provisioning, 401, households, invitations (wrong user 403, reuse 410), last owner 409 |
| `tests/pet` | CRUD, filters, 400 problems, access by household and role, removed member loses access at once, `household.deleted` → pets deleted |
| `tests/health` | vaccinations, weights, medications, summary, validation, access checked through pet |
| `tests/notification` | vaccination → reminders via RabbitMQ (and their cancellation), shared reminders, done/cancel 409, reminder email in Mailpit |

Override the URLs with `GATEWAY_URL`, `KEYCLOAK_URL` and `MAILPIT_URL`. Every test creates its own
households and deletes them afterwards.
