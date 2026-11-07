import { config, UserName, users } from './config';

interface CachedToken {
  value: string;
  expiresAt: number;
}

const tokens = new Map<UserName, CachedToken>();

/** Access token for a seeded test user (password grant on the local-only client `pawtrack-e2e`), cached until near expiry. */
export async function accessToken(user: UserName): Promise<string> {
  const cached = tokens.get(user);
  if (cached && cached.expiresAt > Date.now() + 30_000) {
    return cached.value;
  }
  const response = await fetch(`${config.keycloakUrl}/realms/${config.realm}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: config.clientId,
      username: users[user].username,
      password: users[user].password,
    }),
  });
  if (!response.ok) {
    throw new Error(`Keycloak login for ${user} failed: ${response.status} ${await response.text()}`);
  }
  const body = (await response.json()) as { access_token: string; expires_in: number };
  tokens.set(user, { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 });
  return body.access_token;
}
