import { useCallback, useEffect, useState } from 'react';
import { api, type AuthStatus } from './api';
import { Factory } from './Factory';
import { useFactory } from './useFactory';

export function App() {
  const [auth, setAuth] = useState<AuthStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  const check = useCallback(() => {
    api.status().then(setAuth).catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, []);
  useEffect(check, [check]);

  if (error) return <Centered><h1>No se puede conectar con SocialHub</h1><p>{error}</p><button className="btn primary" onClick={() => { setError(null); check(); }}>Reintentar</button></Centered>;
  if (!auth) return <Centered><p className="src">Cargando…</p></Centered>;
  if (auth.needsSetup || !auth.authenticated) return <Access status={auth} onDone={check} />;
  return <Authenticated onLoggedOut={check} />;
}

function Authenticated({ onLoggedOut }: { onLoggedOut: () => void }) {
  const { data, error, live, reload } = useFactory(onLoggedOut);
  if (error && !data) return <Centered><h1>No se pudo cargar la fábrica</h1><p>{error}</p><button className="btn primary" onClick={reload}>Reintentar</button></Centered>;
  if (!data) return <Centered><p className="src">Cargando la fábrica…</p></Centered>;
  return <Factory data={data} live={live} onRefresh={reload} onLogout={() => api.logout().finally(onLoggedOut)} />;
}

function Access({ status, onDone }: { status: AuthStatus; onDone: () => void }) {
  const setup = status.needsSetup;
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    if (setup && password.length < status.minLength) { setMessage(`La contraseña debe tener al menos ${status.minLength} caracteres.`); return; }
    if (setup && password !== repeat) { setMessage('Las dos contraseñas no coinciden.'); return; }
    setBusy(true);
    try {
      await (setup ? api.setup(password) : api.login(password));
      onDone();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'No se pudo entrar.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Centered>
      <form className="card access" onSubmit={submit}>
        <div className="mark"><i />SocialHub</div>
        <h1>{setup ? 'Crea tu contraseña' : 'Entra en SocialHub'}</h1>
        <p className="src">{setup
          ? `Es la primera vez que abres SocialHub. La contraseña protege la app en este ordenador. Mínimo ${status.minLength} caracteres.`
          : 'SocialHub solo se abre en este ordenador. La sesión dura 30 días en este navegador.'}</p>
        <label htmlFor="pw">Contraseña</label>
        <input id="pw" type="password" autoComplete={setup ? 'new-password' : 'current-password'} value={password}
          onChange={e => setPassword(e.target.value)} autoFocus />
        {setup && (<>
          <label htmlFor="pw2">Repite la contraseña</label>
          <input id="pw2" type="password" autoComplete="new-password" value={repeat} onChange={e => setRepeat(e.target.value)} />
        </>)}
        {message && <p className="error" role="alert">{message}</p>}
        <button className="btn primary" disabled={busy}>{setup ? 'Crear y entrar' : 'Entrar'}</button>
        {!setup && <p className="src">¿La has olvidado? Crea un archivo vacío llamado <code>reset-password</code> en la carpeta de datos de SocialHub y reinicia la aplicación.</p>}
      </form>
    </Centered>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return <main className="centered">{children}</main>;
}
