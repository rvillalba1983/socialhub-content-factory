import { useState } from 'react';
import { Icon, StationIcon } from './icons';
import {
  PROMOTION_STATUS, PUBLICATION_STATUS, STATUS, alertTone, byPriority, isoDay, parseDay, shortDate, hhmm, statusClass,
  type Alert, type Brand, type Promotion, type PublicationStatus, type Snapshot, type Station, type Task, type TaskCommandType, type TaskStatus,
} from './model';
import { AgentChip, BrandTag, Dot, Pill, StepState, prefersReducedMotion, stepClass } from './ui';

// Geometría del anillo (coordenadas del lienzo)
export const TR = { x0: 300, x1: 2260, y0: 620, y1: 1250, r: 150 };
const SX = [650, 1070, 1490, 1910];
/** Alto del centro: su borde inferior queda 15 por encima de los nodos de la fila de abajo (radio 40 sobre y1); las tarjetas se anclan abajo y crecen hacia arriba. */
const HUB_H = TR.y1 - 55 - (TR.y0 + 85);
export const WORLD = { w: 2780, h: 1700 };

const pos = (i: number) => {
  const row = i < 4 ? 0 : 1;
  const k = row ? 7 - i : i;
  return { row, cx: SX[k], cy: row ? TR.y1 : TR.y0 };
};

function segPath(i: number) {
  const { x0, x1, y0, y1, r } = TR, g = 50;
  if (i < 3) return `M${SX[i] + g} ${y0}H${SX[i + 1] - g}`;
  if (i === 3) return `M${SX[3] + g} ${y0}H${x1 - r}A${r} ${r} 0 0 1 ${x1} ${y0 + r}V${y1 - r}A${r} ${r} 0 0 1 ${x1 - r} ${y1}H${SX[3] + g}`;
  if (i < 7) { const k = 7 - i; return `M${SX[k] - g} ${y1}H${SX[k - 1] + g}`; }
  return `M${SX[0] - g} ${y1}H${x0 + r}A${r} ${r} 0 0 1 ${x0} ${y1 - r}V${y0 + r}A${r} ${r} 0 0 1 ${x0 + r} ${y0}H${SX[0] - g}`;
}

function segMid(i: number): [number, number] {
  const { x0, x1, y0, y1 } = TR;
  if (i < 3) return [(SX[i] + SX[i + 1]) / 2, y0];
  if (i === 3) return [x1, (y0 + y1) / 2];
  if (i < 7) { const k = 7 - i; return [(SX[k] + SX[k - 1]) / 2, y1]; }
  return [x0, (y0 + y1) / 2];
}

export interface Selection { type: 'station' | 'task' | 'armory' | null; id: string | null }

export interface WorldProps {
  data: Snapshot;
  sel: Selection;
  onStation: (id: string) => void;
  onTask: (id: string) => void;
  onTaskAction: (task: Task, command: TaskCommandType) => void;
  onArmory: (id: string) => void;
  /** Abre una publicación en el centro de control de su promoción (avisos de publicación). */
  onPublication?: (id: string) => void;
  /** Botón del aviso (por ejemplo, «Pasar a Publora» en una cuenta de Publer llena). */
  onAlertAction?: (action: NonNullable<Alert['action']>) => void;
  onPromotion: (id: string) => void;
  onRegister: () => void;
}

/** Vista de una estación: si alguna tarea tiene error o la estación está bloqueada, pide atención. */
export function stationVisual(s: Station) {
  const alert = s.counts.failed > 0 ? 'err' : s.status === 'BLOCKED' ? 'block' : null;
  return { alert, running: s.counts.running > 0 && !s.paused } as const;
}

