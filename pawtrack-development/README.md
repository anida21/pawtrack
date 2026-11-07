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
3. Or start everything, building the services from the sibling folders:
   ```
   docker compose --profile services up -d --build
   ```
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

`e2e/` – Jest + TypeScript. API clients are generated from `contracts/*.yaml`.
See `e2e/README.md` (coming soon).
