import { useEffect, useState } from 'react';
import { api } from './api';
import type { Autostart } from './api/types';

/**
 * Interruptor «Abrir SocialHub al iniciar sesión en Windows». Solo aparece donde se puede (Windows con SocialHub
 * instalado). Se abre en segundo plano, sin navegador: lo que se ve es el icono junto al reloj.
 */
export function AutostartSwitch({ card = false }: { card?: boolean }) {
  const [state, setState] = useState<Autostart | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { api.autostart().then(setState).catch(() => {}); }, []);
  if (!state?.available) return null;
  const body = (
    <p className="src">
      <label>
        <input type="checkbox" checked={state.enabled} onChange={e => {
          setError(null);
          api.setAutostart(e.target.checked).then(setState)
            .catch(err => setError(err instanceof Error ? err.message : String(err)));
        }} /> Abrir SocialHub al iniciar sesión en Windows
      </label>
      <br />Se abre en segundo plano, sin abrir el navegador: el icono junto al reloj indica que está corriendo.
      {error && <span className="error" role="alert"> {error}</span>}
    </p>
  );
  return card ? <div className="card form" aria-label="Arranque con Windows"><h3>Arranque con Windows</h3>{body}</div> : body;
}
