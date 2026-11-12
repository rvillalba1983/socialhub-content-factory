import { useEffect, useState } from 'react';
import { api } from './api';
import type { Platform, PublerAccount } from './model';

const PLATFORMS: [Platform, string, string][] = [
  ['INSTAGRAM', 'Instagram', 'Litoques'],
  ['TIKTOK', 'TikTok', 'Lito · Litoques · El DeLito'],
];

/**
 * Cuenta de Publer que usa esta marca en cada red. Claude programa en Publer con Chrome y necesita el nombre de la cuenta
 * tal como sale en Publer. Dos marcas pueden indicar la misma (la de TikTok la usan las dos).
 */
export function PublerCard({ brandId, onSaved }: { brandId: string; onSaved?: (message: string) => void }) {
  const [names, setNames] = useState<Partial<Record<Platform, string>>>({});
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoaded(false);
    api.brandPublerAccounts(brandId)
      .then(a => { if (!cancelled) { setNames(Object.fromEntries(a.items.map((x: PublerAccount) => [x.platform, x.account_name]))); setLoaded(true); } })
      .catch(e => { if (!cancelled) setError(e instanceof Error ? e.message : String(e)); });
    return () => { cancelled = true; };
  }, [brandId]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const items = PLATFORMS.filter(([p]) => (names[p] ?? '').trim()).map(([platform]) => ({ platform, account_name: names[platform]!.trim() }));
      const saved = await api.saveBrandPublerAccounts(brandId, items);
      setNames(Object.fromEntries(saved.items.map((x: PublerAccount) => [x.platform, x.account_name])));
      onSaved?.('Cuentas de Publer guardadas');
    } catch (err) { setError(err instanceof Error ? err.message : String(err)); } finally { setBusy(false); }
  };

  return (
    <div className="card form" data-card="publer" aria-label="Cuenta en Publer">
      <h3>Cuenta en Publer</h3>
      <p className="src">Primera vía de publicación. Escribe el nombre de la cuenta tal como sale en Publer: Claude programa ahí con Chrome.
        Dos marcas pueden usar la misma (la de TikTok la comparten). Sin la cuenta, las publicaciones de esa red quedan retenidas.</p>
      {!loaded && !error && <p className="src">Cargando…</p>}
      {loaded && (
        <form onSubmit={e => void save(e)} aria-label="Cuenta de Publer de la marca">
          {PLATFORMS.map(([platform, label, example]) => (
            <label key={platform} className="field"><span>Cuenta de {label} en Publer</span>
              <input aria-label={`Cuenta de Publer para ${label}`} maxLength={120} placeholder={`Por ejemplo: «${example}»`}
                value={names[platform] ?? ''} onChange={e => setNames({ ...names, [platform]: e.target.value })} /></label>
          ))}
          <div className="form-actions"><button className="btn primary" disabled={busy}>Guardar cuentas</button></div>
        </form>
      )}
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