export function World({ data, sel, onStation, onTask, onTaskAction, onArmory, onPublication, onAlertAction, onPromotion, onRegister }: WorldProps) {
  const brands = new Map(data.brands.map(b => [b.id, b]));
  const brandOfPromotion = (pid?: string | null) => {
    const p = data.promotions.find(x => x.id === pid);
    return p ? brands.get(p.brand_id) : undefined;
  };
  return (
    <>
      <Track data={data} brandOf={brandOfPromotion} />
      {data.stations.slice(0, 7).map((s, i) => {
        const next = data.stations[i + 1];
        const queued = data.tasks.filter(t => t.station_id === next.id && (['WAITING', 'QUEUED', 'BLOCKED'] as TaskStatus[]).includes(t.status)).length;
        if (!queued) return null;
        const [x, y] = segMid(i);
        return <div key={s.id} className="seglabel" style={{ left: x, top: y }}>{queued} en cola</div>;
      })}
      {(() => { const [x, y] = segMid(7); return (
        <div className="seglabel fbl" style={{ left: x, top: y }}><Icon name="back" /><span className="min-2">Resultados → Investigación</span><span className="only-1">Resultados</span></div>
      ); })()}
      <div className="hub" style={{ left: TR.x0 + 175, top: TR.y0 + 85, width: TR.x1 - TR.x0 - 350, height: HUB_H }}>
        {data.promotions.map(p => (
          <PromotionCard key={p.id} p={p} brand={brands.get(p.brand_id)} data={data} onOpen={() => onPromotion(p.id)} />
        ))}
        {data.promotions.length === 0 && (
          <article className="promo welcome">
            <span className="eyebrow">Empieza aquí</span>
            <h2>{data.brands.length === 0 ? 'Da de alta tu primera marca' : 'Da de alta tu primera promoción'}</h2>
            <p className="src">Cada promoción tendrá aquí su tarjeta y su centro de control. Las estaciones del anillo ya están listas.</p>
            <button className="open-cc" onClick={e => { e.stopPropagation(); onRegister(); }}>Marcas y promociones →</button>
          </article>
        )}
        <AttentionCard data={data} onStation={onStation} onTask={onTask} onArmory={onArmory} onPublication={onPublication} onAlertAction={onAlertAction} />
      </div>
      <section className="res" style={{ left: TR.x1 + 140, top: TR.y0 - 300, width: 380 }} aria-label="Agentes y herramientas">
        <h2>Agentes y herramientas</h2>
        <span className="sub min-2">Local primero · solo gratuitas · 0 € de gasto</span>
        <div className="res-grid">
          {data.armory_items.length === 0 && <p className="src">Todavía no hay herramientas registradas. Llegan con el motor de tareas.</p>}
          {data.armory_items.map(r => (
            <button key={r.id} className="rc" onClick={() => onArmory(r.id)}>
              <Dot s={r.status} /><b>{r.name}</b>
              <small className="min-2">{r.description}<span> · {r.note}</span></small>
            </button>
          ))}
        </div>
      </section>
      {data.stations.map((s, i) => (
        <StationBlock key={s.id} s={s} index={i} tasks={data.tasks.filter(t => t.station_id === s.id)} sel={sel}
          brandOf={brandOfPromotion} onStation={onStation} onTask={onTask} onTaskAction={onTaskAction} />
      ))}
    </>
  );
}

function Track({ data, brandOf }: { data: Snapshot; brandOf: (p?: string | null) => Brand | undefined }) {
  const reduced = prefersReducedMotion();
  const st = data.stations;
  return (
    <svg className="track" width={WORLD.w} height={WORLD.h} viewBox={`0 0 ${WORLD.w} ${WORLD.h}`} aria-hidden="true">
      {st.map((_, i) => <path key={'b' + i} className="seg-base" d={segPath(i)} />)}
      {st.map((a, i) => {
        const b = st[(i + 1) % st.length];
        const fb = i === 7;
        const va = stationVisual(a), vb = stationVisual(b);
        const moving = fb ? a.status !== 'WAITING' && !a.paused : va.running || vb.running;
        const jam = !fb && !moving && vb.alert;
        const cls = moving ? 'flow' : jam ? `jam jam-${vb.alert}` : 'idle';
        const d = segPath(i);
        const packets = [];
        if (moving && !reduced) {
          const colors = fb ? ['var(--ink-3)']
            : data.tasks.filter(t => (t.station_id === a.id || t.station_id === b.id) && t.status === 'RUNNING')
              .map(t => brandOf(t.promotion_id)?.color ?? 'var(--ink-3)');
          const n = fb ? 2 : 3, dur = fb ? 10 : 5;
          for (let k = 0; k < n; k++) {
            packets.push(
              <circle key={`p${i}-${k}`} className="pk" r={fb ? 7 : 9} style={{ fill: colors[k % colors.length] ?? 'var(--ink-3)' }}>
                <animateMotion dur={`${dur}s`} repeatCount="indefinite" begin={`-${(k * dur / n).toFixed(2)}s`} path={d} />
              </circle>);
          }
        }
        return [<path key={'s' + i} className={`seg ${cls}${fb ? ' fb' : ''}`} d={d} />, ...packets];
      })}
    </svg>
  );
}

