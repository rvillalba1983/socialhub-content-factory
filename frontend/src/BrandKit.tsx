import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import type { BrandKitTemplate, BrandKitVersion, BrandKitView, Snapshot } from './model';

type PieceCode = 'cita' | 'cierre' | 'historia';

const PIECES: { code: PieceCode; label: string }[] = [
  { code: 'cita', label: 'Cita (verso o frase sobre fondo)' },
  { code: 'cierre', label: 'Cierre del carrusel' },
  { code: 'historia', label: 'Historia' },
];

const PIECE_NAME: Record<BrandKitTemplate['piece'], string> = {
  cita: 'Cita', cierre: 'Cierre del carrusel', historia: 'Historia', infografia: 'Infografía', meme: 'Meme',
};

/**
 * Tareas de encargo de kit abiertas: en cola (aún sin enviar), enviadas, retenidas o esperando respuesta de Raúl. Es el mismo criterio
 * que usa el servidor para no dejar pedir otro kit y para la recomendación «Genera el kit de X» (ahí también cuenta el fallido con
 * reintentos por delante, que aquí no se distingue: el servidor lo dirá con un 409 al pedir).
 */
const OPEN_STATUS = new Set(['QUEUED', 'RUNNING', 'WAITING', 'BLOCKED']);

/** Piezas que lleva el kit si la marca no las dice (lo común a todas; la cita es solo de El DeLito: decisiones 13 y 51). */
const DEFAULT_PIECES: PieceCode[] = ['cierre', 'historia'];

/** Misma longitud máxima del comentario que la API (`comment` de «Pedir otra versión»). */
const MAX_COMMENT = 1000;

/** Una plantilla que no se pinta (con errores o sin vista previa) impide aprobar el kit. */
const paints = (t: BrandKitTemplate) => t.errors.length === 0 && !!t.preview_png_base64;

const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

function Template({ t }: { t: BrandKitTemplate }) {
  return (
    <li className="kit-template" aria-label={`Plantilla ${t.name}`}>
      {t.preview_png_base64
        ? <img src={`data:image/png;base64,${t.preview_png_base64}`} alt={`Vista previa de ${t.name}`} className="kit-preview" />
        : <div className="kit-preview kit-nopreview">Sin vista previa</div>}
      <div className="kit-template-info">
        <b>{t.name}</b>
        <small>{PIECE_NAME[t.piece]}</small>
        {t.errors.length > 0 && (
          <ul className="error" role="alert" aria-label={`Errores de ${t.name}`}>{t.errors.map((m, i) => <li key={i}>{m}</li>)}</ul>
        )}
        {t.warnings.length > 0 && (
          <ul className="kit-warnings" aria-label={`Avisos de ${t.name}`}>{t.warnings.map((m, i) => <li key={i}>{m}</li>)}</ul>
        )}
      </div>
    </li>
  );
}

function Version({ kit }: { kit: BrandKitVersion }) {
  return (
    <div className="kit-version">
      <ul className="kit-palette" aria-label="Paleta">
        {kit.palette.map(c => (
          <li key={c.name + c.hex}><span className="color-dot" aria-hidden="true" style={{ background: c.hex }} /><span>{c.name} <small>{c.hex}</small></span></li>
        ))}
      </ul>
      {kit.notes && <p className="kit-notes"><b>Notas de Claude:</b> {kit.notes}</p>}
      <ul className="kit-templates" aria-label="Plantillas">{kit.templates.map(t => <Template key={t.id} t={t} />)}</ul>
    </div>
  );
}

/**
 * Kit de marca: la paleta y las plantillas SVG que Claude diseña una vez por marca. Nada se usa hasta que Raúl lo aprueba.
 * Aquí se ve el aprobado y el pendiente, y se pide el kit (o otra versión).
 */
