import fs from 'node:fs';
import path from 'node:path';
import { expect, makeTempDir, openMap, openPromotion, test, unique } from '../support/fixtures';

const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

test.describe('Sonido de la publicación', () => {
  test('se elige y se guarda en la ficha, y una publicación programada no lo deja cambiar', async ({ page, api }) => {
    const promoName = unique('Promoción sonido');
    const root = path.join(makeTempDir('sonido'), 'raiz');
    const brand = await api.createBrandWithFolders(unique('Marca sonido'), `@${unique('sonido').toLowerCase()}`, root);
    const promo = await api.createPromotion(brand.id, promoName);
    const folders = await api.get<{ items: { role: string; location: string }[] }>(`/api/brands/${brand.id}/folders`);
    const generated = folders.items.find(f => f.role === 'GENERATED')!.location;
    fs.mkdirSync(generated, { recursive: true });
    fs.writeFileSync(path.join(generated, 'pieza.png'), PNG);
    fs.chmodSync(path.join(generated, 'pieza.png'), 0o644);

    const title = unique('Reel sonido');
    const publication = await api.createPublication(promo.id, title, { initial_status: 'IN_PRODUCTION' });
    // Una pieza asociada a mano no trae página ni archivo de origen: la ficha no inventa nada
    await api.post(`/api/publications/${publication.id}/files`, { path: path.join(generated, 'pieza.png') });

    // La API valida: sin tema no se guarda
    const bad = await page.request.put(`/api/publications/${publication.id}/sound`, { data: { mode: 'litoques_track' }, headers: { 'X-SocialHub-Request': '1' } });
    expect(bad.status()).toBe(400);

    await openMap(page);
    await openPromotion(page, promoName);
    await page.getByRole('tab', { name: 'Publicaciones', exact: true }).click();
    await page.getByRole('button', { name: `Editar ${title}`, exact: true }).click();

    const files = page.getByRole('group', { name: 'Archivos' });
    await expect(files.getByText('pieza.png')).toBeVisible();
    await expect(files.getByText(/Diapositiva|Imagen de origen/)).toHaveCount(0);

    const sound = page.getByRole('group', { name: 'Sonido' });
    await expect(sound.locator('strong')).toHaveText('Sin sonido');
    await sound.getByRole('combobox').selectOption('litoques_track');
    await expect(sound.getByRole('button', { name: 'Guardar sonido' })).toBeDisabled();
    await sound.getByLabel('Nombre del tema').fill('Púa');
    await sound.getByRole('button', { name: 'Guardar sonido' }).click();
    await expect(sound.locator('strong')).toHaveText('Tema de Litoques en el vídeo: Púa');
    await expect.poll(async () => (await api.get(`/api/promotions/${promo.id}/publications?limit=50`)).items.find((p: { id: string }) => p.id === publication.id)?.sound).toMatchObject({ mode: 'litoques_track', track: 'Púa' });

    await sound.getByRole('combobox').selectOption('network');
    await sound.getByRole('button', { name: 'Guardar sonido' }).click();
    await expect(sound.locator('strong')).toHaveText('Elegir en la red: Púa');

    // Programada: el selector queda desactivado y la API responde 409
    // El control de calidad no es lo que se prueba aquí: se aceptan sus avisos para poder programar
    const report = await api.get<{ issues: { key: string }[] }>(`/api/publications/${publication.id}/quality`);
    for (const issue of report.issues) await api.post(`/api/publications/${publication.id}/quality-acceptances`, { key: issue.key, reason: 'Prueba de sonido' });
    for (const status of ['DRAFT', 'IN_REVIEW', 'APPROVED']) {
      await api.post(`/api/publications/${publication.id}/status-changes`, { status });
    }
    const scheduled = await page.request.post(`/api/publications/${publication.id}/status-changes`, { data: { status: 'SCHEDULED' }, headers: { 'X-SocialHub-Request': '1' } });
    expect(scheduled.ok(), await scheduled.text()).toBeTruthy();
    {
      await page.getByRole('button', { name: 'Cerrar', exact: true }).first().click();
      await page.getByRole('button', { name: `Editar ${title}`, exact: true }).click();
      const locked = page.getByRole('group', { name: 'Sonido' });
      await expect(locked.getByRole('combobox')).toBeDisabled();
      await expect(locked.getByText(/ya está programada o publicada/)).toBeVisible();
      const refused = await page.request.put(`/api/publications/${publication.id}/sound`, { data: { mode: 'none' }, headers: { 'X-SocialHub-Request': '1' } });
      expect(refused.status()).toBe(409);
    }
  });
});