function PromotionCard({ p, brand, data, onOpen }: { p: Promotion; brand?: Brand; data: Snapshot; onOpen: () => void }) {
  const pubs = data.publications.filter(x => x.promotion_id === p.id);
  const count = (codes: PublicationStatus[]) => pubs.filter(x => codes.includes(x.status)).length;
  const groups: [string, number, string][] = ([
    ['Publicadas', count(['PUBLISHED']), 'var(--ok)'],
    ['Programadas', count(['SCHEDULED', 'PUBLISHING']), 'var(--wait)'],
    ['Aprobadas', count(['APPROVED']), 'var(--ink)'],
    ['En revisión', count(['IN_REVIEW']), 'var(--block)'],
    ['En producción', count(['IN_PRODUCTION', 'DRAFT']), 'var(--run)'],
    ['Ideas y propuestas', count(['IDEA', 'PROPOSAL']), 'var(--line-2)'],
    ['Con error', count(['PUBLISH_ERROR']), 'var(--err)'],
  ] as [string, number, string][]).filter(g => g[1] > 0);
  const total = groups.reduce((a, g) => a + g[1], 0) || 1;
  const today = data.now.slice(0, 10);
  const next = pubs.find(x => x.scheduled_on >= today && x.status !== 'PUBLISHED' && x.status !== 'CANCELLED');
  const incidents = pubs.filter(x => x.flag || x.status === 'PUBLISH_ERROR').length
    + data.tasks.filter(t => t.promotion_id === p.id && t.status === 'FAILED').length;
  const waitingRaul = data.tasks.filter(t => (t.promotion_id === p.id || !t.promotion_id) && t.human
    && t.status !== 'COMPLETED' && t.status !== 'CANCELLED').length;
  const start = parseDay(p.starts_on).getTime(), end = parseDay(p.ends_on).getTime(), span = Math.max(1, end - start);
  const done = Math.max(0, Math.min(1, (parseDay(today).getTime() - start) / span));
  const dayN = Math.floor((parseDay(today).getTime() - start) / 864e5) + 1;
  const totalDays = Math.round(span / 864e5) + 1;
  const phase = dayN < 1 ? `Empieza en ${1 - dayN} ${1 - dayN === 1 ? 'día' : 'días'}` : dayN > totalDays ? 'Terminada' : `Día ${dayN} de ${totalDays}`;
  return (
    <article className="promo" style={{ ['--pc' as string]: brand?.color }} tabIndex={0} onClick={onOpen}
      onKeyDown={e => e.key === 'Enter' && onOpen()} aria-label={`Abrir el centro de control de ${p.name}`}>
      <div className="p-top"><span className="eyebrow">{brand?.name}<span className="min-2"> · {brand?.handle}</span></span><Pill s={PROMOTION_STATUS[p.status].s} label={PROMOTION_STATUS[p.status].label} /></div>
      <h2>{p.name}</h2>
      <div className="sub">{phase}<span className="min-2"> · {shortDate(p.starts_on)} – {shortDate(p.ends_on)}</span></div>
      <div className="timeline min-2" aria-hidden="true">
        <i className="rail" /><i className="done" style={{ width: `${done * 100}%` }} />
        {p.milestones.map((m, i) => {
          const q = (parseDay(m.starts_on).getTime() - start) / span;
          return q >= 0 && q <= 1 ? <i key={i} className="ms" style={{ left: `${q * 100}%` }} title={m.label} /> : null;
        })}
        <i className="now" style={{ left: `${done * 100}%` }} />
      </div>
      <div className="stack min-2" role="img" aria-label="Publicaciones por estado">
        {groups.map(([l, n, c]) => <i key={l} style={{ width: `${n / total * 100}%`, background: c }} title={`${l}: ${n}`} />)}
      </div>
      <div className="legend-row min-2">
        {groups.map(([l, n, c]) => <span key={l} style={{ ['--c' as string]: c }}>{l} <b className="mono">{n}</b></span>)}
      </div>
      <div className="promo-facts min-2">
        <div><small>Próxima</small><b>{next ? `${shortDate(next.scheduled_on)} ${hhmm(next.scheduled_time)}` : '—'}</b></div>
        <div className={incidents ? 'bad' : ''}><small>Incidencias</small><b>{incidents}</b></div>
        <div><small>Esperan a Raúl</small><b>{waitingRaul}</b></div>
      </div>
      <button className="open-cc" onClick={e => { e.stopPropagation(); onOpen(); }}>Abrir centro de control →</button>
    </article>
  );
}

