import type {
  ExternalOperationPage, OperationOutcome, Snapshot, Station, StationCommandType, Task, TaskCommand,
} from './model';

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' };
  // Las peticiones que cambian algo llevan la cabecera de protección (otras webs no pueden añadirla)
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

const post = <T>(path: string, body: unknown) => call<T>(path, { method: 'POST', body: JSON.stringify(body) });

export const api = {
  factory: () => call<Snapshot>('/api/factory'),
  taskCommand: (id: string, command: TaskCommand) =>
    post<Task>(`/api/tasks/${encodeURIComponent(id)}/commands`, command),
  stationCommand: (id: string, type: StationCommandType) =>
    post<Station>(`/api/stations/${encodeURIComponent(id)}/commands`, { type }),
  externalOperations: (filter: { system?: string; outcome?: OperationOutcome; offset?: number; limit?: number }) => {
    const q = new URLSearchParams();
    Object.entries(filter).forEach(([k, v]) => { if (v !== undefined && v !== '') q.set(k, String(v)); });
    return call<ExternalOperationPage>(`/api/external-operations?${q}`);
  },
};
