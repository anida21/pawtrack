# PawTrack events (RabbitMQ)

- Exchange: `pawtrack.events` (type **topic**, durable)
- Routing key = event `type`
- Each consumer owns its queue: `<service>.<purpose>` (e.g. `notification.health-events`)
  with a dead-letter queue `<queue>.dlq` (exchange `pawtrack.events.dlx`).
  `pawtrack.events.dlx` is a durable **direct** exchange; each queue is declared with
  `x-dead-letter-exchange=pawtrack.events.dlx` and `x-dead-letter-routing-key=<queue>.dlq`, and its DLQ
  is bound with that key. (Every service must declare the DLX with the same type, or RabbitMQ refuses it.)
- Payload: JSON, UTF-8, `contentType=application/json`.
- Consumers must be **idempotent** (store processed `eventId`s or use upserts).

## Envelope

```json
{
  "eventId": "uuid",
  "type": "pet.created",
  "occurredAt": "2026-11-07T10:15:30Z",
  "producer": "pawtrack-pet",
  "data": { }
}
```

## Events

| type | producer | data | consumers |
|---|---|---|---|
| `household.member.added` | identity | `householdId, userId, role` | notification. Sent on invitation accept, and also for the OWNER when a household is created (incl. the default "My pets" at provisioning) |
| `household.member.removed` | identity | `householdId, userId` | notification |
| `household.deleted` | identity | `householdId` | pet (soft-delete pets), notification |
| `household.invitation.created` | identity | `householdId, householdName, email, code, invitedBy, expiresAt` | notification (sends invite email) |
| `user.updated` | identity | `userId, email, displayName, timezone, emailNotifications` | notification (keeps recipient copy). Sent on `PATCH /me` and once when the user is first provisioned, so treat it as an upsert |
| `pet.created` | pet | `petId, householdId, name, species` | notification, health |
| `pet.deleted` | pet | `petId, householdId` | health (soft-delete records), notification (cancel reminders). Also sent for every pet that is soft-deleted because its household was deleted (`household.deleted`) |
| `health.vaccination.recorded` | health | `vaccinationId, petId, householdId, vaccine, administeredOn, nextDueDate?` | notification: if `nextDueDate`, reminders 7 days before and on the day, 09:00 user tz. Sent on create **and on every update** (same `vaccinationId`): replace this vaccination's pending reminders, and cancel them when `nextDueDate` is missing |
| `health.medication.scheduled` | health | `medicationId, petId, householdId, name, dosage, startDate, endDate?, frequency, timeOfDay?` | notification: replace future reminders of this medication (max 30 days ahead, rolling) |
| `health.medication.cancelled` | health | `medicationId, petId` | notification: cancel pending reminders |

Health and notification keep a small local copy (`petId → householdId, name`) from pet events,
so they never have to call pet just to know the household.