/**
 * Resumen de hoy (fase 8): lo publicado y lo que queda por publicar. Va dentro de «Necesita atención», que ya cuenta
 * los fallos y lo que espera por Raúl. Solo en la app, sin notificación (decisión de Raúl).
 */
export function TodayCard({ data }: { data: Snapshot }) {
  const today = data.now.slice(0, 10);
  const pubs = data.publications.filter(p => p.scheduled_on === today);
  const published = pubs.filter(p => p.status === 'PUBLISHED').length;
  const pending = pubs.filter(p => p.status === 'SCHEDULED' || p.status === 'PUBLISHING').length;
  const failed = pubs.filter(p => p.status === 'PUBLISH_ERROR').length;
  const tomorrow = new Date(Date.parse(`${today}T12:00:00Z`) + 86_400_000).toISOString().slice(0, 10);
  const next = data.publications.filter(p => p.scheduled_on === tomorrow && p.status === 'SCHEDULED').length;
  const rows: [string, number, boolean][] = [
    ['Publicadas hoy', published, false],
    ['Por publicar hoy', pending, false],
    ['Para mañana', next, false],
  ];
  return (
    <div className="today min-2" aria-label="Resumen de hoy">
      <div className="promo-facts">
        {rows.map(([label, n, bad]) => <div key={label} className={bad ? 'bad' : ''}><small>{label}</small><b>{n}</b></div>)}
      </div>
      {failed > 0 && <p className="src error-text">{failed === 1 ? '1 publicación de hoy con error' : `${failed} publicaciones de hoy con error`}</p>}
    </div>
  );
}

/** Cuántos avisos se ven sin desplegar: con más, «Ver N más» abre la lista entera dentro de la tarjeta. */
const ATTENTION_MAX = 2;

/** Abre lo que señala un aviso: la tarea, la estación, el elemento de la armería (por ejemplo, Drive) o la publicación. */
export function openAlert(a: Alert, go: {
  onTask: (id: string) => void; onStation: (id: string) => void; onArmory?: (id: string) => void; onPublication?: (id: string) => void;
}) {
  if (a.target_type === 'TASK') go.onTask(a.target_id);
  else if (a.target_type === 'PUBLICATION') (go.onPublication ? go.onPublication(a.target_id) : go.onStation(a.station_id));
  else if (a.target_type === 'ARMORY') (go.onArmory ? go.onArmory(a.target_id) : go.onStation(a.station_id));
  else go.onStation(a.target_id);
}

