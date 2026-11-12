import { describe, expect, it } from 'vitest';
import { clampView, fitScale, validBox, type Area, type Box } from './viewLimits';

const box: Box = { x0: 100, y0: 200, x1: 2100, y1: 1300 };
const area: Area = { w: 1200, h: 800 };

describe('límites del mapa', () => {
  it('el zoom mínimo es el del encuadre y no se puede alejar más', () => {
    const min = fitScale(box, area, 58, 52);
    expect(min).toBeCloseTo(Math.min((1200 - 32) / 2000, (800 - 110) / 1100, 0.7), 5);
    expect(clampView({ s: 0.01, tx: 0, ty: 0 }, box, area, min).s).toBe(min);
    expect(clampView({ s: 99, tx: 0, ty: 0 }, box, area, min).s).toBe(3.5);
  });

  it('arrastrar a la derecha o abajo deja el borde del contenido en el centro como mucho', () => {
    const v = clampView({ s: 0.5, tx: 99999, ty: 99999 }, box, area, 0.3);
    expect(v.tx + box.x0 * 0.5).toBeCloseTo(area.w / 2, 5);
    expect(v.ty + box.y0 * 0.5).toBeCloseTo(area.h / 2, 5);
  });

  it('arrastrar a la izquierda o arriba deja el otro borde en el centro como mucho', () => {
    const v = clampView({ s: 0.5, tx: -99999, ty: -99999 }, box, area, 0.3);
    expect(v.tx + box.x1 * 0.5).toBeCloseTo(area.w / 2, 5);
    expect(v.ty + box.y1 * 0.5).toBeCloseTo(area.h / 2, 5);
  });

  it('una vista que ya está dentro de los límites no cambia', () => {
    const v = { s: 0.6, tx: 50, ty: 40 };
    expect(clampView(v, box, area, 0.3)).toEqual(v);
  });

  it('siempre queda contenido a la vista, con cualquier zoom y desplazamiento', () => {
    for (const s of [0.2, 0.5, 1, 2.5]) for (const t of [-1e5, -3000, 0, 3000, 1e5]) {
      const v = clampView({ s, tx: t, ty: t }, box, area, 0.2);
      expect(v.tx + box.x1 * v.s).toBeGreaterThanOrEqual(area.w / 2 - 1e-6);
      expect(v.tx + box.x0 * v.s).toBeLessThanOrEqual(area.w / 2 + 1e-6);
      expect(v.ty + box.y1 * v.s).toBeGreaterThanOrEqual(area.h / 2 - 1e-6);
      expect(v.ty + box.y0 * v.s).toBeLessThanOrEqual(area.h / 2 + 1e-6);
    }
  });

  it('una caja sin medir no es válida', () => {
    expect(validBox(null)).toBe(false);
    expect(validBox({ x0: 0, y0: 0, x1: 0, y1: 0 })).toBe(false);
    expect(validBox(box)).toBe(true);
  });
});
