import { useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import {
  PUBLICATION_NEXT, PUBLICATION_STATUS, dateTime, type Publication, type PublicationFile, type PublicationInput,
  type PublicationStatus,
} from './model';
import { Pill } from './ui';

const INITIAL: PublicationStatus[] = ['IDEA', 'PROPOSAL', 'IN_PRODUCTION', 'DRAFT'];
type Initial = NonNullable<PublicationInput['initial_status']>;

const toInput = (p?: Publication): PublicationInput => ({
  platform: p?.platform ?? 'INSTAGRAM', type: p?.type ?? '', title: p?.title ?? '',
  scheduled_on: p?.scheduled_on ?? '', scheduled_time: p ? p.scheduled_time.slice(0, 5) : '19:00',
  timezone: p?.timezone ?? 'Europe/Madrid', route: p?.route ?? 'Publer', note: p?.note ?? '',
});

/** Alta y edición de una publicación, su estado y sus archivos. */
export function PublicationEditor({ promotionId, publication, onClose, onSaved }: {
  promotionId: string; publication?: Publication; onClose: () => void; onSaved: (p: Publication, message: string) => void;
}) {
  const [form, setForm] = useState<PublicationInput>(toInput(publication));
  const [initial, setInitial] = useState<Initial>('IDEA');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const locked = publication?.status === 'PUBLISHING' || publication?.status === 'PUBLISHED';
  const set = <K extends keyof PublicationInput>(k: K, v: PublicationInput[K]) => setForm({ ...form, [k]: v });

  const run = async (action: () => Promise<Publication>, message: string) => {
    setBusy(true);
    setError(null);
    try { onSaved(await action(), message); } catch (e) { setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const input = { ...form, note: form.note?.trim() || undefined };
    void run(() => (publication ? api.updatePublication(publication.id, input)
      : api.createPublication(promotionId, { ...input, initial_status: initial })),
    publication ? 'Publicación guardada' : 'Publicación creada');
  };

  return (
    <div className="card form" aria-label={publication ? 'Editar publicación' : 'Nueva publicación'}>
      <div className="row-between">
        <h3>{publication ? `Publicación · ${publication.title}` : 'Nueva publicación'}</h3>
        <button type="button" className="ibtn" aria-label="Cerrar" title="Cerrar" onClick={onClose}><Icon name="x" /></button>
      </div>
      <form onSubmit={submit}>
        <div className="form-grid">
          <label className="field"><span>Plataforma</span>
            <select disabled={locked} value={form.platform} onChange={e => set('platform', e.target.value as PublicationInput['platform'])}>
              <option value="INSTAGRAM">Instagram</option><option value="TIKTOK">TikTok</option></select></label>
          <label className="field"><span>Tipo</span>
            <input required disabled={locked} maxLength={40} list="pub-types" placeholder="Carrusel" value={form.type} onChange={e => set('type', e.target.value)} />
            <datalist id="pub-types"><option value="Carrusel" /><option value="Historia" /><option value="Reel" /><option value="Clip" /></datalist></label>
          <label className="field"><span>Título</span>
            <input required disabled={locked} maxLength={200} value={form.title} onChange={e => set('title', e.target.value)} /></label>
          <label className="field"><span>Día</span>
            <input required disabled={locked} type="date" value={form.scheduled_on} onChange={e => set('scheduled_on', e.target.value)} /></label>
          <label className="field"><span>Hora (España)</span>
            <input required disabled={locked} type="time" value={form.scheduled_time} onChange={e => set('scheduled_time', e.target.value)} /></label>
          <label className="field"><span>Vía</span>
            <input required disabled={locked} maxLength={40} value={form.route} onChange={e => set('route', e.target.value)} /></label>
          {!publication && (
            <label className="field"><span>Empieza como</span>
              <select value={initial} onChange={e => setInitial(e.target.value as Initial)}>
                {INITIAL.map(s => <option key={s} value={s}>{PUBLICATION_STATUS[s].label}</option>)}</select></label>
          )}
        </div>
        <label className="field"><span>Observaciones</span>
          <textarea rows={2} disabled={locked} maxLength={2000} value={form.note ?? ''} onChange={e => set('note', e.target.value)} /></label>
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
                onClick={() => void run(() => api.changePublicationStatus(publication.id, next), `Estado: ${PUBLICATION_STATUS[next].label}`)}>
                Pasar a «{PUBLICATION_STATUS[next].label}»</button>
            ))}
          </div>
          <p className="src">Programar exige al menos 6 horas de margen. «Publicada» solo llega con la confirmación de la plataforma.</p>
          <PublicationFiles id={publication.id} />
        </>
      )}
    </div>
  );
}

function PublicationFiles({ id }: { id: string }) {
  const [files, setFiles] = useState<PublicationFile[] | null>(null);
  const [path, setPath] = useState('');
  const [error, setError] = useState<string | null>(null);
  const load = () => api.publicationFiles(id).then(r => setFiles(r.items)).catch(e => setError(String(e.message ?? e)));
  useEffect(() => { void load(); }, [id]);

  const act = async (action: () => Promise<unknown>) => {
    setError(null);
    try { await action(); await load(); return true; } catch (e) { setError(e instanceof Error ? e.message : String(e)); return false; }
  };

  return (
    <fieldset className="rows">
      <legend>Archivos</legend>
      {files?.length === 0 && <p className="src">Sin archivos. Llegan solos si el spec.json indica esta publicación en «publicacion», o añádelos aquí.</p>}
      {files?.map(f => (
        <div key={f.path} className="file-row">
          <span className="mono file-path" title={f.path}>{f.path}</span>
          <small className="src nowrap">{f.origin === 'SPEC' ? 'Del spec' : 'A mano'} · {dateTime(f.added_at)}</small>
          {!f.available && <Pill s="BLOCKED" label="No disponible" />}
          <button type="button" className="ibtn" aria-label="Quitar archivo" title="Quitar de la publicación (no se borra)"
            onClick={() => void act(() => api.detachPublicationFile(id, f.path))}><Icon name="x" /></button>
        </div>
      ))}
      <form className="file-add" onSubmit={e => { e.preventDefault(); void act(() => api.attachPublicationFile(id, path.trim())).then(ok => { if (ok) setPath(''); }); }}>
        <input aria-label="Ruta del archivo" placeholder="G:\Mi unidad\…\pieza.jpg" value={path} onChange={e => setPath(e.target.value)} />
        <button className="btn" disabled={!path.trim()}>Añadir archivo</button>
      </form>
      {error && <p className="error" role="alert">{error}</p>}
    </fieldset>
  );
}
