# pawtrack-ui

The PawTrack web app: pet profiles and photos, shared households with invitations, and (soon) health
records and reminders. Angular 21 + Angular Material 3, signed in through Keycloak.

## Run locally

Prerequisites: Node 20.19+ (CI uses 22), the PawTrack stack from `../pawtrack-development`
(`docker compose --profile services up`). Keycloak is reached at `keycloak.localhost:8180`, which resolves
to 127.0.0.1 without a hosts entry.

```bash
npm ci
npm start          # http://localhost:4200 – sign in as alice@pawtrack.local or bob@pawtrack.local
```

| Script | What it does |
|---|---|
| `npm start` | dev server on port 4200 |
| `npm test` | unit tests once, headless (Vitest + jsdom via `ng test`) |
| `npm run lint` | angular-eslint (TS + templates incl. accessibility rules) |
| `npm run build` | production build to `dist/pawtrack-ui/browser` |
| `npm run generate:api` | regenerate API types from the contracts (runs automatically before the above) |

Gateway URL and Keycloak settings: `src/environments/environment*.ts`.

## How it fits together

```
Browser ──code + PKCE──► Keycloak (realm pawtrack, public client pawtrack-ui)
   │  Authorization: Bearer <access token>   (only for requests to the gateway)
   ▼
gateway :8080 ──► identity (/me, /households, /invitations) · pet (/pets)
```

- **Contract-first.** Types come from `../pawtrack-development/contracts/*.yaml` via
  [openapi-typescript](https://openapi-ts.dev) into a gitignored folder; the services on top are small
  hand-written `HttpClient` wrappers. If a contract changes incompatibly, the build fails.
- **Errors** are RFC 7807 ProblemDetails. `toApiError` maps them to a friendly message per status, with
  per-screen wording (e.g. 410 on an invitation link: "expired or already used").
- **Roles.** Owners manage the household and invitations, members manage pets, pet-sitters can only look.
- **Photos** are served by the pet service as a redirect to a short-lived MinIO URL; the app downloads them
  with the token and caches them per pet version.

## Docker

```bash
docker build --build-context contracts=../pawtrack-development/contracts -t pawtrack-ui .
docker run -p 4200:4200 pawtrack-ui
```

nginx (unprivileged) serves the SPA on port 4200 with history-API fallback; `/healthz` for health checks.
CI (`.github/workflows/ui.yml`) runs lint, tests and the build on every change and pushes
`ghcr.io/<owner>/pawtrack-ui` from `main`.
