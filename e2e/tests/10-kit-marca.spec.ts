import { brandRow, daysFromToday, expect, openMap, test, unique } from '../support/fixtures';
import type { Page } from '@playwright/test';

/** Abre la ficha de una marca desde «Marcas» y devuelve su tarjeta «Kit de marca». */
async function openKit(page: Page, name: string) {
  await openMap(page);
  await page.getByRole('button', { name: 'Marcas', exact: true }).click();
  await brandRow(page, name).click();
  const card = page.getByLabel('Kit de marca', { exact: true });
  await expect(card).toBeVisible();
  return card;
}

test.describe('Kit de marca', () => {
  test('una marca sin promociones no recibe la recomendación y la ficha explica que hace falta una', async ({ page, api }) => {
    const name = unique('Marca kit sin promo');
    const brand = await api.createBrand(name, `@${unique('kitsin').toLowerCase()}`);
    expect((await api.factory()).recommendations.map(r => r.id)).not.toContain(`brand-kit:${brand.id}`);

    const card = await openKit(page, name);
    await expect(card.getByText('Esta marca todavía no tiene kit.')).toBeVisible();
    await expect(card.getByText(/aún no tiene ninguna promoción/)).toBeVisible();
    await expect(card.getByRole('button', { name: 'Pedir kit' })).toBeDisabled();
  });

  test('con una promoción sale «Genera el kit de X»; pedir el kit deja el encargo en curso y quita la recomendación', async ({ page, api }) => {
    const name = unique('Marca kit');
    const brand = await api.createBrand(name, `@${unique('kit').toLowerCase()}`);
    await api.createPromotion(brand.id, unique('Promoción kit'), { starts_on: daysFromToday(0) });
    const recommendation = (await api.factory()).recommendations.find(r => r.id === `brand-kit:${brand.id}`);
    expect(recommendation?.kind).toBe('BRAND_KIT_MISSING');
    expect(recommendation?.title).toBe(`Genera el kit de ${name}`);
    expect(recommendation?.target).toMatchObject({ screen: 'BRAND_KIT', brand_id: brand.id });

    const card = await openKit(page, name);
    // Sin cita (solo para El DeLito) y con cierre e historia marcados
    await expect(card.getByLabel('Cierre del carrusel')).toBeChecked();
    await expect(card.getByLabel('Historia')).toBeChecked();
    await expect(card.getByLabel(/Cita/)).toHaveCount(0);
    await card.getByRole('button', { name: 'Pedir kit' }).click();
    await expect(card.getByRole('status')).toContainText('Encargo en curso');
    await expect(card.getByRole('button', { name: 'Pedir kit' })).toHaveCount(0);

    // El encargo está abierto: la fábrica ya no vuelve a recomendarlo y la API sigue sin kit (nada se usa hasta aprobar)
    await expect.poll(async () => (await api.factory()).recommendations.map(r => r.id)).not.toContain(`brand-kit:${brand.id}`);
    // Con el encargo abierto no se puede pedir otro kit de la marca: la API responde 409
    const duplicate = await page.request.post(`/api/brands/${brand.id}/kit/request`, { data: { pieces: ['cierre'] }, headers: { 'X-SocialHub-Request': '1' } });
    expect(duplicate.status()).toBe(409);
    expect((await duplicate.json()).detail).toContain('encargo de kit abierto');
    const kit = await api.get(`/api/brands/${brand.id}/kit`);
    expect(kit.approved ?? null).toBeNull();
    expect(kit.pending ?? null).toBeNull();
  });
});
