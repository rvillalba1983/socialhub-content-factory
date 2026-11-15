import { expect, openMap, test } from '../support/fixtures';

/** Las 8 herramientas y servicios que SocialHub enseña en el panel, con el estado que deben tener en una instalación nueva. */
const ITEMS = ['FFmpeg', 'Google Drive (carpetas)', 'Imágenes (Java 2D)', 'Claude en la nube', 'Claude en el PC · Publer',
  'Publora', 'Instagram · API oficial', 'Copias de seguridad'];

test.describe('Agentes y herramientas', () => {
  test('el panel lista las herramientas y la tarjeta de Instagram, sin clave, deja claro qué hacer', async ({ page, api }) => {
    await openMap(page);
    const panel = page.getByRole('region', { name: 'Agentes y herramientas' });
    await expect(panel.getByRole('heading', { name: 'Agentes y herramientas' })).toBeVisible();
    for (const name of ITEMS) await expect(panel.getByRole('button', { name })).toBeVisible();
    await expect(panel).toContainText('0 € de gasto');

    // La API dice lo mismo: sin clave de Instagram
    expect((await api.get('/api/instagram')).configured).toBe(false);

    // --- Instagram · API oficial: abre su panel lateral
    await panel.getByRole('button', { name: 'Instagram · API oficial' }).click();
    const side = page.locator('aside.panel');
    await expect(side.getByRole('heading', { name: 'Instagram · API oficial' })).toBeVisible();
    await expect(side.getByText('Servicio', { exact: true })).toBeVisible();
    await expect(side).toContainText('Sin conexión: pulsa «Conectar con Instagram» o pega la clave en esta tarjeta');
    await expect(side).toContainText('0 € · gratuito');
    await expect(side).toContainText('Sabe hacer');

    // La tarjeta: «Sin conectar» y sin clave; «Conectar con Instagram» espera a que se guarde la app de Meta
    const card = side.getByRole('region', { name: 'Instagram · API oficial' });
    await expect(card.getByText('Sin conectar', { exact: true })).toBeVisible();
    await expect(card.getByRole('button', { name: 'Conectar con Instagram' })).toBeDisabled();
    await expect(card.getByRole('button', { name: 'Guardar la app' })).toBeDisabled();
    await expect(card.getByLabel('App Secret de Meta')).toHaveAttribute('type', 'password');
    await expect(card.getByRole('button', { name: 'Desconectar' })).toHaveCount(0);
    await expect(card.getByRole('heading', { name: 'Clave de usuario del sistema (alternativa)' })).toBeVisible();
    await expect(card.getByText('Sin clave', { exact: true })).toBeVisible();
    await expect(card.getByRole('button', { name: 'Comprobar clave' })).toHaveCount(0);
    await expect(card.getByRole('button', { name: 'Borrar clave' })).toHaveCount(0);

    // El campo es de contraseña (la clave no se ve) y una clave demasiado corta no se puede enviar
    const input = card.getByLabel('Clave de la API de Instagram', { exact: true });
    await expect(input).toHaveAttribute('type', 'password');
    await expect(input).toHaveAttribute('placeholder', 'Pega aquí la clave del usuario del sistema');
    const save = card.getByRole('button', { name: 'Comprobar y guardar' });
    await expect(save).toBeDisabled();
    await input.fill('corta');
    await expect(save).toBeDisabled();
    await input.fill('');

    // Cierra el panel con su botón
    await side.getByRole('button', { name: 'Cerrar panel' }).click();
    await expect(page.locator('aside.panel')).toHaveCount(0);
    // Sigue sin haberse guardado nada
    expect((await api.get('/api/instagram')).configured).toBe(false);
  });

  test('cada herramienta abre su ficha con qué es y su situación', async ({ page }) => {
    await openMap(page);
    const panel = page.getByRole('region', { name: 'Agentes y herramientas' });

    await panel.getByRole('button', { name: 'FFmpeg' }).click();
    const side = page.locator('aside.panel');
    await expect(side.getByRole('heading', { name: 'FFmpeg' })).toBeVisible();
    await expect(side.getByText('Herramienta local')).toBeVisible();
    await expect(side).toContainText('GPL (incluido con la aplicación)');
    await expect(side).toContainText('reel, clip, comprimir');

    // El panel lateral tapa la lista de herramientas (a 1366 px de ancho): hay que cerrarlo para elegir otra
    await side.getByRole('button', { name: 'Cerrar panel' }).click();
    await panel.getByRole('button', { name: 'Publora' }).click();
    await expect(side.getByRole('heading', { name: 'Publora' })).toBeVisible();
    await expect(side).toContainText('Sin clave');
    await expect(side.getByText('Servicio', { exact: true })).toBeVisible();

    await side.getByRole('button', { name: 'Cerrar panel' }).click();
    await panel.getByRole('button', { name: 'Claude en la nube' }).click();
    await expect(side.getByRole('heading', { name: 'Claude en la nube' })).toBeVisible();
    await expect(side).toContainText('Encargos de SocialHub');
    await expect(side.getByRole('region', { name: 'Instagram · API oficial' })).toHaveCount(0);
  });
});
