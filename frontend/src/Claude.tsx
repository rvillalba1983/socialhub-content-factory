import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import {
  AI_MODELS, dateTime, type AiModel, type AiRequest, type AiRequestPage, type AiRequestType, type AiSettings,
  type Snapshot, type StatusCode,
} from './model';
import { Pill } from './ui';

const PAGE = 50;

const INPUT_HINT: Record<AiRequestType, string> = {
  WEEKLY_SPEC: 'Opcional: un día de la semana (2026-11-09). Sin fecha, la semana que viene. Sale solo cada domingo.',
  CAPTIONS: 'Los identificadores de las publicaciones (por ejemplo, 2026-11-10-instagram). También desde cada publicación.',
  WEEKLY_BALANCE: 'Opcional: un día de la semana. Sin fecha, la semana pasada. Sale solo cada lunes.',
  RESEARCH: 'Lo que Claude deba tener en cuenta: lugares, nombres, enlaces…',
  QUALITY_REVIEW: 'El identificador de una publicación. También desde la propia publicación.',
  WEEKLY_PROPOSALS: 'Opcional: un día de la semana. Sin fecha, la que viene. Sale solo los lunes a las 18:00.',
  PUBLER_SCHEDULE: 'Lo encarga SocialHub al pasar una publicación a «Programada».',
  PUBLISH_CHECK: 'Lo encarga SocialHub pasada la hora de publicación.',
  CALENDAR_READ: 'Lo encarga SocialHub cada día, desde las 7:00.',
  METRICS_READ: 'Lo encarga SocialHub a los 7 días de cada publicación.',
};

const REQUEST_STATUS: Record<AiRequest['status'], { label: string; s: StatusCode }> = {
  SENT: { label: 'Esperando a Claude', s: 'WAITING' },
  COMPLETED: { label: 'Completado', s: 'OK' },
  FAILED: { label: 'Error', s: 'FAILED' },
  NEEDS_HUMAN: { label: 'Te pregunta algo', s: 'WAITING' },
  INVALID: { label: 'Resultado no válido', s: 'FAILED' },
};

const duration = (ms?: number | null) => {
  if (ms == null) return '—';
  const s = Math.round(ms / 1000);
  return s < 60 ? `${s} s` : `${Math.floor(s / 60)} min ${s % 60} s`;
};

/**
 * Encargos a Claude: ajustes (límite diario y modelo de cada tipo), alta de un encargo y registro de envíos.
 * SocialHub no llama a la API de Claude: deja cada encargo en la carpeta de «Encargos» de la marca.
 */
export function Claude({ data, onClose, onTask, onMessage }: {
  data: Snapshot; onClose: () => void; onTask: (id: string) => void; onMessage: (m: string) => void;
}) {
  const [settings, setSettings] = useState<AiSettings | null>(null);
  const [page, setPage] = useState<AiRequestPage | null>(null);
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [s, p] = await Promise.all([api.aiSettings(), api.aiRequests(offset, PAGE)]);
      setSettings(s);
      setPage(p);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [offset]);

  useEffect(() => { void load(); }, [load]);
  const labels = new Map(settings?.models.map(m => [m.type, m.label]) ?? []);

  return (
    <section className="cc" aria-label="Encargos a Claude">
      <div className="cc-in">
        <div><button className="btn back" onClick={onClose}><Icon name="back" />Volver al mapa</button></div>
        <div className="cc-head">
          <div>
            <div className="eyebrow">Orquestador de IA</div>
            <h1>Encargos a Claude</h1>
            <p>SocialHub deja cada encargo en «Encargos/para-claude» de la marca. La tarea programada «Encargos de SocialHub» lo hace y deja el resultado en «de-claude».</p>
          </div>
          <button className="btn" onClick={load}>Actualizar</button>
        </div>
        {error && <p className="error" role="alert">{error}</p>}

        <div className="grid2">
          {settings && <SettingsCard settings={settings} onSaved={s => { setSettings(s); onMessage('Ajustes de Claude guardados'); }} />}
          {settings && <OrderCard data={data} settings={settings} onOrdered={id => { onMessage('Encargo en cola'); void load(); onTask(id); }} />}
        </div>

        <h3>Registro de envíos</h3>
        <div className="tbl-wrap"><table>
          <thead><tr><th>Enviado</th><th>Tipo</th><th>Modelo</th><th>Intento</th><th>Estado</th><th>Duración</th><th>Tokens</th><th>Coste</th><th>Detalle</th></tr></thead>
          <tbody>
            {page?.items.map(r => {
              const st = REQUEST_STATUS[r.status];
              return (
                <tr key={r.id}>
                  <td className="nowrap mono">{dateTime(r.sent_at)}</td>
                  <td>{labels.get(r.type) ?? r.type}</td>
                  <td>Claude {AI_MODELS.find(m => m.id === r.model)?.label}</td>
                  <td>{r.attempt}</td>
                  <td><Pill s={st.s} label={st.label} /></td>
                  <td className="nowrap">{duration(r.duration_ms)}</td>
                  <td className="src">desconocido</td>
                  <td className="src">desconocido</td>
                  <td>
                    {r.error && <span className="error-text">{r.error} </span>}
                    <button className="btn" style={{ padding: '3px 8px' }} onClick={() => onTask(r.task_id)}>Ver tarea</button>
                  </td>
                </tr>
              );
            })}
            {page && page.items.length === 0 && <tr><td colSpan={9}>Todavía no se ha enviado ningún encargo.</td></tr>}
          </tbody>
        </table></div>
        <p className="src">Tokens y coste figuran como desconocidos: Claude Pro no los da. El límite diario cuenta los envíos desde las 0:00 (hora de España).</p>
        {page && page.total > PAGE && (
          <div className="cal-nav">
            <button className="btn" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE))}>Anteriores</button>
            <span className="src">{offset + 1}–{Math.min(offset + PAGE, page.total)} de {page.total}</span>
            <button className="btn" disabled={offset + PAGE >= page.total} onClick={() => setOffset(offset + PAGE)}>Siguientes</button>
          </div>
        )}
      </div>
    </section>
  );
}

