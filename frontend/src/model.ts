// Tipos de la API (generados desde contracts/openapi.yaml) y utilidades de presentación.
import type {
  ArmoryItem, Factory, PublicationStatus, StatusLevel, Task, TaskStatus, TaskStepState,
} from './api/types';

export type * from './api/types';

/** Estado visual: el de una estación o recurso (StatusLevel) o el de una tarea (TaskStatus). */
export type StatusCode = StatusLevel | TaskStatus;

/** Vista completa del anillo tal como la devuelve GET /api/factory. */
export type Snapshot = Factory;

export const STATUS: Record<StatusCode, { label: string; icon: IconName; cls: string }> = {
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

export const PUBLICATION_STATUS: Record<PublicationStatus, { label: string; s: StatusCode }> = {
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

export const ARMORY_KIND: Record<ArmoryItem['kind'], string> = {
  AGENT: 'Agentes', TOOL: 'Herramientas locales', SERVICE: 'Servicios', DEVICE: 'Dispositivos',
};

export type IconName = 'check' | 'run' | 'clock' | 'lock' | 'alert' | 'dash' | 'x' | 'local' | 'ai' | 'human' | 'browser'
  | 'plug' | 'back' | 'close' | 'search' | 'minus' | 'plus' | 'fit';

export const AGENT_ICON: Record<Task['agent']['kind'], IconName> = {
  AI: 'ai', LOCAL: 'local', HUMAN: 'human', BROWSER: 'browser', CONNECTOR: 'plug',
};

export const agentLabel = (agent: Task['agent']) => (agent.kind === 'AI' ? `Claude ${agent.name}` : agent.name);

export const AI_MODELS = [
  { id: 'HAIKU', label: 'Haiku' }, { id: 'SONNET', label: 'Sonnet' }, { id: 'OPUS', label: 'Opus' },
] as const;

export const statusClass = (s: StatusCode) => (STATUS[s] ?? STATUS.IDLE).cls;

/** Token visual de un aviso (err o block) para las clases CSS. */
export const alertTone = (kind: 'FAILED' | 'BLOCKED') => (kind === 'FAILED' ? 'err' : 'block');

/** Avisos por prioridad: primero los errores, luego los bloqueos. */
export const byPriority = <T extends { kind: 'FAILED' | 'BLOCKED' }>(alerts: T[]) =>
  [...alerts].sort((a, b) => (a.kind === b.kind ? 0 : a.kind === 'FAILED' ? -1 : 1));

export const isDone = (s: TaskStepState) => s === 'DONE';

const DAYS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** Fecha ISO (yyyy-mm-dd) como día local, sin desfases de zona horaria. */
export const parseDay = (iso: string) => {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
};
export const isoDay = (d: Date) => d.toISOString().slice(0, 10);
export const addDays = (d: Date, n: number) => new Date(d.getTime() + n * 864e5);
export const dayLabel = (d: Date) => `${DAYS[d.getUTCDay()]} ${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
export const shortDate = (iso: string) => { const d = parseDay(iso); return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`; };
export const weekday = (d: Date) => DAYS[d.getUTCDay()];
export const hhmm = (t: string) => t.slice(0, 5);
export const clockTime = (iso: string) =>
  new Date(iso).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Madrid' });
export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'Europe/Madrid' });

export const normalize = (s: string) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
