import { useEffect, useState } from 'react';
import { api } from './api';
import type { InstagramAccount, InstagramCheck, InstagramDetectedAccount, InstagramStatus } from './model';
import { Pill } from './ui';
import { WhereHelp } from './WhereHelp';

const DATE = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'numeric', year: 'numeric' });
const date = (iso: string) => DATE.format(new Date(iso));

/**
 * API oficial de Instagram, en «Agentes y herramientas». Dos formas de tener la clave:
 * - «Conectar con Instagram»: inicio de sesión de Meta (OAuth). Raúl guarda una vez el App ID y el App Secret de su app y
 *   SocialHub detecta solo la cuenta de cada marca. La conexión dura unos 60 días y se renueva con un clic.
 * - La clave de «usuario del sistema» pegada a mano: no caduca y sirve de alternativa.
 * Todo se guarda en archivos de este ordenador, nunca en la base de datos.
 */
export function InstagramKeyCard({ onChanged }: { onChanged?: () => void }) {
  const [status, setStatus] = useState<InstagramStatus | null>(null);
  const [check, setCheck] = useState<InstagramCheck | null>(null);
  const [accounts, setAccounts] = useState<InstagramDetectedAccount[]>([]);
  const [key, setKey] = useState('');
  const [appId, setAppId] = useState('');
  const [appSecret, setAppSecret] = useState('');
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      const s = await api.instagram();
      setStatus(s);
      setAppId(current => current || s.app_id || '');
      if (s.configured) {
        try { setAccounts((await api.instagramAccounts()).items); } catch { setAccounts([]); }
      } else setAccounts([]);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };
  useEffect(() => { void load(); }, []);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try { await action(); await load(); onChanged?.(); return true; }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); return false; } finally { setBusy(false); }
  };

  const connection = status?.connection;
  const appReady = !!status?.app_id && !!status.app_secret_saved;
  const expiring = !!connection && !connection.expired && connection.days_left <= 7;

  const copyRedirect = async () => {
    if (!status?.redirect_uri) return;
    try { await navigator.clipboard.writeText(status.redirect_uri); setCopied(true); window.setTimeout(() => setCopied(false), 2500); }
    catch { setError('No se pudo copiar: selecciona la dirección y cópiala con Ctrl + C'); }
  };

  return (
    <section className="form" data-card="instagram" aria-label="Instagram · API oficial">
      <h4>Conectar con Instagram</h4>
      <p className="src">
        SocialHub te lleva a Meta, das tu permiso y vuelves: sin crear ningún usuario del sistema. Encuentra solo la cuenta de
        Instagram de cada marca. La conexión dura unos 60 días y se renueva con un clic. Todo se guarda solo en este ordenador.
      </p>
      {!status && !error && <p className="src">Cargando…</p>}

      <form className="file-add" onSubmit={e => {
        e.preventDefault();
        void run(async () => { await api.saveInstagramApp(appId.trim(), appSecret.trim()); }).then(ok => { if (ok) setAppSecret(''); });
      }}>
        <input inputMode="numeric" autoComplete="off" aria-label="App ID de Meta" placeholder="App ID de tu app de Meta"
          value={appId} onChange={e => setAppId(e.target.value)} />
        <span />
        <input type="password" autoComplete="off" aria-label="App Secret de Meta"
          placeholder={status?.app_secret_saved ? 'App Secret guardado (pega otro para cambiarlo)' : 'App Secret de tu app de Meta'}
          value={appSecret} onChange={e => setAppSecret(e.target.value)} />
        <button className="btn" disabled={busy || appId.trim().length < 5 || (!status?.app_secret_saved && appSecret.trim().length < 16)}>
          Guardar la app</button>
      </form>
      <WhereHelp topic="meta-app" />

      {status?.redirect_uri && (
        <div className="file-add">
          <input readOnly aria-label="Dirección de redireccionamiento para Meta" value={status.redirect_uri}
            onFocus={e => e.currentTarget.select()} />
          <button type="button" className="btn" onClick={() => void copyRedirect()}>{copied ? 'Copiada' : 'Copiar'}</button>
        </div>
      )}
      <WhereHelp topic="meta-redirect-uri" />

      {status && (
        <div className="status-row">
          {connection && !connection.expired && (
            <span>
              <Pill s="OK" label={`Conectado como ${connection.usernames.length > 0 ? connection.usernames.map(u => `@${u}`).join(', ') : (connection.name ?? 'Meta')}`} />
              {' '}· caduca el {date(connection.expires_at)}
            </span>
          )}
          {connection?.expired && <Pill s="FAILED" label={`Conexión caducada el ${date(connection.expires_at)}`} />}
          {!connection && <Pill s="BLOCKED" label="Sin conectar" />}
          <button type="button" className="btn primary" disabled={busy || !appReady}
            title={appReady ? undefined : 'Primero guarda el App ID y el App Secret'}
            onClick={() => void run(async () => {
              const { authorization_url } = await api.startInstagramOAuth();
              window.open(authorization_url, '_self');
            })}>{connection ? 'Renovar conexión' : 'Conectar con Instagram'}</button>
          {connection && (
            <button type="button" className="btn" disabled={busy}
              onClick={() => void run(async () => { await api.disconnectInstagram(); setCheck(null); })}>Desconectar</button>
          )}
        </div>
      )}
      {expiring && connection && (
        <p className="error" role="status">
          La conexión caduca {connection.days_left === 0 ? 'hoy' : `en ${connection.days_left} ${connection.days_left === 1 ? 'día' : 'días'}`}:
          pulsa «Renovar conexión».{status?.system_key ? ' Mientras tanto se usa la clave de usuario del sistema.' : ''}
        </p>
      )}
      {accounts.length > 0 && (
        <div role="group" aria-label="Cuentas de Instagram encontradas">
          <p className="src">Cuentas encontradas en tus páginas de Facebook (la de cada marca se elige en su ficha):</p>
          <ul>
            {accounts.map(a => (
              <li key={a.ig_user_id}><b>@{a.username}</b> · {a.page_name}{a.brand_id ? ` · asignada a ${a.brand_id}` : ' · sin marca'}</li>
            ))}
          </ul>
        </div>
      )}

      <h4>Clave de usuario del sistema (alternativa)</h4>
      <p className="src">
        Clave de «usuario del sistema» del porfolio de Meta (permisos instagram_basic e instagram_content_publish). No caduca y
        vale para todas las marcas. Se usa cuando no hay una conexión vigente.
      </p>
      {status && (
        <div className="status-row">
          {status.system_key ? <Pill s="OK" label={`Clave ${status.key_hint ?? ''}`} /> : <Pill s="BLOCKED" label="Sin clave" />}
          {status.configured && (
            <button type="button" className="btn" disabled={busy}
              onClick={() => void run(async () => { setCheck(await api.checkInstagram()); })}>Comprobar clave</button>
          )}
          {status.system_key && (
            <button type="button" className="btn" disabled={busy}
              onClick={() => void run(async () => { await api.deleteInstagramKey(); setCheck(null); })}>Borrar clave</button>
          )}
        </div>
      )}
      <form className="file-add" onSubmit={e => { e.preventDefault(); void run(async () => { await api.saveInstagramKey(key.trim()); setCheck(null); }).then(ok => { if (ok) setKey(''); }); }}>
        <input type="password" autoComplete="off" aria-label="Clave de la API de Instagram"
          placeholder={status?.system_key ? 'Pegar otra clave' : 'Pega aquí la clave del usuario del sistema'} value={key} onChange={e => setKey(e.target.value)} />
        <button className="btn" disabled={busy || key.trim().length < 10}>Comprobar y guardar</button>
      </form>
      <WhereHelp topic="meta-token" />
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
  const [detected, setDetected] = useState<InstagramDetectedAccount[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Con una conexión (o la clave del sistema), las cuentas de tus páginas de Facebook se pueden elegir de una lista
  useEffect(() => {
    let cancelled = false;
    api.instagramAccounts().then(l => { if (!cancelled) setDetected(l.items); }).catch(() => { if (!cancelled) setDetected([]); });
    return () => { cancelled = true; };
  }, [brandId]);

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
        Primera vía para publicar en Instagram. Con «Conectar con Instagram» (en «Agentes y herramientas») este campo se rellena
        solo cuando el @usuario de la marca coincide con el de una de tus cuentas; si no, elige la cuenta de la lista o pega a mano
        su identificador (un número largo). SocialHub lo comprueba con Meta.
      </p>
      {detected.length > 0 && (
        <label className="field"><span>Elegir de tus cuentas de Instagram</span>
          <select aria-label="Elegir de tus cuentas de Instagram" value={detected.some(a => a.ig_user_id === id) ? id : ''}
            onChange={e => { if (e.target.value) setId(e.target.value); }}>
            <option value="">— Elige una cuenta —</option>
            {detected.map(a => (
              <option key={a.ig_user_id} value={a.ig_user_id}>
                @{a.username} · {a.page_name}{a.brand_id && a.brand_id !== brandId ? ` (ya es de ${a.brand_id})` : ''}
              </option>
            ))}
          </select></label>
      )}
      <label className="field"><span>Cuenta de Instagram (API oficial)</span>
        <input inputMode="numeric" autoComplete="off" aria-label="Cuenta de Instagram (API oficial)" placeholder="Identificador de la cuenta (número largo)"
          value={id} onChange={e => setId(e.target.value)} /></label>
      <WhereHelp topic="instagram-account-id" />
      {account?.username && <p><Pill s="OK" label={`@${account.username}`} /></p>}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="form-actions">
        <button className="btn primary" disabled={busy || id.trim() === (account?.ig_user_id ?? '')}>Comprobar y guardar</button>
      </div>
    </form>
  );
}