export function AttentionCard({ data, onStation, onTask, onArmory, onPublication, onAlertAction }: {
  data: Snapshot; onStation: (id: string) => void; onTask: (id: string) => void; onArmory?: (id: string) => void;
  onPublication?: (id: string) => void; onAlertAction?: (action: NonNullable<Alert['action']>) => void;
}) {
  // Lista entera desplegada y aviso con el texto completo: viven en la tarjeta, que no se desmonta al recargar datos
  const [listOpen, setListOpen] = useState(false);
  const [fullKey, setFullKey] = useState<string | null>(null);
  const blockedByDeps = data.tasks.filter(t => t.status === 'BLOCKED').length;
  const waitingRaul = data.tasks.filter(t => t.human && t.status !== 'COMPLETED' && t.status !== 'CANCELLED').length;
  const sorted = byPriority(data.alerts);
  const extra = sorted.length - ATTENTION_MAX;
  const shown = listOpen ? sorted : sorted.slice(0, ATTENTION_MAX);
  return (
    <section className={`attn-card${listOpen && extra > 0 ? ' open' : ''}`} aria-label="Necesita tu atención">
      <h2>Necesita atención {data.alerts.length > 0 && <span className="count">{data.alerts.length}</span>}</h2>
      <TodayCard data={data} />
      {data.alerts.length === 0 && <p className="src">Todo en orden.</p>}
      <div className="attn-list">
        {shown.map(a => {
          const key = `${a.kind}|${a.target_id}|${a.title}`;
          const full = fullKey === key;
          return (
            <div key={key} className={`aitem-row attn-row${full ? ' full' : ''}`}>
              <button className={`aitem s-${alertTone(a.kind)}`} aria-expanded={full} title={a.detail ? `${a.title}. ${a.detail}` : a.title}
                onClick={() => {
                  // Primer toque: despliega el texto completo; segundo toque sobre el desplegado: abre su destino
                  if (!full) setFullKey(key);
                  else { setFullKey(null); openAlert(a, { onTask, onStation, onArmory, onPublication }); }
                }}>
                <Dot s={a.kind} /><span><b>{a.title}</b><span className="min-2">{a.detail}</span></span>
              </button>
              {a.action && onAlertAction && (
                <button type="button" className="btn attn-action" onClick={() => onAlertAction(a.action!)}>{a.action.label}</button>
              )}
            </div>
          );
        })}
      </div>
      {extra > 0 && (
        <button type="button" className="btn attn-more min-2" aria-expanded={listOpen} onClick={() => setListOpen(!listOpen)}>
          {listOpen ? 'Ver menos' : `Ver ${extra} más`}
        </button>
      )}
      <p className="src min-2">{blockedByDeps} tareas bloqueadas · {waitingRaul} esperan a Raúl</p>
    </section>
  );
}

interface StationBlockProps {
  s: Station; index: number; tasks: Task[]; sel: Selection; brandOf: (p?: string | null) => Brand | undefined;
  onStation: (id: string) => void; onTask: (id: string) => void; onTaskAction: (task: Task, command: TaskCommandType) => void;
}

