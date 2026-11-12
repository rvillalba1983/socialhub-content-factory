// Arranca SocialHub de verdad para las pruebas: el jar con la web dentro, una carpeta de datos temporal, PostgreSQL
// incluido (puerto libre) y la API en E2E_PORT. Playwright lo lanza con `webServer` y espera a /api/app-mode.
//
// - Nada de servicios externos: no hay claves de Publora ni de Meta, y las carpetas de Drive son carpetas temporales.
// - PostgreSQL no puede ir como root. En el entorno de Claude en la nube (donde todo va como root) el jar se lanza como
//   el usuario «claude», igual que `/opt/jdk/mvnu`; en GitHub y en el PC de Raúl se lanza con el usuario actual.
// - Al terminar (Playwright manda SIGTERM) se para el jar, que apaga PostgreSQL, y se borran los datos.
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const e2e = path.resolve(here, '..');
const jar = path.resolve(e2e, '..', 'backend', 'target', 'socialhub.jar');
const port = process.env.E2E_PORT;
if (!port) throw new Error('Falta E2E_PORT (lo pone playwright.config.ts)');
if (!fs.existsSync(jar)) {
  console.error(`No existe ${jar}. Compílalo antes: npm run build:app (o npm run e2e:full).`);
  process.exit(1);
}

const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'socialhub-e2e-data-'));
// Si el jar va como otro usuario (caso root), tiene que poder escribir aquí
fs.chmodSync(dataDir, 0o777);
fs.mkdirSync(path.join(e2e, '.run'), { recursive: true });
const logPath = path.join(e2e, '.run', 'app.log');
const log = fs.openSync(logPath, 'w');

const args = [
  '-jar', jar,
  `--server.port=${port}`,
  `--socialhub.data-dir=${dataDir}`,
  '--socialhub.postgres.port=0',
  // Sin simulador de la demo y con los envíos a Claude cada 1,5 s (por defecto, cada 30 s): las pruebas no esperan rondas
  '--socialhub.demo.simulate=false',
  '--socialhub.ai.initial-delay-millis=1000',
  '--socialhub.ai.dispatch-millis=1500',
];

const asRoot = typeof process.getuid === 'function' && process.getuid() === 0;
let child;
if (asRoot) {
  const quoted = args.map(a => `'${a}'`).join(' ');
  fs.chmodSync(path.join(e2e, '.run'), 0o777);
  fs.chmodSync(logPath, 0o666);
  child = spawn('su', ['claude', '-s', '/bin/bash', '-c', `[ -f /opt/jdk/env.sh ] && . /opt/jdk/env.sh; exec java ${quoted}`],
    { stdio: ['ignore', log, log] });
} else {
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
  child = spawn(java, args, { stdio: ['ignore', log, log] });
}
console.log(`SocialHub arrancando en http://127.0.0.1:${port} (datos en ${dataDir}, registro en ${logPath})`);

let stopping = false;
function cleanup() {
  fs.rmSync(dataDir, { recursive: true, force: true });
}
function stop() {
  if (stopping) return;
  stopping = true;
  if (asRoot) {
    // `su` no pasa las señales con seguridad: se para el jar por su carpeta de datos, que es única
    spawnSync('pkill', ['-TERM', '-f', `--socialhub.data-dir=${dataDir}`]);
  } else {
    child.kill('SIGTERM');
  }
  // Si el jar no se para en 20 s, se mata (el evento «exit» de abajo borra los datos y termina)
  setTimeout(() => {
    if (asRoot) spawnSync('pkill', ['-KILL', '-f', `--socialhub.data-dir=${dataDir}`]);
    else child.kill('SIGKILL');
  }, 20_000).unref();
}
process.on('SIGTERM', stop);
process.on('SIGINT', stop);
child.on('exit', code => {
  if (!stopping) console.error(`SocialHub se ha parado solo (código ${code}). Mira ${logPath}`);
  cleanup();
  process.exit(stopping ? 0 : (code ?? 1));
});
