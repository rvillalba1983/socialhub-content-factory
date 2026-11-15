import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, openMap, test, toast, unique, type Api } from '../support/fixtures';
import type { Page } from '@playwright/test';

/** El ejemplo oficial del formato, el mismo que enlaza el manual. */
const EXAMPLE = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'docs', 'ejemplos', 'identidad-marca.txt');

interface Identity {
  tone: string; audience: string; forbidden_topics: string[]; preferred_expressions: string[]; forbidden_expressions: string[];
  colors: string[]; font: string; logo: string; visual_style: string; photo_style: string; call_to_action: string; fixed_hashtags: string;
}

/** Abre la ficha de una marca desde «Marcas». */
async function openBrand(page: Page, name: string) {
  await openMap(page);
  await page.getByRole('button', { name: 'Marcas', exact: true }).click();
  await page.locator('button.qrow', { hasText: name }).click();
  await expect(page.getByRole('form', { name: 'Identidad de la marca' })).toBeVisible();
}

const identityOf = (api: Api, brandId: string) => api.get<Identity>(`/api/brands/${brandId}/identity`);

test.describe('Identidad de la marca: importar desde .txt', () => {
  test('«Copiar plantilla» deja en el portapapeles la instrucción y el formato', async ({ page, api }) => {
    const name = unique('Marca plantilla');
    const brand = await api.createBrand(name, `@${unique('plantilla').toLowerCase()}`);
    // Con un valor ya guardado, la plantilla lo lleva dentro
    await api.put(`/api/brands/${brand.id}/identity`, { ...(await identityOf(api, brand.id)), tone: 'Tono ya guardado de la plantilla' });

    await openBrand(page, name);
    const form = page.getByRole('form', { name: 'Identidad de la marca' });
    await form.getByRole('button', { name: 'Copiar plantilla' }).click();
    await expect(form.getByRole('status')).toHaveText('Plantilla copiada');

    const copied = await page.evaluate(() => navigator.clipboard.readText());
    expect(copied).toContain(name);
    expect(copied).toContain('## Tono');
    expect(copied).toContain('## Hashtags fijos');
    expect(copied).toContain('Tono ya guardado de la plantilla');
    // Pegada tal cual, la primera línea (la instrucción para la IA) estorba y la importación lo explica sin guardar nada
    await form.getByRole('button', { name: 'Importar desde .txt' }).click();
    await form.getByLabel('Texto de la identidad').fill(copied);
    await form.getByRole('button', { name: 'Ver cambios' }).click();
    await expect(form.getByRole('alert').filter({ hasText: 'No se puede importar' })).toContainText('Hay texto antes del primer «## Campo»');
    // Sin la instrucción (lo que devolvería la IA), la plantilla se importa sin errores y no cambia nada
    const formatOnly = copied.slice(copied.indexOf('###'));
    await form.getByLabel('Texto de la identidad').fill(formatOnly);
    await form.getByRole('button', { name: 'Ver cambios' }).click();
    await expect(form.getByRole('alert')).toHaveCount(0);
    await expect(form.getByRole('region', { name: 'Cambios de la identidad' })).toContainText('No cambia ningún campo.');
  });

  test('importa el .txt de ejemplo: vista previa antes de guardar y guardado completo', async ({ page, api }) => {
    const name = unique('Marca identidad');
    const brand = await api.createBrand(name, `@${unique('identidad').toLowerCase()}`);
    expect((await identityOf(api, brand.id)).tone).toBe('');

    await openBrand(page, name);
    // La marca recién creada avisa de que falta la identidad
    const notices = page.getByRole('region', { name: 'Avisos de la marca' });
    await expect(notices).toContainText('Falta rellenar la identidad de la marca: el tono y el público.');

    const form = page.getByRole('form', { name: 'Identidad de la marca' });
    await form.getByRole('button', { name: 'Importar desde .txt' }).click();
    await form.getByLabel('Archivo .txt de la identidad').setInputFiles(EXAMPLE);
    await expect(form.getByTestId('identity-file-name')).toHaveText('identidad-marca.txt');

    // Vista previa: los 12 campos cambian de vacío a lo que trae el archivo
    const diff = form.getByRole('region', { name: 'Cambios de la identidad' });
    await expect(diff.locator('tbody tr')).toHaveCount(12);
    await expect(diff.getByRole('cell', { name: 'Tono antes' })).toHaveText('(vacío)');
    await expect(diff.getByRole('cell', { name: 'Tono después' })).toContainText('Cercano, con humor y sin solemnidad');
    await expect(diff.getByRole('cell', { name: 'Expresiones prohibidas después' })).toContainText('compra ya');
    await expect(diff.getByRole('cell', { name: 'Hashtags fijos después' })).toHaveText('#eldelito #litoques');

    // Todavía no se ha guardado nada
    expect((await identityOf(api, brand.id)).tone).toBe('');

    await diff.getByRole('button', { name: 'Guardar estos cambios' }).click();
    await expect(toast(page)).toHaveText('Identidad importada y guardada');
    // El bloque de importación se cierra y el formulario enseña lo importado
    await expect(form.getByLabel('Texto de la identidad')).toHaveCount(0);
    await expect(form.getByLabel('Tono de comunicación')).toHaveValue(/Cercano, con humor y sin solemnidad/);
    await expect(form.getByLabel('Temas que no se tocan (una por línea)')).toHaveValue('Política de partidos\nReligión');
    await expect(form.getByLabel('Llamada a la acción (al final de los textos, salvo historias)')).toHaveValue('Cuéntanos qué te parece en los comentarios.');

    // Guardado de verdad (API)
    const saved = await identityOf(api, brand.id);
    expect(saved.tone).toBe('Cercano, con humor y sin solemnidad. Habla de tú, con frases cortas.');
    expect(saved.forbidden_topics).toEqual(['Política de partidos', 'Religión']);
    expect(saved.preferred_expressions).toEqual(['de aquí a la luna', '¡a por ello!']);
    expect(saved.forbidden_expressions).toEqual(['lo mejor del mercado', 'compra ya']);
    expect(saved.colors).toEqual(['#D23A2E', '#1B1B1B']);
    expect(saved.font).toBe('Fraunces para títulos, Inter para el texto.');
    expect(saved.fixed_hashtags).toBe('#eldelito #litoques');

    // El aviso de identidad desaparece al guardar
    await expect(notices).not.toContainText('Falta rellenar la identidad');

    // Importar otra vez el mismo archivo: no cambia nada y no ofrece guardar
    await form.getByRole('button', { name: 'Importar desde .txt' }).click();
    await form.getByLabel('Archivo .txt de la identidad').setInputFiles(EXAMPLE);
    await expect(form.getByRole('region', { name: 'Cambios de la identidad' })).toContainText('No cambia ningún campo.');
    await expect(form.getByRole('button', { name: 'Guardar estos cambios' })).toHaveCount(0);
  });

  test('un texto con errores no se importa; los campos que no vienen se quedan como estaban', async ({ page, api }) => {
    const name = unique('Marca parcial');
    const brand = await api.createBrand(name, `@${unique('parcial').toLowerCase()}`);
    await api.put(`/api/brands/${brand.id}/identity`, {
      ...(await identityOf(api, brand.id)), tone: 'Tono de partida', audience: 'Público de partida', font: 'Inter', fixed_hashtags: '#partida',
    });

    await openBrand(page, name);
    const form = page.getByRole('form', { name: 'Identidad de la marca' });
    await form.getByRole('button', { name: 'Importar desde .txt' }).click();
    const box = form.getByLabel('Texto de la identidad');

    // Campo desconocido y una lista mal escrita: se explica y no se ofrece guardar
    await box.fill('## Campo inventado\nlo que sea\n\n## Colores\n#FF0000 sin guion');
    await form.getByRole('button', { name: 'Ver cambios' }).click();
    const alert = form.getByRole('alert').filter({ hasText: 'No se puede importar' });
    await expect(alert).toContainText('Campo desconocido: «Campo inventado»');
    await expect(alert).toContainText('«Colores» es una lista');
    await expect(form.getByRole('button', { name: 'Guardar estos cambios' })).toHaveCount(0);
    expect((await identityOf(api, brand.id)).tone).toBe('Tono de partida');

    // Un texto válido solo con «Tono»: cambia el tono y nada más
    await box.fill('## Tono\nSerio y directo.');
    await form.getByRole('button', { name: 'Ver cambios' }).click();
    const diff = form.getByRole('region', { name: 'Cambios de la identidad' });
    await expect(diff.locator('tbody tr')).toHaveCount(1);
    await expect(diff.getByRole('cell', { name: 'Tono antes' })).toHaveText('Tono de partida');
    await expect(diff.getByRole('cell', { name: 'Tono después' })).toHaveText('Serio y directo.');
    await diff.getByRole('button', { name: 'Guardar estos cambios' }).click();
    await expect(toast(page)).toHaveText('Identidad importada y guardada');

    const saved = await identityOf(api, brand.id);
    expect(saved.tone).toBe('Serio y directo.');
    expect(saved.audience).toBe('Público de partida');
    expect(saved.font).toBe('Inter');
    expect(saved.fixed_hashtags).toBe('#partida');
  });
});
