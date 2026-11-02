import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { dateTime, type PieceSpecList, type Promotion } from './model';

/**
 * Encargar piezas (lo que hacía promo.py) a partir de un spec.json. Se listan los de la carpeta vigilada de la
 * promoción («SocialHub/encargos» dentro de su primera carpeta); también se puede indicar otra ruta de sus carpetas.
 */
export function PieceOrders({ promotions, onOrdered, onTask }: {
  promotions: Promotion[]; onOrdered: (message: string) => void; onTask: (id: string) => void;
}) {
  const withFolder = promotions.filter(p => p.resources.some(r => r.kind === 'DRIVE'));
  const [promo, setPromo] = useState(withFolder[0]?.id ?? '');
  const [list, setList] = useState<PieceSpecList | null>(null);
  const [path, setPath] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!promo) return;
    try { setList(await api.pieceSpecs(promo)); setError(null); }
    catch (e) { setList(null); setError(e instanceof Error ? e.message : String(e)); }
  }, [promo]);
  useEffect(() => { load(); }, [load]);

  const order = async (specPath: string) => {
    setBusy(true);
    setError(null);
    try {
      const o = await api.orderPieces(promo, specPath);
      onOrdered(`Piezas encargadas · ${specPath.split(/[\\/]/).pop()}`);
      setPath('');
      await load();
      onTask(o.task_id);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally { setBusy(false); }
  };

  if (withFolder.length === 0) {
    return <p className="src">Para encargar piezas, añade a una promoción una carpeta en «Recursos» (Marcas → editar promoción).</p>;
  }
  return (
    <div className="pieces">
      <label className="field"><span>Promoción</span>
        <select value={promo} onChange={e => setPromo(e.target.value)}>
          {withFolder.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </label>
      {list?.folder && <p className="src">Carpeta vigilada: <span className="mono">{list.folder}</span>. Los spec.json nuevos que aparezcan ahí se encargan solos.</p>}
      {list && list.items.length === 0 && <p className="src">No hay spec.json en la carpeta vigilada.</p>}
      {list?.items.map(s => (
        <div key={s.path} className="qrow spec-row">
          <span>{s.name}<small>{dateTime(s.modified_at)}</small></span>
          {s.task_id
            ? <button className="btn" onClick={() => onTask(s.task_id!)}>Ver tarea</button>
            : <button className="btn primary" disabled={busy} onClick={() => order(s.path)}>Encargar</button>}
        </div>
      ))}
      <form className="spec-path" onSubmit={e => { e.preventDefault(); if (path.trim()) order(path.trim()); }}>
        <label className="field"><span>Otro spec.json (ruta completa, dentro de las carpetas de la promoción)</span>
          <input value={path} placeholder="G:\Mi unidad\…\spec.json" onChange={e => setPath(e.target.value)} />
        </label>
        <button className="btn" disabled={busy || !path.trim()}>Encargar</button>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
