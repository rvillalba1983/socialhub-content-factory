import { describe, expect, it } from 'vitest';
import { boxForLevel, clampView, fitScale, validBox, type Area, type Box } from './viewLimits';

const box: Box = { x0: 100, y0: 200, x1: 2100, y1: 1300 };
const area: Area = { w: 1200, h: 800 };

describe('límites del mapa', () => {
  it('el zoom mínimo es el del encuadre y no se puede alejar más', () => {
    const min = fitScale(box, area, 58, 52);
    expect(min).toBeCloseTo(Math.min((1200 - 32) / 2000, (800 - 110) / 1100, 0.7), 5);
    expect(clampView({ s: 0.01, tx: 0, ty: 0 }, box, area, min).s).toBe(min);
    expect(clampView({ s: 99, tx: 0, ty: 0 }, box, area, min).s).toBe(3.5);
  });

  it('el zoom mínimo sigue al alto disponible: con la franja de recomendaciones es el del encuadre más bajo, sin ella el más alto', () => {
    const tall = fitScale(box, { w: 3000, h: 900 }, 58, 52);
    const short = fitScale(box, { w: 3000, h: 800 }, 58, 52);
    expect(short).toBeLessThan(tall);
    // con el alto nuevo, el encuadre y el límite son exactamente el mismo número
    expect(clampView({ s: 0.01, tx: 0, ty: 0 }, box, { w: 3000, h: 800 }, short).s).toBe(short);
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

  it('cada nivel de detalle se limita con su propia caja, y la de reserva si no se ha medido', () => {
    const l1: Box = { x0: 100, y0: 200, x1: 2100, y1: 1300 };
    const l3: Box = { x0: 60, y0: 180, x1: 2400, y1: 1900 };
    expect(boxForLevel(1, { 1: l1, 3: l3 }, null)).toEqual(l1);
    expect(boxForLevel(3, { 1: l1, 3: l3 }, null)).toEqual(l3);
    expect(boxForLevel(2, { 1: l1, 3: l3 }, l1)).toEqual(l1);
    expect(boxForLevel(2, { 2: { x0: 0, y0: 0, x1: 0, y1: 0 } }, null)).toBeNull();
  });

  it('con la caja del nivel 3 mayor que la del nivel 1 se llega a ver el borde de todo el contenido', () => {
    const l1: Box = { x0: 100, y0: 200, x1: 2100, y1: 1300 };
    const l3: Box = { x0: 60, y0: 180, x1: 2400, y1: 1900 };
    const s = 1.7;
    const right = clampView({ s, tx: -1e6, ty: -1e6 }, boxForLevel(3, { 1: l1, 3: l3 }, l1)!, area, 0.3);
    // El borde derecho e inferior del nivel 3 llegan al centro: se ve entero hasta el borde
    expect(right.tx + l3.x1 * s).toBeCloseTo(area.w / 2, 5);
    expect(right.ty + l3.y1 * s).toBeCloseTo(area.h / 2, 5);
    // Con la caja del nivel 1 el borde del nivel 3 quedaba por debajo del centro, sin poder verse
    const old = clampView({ s, tx: -1e6, ty: -1e6 }, l1, area, 0.3);
    expect(old.ty + l3.y1 * s).toBeGreaterThan(area.h / 2 + 100);
    const left = clampView({ s, tx: 1e6, ty: 1e6 }, l3, area, 0.3);
    expect(left.tx + l3.x0 * s).toBeCloseTo(area.w / 2, 5);
    expect(left.ty + l3.y0 * s).toBeCloseTo(area.h / 2, 5);
  });

  it('con la caja del nivel 3 menor que la del nivel 1 el centro no sale del contenido que se ve', () => {
    const l1: Box = { x0: 100, y0: 200, x1: 2100, y1: 1300 };
    const l3: Box = { x0: 300, y0: 300, x1: 1500, y1: 1000 };
    const s = 3;
    const v = clampView({ s, tx: -1e6, ty: -1e6 }, boxForLevel(3, { 1: l1, 3: l3 }, l1)!, area, 0.3);
    // El centro de la vista cae en el borde inferior derecho del contenido del nivel 3, no más allá
    expect((area.w / 2 - v.tx) / s).toBeCloseTo(l3.x1, 5);
    expect((area.h / 2 - v.ty) / s).toBeCloseTo(l3.y1, 5);
  });

  it('el zoom mínimo sale de la caja del nivel 1', () => {
    const l1: Box = { x0: 100, y0: 200, x1: 2100, y1: 1300 };
    const l3: Box = { x0: 60, y0: 180, x1: 2400, y1: 1900 };
    const min = fitScale(l1, area, 58, 52);
    expect(clampView({ s: 0.01, tx: 0, ty: 0 }, l3, area, min).s).toBe(min);
    expect(min).toBeGreaterThan(fitScale(l3, area, 58, 52));
  });
});
