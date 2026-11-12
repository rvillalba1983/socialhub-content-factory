import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test as base, expect, type APIRequestContext, type Page } from '@playwright/test';

/** Respuesta de /api/factory, solo con lo que miran las pruebas. */
export interface FactoryState {
  stations: { id: string; name: string }[];
  brands: { id: string; name: string; handle: string; color: string }[];
  promotions: { id: string; brand_id: string; name: string }[];
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
}

/** Toast de confirmación de la app. */
export const toast = (page: Page) => page.locator('.toast');
