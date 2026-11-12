import { expect, openMap, test } from '../support/fixtures';
import type { Page } from '@playwright/test';

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
    await expect.poll(() => scaleOf(page)).toBeGreaterThan(0.1);
    const fitted = await scaleOf(page);
    const before = await page.locator('.node').first().boundingBox();

    await page.getByRole('button', { name: 'Acercar' }).click();
    await page.getByRole('button', { name: 'Acercar' }).click();
    await expect.poll(() => scaleOf(page)).toBeGreaterThan(fitted * 1.5);
    await page.mouse.move(600, 500);
    await page.mouse.down();
    await page.mouse.move(500, 450, { steps: 5 });
    await page.mouse.up();

    await page.getByRole('button', { name: 'Encuadrar todo' }).click();
    await expect.poll(async () => Math.abs((await scaleOf(page)) - fitted)).toBeLessThan(0.005);
    await expect.poll(async () => Math.abs(((await page.locator('.node').first().boundingBox())!.x) - before!.x)).toBeLessThan(2);
    await expect(page.locator('.zpct')).toHaveText(`${Math.round(fitted * 100)}%`);
  });

  test('límites de arrastre y de zoom: el mapa nunca queda vacío', async ({ page }) => {
    await openMap(page);
    await expect.poll(() => scaleOf(page)).toBeGreaterThan(0.1);
    const fitted = await scaleOf(page);
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
    await expect.poll(() => scaleOf(page)).toBeGreaterThanOrEqual(fitted - 0.005);
    await expect.poll(() => stationsInView(page)).toBe(8);

    // Acercar con la rueda hasta el límite: no pasa del 350 % y el contenido sigue a la vista
    for (let i = 0; i < 8; i++) await page.mouse.wheel(0, -2000);
    await expect.poll(() => scaleOf(page)).toBeLessThanOrEqual(3.5 + 0.001);
    await expect.poll(() => scaleOf(page)).toBeGreaterThan(3);
    await expect.poll(() => stationsInView(page)).toBeGreaterThan(0);
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
    // Ninguna letra visible baja de 12 px en pantalla (1366x768 es el caso peor)
    const small = await smallestTextOnScreen(page);
    expect(small.min, `texto más pequeño: «${small.which}»`).toBeGreaterThanOrEqual(12);

    // Acercado (nivel «Estación»): aparece la descripción de la estación
    await page.getByRole('button', { name: 'Estación', exact: true }).click();
    await expect(vp).toHaveAttribute('data-lod', '2');
    await expect(investigation.locator('.st-what')).toBeVisible();
    await expect(page.locator('.zpct')).toHaveText('110%');

    // Y de vuelta: «Fábrica» encuadra y vuelve al nivel de poco texto
    await page.getByRole('button', { name: 'Fábrica', exact: true }).click();
    await expect(vp).toHaveAttribute('data-lod', '1');
    await expect(investigation.locator('.st-what')).toBeHidden();
    await expect.poll(async () => (await smallestTextOnScreen(page)).min).toBeGreaterThanOrEqual(12);
  });

  test('con una ventana más grande el texto alejado sigue siendo legible', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await openMap(page);
    await expect.poll(async () => (await smallestTextOnScreen(page)).min).toBeGreaterThanOrEqual(12);
    await expect(page.locator('main.vp')).toHaveAttribute('data-lod', '1');
  });
});
