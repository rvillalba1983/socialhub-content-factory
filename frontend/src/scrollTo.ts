/** Desplazamiento suave, salvo que la persona haya pedido menos movimiento en su sistema. */
export function smoothBehavior(): ScrollBehavior {
  try {
    return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
  } catch { return 'auto'; }
}

/** Lleva la página hasta el elemento con ese id. Con `focus`, pone el foco en su primer campo editable (sin saltar de golpe). */
export function scrollToId(id: string, focus = false) {
  const el = document.getElementById(id);
  if (!el) return;
  el.scrollIntoView?.({ block: focus ? 'start' : 'center', behavior: smoothBehavior() });
  if (focus) {
    const field = el.querySelector<HTMLElement>('input:not([disabled]):not([type=hidden]),select:not([disabled]),textarea:not([disabled])');
    (field ?? el).focus({ preventScroll: true });
  }
}
