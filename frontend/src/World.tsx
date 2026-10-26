import { Icon, StationIcon } from './icons';
import {
  PUBLICATION_STATUS, isoDay, parseDay, shortDate, hhmm,
  type Brand, type Promotion, type Snapshot, type Station, type Task,
} from './model';
import { AgentChip, BrandTag, Dot, Pill, StepState, prefersReducedMotion, stepClass } from './ui';

// Geometría del anillo (coordenadas del lienzo)
export const TR = { x0: 300, x1: 2260, y0: 620, y1: 1250, r: 150 };
const SX = [650, 1070, 1490, 1910];
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
  onTaskAction: (task: Task, action: string) => void;
  onArmory: (id: string) => void;
  onPromotion: (id: string) => void;
}

/** Vista de una estación: si alguna tarea tiene error o la estación está bloqueada, pide atención. */
export function stationVisual(s: Station) {
  const alert = s.counts.errors > 0 ? 'err' : s.status === 'block' ? 'block' : null;
  return { alert, running: s.counts.running > 0 && !s.paused } as const;
}

export function World({ data, sel, onStation, onTask, onTaskAction, onArmory, onPromotion }: WorldProps) {
  const brands = new Map(data.brands.map(b => [b.id, b]));
  const brandOfPromotion = (pid: string | null) => {
    const p = data.promotions.find(x => x.id === pid);
    return p ? brands.get(p.brandId) : undefined;
  };
  return (
    <>
      <Track data={data} brandOf={brandOfPromotion} />
      {data.stations.slice(0, 7).map((s, i) => {
        const next = data.stations[i + 1];
        const queued = data.tasks.filter(t => t.stationId === next.id && ['wait', 'queued', 'block'].includes(t.status)).length;
        if (!queued) return null;
        const [x, y] = segMid(i);
        return <div key={s.id} className="seglabel" style={{ left: x, top: y }}>{queued} en cola</div>;
      })}
      {(() => { const [x, y] = segMid(7); return (
        <div className="seglabel fbl" style={{ left: x, top: y }}><Icon name="back" />Resultados → Investigación</div>
      ); })()}
      <div className="hub" style={{ left: TR.x0 + 175, top: TR.y0 + 85, width: TR.x1 - TR.x0 - 350 }}>
        {data.promotions.map(p => (
          <PromotionCard key={p.id} p={p} brand={brands.get(p.brandId)} data={data} onOpen={() => onPromotion(p.id)} />
        ))}
        <AttentionCard data={data} onStation={onStation} onTask={onTask} />
      </div>
      <section className="res" style={{ left: TR.x1 + 140, top: TR.y0 - 300, width: 380 }} aria-label="Agentes y herramientas">
        <h2>Agentes y herramientas</h2>
        <span className="sub">Local primero · solo gratuitas · 0 € de gasto</span>
        <div className="res-grid">
          {data.armory.map(r => (
            <button key={r.id} className="rc" onClick={() => onArmory(r.id)}>
              <Dot s={r.status} /><b>{r.name}</b>
              <small>{r.description}<span className="min-2"> · {r.note}</span></small>
            </button>
          ))}
        </div>
      </section>
      {data.stations.map((s, i) => (
        <StationBlock key={s.id} s={s} index={i} tasks={data.tasks.filter(t => t.stationId === s.id)} sel={sel}
          brandOf={brandOfPromotion} onStation={onStation} onTask={onTask} onTaskAction={onTaskAction} />
      ))}
    </>
  );
}

