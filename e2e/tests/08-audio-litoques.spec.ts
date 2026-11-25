import path from 'node:path';
import { expect, makeTempDir, openMap, test, toast, unique } from '../support/fixtures';

test.describe('Carpeta de audio de Litoques', () => {
  test('se guarda en Claude, se relee tras recargar y se puede quitar', async ({ page, api }) => {
    const brand = await api.createBrandWithFolders(unique('Marca audio'), `@${unique('audio').toLowerCase()}`, path.join(makeTempDir('audio-marca'), 'raiz'));
    await api.createPromotion(brand.id, unique('Promoción audio'));
    const folder = makeTempDir('audio-litoques');

    await openMap(page);
    await page.getByRole('button', { name: 'Claude', exact: true }).click();
    const card = page.getByRole('form', { name: 'Carpeta de audio de Litoques' });
    await expect(card.getByText(/Tema de Litoques en el vídeo/)).toBeVisible();
    await card.getByLabel(/Ruta de la carpeta con los MP3/).fill(folder);
    await card.getByRole('button', { name: 'Guardar carpeta de audio' }).click();
    await expect(toast(page)).toHaveText('Carpeta de audio de Litoques guardada');
    expect(await api.get<{ litoques_folder: string; available: boolean }>('/api/settings/audio')).toMatchObject({ litoques_folder: folder, available: true });

    // Se relee al volver a abrir la pantalla
    await page.reload();
    await openMap(page);
    await page.getByRole('button', { name: 'Claude', exact: true }).click();
    await expect(page.getByRole('form', { name: 'Carpeta de audio de Litoques' }).getByLabel(/Ruta de la carpeta con los MP3/)).toHaveValue(folder);

    // Vacía: se quita el ajuste
    const again = page.getByRole('form', { name: 'Carpeta de audio de Litoques' });
    await again.getByLabel(/Ruta de la carpeta con los MP3/).fill('');
    await again.getByRole('button', { name: 'Guardar carpeta de audio' }).click();
    await expect(toast(page)).toHaveText('Carpeta de audio de Litoques guardada');
    expect((await api.get<{ litoques_folder: string | null }>('/api/settings/audio')).litoques_folder).toBeNull();
  });
});
