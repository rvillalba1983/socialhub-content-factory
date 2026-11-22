import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { api } from './api';
import { dayLabel, hhmm, parseDay, shortDate, type Publication, type PublicationOutcome, type SlotProposal } from './model';

const NETWORK = { INSTAGRAM: 'Instagram', TIKTOK: 'TikTok' } as const;

/** «mié 25 nov, 19:00». */
const dayAndTime = (day: string, time: string) => `${dayLabel(parseDay(day))}, ${hhmm(time)}`;

/**
 * Descartar una publicación (la cancelación de siempre, con motivo «Repetido») y moverla a la siguiente hora libre, los dos
 * con diálogo de confirmación: nada se cambia sin que Raúl lo confirme. Sirve al centro de control y a «Qué hacer ahora».
 */
export function usePublicationActions(publications: Publication[], notify: (message: string) => void) {
  const [discarding, setDiscarding] = useState<string | null>(null);
  const [moving, setMoving] = useState<string | null>(null);
  const find = (id: string | null) => publications.find(p => p.id === id);
  const discard = useCallback((id: string) => setDiscarding(id), []);
  const move = useCallback((id: string) => setMoving(id), []);
  const dialogs: ReactNode = (
    <>
      {discarding && find(discarding) && (
        <DiscardDialog publication={find(discarding)!} onClose={() => setDiscarding(null)}
          onDone={m => { setDiscarding(null); notify(m); }} />
      )}
      {moving && find(moving) && (
        <MoveDialog publication={find(moving)!} onClose={() => setMoving(null)} onDone={m => { setMoving(null); notify(m); }} />
      )}
    </>
  );
  return { discard, move, dialogs };
}

function Dialog({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="dlg-back">
      <div className="card dlg" role="dialog" aria-modal="true" aria-label={title}>
        <h3>{title}</h3>
        {children}
      </div>
    </div>
  );
}

function DiscardDialog({ publication: p, onClose, onDone }: { publication: Publication; onClose: () => void; onDone: (m: string) => void }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const confirm = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.changePublicationStatus(p.id, 'CANCELLED', 'REPEATED', 'Duplicada: descartada desde el aviso de duplicados');
      onDone(`Descartada · ${p.title}`);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); setBusy(false); }
  };
  return (
    <Dialog title="¿Descartar esta publicación?">
      <p><b>{p.title}</b> · {NETWORK[p.platform]} · {dayAndTime(p.scheduled_on, p.scheduled_time)}</p>
      <p className="src">Pasa a «Cancelada» y queda en la memoria editorial como «Repetido». Si estaba programada en una vía, se quita de allí.</p>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="form-actions">
        <button type="button" className="btn primary" disabled={busy} onClick={() => void confirm()}>Descartar esta</button>
        <button type="button" className="btn" onClick={onClose}>Volver</button>
      </div>
    </Dialog>
  );
}

function MoveDialog({ publication: p, onClose, onDone }: { publication: Publication; onClose: () => void; onDone: (m: string) => void }) {
  const [slot, setSlot] = useState<SlotProposal | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [acceptEnd, setAcceptEnd] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let live = true;
    api.nextFreeSlot(p.id).then(s => { if (live) setSlot(s); }).catch(e => { if (live) setError(e instanceof Error ? e.message : String(e)); });
    return () => { live = false; };
  }, [p.id]);
  const confirm = async () => {
    if (!slot) return;
    setBusy(true);
    setError(null);
    try {
      await api.moveToFreeSlot(p.id, { new_on: slot.new_on, new_time: slot.new_time, accept_after_end: acceptEnd });
      onDone(`Movida a ${dayAndTime(slot.new_on, slot.new_time)} · ${p.title}`);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); setBusy(false); }
  };
  return (
    <Dialog title="Mover a la siguiente hora libre">
      <p><b>{p.title}</b> · {NETWORK[p.platform]}</p>
      {!slot && !error && <p>Calculando la siguiente hora libre…</p>}
      {slot && (
        <>
          <p className="src">{slot.reason === 'LOST_MARGIN'
            ? `Ya no deja el margen mínimo de publicación (${slot.margin_minutes} min).`
            : 'Choca con otra publicación de la misma marca y red (menos de 30 min de separación).'}</p>
          <dl className="move-times">
            <dt>Hora actual</dt><dd>{dayAndTime(slot.current_on, slot.current_time)}</dd>
            <dt>Hora nueva</dt><dd><b>{dayAndTime(slot.new_on, slot.new_time)}</b></dd>
          </dl>
          {slot.changes_day && <p className="warn" role="note"><b>Cambia de día:</b> pasa del {shortDate(slot.current_on)} al {shortDate(slot.new_on)}.</p>}
          {slot.after_promotion_end && (
            <label className="warn field-inline">
              <input type="checkbox" checked={acceptEnd} onChange={e => setAcceptEnd(e.target.checked)} />
              <span><b>Pasa del fin de la promoción</b> ({slot.promotion_ends_on ? shortDate(slot.promotion_ends_on) : '—'}). Acepto que quede después.</span>
            </label>
          )}
        </>
      )}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="form-actions">
        <button type="button" className="btn primary" disabled={!slot || busy || (slot.after_promotion_end && !acceptEnd)}
          onClick={() => void confirm()}>Mover</button>
        <button type="button" className="btn" onClick={onClose}>Volver</button>
      </div>
    </Dialog>
  );
}

const RESULT_CLASS: Record<PublicationOutcome['result'], string> = {
  PUBLISHED: 's-ok', FAILED: 's-err', DELAYED: 's-block', PENDING: 's-wait', UNCONFIRMED: 's-idle',
};

const HOUR = new Intl.DateTimeFormat('es-ES', { timeZone: 'Europe/Madrid', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

/** Resultado de una publicación: «Publicada ✓ / Falló / Retrasada / Pendiente / Sin confirmar», con hora real, vía y enlace. */
export function OutcomeBadge({ outcome }: { outcome?: PublicationOutcome }) {
  if (!outcome) return null;
  const mark = outcome.result === 'PUBLISHED' ? ' ✓' : '';
  const when = outcome.at ? `${outcome.at_source === 'CONFIRMATION' ? 'confirmada ' : ''}${HOUR.format(new Date(outcome.at))}` : null;
  return (
    <span className="outcome" title={[outcome.reason, outcome.advice].filter(Boolean).join(' · ') || undefined}>
      <span className={`pill ${RESULT_CLASS[outcome.result]}`}><span>{outcome.label}{mark}</span></span>
      {when && <small className="mono"> {when}</small>}
      {outcome.route && <small> · {outcome.route}</small>}
      {outcome.url && <> <a href={outcome.url} target="_blank" rel="noreferrer">Ver post</a></>}
      {(outcome.result === 'FAILED' || outcome.result === 'DELAYED' || outcome.result === 'UNCONFIRMED') && outcome.reason && (
        <small className="outcome-why">{outcome.reason}{outcome.advice ? `. ${outcome.advice}` : ''}</small>
      )}
    </span>
  );
}

/** Cuántas publicaciones hay en cada resultado (las canceladas no tienen). */
export function outcomeCounts(publications: Publication[]): Record<PublicationOutcome['result'], number> {
  const counts = { PUBLISHED: 0, FAILED: 0, DELAYED: 0, PENDING: 0, UNCONFIRMED: 0 };
  for (const p of publications) if (p.outcome) counts[p.outcome.result]++;
  return counts;
}
