import type { Recommendation, RecommendationTarget } from './api/schema';

/** Un destino de navegación solo es válido si trae los datos que necesita su pantalla. */
export function canOpen(t?: RecommendationTarget): t is RecommendationTarget {
  if (!t) return false;
  if (t.screen === 'BRAND_FOLDERS' || t.screen === 'PUBLORA' || t.screen === 'NEW_PROMOTION') return !!t.brand_id;
  if (t.screen === 'PROPOSALS') return !!t.promotion_id;
  return true;
}

const BUTTON_LABEL: Record<RecommendationTarget['screen'], string> = {
  BRAND_FOLDERS: 'Ir a las carpetas de la marca', PROPOSALS: 'Ir a las propuestas', PUBLORA: 'Ir a Publora',
  BACKUPS: 'Ir a Copias', NEW_PROMOTION: 'Dar de alta la promoción',
};

/** «Qué hacer ahora»: hasta 3 recomendaciones del backend. Sin recomendaciones no se pinta nada. */
export function NextSteps({ items, onOpen, onDismissPublora }: {
  items: Recommendation[]; onOpen: (target: RecommendationTarget) => void;
  /** «No uso Publora» para la marca de la recomendación PUBLORA_MISSING. */
  onDismissPublora?: (brandId: string) => void;
}) {
  if (items.length === 0) return null;
  return (
    <section className="attn-card next-card" aria-label="Qué hacer ahora">
      <h2>Qué hacer ahora</h2>
      {items.map(r => {
        const body = <span><b>{r.title}</b>{r.detail}</span>;
        const target = r.target;
        const main = canOpen(target)
          ? (
            <button className="aitem" onClick={() => onOpen(target)} aria-label={`${r.title}. ${BUTTON_LABEL[target.screen]}`}>
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
    </section>
  );
}

/** Tiempo del aviso de confirmación: más largo si el mensaje lleva siguiente paso o estado de propuestas. */
export function toastDuration(base: string, text: string): number {
  return text !== base ? 6000 : 2800;
}

export interface SavedRef { promotionId?: string; brandId?: string }

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
    // El envío ocurre cada 30 s: justo al guardar puede no haber nada todavía. No se inventa hora.
    return `${base}. Te avisaremos aquí del estado de sus propuestas en «Qué hacer ahora»`;
  }
  if (ref.brandId) {
    const next = recommendations.find(r => r.target?.brand_id === ref.brandId);
    if (next) return `${base} · Siguiente paso: ${next.title}`;
  }
  return base;
}
