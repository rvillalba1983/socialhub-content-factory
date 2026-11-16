import { useEffect, useState } from 'react';
import { api } from './api';
import type { Publication, PublicationFile, PublishJob } from './model';

/** Cómo se enseña cada tipo: carrusel (varias imágenes con flechas) o pieza suelta; vertical 9:16 o 4:5. */
export function previewShape(platform: Publication['platform'], type: string): { carousel: boolean; ratio: '9 / 16' | '4 / 5' } {
  const t = type.toLowerCase();
  const carousel = t.includes('carrusel') || t.includes('carousel');
  const vertical = platform === 'TIKTOK' || t.includes('historia') || t.includes('story') || t.includes('reel') || t.includes('clip') || t.includes('vídeo') || t.includes('video');
  return { carousel, ratio: vertical ? '9 / 16' : '4 / 5' };
}

const networkName = (p: Publication['platform']) => (p === 'TIKTOK' ? 'TikTok' : 'Instagram');

/** Por qué no hay nada que enseñar, según lo que hay. */
export function emptyReason(files: PublicationFile[], status: Publication['status']): string {
  if (files.length === 0) {
    return status === 'IDEA' || status === 'PROPOSAL' || status === 'IN_PRODUCTION'
      ? 'Claude todavía no ha generado las piezas de esta publicación. Cuando las deje en la carpeta de generados, saldrán aquí.'
      : 'Esta publicación no tiene archivos asociados: añádelos abajo, en «Archivos».';
  }
  if (files.every(f => !f.available)) return 'Los archivos asociados no están en este ordenador. Comprueba que Google Drive está abierto y sincronizado.';
  return 'Los archivos asociados no son imágenes ni vídeos que se puedan enseñar aquí.';
}

/** Un archivo enseñable: imagen o vídeo. El vídeo no se descarga hasta darle al play (el servidor lo entrega a trozos). */
export function MediaItem({ publicationId, file, label, large }: { publicationId: string; file: PublicationFile; label: string; large?: boolean }) {
  const src = api.publicationFileUrl(publicationId, file.id);
  const [broken, setBroken] = useState(false);
  if (broken) return <p className="src" role="status">No se pudo cargar «{label}».</p>;
  return file.kind === 'VIDEO'
    ? <video className={`pv-media${large ? ' large' : ''}`} src={src} controls preload="none" playsInline aria-label={label} onError={() => setBroken(true)} />
    : <img className={`pv-media${large ? ' large' : ''}`} src={src} alt={label} loading="lazy" onError={() => setBroken(true)} />;
}

/** «Cómo queda»: la publicación con la estructura de Instagram o TikTok (sin logos ni marcas), el texto y los hashtags. */
export function HowItLooks({ publication, files }: { publication: Publication; files: PublicationFile[] | null }) {
  const [index, setIndex] = useState(0);
  const shape = previewShape(publication.platform, publication.type);
  const visual = (files ?? []).filter(f => f.available && f.kind !== 'OTHER');
  const shown = shape.carousel ? visual : visual.slice(0, 1);
  useEffect(() => { if (index >= shown.length) setIndex(0); }, [shown.length, index]);
  const current = shown[Math.min(index, shown.length - 1)];
  const network = networkName(publication.platform);
  const label = (n: number) => `Pieza ${n} de ${shown.length} de «${publication.title}»`;
  const caption = (publication.caption ?? '').trim();
  const hashtags = (publication.hashtags ?? '').trim();
  const tiktok = publication.platform === 'TIKTOK';

  const text = (
    <div className="pv-text">
      {caption ? <p className="pv-caption">{caption}</p> : <p className="src">Aún no hay texto.</p>}
      {hashtags && <p className="pv-tags">{hashtags}</p>}
    </div>
  );

  return (
    <div className="pv-block" aria-label={`Cómo queda en ${network}`}>
      <h4>Cómo queda en {network}</h4>
      {files && !current ? (
        <p className="src" role="status"><b>Aún no hay archivos.</b> {emptyReason(files, publication.status)}</p>
      ) : (
        <div className={`pv-phone ${tiktok ? 'tt' : 'ig'}`}>
          {!tiktok && (
            <div className="pv-head"><span className="pv-avatar" aria-hidden="true" /><b>Tu cuenta</b><small>{publication.type}</small></div>
          )}
          <div className="pv-stage" style={{ aspectRatio: shape.ratio }}>
            {current && <MediaItem key={current.id} publicationId={publication.id} file={current} label={label(shown.indexOf(current) + 1)} />}
            {!files && <p className="src">Cargando…</p>}
            {shown.length > 1 && (
              <>
                <button type="button" className="pv-nav prev" aria-label="Pieza anterior" disabled={index === 0}
                  onClick={() => setIndex(i => Math.max(0, i - 1))}>‹</button>
                <button type="button" className="pv-nav next" aria-label="Pieza siguiente" disabled={index >= shown.length - 1}
                  onClick={() => setIndex(i => Math.min(shown.length - 1, i + 1))}>›</button>
                <span className="pv-count" aria-live="polite">{index + 1} / {shown.length}</span>
              </>
            )}
            {tiktok && <div className="pv-overlay">{text}</div>}
          </div>
          {shown.length > 1 && (
            <div className="pv-dots" aria-hidden="true">{shown.map((f, n) => <i key={f.id} className={n === index ? 'on' : ''} />)}</div>
          )}
          {!tiktok && (
            <>
              <div className="pv-actions" aria-hidden="true"><span>♡</span><span>💬</span><span>➤</span><span className="sp" /><span>🔖</span></div>
              {text}
            </>
          )}
        </div>
      )}
      <p className="src">Es un esbozo con la proporción y la estructura de {network}; en la red el aspecto puede variar un poco.</p>
    </div>
  );
}

/** «Ver en Instagram/TikTok»: el enlace que guardó SocialHub al confirmar la publicación, o el motivo de que no esté. */
export function ViewOnNetwork({ publication }: { publication: Publication }) {
  const [job, setJob] = useState<PublishJob | null | undefined>(undefined);
  const published = publication.status === 'PUBLISHED';
  useEffect(() => {
    if (!published) { setJob(undefined); return; }
    let live = true;
    api.publishJob(publication.id).then(j => { if (live) setJob(j); }).catch(() => { if (live) setJob(null); });
    return () => { live = false; };
  }, [publication.id, published]);
  const network = networkName(publication.platform);
  if (!published) return <p className="src">«Ver en {network}» aparecerá aquí cuando la publicación esté publicada.</p>;
  if (job === undefined) return <p className="src">Buscando el enlace…</p>;
  if (job?.url) {
    return <p><a className="btn" href={job.url} target="_blank" rel="noreferrer">Ver en {network}</a></p>;
  }
  return <p className="src" role="status">No hay botón «Ver en {network}» porque SocialHub no guardó el enlace al confirmar la publicación.
    Ábrela desde tu perfil de {network}.</p>;
}
