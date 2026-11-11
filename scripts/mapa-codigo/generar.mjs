// Genera el mapa 3D del código de SocialHub: un único index.html, sin servidor ni internet.
//
// Cada módulo del monolito modular es una isla hexagonal con sus capas en anillos
// (dominio en el centro, aplicación en medio y adaptadores fuera: entrada a un lado y salida al otro).
// El frontal (React) es otra isla y los contratos, el puente entre las dos.
//
// Uso: node scripts/mapa-codigo/generar.mjs [carpeta de salida]   (por defecto, scripts/mapa-codigo/dist)
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const out = path.resolve(process.argv[2] ?? path.join(here, 'dist'));
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

const MAX_TEXT = 160 * 1024;
const TEXT_EXT = new Set(['java', 'ts', 'tsx', 'js', 'mjs', 'css', 'sql', 'yaml', 'yml', 'json', 'md', 'html', 'xml', 'd2', 'sh', 'ps1', 'cmd', 'properties', 'txt']);
const SKIP = [/package-lock\.json$/, /\/node_modules\//, /\.(jpg|jpeg|png|gif|webp|svg|ico|mp4|txz|jar|zip)$/i];
const GENERATED = [/frontend\/src\/api\/schema\.d\.ts$/];

/** Isla y capa de cada archivo. */
function classify(file) {
  let m = file.match(/^backend\/src\/(main|test)\/java\/com\/socialhub\/([^/]+)\/(.*)$/);
  if (m) {
    const [, kind, mod, rest] = m;
    if (mod.endsWith('.java')) return { island: 'platform', layer: 'aplicacion' };
    if (kind === 'test') return { island: mod, layer: 'pruebas' };
    if (rest.startsWith('domain/')) return { island: mod, layer: 'dominio' };
    if (rest.startsWith('adapter/in/')) return { island: mod, layer: 'entrada' };
    if (rest.startsWith('adapter/out/')) return { island: mod, layer: 'salida' };
    // Módulos sin capas hexagonales: lo que habla con fuera hace de adaptador
    if (mod === 'platform') {
      if (/^(web|live)\//.test(rest)) return { island: mod, layer: 'entrada' };
      if (/^(postgres|process|observability)\//.test(rest)) return { island: mod, layer: 'salida' };
    }
    if (/Controller\.java$/.test(rest)) return { island: mod, layer: 'entrada' };
    return { island: mod, layer: 'aplicacion' };
  }
  if (/^backend\/src\/test\//.test(file)) return { island: 'platform', layer: 'pruebas' };
  if (/^backend\/src\/main\/resources\/db\/migration\//.test(file)) return { island: 'basedatos', layer: 'salida' };
  if (/^backend\//.test(file)) return { island: 'platform', layer: 'config' };
  if (/^frontend\/src\/.*\.test\.tsx?$/.test(file)) return { island: 'frontal', layer: 'pruebas' };
  if (/^frontend\/src\/(api|api\.ts|model\.ts)/.test(file)) return { island: 'frontal', layer: 'salida' };
  if (/^frontend\/src\/.*\.(css)$/.test(file)) return { island: 'frontal', layer: 'dominio' };
  if (/^frontend\/src\/(main|App)\.tsx$/.test(file)) return { island: 'frontal', layer: 'aplicacion' };
  if (/^frontend\/src\//.test(file)) return { island: 'frontal', layer: 'entrada' };
  if (/^frontend\//.test(file)) return { island: 'frontal', layer: 'config' };
  if (/^contracts\//.test(file)) return { island: 'contratos', layer: 'aplicacion' };
  if (/^docs\/|\.md$/.test(file)) return { island: 'docs', layer: 'aplicacion' };
  return { island: 'herramientas', layer: 'aplicacion' };
}

const ISLANDS = {
  publishing: ['Publicación', 'backend'], ai: ['Claude (IA)', 'backend'], production: ['Producción', 'backend'],
  promotion: ['Promociones', 'backend'], pieces: ['Piezas', 'backend'], platform: ['Plataforma', 'backend'],
  factory: ['Fábrica', 'backend'], armory: ['Armería', 'backend'], operations: ['Operaciones', 'backend'],
  drive: ['Drive', 'backend'], tools: ['Herramientas locales', 'backend'], demo: ['Demo', 'backend'],
  api: ['API generada', 'backend'], backup: ['Copias', 'backend'],
  basedatos: ['Base de datos (Flyway)', 'datos'], frontal: ['Frontal (React)', 'frontal'],
  contratos: ['Contratos', 'contratos'], docs: ['Documentación', 'docs'], herramientas: ['CI y scripts', 'docs'],
};

const files = git('ls-files').split('\n').filter(Boolean).filter(f => !SKIP.some(r => r.test(f)) && fs.existsSync(path.join(root, f)));

// Cambios por archivo en el historial
const churn = {};
for (const line of git('log', '--format=', '--name-only', '--no-renames').split('\n')) {
  if (line) churn[line] = (churn[line] ?? 0) + 1;
}

const items = [];
const deps = {};
for (const file of files) {
  const ext = file.split('.').pop().toLowerCase();
  if (!TEXT_EXT.has(ext)) continue;
  const raw = fs.readFileSync(path.join(root, file), 'utf8');
  const lines = raw.split('\n').length;
  const { island, layer } = classify(file);
  const generated = GENERATED.some(r => r.test(file));
  const text = generated ? raw.slice(0, 4000) + '\n\n… (archivo generado: se muestra solo el principio)' : raw.length > MAX_TEXT ? raw.slice(0, MAX_TEXT) + '\n\n… (recortado)' : raw;
  items.push({ path: file, island, layer, lines, ext, churn: churn[file] ?? 0, text });
  // Quién usa a quién entre módulos del backend (imports de com.socialhub.<módulo>)
  if (ext === 'java' && ISLANDS[island]?.[1] === 'backend') {
    for (const m of raw.matchAll(/^import\s+(?:static\s+)?com\.socialhub\.([a-z]+)\./gm)) {
      const target = m[1];
      if (target !== island && ISLANDS[target]) {
        const key = `${island}>${target}`;
        deps[key] = (deps[key] ?? 0) + 1;
      }
    }
  }
}
// El frontal habla con el backend por los contratos (OpenAPI) y la API generada
deps['frontal>contratos'] = (deps['frontal>contratos'] ?? 0) + 1;
deps['contratos>api'] = (deps['contratos>api'] ?? 0) + 1;
deps['platform>basedatos'] = (deps['platform>basedatos'] ?? 0) + 1;

const commit = git('rev-parse', '--short', 'HEAD').trim();
const branch = (process.env.GITHUB_HEAD_REF || git('rev-parse', '--abbrev-ref', 'HEAD').trim());
const data = {
  generated: new Date().toISOString(), commit, branch,
  islands: Object.fromEntries(Object.entries(ISLANDS).filter(([id]) => items.some(i => i.island === id))
    .map(([id, [label, kind]]) => [id, { label, kind }])),
  deps: Object.entries(deps).map(([k, n]) => { const [from, to] = k.split('>'); return { from, to, n }; }),
  files: items,
};

const three = fs.readFileSync(path.join(here, 'node_modules/three/build/three.module.min.js'), 'utf8');
const orbit = fs.readFileSync(path.join(here, 'node_modules/three/examples/jsm/controls/OrbitControls.js'), 'utf8');
const toDataUrl = js => 'data:text/javascript;base64,' + Buffer.from(js).toString('base64');
// Reemplazos con función: con una cadena, «$'», «$&» o «$`» del código (p. ej. `$'\t'` en un script de bash)
// se interpretarían como patrones y pegarían trozos del HTML en medio de los datos.
const html = fs.readFileSync(path.join(here, 'visor.html'), 'utf8')
  .replace('"__THREE__"', () => JSON.stringify(toDataUrl(three)))
  .replace('"__ORBIT__"', () => JSON.stringify(toDataUrl(orbit)))
  .replace('/*__DATA__*/null', () => JSON.stringify(data).replace(/</g, '\\u003c'));
if (html.split('</html>').length !== 2) throw new Error('El mapa ha salido mal formado: más de un </html>');

fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, 'index.html'), html);
const total = items.reduce((s, i) => s + i.lines, 0);
console.log(`Mapa del código: ${items.length} archivos, ${total} líneas, ${Object.keys(data.islands).length} islas → ${path.join(out, 'index.html')} (${(html.length / 1024 / 1024).toFixed(1)} MB)`);
