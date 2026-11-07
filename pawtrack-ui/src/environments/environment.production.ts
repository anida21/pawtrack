// Used by `ng build` (production configuration, replaces environment.ts).
// The docker-compose setup runs everything on localhost, so the values match local development.
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080',
  auth: {
    // Must match KEYCLOAK_ISSUER_URI in docker-compose (token `iss`).
    authority: 'http://keycloak.localhost:8180/realms/pawtrack',
    clientId: 'pawtrack-ui',
  },
};
