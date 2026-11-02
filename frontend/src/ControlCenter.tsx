import { useEffect, useMemo, useRef, useState } from 'react';
import { Icon } from './icons';
import {
  PROMOTION_STATUS, PUBLICATION_STATUS, STATUS, addDays, dayLabel, hhmm, isoDay, parseDay, shortDate, statusClass, weekday,
  type Brand, type Promotion, type Publication, type PublicationStatus, type Snapshot,
} from './model';
import { AgentChip, Dot, Pill } from './ui';
import { FolderRoles } from './Folders';
import { PublicationEditor } from './PublicationEditor';

const TABS: [string, string][] = [
  ['resumen', 'Resumen'], ['calendario', 'Calendario'], ['publicaciones', 'Publicaciones'], ['tareas', 'Tareas'],
  ['recursos', 'Recursos'], ['automatizacion', 'Automatización'], ['metricas', 'Métricas'], ['config', 'Configuración'],
];

const FILTERS: [string, string][] = [
  ['all', 'Todas'], ['pending', 'Pendientes'], ['IN_REVIEW', 'En revisión'], ['SCHEDULED', 'Programadas'],
  ['PUBLISHED', 'Publicadas'], ['issues', 'Con incidencia'],
];

export function ControlCenter({ p, brand, data, initialTab, highlight, onClose, onTask, onEdit, onMessage }: {
  p: Promotion; brand?: Brand; data: Snapshot; initialTab: string; highlight: string | null;
  onClose: () => void; onTask: (id: string) => void; onEdit: () => void; onMessage?: (message: string) => void;
}) {
  /** Publicación abierta en el editor: su id, «new» para una nueva o null. */
  const [editing, setEditing] = useState<string | null>(null);
  const [tab, setTab] = useState(initialTab);
  const [filter, setFilter] = useState('all');
  const [hl, setHl] = useState<string | null>(highlight);
  const today = data.now.slice(0, 10);
  const pubs = data.publications.filter(x => x.promotion_id === p.id);
  const weeks = useMemo(() => {
    const start = parseDay(p.starts_on), end = parseDay(p.ends_on);
    const first = addDays(start, -((start.getUTCDay() + 6) % 7));
    const out: Date[] = [];
    for (let d = first; d <= end; d = addDays(d, 7)) out.push(d);
    return out;
  }, [p.starts_on, p.ends_on]);
  const [week, setWeek] = useState(() => {
    const t = parseDay(today).getTime();
    const i = weeks.findIndex(w => t >= w.getTime() && t < addDays(w, 7).getTime());
    return i < 0 ? 0 : i;
  });
  const ref = useRef<HTMLElement>(null);
  useEffect(() => { ref.current?.scrollTo?.({ top: 0 }); }, [p.id]);
  useEffect(() => {
    if (hl && tab === 'publicaciones') document.getElementById(`row-${hl}`)?.scrollIntoView?.({ block: 'center' });
  }, [hl, tab]);

  const jump = (id: string) => { setTab('publicaciones'); setFilter('all'); setHl(id); };
  const freeDays = p.milestones.find(m => m.ends_on && /gratis/i.test(m.label));

  return (
    <section className="cc" ref={ref}>
      <div className="cc-in" style={{ ['--pc' as string]: brand?.color }}>
        <div><button className="btn back" onClick={onClose}><Icon name="back" />Volver al mapa</button></div>
        <div className="cc-head">
          <div>
            <div className="eyebrow">{brand?.name} · {brand?.handle}</div>
            <h1>{p.name}</h1>
            <p>{shortDate(p.starts_on)} – {shortDate(p.ends_on)} · {p.cadence}</p>
            <p>{p.description}</p>
          </div>
          <div className="promo-row"><Pill s={PROMOTION_STATUS[p.status].s} label={PROMOTION_STATUS[p.status].label} /><span className="pill s-wait"><Icon name="human" /><span>Supervisada</span></span>
            <button className="btn" onClick={onEdit}>Editar</button></div>
        </div>
        <nav className="tabs" role="tablist">
          {TABS.map(([k, l]) => <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'on' : ''} onClick={() => { setTab(k); setHl(null); }}>{l}</button>)}
        </nav>

        {tab === 'resumen' && (() => {
          const tasks = data.tasks.filter(t => t.promotion_id === p.id);
          const incidents = tasks.filter(t => t.status === 'FAILED' || t.status === 'BLOCKED');
          const next = pubs.filter(x => x.scheduled_on >= today && x.status !== 'PUBLISHED' && x.status !== 'CANCELLED').slice(0, 4);
          return (
            <div className="grid2">
              <div className="card"><h3>Objetivos</h3><ul>{p.goals.map((g, i) => <li key={i}>{g}</li>)}</ul><p className="src">{p.scope}</p></div>
              <div className="card"><h3>Hitos</h3>
                <ul className="ms-list">{p.milestones.map((m, i) => (
                  <li key={i}><time>{shortDate(m.starts_on)}{m.ends_on ? `–${shortDate(m.ends_on)}` : ''}</time><span>{m.label}</span></li>
                ))}</ul>
              </div>
              <div className="card"><h3>Próximas publicaciones</h3>{next.length ? next.map(x => <PubEvent key={x.id} x={x} onClick={() => jump(x.id)} />) : <p>Nada pendiente.</p>}</div>
              <div className="card"><h3>Incidencias</h3>
                {incidents.length ? incidents.map(t => (
                  <button key={t.id} className="qrow" onClick={() => onTask(t.id)}><Dot s={t.status} /><span>{t.title}<small>{t.error ?? t.reason}</small></span></button>
                )) : <p>Ninguna.</p>}
              </div>
              <div className="card"><h3>Esperan a Raúl</h3><ul>{p.human_todos.map((h, i) => <li key={i}>{h}</li>)}</ul>
                <p className="src">Claude prepara, programa y avisa. Raúl solo revisa y pulsa enviar.</p></div>
              <div className="card"><h3>Reglas de la promoción</h3><ul>{[...p.rules, ...data.common_rules].map((r, i) => <li key={i}>{r}</li>)}</ul></div>
            </div>
          );
        })()}

        {tab === 'calendario' && weeks.length > 0 && (() => {
          const w = weeks[Math.min(week, weeks.length - 1)];
          const days = [...Array(7)].map((_, i) => addDays(w, i));
          return (
            <>
              <div className="cal-nav">
                <button className="btn" disabled={week === 0} onClick={() => setWeek(week - 1)}>Semana anterior</button>
                <b>{dayLabel(days[0])} – {dayLabel(days[6])}</b>
                <button className="btn" disabled={week >= weeks.length - 1} onClick={() => setWeek(week + 1)}>Semana siguiente</button>
                <span className="src">{p.cadence} · Europe/Madrid</span>
              </div>
              <div className="cal">
                {days.map(d => {
                  const iso = isoDay(d);
                  const ev = pubs.filter(x => x.scheduled_on === iso);
                  const free = freeDays && iso >= freeDays.starts_on && iso <= (freeDays.ends_on ?? freeDays.starts_on);
                  return (
                    <div key={iso} className={`day${iso === today ? ' today' : ''}`}>
                      <header><span>{weekday(d)}{iso === today ? ' · hoy' : ''}</span><b>{d.getUTCDate()}</b></header>
                      {free && <span className="pill s-ok" style={{ justifySelf: 'start' }}>Días gratis</span>}
                      {ev.length ? ev.map(x => <PubEvent key={x.id} x={x} onClick={() => jump(x.id)} />) : <span className="empty">Sin publicaciones</span>}
                    </div>
                  );
                })}
              </div>
            </>
          );
        })()}

        {tab === 'publicaciones' && (() => {
          const list = pubs.filter(x => filter === 'all'
            || (filter === 'pending' && (['IDEA', 'PROPOSAL', 'IN_PRODUCTION', 'DRAFT'] as PublicationStatus[]).includes(x.status))
            || (filter === 'issues' && (x.flag || x.status === 'PUBLISH_ERROR'))
            || x.status === filter);
          return (
            <>
              <div className="row-between">
                <div className="filters">{FILTERS.map(([k, l]) => <button key={k} className={`chip${filter === k ? ' on' : ''}`} onClick={() => setFilter(k)}>{l}</button>)}</div>
                <button className="btn primary" onClick={() => setEditing('new')}>Nueva publicación</button>
              </div>
              {editing && (
                <PublicationEditor key={editing} promotionId={p.id}
                  publication={editing === 'new' ? undefined : pubs.find(x => x.id === editing)}
                  onClose={() => setEditing(null)}
                  onSaved={(saved, message) => { setEditing(saved.id); setHl(saved.id); onMessage?.(message); }} />
              )}
              <div className="tbl-wrap"><table>
                <thead><tr><th>Fecha</th><th>Plataforma</th><th>Tipo</th><th>Pieza</th><th>Estado</th><th>Vía</th><th>Observaciones</th><th /></tr></thead>
                <tbody>
                  {list.map(x => {
                    const st = PUBLICATION_STATUS[x.status];
                    return (
                      <tr key={x.id} id={`row-${x.id}`} className={hl === x.id ? 'hl' : ''}>
                        <td className="nowrap mono">{dayLabel(parseDay(x.scheduled_on))} {hhmm(x.scheduled_time)}</td>
                        <td>{x.platform === 'TIKTOK' ? 'TikTok' : 'Instagram'}</td><td>{x.type}</td><td>{x.title}</td>
                        <td><Pill s={st.s} label={st.label} /></td><td>{x.route}</td>
                        <td>{x.flag ?? x.note ?? ''}</td>
                        <td><button className="btn" aria-label={`Editar ${x.title}`} onClick={() => setEditing(x.id)}>Editar</button></td>
                      </tr>
                    );
                  })}
                  {list.length === 0 && <tr><td colSpan={8}>Ninguna publicación con este filtro.</td></tr>}
                </tbody>
              </table></div>
              <p className="src">Una publicación solo pasa a «Publicada» cuando se confirma en la plataforma o en la vista semanal de Publer, nunca porque se haya generado el archivo.</p>
            </>
          );
        })()}

        {tab === 'tareas' && (
          <div className="tbl-wrap"><table>
            <thead><tr><th>Estación</th><th>Tarea</th><th>Estado</th><th>Agente</th><th>Detalle</th></tr></thead>
            <tbody>
              {data.tasks.filter(t => t.promotion_id === p.id || !t.promotion_id).map(t => (
                <tr key={t.id}>
                  <td className="nowrap">{data.stations.find(s => s.id === t.station_id)?.name}</td>
                  <td><button className="btn" style={{ padding: '3px 8px', textAlign: 'left' }} onClick={() => onTask(t.id)}>{t.title}</button></td>
                  <td><Pill s={t.status} /></td><td><AgentChip agent={t.agent} /></td>
                  <td>{t.status === 'RUNNING' ? `${Math.floor(t.progress)} %` : t.error ?? t.reason ?? t.result ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}

        {tab === 'recursos' && (<>
          <FolderRoles owner="promotion" id={p.id} onSaved={onMessage} />
          <div className="grid2">
            <div className="card"><h3>Google Drive · cositas artisticas</h3>
              <div className="tbl-wrap"><table><tbody>
                {p.resources.filter(r => r.kind === 'DRIVE').map((r, i) => <tr key={i}><td className="mono" style={{ fontSize: 12 }}>{r.location}</td><td>{r.description}</td></tr>)}
              </tbody></table></div>
              <p className="src">Solo se crean archivos nuevos dentro de la carpeta de la campaña. Nunca se borra ni se modifica nada.</p>
            </div>
            <div className="card"><h3>Planes y documentos</h3>
              {p.resources.filter(r => r.kind === 'DOC').map((r, i) => <div key={i}><b>{r.location}</b><p>{r.description}</p></div>)}
            </div>
          </div>
        </>)}

        {tab === 'automatizacion' && (
          <>
            <div className="card"><h3>Tareas programadas</h3>
              <div className="tbl-wrap"><table>
                <thead><tr><th>Nombre</th><th>Cuándo</th><th>Modelo</th><th>Dónde</th><th>Estado</th></tr></thead>
                <tbody>{p.automations.map((a, i) => (
                  <tr key={i}><td>{a.name}</td><td className="nowrap">{a.schedule}</td><td>Claude {a.model}</td>
                    <td>{a.runs_on === 'PC' ? 'PC de Raúl (Chrome activo)' : 'Nube'}</td><td><Pill s={a.status} /></td></tr>
                ))}</tbody>
              </table></div>
            </div>
            <div className="card"><h3>Vías de publicación, por orden de preferencia</h3>
              <div className="tbl-wrap"><table>
                <thead><tr><th>Vía</th><th>Estado</th><th>Notas</th></tr></thead>
                <tbody>{p.routes.map((r, i) => <tr key={i}><td>{r.name}</td><td><Pill s={r.status} label={r.label} /></td><td>{r.notes}</td></tr>)}</tbody>
              </table></div>
            </div>
            <div className="card"><h3>Elección de modelo por tipo de trabajo</h3>
              <div className="tbl-wrap"><table>
                <thead><tr><th>Trabajo</th><th>Base</th><th>Sube un nivel si…</th></tr></thead>
                <tbody>
                  <tr><td>Revisar correo, comprobar publicaciones, listar archivos, leer métricas</td><td>Haiku</td><td>Un correo pide dinero, datos o algo raro</td></tr>
                  <tr><td>Tanda semanal: piezas, textos, hashtags, spec de promo.py, programar, contactos</td><td>Sonnet</td><td>Falla dos veces; piezas de los días gratis; algo distinto de lo habitual</td></tr>
                  <tr><td>Estrategia, herramienta nueva, arreglar algo roto, balances, campaña nueva</td><td>Opus</td><td>—</td></tr>
                </tbody>
              </table></div>
              <p className="src">Local primero: si promo.py, FFmpeg o ImageMagick resuelven la tarea, no se usa IA.</p>
            </div>
          </>
        )}

        {tab === 'metricas' && (
          <div className="grid2">
            <div className="card"><h3>Métricas de la promoción</h3><p className="src">{p.metrics_source}</p>
              {['Alcance', 'Impresiones', 'Interacciones', 'Reproducciones', 'Guardados', 'Compartidos', 'Seguidores ganados', 'Clics en el enlace']
                .map(m => <div key={m} className="metric"><span>{m}</span><span className="v">Sin dato</span></div>)}
            </div>
            <div className="card"><h3>Cómo se usarán</h3>
              <ul><li>Comparar por formato, tema, día y hora dentro de la misma marca.</li><li>No sacar conclusiones con muestras pequeñas.</li>
                <li>Las conclusiones vuelven a la estación de investigación y quedan registradas.</li></ul>
              <p className="src">Las métricas son opcionales. SocialHub no inventa cifras: aparecerán cuando lleguen datos reales.</p>
            </div>
          </div>
        )}

        {tab === 'config' && (
          <div className="tbl-wrap"><table>
            <thead><tr><th>Parámetro</th><th>Valor</th><th>Origen</th></tr></thead>
            <tbody>{p.settings.map((s, i) => (
              <tr key={i}><td className="nowrap">{s.label}</td><td>{s.value}</td><td>{s.origin === 'BRAND' ? 'Heredado de la marca' : 'Propio de la promoción'}</td></tr>
            ))}</tbody>
          </table></div>
        )}
      </div>
    </section>
  );
}

function PubEvent({ x, onClick }: { x: Publication; onClick: () => void }) {
  const st = PUBLICATION_STATUS[x.status];
  return (
    <button className={`ev ${statusClass(st.s)}`} onClick={onClick}>
      <span className="t"><Icon name={STATUS[st.s].icon} />
        <span className="mono">{hhmm(x.scheduled_time)}</span> · {x.type}</span>
      <span className="x">{x.title}</span>
      <span className="t" style={{ fontWeight: 500 }}>{st.label}</span>
      {x.flag && <span className="f">{x.flag}</span>}
    </button>
  );
}
