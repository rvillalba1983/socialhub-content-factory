import path from 'node:path';
import { expect, makeTempDir, openMap, test, toast, unique } from '../support/fixtures';
import type { Locator, Page } from '@playwright/test';

/** ¿La tarjeta está a la vista en la ventana del navegador (la parte de arriba dentro de la pantalla)? */
const onScreen = (locator: Locator) => locator.evaluate(el => {
  const r = el.getBoundingClientRect();
  return r.top >= 0 && r.top < window.innerHeight * 0.6;
});

const strip = (page: Page) => page.getByRole('region', { name: 'Qué hacer ahora' });

test.describe('Avisos: «Qué hacer ahora» y «Te falta por completar»', () => {
  test('la franja del mapa y los avisos de la ficha se reparten el trabajo y se van al completarlo', async ({ page, api }) => {
    const brandName = unique('Marca avisos');
    const promoName = unique('Promoción avisos');
    const brand = await api.createBrand(brandName, `@${unique('avisos').toLowerCase()}`);
    await api.createPromotion(brand.id, promoName);
    // Más recomendaciones de otra marca, para que la franja tenga más de 3 y salga «Ver N más»
    const other = await api.createBrand(unique('Marca vecina'), `@${unique('vecina').toLowerCase()}`);
    await api.createPromotion(other.id, unique('Promoción vecina'));

    await openMap(page);
    const next = strip(page);
    await expect(next).toBeVisible();

    // --- «Qué hacer ahora»: 3 a la vista, «Ver N más» despliega el resto y «Ver menos» lo recoge
    await expect(next.locator('.aitem')).toHaveCount(3);
    const more = next.getByRole('button', { name: /^Ver \d+ más$/ });
    await expect(more).toBeVisible();
    const hidden = Number((await more.innerText()).match(/\d+/)![0]);
    expect(hidden).toBeGreaterThanOrEqual(3);
    await more.click();
    // (las recomendaciones se actualizan solas: no se compara con un número exacto)
    await expect.poll(() => next.locator('.aitem').count()).toBeGreaterThanOrEqual(3 + hidden);
    await expect(next.getByRole('button', { name: 'Ver menos' })).toHaveAttribute('aria-expanded', 'true');
    // Las recomendaciones de la marca: cuentas de Publer de las dos redes y propuestas retenidas (no hay carpeta de Encargos)
    const publerInstagram = next.getByRole('button', { name: new RegExp(`Indica la cuenta de Publer de ${brandName} para Instagram`) });
    await expect(publerInstagram).toBeVisible();
    await expect(next.getByRole('button', { name: new RegExp(`Indica la cuenta de Publer de ${brandName} para TikTok`) })).toBeVisible();
    await expect(next).toContainText(`Las propuestas de «${promoName}» no pueden salir`);
    await next.getByRole('button', { name: 'Ver menos' }).click();
    await expect(next.locator('.aitem')).toHaveCount(3);
    await expect(next.getByRole('button', { name: /^Ver \d+ más$/ })).toHaveAttribute('aria-expanded', 'false');
    await next.getByRole('button', { name: /^Ver \d+ más$/ }).click();

    // --- Un clic en la recomendación abre la ficha de la marca, ya posicionada en su tarjeta
    await publerInstagram.click();
    await expect(page.getByRole('heading', { name: 'Marcas y programación' }).or(page.getByRole('heading', { name: 'Marcas y promociones' }))).toBeVisible();
    const publerCard = page.locator('[data-card="publer"]');
    await expect(publerCard).toBeVisible();
    await expect.poll(() => onScreen(publerCard)).toBe(true);

    // --- «Te falta por completar»: identidad, carpetas y las dos cuentas de Publer
    const notices = page.getByRole('region', { name: 'Avisos de la marca' });
    await expect(notices.getByRole('heading', { name: 'Te falta por completar' })).toBeVisible();
    await expect(notices.getByRole('listitem')).toHaveCount(4);
    await expect(notices).toContainText('Falta rellenar la identidad de la marca: el tono y el público.');
    await expect(notices).toContainText('Carpetas sin configurar: Material, Borradores, Generados, Aprobados, Publicados, Informes, Encargos.');
    await expect(notices).toContainText('Falta indicar la cuenta de Publer de Instagram.');
    await expect(notices).toContainText('Falta indicar la cuenta de Publer de TikTok.');

    // Cada botón baja a su tarjeta
    await notices.getByRole('button', { name: 'Ir a la identidad' }).click();
    await expect.poll(() => onScreen(page.locator('[data-card="identity"]'))).toBe(true);
    await notices.getByRole('button', { name: 'Ir a las carpetas' }).click();
    await expect.poll(() => onScreen(page.locator('[data-card="folders"]'))).toBe(true);

    // La ficha ya avisa de carpetas y de Publer: «Qué hacer ahora» no lo repite para esta marca (sí lo de otras marcas)
    await expect(next).not.toContainText(`Indica la cuenta de Publer de ${brandName}`);
    await expect(next).toContainText('Indica la cuenta de Publer de');

    // --- Se completa cada cosa y su aviso se va
    // 1. Identidad
    const identity = page.getByRole('form', { name: 'Identidad de la marca' });
    await identity.getByLabel('Tono de comunicación').fill('Cercano y con humor');
    await identity.getByLabel('Público objetivo').fill('Lectores de 25 a 45 años');
    await identity.getByRole('button', { name: 'Guardar identidad' }).click();
    await expect(toast(page)).toHaveText('Identidad de la marca guardada');
    await expect(notices).not.toContainText('Falta rellenar la identidad');
    await expect(notices.getByRole('listitem')).toHaveCount(3);

    // 2. Cuentas de Publer
    const publer = page.getByRole('form', { name: 'Cuenta de Publer de la marca' });
    await publer.getByLabel('Cuenta de Publer para Instagram').fill('Cuenta IG de pruebas');
    await publer.getByRole('button', { name: 'Guardar cuentas' }).click();
    await expect(toast(page)).toHaveText('Cuentas de Publer guardadas');
    await expect(notices).not.toContainText('Falta indicar la cuenta de Publer de Instagram');
    await expect(notices).toContainText('Falta indicar la cuenta de Publer de TikTok');
    await publer.getByLabel('Cuenta de Publer para TikTok').fill('Cuenta TikTok de pruebas');
    await publer.getByRole('button', { name: 'Guardar cuentas' }).click();
    await expect(notices).not.toContainText('Publer');

    // 3. Carpetas, desde la carpeta raíz
    const root = path.join(makeTempDir('avisos'), 'raiz');
    const folders = page.getByRole('form', { name: 'Carpetas de Drive' });
    await folders.getByLabel('Carpeta raíz').fill(root);
    await folders.getByRole('button', { name: 'Crear carpetas' }).click();
    await expect(toast(page)).toHaveText('Carpetas creadas');

    // Con todo hecho, el bloque de avisos desaparece
    await expect(notices).toHaveCount(0);

    // --- Al volver al mapa, las recomendaciones de la marca ya no hablan de Publer ni de carpetas
    await page.getByRole('button', { name: 'Volver al mapa' }).click();
    await expect.poll(async () => (await api.factory()).recommendations
      .filter(r => r.target?.brand_id === brand.id && /Publer|carpetas/i.test(r.title)).length).toBe(0);
    await expect(strip(page)).not.toContainText(`Indica la cuenta de Publer de ${brandName}`);
    await expect(strip(page)).not.toContainText(`Las propuestas de «${promoName}» no pueden salir`);
  });
});
