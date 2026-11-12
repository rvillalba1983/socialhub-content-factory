import { useEffect, useState } from 'react';
import { api } from './api';
import type { InstagramAccount, InstagramCheck, InstagramStatus } from './model';
import { Pill } from './ui';

/**
 * API oficial de Instagram: la clave de «usuario del sistema» del porfolio de Meta (no caduca, vale para todas las
 * marcas y se guarda en un archivo de este ordenador, nunca en la base de datos). Va en «Agentes y herramientas».
 */
export function InstagramKeyCard({ onChanged }: { onChanged?: () => void }) {
  const [status, setStatus] = useState<InstagramStatus | null>(null);
  const [check, setCheck] = useState<InstagramCheck | null>(null);
  const [key, setKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try { setStatus(await api.instagram()); } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };
  useEffect(() => { void load(); }, []);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try { await action(); await load(); onChanged?.(); return true; }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); return false; } finally { setBusy(false); }
  };

  return (
    <section className="form" data-card="instagram" aria-label="Instagram · API oficial">
      <h4>Clave de la API oficial</h4>
      <p className="src">
        Clave de «usuario del sistema» del porfolio de Meta (permisos instagram_basic e instagram_content_publish). No caduca y
        vale para todas las marcas. Se guarda solo en este ordenador.
      </p>
      {!status && !error && <p className="src">Cargando…</p>}
      {status && (
        <div className="status-row">
          {status.configured ? <Pill s="OK" label={`Clave ${status.key_hint ?? ''}`} /> : <Pill s="BLOCKED" label="Sin clave" />}
          {status.configured && (
            <>
              <button type="button" className="btn" disabled={busy}
                onClick={() => void run(async () => { setCheck(await api.checkInstagram()); })}>Comprobar clave</button>
              <button type="button" className="btn" disabled={busy}
                onClick={() => void run(async () => { await api.deleteInstagramKey(); setCheck(null); })}>Borrar clave</button>
            </>
          )}
        </div>
      )}
      <form className="file-add" onSubmit={e => { e.preventDefault(); void run(async () => { await api.saveInstagramKey(key.trim()); setCheck(null); }).then(ok => { if (ok) setKey(''); }); }}>
        <input type="password" autoComplete="off" aria-label="Clave de la API de Instagram"
          placeholder={status?.configured ? 'Pegar otra clave' : 'Pega aquí la clave del usuario del sistema'} value={key} onChange={e => setKey(e.target.value)} />
        <button className="btn" disabled={busy || key.trim().length < 10}>Comprobar y guardar</button>
      </form>
      {check && (
        <div role="status" aria-label="Resultado de la comprobación">
          <p>{check.ok ? <Pill s="OK" label={`Clave válida${check.name ? ` · ${check.name}` : ''}`} /> : <Pill s="FAILED" label="Hay algo que revisar" />}</p>
          {check.error && <p className="error">{check.error}</p>}
          {check.brands.length === 0 && !check.error && <p className="src">Ninguna marca tiene aún su cuenta de Instagram (se pone en la ficha de la marca).</p>}
          <ul>
            {check.brands.map(b => (
              <li key={b.brand_id}>
                <b>{b.brand_name}</b>{b.username ? ` · @${b.username}` : ''}
                {b.publishing_used != null && <> · {b.publishing_used} de {b.publishing_total} publicaciones en 24 h</>}
                {b.error && <span className="error"> · {b.error}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
      {error && <p className="error" role="alert">{error}</p>}
    </section>
  );
}

/** Campo de la ficha de la marca: el identificador de su cuenta de Instagram, que se comprueba con Meta y enseña el @usuario. */
export function InstagramAccountCard({ brandId, onSaved }: { brandId: string; onSaved?: (message: string) => void }) {
  const [account, setAccount] = useState<InstagramAccount | null>(null);
  const [id, setId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.brandInstagramAccount(brandId).then(a => { if (!cancelled) { setAccount(a); setId(a.ig_user_id ?? ''); } })
      .catch(e => { if (!cancelled) setError(e instanceof Error ? e.message : String(e)); });
    return () => { cancelled = true; };
  }, [brandId]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const saved = await api.saveBrandInstagramAccount(brandId, id.trim());
      setAccount(saved);
      setId(saved.ig_user_id ?? '');
      onSaved?.(saved.username ? `Cuenta de Instagram guardada · @${saved.username}` : 'Cuenta de Instagram quitada');
    } catch (err) { setError(err instanceof Error ? err.message : String(err)); } finally { setBusy(false); }
  };

  return (
    <form className="card form" data-card="instagram-account" aria-label="Instagram (API oficial)" onSubmit={save}>
      <h3>Instagram · API oficial</h3>
      <p className="src">
        Primera vía para publicar en Instagram. La clave se pega en «Agentes y herramientas». Meta no deja listar las cuentas:
        pega aquí el identificador de la de esta marca (un número largo) y SocialHub lo comprueba.
      </p>
      <label className="field"><span>Cuenta de Instagram (API oficial)</span>
        <input inputMode="numeric" autoComplete="off" aria-label="Cuenta de Instagram (API oficial)" placeholder="Identificador de la cuenta (número largo)"
          value={id} onChange={e => setId(e.target.value)} /></label>
      {account?.username && <p><Pill s="OK" label={`@${account.username}`} /></p>}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="form-actions">
        <button className="btn primary" disabled={busy || id.trim() === (account?.ig_user_id ?? '')}>Comprobar y guardar</button>
      </div>
    </form>
  );
}
