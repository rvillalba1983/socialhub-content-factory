import { useEffect, useState } from 'react';
import { api } from './api';
import type { Platform, PubloraAccount, PubloraStatus } from './model';
import { Pill } from './ui';

const PLATFORMS: [Platform, string, string][] = [['INSTAGRAM', 'Instagram', 'instagram'], ['TIKTOK', 'TikTok', 'tiktok']];

/**
 * Publora, segunda vía de publicación: la clave de la API (vale para todas las marcas y se guarda en un archivo de
 * este ordenador, nunca en la base de datos) y la cuenta de Publora que usa esta marca en cada red.
 */
export function PubloraCard({ brandId, onSaved }: { brandId: string; onSaved?: (message: string) => void }) {
  const [status, setStatus] = useState<PubloraStatus | null>(null);
  const [chosen, setChosen] = useState<Partial<Record<Platform, string>>>({});
  const [key, setKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      const [s, a] = await Promise.all([api.publora(), api.brandPubloraAccounts(brandId)]);
      setStatus(s);
      setChosen(Object.fromEntries(a.items.map((x: PubloraAccount) => [x.platform, x.connection_id])));
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };
  useEffect(() => { void load(); }, [brandId]);

  const run = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true);
    setError(null);
    try { await action(); await load(); onSaved?.(message); return true; }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); return false; } finally { setBusy(false); }
  };

  const saveAccounts = (e: React.FormEvent) => {
    e.preventDefault();
    const items = PLATFORMS.filter(([p]) => chosen[p]).map(([platform]) => ({ platform, connection_id: chosen[platform]! }));
    void run(() => api.saveBrandPubloraAccounts(brandId, items), 'Cuentas de Publora guardadas');
  };

  return (
    <div className="card form" aria-label="Publora">
      <h3>Publora</h3>
      <p className="src">Segunda vía de publicación, si falla Publer. La clave vale para todas las marcas y se guarda solo en este ordenador.</p>
      {!status && !error && <p className="src">Cargando…</p>}
      {status && (
        <div className="status-row">
          {status.configured ? <Pill s="OK" label={`Clave ${status.key_hint ?? ''}`} /> : <Pill s="BLOCKED" label="Sin clave" />}
          {status.configured && (
            <button type="button" className="btn" disabled={busy}
              onClick={() => void run(() => api.deletePubloraKey(), 'Clave de Publora borrada')}>Borrar clave</button>
          )}
        </div>
      )}
      {status?.error && <p className="error">{status.error}</p>}
      <form className="file-add" onSubmit={e => { e.preventDefault(); void run(() => api.savePubloraKey(key.trim()), 'Clave de Publora guardada').then(ok => { if (ok) setKey(''); }); }}>
        <input type="password" autoComplete="off" aria-label="Clave de la API de Publora"
          placeholder={status?.configured ? 'Pegar otra clave' : 'Pega aquí la clave (Settings › API Keys)'} value={key} onChange={e => setKey(e.target.value)} />
        <button className="btn" disabled={busy || key.trim().length < 10}>Comprobar y guardar</button>
      </form>
      {status?.configured && (
        <form onSubmit={saveAccounts} aria-label="Cuentas de Publora de la marca">
          {PLATFORMS.map(([platform, label, prefix]) => (
            <label key={platform} className="field"><span>Cuenta de {label}</span>
              <select aria-label={`Cuenta de Publora para ${label}`} value={chosen[platform] ?? ''}
                onChange={e => setChosen({ ...chosen, [platform]: e.target.value || undefined })}>
                <option value="">Sin cuenta (Publora no se usa en {label})</option>
                {status.connections.filter(c => c.platform === prefix).map(c => (
                  <option key={c.id} value={c.id}>{c.label}{c.usable ? '' : ' (hay que volver a conectarla en Publora)'}</option>
                ))}
              </select></label>
          ))}
          <div className="form-actions"><button className="btn primary" disabled={busy}>Guardar cuentas</button></div>
        </form>
      )}
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
