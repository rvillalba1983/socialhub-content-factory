import { useEffect, useState } from 'react';
import { ApiError, api } from './api';
import { Icon } from './icons';
import {
  PUBLICATION_NEXT, PUBLICATION_STATUS, PUBLICATION_TYPES, REJECTION_REASONS, routesFor, dateTime, marginFor, spanText, type RejectionReason, type Publication, type PublicationMetrics, type PublishJob, type QualityReport, type PublicationFile, type PublicationInput,
  type PublicationStatus, type PublishSettings, type StatusCode,
} from './model';
import { Pill } from './ui';
import { WhereHelp } from './WhereHelp';
import { HowItLooks, MediaItem, ViewOnNetwork } from './PublicationPreview';

const OTHER = '__other';
const INITIAL: PublicationStatus[] = ['IDEA', 'PROPOSAL', 'IN_PRODUCTION', 'DRAFT'];
type Initial = NonNullable<PublicationInput['initial_status']>;

const toInput = (p?: Publication): PublicationInput => ({
  platform: p?.platform ?? 'INSTAGRAM', type: p?.type ?? '', title: p?.title ?? '',
  scheduled_on: p?.scheduled_on ?? '', scheduled_time: p ? p.scheduled_time.slice(0, 5) : '19:00',
  timezone: p?.timezone ?? 'Europe/Madrid', route: p?.route ?? 'Publer', note: p?.note ?? '',
  caption: p?.caption ?? '', hashtags: p?.hashtags ?? '',
});

/** Los márgenes mínimos de publicación vigentes (general y de TikTok), para los textos del editor. */
function usePublishSettings(): PublishSettings | null {
  const [settings, setSettings] = useState<PublishSettings | null>(null);
  useEffect(() => {
    let alive = true;
    api.publishSettings().then(s => { if (alive) setSettings(s); }).catch(() => {});
    return () => { alive = false; };
  }, []);
  return settings;
}

/** El margen mínimo que vale para la red elegida, con palabras («30 minutos», «8 horas»). */
const marginText = (settings: PublishSettings | null, platform: string) => spanText(marginFor(settings, platform));

