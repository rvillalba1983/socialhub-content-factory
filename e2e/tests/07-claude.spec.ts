import fs from 'node:fs';
import path from 'node:path';
import { expect, makeTempDir, openMap, test, toast, unique } from '../support/fixtures';
import type { Page } from '@playwright/test';

interface Request { contract: string; version: number; taskId: string; type: string; goal: string; urgent?: boolean; model?: string; brand?: string; promotion?: string }

/** Los encargos que SocialHub ha dejado en `para-claude`, ya leídos. */
function requestsIn(dir: string): Request[] {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter(f => f.endsWith('.json')).map(f => JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')) as Request);
}

/** Lo que haría la tarea programada de Claude: dejar un resultado según el contrato en `de-claude`. */
function answer(ordersDir: string, taskId: string, body: Record<string, unknown>) {
  const dir = path.join(ordersDir, 'de-claude');
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${taskId}.json`);
  fs.writeFileSync(file, JSON.stringify({
    contract: 'socialhub.task-result', version: 1, taskId, outputs: {}, agent: { runtime: 'claude-scheduled-task', model: 'sonnet' },
    startedAt: new Date(Date.now() - 20_000).toISOString(), finishedAt: new Date().toISOString(), ...body,
  }));
  fs.chmodSync(file, 0o666);
}

async function openClaude(page: Page) {
  await openMap(page);
  await page.getByRole('button', { name: 'Claude', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Encargos a Claude' })).toBeVisible();
}

test.describe('Claude: ajustes, encargos y ritmo', () => {
  test('margen mínimo de publicación: 30 minutos por defecto, ajustable y nunca por debajo de 5', async ({ page, api }) => {
    const brand = await api.createBrandWithFolders(unique('Marca Claude margen'), `@${unique('margen').toLowerCase()}`, path.join(makeTempDir('claude-margen'), 'raiz'));
    await api.createPromotion(brand.id, unique('Promoción Claude margen'));
    await openClaude(page);

    const card = page.getByRole('form', { name: 'Margen mínimo de publicación' });
    const input = card.getByLabel(/Minutos entre ahora y la hora de publicación/);
    await expect(input).toHaveValue('30');
    await input.fill('90');
    await card.getByRole('button', { name: 'Guardar margen' }).click();
    await expect(toast(page)).toHaveText('Margen mínimo de publicación guardado');
    expect((await api.get<{ min_margin_minutes: number }>('/api/settings/publishing')).min_margin_minutes).toBe(90);

    // Fuera de límites no se guarda (el campo ya lo impide en el navegador; la API también lo rechaza)
    await expect(input).toHaveAttribute('min', '5');
    await input.fill('30');
    await card.getByRole('button', { name: 'Guardar margen' }).click();
    await expect(toast(page)).toHaveText('Margen mínimo de publicación guardado');
  });

  test('ajustes del límite y de los modelos, y los avisos de cada tipo de encargo', async ({ page, api }) => {
    const brand = await api.createBrandWithFolders(unique('Marca Claude ajustes'), `@${unique('ajustes').toLowerCase()}`, path.join(makeTempDir('claude-ajustes'), 'raiz'));
    await api.createPromotion(brand.id, unique('Promoción Claude ajustes'));
    await openClaude(page);

    const settings = page.getByRole('form', { name: 'Ajustes de Claude' });
    await expect(settings.getByText(/Encargos al día \(hoy: \d+\)/)).toBeVisible();
    // Los encargos del PC (Chrome) y los automáticos no cuentan en el límite diario
    await expect(settings.getByText('con Chrome en tu PC; no cuenta en el límite').first()).toBeVisible();
    await expect(settings.getByText('lo encarga SocialHub; no cuenta en el límite').first()).toBeVisible();
    await expect(settings).toContainText('Si un encargo falla dos veces, el tercer intento sube un nivel (Sonnet → Opus).');

    // Cambia el límite y el modelo de «Investigación»
    const before = await api.get<{ daily_limit: number; models: { type: string; label: string; model: string }[] }>('/api/ai/settings');
    const research = before.models.find(m => m.type === 'RESEARCH')!;
    const newLimit = before.daily_limit === 7 ? 8 : 7;
    await settings.getByLabel(/^Encargos al día/).fill(String(newLimit));
    await settings.getByLabel(`Modelo para «${research.label}»`, { exact: false }).selectOption('OPUS');
    await settings.getByRole('button', { name: 'Guardar ajustes' }).click();
    await expect(toast(page)).toHaveText('Ajustes de Claude guardados');

    // Persistido: lo dice la API y se ve al volver a abrir la pantalla
    const after = await api.get<typeof before>('/api/ai/settings');
    expect(after.daily_limit).toBe(newLimit);
    expect(after.models.find(m => m.type === 'RESEARCH')!.model).toBe('OPUS');
    await openClaude(page);
    await expect(page.getByRole('form', { name: 'Ajustes de Claude' }).getByLabel(/^Encargos al día/)).toHaveValue(String(newLimit));

    // Un límite fuera de rango no se puede guardar (el campo lo impide)
    const limit = page.getByRole('form', { name: 'Ajustes de Claude' }).getByLabel(/^Encargos al día/);
    await limit.fill('101');
    await expect(limit).toHaveJSProperty('validity.rangeOverflow', true);

    // «Nuevo encargo»: el tipo de encargo cambia la ayuda de cuándo sale (el ritmo de las pasadas)
    const order = page.getByRole('form', { name: 'Nuevo encargo a Claude' });
    await order.getByLabel('Tipo').selectOption('WEEKLY_PROPOSALS');
    await expect(order).toContainText('Sale solo los lunes a las 18:00.');
    await order.getByLabel('Tipo').selectOption('WEEKLY_SPEC');
    await expect(order).toContainText('Sale solo con 3 pendientes, o si la más próxima sale en menos de 3 días.');
    // Los encargos del PC y los automáticos no se piden a mano
    await expect(order.getByLabel('Tipo').locator('option')).not.toContainText(['Programar en Publer']);
  });

  test('un encargo a mano sale urgente hacia Drive, Claude contesta y el registro lo refleja', async ({ page, api, allowConsole }) => {
    test.setTimeout(180_000);
    // Al contestar «failed» la tarea falla, y no hay más que mirar en la consola: no se declara nada
    const root = path.join(makeTempDir('claude-encargos'), 'raiz');
    const brand = await api.createBrandWithFolders(unique('Marca Claude encargos'), `@${unique('encargos').toLowerCase()}`, root);
    const promoName = unique('Promoción Claude encargos');
    await api.createPromotion(brand.id, promoName);
    // El límite diario es de toda la app y los demás recorridos ya han gastado envíos: se sube para no depender del orden
    const current = await api.get<{ models: { type: string; model: string }[] }>('/api/ai/settings');
    await api.put('/api/ai/settings', { daily_limit: 100, models: current.models.map(({ type, model }) => ({ type, model })) });
    const ordersDir = path.join(root, 'encargos');
    const toClaude = path.join(ordersDir, 'para-claude');

    await openClaude(page);
    const order = page.getByRole('form', { name: 'Nuevo encargo a Claude' });
    await order.locator('select').first().selectOption({ label: promoName });
    await order.getByLabel('Tipo').selectOption('RESEARCH');
    const title = unique('Investiga el barrio');
    const goal = `Busca tres librerías de Granada que vendan poesía (${title})`;
    await order.getByLabel('Título (opcional)').fill(title);
    await order.getByLabel('Qué tiene que hacer').fill(goal);
    await order.getByLabel('Datos de entrada (uno por línea)').fill('Granada\nPoesía');
    await order.getByRole('button', { name: 'Encargar a Claude' }).click();
    await expect(toast(page)).toHaveText('Encargo en cola');

    // SocialHub lo deja en la carpeta «para-claude» de la marca, con el contrato y marcado como urgente (lo pedí yo)
    await expect.poll(() => requestsIn(toClaude).filter(r => r.goal.includes(title)).length, { timeout: 30_000 }).toBe(1);
    const sent = requestsIn(toClaude).find(r => r.goal.includes(title))!;
    expect(sent.contract).toBe('socialhub.task-request');
    expect(sent.version).toBe(1);
    expect(sent.urgent).toBe(true);
    expect(sent.type).toMatch(/research/i);

    // El registro de envíos lo muestra «Esperando a Claude»
    await openClaude(page);
    const registry = page.getByRole('table').filter({ has: page.getByRole('columnheader', { name: 'Duración' }) });
    const entry = registry.locator('tbody tr').filter({ hasText: 'Esperando a Claude' }).first();
    await expect(entry).toBeVisible();
    await expect(entry).toContainText('Investigación');
    await expect(entry).toContainText('desconocido'); // tokens y coste: Claude Pro no los da

    // --- Claude contesta bien: el encargo se completa
    answer(ordersDir, sent.taskId, { status: 'completed', outputs: { summary: 'Tres librerías: Un, Dos y Tres.', report: '# Librerías de poesía\n\n1. Un\n2. Dos\n3. Tres\n' } });
    await expect.poll(async () => {
      const list = await api.get<{ items: { task_id: string; status: string }[] }>('/api/ai/requests?offset=0&limit=50');
      return list.items.find(r => r.task_id === sent.taskId)?.status;
    }, { timeout: 40_000 }).toBe('COMPLETED');
    // El informe se guarda en la carpeta «informes» de la marca
    const reports = fs.readdirSync(path.join(root, 'informes'));
    expect(reports.length).toBeGreaterThan(0);
    expect(fs.readFileSync(path.join(root, 'informes', reports[0]), 'utf8')).toContain('Librerías de poesía');
    await page.getByRole('button', { name: 'Actualizar' }).click();
    await expect(registry.locator('tbody tr').filter({ hasText: 'Completado' }).first()).toBeVisible();

    // --- Un segundo encargo, al que Claude responde con un error: queda «Error» con su motivo
    await order.locator('select').first().selectOption({ label: promoName }); // al reabrir la pantalla vuelve a la primera promoción
    await order.getByLabel('Qué tiene que hacer').fill('Un encargo que saldrá mal (prueba)');
    await order.getByRole('button', { name: 'Encargar a Claude' }).click();
    await expect.poll(() => requestsIn(toClaude).filter(r => r.goal.includes('saldrá mal')).length, { timeout: 30_000 }).toBe(1);
    const failing = requestsIn(toClaude).find(r => r.goal.includes('saldrá mal'))!;
    answer(ordersDir, failing.taskId, { status: 'failed', error: 'No he podido abrir la web (prueba)' });
    await expect.poll(async () => {
      const list = await api.get<{ items: { task_id: string; status: string; error?: string }[] }>('/api/ai/requests?offset=0&limit=50');
      return list.items.find(r => r.task_id === failing.taskId)?.status;
    }, { timeout: 40_000 }).toBe('FAILED');
    await openClaude(page);
    const failed = page.getByRole('table').filter({ has: page.getByRole('columnheader', { name: 'Duración' }) })
      .locator('tbody tr').filter({ hasText: 'No he podido abrir la web (prueba)' });
    await expect(failed).toContainText('Error');
    await expect(failed.getByRole('button', { name: 'Ver tarea' })).toBeVisible();

    // La tarjeta «Claude en la nube» de Agentes y herramientas ya no espera el primer resultado
    // La tarjeta se recalcula en una ronda propia de la app (no al instante): se espera a que la API lo refleje
    await expect.poll(async () => (await api.get<{ armory_items: { id: string; note: string }[] }>('/api/factory'))
      .armory_items.find(i => i.id === 'claude-cloud')?.note, { timeout: 90_000, intervals: [2000] }).not.toContain('Esperando el primer resultado');
    await openMap(page); // recarga la web: la tarjeta sale de datos nuevos
    await page.getByRole('region', { name: 'Agentes y herramientas' }).getByRole('button', { name: 'Claude en la nube' }).click();
    await expect(page.locator('aside.panel')).not.toContainText('Esperando el primer resultado');
  });
});
