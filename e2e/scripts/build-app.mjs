// Compila SocialHub como lo hace «Arranque real»: primero la web y después el jar con la web dentro.
// Uso: npm run build:app   (o npm run e2e:full, que además lanza las pruebas)
// Variables: MVN (por defecto «mvn»; en el entorno de Claude en la nube, /opt/jdk/mvnu) y NPM (por defecto «npm»;
// allí, /opt/jdk/npmp).
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

function run(command, args, cwd) {
  console.log(`\n> ${command} ${args.join(' ')}   (${path.relative(root, cwd) || '.'})`);
  const result = spawnSync(command, args, { cwd, stdio: 'inherit', shell: process.platform === 'win32' });
  if (result.status !== 0) {
    console.error(`Ha fallado: ${command} ${args.join(' ')}`);
    process.exit(result.status ?? 1);
  }
}

const npm = process.env.NPM ?? 'npm';
const mvn = process.env.MVN ?? 'mvn';
const frontend = path.join(root, 'frontend');

run(npm, ['ci'], frontend);
run(npm, ['run', 'build'], frontend);
run(mvn, ['-B', '-q', '-DskipTests', 'package'], path.join(root, 'backend'));
console.log('\nListo: backend/target/socialhub.jar con la web dentro.');
