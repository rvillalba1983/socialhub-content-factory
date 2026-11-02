import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import { REJECTION_REASONS, dateTime, type EditorialMemory } from './model';

const reasonLabel = (r: string) => REJECTION_REASONS.find(([k]) => k === r)?.[1] ?? r;

/** Memoria editorial de la marca: decisiones (editables), rechazos y correcciones. Va en cada encargo a Claude. */
export function MemoryCard({ brandId, onSaved }: { brandId: string; onSaved?: (message: string) => void }) {
  const [memory, setMemory] = useState<EditorialMemory | null>(null);
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => api.editorialMemory(brandId).then(setMemory)
    .catch(e => setError(e instanceof Error ? e.message : String(e))), [brandId]);
  useEffect(() => { void load(); }, [load]);

  const act = async (action: () => Promise<unknown>, message: string) => {
    setError(null);
    try { await action(); await load(); onSaved?.(message); } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  return (
    <div className="card form" aria-label="Memoria editorial">
      <h3>Memoria editorial</h3>
      <p className="src">Va en cada encargo a Claude: las decisiones, los últimos rechazos y correcciones, y lo publicado en las últimas 8 semanas.</p>
      <h4>Decisiones editoriales</h4>
      {memory?.decisions.length === 0 && <p className="src">Ninguna todavía.</p>}
      {memory?.decisions.map(d => (
        <div key={d.id} className="file-row">
          <span>{d.text}</span><small className="src nowrap">{dateTime(d.created_at)}</small><span />
          <button type="button" className="ibtn" aria-label={`Borrar «${d.text}»`} title="Borrar"
            onClick={() => void act(() => api.deleteDecision(brandId, d.id), 'Decisión borrada')}><Icon name="x" /></button>
        </div>
      ))}
      <form className="file-add" onSubmit={e => { e.preventDefault(); void act(() => api.addDecision(brandId, text.trim()), 'Decisión añadida').then(() => setText('')); }}>
        <input aria-label="Nueva decisión" placeholder="Por ejemplo: máximo dos reels por semana" maxLength={1000} value={text} onChange={e => setText(e.target.value)} />
        <button className="btn" disabled={!text.trim()}>Añadir</button>
      </form>

      <h4>Rechazos recientes</h4>
      {memory?.rejections.length === 0 && <p className="src">Ninguno.</p>}
      <ul className="memory-list">
        {memory?.rejections.map((r, i) => (
          <li key={i}><b>{r.kind === 'TEXT' ? 'Texto descartado' : 'Publicación cancelada'}</b> · {r.title} · {reasonLabel(r.reason)}
            {r.note && <> · «{r.note}»</>} <small className="src">{dateTime(r.created_at)}</small></li>
        ))}
      </ul>

      <h4>Correcciones a textos de Claude</h4>
      {memory?.corrections.length === 0 && <p className="src">Ninguna.</p>}
      <ul className="memory-list">
        {memory?.corrections.map((c, i) => (
          <li key={i}><span className="src">Claude:</span> {c.before_caption ?? '—'}<br /><span className="src">Tú:</span> {c.after_caption ?? '—'}
            {' '}<small className="src">{dateTime(c.created_at)}</small></li>
        ))}
      </ul>
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
