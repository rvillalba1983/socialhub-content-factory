export class ApiError extends Error {
    status;
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}
async function call(path, init = {}) {
    const method = init.method ?? 'GET';
    const headers = { Accept: 'application/json, application/problem+json' };
    // Las peticiones que cambian algo llevan la cabecera de protección (otras webs no pueden añadirla)
    if (method !== 'GET')
        headers['X-SocialHub-Request'] = '1';
    if (init.body)
        headers['Content-Type'] = 'application/json';
    const res = await fetch(path, { ...init, method, headers, credentials: 'same-origin' });
    if (!res.ok) {
        let detail = `Error ${res.status}`;
        try {
            detail = (await res.json()).detail ?? detail;
        }
        catch { /* sin cuerpo */ }
        throw new ApiError(res.status, detail);
    }
    return res.status === 204 ? undefined : res.json();
}
const post = (path, body) => call(path, { method: 'POST', body: JSON.stringify(body) });
const putJson = (path, body) => call(path, { method: 'PUT', body: JSON.stringify(body) });
export const api = {
    factory: () => call('/api/factory'),
    appMode: () => call('/api/app-mode'),
    changeAppMode: (mode) => putJson('/api/app-mode', { mode }),
    createBrand: (input) => post('/api/brands', input),
    updateBrand: (id, input) => putJson(`/api/brands/${encodeURIComponent(id)}`, input),
    createPromotion: (input) => post('/api/promotions', input),
    pieceSpecs: (promotionId) => call(`/api/promotions/${encodeURIComponent(promotionId)}/piece-specs`),
    orderPieces: (promotionId, specPath) => post(`/api/promotions/${encodeURIComponent(promotionId)}/piece-orders`, { spec_path: specPath }),
    updatePromotion: (id, input) => putJson(`/api/promotions/${encodeURIComponent(id)}`, input),
    brandFolders: (id) => call(`/api/brands/${encodeURIComponent(id)}/folders`),
    saveBrandFolders: (id, items) => putJson(`/api/brands/${encodeURIComponent(id)}/folders`, { items }),
    promotionFolders: (id) => call(`/api/promotions/${encodeURIComponent(id)}/folders`),
    savePromotionFolders: (id, items) => putJson(`/api/promotions/${encodeURIComponent(id)}/folders`, { items }),
    createPublication: (promotionId, input) => post(`/api/promotions/${encodeURIComponent(promotionId)}/publications`, input),
    updatePublication: (id, input) => putJson(`/api/publications/${encodeURIComponent(id)}`, input),
    changePublicationStatus: (id, status, reason, note) => post(`/api/publications/${encodeURIComponent(id)}/status-changes`, { status, reason, note }),
    rejectPublicationText: (id, reason, note) => post(`/api/publications/${encodeURIComponent(id)}/text-rejections`, { reason, note }),
    editorialMemory: (brandId) => call(`/api/brands/${encodeURIComponent(brandId)}/editorial-memory`),
    addDecision: (brandId, text) => post(`/api/brands/${encodeURIComponent(brandId)}/decisions`, { text }),
    deleteDecision: (brandId, id) => call(`/api/brands/${encodeURIComponent(brandId)}/decisions/${id}`, { method: 'DELETE' }),
    publicationFiles: (id) => call(`/api/publications/${encodeURIComponent(id)}/files`),
    attachPublicationFile: (id, path) => post(`/api/publications/${encodeURIComponent(id)}/files`, { path }),
    detachPublicationFile: (id, path) => call(`/api/publications/${encodeURIComponent(id)}/files?${new URLSearchParams({ path })}`, { method: 'DELETE' }),
    aiSettings: () => call('/api/ai/settings'),
    saveAiSettings: (input) => putJson('/api/ai/settings', input),
    aiRequests: (offset = 0, limit = 50) => call(`/api/ai/requests?offset=${offset}&limit=${limit}`),
    orderFromClaude: (promotionId, input) => post(`/api/promotions/${encodeURIComponent(promotionId)}/ai-orders`, input),
    brandIdentity: (id) => call(`/api/brands/${encodeURIComponent(id)}/identity`),
    saveBrandIdentity: (id, identity) => putJson(`/api/brands/${encodeURIComponent(id)}/identity`, identity),
    publicationQuality: (id) => call(`/api/publications/${encodeURIComponent(id)}/quality`),
    externalCalendar: (promotionId, from, to) => call(`/api/promotions/${encodeURIComponent(promotionId)}/external-calendar?from=${from}&to=${to}`),
    publora: () => call('/api/publora'),
    savePubloraKey: (key) => putJson('/api/publora/key', { key }),
    deletePubloraKey: () => call('/api/publora/key', { method: 'DELETE' }),
    brandPubloraAccounts: (id) => call(`/api/brands/${encodeURIComponent(id)}/publora-accounts`),
    saveBrandPubloraAccounts: (id, items) => putJson(`/api/brands/${encodeURIComponent(id)}/publora-accounts`, { items }),
    publicationMetrics: (id) => call(`/api/publications/${encodeURIComponent(id)}/metrics`),
    publishJob: (id) => call(`/api/publications/${encodeURIComponent(id)}/publish-job`),
    clearPublicationFlag: (id) => call(`/api/publications/${encodeURIComponent(id)}/flag`, { method: 'DELETE' }),
    acceptQualityIssue: (id, key, reason) => post(`/api/publications/${encodeURIComponent(id)}/quality-acceptances`, { key, reason }),
    taskCommand: (id, command) => post(`/api/tasks/${encodeURIComponent(id)}/commands`, command),
    stationCommand: (id, type) => post(`/api/stations/${encodeURIComponent(id)}/commands`, { type }),
    shutdown: () => call('/api/app/shutdown', { method: 'POST' }),
    backups: () => call('/api/backups'),
    createBackup: () => call('/api/backups', { method: 'POST' }),
    saveBackupFolder: (folder) => putJson('/api/backups/folder', { folder }),
    restoreBackup: (name, confirmation) => post('/api/backups/restorations', { name, confirmation }),
    externalOperations: (filter) => {
        const q = new URLSearchParams();
        Object.entries(filter).forEach(([k, v]) => { if (v !== undefined && v !== '')
            q.set(k, String(v)); });
        return call(`/api/external-operations?${q}`);
    },
};
