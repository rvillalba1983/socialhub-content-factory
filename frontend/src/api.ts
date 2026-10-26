import type { Snapshot } from './model';

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') headers['X-SocialHub-Request'] = '1';
  if (init.body) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, { ...init, method, headers, credentials: 'same-origin' });
  if (!res.ok) {
    let detail = `Error ${res.status}`;
    try { detail = (await res.json()).detail ?? detail; } catch { /* sin cuerpo */ }
    throw new ApiError(res.status, detail);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export interface AuthStatus { needsSetup: boolean; authenticated: boolean; minLength: number }

export const api = {
  status: () => call<AuthStatus>('/api/v1/auth/status'),
  setup: (password: string) => call<void>('/api/v1/auth/setup', { method: 'POST', body: JSON.stringify({ password }) }),
  login: (password: string) => call<void>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ password }) }),
  logout: () => call<void>('/api/v1/auth/logout', { method: 'POST' }),
  factory: () => call<Snapshot>('/api/v1/factory'),
  taskAction: (id: string, action: string, model?: string) =>
    call<void>(`/api/v1/tasks/${encodeURIComponent(id)}/actions/${encodeURIComponent(action)}`,
      { method: 'POST', body: model ? JSON.stringify({ model }) : undefined }),
  stationAction: (id: string, action: 'pause' | 'resume') =>
    call<void>(`/api/v1/stations/${encodeURIComponent(id)}/actions/${action}`, { method: 'POST' }),
};