function StationBlock({ s, index, tasks, sel, brandOf, onStation, onTask, onTaskAction }: StationBlockProps) {
  const p = pos(index);
  const v = stationVisual(s);
  const selected = sel.type === 'station' && sel.id === s.id;
  const c = s.counts;
  const alertCount = c.failed + c.blocked;
  const kpi = (cls: string, n: number, label: string, icon: 'run' | 'clock' | 'lock' | 'alert') => (
    <div className={`kpi k-${cls}${n ? '' : ' zero'}`} title={label}>
      <span className="ki"><Icon name={icon} /></span><b>{n}</b><span className="kl">{label}</span>
    </div>
  );
  return (
    <>
      <button className={`node ${v.alert ? `s-${v.alert} attn` : statusClass(s.status)}${v.running ? ' running' : ''}${selected ? ' sel' : ''}`}
        data-st={s.id} style={{ left: p.cx - 40, top: p.cy - 40 }} onClick={() => onStation(s.id)}
        aria-label={`${s.name}: ${s.status}`}>
        <StationIcon id={s.id} />
        {v.alert && <span className="badge">{c.failed || c.blocked || '!'}</span>}
      </button>
      <article className={`st row${p.row} ${statusClass(s.status)}${selected ? ' sel' : ''}${v.alert ? ` attn-${v.alert}` : ''}`} data-st={s.id}
        style={{ left: p.cx - 170, top: p.row ? p.cy + 64 : p.cy - 64 }} tabIndex={0}
        onClick={() => onStation(s.id)} onKeyDown={e => e.key === 'Enter' && onStation(s.id)} aria-label={s.name}>
        <header className="st-h">
          <span className="st-n min-2">{String(index + 1).padStart(2, '0')}</span><h3>{s.name}</h3><Pill s={s.status} />
        </header>
        <div className="st-sum only-1">
          <span>{tasks.length} {tasks.length === 1 ? 'tarea' : 'tareas'}</span>
          {alertCount > 0 && <span className="bad">{alertCount} {alertCount === 1 ? 'aviso' : 'avisos'}</span>}
        </div>
        <p className="st-what min-2">{s.description}</p>
        {s.note && <p className="st-note min-2">{s.note}</p>}
        <div className="kpis min-2">
          {kpi('run', c.running, 'En curso', 'run')}{kpi('wait', c.waiting, 'En espera', 'clock')}
          {kpi('block', c.blocked, 'Bloqueadas', 'lock')}{kpi('err', c.failed, 'Errores', 'alert')}
        </div>
        <ul className="tasks min-2">
          {tasks.map(t => (
            <TaskRow key={t.id} t={t} brand={brandOf(t.promotion_id)} selected={sel.type === 'task' && sel.id === t.id}
              onOpen={() => onTask(t.id)} onAction={a => onTaskAction(t, a)} />
          ))}
        </ul>
        <div className="st-res only-3">
          <div className="chips">{s.tools.map(x => <span key={x} className="agent">{x}</span>)}</div>
          <div>{s.limit_note}</div>
        </div>
      </article>
    </>
  );
}

function TaskRow({ t, brand, selected, onOpen, onAction }: {
  t: Task; brand?: Brand; selected: boolean; onOpen: () => void; onAction: (a: TaskCommandType) => void;
}) {
  return (
    <li className={`tk ${statusClass(t.status)}${selected ? ' sel' : ''}`} onClick={e => { e.stopPropagation(); onOpen(); }}>
      <div className="tk-top"><Dot s={t.status} /><span className="tk-title">{t.title}</span></div>
      <div className="tk-meta"><BrandTag brand={brand} /><AgentChip agent={t.agent} /></div>
      {t.status === 'RUNNING' ? (
        <div className="prog"><div className="bar"><i style={{ width: `${t.progress}%` }} /></div>
          <span className="mono">{Math.floor(t.progress)}%</span></div>
      ) : t.status === 'FAILED' ? <div className="why" style={{ color: 'var(--err)' }}>{t.error}</div>
        : (t.reason || t.result) ? <div className="why">{t.reason ?? t.result}</div> : null}
      <div className="deep only-3">
        <ol className="steps">
          {t.steps.map((s, i) => <li key={i} className={stepClass(s.state)}><StepState state={s.state} />{s.label}</li>)}
        </ol>
        <dl className="facts">
          <dt>Estado</dt><dd>{t.paused ? 'En espera · pausada' : STATUS[t.status].label}</dd>
          <dt>Coste</dt><dd>0 € · {t.agent.kind === 'AI' ? 'incluido en Claude Pro' : 'gratuito'}</dd>
          {t.agent.kind === 'AI' && <><dt>Cuota</dt><dd>No expuesta por el proveedor</dd></>}
        </dl>
        <div className="mini-acts">
          {t.available_commands.filter(a => a.type !== 'CHANGE_AGENT').slice(0, 3).map(a => (
            <button key={a.type} onClick={e => { e.stopPropagation(); onAction(a.type); }}>{a.label}</button>
          ))}
        </div>
      </div>
    </li>
  );
}

export const publicationState = (code: PublicationStatus) => PUBLICATION_STATUS[code];
export const todayIso = (data: Snapshot) => isoDay(parseDay(data.now.slice(0, 10)));
