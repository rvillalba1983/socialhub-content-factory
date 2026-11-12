import { useState } from 'react';
import type { Recommendation, RecommendationTarget } from './api/schema';

/** Un destino de navegación solo es válido si trae los datos que necesita su pantalla. */
export function canOpen(t?: RecommendationTarget): t is RecommendationTarget {
  if (!t) return false;
  if (t.screen === 'BRAND_FOLDERS' || t.screen === 'PUBLORA' || t.screen === 'PUBLER' || t.screen === 'NEW_PROMOTION') return !!t.brand_id;
  if (t.screen === 'PROPOSALS') return !!t.promotion_id;
  return true;
}

const BUTTON_LABEL: Record<RecommendationTarget['screen'], string> = {
  BRAND_FOLDERS: 'Ir a las carpetas de la marca', PROPOSALS: 'Ir a las propuestas', PUBLORA: 'Ir a Publora', PUBLER: 'Indicar la cuenta de Publer',
  BACKUPS: 'Ir a Copias', NEW_PROMOTION: 'Dar de alta la promoción', CLAUDE: 'Ir a Claude',
};

function buttonLabel(t: RecommendationTarget): string {
  return t.screen === 'NEW_PROMOTION' && t.promotion_id ? 'Abrir la promoción' : BUTTON_LABEL[t.screen];
}

/**
 * Dentro de la ficha de una marca, sus avisos ya salen en «Te falta por completar» (`BrandNotices`), así que se quitan de
 * «Qué hacer ahora»: las carpetas sin crear (BRAND_FOLDERS_MISSING) y la cuenta de Publer de Instagram o TikTok
 * (PUBLER_ACCOUNT_MISSING, cuyo id acaba en la red). Identidad, Instagram API y «publico» no tienen recomendación.
 * Lo demás (propuestas retenidas, Publora, otras marcas…) se queda.
 */
export function withoutBrandNotices(items: Recommendation[], brandId: string | null | undefined): Recommendation[] {
  if (!brandId) return items;
  return items.filter(r => {
    if (r.target?.brand_id !== brandId) return true;
    if (r.kind === 'BRAND_FOLDERS_MISSING') return false;
    if (r.kind === 'PUBLER_ACCOUNT_MISSING') return !/:(INSTAGRAM|TIKTOK)$/.test(r.id);
    return true;
  });
}

/** Cuántas recomendaciones se ven sin desplegar. */
export const VISIBLE = 3;

/** «Qué hacer ahora»: 3 a la vista y «Ver N más» para el resto. Sin recomendaciones no se pinta nada. */

export function NextSteps({ items, onOpen, onDismissPublora }: {
  items: Recommendation[]; onOpen: (target: RecommendationTarget) => void;
  /** «No uso Publora» para la marca de la recomendación PUBLORA_MISSING. */
  onDismissPublora?: (brandId: string) => void;
}) {
  // El estado vive en el componente, que no se desmonta al recargar los datos
  const [expanded, setExpanded] = useState(false);
  if (items.length === 0) return null;
  const extra = items.length - VISIBLE;
  const shown = extra > 0 && !expanded ? items.slice(0, VISIBLE) : items;
  return (
    <section className="next-strip" aria-label="Qué hacer ahora">
      <h2>Qué hacer ahora</h2>
      <div className="next-list">
      {shown.map(r => {
        const body = <span><b>{r.title}</b>{r.detail}</span>;
        const target = r.target;
        const main = canOpen(target)
          ? (
            <button className="aitem" onClick={() => onOpen(target)} aria-label={`${r.title}. ${buttonLabel(target)}`}>
              {body}<span className="go" aria-hidden="true">→</span>
            </button>
          )
          : <div className="aitem static">{body}</div>;
        const brandId = r.kind === 'PUBLORA_MISSING' ? target?.brand_id : undefined;
        // El botón secundario va al lado, nunca dentro del principal (no se anidan botones).
        return brandId && onDismissPublora
          ? (
            <div key={r.id} className="aitem-row">
              {main}
              <button type="button" className="btn aitem-side" onClick={() => onDismissPublora(brandId)}
                aria-label={`No uso Publora (${r.title})`}>No uso Publora</button>
            </div>
          )
          : <div key={r.id} className="aitem-wrap">{main}</div>;
      })}
      </div>
      {extra > 0 && (
        <button type="button" className="btn next-more" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Ver menos' : `Ver ${extra} más`}
        </button>
      )}
    </section>
  );
}

/** Tiempo del aviso de confirmación: más largo si el mensaje lleva siguiente paso o estado de propuestas. */
export function toastDuration(base: string, text: string): number {
  return text !== base ? 6000 : 2800;
}

export interface SavedRef {
  promotionId?: string; brandId?: string;
  /** Estado de la promoción guardada (PLANNED, ACTIVE, PAUSED o FINISHED). */
  status?: string;
}

/**
 * Mensaje al guardar con el paso siguiente, sacado de las recomendaciones del siguiente estado de la fábrica.
 * Las recomendaciones de una promoción traen su `promotion_id` y, las de propuestas en camino, la hora en `eta_text`;
 * no se deduce nada del id ni del título.
 */
export function savedMessage(base: string, ref: SavedRef, recommendations: Recommendation[]): string {
  if (ref.promotionId) {
    const held = recommendations.find(r => r.kind === 'HELD_PROPOSALS' && r.promotion_id === ref.promotionId);
    if (held) return `${base}. Las propuestas no pueden salir${held.detail ? `: ${held.detail}` : ''}`;
    const pending = recommendations.find(r => r.kind === 'PROPOSALS_PENDING' && r.promotion_id === ref.promotionId);
    if (pending?.eta_text) return `${base}. Propuestas pedidas: llegarán ${pending.eta_text}`;
    // La primera semana se crea en una ronda de cada 10 min y se envía en otra de 30 s: justo al guardar puede no haber
    // nada todavía, así que no se inventa hora. Y solo una promoción activa pide propuestas: si está planificada, en
    // pausa o terminada, no se promete ningún aviso.
    if (ref.status === 'ACTIVE') return `${base}. Te avisaremos aquí del estado de sus propuestas en «Qué hacer ahora»`;
    return base;
  }
  if (ref.brandId) {
    const next = recommendations.find(r => r.target?.brand_id === ref.brandId);
    if (next) return `${base} · Siguiente paso: ${next.title}`;
  }
  return base;
}
