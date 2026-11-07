import { config } from './helpers/config';

/** Fails fast with a clear message when the stack is not running. */
export default async function globalSetup(): Promise<void> {
  const checks = [
    ['gateway', `${config.gatewayUrl}/actuator/health`],
    ['keycloak', `${config.keycloakUrl}/realms/${config.realm}/.well-known/openid-configuration`],
  ] as const;
  for (const [name, url] of checks) {
    try {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (error) {
      throw new Error(
        `${name} is not reachable at ${url} (${String(error)}).\n` +
          'Start the stack first: cd pawtrack-development && docker compose --profile services up -d --build',
      );
    }
  }
}
