# pawtrack-notification – agent notes

Reminders (from health events + custom ones) and email (Mailpit locally). Port 8084.

- Contract (source of truth): `../pawtrack-development/contracts/notification.yaml` (1.1.0), events: `../pawtrack-development/contracts/events.md`.
  Do not read other service folders.
- Layout copied from `pawtrack-identity` (reference): same stack, packages
  `com.pawtrack.notification.{config,controller,dto,entity,exception,mapper,messaging,repository,service}`.
- DB schema: `src/main/resources/db/changelog/changes/` – never edit an applied changeSet, add a new file. `ddl-auto: validate`.
- CI: `../.github/workflows/notification.yml` (copy of identity.yml).
- Tests: `mvn verify` (needs Docker: Postgres, RabbitMQ, Mailpit via Testcontainers).

## Status
- [x] Skeleton: security, OpenAPI, ProblemDetail, Liquibase schema, Testcontainers smoke test, Dockerfile, CI
- [x] Local copies from events: recipients (`user.updated`), memberships (`household.member.*`), pets (`pet.*`)
- [x] Reminders API: `GET/POST /api/v1/reminders`, `DELETE /{id}`, `POST /{id}/done` (401/400/403/404/409)
- [x] Consumers with DLQ + idempotency (`processed_events`) for every event notification consumes in events.md
- [x] Vaccination reminders (7 days before + on the day, 09:00 household time; replaced on every update, cancelled without `nextDueDate`)
- [x] Medication reminders (rolling 30 days, hourly top-up), cancelled on `health.medication.cancelled` / `pet.deleted` / `household.deleted`
- [x] Scheduler: every minute emails due reminders to members with `emailNotifications=true`, marks SENT; invitation emails
- [x] Tests for every endpoint and listener

## How it works
- Queues (one per producer, each with `<queue>.dlq` on the direct DLX `pawtrack.events.dlx`):
  `notification.identity-events` (`household.#`, `user.updated`), `notification.pet-events` (`pet.created|updated|deleted`),
  `notification.health-events` (`health.vaccination.recorded`, `health.medication.scheduled|cancelled`).
- `RabbitEventListener` parses the envelope → `EventHandlingService.handle` (one transaction): claims the eventId with
  `INSERT … ON CONFLICT DO NOTHING` (0 rows = duplicate → skip). Failures retry 3× then dead-letter.
- Access: local `household_members` copy. Unknown pet/reminder → 404, other household → 403. No call to identity/pet.
- Household time zone = OWNER's (from `user.updated`), else another member's, else `Europe/Sarajevo`.
- Jobs (`ReminderJobs`): `pawtrack.reminders.dispatch-cron` (every minute) and `medication-top-up-cron` (hourly);
  `-` disables them (tests call `ReminderDispatcher.dispatchDue()` / `MedicationReminderService.topUpAll()` directly).
- Dispatch locks rows with `FOR UPDATE SKIP LOCKED` (safe with several instances). SMTP failure → stays PENDING, retried next run.

## Conventions / gotchas
- Java 17: no pattern-matching `switch`, no `List.reversed()/getFirst()`.
- Entities with an assigned id (pets, schedules, recipients): `save()` merges and returns a **new** managed copy — keep using that.
- Bulk `@Modifying` updates flush + clear the persistence context; re-read entities afterwards.
- Test support: `IntegrationTest.publish(type, data)` sends an event and waits until it is processed;
  `household(users…)` builds memberships + a pet through events; `Mailpit` reads sent mail (HttpURLConnection, not JDK HttpClient).

## Backfill (events.md)
- `messaging/ReplayRequester` publishes `replay.requested` (user.updated, household.member.added, pet.updated) on
  `ApplicationReadyEvent` (`pawtrack.events.replay-on-startup`). Identity and pet answer with snapshots; every
  consumed type is an upsert, so repeated replays change nothing.
- `pet.updated` is bound to `notification.pet-events` and handled like `pet.created` (upsert).

## Known gaps (not in the contract)
- Vaccination DELETE sends no event, so its reminders stay (needs e.g. `health.vaccination.deleted`).
- Reminders keep the household time zone of when they were generated.

Done = all boxes checked, `mvn verify` green, endpoints match `notification.yaml`.
