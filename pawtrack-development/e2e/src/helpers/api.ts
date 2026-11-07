import { accessToken } from './auth';
import { config, UserName } from './config';

export interface ApiResponse<T> {
  status: number;
  body: T;
  headers: Headers;
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/**
 * Minimal HTTP client for the gateway. Response bodies are typed with the schemas generated from the
 * contracts (src/proxy), e.g. `api('alice').get<Pet>(...)`. Never throws on an HTTP status: tests assert it.
 */
export function api(user: UserName | null) {
  async function request<T>(method: Method, path: string, body?: unknown): Promise<ApiResponse<T>> {
    const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' };
    if (user) {
      headers.Authorization = `Bearer ${await accessToken(user)}`;
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    const response = await fetch(config.gatewayUrl + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'manual',
    });
    const text = await response.text();
    let parsed: unknown = text;
    if (text && (response.headers.get('content-type') ?? '').includes('json')) {
      parsed = JSON.parse(text);
    }
    return { status: response.status, body: parsed as T, headers: response.headers };
  }

  return {
    get: <T>(path: string) => request<T>('GET', path),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
    put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
    patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
    delete: <T = unknown>(path: string) => request<T>('DELETE', path),
  };
}

/**
 * Polls `probe` until it returns a value (not undefined) or the timeout passes. Used where a result
 * depends on an event travelling through RabbitMQ.
 */
export async function eventually<T>(probe: () => Promise<T | undefined>, timeoutMs = 20_000, what = 'condition'): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  let lastError: unknown;
  while (Date.now() < deadline) {
    try {
      const value = await probe();
      if (value !== undefined) {
        return value;
      }
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Timed out after ${timeoutMs} ms waiting for ${what}${lastError ? `: ${String(lastError)}` : ''}`);
}
