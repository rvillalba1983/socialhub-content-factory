/**
 * Límites del lienzo del mapa: el contenido (estaciones, anillo, tarjetas) nunca sale del todo de la vista y no se puede
 * alejar más de lo que se ve al «Encuadrar todo».
 */
export interface View { s: number; tx: number; ty: number }
/** Caja del contenido en coordenadas del lienzo. */
export interface Box { x0: number; y0: number; x1: number; y1: number }
/** Parte de la vista que se ve (sin el panel lateral). */
export interface Area { w: number; h: number }

export const MAX_SCALE = 3.5;
export const HARD_MIN_SCALE = 0.08;

export const validBox = (b: Box | null | undefined): b is Box =>
  !!b && Number.isFinite(b.x0 + b.y0 + b.x1 + b.y1) && b.x1 - b.x0 > 0 && b.y1 - b.y0 > 0;

/** Zoom con el que «Encuadrar todo» deja el contenido: lo más pequeño que se permite. */
export function fitScale(b: Box, a: Area, padTop: number, padBottom: number, pad = 16, cap = 0.7): number {
  const w = b.x1 - b.x0, h = b.y1 - b.y0, ah = a.h - padTop - padBottom;
  return Math.max(HARD_MIN_SCALE, Math.min(MAX_SCALE, Math.min((a.w - pad * 2) / w, ah / h, cap)));
}

/**
 * Acota la vista: zoom entre el mínimo (el encuadre) y el máximo, y desplazamiento tal que el borde del contenido nunca
 * pase del centro de la vista (siempre se ve al menos la mitad, en cada eje, cuando es más grande que la vista).
 */
export function clampView(v: View, b: Box, a: Area, minScale: number): View {
  const s = Math.max(Math.min(minScale, MAX_SCALE), Math.min(MAX_SCALE, v.s));
  const cx = a.w / 2, cy = a.h / 2;
  // borde derecho >= centro y borde izquierdo <= centro (y lo mismo en vertical)
  const tx = Math.min(cx - b.x0 * s, Math.max(cx - b.x1 * s, v.tx));
  const ty = Math.min(cy - b.y0 * s, Math.max(cy - b.y1 * s, v.ty));
  return { s, tx, ty };
}
