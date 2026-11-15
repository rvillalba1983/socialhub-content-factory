import fs from 'node:fs';
import path from 'node:path';
import { expect, makeTempDir, openMap, test, toast, unique } from '../support/fixtures';

/** Las 7 carpetas de una marca, relativas a la raíz (Material es la propia raíz). */
const SUBFOLDERS = ['borradores', 'generados', 'aprobados', 'publicados', 'informes', 'encargos',
  'encargos/para-claude', 'encargos/para-claude-pc', 'encargos/de-claude', 'encargos/publico'];

test.describe('Alta de una marca con sus carpetas de Drive', () => {
  test('desde la raíz se crean las carpetas y los 7 papeles quedan accesibles', async ({ page, api }) => {
    const name = unique('Marca con carpetas');
    const handle = `@${unique('carpetas').toLowerCase()}`;
    const root = path.join(makeTempDir('alta'), 'Mi unidad', 'socialhub', 'nueva-marca');
    // La raíz todavía no existe: SocialHub la crea con todo lo de dentro

    await openMap(page);
    await page.getByRole('button', { name: 'Marcas', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Marcas y promociones' })).toBeVisible();
    await page.getByRole('button', { name: 'Nueva marca' }).click();

    const form = page.getByRole('form', { name: 'Nueva marca' });
    await form.getByLabel('Nombre').fill(name);
    await form.getByLabel('Cuenta').fill(handle);
    await form.getByLabel('Color de la marca').first().fill('#1f7a4d');
    await form.getByLabel('Carpeta raíz de Drive (opcional)').fill(root);
    await form.getByRole('button', { name: 'Guardar' }).click();

    // Aviso de confirmación con el paso siguiente
    await expect(toast(page)).toContainText(`Marca guardada · ${name} · carpetas creadas`);

    // En disco: la estructura completa, con la raíz como carpeta de Material
    for (const sub of SUBFOLDERS) {
      expect(fs.statSync(path.join(root, sub)).isDirectory(), `falta la carpeta ${sub}`).toBe(true);
    }

    // En la API: los 7 papeles apuntan a esas rutas y están disponibles
    const brand = (await api.factory()).brands.find(b => b.name === name)!;
    expect(brand.handle).toBe(handle);
    expect(brand.color.toLowerCase()).toBe('#1f7a4d');
    const folders = await api.get<{ items: { role: string; location: string; available: boolean }[] }>(`/api/brands/${brand.id}/folders`);
    expect(folders.items.map(f => f.role).sort()).toEqual(['APPROVED', 'DRAFTS', 'GENERATED', 'MATERIAL', 'ORDERS', 'PUBLISHED', 'REPORTS']);
    expect(folders.items.every(f => f.available)).toBe(true);
    expect(folders.items.find(f => f.role === 'MATERIAL')!.location).toBe(root);
    expect(folders.items.find(f => f.role === 'ORDERS')!.location).toBe(path.join(root, 'encargos'));

    // En la lista de marcas sale la nueva; al abrirla, la tarjeta «Carpetas de Drive» tiene la raíz y 7 «Accesible»
    await page.locator('button.qrow', { hasText: name }).click();
    const card = page.getByRole('form', { name: 'Carpetas de Drive' });
    await expect(card.getByLabel('Carpeta raíz')).toHaveValue(root);
    await expect(card.getByText('Accesible', { exact: true })).toHaveCount(7);
    await expect(card.getByText('No disponible')).toHaveCount(0);
    // Con todas las carpetas hechas ya no se avisa de carpetas sin configurar
    const notices = page.getByRole('region', { name: 'Avisos de la marca' });
    await expect(notices).toBeVisible(); // faltan identidad y cuentas de Publer
    await expect(notices).not.toContainText('Carpetas sin configurar');
    await expect(notices).toContainText('Falta rellenar la identidad de la marca');
  });

  test('si la carpeta raíz no se puede crear, la marca se da de alta y el aviso lo explica', async ({ page, api, allowConsole }) => {
    // La API contesta 409 (raíz no accesible) y el navegador lo anota en la consola: es lo esperado
    allowConsole(/status of 409.*\/folders\/creation/);
    const name = unique('Marca sin unidad');
    // Un archivo en lugar de una carpeta: la raíz «cuelga» de algo que no es un directorio, así que no se puede crear
    const blocker = path.join(makeTempDir('bloqueo'), 'no-es-carpeta');
    fs.writeFileSync(blocker, 'x');
    fs.chmodSync(blocker, 0o666);

    await openMap(page);
    await page.getByRole('button', { name: 'Marcas', exact: true }).click();
    await page.getByRole('button', { name: 'Nueva marca' }).click();
    const form = page.getByRole('form', { name: 'Nueva marca' });
    await form.getByLabel('Nombre').fill(name);
    await form.getByLabel('Cuenta').fill(`@${unique('sinunidad').toLowerCase()}`);
    await form.getByLabel('Carpeta raíz de Drive (opcional)').fill(path.join(blocker, 'raiz'));
    await form.getByRole('button', { name: 'Guardar' }).click();

    await expect(toast(page)).toContainText(`Marca guardada · ${name} · no se pudieron crear las carpetas`);
    // La marca existe, sin carpetas, y la ficha avisa de que faltan
    expect((await api.factory()).brands.some(b => b.name === name)).toBe(true);
    await page.locator('button.qrow', { hasText: name }).click();
    await expect(page.getByRole('region', { name: 'Avisos de la marca' })).toContainText('Carpetas sin configurar');
  });
});