function SettingsCard({ settings, onSaved }: { settings: AiSettings; onSaved: (s: AiSettings) => void }) {
  const [limit, setLimit] = useState(settings.daily_limit);
  const [models, setModels] = useState(settings.models);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onSaved(await api.saveAiSettings({ daily_limit: limit, models: models.map(({ type, model }) => ({ type, model })) }));
    } catch (err) { setError(err instanceof Error ? err.message : String(err)); } finally { setBusy(false); }
  };
  return (
    <form className="card form" onSubmit={save} aria-label="Ajustes de Claude">
      <h3>Ajustes</h3>
      <label className="field"><span>Encargos al día (hoy: {settings.used_today})</span>
        <input type="number" min={0} max={100} required value={limit} onChange={e => setLimit(Number(e.target.value))} /></label>
      {models.map(m => (
        <label key={m.type} className="field"><span>Modelo para «{m.label}»{m.on_pc && <small className="src"> · con Chrome en tu PC; no cuenta en el límite</small>}</span>
          <select value={m.model} onChange={e => setModels(models.map(x => (x.type === m.type ? { ...x, model: e.target.value as AiModel } : x)))}>
            {AI_MODELS.map(a => <option key={a.id} value={a.id}>{a.label}</option>)}
          </select></label>
      ))}
      <p className="src">Si un encargo falla dos veces, el tercer intento sube un nivel (Sonnet → Opus).</p>
      <div className="form-actions">
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn primary" disabled={busy}>{busy ? 'Guardando…' : 'Guardar ajustes'}</button>
      </div>
    </form>
  );
}

function OrderCard({ data, settings, onOrdered }: { data: Snapshot; settings: AiSettings; onOrdered: (taskId: string) => void }) {
  const [promotion, setPromotion] = useState(data.promotions[0]?.id ?? '');
  const [type, setType] = useState<AiRequestType>('RESEARCH');
  const [title, setTitle] = useState('');
  const [goal, setGoal] = useState('');
  const [inputs, setInputs] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (data.promotions.length === 0) {
    return <div className="card"><h3>Nuevo encargo</h3><p>Primero da de alta una promoción.</p></div>;
  }
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await api.orderFromClaude(promotion, {
        type, title: title.trim() || undefined, goal: goal.trim(), inputs: inputs.split('\n').map(x => x.trim()).filter(Boolean),
      });
      setGoal(''); setTitle(''); setInputs('');
      onOrdered(r.task_id);
    } catch (err) { setError(err instanceof Error ? err.message : String(err)); } finally { setBusy(false); }
  };
  return (
    <form className="card form" onSubmit={submit} aria-label="Nuevo encargo a Claude">
      <h3>Nuevo encargo</h3>
      <label className="field"><span>Promoción</span>
        <select value={promotion} onChange={e => setPromotion(e.target.value)}>
          {data.promotions.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
      <label className="field"><span>Tipo</span>
        <select value={type} onChange={e => setType(e.target.value as AiRequestType)}>
          {settings.models.filter(m => !m.on_pc).map(m => <option key={m.type} value={m.type}>{m.label} · {AI_MODELS.find(a => a.id === m.model)?.label}</option>)}</select></label>
      <label className="field"><span>Título (opcional)</span><input maxLength={120} value={title} onChange={e => setTitle(e.target.value)} /></label>
      <label className="field"><span>Qué tiene que hacer</span>
        <textarea required rows={3} maxLength={4000} value={goal} onChange={e => setGoal(e.target.value)} /></label>
      <label className="field"><span>Datos de entrada (uno por línea)</span>
        <textarea rows={3} value={inputs} onChange={e => setInputs(e.target.value)} /></label>
      <p className="src">{INPUT_HINT[type]}</p>
      <div className="form-actions">
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn primary" disabled={busy || !goal.trim()}>{busy ? 'Encargando…' : 'Encargar a Claude'}</button>
      </div>
    </form>
  );
}