/** Alta y edición de una publicación, su estado y sus archivos. */
export function PublicationEditor({ promotionId, publication, onClose, onSaved }: {
  promotionId: string; publication?: Publication; onClose: () => void; onSaved: (p: Publication, message: string) => void;
}) {
  const [form, setForm] = useState<PublicationInput>(toInput(publication));
  const publishSettings = usePublishSettings();
  const [initial, setInitial] = useState<Initial>('IDEA');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** «Otro…» en el tipo: se escribe a mano (por ejemplo, un tipo que propone Claude). */
  const [otherType, setOtherType] = useState(!!publication && !PUBLICATION_TYPES.includes(publication.type));
  const [asked, setAsked] = useState<string | null>(null);
  const [textsTouched, setTextsTouched] = useState(false);
  /** Pide el motivo antes de cancelar la publicación o descartar el texto (memoria editorial). */
  const [rejecting, setRejecting] = useState<'CANCEL' | 'TEXT' | null>(null);
  // Si Claude deja textos nuevos mientras el editor está abierto y Raúl no ha tocado los suyos, se ven al momento
  useEffect(() => {
    if (!textsTouched) setForm(f => ({ ...f, caption: publication?.caption ?? '', hashtags: publication?.hashtags ?? '' }));
  }, [publication?.caption, publication?.hashtags, textsTouched]);
  /** Programada, publicándose o publicada: la vía ya tiene esta versión y no se cambia. */
  const locked = publication?.status === 'SCHEDULED' || publication?.status === 'PUBLISHING' || publication?.status === 'PUBLISHED';
  const [files, setFiles] = useState<PublicationFile[] | null>(null);
  const [filesError, setFilesError] = useState<string | null>(null);
  const publicationId = publication?.id;
  const loadFiles = () => {
    if (!publicationId) return Promise.resolve();
    return api.publicationFiles(publicationId).then(r => { setFiles(r.items); setFilesError(null); })
      .catch(e => setFilesError(String(e.message ?? e)));
  };
  // También cuando la publicación cambia (Claude puede dejar piezas nuevas mientras el editor está abierto)
  useEffect(() => { void loadFiles(); }, [publicationId, publication?.status]);
  const set = <K extends keyof PublicationInput>(k: K, v: PublicationInput[K]) => setForm({ ...form, [k]: v });

  const run = async (action: () => Promise<Publication>, message: string) => {
    setBusy(true);
    setError(null);
    try { onSaved(await action(), message); } catch (e) { setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const input = { ...form, note: form.note?.trim() || undefined, caption: form.caption?.trim() || undefined,
      hashtags: form.hashtags?.trim() || undefined };
    void run(() => (publication ? api.updatePublication(publication.id, input)
      : api.createPublication(promotionId, { ...input, initial_status: initial })),
    publication ? 'Publicación guardada' : 'Publicación creada');
    setTextsTouched(false);
  };

  const askClaude = async () => {
    if (!publication) return;
    setError(null);
    try {
      await api.orderFromClaude(promotionId, {
        type: 'CAPTIONS', title: `Textos · ${publication.title}`, inputs: [publication.id],
        goal: `Escribe el texto y los hashtags de «${publication.title}» (${publication.type}, ${publication.platform === 'TIKTOK' ? 'TikTok' : 'Instagram'}).`,
      });
      setAsked(publication.caption || publication.hashtags
        ? 'Pedido a Claude: su texto sustituirá al actual (podrás recuperar el tuyo).'
        : 'Pedido a Claude: los textos llegarán aquí para que los revises.');
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  return (
    <div id="publication-editor" tabIndex={-1} className="card form" aria-label={publication ? 'Editar publicación' : 'Nueva publicación'}>
      <div className="row-between">
        <h3>{publication ? `Publicación · ${publication.title}` : 'Nueva publicación'}</h3>
        <button type="button" className="ibtn" aria-label="Cerrar" title="Cerrar" onClick={onClose}><Icon name="x" /></button>
      </div>
      <form onSubmit={submit}>
        <div className="form-grid">
          <label className="field"><span>Plataforma</span>
            <select disabled={locked} value={form.platform} onChange={e => { const platform = e.target.value as PublicationInput['platform']; setForm({ ...form, platform, route: routesFor(platform).includes(form.route) ? form.route : 'Publer' }); }}>
              <option value="INSTAGRAM">Instagram</option><option value="TIKTOK">TikTok</option></select></label>
          <label className="field"><span>Tipo</span>
            <select required disabled={locked} value={otherType ? OTHER : form.type}
              onChange={e => {
                if (e.target.value === OTHER) { setOtherType(true); set('type', PUBLICATION_TYPES.includes(form.type) ? '' : form.type); }
                else { setOtherType(false); set('type', e.target.value); }
              }}>
              <option value="" disabled>Elige un tipo</option>
              {PUBLICATION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              <option value={OTHER}>Otro…</option>
            </select></label>
          {otherType && (
            <label className="field"><span>Otro tipo</span>
              <input required disabled={locked} maxLength={40} placeholder="Por ejemplo, Directo" value={form.type}
                onChange={e => set('type', e.target.value)} /></label>
          )}
          <label className="field"><span>Título</span>
            <input required disabled={locked} maxLength={200} value={form.title} onChange={e => set('title', e.target.value)} /></label>
          <label className="field"><span>Día</span>
            <input required disabled={locked} type="date" value={form.scheduled_on} onChange={e => set('scheduled_on', e.target.value)} /></label>
          <label className="field"><span>Hora (España)</span>
            <input required disabled={locked} type="time" value={form.scheduled_time} onChange={e => set('scheduled_time', e.target.value)} /></label>
          <label className="field"><span>Vía</span>
            <select required disabled={locked} value={form.route} onChange={e => set('route', e.target.value)}>
              {(routesFor(form.platform).includes(form.route) ? routesFor(form.platform) : [...routesFor(form.platform), form.route]).map(r => <option key={r} value={r}>{r}</option>)}
            </select></label>
          {!publication && (
            <label className="field"><span>Empieza como</span>
              <select value={initial} onChange={e => setInitial(e.target.value as Initial)}>
                {INITIAL.map(s => <option key={s} value={s}>{PUBLICATION_STATUS[s].label}</option>)}</select></label>
          )}
        </div>
        {form.platform === 'TIKTOK' && (
          <p className="src" role="note">Margen mínimo de publicación en TikTok: {marginText(publishSettings, 'TIKTOK')}. TikTok se publica por
            Publer, que programa Claude desde tu PC en sus pasadas; con menos de unas 8 horas puede no llegar y quedará para subirla a mano.</p>
        )}
        <label className="field"><span>Texto ({(form.caption ?? '').length} / 2200)</span>
          <textarea rows={4} disabled={locked} maxLength={2200} value={form.caption ?? ''}
            onChange={e => { setTextsTouched(true); set('caption', e.target.value); }} /></label>
        <label className="field"><span>Hashtags</span>
          <input disabled={locked} maxLength={1000} placeholder="#poesia #eldelito" value={form.hashtags ?? ''}
            onChange={e => { setTextsTouched(true); set('hashtags', e.target.value); }} /></label>
        {publication && !locked && (
          <p className="src">
            <button type="button" className="btn" title={publication.caption || publication.hashtags
              ? 'Si ya hay texto, el de Claude lo sustituirá; podrás recuperar el tuyo.' : undefined}
              onClick={() => void askClaude()}>Pedir textos a Claude</button>{' '}
            {(publication.previous_caption || publication.previous_hashtags) && (
              <><button type="button" className="btn" disabled={busy}
                onClick={() => { setTextsTouched(false); void run(() => api.restorePublicationText(publication.id), 'Texto anterior recuperado'); }}>
                Recuperar mi texto anterior</button>{' '}</>
            )}
            {(publication.caption || publication.hashtags) && (
              <button type="button" className="btn" onClick={() => setRejecting('TEXT')}>Descartar texto</button>
            )}
            {asked && <> {asked}</>}
          </p>
        )}
        <label className="field"><span>Observaciones</span>
          <textarea rows={2} disabled={locked} maxLength={2000} value={form.note ?? ''} onChange={e => set('note', e.target.value)} /></label>
        {publication?.status === 'SCHEDULED' && (
          <p className="src">Está programada: para cambiar algo, vuelve a «Aprobada» y prográmala otra vez.</p>
        )}
        <div className="form-actions">
          {error && <p className="error" role="alert">{error}</p>}
          {!locked && <button className="btn primary" disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</button>}
          <button type="button" className="btn" onClick={onClose}>Cerrar</button>
        </div>
      </form>

      {publication && (
        <>
          <div className="status-row">
            <span>Estado</span><Pill s={PUBLICATION_STATUS[publication.status].s} label={PUBLICATION_STATUS[publication.status].label} />
            {PUBLICATION_NEXT[publication.status].map(next => (
              <button key={next} type="button" className="btn" disabled={busy}
                onClick={() => (next === 'CANCELLED' ? setRejecting('CANCEL')
                  : void run(() => api.changePublicationStatus(publication.id, next), `Estado: ${PUBLICATION_STATUS[next].label}`))}>
                Pasar a «{PUBLICATION_STATUS[next].label}»</button>
            ))}
          </div>
          {rejecting && (
            <ReasonForm title={rejecting === 'CANCEL' ? 'Por qué la cancelas' : 'Por qué descartas el texto'}
              action={rejecting === 'CANCEL' ? 'Cancelar publicación' : 'Descartar texto'}
              onCancel={() => setRejecting(null)}
              onSubmit={(reason, note) => {
                setRejecting(null);
                void run(() => (rejecting === 'CANCEL'
                  ? api.changePublicationStatus(publication.id, 'CANCELLED', reason, note)
                  : api.rejectPublicationText(publication.id, reason, note)),
                rejecting === 'CANCEL' ? 'Publicación cancelada' : 'Texto descartado');
                if (rejecting === 'TEXT') setTextsTouched(false);
              }} />
          )}
          <p className="src">Programar exige el margen mínimo de publicación{publication.platform === 'TIKTOK' ? ' en TikTok' : ''} ({marginText(publishSettings, publication.platform)}; se cambia en «Encargos a Claude»). «Publicada» solo llega con la confirmación de la plataforma.</p>
          {publication.flag && (
            <p className="flag-row" role="status"><Icon name="alert" /> <span>{publication.flag}</span>{' '}
              <button type="button" className="btn" disabled={busy}
                onClick={() => void run(() => api.clearPublicationFlag(publication.id).then(() => ({ ...publication, flag: undefined })), 'Incidencia resuelta')}>Resuelto</button></p>
          )}
          <PublishPanel publication={publication} />
          <MetricsPanel publication={publication} />
          <QualityPanel publication={publication} promotionId={promotionId} margin={marginText(publishSettings, publication.platform)} />
          <fieldset className="rows" aria-label="Vista previa">
            <legend>Vista previa</legend>
            <HowItLooks publication={publication} files={files} />
            <ViewOnNetwork publication={publication} />
          </fieldset>
          <PublicationFiles id={publication.id} title={publication.title} locked={locked} files={files} error={filesError} reload={loadFiles} />
        </>
      )}
    </div>
  );
}

function PublicationFiles({ id, title, locked, files, error: loadError, reload }: {
  id: string; title: string; locked: boolean; files: PublicationFile[] | null; error: string | null; reload: () => Promise<unknown>;
}) {
  const [path, setPath] = useState('');
  const [error, setError] = useState<string | null>(null);
  /** Archivo abierto (por su identificador): imagen ampliada o vídeo con reproductor. */
  const [open, setOpen] = useState<string | null>(null);

  const act = async (action: () => Promise<unknown>) => {
    setError(null);
    try { await action(); await reload(); return true; } catch (e) { setError(e instanceof Error ? e.message : String(e)); return false; }
  };

  return (
    <fieldset className="rows">
      <legend>Archivos</legend>
      {files?.length === 0 && (
        <p className="src">Aún no hay archivos. Claude todavía no ha generado las piezas de esta publicación: normalmente llegan solas cuando las prepara.
          Si no han llegado, pega abajo la ruta del archivo en tu ordenador (por ejemplo, en tu Google Drive) y pulsa «Añadir archivo».</p>
      )}
      {files?.map((f, n) => {
        const name = f.path.split(/[\\/]/).pop() ?? f.path;
        const label = `${f.kind === 'VIDEO' ? 'Vídeo' : 'Imagen'} ${n + 1} de «${title}» (${name})`;
        const shown = open === f.id;
        return (
          <div key={f.path}>
            <div className="file-row">
              <span className="mono file-path" title={f.path}>{f.path}</span>
              <small className="src nowrap">{f.origin === 'SPEC' ? 'Del spec' : 'A mano'} · {dateTime(f.added_at)}</small>
              {!f.available && <Pill s="BLOCKED" label="No disponible" />}
              {f.available && f.kind !== 'OTHER' && (
                <button type="button" className="btn" aria-expanded={shown} aria-controls={`pv-${f.id}`}
                  onClick={() => setOpen(shown ? null : f.id)}>{shown ? 'Cerrar' : f.kind === 'VIDEO' ? 'Ver vídeo' : 'Ver imagen'}</button>
              )}
              {f.available && f.kind === 'OTHER' && <small className="src nowrap">Sin vista previa</small>}
              {!locked && <button type="button" className="ibtn" aria-label="Quitar archivo" title="Quitar de la publicación (no se borra)"
                onClick={() => void act(() => api.detachPublicationFile(id, f.path))}><Icon name="x" /></button>}
            </div>
            {shown && <div id={`pv-${f.id}`} className="pv-open"><MediaItem publicationId={id} file={f} label={label} large /></div>}
          </div>
        );
      })}
      {!locked && (
        <form className="file-add" onSubmit={e => { e.preventDefault(); void act(() => api.attachPublicationFile(id, path.trim())).then(ok => { if (ok) setPath(''); }); }}>
          <input aria-label="Ruta del archivo" placeholder="G:\Mi unidad\…\pieza.jpg" value={path} onChange={e => setPath(e.target.value)} />
          <button className="btn" disabled={!path.trim()}>Añadir archivo</button>
        </form>
      )}
      {!locked && <WhereHelp topic="local-path" />}
      {(error ?? loadError) && <p className="error" role="alert">{error ?? loadError}</p>}
    </fieldset>
  );
}

const JOB_STATUS: Record<PublishJob['status'], StatusCode> = {
  WORKING: 'RUNNING', HELD: 'WAITING', RETRY_WAIT: 'WAITING', SCHEDULED: 'OK', CHECKING: 'RUNNING', MANUAL: 'BLOCKED',
  PUBLISHED: 'OK', FAILED: 'FAILED', CANCELLED: 'WAITING',
};

/** Publicación: vía en uso, próximo intento, LEEME si hay que hacerla a mano e historial. */
function PublishPanel({ publication }: { publication: Publication }) {
  const [job, setJob] = useState<PublishJob | null>(null);
  const [error, setError] = useState<string | null>(null);
  const live = ['SCHEDULED', 'PUBLISHING', 'PUBLISHED', 'PUBLISH_ERROR'].includes(publication.status);
  useEffect(() => {
    if (!live) { setJob(null); return; }
    api.publishJob(publication.id).then(setJob)
      .catch(e => { if (e instanceof ApiError && e.status === 404) setJob(null); else setError(e instanceof Error ? e.message : String(e)); });
  }, [publication, live]);
  if (!live || (!job && !error)) return null;
  return (
    <fieldset className="rows" aria-label="Publicación">
      <legend>Publicación</legend>
      {error && <p className="error">{error}</p>}
      {job && (
        <>
          <div className="status-row">
            <Pill s={JOB_STATUS[job.status]} label={job.status_label} />
            {job.route && <span>Vía: <b>{job.route}</b></span>}
            {job.attempts > 1 && <small className="src">Ronda {job.attempts}</small>}
            {job.next_try_at && <small className="src">Próximo intento: {dateTime(job.next_try_at)}</small>}
          </div>
          <p className="src">Vías por orden: {job.routes.filter(r => r.label !== 'API oficial' || publication.platform === 'INSTAGRAM').map(r => `${r.label}${r.configured ? '' : ' (no configurada)'}`).join(' → ')}</p>
          {job.url && <p>Enlace: <a href={job.url} target="_blank" rel="noreferrer">{job.url}</a></p>}
          {job.external_id && <p className="src">Identificador: <span className="mono">{job.external_id}</span></p>}
          {job.manual_file && <p>LEEME para publicarla a mano: <span className="mono">{job.manual_file}</span></p>}
          {job.last_error && job.status !== 'PUBLISHED' && <p className="src">Último problema: {job.last_error}</p>}
          <details>
            <summary>Historial</summary>
            <ul className="src">{job.log.map((line, n) => <li key={n}>{line}</li>)}</ul>
          </details>
        </>
      )}
    </fieldset>
  );
}

const METRICS: [keyof PublicationMetrics & ('views' | 'reach' | 'likes' | 'comments' | 'saves' | 'shares'), string][] = [
  ['views', 'Reproducciones'], ['reach', 'Alcance'], ['likes', 'Me gusta'], ['comments', 'Comentarios'],
  ['saves', 'Guardados'], ['shares', 'Compartidos'],
];

/** Métricas: se leen una vez, a los 7 días de publicarse. Lo que no se vio queda «Sin dato». */
function MetricsPanel({ publication }: { publication: Publication }) {
  const [m, setM] = useState<PublicationMetrics | null>(null);
  useEffect(() => {
    if (publication.status !== 'PUBLISHED') { setM(null); return; }
    api.publicationMetrics(publication.id).then(setM).catch(() => setM(null));
  }, [publication]);
  if (publication.status !== 'PUBLISHED') return null;
  return (
    <fieldset className="rows" aria-label="Métricas">
      <legend>Métricas</legend>
      {!m && <p className="src">Se leen solas a los 7 días de publicarse.</p>}
      {m?.status === 'REQUESTED' && <p className="src">Pedidas a Claude el {dateTime(m.requested_at)}.{m.note ? ` ${m.note}` : ''}</p>}
      {m?.status === 'FAILED' && <p className="src">No se pudieron leer{m.note ? `: ${m.note}` : ''}. Se reintenta al día siguiente (máximo 3 veces).</p>}
      {m?.status === 'DONE' && (
        <>
          <div className="metrics">
            {METRICS.map(([k, label]) => (
              <div key={k}><small>{label}</small><b>{m[k] == null ? 'Sin dato' : (m[k] as number).toLocaleString('es-ES')}</b></div>
            ))}
          </div>
          <p className="src">Leídas el {dateTime(m.measured_at!)}{m.note ? ` · ${m.note}` : ''}</p>
        </>
      )}
    </fieldset>
  );
}

/** Una frase de «qué hacer» para cada comprobación, a partir de la clave estable del aviso. */
export function whatToDo(key: string, source: string): string {
  const kind = key.split(':')[0];
  switch (kind) {
    case 'TEXT_MISSING': return 'escribe el texto arriba, o pídeselo a Claude con «Pedir textos a Claude».';
    case 'TEXT_TOO_LONG': return 'acorta el texto o quita hashtags hasta que quepa.';
    case 'HASHTAGS_TOO_MANY': return 'quita hashtags hasta dejar los que admite la red.';
    case 'CTA_MISSING': return 'añade al texto la llamada a la acción que has fijado en la identidad de la marca.';
    case 'HASHTAG_MISSING': return 'añade ese hashtag fijo de la marca a los hashtags.';
    case 'FORBIDDEN': return 'cambia esa expresión en el texto o el título; la marca no quiere usarla.';
    case 'DUPLICATE': return 'reescribe el texto para que no sea idéntico al de otra publicación.';
    case 'FILES_MISSING': return 'añade abajo, en «Archivos», las imágenes o vídeos de la publicación.';
    case 'FILE_MISSING': return 'comprueba que el archivo existe y que Google Drive está abierto y sincronizado; si no, quítalo y añade otro.';
    case 'FILE_FORMAT': return 'cambia el archivo por uno del tipo que admite la red (JPEG o MP4, por ejemplo).';
    case 'FILE_SIZE': return 'usa una versión más ligera del archivo.';
    case 'IMAGE_RATIO': return 'recorta la imagen con otra proporción; si te da igual cómo se recorte, acéptalo.';
    case 'CAROUSEL_COUNT': return 'ajusta el número de imágenes del carrusel a lo que admite la red.';
    case 'CAROUSEL_MIX': return 'usa solo fotos o solo vídeos en esta publicación.';
    case 'VIDEO_MISSING': return 'añade el archivo de vídeo en «Archivos».';
    default: return source === 'CLAUDE'
      ? 'léelo y corrige lo que señala; si no estás de acuerdo, acéptalo con tu motivo.'
      : 'corrígelo o, si lo das por bueno así, acéptalo con tu motivo.';
  }
}

/** Control de calidad: avisos del código y de Claude. Cada uno se corrige o se acepta con su motivo. */
function QualityPanel({ publication, promotionId, margin }: { publication: Publication; promotionId: string; margin: string }) {
  const [report, setReport] = useState<QualityReport | null>(null);
  const [accepting, setAccepting] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = () => api.publicationQuality(publication.id).then(setReport).catch(e => setError(e instanceof Error ? e.message : String(e)));
  // Se vuelve a comprobar cuando cambia la publicación (texto, estado…)
  useEffect(() => { void load(); }, [publication]);

  const accept = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!accepting) return;
    setError(null);
    try { setReport(await api.acceptQualityIssue(publication.id, accepting, reason.trim())); setAccepting(null); setReason(''); }
    catch (err) { setError(err instanceof Error ? err.message : String(err)); }
  };
  const review = async () => {
    setError(null);
    try {
      await api.orderFromClaude(promotionId, {
        type: 'QUALITY_REVIEW', title: `Revisión · ${publication.title}`, inputs: [publication.id],
        goal: `Revisa la calidad de «${publication.title}»: coherencia, tono de la marca, ortografía y afirmaciones sin fuente.`,
      });
      setNote('Revisión pedida a Claude: sus avisos aparecerán aquí.');
    } catch (err) { setError(err instanceof Error ? err.message : String(err)); }
  };

  return (
    <fieldset className="rows" aria-label="Control de calidad">
      <legend>Control de calidad</legend>
      <p className="src">Antes de aprobar una publicación, SocialHub la repasa sola y te avisa de lo que no encaja.</p>
      <details className="quality-help">
        <summary>¿Qué es esto?</summary>
        <p>Son comprobaciones automáticas de SocialHub, antes de aprobar y programar. Miran los archivos (que existan, que sean del tipo
          y el tamaño que admite la red y que se vean bien), el texto (que no falte ni se pase de largo), los hashtags y lo que has fijado
          en la identidad de la marca (llamada a la acción, hashtags fijos y expresiones prohibidas).</p>
        <ul>
          <li><b>Aceptar</b> significa «lo doy por bueno aunque falle». Escribes el motivo, el aviso deja de bloquear y queda anotado.
            Solo vale mientras no cambie la publicación: si cambias el texto, los hashtags o los archivos, las aceptaciones se anulan y los avisos se comprueban de nuevo (reaparecen si siguen fallando).</li>
          <li><b>Comprobar de nuevo</b> vuelve a pasar las comprobaciones. Úsalo después de corregir algo (añadir un archivo, cambiar el texto…).</li>
          <li><b>Revisar con Claude</b> le pide a Claude una segunda lectura: coherencia, tono de la marca, ortografía y datos sin fuente.
            Sus avisos llegan aquí en la siguiente recogida.</li>
        </ul>
        <p>Aparte, para programar hace falta el margen mínimo de publicación antes de la hora ({margin} en esta publicación, ajustable en «Encargos a Claude»).</p>
      </details>
      {report && report.issues.length === 0 && <p className="src">Sin avisos.</p>}
      {report?.issues.map(i => (
        <div key={i.key} className="quality-row">
          <span className={`pill ${i.accepted ? 's-ok' : 's-block'}`}>{i.source === 'CLAUDE' ? 'Claude' : 'Comprobación'}</span>
          <span>{i.message}{i.accepted && <small className="src"> · Aceptado: {i.accepted_reason} (vale mientras no cambie la publicación)</small>}
            {!i.accepted && <small className="quality-todo">Qué hacer: {whatToDo(i.key, i.source)}</small>}</span>
          {!i.accepted && accepting !== i.key && (
            <button type="button" className="btn" onClick={() => { setAccepting(i.key); setReason(''); }}>Aceptar</button>
          )}
          {accepting === i.key && (
            <form className="file-add" onSubmit={accept}>
              <input aria-label="Por qué lo aceptas" placeholder="Por qué lo aceptas" value={reason} onChange={e => setReason(e.target.value)} />
              <button className="btn" disabled={reason.trim().length < 3}>Aceptar aviso</button>
            </form>
          )}
        </div>
      ))}
      {report && report.open > 0 && (
        <p className="src">{report.open === 1 ? '1 aviso abierto' : `${report.open} avisos abiertos`}: no puede pasar a «Aprobada» ni a «Programada» hasta corregirlos o aceptarlos.</p>
      )}
      <p className="src">
        <button type="button" className="btn" onClick={() => void load()}>Comprobar de nuevo</button>{' '}
        <button type="button" className="btn" onClick={() => void review()}>Revisar con Claude</button>
        {note && <> {note}</>}
      </p>
      {error && <p className="error" role="alert">{error}</p>}
    </fieldset>
  );
}

/** Motivo fijo y nota: queda en la memoria editorial de la marca. */
export function ReasonForm({ title, action, onSubmit, onCancel }: {
  title: string; action: string; onSubmit: (reason: RejectionReason, note?: string) => void; onCancel: () => void;
}) {
  const [reason, setReason] = useState<RejectionReason>('TONE');
  const [note, setNote] = useState('');
  return (
    <div className="card form reason-form" role="group" aria-label={title}>
      <b>{title}</b>
      <label className="field"><span>Motivo</span>
        <select value={reason} onChange={e => setReason(e.target.value as RejectionReason)}>
          {REJECTION_REASONS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></label>
      <label className="field"><span>Nota (opcional)</span>
        <input maxLength={1000} value={note} onChange={e => setNote(e.target.value)} /></label>
      <p className="src">Queda en la memoria editorial: Claude lo tendrá en cuenta en los próximos encargos.</p>
      <div className="form-actions">
        <button type="button" className="btn primary" onClick={() => onSubmit(reason, note.trim() || undefined)}>{action}</button>
        <button type="button" className="btn" onClick={onCancel}>Volver</button>
      </div>
    </div>
  );
}
