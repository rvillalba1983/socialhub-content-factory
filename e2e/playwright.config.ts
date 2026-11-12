import net from 'node:net';
import { defineConfig, devices } from '@playwright/test';

/** Un puerto libre del ordenador: la app de pruebas no pisa a otra SocialHub abierta (8686). */
function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address() as net.AddressInfo;
      server.close(() => resolve(port));
    });
  });
}

// El proceso principal fija el puerto y los procesos de prueba lo heredan por el entorno
if (!process.env.E2E_PORT) process.env.E2E_PORT = String(await freePort());
const baseURL = `http://127.0.0.1:${process.env.E2E_PORT}`;

export default defineConfig({
  testDir: './tests',
  // Todos los recorridos comparten una sola SocialHub (con su PostgreSQL): van uno tras otro y en orden
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
    viewport: { width: 1366, height: 768 },
    // El portapapeles de «Copiar plantilla»
    permissions: ['clipboard-read', 'clipboard-write'],
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 768 } } }],
  webServer: {
    command: 'node scripts/start-app.mjs',
    url: `${baseURL}/api/app-mode`,
    timeout: 240_000,
    reuseExistingServer: false,
    gracefulShutdown: { signal: 'SIGTERM', timeout: 30_000 },
  },
});
