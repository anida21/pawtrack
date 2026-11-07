#!/usr/bin/env node
// Moves every message from a dead-letter queue back to the pawtrack.events exchange with the routing key it had
// before it was dead-lettered (taken from its x-death header). Consumers are idempotent, so replaying is safe.
//
//   node scripts/replay-dlq.mjs health.pet-events.dlq
//
// Uses the RabbitMQ management API of the local environment (http://localhost:15672, pawtrack/pawtrack).
const dlq = process.argv[2];
if (!dlq) {
  console.error('usage: node scripts/replay-dlq.mjs <queue>.dlq');
  process.exit(1);
}
const api = process.env.RABBITMQ_API ?? 'http://localhost:15672/api';
const auth = 'Basic ' + Buffer.from(process.env.RABBITMQ_AUTH ?? 'pawtrack:pawtrack').toString('base64');

async function call(path, body) {
  const response = await fetch(`${api}${path}`, {
    method: 'POST',
    headers: { authorization: auth, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${path}: ${response.status} ${await response.text()}`);
  }
  return response.json();
}

let replayed = 0;
for (;;) {
  // Take one message off the DLQ. It is acknowledged, so publish failures below stop the script loudly.
  const [message] = await call(`/queues/%2F/${encodeURIComponent(dlq)}/get`,
    { count: 1, ackmode: 'ack_requeue_false', encoding: 'auto' });
  if (!message) {
    break;
  }
  const death = message.properties.headers?.['x-death']?.[0];
  const routingKey = death?.['routing-keys']?.[0];
  if (!routingKey) {
    throw new Error(`No original routing key on message: ${message.payload}`);
  }
  const { headers = {}, ...properties } = message.properties;
  delete headers['x-death'];
  delete headers['x-first-death-exchange'];
  delete headers['x-first-death-queue'];
  delete headers['x-first-death-reason'];
  delete headers['x-last-death-exchange'];
  delete headers['x-last-death-queue'];
  delete headers['x-last-death-reason'];
  const result = await call('/exchanges/%2F/pawtrack.events/publish', {
    properties: { ...properties, headers },
    routing_key: routingKey,
    payload: message.payload,
    payload_encoding: message.payload_encoding,
  });
  if (!result.routed) {
    throw new Error(`Not routed (${routingKey}): ${message.payload}`);
  }
  replayed++;
  console.log(`replayed ${routingKey}`);
}
console.log(`${replayed} message(s) moved from ${dlq} back to pawtrack.events`);
