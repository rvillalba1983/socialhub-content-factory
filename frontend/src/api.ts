import type {
  AiOrder, AiOrderInput, AiRequestPage, AiSettings, AiSettingsInput, AppMode, AppModeState, Brand, FolderInput, FolderList, Publication, PublicationFile, PublicationFileList, PublicationInput, PublicationStatus, PieceOrder, PieceSpecList, BrandInput, ExternalOperationPage, Promotion, PromotionInput, OperationOutcome, Snapshot, Station, StationCommandType, Task, TaskCommand,
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
const putJson = <T>(path: string, body: unknown) => call<T>(path, { method: 'PUT', body: JSON.stringify(body) });

export const api = {
  factory: () => call<Snapshot>('/api/factory'),
  appMode: () => call<AppModeState>('/api/app-mode'),
  changeAppMode: (mode: AppMode) => putJson<AppModeState>('/api/app-mode', { mode }),
  createBrand: (input: BrandInput) => post<Brand>('/api/brands', input),
  updateBrand: (id: string, input: BrandInput) => putJson<Brand>(`/api/brands/${encodeURIComponent(id)}`, input),
  createPromotion: (input: PromotionInput) => post<Promotion>('/api/promotions', input),
  pieceSpecs: (promotionId: string) => call<PieceSpecList>(`/api/promotions/${encodeURIComponent(promotionId)}/piece-specs`),
  orderPieces: (promotionId: string, specPath: string) =>
    post<PieceOrder>(`/api/promotions/${encodeURIComponent(promotionId)}/piece-orders`, { spec_path: specPath }),
  updatePromotion: (id: string, input: PromotionInput) => putJson<Promotion>(`/api/promotions/${encodeURIComponent(id)}`, input),
  brandFolders: (id: string) => call<FolderList>(`/api/brands/${encodeURIComponent(id)}/folders`),
  saveBrandFolders: (id: string, items: FolderInput[]) => putJson<FolderList>(`/api/brands/${encodeURIComponent(id)}/folders`, { items }),
  promotionFolders: (id: string) => call<FolderList>(`/api/promotions/${encodeURIComponent(id)}/folders`),
  savePromotionFolders: (id: string, items: FolderInput[]) =>
    putJson<FolderList>(`/api/promotions/${encodeURIComponent(id)}/folders`, { items }),
  createPublication: (promotionId: string, input: PublicationInput) =>
    post<Publication>(`/api/promotions/${encodeURIComponent(promotionId)}/publications`, input),
  updatePublication: (id: string, input: PublicationInput) => putJson<Publication>(`/api/publications/${encodeURIComponent(id)}`, input),
  changePublicationStatus: (id: string, status: PublicationStatus) =>
    post<Publication>(`/api/publications/${encodeURIComponent(id)}/status-changes`, { status }),
  publicationFiles: (id: string) => call<PublicationFileList>(`/api/publications/${encodeURIComponent(id)}/files`),
  attachPublicationFile: (id: string, path: string) =>
    post<PublicationFile>(`/api/publications/${encodeURIComponent(id)}/files`, { path }),
  detachPublicationFile: (id: string, path: string) =>
    call<void>(`/api/publications/${encodeURIComponent(id)}/files?${new URLSearchParams({ path })}`, { method: 'DELETE' }),
  aiSettings: () => call<AiSettings>('/api/ai/settings'),
  saveAiSettings: (input: AiSettingsInput) => putJson<AiSettings>('/api/ai/settings', input),
  aiRequests: (offset = 0, limit = 50) => call<AiRequestPage>(`/api/ai/requests?offset=${offset}&limit=${limit}`),
  orderFromClaude: (promotionId: string, input: AiOrderInput) =>
    post<AiOrder>(`/api/promotions/${encodeURIComponent(promotionId)}/ai-orders`, input),
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
