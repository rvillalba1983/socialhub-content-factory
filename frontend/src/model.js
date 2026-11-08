export const STATUS = {
    OK: { label: 'OK', icon: 'check', cls: 's-ok' },
    COMPLETED: { label: 'Completada', icon: 'check', cls: 's-ok' },
    RUNNING: { label: 'En progreso', icon: 'run', cls: 's-run' },
    WAITING: { label: 'En espera', icon: 'clock', cls: 's-wait' },
    QUEUED: { label: 'En cola', icon: 'clock', cls: 's-wait' },
    BLOCKED: { label: 'Bloqueado', icon: 'lock', cls: 's-block' },
    FAILED: { label: 'Error', icon: 'alert', cls: 's-err' },
    IDLE: { label: 'Inactivo', icon: 'dash', cls: 's-idle' },
    CANCELLED: { label: 'Cancelado', icon: 'x', cls: 's-cancel' },
};
export const PUBLICATION_STATUS = {
    IDEA: { label: 'Idea', s: 'IDLE' },
    PROPOSAL: { label: 'Propuesta', s: 'IDLE' },
    IN_PRODUCTION: { label: 'En producción', s: 'RUNNING' },
    DRAFT: { label: 'Borrador', s: 'RUNNING' },
    IN_REVIEW: { label: 'Pendiente de revisión', s: 'WAITING' },
    APPROVED: { label: 'Aprobada', s: 'OK' },
    SCHEDULED: { label: 'Programada', s: 'WAITING' },
    PUBLISHING: { label: 'Publicando', s: 'RUNNING' },
    PUBLISHED: { label: 'Publicada', s: 'OK' },
    PUBLISH_ERROR: { label: 'Error de publicación', s: 'FAILED' },
    CANCELLED: { label: 'Cancelada', s: 'CANCELLED' },
};
/** Transiciones que Raúl puede pedir a mano (las de publicación las pone SocialHub). Igual que el backend. */
export const PUBLICATION_NEXT = {
    IDEA: ['PROPOSAL', 'CANCELLED'],
    PROPOSAL: ['IN_PRODUCTION', 'IDEA', 'CANCELLED'],
    IN_PRODUCTION: ['DRAFT', 'CANCELLED'],
    DRAFT: ['IN_REVIEW', 'IN_PRODUCTION', 'CANCELLED'],
    IN_REVIEW: ['APPROVED', 'DRAFT', 'CANCELLED'],
    APPROVED: ['SCHEDULED', 'IN_REVIEW', 'CANCELLED'],
    SCHEDULED: ['APPROVED', 'CANCELLED'],
    PUBLISHING: [],
    PUBLISHED: [],
    PUBLISH_ERROR: ['SCHEDULED', 'APPROVED', 'CANCELLED'],
    CANCELLED: ['IDEA'],
};
/** Motivos fijos de un rechazo (memoria editorial). */
export const REJECTION_REASONS = [
    ['TONE', 'Tono'], ['REPEATED', 'Repetido'], ['OFF_BRAND', 'Fuera de la marca'], ['WRONG_DATA', 'Error de datos'],
    ['FORMAT', 'Formato'], ['OTHER', 'Otro'],
];
/** Papeles de carpeta, en el orden en que se muestran. */
export const FOLDER_ROLES = [
    ['MATERIAL', 'Material', 'Fotos, audios y vídeos de partida'],
    ['DRAFTS', 'Borradores', 'Piezas a medio hacer'],
    ['GENERATED', 'Generados', 'Lo que crea SocialHub'],
    ['APPROVED', 'Aprobados', 'Listo para programar'],
    ['PUBLISHED', 'Publicados', 'Lo que ya salió'],
    ['REPORTS', 'Informes', 'Balances y métricas'],
    ['ORDERS', 'Encargos', 'spec.json y encargos que vigila SocialHub'],
];
export const ARMORY_KIND = {
    AGENT: 'Agentes', TOOL: 'Herramientas locales', SERVICE: 'Servicios', DEVICE: 'Dispositivos',
};
export const AGENT_ICON = {
    AI: 'ai', LOCAL: 'local', HUMAN: 'human', BROWSER: 'browser', CONNECTOR: 'plug',
};
export const agentLabel = (agent) => (agent.kind === 'AI' ? `Claude ${agent.name}` : agent.name);
export const AI_MODELS = [
    { id: 'HAIKU', label: 'Haiku' }, { id: 'SONNET', label: 'Sonnet' }, { id: 'OPUS', label: 'Opus' },
];
export const statusClass = (s) => (STATUS[s] ?? STATUS.IDLE).cls;
/** Token visual de un aviso (err o block) para las clases CSS. */
export const alertTone = (kind) => (kind === 'FAILED' ? 'err' : 'block');
/** Avisos por prioridad: primero los errores, luego los bloqueos. */
export const byPriority = (alerts) => [...alerts].sort((a, b) => (a.kind === b.kind ? 0 : a.kind === 'FAILED' ? -1 : 1));
/** Estado de una promoción para mostrarlo. */
export const PROMOTION_STATUS = {
    PLANNED: { label: 'Planificada', s: 'IDLE' }, ACTIVE: { label: 'Activa', s: 'OK' },
    PAUSED: { label: 'En pausa', s: 'WAITING' }, FINISHED: { label: 'Terminada', s: 'COMPLETED' },
};
export const isDone = (s) => s === 'DONE';
const DAYS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
/** Fecha ISO (yyyy-mm-dd) como día local, sin desfases de zona horaria. */
export const parseDay = (iso) => {
    const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
    return new Date(Date.UTC(y, m - 1, d));
};
export const isoDay = (d) => d.toISOString().slice(0, 10);
export const addDays = (d, n) => new Date(d.getTime() + n * 864e5);
export const dayLabel = (d) => `${DAYS[d.getUTCDay()]} ${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
export const shortDate = (iso) => { const d = parseDay(iso); return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`; };
export const weekday = (d) => DAYS[d.getUTCDay()];
export const hhmm = (t) => t.slice(0, 5);
export const clockTime = (iso) => new Date(iso).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Madrid' });
export const dateTime = (iso) => new Date(iso).toLocaleString('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'Europe/Madrid' });
export const normalize = (s) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
