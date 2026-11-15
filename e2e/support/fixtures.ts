import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test as base, expect, type APIRequestContext, type Page } from '@playwright/test';

/** Respuesta de /api/factory, solo con lo que miran las pruebas. */
export interface FactoryState {
  stations: { id: string; name: string }[];
  brands: { id: string; name: string; handle: string; color: string }[];
  promotions: { id: string; brand_id: string; name: string }[];
  publications: { id: string; promotion_id: string; title: string; status: string }[];
  alerts: { kind: string; title: string; detail: string }[];
  recommendations: { id: string; kind: string; title: string; target?: { screen: string; brand_id?: string } }[];
}

/** Cliente de la API pública de SocialHub (la misma que usa la web). Sirve para preparar datos y comprobar lo guardado. */
export class Api {
  constructor(private readonly http: APIRequestContext) {}

  private async send<T>(method: 'get' | 'post' | 'put' | 'delete', url: string, data?: unknown): Promise<T> {
    const response = await this.http[method](url, data === undefined ? {} : { data });
    expect(response.ok(), `${method.toUpperCase()} ${url} → ${response.status()} ${await response.text()}`).toBeTruthy();
    return (response.status() === 204 ? undefined : await response.json()) as T;
  }

  get = <T = any>(url: string) => this.send<T>('get', url);
  put = <T = any>(url: string, data: unknown) => this.send<T>('put', url, data);
  post = <T = any>(url: string, data: unknown) => this.send<T>('post', url, data);
  factory = () => this.get<FactoryState>('/api/factory');

  createBrand(name: string, handle: string, color = '#3366CC') {
    return this.send<{ id: string; name: string }>('post', '/api/brands', { name, handle, color });
  }

  createPromotion(brandId: string, name: string, extra: Record<string, unknown> = {}) {
    return this.send<{ id: string; name: string }>('post', '/api/promotions', {
      brand_id: brandId, name, starts_on: daysFromToday(-3), ends_on: daysFromToday(40), status: 'ACTIVE',
      cadence: 'Posts L/X/V 19:00', goals: ['Objetivo de prueba'], ...extra,
    });
  }

  /** Alta de una marca con sus 7 carpetas creadas en `root` (la carpeta raíz de la marca). */
  async createBrandWithFolders(name: string, handle: string, root: string) {
    const brand = await this.createBrand(name, handle);
    await this.send('post', `/api/brands/${brand.id}/folders/creation`, { base: root });
    return brand;
  }

  createPublication(promotionId: string, title: string, extra: Record<string, unknown> = {}) {
    return this.send<{ id: string; title: string; status: string }>('post', `/api/promotions/${promotionId}/publications`, {
      platform: 'INSTAGRAM', type: 'Carrusel', title, scheduled_on: daysFromToday(10), scheduled_time: '19:00', route: 'Publer',
      initial_status: 'PROPOSAL', ...extra,
    });
  }

  /** Encargo a Claude: sin carpeta de «Encargos» en la marca se queda retenido y sale en «Necesita atención». */
  createAiOrder(promotionId: string, title: string) {
    return this.send<{ task_id: string }>('post', `/api/promotions/${promotionId}/ai-orders`, { type: 'RESEARCH', title, goal: 'Investiga algo de prueba', inputs: [] });
  }
}

/** Día de hoy (hora de España) desplazado `days` días, como AAAA-MM-DD. */
export function daysFromToday(days: number): string {
  const madrid = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit' });
  return madrid.format(new Date(Date.now() + days * 86_400_000));
}

let counter = 0;
/** Nombre único por ejecución (la app puede reutilizarse en local): «Etiqueta ab12». */
export function unique(label: string): string {
  return `${label} ${(Date.now() % 1e6).toString(36)}${(counter++).toString(36)}`;
}

/** Carpeta temporal para las carpetas «de Drive» de las pruebas; el jar puede ir como otro usuario, así que se abre a todos. */
export function makeTempDir(label: string): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `socialhub-e2e-${label}-`));
  fs.chmodSync(dir, 0o777);
  return dir;
}

type Fixtures = {
  api: Api;
  /** Patrones de errores de consola esperados en esta prueba (el resto hace fallar la prueba). */
  allowedConsole: RegExp[];
  /** Declara un error de consola esperado. */
  allowConsole: (pattern: RegExp) => void;
};

export const test = base.extend<Fixtures>({
  api: async ({ playwright, baseURL }, use) => {
    const http = await playwright.request.newContext({ baseURL, extraHTTPHeaders: { 'X-SocialHub-Request': '1' } });
    await use(new Api(http));
    await http.dispose();
  },

  allowedConsole: async ({}, use) => { await use([]); },
  allowConsole: async ({ allowedConsole }, use) => { await use(pattern => { allowedConsole.push(pattern); }); },

  // Cada recorrido vigila la consola del navegador: un error o una excepción sin declarar hace fallar la prueba
  page: async ({ page, allowedConsole }, use) => {
    const problems: string[] = [];
    page.on('console', message => {
      if (message.type() === 'error') problems.push(`console.error: ${message.text()} (${message.location().url})`);
    });
    page.on('pageerror', error => problems.push(`excepción sin capturar: ${error.message}`));
    await use(page);
    const unexpected = problems.filter(p => !allowedConsole.some(re => re.test(p)));
    expect(unexpected, 'Errores en la consola del navegador').toEqual([]);
  },
});

export { expect };

/** Abre SocialHub y espera a que el mapa esté dibujado (las 8 estaciones del anillo). */
export async function openMap(page: Page) {
  await page.goto('/');
  await expect(page.locator('.node')).toHaveCount(8);
  await expect(page.locator('.zpct')).not.toHaveText('');
  // La franja «Qué hacer ahora» cambia el alto del mapa y la app vuelve a encuadrar al aparecer: se espera a que salga
  const state = await page.request.get('/api/factory').then(r => r.json());
  if (state.recommendations.length > 0) await expect(page.getByRole('region', { name: 'Qué hacer ahora' })).toBeVisible();
  // Espera a que el encuadre inicial termine (y no lo repita una actualización de datos, como al aparecer «Qué hacer ahora»)
  let last = '';
  await expect.poll(async () => {
    const now = await page.locator('.world').evaluate(el => getComputedStyle(el).transform);
    const same = now === last; last = now; return same;
  }, { intervals: [400, 400, 400], timeout: 15_000 }).toBe(true);
}

/** Toast de confirmación de la app. */
export const toast = (page: Page) => page.locator('.toast');

/** Fila de una marca en la lista de «Marcas» (por su nombre, no por el texto de las promociones que lo citan). */
export const brandRow = (page: Page, name: string) =>
  page.locator('button.qrow').filter({ has: page.locator('.qname', { hasText: name }) });

/**
 * Abre el centro de control de una promoción desde su tarjeta del mapa, con el teclado (Enter). Con 4 o más promociones
 * las tarjetas se salen del anillo y las estaciones o la leyenda pueden taparlas (fallo conocido de la app, anotado en la
 * PR), así que un clic podría caer en otra cosa.
 */
export async function openPromotion(page: Page, name: string) {
  const card = page.getByRole('article', { name: `Abrir el centro de control de ${name}` });
  await card.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible();
}
