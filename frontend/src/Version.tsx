import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import type { AppVersion, UpdateStatus } from './api/types';

/** Fecha y hora de compilación en la zona del PC («15 nov 2026, 11:30»). */
export function buildDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('es-ES', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/**
 * Versión de SocialHub junto al nombre, discreta: «1.2.7 · a1b2c3d». Al pulsarla enseña el detalle
 * (versión, commit, fecha de compilación y modo) para saber qué hay instalado en este PC.
 */
export function VersionBadge({ demo }: { demo: boolean }) {
  const [info, setInfo] = useState<AppVersion | null>(null);
  const [update, setUpdate] = useState<UpdateStatus | null>(null);
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLSpanElement>(null);
  useEffect(() => { api.appVersion().then(setInfo).catch(() => {}); }, []);
  // Al abrir: el servidor mira GitHub como mucho una vez al día. Sin red o sin releases no pasa nada
  useEffect(() => { api.updateStatus().then(setUpdate).catch(() => {}); }, []);
  useEffect(() => {
    if (!open) return;
    const close = (e: Event) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !box.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', close); };
  }, [open]);
  if (!info) return null;
  return (
    <span className="ver" ref={box}>
      <button className="ver-btn mono" aria-expanded={open} aria-haspopup="dialog"
        title={`SocialHub ${info.version}, compilado el ${buildDate(info.build_time)}. Pulsa para ver el detalle.`}
        onClick={() => setOpen(!open)}>{info.version} · {info.commit}</button>
      {update?.state === 'UPDATE_AVAILABLE' && (
        <button type="button" className="ver-new" aria-haspopup="dialog" onClick={() => setOpen(true)}
          title={update.message}>Nueva versión {update.latest_version}</button>
      )}
      {open && (
        <div className="ver-pop" role="dialog" aria-label="Versión de SocialHub">
          <dl>
            <dt>Versión</dt><dd className="mono">{info.version}</dd>
            <dt>Commit</dt><dd className="mono">{info.commit}</dd>
            <dt>Compilada el</dt><dd>{buildDate(info.build_time)}</dd>
            <dt>Modo</dt><dd>{demo ? 'Demo (datos de ejemplo)' : 'Real'}</dd>
            <dt>Instalada</dt><dd className="mono">{update?.installed_version ?? info.version}</dd>
            <dt>Disponible</dt>
            <dd className="mono">{update?.latest_version ?? '—'}</dd>
          </dl>
          {update?.state === 'UPDATE_AVAILABLE' ? (
            <p className="src" role="status">
              {update.message}.{' '}
              {update.installer_url && <><a href={update.installer_url} rel="noreferrer">Descargar el instalador</a>{' · '}</>}
              {update.release_url && <a href={update.release_url} target="_blank" rel="noreferrer">Ver la versión en GitHub</a>}
              <br />Instalar sigue siendo a mano: cierra SocialHub con «Cerrar» y ejecuta «Instalar SocialHub (con registro)».
            </p>
          ) : (
            <p className="src">{update ? (update.state === 'UP_TO_DATE' ? update.message : update.message) : 'No hay información de versiones nuevas'}</p>
          )}
          <p className="src">Si tras instalar una versión nueva sigue saliendo la de antes, ciérrala con «Cerrar» y ábrela de nuevo.</p>
        </div>
      )}
    </span>
  );
}
