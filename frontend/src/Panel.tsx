import { useState } from 'react';
import { Icon } from './icons';
import { clockTime, type ArmoryItem, type Brand, type Snapshot, type Station, type Task } from './model';
import { AgentChip, BrandTag, Dot, Pill, StepState, stepClass } from './ui';

function Header({ eyebrow, title, extra, onClose }: { eyebrow: string; title: string; extra?: React.ReactNode; onClose: () => void }) {
  return (
    <div className="panel-h">
      <div style={{ flex: 1, minWidth: 0 }}><small>{eyebrow}</small><h2>{title}</h2>{extra}</div>
      <button className="ibtn" onClick={onClose} aria-label="Cerrar panel"><Icon name="close" /></button>
    </div>
  );
}

export function StationPanel({ s, index, tasks, brandOf, onClose, onTask, onPause, onResume, onZoom }: {
  s: Station; index: number; tasks: Task[]; brandOf: (p: string | null) => Brand | undefined; onClose: () => void;
  onTask: (id: string) => void; onPause: () => void; onResume: () => void; onZoom: () => void;
}) {
  const c = s.counts;
  const errors = tasks.filter(t => t.status === 'err');
  return (
    <aside className="panel" aria-live="polite">
      <Header eyebrow={`Estación ${String(index + 1).padStart(2, '0')}`} title={s.name} onClose={onClose}
        extra={<div style={{ marginTop: 8 }}><Pill s={s.status} /></div>} />
      <div className="panel-b">
        <section>
          <p>{s.description}</p>
          {s.note && <p style={{ color: 'var(--ink)' }}>{s.note}</p>}
          <p className="src">El estado de la estación es independiente del de sus tareas: puede estar operativa con trabajos en espera.</p>
        </section>
        <section><h4>Tareas</h4>
          <dl className="kv">
            <dt>Total</dt><dd className="mono">{c.total}</dd><dt>En curso</dt><dd className="mono">{c.running}</dd>
            <dt>En espera o en cola</dt><dd className="mono">{c.waiting}</dd><dt>Bloqueadas</dt><dd className="mono">{c.blocked}</dd>
            <dt>Con error</dt><dd className="mono">{c.errors}</dd><dt>Terminadas</dt><dd className="mono">{c.completed}</dd>
          </dl>
        </section>
        <section><h4>Cola de trabajo</h4>
          {tasks.map(t => (
            <button key={t.id} className="qrow" onClick={() => onTask(t.id)}>
              <Dot s={t.status} />
              <span>{t.title}<small>{brandOf(t.promotionId)?.name ?? 'Común'} · {t.agentKind === 'ai' ? `Claude ${t.agentName}` : t.agentName}
                {t.status === 'run' ? ` · ${Math.floor(t.progress)}%` : ''}</small></span>
            </button>
          ))}
        </section>
        {errors.length > 0 && <section><h4>Errores recientes</h4>{errors.map(t => <p key={t.id} style={{ color: 'var(--err)' }}>{t.error}</p>)}</section>}
        <section><h4>Herramientas y agentes</h4>
          <div className="acts">{s.tools.map(x => <span key={x} className="agent">{x}</span>)}</div><p>{s.limit}</p>
        </section>
        <section><h4>Acciones</h4>
          <div className="acts">
            {s.paused ? <button className="btn primary" onClick={onResume}>Reanudar estación</button>
              : <button className="btn" onClick={onPause}>Pausar estación</button>}
            <button className="btn" onClick={onZoom}>Acercar al detalle de tareas</button>
          </div>
        </section>
      </div>
    </aside>
  );
}

const reasonTitle = (t: Task) => (t.status === 'block' ? 'Por qué está bloqueada' : t.status === 'idle' ? 'Situación' : 'Motivo de la espera');

