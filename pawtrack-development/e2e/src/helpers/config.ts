/** Where the stack runs. Defaults match pawtrack-development/docker-compose.yml. */
export const config = {
  gatewayUrl: process.env.GATEWAY_URL ?? 'http://localhost:8080',
  // Node's resolver (unlike browsers and curl) does not map *.localhost to 127.0.0.1. Keycloak's issuer is
  // fixed by KC_HOSTNAME, so tokens fetched via localhost still carry the keycloak.localhost issuer.
  keycloakUrl: process.env.KEYCLOAK_URL ?? 'http://localhost:8180',
  mailpitUrl: process.env.MAILPIT_URL ?? 'http://localhost:8025',
  realm: 'pawtrack',
  clientId: 'pawtrack-e2e',
};

/** Test users from the Keycloak realm seed (docker-mounts/keycloak/pawtrack-realm.json). Local-only values. */
export const users = {
  alice: { username: 'alice@pawtrack.local', password: 'alice' },
  bob: { username: 'bob@pawtrack.local', password: 'bob' },
} as const;

export type UserName = keyof typeof users;
