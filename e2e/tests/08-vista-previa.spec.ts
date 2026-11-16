import fs from 'node:fs';
import path from 'node:path';
import { expect, makeTempDir, openMap, openPromotion, test, unique } from '../support/fixtures';

/** PNG de 1x1 píxel. */
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
const VIDEO = Buffer.from('0123456789abcdefghijklmnopqrstuvwxyz'.repeat(8));

test.describe('Vista previa de una publicación', () => {
  test('«Cómo queda», archivos abribles uno a uno y enlace a la red, solo con los archivos de esa publicación', async ({ page, api }) => {
    const brandName = unique('Marca vista');
    const promoName = unique('Promoción vista');
    const root = path.join(makeTempDir('vista'), 'raiz');
    const brand = await api.createBrandWithFolders(brandName, `@${unique('vista').toLowerCase()}`, root);
    const promo = await api.createPromotion(brand.id, promoName);
    const folders = await api.get<{ items: { role: string; location: string }[] }>(`/api/brands/${brand.id}/folders`);
    const generated = folders.items.find(f => f.role === 'GENERATED')!.location;
    fs.mkdirSync(generated, { recursive: true });
    fs.writeFileSync(path.join(generated, 'pieza1.png'), PNG);
    fs.writeFileSync(path.join(generated, 'pieza2.png'), PNG);
    fs.writeFileSync(path.join(generated, 'clip.mp4'), VIDEO);
    for (const f of fs.readdirSync(generated)) fs.chmodSync(path.join(generated, f), 0o644);

    const title = unique('Carrusel de prueba');
    const withFiles = await api.createPublication(promo.id, title, { initial_status: 'IN_PRODUCTION', caption: 'Un verso de prueba', hashtags: '#prueba' });
    const empty = await api.createPublication(promo.id, unique('Sin piezas'), { initial_status: 'IN_PRODUCTION', scheduled_time: '20:00' });
    for (const f of ['pieza1.png', 'pieza2.png', 'clip.mp4']) {
      await api.post(`/api/publications/${withFiles.id}/files`, { path: path.join(generated, f) });
    }

    // El endpoint: solo lo asociado, con su tipo y con Range
    const listed = (await api.get<{ items: { id: string; kind: string }[] }>(`/api/publications/${withFiles.id}/files`)).items;
    const image = listed.find(f => f.kind === 'IMAGE')!;
    const video = listed.find(f => f.kind === 'VIDEO')!;
    const got = await page.request.get(`/api/publications/${withFiles.id}/files/${image.id}/content`);
    expect(got.status()).toBe(200);
    expect(got.headers()['content-type']).toBe('image/png');
    const part = await page.request.get(`/api/publications/${withFiles.id}/files/${video.id}/content`, { headers: { Range: 'bytes=0-9' } });
    expect(part.status()).toBe(206);
    expect((await part.body()).length).toBe(10);
    expect((await page.request.get(`/api/publications/${empty.id}/files/${image.id}/content`)).status()).toBe(404);
    expect((await page.request.get(`/api/publications/${withFiles.id}/files/0123456789abcdef/content`)).status()).toBe(404);

    await openMap(page);
    await openPromotion(page, promoName);
    await page.getByRole('tab', { name: 'Publicaciones', exact: true }).click();
    await page.getByRole('button', { name: `Editar ${title}`, exact: true }).click();

    // --- Cómo queda: imagen con su texto y hashtags, y el carrusel se recorre
    const preview = page.getByRole('group', { name: 'Vista previa' });
    await expect(preview.getByText('Cómo queda en Instagram')).toBeVisible();
    const first = preview.getByRole('img', { name: /^Pieza 1 de 3/ });
    await expect(first).toBeVisible();
    await expect.poll(() => first.evaluate((el: HTMLImageElement) => el.naturalWidth)).toBeGreaterThan(0);
    await expect(preview.getByText('Un verso de prueba')).toBeVisible();
    await expect(preview.getByText('#prueba')).toBeVisible();
    await preview.getByRole('button', { name: 'Pieza siguiente' }).click();
    await expect(preview.getByRole('img', { name: /^Pieza 2 de 3/ })).toBeVisible();
    await preview.getByRole('button', { name: 'Pieza siguiente' }).click();
    await expect(preview.locator('video')).toHaveAttribute('preload', 'none');

    // --- Archivos: imagen ampliada y vídeo con reproductor, de uno en uno
    const files = page.getByRole('group', { name: 'Archivos' });
    await files.getByRole('button', { name: 'Ver imagen' }).first().click();
    await expect(files.locator('.pv-open img')).toBeVisible();
    await files.getByRole('button', { name: 'Ver vídeo' }).click();
    await expect(files.locator('.pv-open video[controls]')).toBeVisible();
    await expect(files.locator('.pv-open img')).toHaveCount(0);

    // --- Aún no publicada: el enlace aparecerá más tarde
    await expect(preview.getByText(/aparecerá aquí cuando la publicación esté publicada/)).toBeVisible();
    await expect(preview.getByRole('link', { name: /^Ver en/ })).toHaveCount(0);

    // --- Una publicación sin piezas lo dice y da el motivo
    await page.getByRole('button', { name: 'Cerrar', exact: true }).first().click();
    await page.getByRole('button', { name: `Editar ${(await api.get(`/api/promotions/${promo.id}/publications?limit=50`)).items.find((p: { id: string }) => p.id === empty.id).title}`, exact: true }).click();
    await expect(page.getByRole('group', { name: 'Vista previa' }).getByText('Aún no hay archivos.')).toBeVisible();
    await expect(page.getByRole('group', { name: 'Vista previa' }).getByText(/Claude todavía no ha generado las piezas/)).toBeVisible();
  });
});