export function BrandKitCard({ brandId, brandName, data, onChanged }: {
  brandId: string; brandName: string; data: Snapshot; onChanged?: (message: string) => void;
}) {
  const [view, setView] = useState<BrandKitView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [ordered, setOrdered] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [comment, setComment] = useState('');
  const [rejectPromotion, setRejectPromotion] = useState('');

  // Las piezas que lleva el kit las dice la marca (`kit_pieces`), no su nombre
  const carried = (data.brands?.find(b => b.id === brandId)?.kit_pieces ?? DEFAULT_PIECES)
    .filter((c): c is PieceCode => PIECES.some(p => p.code === c));
  const promotions = data.promotions.filter(p => p.brand_id === brandId);
  const [promotion, setPromotion] = useState('');
  const [pieces, setPieces] = useState<Set<PieceCode>>(new Set(carried));
  const promotionId = promotions.length === 1 ? promotions[0].id : promotion;

  const reload = useCallback(() =>
    api.brandKit(brandId).then(setView).catch(e => setError(message(e))), [brandId]);
  useEffect(() => { setView(null); setError(null); void reload(); }, [reload]);

  const promotionIds = new Set(promotions.map(p => p.id));
  const orderOpen = ordered || data.tasks.some(t => t.type === 'AI_BRAND_KIT' && t.promotion_id && promotionIds.has(t.promotion_id) && OPEN_STATUS.has(t.status));

  const run = async (action: () => Promise<string | void>) => {
    setBusy(true); setError(null);
    try { const done = await action(); await reload(); if (done) onChanged?.(done); }
    catch (e) { setError(message(e)); void reload(); } finally { setBusy(false); }
  };

  const request = () => run(async () => {
    const order = PIECES.map(p => p.code).filter(c => pieces.has(c));
    await api.requestBrandKit(brandId, { ...(promotionId ? { promotion_id: promotionId } : {}), pieces: order });
    setOrdered(true);
    return `Kit de marca pedido · ${brandName}`;
  });
  // Aprobar y rechazar llevan el id del kit que se está viendo: si ya no es el pendiente, el servidor responde 409 («recarga»)
  const approve = (kit: BrandKitVersion) => run(async () => {
    await api.approveBrandKit(brandId, kit.id); setOrdered(false); return `Kit de marca aprobado · ${brandName}`;
  });
  const reject = (kit: BrandKitVersion) => run(async () => {
    const result = await api.rejectBrandKit(brandId, kit.id, comment.trim(), true, rejectPromotion || undefined);
    setRejecting(false); setComment(''); setRejectPromotion('');
    if (result.task_id) setOrdered(true);
    return `Otra versión del kit pedida · ${brandName}`;
  });

  const toggle = (code: PieceCode) => setPieces(prev => {
    const next = new Set(prev);
    if (next.has(code)) next.delete(code); else next.add(code);
    return next;
  });

  const canRequest = !busy && (promotions.length > 0) && promotionId !== '' && pieces.size > 0;
  const unpainted = view?.pending ? view.pending.templates.filter(t => !paints(t)) : [];

  return (
    <div className="card form kit-card" data-card="kit" aria-label="Kit de marca">
      <h3>Kit de marca</h3>
      <p className="src">La paleta y las plantillas de las piezas de la marca (cierre del carrusel, historia…). Las diseña Claude una vez, van encima de la ilustración y no se usan hasta que las apruebes.</p>
      {error && <p className="error" role="alert">{error}</p>}
      {!view && !error && <p className="src">Cargando…</p>}

      {view?.pending && (
        <section className="kit-block" aria-label="Kit pendiente de aprobación">
          <h4>Pendiente de aprobación · versión {view.pending.version}</h4>
          <Version kit={view.pending} />
          {unpainted.length > 0 && (
            <p className="error" role="alert">No se puede aprobar: {unpainted.length === 1 ? 'una plantilla no se pinta' : `${unpainted.length} plantillas no se pintan`} ({unpainted.map(t => t.name).join(', ')}). Pide otra versión.</p>
          )}
          {!rejecting && (
            <div className="form-actions">
              <button type="button" className="btn primary" disabled={busy || unpainted.length > 0} onClick={() => void approve(view.pending!)}>Aprobar</button>
              <button type="button" className="btn" disabled={busy} onClick={() => setRejecting(true)}>Pedir otra versión</button>
            </div>
          )}
          {rejecting && (
            <div className="kit-reject" role="group" aria-label="Pedir otra versión del kit">
              <label className="field"><span>¿Qué quieres que cambie? (obligatorio)</span>
                <textarea rows={3} maxLength={MAX_COMMENT} value={comment} onChange={e => setComment(e.target.value)} /></label>
              {promotions.length > 1 && (
                <label className="field"><span>Promoción del encargo</span>
                  <select value={rejectPromotion} onChange={e => setRejectPromotion(e.target.value)}>
                    <option value="">La misma del encargo anterior</option>
                    {promotions.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                  </select></label>
              )}
              <div className="form-actions">
                <button type="button" className="btn primary" disabled={busy || comment.trim() === ''} onClick={() => void reject(view.pending!)}>Enviar y pedir otra versión</button>
                <button type="button" className="btn" disabled={busy} onClick={() => { setRejecting(false); setComment(''); }}>Cancelar</button>
              </div>
            </div>
          )}
        </section>
      )}

      {view?.approved && (
        <section className="kit-block" aria-label="Kit aprobado">
          <h4>Kit aprobado · versión {view.approved.version}</h4>
          <Version kit={view.approved} />
        </section>
      )}

      {view && !view.approved && !view.pending && <p className="src">Esta marca todavía no tiene kit.</p>}

      {orderOpen && <p className="kit-order" role="status">Encargo en curso: Claude está diseñando el kit. Cuando lo deje, aparecerá aquí para que lo apruebes.</p>}

      {view?.pending && !orderOpen && (
        <p className="kit-order src" role="status">Mientras haya un kit pendiente no se puede pedir otro: apruébalo o pide otra versión.</p>
      )}

      {view && !orderOpen && !view.pending && (
        <div className="kit-request" role="group" aria-label="Pedir kit">
          {promotions.length === 0 && (
            <p className="src">Esta marca aún no tiene ninguna promoción: crea una para poder pedir el kit de marca (el encargo cuelga de una promoción).</p>
          )}
          {promotions.length === 1 && <p className="src">El encargo irá con la promoción «{promotions[0].name}».</p>}
          {promotions.length > 1 && (
            <label className="field"><span>Promoción de la que cuelga el encargo (obligatoria)</span>
              <select value={promotion} onChange={e => setPromotion(e.target.value)}>
                <option value="">Elige una promoción</option>
                {promotions.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select></label>
          )}
          {promotions.length > 0 && (
            <fieldset className="kit-pieces">
              <legend>Piezas</legend>
              {PIECES.filter(p => carried.includes(p.code)).map(p => (
                <label key={p.code} className="check"><input type="checkbox" checked={pieces.has(p.code)} onChange={() => toggle(p.code)} /> {p.label}</label>
              ))}
            </fieldset>
          )}
          <div className="form-actions">
            <button type="button" className="btn primary" disabled={!canRequest} onClick={() => void request()}>Pedir kit</button>
          </div>
        </div>
      )}
    </div>
  );
}
