import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api';
import type { Snapshot } from './model';

/**
 * Carga el anillo desde la API y lo recarga cuando el backend avisa de un cambio (SSE).
 * El estado sale siempre del backend: la interfaz no lo deduce.
 */
export function useFactory() {
  const [data, setData] = useState<Snapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState(false);
  const timer = useRef<number | undefined>(undefined);

  const reload = useCallback(async () => {
    try {
      setData(await api.factory());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    reload();
    if (typeof EventSource === 'undefined') return;
    const source = new EventSource('/api/events');
    source.addEventListener('hello', () => setLive(true));
    source.addEventListener('factory-changed', () => {
      window.clearTimeout(timer.current);
      timer.current = window.setTimeout(reload, 250);
    });
    source.onerror = () => setLive(false);
    return () => { source.close(); window.clearTimeout(timer.current); };
  }, [reload]);

  return { data, error, live, reload };
}
