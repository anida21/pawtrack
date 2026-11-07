# pawtrack-ui – agent notes

Angular frontend. Talks **only** to the gateway (`environment.apiBaseUrl`, default `http://localhost:8080`),
logs in against Keycloak realm `pawtrack` with the public client `pawtrack-ui` (code flow + PKCE).

- Contracts (source of truth): `../pawtrack-development/contracts/*.yaml`. Do not read service source code.
- Stack: Angular 21 (standalone, signals, zoneless, OnPush), strict TS + strict templates, Angular Material 3,
  `angular-auth-oidc-client`, Vitest via `ng test` (jsdom), angular-eslint.
  Angular 22 needs Node ≥ 22.22; the dev machine runs Node 20.19, so we stay on 21 until Node is upgraded.
- API layer: **types generated, services hand-written.** `scripts/generate-api.mjs` (openapi-typescript) writes
  `src/app/core/api/generated/*.ts` (gitignored) before `start`/`build`/`test`/`lint`. `core/api/models.ts`
  aliases them; `core/api/*.service.ts` are thin typed `HttpClient` wrappers. A contract change that breaks
  the UI fails `npm run build`. To add a service: add its name to `contracts` in the script, alias the types.
- Errors: `toApiError(err, { 409: '…' })` turns any `HttpErrorResponse` + ProblemDetail into
  `{status,title,message}`; pages show load errors with `<app-error-state>`, action errors via `NotifyService`.
  A 401 from the gateway triggers a new login (`unauthorizedInterceptor`).
- State: `SessionStore` (user + memberships, roles). Pages keep their own signals; filters/paging of the pet
  list live in query params (component input binding).
- Roles: `canWrite(role)` – OWNER/MEMBER write pets, SITTER read-only. OWNER-only actions are hidden for others,
  the server is still the authority (403 is handled). **Health records are writable by everyone who can read
  the pet, SITTER included** (health.yaml says so) – change the contract first if that should differ.
- Health: `features/health/` – `HealthSection` (embedded in the pet page), one dialog per record type,
  `WeightChart` (hand-drawn SVG, crosshair + tooltip, the table next to it is the text alternative).
- Photos: `GET /pets/{id}/photo` is a 302 to MinIO; fetched as blob with the bearer token
  (`PetPhotoCache`, keyed by `updatedAt`). Verified against the local stack (MinIO answers CORS).
- Local Keycloak runs at `keycloak.localhost:8180` (no hosts entry needed).
- Layout: `core/` (api, auth, errors, stores), `shared/` (dialogs, error state, pet photo, species),
  `layout/shell`, `features/<area>/*.page.ts`. Test helpers in `src/testing/fixtures.ts`.
- Build/test: `npm test` (headless, once), `npm run lint`, `npm run build`. CI: `../.github/workflows/ui.yml`.
- Docker: nginx on port 4200, contracts passed as named build context (see Dockerfile header).

## Status
- [x] Skeleton: Angular 21, Material 3 theme (light/dark), responsive shell, ESLint, Vitest, Dockerfile, CI
- [x] Keycloak login (code + PKCE, refresh tokens), bearer only to the gateway, 401 → re-login
- [x] API types generated from contracts (identity, pet, health, notification) + typed services
- [x] ProblemDetail error handling (403/404/409/410/413/415/503, offline)
- [x] Profile (GET/PATCH /me: name, time zone, email notifications)
- [x] Households: list, create, detail with members, rename, delete, invite (link + copy), remove member / leave
- [x] Accept invitation link `/invite/:code` (works when not signed in yet)
- [x] Pets: list with household/species filters + paging, create, edit, delete
- [x] Pet photo upload (client pre-check) and display
- [x] Unit tests for services, error mapping, interceptor, store and key pages
- [x] Health on the pet page: summary tiles, vaccinations (due/overdue per vaccine), vet visits, medications
      (active filter), weight chart (3m/1y/all) + table; add/edit/delete dialogs (weights: add only, per contract)
- [x] Browser check of pets + health against the running stack (desktop and phone width)
- [x] Reminders screen `/reminders`: open (PENDING+SENT, grouped overdue/today/7 days/later), done, cancelled;
      pet filter in the URL, custom reminder dialog, mark done / cancel (409 handled); link from the pet page
- [ ] Browser check of reminders with the notification container running (only the 503 path was checked)
- [x] Browser check of households, invitations (wrong email 403, accept after login redirect, reused link 410)
      and photo upload/display (302 → MinIO works with CORS) against the running stack

Done = all boxes checked, `npm test`, `npm run lint` and `npm run build` green.
