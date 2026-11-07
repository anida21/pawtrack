# pawtrack-development – agent notes

Scope: docker-compose environment, contracts, E2E tests. No service code here.

- `contracts/*.yaml` and `contracts/events.md` are the **source of truth** for all services.
  Changing them is an API change: update the yaml, then mention which service folders must follow.
- `docker-compose.yml`: infra always on; app services under profile `services`, built from `../pawtrack-<svc>`.
- New DB → add to `docker-mounts/postgres/01_create_databases.sql` (needs `docker compose down -v`).
- Keycloak realm seed: `docker-mounts/keycloak/pawtrack-realm.json` (test users alice/bob, client `pawtrack-e2e` for password grant).

## E2E – `e2e/`
- Jest + ts-jest + TypeScript, `jest-junit` reporter, `@faker-js/faker` builders (`src/helpers/builders.ts`).
- Types: `openapi-typescript` generates `src/proxy/<svc>.ts` from `../contracts/<svc>.yaml` in `pretest`
  (gitignored; same tool as the UI, no Java needed). `src/helpers/api.ts` is a small fetch client typed with
  those schemas; `eventually(...)` polls for results that travel through RabbitMQ.
- Keycloak is called via `http://localhost:8180`: Node does not resolve `*.localhost`, and the issuer is fixed by KC_HOSTNAME.
- `src/helpers/auth.ts` gets tokens from Keycloak with client `pawtrack-e2e`.
- Tests in `src/tests/<svc>/*-test.ts`, run against the gateway (`http://localhost:8080`).
- Done = `npm test` green against `docker compose --profile services up`.
