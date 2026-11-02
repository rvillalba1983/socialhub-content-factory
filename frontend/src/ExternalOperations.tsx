import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import { dateTime, type ExternalOperation, type ExternalOperationPage, type OperationOutcome } from './model';
import { Pill } from './ui';

const PAGE = 50;

const SYSTEMS: Record<string, string> = { postgres: 'PostgreSQL', drive: 'Google Drive', publer: 'Publer', tiktok: 'TikTok', instagram: 'Instagram' };
const systemLabel = (s: string) => SYSTEMS[s] ?? s;

/**
 * Registro de las operaciones con sistemas externos (PostgreSQL, Drive, Publer, APIs…): qué se hizo, cuánto tardó
 * y, si falló, por qué y con qué identificador de traza buscarlo en los registros.
 */
export function ExternalOperations({ onClose }: { onClose: () => void }) {
  const [outcome, setOutcome] = useState<OperationOutcome | undefined>(undefined);
  const [system, setSystem] = useState('');
  const [offset, setOffset] = useState(0);
  const [page, setPage] = useState<ExternalOperationPage | null>(null);
  const [systems, setSystems] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const result = await api.externalOperations({ system: system || undefined, outcome, offset, limit: PAGE });
      setPage(result);
      setSystems(prev => [...new Set([...prev, ...result.items.map(o => o.system)])].sort());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [system, outcome, offset]);

  useEffect(() => { load(); }, [load]);

  const filter = (o: OperationOutcome | undefined) => { setOutcome(o); setOffset(0); };

  return (
    <section className="cc" aria-label="Operaciones externas">
      <div className="cc-in">
        <div><button className="btn back" onClick={onClose}><Icon name="back" />Volver al mapa</button></div>
        <div className="cc-head">
          <div>
            <div className="eyebrow">Observabilidad</div>
            <h1>Operaciones externas</h1>
            <p>Cada llamada de SocialHub a un sistema externo, con su resultado. Se guardan 30 días.</p>
          </div>
          <button className="btn" onClick={load}>Actualizar</button>
        </div>

        <div className="filters ops-filters">
          {([[undefined, 'Todas'], ['FAILURE', 'Fallidas'], ['SUCCESS', 'Correctas']] as const).map(([o, l]) => (
            <button key={l} className={`chip${outcome === o ? ' on' : ''}`} onClick={() => filter(o)}>{l}</button>
          ))}
          <label className="src" htmlFor="ops-system">Sistema</label>
          <select id="ops-system" className="agent-sel" value={system} onChange={e => { setSystem(e.target.value); setOffset(0); }}>
            <option value="">Todos</option>
            {systems.map(s => <option key={s} value={s}>{systemLabel(s)}</option>)}
          </select>
        </div>

        {error && <p className="error" role="alert">No se pudo cargar el registro: {error}</p>}

        <div className="tbl-wrap"><table>
          <thead><tr><th>Cuándo</th><th>Sistema</th><th>Operación</th><th>Resultado</th><th>Duración</th><th>Detalle</th></tr></thead>
          <tbody>
            {page?.items.map(o => (
              <Row key={o.id} o={o} open={open === o.id} onToggle={() => setOpen(open === o.id ? null : o.id)} />
            ))}
            {page && page.items.length === 0 && <tr><td colSpan={6}>No hay operaciones con este filtro.</td></tr>}
            {!page && !error && <tr><td colSpan={6}>Cargando…</td></tr>}
          </tbody>
        </table></div>

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

function Row({ o, open, onToggle }: { o: ExternalOperation; open: boolean; onToggle: () => void }) {
  const failed = o.outcome === 'FAILURE';
  return (
    <>
      <tr className={failed ? 'op-fail' : ''}>
        <td className="nowrap mono">{dateTime(o.started_at)}</td>
        <td>{systemLabel(o.system)}</td>
        <td className="mono">{o.operation}</td>
        <td><Pill s={failed ? 'FAILED' : 'OK'} label={failed ? 'Fallida' : 'Correcta'} /></td>
        <td className="nowrap mono">{o.duration_ms} ms</td>
        <td>
          {(failed || o.detail || o.trace_id)
            ? <button className="btn" style={{ padding: '3px 8px' }} aria-expanded={open} onClick={onToggle}>{open ? 'Ocultar' : 'Ver'}</button>
            : '—'}
        </td>
      </tr>
      {open && (
        <tr className="op-detail"><td colSpan={6}>
          <dl className="kv">
            {o.error_message && <><dt>Error</dt><dd style={{ color: 'var(--err)' }}>{o.error_message}</dd></>}
            {o.error_type && <><dt>Tipo</dt><dd className="mono">{o.error_type}</dd></>}
            {o.detail && <><dt>Detalle</dt><dd className="mono">{o.detail}</dd></>}
            {o.trace_id && <><dt>Traza</dt><dd className="mono">{o.trace_id} <span className="src">(búscala en los registros de SocialHub)</span></dd></>}
          </dl>
        </td></tr>
      )}
    </>
  );
}
