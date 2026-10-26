// Tipos de la API v1 y utilidades de presentación.

export type StatusCode = 'ok' | 'run' | 'wait' | 'block' | 'err' | 'idle' | 'cancel' | 'queued';

export interface Counts {
  total: number; running: number; waiting: number; blocked: number; errors: number; completed: number; idle: number; cancelled: number;
}
export interface Station {
  id: string; position: number; name: string; description: string; status: StatusCode; paused: boolean;
  note: string | null; limit: string; tools: string[]; counts: Counts;
}
export interface TaskAction { id: string; label: string; confirm: boolean }
export interface Task {
  id: string; stationId: string; promotionId: string | null; type: string; title: string; goal: string;
  status: StatusCode; paused: boolean; progress: number; agentKind: string; agentName: string; why: string | null;
  reason: string | null; error: string | null; result: string | null; human: boolean; attempts: number; maxAttempts: number;
  startedAt: string | null; inputs: string[]; steps: { label: string; state: number }[];
  checks: { label: string; passed: boolean }[]; dependsOn: string[]; events: { at: string; message: string }[];
  actions: TaskAction[]; demo: boolean;
}
export interface Brand { id: string; name: string; handle: string; color: string; demo: boolean }
export interface Promotion {
  id: string; brandId: string; name: string; description: string; scope: string; cadence: string;
  startsOn: string; endsOn: string; status: string; metricsSource: string; goals: string[]; rules: string[];
  humanTodos: string[]; milestones: { startsOn: string; endsOn: string | null; label: string }[];
  resources: { kind: string; location: string; description: string }[];
  automations: { name: string; schedule: string; model: string; runsOn: string; status: StatusCode }[];
  routes: { name: string; status: StatusCode; label: string; notes: string }[];
  settings: { label: string; value: string; origin: string }[]; demo: boolean;
}
export interface Publication {
  id: string; promotionId: string; platform: string; type: string; title: string; date: string; time: string;
  timezone: string; status: string; route: string; flag: string | null; note: string | null; confirmation: string | null; demo: boolean;
}
export interface ArmoryItem {
  id: string; kind: string; name: string; description: string; status: StatusCode; note: string; capabilities: string[]; demo: boolean;
}
export interface Alert { kind: 'err' | 'block'; target: 'task' | 'station'; id: string; stationId: string; title: string; detail: string }
export interface Snapshot {
  now: string; demo: boolean; brands: Brand[]; promotions: Promotion[]; stations: Station[]; tasks: Task[];
  publications: Publication[]; armory: ArmoryItem[]; commonRules: string[]; alerts: Alert[];
}

export const STATUS: Record<StatusCode, { label: string; icon: IconName; cls: string }> = {
  ok: { label: 'OK', icon: 'check', cls: 's-ok' },
  run: { label: 'En progreso', icon: 'run', cls: 's-run' },
  wait: { label: 'En espera', icon: 'clock', cls: 's-wait' },
  queued: { label: 'En cola', icon: 'clock', cls: 's-wait' },
  block: { label: 'Bloqueado', icon: 'lock', cls: 's-block' },
  err: { label: 'Error', icon: 'alert', cls: 's-err' },
  idle: { label: 'Inactivo', icon: 'dash', cls: 's-idle' },
  cancel: { label: 'Cancelado', icon: 'x', cls: 's-cancel' },
};

export const PUBLICATION_STATUS: Record<string, { label: string; s: StatusCode }> = {
  idea: { label: 'Idea', s: 'idle' }, proposal: { label: 'Propuesta', s: 'idle' },
  production: { label: 'En producción', s: 'run' }, draft: { label: 'Borrador', s: 'run' },
  review: { label: 'Pendiente de revisión', s: 'wait' }, approved: { label: 'Aprobada', s: 'ok' },
  scheduled: { label: 'Programada', s: 'wait' }, publishing: { label: 'Publicando', s: 'run' },
  published: { label: 'Publicada', s: 'ok' }, error: { label: 'Error de publicación', s: 'err' },
  cancelled: { label: 'Cancelada', s: 'cancel' },
};

export type IconName = 'check' | 'run' | 'clock' | 'lock' | 'alert' | 'dash' | 'x' | 'local' | 'ai' | 'human' | 'browser'
  | 'plug' | 'back' | 'close' | 'search' | 'minus' | 'plus' | 'fit';

export const AGENT_ICON: Record<string, IconName> = { ai: 'ai', local: 'local', human: 'human', browser: 'browser', conn: 'plug' };

export const agentLabel = (t: { agentKind: string; agentName: string }) =>
  t.agentKind === 'ai' ? `Claude ${t.agentName}` : t.agentName;

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

export const normalize = (s: string) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
