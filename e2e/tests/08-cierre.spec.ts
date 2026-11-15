import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, openMap, test } from '../support/fixtures';

const here = path.dirname(fileURLToPath(import.meta.url));

/** Procesos que siguen vivos con esta carpeta de datos: el jar y la PostgreSQL que arrancó (solo Linux y macOS). */
function aliveWith(dataDir: string): string[] {
  const out = spawnSync('pgrep', ['-af', dataDir], { encoding: 'utf8' }).stdout.trim();
  return out === '' ? [] : out.split('\n');
}

// Este recorrido tiene que ser el último: cierra la app de todos los demás (por eso su nombre empieza por 08)
test.describe('Cierre de la app', () => {
  test('el botón «Cerrar» pide confirmación, para el servidor y apaga PostgreSQL', async ({ page, request, allowConsole }) => {
    // Al cerrarse el servidor, las peticiones que la web aún lanza fallan: es lo esperado

    test.setTimeout(150_000);
    allowConsole(/ERR_CONNECTION_REFUSED|Failed to load resource|Failed to fetch/);
    const dataDir = fs.readFileSync(path.join(here, '..', '.run', 'data-dir.txt'), 'utf8').trim();
    const canInspect = process.platform !== 'win32';
    if (canInspect) expect(aliveWith(dataDir).length, 'la app y su PostgreSQL deberían estar vivas').toBeGreaterThan(0);

    await openMap(page);
    await page.getByRole('button', { name: 'Cerrar', exact: true }).click();
    const ask = page.getByRole('group', { name: '¿Cerrar SocialHub?' });
    await expect(ask).toBeVisible();

    // «No» deja todo como estaba
    await ask.getByRole('button', { name: 'No' }).click();
    await expect(ask).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Cerrar', exact: true })).toBeVisible();
    expect((await request.get('/api/app-mode')).ok()).toBe(true);

    // «Sí, cerrar»: pantalla de despedida y servidor parado
    await page.getByRole('button', { name: 'Cerrar', exact: true }).click();
    await page.getByRole('group', { name: '¿Cerrar SocialHub?' }).getByRole('button', { name: 'Sí, cerrar' }).click();
    const closed = page.getByRole('alertdialog', { name: 'SocialHub cerrado' });
    await expect(closed).toBeVisible();
    await expect(closed).toContainText('SocialHub se ha cerrado');
    await expect(closed).toContainText('Puedes cerrar esta pestaña');

    // La API deja de contestar
    await expect.poll(async () => {
      try { await request.get('/api/app-mode', { timeout: 2000 }); return 'responde'; } catch { return 'parada'; }
    }, { timeout: 30_000, intervals: [500] }).toBe('parada');

    // Y no queda nada vivo: ni el jar ni PostgreSQL. Con una pestaña abierta, el cierre ordenado de Tomcat espera 30 s a
    // la petición de «En directo» (fallo conocido, anotado en la PR), así que se da hasta un minuto
    if (canInspect) {
      await expect.poll(() => aliveWith(dataDir), { timeout: 60_000, message: 'procesos que siguen vivos' }).toEqual([]);
    }
  });
});