function Track({ data, brandOf }: { data: Snapshot; brandOf: (p: string | null) => Brand | undefined }) {
  const reduced = prefersReducedMotion();
  const st = data.stations;
  return (
    <svg className="track" width={WORLD.w} height={WORLD.h} viewBox={`0 0 ${WORLD.w} ${WORLD.h}`} aria-hidden="true">
      {st.map((_, i) => <path key={'b' + i} className="seg-base" d={segPath(i)} />)}
      {st.map((a, i) => {
        const b = st[(i + 1) % st.length];
        const fb = i === 7;
        const va = stationVisual(a), vb = stationVisual(b);
        const moving = fb ? a.status !== 'wait' && !a.paused : va.running || vb.running;
        const jam = !fb && !moving && vb.alert;
        const cls = moving ? 'flow' : jam ? `jam jam-${vb.alert}` : 'idle';
        const d = segPath(i);
        const packets = [];
        if (moving && !reduced) {
          const colors = fb ? ['var(--ink-3)']
            : data.tasks.filter(t => (t.stationId === a.id || t.stationId === b.id) && t.status === 'run')
              .map(t => brandOf(t.promotionId)?.color ?? 'var(--ink-3)');
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
  const pubs = data.publications.filter(x => x.promotionId === p.id);
  const count = (codes: string[]) => pubs.filter(x => codes.includes(x.status)).length;
  const groups: [string, number, string][] = ([
    ['Publicadas', count(['published']), 'var(--ok)'],
    ['Programadas', count(['scheduled', 'publishing']), 'var(--wait)'],
    ['Aprobadas', count(['approved']), 'var(--ink)'],
    ['En revisión', count(['review']), 'var(--block)'],
    ['En producción', count(['production', 'draft']), 'var(--run)'],
    ['Ideas y propuestas', count(['idea', 'proposal']), 'var(--line-2)'],
    ['Con error', count(['error']), 'var(--err)'],
  ] as [string, number, string][]).filter(g => g[1] > 0);
  const total = groups.reduce((a, g) => a + g[1], 0) || 1;
  const today = data.now.slice(0, 10);
  const next = pubs.find(x => x.date >= today && !['published', 'cancelled'].includes(x.status));
  const incidents = pubs.filter(x => x.flag || x.status === 'error').length
    + data.tasks.filter(t => t.promotionId === p.id && t.status === 'err').length;
  const waitingRaul = data.tasks.filter(t => (t.promotionId === p.id || t.promotionId === null) && t.human
    && !['ok', 'cancel'].includes(t.status)).length;
  const start = parseDay(p.startsOn).getTime(), end = parseDay(p.endsOn).getTime(), span = Math.max(1, end - start);
  const done = Math.max(0, Math.min(1, (parseDay(today).getTime() - start) / span));
  const dayN = Math.floor((parseDay(today).getTime() - start) / 864e5) + 1;
  const totalDays = Math.round(span / 864e5) + 1;
  const phase = dayN < 1 ? `Empieza en ${1 - dayN} ${1 - dayN === 1 ? 'día' : 'días'}` : dayN > totalDays ? 'Terminada' : `Día ${dayN} de ${totalDays}`;
  return (
    <article className="promo" style={{ ['--pc' as string]: brand?.color }} tabIndex={0} onClick={onOpen}
      onKeyDown={e => e.key === 'Enter' && onOpen()} aria-label={`Abrir el centro de control de ${p.name}`}>
      <div className="p-top"><span className="eyebrow">{brand?.name} · {brand?.handle}</span><Pill s="ok" label="Activa" /></div>
      <h2>{p.name}</h2>
      <div className="sub">{phase}<span className="min-2"> · {shortDate(p.startsOn)} – {shortDate(p.endsOn)}</span></div>
      <div className="timeline" aria-hidden="true">
        <i className="rail" /><i className="done" style={{ width: `${done * 100}%` }} />
        {p.milestones.map((m, i) => {
          const q = (parseDay(m.startsOn).getTime() - start) / span;
          return q >= 0 && q <= 1 ? <i key={i} className="ms" style={{ left: `${q * 100}%` }} title={m.label} /> : null;
        })}
        <i className="now" style={{ left: `${done * 100}%` }} />
      </div>
      <div className="stack" role="img" aria-label="Publicaciones por estado">
        {groups.map(([l, n, c]) => <i key={l} style={{ width: `${n / total * 100}%`, background: c }} title={`${l}: ${n}`} />)}
      </div>
      <div className="legend-row min-2">
        {groups.map(([l, n, c]) => <span key={l} style={{ ['--c' as string]: c }}>{l} <b className="mono">{n}</b></span>)}
      </div>
      <div className="promo-facts">
        <div><small>Próxima</small><b>{next ? `${shortDate(next.date)} ${hhmm(next.time)}` : '—'}</b></div>
        <div className={incidents ? 'bad' : ''}><small>Incidencias</small><b>{incidents}</b></div>
        <div><small>Esperan a Raúl</small><b>{waitingRaul}</b></div>
      </div>
      <button className="open-cc" onClick={e => { e.stopPropagation(); onOpen(); }}>Abrir centro de control →</button>
    </article>
  );
}

export function AttentionCard({ data, onStation, onTask }: { data: Snapshot; onStation: (id: string) => void; onTask: (id: string) => void }) {
  const blockedByDeps = data.tasks.filter(t => t.status === 'block').length;
  const waitingRaul = data.tasks.filter(t => t.human && !['ok', 'cancel'].includes(t.status)).length;
  return (
    <section className="attn-card" aria-label="Necesita tu atención">
      <h2>Necesita atención {data.alerts.length > 0 && <span className="count">{data.alerts.length}</span>}</h2>
      {data.alerts.length === 0 && <p className="src">Todo en orden.</p>}
      {data.alerts.map(a => (
        <button key={a.kind + a.id} className={`aitem s-${a.kind}`} onClick={() => (a.target === 'task' ? onTask(a.id) : onStation(a.id))}>
          <Dot s={a.kind} /><span><b>{a.title}</b>{a.detail}</span>
        </button>
      ))}
      <p className="src">{blockedByDeps} tareas bloqueadas · {waitingRaul} esperan a Raúl</p>
    </section>
  );
}

interface StationBlockProps {
  s: Station; index: number; tasks: Task[]; sel: Selection; brandOf: (p: string | null) => Brand | undefined;
  onStation: (id: string) => void; onTask: (id: string) => void; onTaskAction: (task: Task, action: string) => void;
}

function StationBlock({ s, index, tasks, sel, brandOf, onStation, onTask, onTaskAction }: StationBlockProps) {
  const p = pos(index);
  const v = stationVisual(s);
  const selected = sel.type === 'station' && sel.id === s.id;
  const c = s.counts;
  const kpi = (cls: string, n: number, label: string, icon: 'run' | 'clock' | 'lock' | 'alert') => (
    <div className={`kpi k-${cls}${n ? '' : ' zero'}`} title={label}>
      <span className="ki"><Icon name={icon} /></span><b>{n}</b><span className="kl">{label}</span>
    </div>
  );
  return (
    <>
      <button className={`node ${v.alert ? `s-${v.alert} attn` : `s-${s.status}`}${v.running ? ' running' : ''}${selected ? ' sel' : ''}`}
        data-st={s.id} style={{ left: p.cx - 40, top: p.cy - 40 }} onClick={() => onStation(s.id)}
        aria-label={`${s.name}: ${s.status}`}>
        <StationIcon id={s.id} />
        {v.alert && <span className="badge">{c.errors || c.blocked || '!'}</span>}
      </button>
      <article className={`st row${p.row} s-${s.status}${selected ? ' sel' : ''}${v.alert ? ` attn-${v.alert}` : ''}`} data-st={s.id}
        style={{ left: p.cx - 170, top: p.row ? p.cy + 64 : p.cy - 64 }} tabIndex={0}
        onClick={() => onStation(s.id)} onKeyDown={e => e.key === 'Enter' && onStation(s.id)} aria-label={s.name}>
        <header className="st-h">
          <span className="st-n">{String(index + 1).padStart(2, '0')}</span><h3>{s.name}</h3><Pill s={s.status} />
        </header>
        <p className="st-what min-2">{s.description}</p>
        {s.note && <p className="st-note min-2">{s.note}</p>}
        <div className="kpis">
          {kpi('run', c.running, 'En curso', 'run')}{kpi('wait', c.waiting, 'En espera', 'clock')}
          {kpi('block', c.blocked, 'Bloqueadas', 'lock')}{kpi('err', c.errors, 'Errores', 'alert')}
        </div>
        <ul className="tasks min-2">
          {tasks.map(t => (
            <TaskRow key={t.id} t={t} brand={brandOf(t.promotionId)} selected={sel.type === 'task' && sel.id === t.id}
              onOpen={() => onTask(t.id)} onAction={a => onTaskAction(t, a)} />
          ))}
        </ul>
        <div className="st-res only-3">
          <div className="chips">{s.tools.map(x => <span key={x} className="agent">{x}</span>)}</div>
          <div>{s.limit}</div>
        </div>
      </article>
    </>
  );
}

function TaskRow({ t, brand, selected, onOpen, onAction }: {
  t: Task; brand?: Brand; selected: boolean; onOpen: () => void; onAction: (a: string) => void;
}) {
  return (
    <li className={`tk s-${t.status}${selected ? ' sel' : ''}`} onClick={e => { e.stopPropagation(); onOpen(); }}>
      <div className="tk-top"><Dot s={t.status} /><span className="tk-title">{t.title}</span></div>
      <div className="tk-meta"><BrandTag brand={brand} /><AgentChip t={t} /></div>
      {t.status === 'run' ? (
        <div className="prog"><div className="bar"><i style={{ width: `${t.progress}%` }} /></div>
          <span className="mono">{Math.floor(t.progress)}%</span></div>
      ) : t.status === 'err' ? <div className="why" style={{ color: 'var(--err)' }}>{t.error}</div>
        : (t.reason || t.result) ? <div className="why">{t.reason ?? t.result}</div> : null}
      <div className="deep only-3">
        <ol className="steps">
          {t.steps.map((s, i) => <li key={i} className={stepClass(s.state)}><StepState state={s.state} />{s.label}</li>)}
        </ol>
        <dl className="facts">
          <dt>Estado</dt><dd>{t.paused ? 'En espera · pausada' : t.status}</dd>
          <dt>Coste</dt><dd>0 € · {t.agentKind === 'ai' ? 'incluido en Claude Pro' : 'gratuito'}</dd>
          {t.agentKind === 'ai' && <><dt>Cuota</dt><dd>No expuesta por el proveedor</dd></>}
        </dl>
        <div className="mini-acts">
          {t.actions.filter(a => a.id !== 'agent').slice(0, 3).map(a => (
            <button key={a.id} onClick={e => { e.stopPropagation(); onAction(a.id); }}>{a.label}</button>
          ))}
        </div>
      </div>
    </li>
  );
}

export const publicationState = (code: string) => PUBLICATION_STATUS[code] ?? { label: code, s: 'idle' as const };
export const todayIso = (data: Snapshot) => isoDay(parseDay(data.now.slice(0, 10)));