export function TaskPanel({ t, station, brand, tasks, onClose, onAction, onTask, demo }: {
  t: Task; station?: Station; brand?: Brand; tasks: Task[]; demo: boolean; onClose: () => void;
  onAction: (action: string, model?: string) => void; onTask: (id: string) => void;
}) {
  const [confirm, setConfirm] = useState<string | null>(null);
  const run = (id: string, needsConfirm: boolean) => {
    if (needsConfirm && confirm !== id) { setConfirm(id); return; }
    setConfirm(null);
    onAction(id);
  };
  const canChangeAgent = t.actions.some(a => a.id === 'agent');
  return (
    <aside className="panel" aria-live="polite">
      <Header eyebrow={`${station?.name ?? ''} · ${brand?.name ?? 'Común'}`} title={t.title} onClose={onClose}
        extra={<div className="promo-row" style={{ marginTop: 8 }}>
          <Pill s={t.status} label={t.paused ? 'En espera · pausada' : undefined} /><BrandTag brand={brand} />
        </div>} />
      <div className="panel-b">
        {confirm && (
          <div className="confirm">
            <b>¿{t.actions.find(a => a.id === confirm)?.label} «{t.title}»?</b>
            <span>Se detiene la ejecución y queda registrada. Podrás reactivarla.</span>
            <div className="acts">
              <button className="btn danger" onClick={() => run(confirm, true)}>Sí, {t.actions.find(a => a.id === confirm)?.label.toLowerCase()}</button>
              <button className="btn" onClick={() => setConfirm(null)}>No</button>
            </div>
          </div>
        )}
        <section><h4>Objetivo</h4><p>{t.goal}</p></section>
        {t.status === 'run' && (
          <section><h4>Progreso</h4>
            <div className="prog"><div className="bar"><i style={{ width: `${t.progress}%` }} /></div><span className="mono">{Math.floor(t.progress)}%</span></div>
          </section>
        )}
        {t.reason && t.status !== 'run' && <section><h4>{reasonTitle(t)}</h4><p>{t.reason}</p></section>}
        {t.error && <section><h4>Error</h4><p style={{ color: 'var(--err)' }}>{t.error}</p></section>}
        {t.result && <section><h4>Resultado</h4><p>{t.result}</p></section>}
        {t.inputs.length > 0 && <section><h4>Datos de entrada</h4><ul>{t.inputs.map((x, i) => <li key={i}>{x}</li>)}</ul></section>}
        <section><h4>Plan de ejecución</h4>
          <ol className="steps">{t.steps.map((s, i) => <li key={i} className={stepClass(s.state)}><StepState state={s.state} />{s.label}</li>)}</ol>
        </section>
        {t.checks.length > 0 && (
          <section><h4>Validaciones</h4>
            <ol className="steps">{t.checks.map((c, i) => <li key={i} className={c.passed ? 'd' : ''}><StepState state={c.passed ? 1 : 0} />{c.label}</li>)}</ol>
          </section>
        )}
        <section><h4>Agente o herramienta</h4>
          <dl className="kv">
            <dt>Usa</dt><dd><AgentChip t={t} /></dd>
            {t.why && <><dt>Motivo</dt><dd>{t.why}</dd></>}
            <dt>Coste</dt><dd>0 € · {t.agentKind === 'ai' ? 'incluido en Claude Pro' : 'gratuito'}</dd>
            <dt>Tokens</dt><dd>Desconocido</dd>
            {t.agentKind === 'ai' && <><dt>Cuota</dt><dd>No expuesta por el proveedor</dd></>}
            <dt>Intentos</dt><dd className="mono">{t.attempts} de {t.maxAttempts}</dd>
            {t.startedAt && <><dt>Inicio</dt><dd className="mono">{clockTime(t.startedAt)}</dd></>}
            {t.dependsOn.length > 0 && <><dt>Depende de</dt><dd>{t.dependsOn.map(d => (
              <button key={d} className="btn" style={{ padding: '3px 8px' }} onClick={() => onTask(d)}>{tasks.find(x => x.id === d)?.title ?? d}</button>
            ))}</dd></>}
          </dl>
          {canChangeAgent && (
            <>
              <label htmlFor="agent-sel" className="src">Reasignar agente</label>
              <select id="agent-sel" className="agent-sel" value={t.agentName} onChange={e => onAction('agent', e.target.value)}>
                {['Haiku', 'Sonnet', 'Opus'].map(m => <option key={m} value={m}>Claude {m} · Claude Pro</option>)}
                <option disabled>OpenCode · no configurado</option>
              </select>
            </>
          )}
        </section>
        <section><h4>Acciones</h4>
          <div className="acts">
            {t.actions.filter(a => a.id !== 'agent').map((a, i) => (
              <button key={a.id} className={`btn ${i === 0 && !a.confirm ? 'primary' : ''} ${a.confirm ? 'danger' : ''}`} onClick={() => run(a.id, a.confirm)}>{a.label}</button>
            ))}
            {t.actions.length === 0 && <span className="src">No hay acciones disponibles en este estado.</span>}
          </div>
        </section>
        <section><h4>Historial</h4>
          <ul className="log">
            {[...t.events].reverse().map((e, i) => <li key={i}><time>{clockTime(e.at)}</time><span>{e.message}</span></li>)}
            {t.events.length === 0 && <li><time>—</time><span>Sin eventos todavía</span></li>}
          </ul>
        </section>
        {demo && <p className="note">Datos de ejemplo. Las acciones cambian la base de datos de SocialHub, pero no actúan sobre servicios reales.</p>}
      </div>
    </aside>
  );
}

export function ArmoryPanel({ item, data, onClose, onStation }: { item: ArmoryItem; data: Snapshot; onClose: () => void; onStation: (id: string) => void }) {
  const key = item.name.toLowerCase().split(' ')[0];
  const uses = data.stations.filter(s => s.tools.some(x => x.toLowerCase().includes(key)));
  const kind = { agent: 'Agente de IA', tool: 'Herramienta local', service: 'Servicio', device: 'Equipo' }[item.kind] ?? item.kind;
  return (
    <aside className="panel" aria-live="polite">
      <Header eyebrow={kind} title={item.name} onClose={onClose} extra={<div style={{ marginTop: 8 }}><Pill s={item.status} /></div>} />
      <div className="panel-b">
        <section>
          <dl className="kv">
            <dt>Qué es</dt><dd>{item.description}</dd><dt>Situación</dt><dd>{item.note || '—'}</dd><dt>Coste</dt><dd>0 € · gratuito</dd>
            {item.capabilities.length > 0 && <><dt>Sabe hacer</dt><dd>{item.capabilities.join(', ')}</dd></>}
          </dl>
        </section>
        {uses.length > 0 && <section><h4>La usan</h4>{uses.map(s => (
          <button key={s.id} className="qrow" onClick={() => onStation(s.id)}><Dot s={s.status} /><span>{s.name}</span></button>
        ))}</section>}
      </div>
    </aside>
  );
}
