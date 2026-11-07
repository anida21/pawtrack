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
| `pet.updated` | pet | `petId, householdId, name, species` | health, notification: upsert the local `petId → householdId, name` copy. Sent on every `PUT /pets/{petId}`; `householdId` is the new one if the pet was moved |
| `pet.deleted` | pet | `petId, householdId` | health (soft-delete records), notification (cancel reminders). Also sent for every pet that is soft-deleted because its household was deleted (`household.deleted`) |
| `health.vaccination.recorded` | health | `vaccinationId, petId, householdId, vaccine, administeredOn, nextDueDate?` | notification: if `nextDueDate`, reminders 7 days before and on the day, 09:00 user tz. Sent on create **and on every update** (same `vaccinationId`): replace this vaccination's pending reminders, and cancel them when `nextDueDate` is missing |
| `health.medication.scheduled` | health | `medicationId, petId, householdId, name, dosage, startDate, endDate?, frequency, timeOfDay?` | notification: replace future reminders of this medication (max 30 days ahead, rolling) |
| `health.medication.cancelled` | health | `medicationId, petId` | notification: cancel pending reminders |

| `replay.requested` | notification, health (on startup) | `requestedBy, types[]` | identity, pet: republish the **current state** for the requested types they produce (see Backfill) |

Health and notification keep a small local copy (`petId → householdId, name`) from pet events,
so they never have to call pet just to know the household.

## Backfill (`replay.requested`)

A consumer that starts after events were published (a new service, a wiped database) would never learn
about older users, households or pets. So on every startup it publishes `replay.requested`, and the producers
answer by republishing a **snapshot** of their current state with fresh `eventId`s:

| requested type | answered by | republishes |
|---|---|---|
| `user.updated` | identity (queue `identity.replay-requests`) | one `user.updated` per user |
| `household.member.added` | identity | one `household.member.added` per current membership |
| `pet.updated` | pet (queue `pet.replay-requests`) | one `pet.updated` per pet that is not deleted |

- Requesters: notification asks for all three types, health for `pet.updated`.
- Every consumer must treat these types as **upserts**, which they already are for live events.
- Snapshots only add or update. A removal that happened while the consumer was offline (member removed, pet
  deleted) is still delivered from the consumer's durable queue, as long as that queue existed at the time.
- The request queues are durable, so a request waits until identity / pet are up. Only on the very first
  rollout of a producer, before it has ever declared its request queue, is the request unroutable and lost;
  the next startup of the requester asks again.
