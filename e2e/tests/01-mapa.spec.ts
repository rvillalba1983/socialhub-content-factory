import { expect, openMap, test } from '../support/fixtures';
import type { Page } from '@playwright/test';

/** decisiones.md promete 12 px como mínimo con el mapa alejado (1366x768 es el caso peor). */
const MIN_LEGIBLE_PX = 12;

/** Zoom actual del mapa (escala del lienzo), leído de su transformación CSS. */
const scaleOf = (page: Page) => page.evaluate(() => {
  const world = document.querySelector('.world') as HTMLElement;
  return new DOMMatrix(getComputedStyle(world).transform).a;
});

/** Cuántas estaciones del anillo tienen alguna parte dentro de la ventana. */
const stationsInView = (page: Page) => page.evaluate(() => {
  const w = window.innerWidth, h = window.innerHeight;
  return [...document.querySelectorAll('.node')].filter(el => {
    const r = el.getBoundingClientRect();
    return r.right > 0 && r.bottom > 0 && r.left < w && r.top < h;
  }).length;
});

/** Espera a que acabe la animación de «Encuadrar todo» (el zoom deja de cambiar) y devuelve el zoom final. */
async function settledScale(page: Page) {
  let last = -1;
  await expect.poll(async () => { const now = await scaleOf(page); const same = Math.abs(now - last) < 1e-4; last = now; return same && now > 0.1; }, { intervals: [300] }).toBe(true);
  return last;
}

/**
 * ¿El centro de la ventana del mapa cae dentro de la caja del contenido (estaciones, anillo, tarjetas)? Es lo que
 * promete la app: el borde del contenido nunca pasa del centro de la vista (dentro del anillo puede haber huecos vacíos).
 */
const centerInsideContent = (page: Page) => page.evaluate(() => {
  const vp = document.querySelector('main.vp')!.getBoundingClientRect();
  const cx = vp.left + vp.width / 2, cy = vp.top + vp.height / 2;
  const box = { l: Infinity, t: Infinity, r: -Infinity, b: -Infinity };
  document.querySelectorAll('.st,.hub,.res,.node,.seglabel').forEach(el => {
    const q = el.getBoundingClientRect();
    if (!q.width || !q.height) return; // oculto en este nivel de zoom
    box.l = Math.min(box.l, q.left); box.t = Math.min(box.t, q.top); box.r = Math.max(box.r, q.right); box.b = Math.max(box.b, q.bottom);
  });
  return box.l <= cx + 2 && box.r >= cx - 2 && box.t <= cy + 2 && box.b >= cy - 2;
});

/** Letra más pequeña, en píxeles de pantalla, de todo el texto visible del lienzo. */
const smallestTextOnScreen = (page: Page) => page.evaluate(() => {
  const scale = new DOMMatrix(getComputedStyle(document.querySelector('.world')!).transform).a;
  let min = Infinity, which = '';
  const walker = document.createTreeWalker(document.querySelector('.world')!, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const text = node.textContent?.trim();
    const el = node.parentElement;
    if (!text || !el || !el.getClientRects().length) continue; // oculto (display:none)
    const style = getComputedStyle(el);
    if (style.visibility === 'hidden' || style.opacity === '0') continue;
    const px = parseFloat(style.fontSize) * scale;
    if (px < min) { min = px; which = text.slice(0, 40); }
  }
  return { min, which };
});

