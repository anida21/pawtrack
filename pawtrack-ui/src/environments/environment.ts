// Local development (ng serve). Production values: environment.production.ts.
export const environment = {
  production: false,
  /** All API calls go through the gateway; the bearer token is only attached to this origin. */
  apiBaseUrl: 'http://localhost:8080',
  auth: {
    authority: 'http://keycloak.localhost:8180/realms/pawtrack',
    clientId: 'pawtrack-ui',
  },
};
