import path from 'node:path';
import { brandRow, daysFromToday, expect, makeTempDir, openMap, openPromotion, test, toast, unique } from '../support/fixtures';
import type { Page } from '@playwright/test';

/** Fila de la tabla de publicaciones por el título de la pieza. */
const row = (page: Page, title: string) => page.locator('tbody tr', { hasText: title });

test.describe('Promoción y propuestas', () => {
  test('alta de una promoción desde «Marcas» y recorrido por las pestañas de su ficha', async ({ page, api }) => {
    const brandName = unique('Marca promo');
    const promoName = unique('Promoción nueva');
    const root = path.join(makeTempDir('promo'), 'raiz');
    await api.createBrandWithFolders(brandName, `@${unique('promo').toLowerCase()}`, root);

    await openMap(page);
    await page.getByRole('button', { name: 'Marcas', exact: true }).click();
    await page.getByRole('button', { name: 'Nueva promoción' }).click();
    const form = page.getByRole('form', { name: 'Nueva promoción' });
    await form.locator('select').first().selectOption({ label: brandName });
    await form.getByLabel('Nombre').fill(promoName);
    await form.getByLabel('Empieza').fill(daysFromToday(-2));
    await form.getByLabel('Termina', { exact: true }).fill(daysFromToday(30));
    await form.locator('select').nth(1).selectOption('ACTIVE');
    await form.getByLabel('Cadencia').fill('Posts L/X/V 19:00');
    await form.getByLabel('Descripción').fill('Promoción creada por el recorrido de pruebas');
    await form.getByLabel('Objetivos (uno por línea)').fill('Vender el primer lote\nGanar seguidores');
    await form.getByRole('button', { name: 'Guardar' }).click();
    await expect(toast(page)).toContainText(`Promoción guardada · ${promoName}`);

    // Está en la API y en el mapa, con su tarjeta
    const promo = (await api.factory()).promotions.find(p => p.name === promoName)!;
    expect(promo).toBeTruthy();
    await page.getByRole('button', { name: 'Volver al mapa' }).click();
    await openPromotion(page, promoName);

    // Ficha: cabecera y resumen con lo escrito en el alta
    await expect(page.getByRole('heading', { level: 1, name: promoName })).toBeVisible();
    await expect(page.locator('.cc-head .eyebrow')).toContainText(brandName);
    await expect(page.getByRole('heading', { name: 'Objetivos' })).toBeVisible();
    await expect(page.getByText('Vender el primer lote')).toBeVisible();
    await expect(page.getByText('Ganar seguidores')).toBeVisible();

    // Cada pestaña se abre y enseña lo suyo
    const tab = (name: string) => page.getByRole('tab', { name, exact: true });
    await tab('Calendario').click();
    await expect(tab('Calendario')).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('button', { name: 'Semana siguiente' })).toBeVisible();
    await tab('Publicaciones').click();
    await expect(page.getByRole('button', { name: 'Nueva publicación' })).toBeVisible();
    await tab('Tareas').click();
    await expect(page.getByRole('columnheader', { name: 'Estación' })).toBeVisible();
    await tab('Recursos').click();
    await expect(page.getByRole('form', { name: 'Carpetas de Drive' })).toBeVisible();
    await tab('Automatización').click();
    await expect(page.getByRole('heading', { name: 'Vías de publicación, por orden de preferencia' })).toBeVisible();
    await tab('Métricas').click();
    await expect(tab('Métricas')).toHaveAttribute('aria-selected', 'true');
    await tab('Configuración').click();
    await expect(page.getByRole('columnheader', { name: 'Parámetro' })).toBeVisible();

    // «Editar» abre el formulario con los datos guardados
    await page.getByRole('button', { name: 'Editar', exact: true }).click();
    const edit = page.getByRole('form', { name: 'Editar promoción' });
    await expect(edit.getByLabel('Nombre')).toHaveValue(promoName);
    await expect(edit.getByLabel('Cadencia')).toHaveValue('Posts L/X/V 19:00');
    await expect(edit.getByLabel('Objetivos (uno por línea)')).toHaveValue('Vender el primer lote\nGanar seguidores');
  });

  test('propuestas: aprobar, rechazar con motivo y volver atrás, con su rastro en la memoria editorial', async ({ page, api }) => {
    const brandName = unique('Marca propuestas');
    const promoName = unique('Promoción propuestas');
    const root = path.join(makeTempDir('propuestas'), 'raiz');
    const brand = await api.createBrandWithFolders(brandName, `@${unique('propuestas').toLowerCase()}`, root);
    const promo = await api.createPromotion(brand.id, promoName);
    const approve = unique('Propuesta para aprobar');
    const reject = unique('Propuesta para rechazar');
    const keep = unique('Propuesta que se queda');
    const approveId = (await api.createPublication(promo.id, approve)).id;
    const rejectId = (await api.createPublication(promo.id, reject, { scheduled_time: '20:00' })).id;
    const keepId = (await api.createPublication(promo.id, keep, { scheduled_time: '21:00' })).id;

    await openMap(page);
    await openPromotion(page, promoName);
    await page.getByRole('tab', { name: 'Publicaciones', exact: true }).click();
    await page.getByRole('button', { name: 'Propuestas', exact: true }).click();
    for (const title of [approve, reject, keep]) {
      await expect(row(page, title)).toContainText('Propuesta');
    }

    // --- Aprobar: pasa a «En producción» y deja de ser propuesta
    await page.getByRole('button', { name: `Aprobar ${approve}` }).click();
    await expect(toast(page)).toHaveText(`Propuesta aprobada · ${approve}`);
    await expect.poll(async () => (await api.factory()).publications.find(p => p.id === approveId)?.status).toBe('IN_PRODUCTION');
    await expect(row(page, approve)).toHaveCount(0); // ya no está en el filtro «Propuestas»
    await page.getByRole('button', { name: 'Todas', exact: true }).click();
    await expect(row(page, approve)).toContainText('En producción');
    await expect(page.getByRole('button', { name: `Aprobar ${approve}` })).toHaveCount(0);
    await page.getByRole('button', { name: 'Propuestas', exact: true }).click();

    // --- «Volver» en el motivo no rechaza nada
    await page.getByRole('button', { name: `Rechazar ${keep}` }).click();
    const reasonForm = page.getByRole('group', { name: `Por qué rechazas «${keep}»` });
    await expect(reasonForm).toBeVisible();
    await reasonForm.getByRole('button', { name: 'Volver' }).click();
    await expect(reasonForm).toHaveCount(0);
    expect((await api.factory()).publications.find(p => p.id === keepId)?.status).toBe('PROPOSAL');

    // --- Rechazar con motivo y nota
    await page.getByRole('button', { name: `Rechazar ${reject}` }).click();
    const form = page.getByRole('group', { name: `Por qué rechazas «${reject}»` });
    await form.getByLabel('Motivo').selectOption('REPEATED');
    await form.getByLabel('Nota (opcional)').fill('Ya hicimos algo igual la semana pasada');
    await form.getByRole('button', { name: 'Rechazar propuesta' }).click();
    await expect(toast(page)).toHaveText('Propuesta rechazada');
    await expect.poll(async () => (await api.factory()).publications.find(p => p.id === rejectId)?.status).toBe('CANCELLED');
    await expect(row(page, reject)).toHaveCount(0);
    await expect(row(page, keep)).toContainText('Propuesta');

    // --- El rechazo queda en la memoria editorial de la marca, con su motivo y su nota
    await page.getByRole('button', { name: 'Volver al mapa' }).click();
    await page.getByRole('button', { name: 'Marcas', exact: true }).click();
    await brandRow(page, brandName).click();
    const memory = page.getByLabel('Memoria editorial');
    await expect(memory.getByRole('listitem').filter({ hasText: reject })).toContainText('Publicación cancelada');
    await expect(memory.getByRole('listitem').filter({ hasText: reject })).toContainText('Repetido');
    await expect(memory.getByRole('listitem').filter({ hasText: reject })).toContainText('Ya hicimos algo igual la semana pasada');
    // La propuesta que se quedó y la aprobada no aparecen como rechazos
    await expect(memory.getByRole('listitem').filter({ hasText: keep })).toHaveCount(0);
    await expect(memory.getByRole('listitem').filter({ hasText: approve })).toHaveCount(0);
  });
});