test.describe('Mapa', () => {
  test.beforeAll(async ({ api }) => {
    // Una marca y una promoción activa, para que el centro del anillo tenga su tarjeta
    // (si una prueba falla, Playwright reinicia el proceso y repite este gancho: no se duplica nada)
    if ((await api.factory()).promotions.some(p => p.name === 'Promoción del mapa')) return;
    const brand = await api.createBrand('Marca del mapa', '@marca_mapa', '#7A3FB5');
    await api.createPromotion(brand.id, 'Promoción del mapa');
  });

  test('carga con las 8 estaciones, la promoción y los controles', async ({ page }) => {
    await openMap(page);
    const names = ['Investigación', 'Selección y estrategia', 'Generación de contenido', 'Revisión y calidad',
      'Programación', 'Publicación', 'Análisis de resultados', 'Retroalimentación'];
    for (const name of names) {
      await expect(page.locator('article.st').filter({ has: page.getByRole('heading', { name, exact: true }) })).toBeVisible();
    }
    await expect(page.locator('.node')).toHaveCount(8);
    // El centro: tarjeta de la promoción y «Necesita atención»
    await expect(page.getByRole('article', { name: 'Abrir el centro de control de Promoción del mapa' })).toContainText('Marca del mapa');
    await expect(page.getByRole('region', { name: 'Necesita tu atención' })).toBeVisible();
    // «Encuadrar todo» al arrancar: todo el anillo cabe en la ventana
    await expect(page.locator('main.vp')).toHaveAttribute('data-lod', '1');
    const box = await page.locator('main.vp').boundingBox();
    for (const node of await page.locator('.node').all()) {
      const r = (await node.boundingBox())!;
      expect(r.x).toBeGreaterThanOrEqual(box!.x - 1);
      expect(r.x + r.width).toBeLessThanOrEqual(box!.x + box!.width + 1);
      expect(r.y).toBeGreaterThanOrEqual(box!.y - 1);
      expect(r.y + r.height).toBeLessThanOrEqual(box!.y + box!.height + 1);
    }
  });

  test('«Encuadrar todo» devuelve la vista tras acercar y mover', async ({ page }) => {
    await openMap(page);
    const fitted = await settledScale(page);
    const before = await page.locator('.node').first().boundingBox();

    await page.getByRole('button', { name: 'Acercar' }).click();
    await page.getByRole('button', { name: 'Acercar' }).click();
    await expect.poll(() => scaleOf(page)).toBeGreaterThan(fitted * 1.5);
    await page.mouse.move(600, 500);
    await page.mouse.down();
    await page.mouse.move(500, 450, { steps: 5 });
    await page.mouse.up();

    await page.getByRole('button', { name: 'Encuadrar todo' }).click();
    // El encuadre puede variar un poco entre dos medidas (la franja «Qué hacer ahora» cambia el alto del mapa y las
    // recomendaciones se actualizan solas): se admite un 5 % de zoom y 25 px de posición, no una igualdad exacta
    await expect.poll(async () => Math.abs((await scaleOf(page)) - fitted)).toBeLessThan(fitted * 0.05);
    await expect.poll(async () => Math.abs(((await page.locator('.node').first().boundingBox())!.x) - before!.x)).toBeLessThan(25);
    await expect.poll(async () => Math.abs(parseInt((await page.locator('.zpct').innerText()), 10) - Math.round(fitted * 100))).toBeLessThanOrEqual(2);
  });

  test('límites de arrastre y de zoom: el mapa nunca queda vacío', async ({ page }) => {
    await openMap(page);
    const fitted = await settledScale(page);
    const vp = (await page.locator('main.vp').boundingBox())!;
    const cx = vp.x + vp.width / 2, cy = vp.y + vp.height - 40;

    // Arrastre brutal hacia cada lado: siempre queda contenido a la vista
    for (const [dx, dy] of [[4000, 0], [-4000, 0], [0, 3000], [0, -3000], [4000, 3000], [-4000, -3000]]) {
      await page.getByRole('button', { name: 'Encuadrar todo' }).click();
      await page.waitForTimeout(600);
      await page.mouse.move(cx, cy);
      await page.mouse.down();
      for (let i = 1; i <= 8; i++) await page.mouse.move(cx + (dx * i) / 8, cy + (dy * i) / 8);
      await page.mouse.up();
      await expect.poll(() => stationsInView(page), { message: `tras arrastrar (${dx}, ${dy})` }).toBeGreaterThan(0);
    }

    // Alejar con la rueda hasta el límite: no baja del encuadre
    await page.mouse.move(vp.x + vp.width / 2, vp.y + vp.height / 2);
    for (let i = 0; i < 6; i++) await page.mouse.wheel(0, 2000);
    await expect.poll(() => scaleOf(page)).toBeGreaterThanOrEqual(fitted * 0.999);
    await expect.poll(() => stationsInView(page)).toBe(8);

    // Acercar con la rueda hasta el límite: no pasa del 350 %
    for (let i = 0; i < 8; i++) await page.mouse.wheel(0, -2000);
    await expect.poll(() => scaleOf(page)).toBeLessThanOrEqual(3.5 + 0.001);
    await expect.poll(() => scaleOf(page)).toBeGreaterThan(3);
  });

  // Los límites del arrastre usan la caja del contenido del nivel de detalle que se ve (cada nivel tiene tarjetas de otro
  // tamaño): con el zoom al máximo el centro no sale del contenido y la pantalla no queda vacía.
  test('acercar al máximo con el mapa arrastrado al borde no deja la pantalla vacía', async ({ page }) => {
    await openMap(page);
    await settledScale(page);
    const vp = (await page.locator('main.vp').boundingBox())!;
    const cx = vp.x + vp.width / 2, cy = vp.y + vp.height - 40;
    await page.mouse.move(cx, cy);
    await page.mouse.down();
    for (let i = 1; i <= 8; i++) await page.mouse.move(cx - (4000 * i) / 8, cy - (3000 * i) / 8);
    await page.mouse.up();
    await page.mouse.move(vp.x + vp.width / 2, vp.y + vp.height / 2);
    for (let i = 0; i < 8; i++) await page.mouse.wheel(0, -2000);
    await expect.poll(() => scaleOf(page)).toBeGreaterThan(3);
    await expect.poll(() => centerInsideContent(page), { timeout: 3000 }).toBe(true);
  });

  test('con el zoom al máximo se llega a ver entero el borde del contenido en cada lado', async ({ page }) => {
    await openMap(page);
    await settledScale(page);
    const vp = (await page.locator('main.vp').boundingBox())!;
    await page.mouse.move(vp.x + vp.width / 2, vp.y + vp.height / 2);
    for (let i = 0; i < 8; i++) await page.mouse.wheel(0, -2000);
    await expect.poll(() => scaleOf(page)).toBeGreaterThan(3);
    // El ratón no puede salir de la ventana, así que se arrastra varias veces, de un extremo al otro, hasta el límite
    const x0 = vp.x + 40, x1 = vp.x + vp.width - 40, y0 = vp.y + 120, y1 = vp.y + vp.height - 100; // lejos de las franjas flotantes, donde no se arrastra
    const drag = async (fx: number, fy: number, tx: number, ty: number) => {
      await page.mouse.move(fx, fy);
      await page.mouse.down();
      await page.mouse.move(tx, ty, { steps: 2 });
      await page.mouse.up();
    };
    // Arrastre hasta el límite hacia cada lado: el borde del contenido de ese lado debe quedar dentro de la ventana
    for (const side of ['r', 'l', 'b', 't'] as const) {
      const series: string[] = [];
      let last = '', same = 0;
      for (let i = 0; i < 60 && same < 2; i++) {
        if (side === 'r') await drag(x1, y1, x0, y1);
        else if (side === 'l') await drag(x0, y1, x1, y1);
        else if (side === 'b') await drag(x0, y1, x0, y0);
        else await drag(x0, y0, x0, y1);
        const tf = await page.evaluate(() => getComputedStyle(document.querySelector('.world')!).transform);
        series.push(tf.replace('matrix(3.5, 0, 0, 3.5, ', '').replace(')', ''));
        same = tf === last ? same + 1 : 0; last = tf;
      }
      await page.waitForTimeout(800);
      const info = await page.evaluate((sd) => {
        const v = document.querySelector('main.vp')!.getBoundingClientRect();
        const w = document.querySelector('.world') as HTMLElement;
        let l = Infinity, t = Infinity, r = -Infinity, b = -Infinity, who = '';
        document.querySelectorAll('.st,.hub,.res,.node,.seglabel').forEach(el => {
          const q = el.getBoundingClientRect();
          if (!q.width || !q.height) return;
          l = Math.min(l, q.left); t = Math.min(t, q.top);
          if (q.right > r) { r = q.right; if (sd === 'r') who = `${el.className} ${Math.round(q.left)}-${Math.round(q.right)}`; }
          b = Math.max(b, q.bottom);
        });
        return { ok: sd === 'r' ? r <= v.right + 2 : sd === 'l' ? l >= v.left - 2 : sd === 'b' ? b <= v.bottom + 2 : t >= v.top - 2,
          detail: `${sd}: l=${Math.round(l)} t=${Math.round(t)} r=${Math.round(r)} b=${Math.round(b)} vp=${Math.round(v.right)}x${Math.round(v.bottom)} ${who} tf=${getComputedStyle(w).transform} lod=${document.querySelector('main.vp')!.getAttribute('data-lod')}` };
      }, side);
      expect(info.ok, info.detail + ' | ' + series.join(' ; ')).toBe(true);
    }
  });

  test('zoom semántico: al alejar solo hay texto legible, al acercar sale el detalle', async ({ page }) => {
    await openMap(page);
    const vp = page.locator('main.vp');
    await expect(vp).toHaveAttribute('data-lod', '1');

    // Alejado: nombre, estado, tareas y avisos; la descripción y el listado de tareas no están
    const investigation = page.locator('article.st[data-st]').first();
    await expect(investigation.locator('h3')).toBeVisible();
    await expect(investigation.locator('.st-sum')).toContainText(/tareas?/);
    await expect(investigation.locator('.st-what')).toBeHidden();
    await expect(page.locator('.attn-card .attn-more')).toBeHidden();
    // Legible en pantalla, también el número de avisos de una estación (`.badge`), que solo sale si hay avisos: se añade uno
    // de prueba a la primera estación (React no lo toca) y se quita al acabar de medir
    await page.locator('.node').first().evaluate(node => {
      const badge = document.createElement('span'); badge.className = 'badge'; badge.id = 'badge-de-prueba'; badge.textContent = '3'; node.append(badge);
    });
    await settledScale(page);
    await expect.poll(async () => (await smallestTextOnScreen(page)).min).toBeGreaterThanOrEqual(MIN_LEGIBLE_PX);
    const small = await smallestTextOnScreen(page);
    expect(small.min, `texto más pequeño: «${small.which}»`).toBeGreaterThanOrEqual(MIN_LEGIBLE_PX);
    await page.locator('#badge-de-prueba').evaluate(badge => badge.remove());

    // Acercado (nivel «Estación»): aparece la descripción de la estación
    await page.getByRole('group', { name: 'Nivel de detalle' }).getByRole('button', { name: 'Estación', exact: true }).click();
    await expect(vp).toHaveAttribute('data-lod', '2');
    await expect(investigation.locator('.st-what')).toBeVisible();
    await expect(page.locator('.zpct')).toHaveText('110%');

    // Y de vuelta: «Fábrica» encuadra y vuelve al nivel de poco texto
    await page.getByRole('group', { name: 'Nivel de detalle' }).getByRole('button', { name: 'Fábrica', exact: true }).click();
    await expect(vp).toHaveAttribute('data-lod', '1');
    await expect(investigation.locator('.st-what')).toBeHidden();
    await expect.poll(async () => (await smallestTextOnScreen(page)).min).toBeGreaterThanOrEqual(MIN_LEGIBLE_PX);
  });

  test('con una ventana más grande el texto alejado sigue siendo legible', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await openMap(page);
    await expect.poll(async () => (await smallestTextOnScreen(page)).min).toBeGreaterThanOrEqual(MIN_LEGIBLE_PX);
    await expect(page.locator('main.vp')).toHaveAttribute('data-lod', '1');
  });

  // Va el último del recorrido: deja 4 promociones en la app, y los recorridos siguientes (que abren promociones con un
  // clic en su tarjeta) ya trabajan con el centro del anillo lleno
  test('con 4 promociones las tarjetas caben en el anillo y no tapan estaciones', async ({ page, api }) => {
    const names = ['Promoción del mapa', 'Promoción del mapa B', 'Promoción del mapa C', 'Promoción del mapa D'];
    const brand = (await api.factory()).brands.find(b => b.name === 'Marca del mapa')!;
    for (const name of names.slice(1)) {
      if (!(await api.factory()).promotions.some(p => p.name === name)) await api.createPromotion(brand.id, name);
    }
    await openMap(page);
    await expect(page.locator('.promo')).toHaveCount(names.length);

    // Las tarjetas (promociones y «Necesita atención») quedan dentro del centro del anillo, sin pisarse entre sí ni pisar
    // las estaciones, a nivel «Fábrica» y a nivel «Estación»
    const overlaps = () => page.evaluate(() => {
      const box = (e: Element) => e.getBoundingClientRect();
      const cards = [...document.querySelectorAll('.promo,.attn-card')];
      const hub = box(document.querySelector('.hub')!);
      const others = [...document.querySelectorAll('.node,article.st')];
      const problems: string[] = [];
      const hit = (a: DOMRect, b: DOMRect) => a.left < b.right - 1 && b.left < a.right - 1 && a.top < b.bottom - 1 && b.top < a.bottom - 1;
      cards.forEach((c, i) => {
        const r = box(c), name = (c.querySelector('h2')?.textContent ?? c.className).trim();
        if (r.left < hub.left - 2 || r.right > hub.right + 2 || r.top < hub.top - 36 || r.bottom > hub.bottom + 2) problems.push(`${name} se sale del centro`);
        cards.slice(i + 1).forEach(d => { if (hit(r, box(d))) problems.push(`${name} pisa a ${(d.querySelector('h2')?.textContent ?? d.className).trim()}`); });
        others.forEach(o => { if (hit(r, box(o))) problems.push(`${name} pisa a ${o.getAttribute('aria-label') ?? o.className}`); });
      });
      return problems;
    });
    await expect(page.locator('main.vp')).toHaveAttribute('data-lod', '1');
    expect(await overlaps()).toEqual([]);
    // Cada promoción sigue abriéndose con un clic en su tarjeta
    for (const name of names) await expect(page.getByRole('article', { name: `Abrir el centro de control de ${name}`, exact: true })).toBeVisible();

    await page.getByRole('group', { name: 'Nivel de detalle' }).getByRole('button', { name: 'Estación', exact: true }).click();
    await expect(page.locator('main.vp')).toHaveAttribute('data-lod', '2');
    await settledScale(page);
    expect(await overlaps()).toEqual([]);
  });
});
