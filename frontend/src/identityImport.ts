import type { BrandIdentity } from './model';

/**
 * Importar la identidad de una marca desde un .txt (formato versión 1, documentado en MANUAL.md y
 * docs/ejemplos/identidad-marca.txt). Cada campo empieza con «## Nombre»; las líneas «###» son comentarios.
 * Los límites son los de `BrandIdentity` en contracts/openapi.yaml.
 */
type Kind = { kind: 'text'; max: number } | { kind: 'list'; maxItems: number; max: number };
type Field = { key: keyof BrandIdentity; label: string; spec: Kind };

export const IDENTITY_FIELDS: Field[] = [
  { key: 'tone', label: 'Tono', spec: { kind: 'text', max: 2000 } },
  { key: 'audience', label: 'Público', spec: { kind: 'text', max: 2000 } },
  { key: 'forbidden_topics', label: 'Temas que no se tocan', spec: { kind: 'list', maxItems: 50, max: 200 } },
  { key: 'preferred_expressions', label: 'Expresiones preferidas', spec: { kind: 'list', maxItems: 100, max: 200 } },
  { key: 'forbidden_expressions', label: 'Expresiones prohibidas', spec: { kind: 'list', maxItems: 100, max: 200 } },
  { key: 'colors', label: 'Colores', spec: { kind: 'list', maxItems: 20, max: 40 } },
  { key: 'font', label: 'Tipografía', spec: { kind: 'text', max: 200 } },
  { key: 'logo', label: 'Logotipo', spec: { kind: 'text', max: 500 } },
  { key: 'visual_style', label: 'Estilo visual', spec: { kind: 'text', max: 2000 } },
  { key: 'photo_style', label: 'Estilo fotográfico', spec: { kind: 'text', max: 2000 } },
  { key: 'call_to_action', label: 'Llamada a la acción', spec: { kind: 'text', max: 500 } },
  { key: 'fixed_hashtags', label: 'Hashtags fijos', spec: { kind: 'text', max: 1000 } },
];

/** Sin mayúsculas ni tildes ni espacios sobrantes. */
const norm = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();
const BY_NAME = new Map(IDENTITY_FIELDS.map(f => [norm(f.label), f]));

export type ParsedIdentity = { values: Partial<BrandIdentity>; errors: string[] };

export function parseIdentityText(raw: string): ParsedIdentity {
  const values: Record<string, string | string[]> = {};
  const errors: string[] = [];
  let current: Field | null = null;
  let buffer: string[] = [];
  let ignoring = false;

  const close = () => {
    const f = current;
    if (!f) return;
    if (f.spec.kind === 'list') {
      const items: string[] = [];
      for (const line of buffer) {
        const t = line.trim();
        if (t === '') continue;
        if (!t.startsWith('- ') && t !== '-') {
          errors.push(`«${f.label}» es una lista: cada elemento va en una línea que empieza por «- » (línea «${t.slice(0, 40)}»).`);
          continue;
        }
        const item = t.slice(1).trim();
        if (item === '') continue;
        if (item.length > f.spec.max) errors.push(`«${f.label}»: el elemento «${item.slice(0, 30)}…» pasa de ${f.spec.max} caracteres.`);
        items.push(item);
      }
      if (items.length > f.spec.maxItems) errors.push(`«${f.label}» admite como mucho ${f.spec.maxItems} elementos y hay ${items.length}.`);
      values[f.key] = items;
    } else {
      const text = buffer.join('\n').trim();
      if (text.length > f.spec.max) errors.push(`«${f.label}» pasa del máximo de ${f.spec.max} caracteres (tiene ${text.length}).`);
      values[f.key] = text;
    }
    current = null;
    buffer = [];
  };

  for (const line of raw.replace(/^﻿/, '').split(/\r?\n/)) {
    if (line.startsWith('###')) continue;
    const header = /^##(?!#)\s*(.*)$/.exec(line);
    if (header) {
      close();
      const name = header[1].trim();
      const field = BY_NAME.get(norm(name));
      if (!field) {
        errors.push(`Campo desconocido: «${name}». Los campos válidos son: ${IDENTITY_FIELDS.map(f => f.label).join(', ')}.`);
        current = null;
        ignoring = true;
      } else if (field.key in values) {
        errors.push(`El campo «${field.label}» aparece dos veces.`);
        current = null;
        ignoring = true;
      } else {
        current = field;
        ignoring = false;
      }
      continue;
    }
    if (current) buffer.push(line);
    else if (!ignoring && line.trim() !== '') {
      errors.push(`Hay texto antes del primer «## Campo»: «${line.trim().slice(0, 40)}».`);
      ignoring = true;
    }
  }
  close();
  if (Object.keys(values).length === 0 && errors.length === 0) errors.push('No he encontrado ningún campo: cada uno empieza con una línea «## Nombre».');
  return { values: values as Partial<BrandIdentity>, errors };
}

/**
 * Plantilla para pegar en una IA: instrucción breve y, debajo, todos los campos con las indicaciones de formato y los
 * límites como comentarios «###». Si la marca ya tiene identidad, lleva los valores actuales para que la IA los mejore.
 */
export function identityTemplate(brand: { name: string; handle: string }, current?: BrandIdentity | null): string {
  const lines: string[] = [
    `Rellena esta plantilla con la identidad de la marca ${brand.name} (${brand.handle}). Respeta el formato exacto: cada campo empieza con una línea «## Nombre del campo» (no cambies los nombres), los campos de lista llevan un elemento por línea empezando por «- », y las líneas que empiezan por «###» son comentarios que puedes borrar. Respeta los máximos de cada campo. Si un campo no aplica, déjalo vacío bajo su «##». Devuélveme solo el .txt, sin explicaciones.`,
    '',
    '### Identidad de la marca · formato versión 1',
    '### Cada campo empieza con «## Nombre». Texto: lo que siga hasta el siguiente «##». Lista: un elemento por línea, empezando por «- ».',
    '',
  ];
  for (const f of IDENTITY_FIELDS) {
    lines.push(`## ${f.label}`);
    lines.push(f.spec.kind === 'list'
      ? `### Lista: un elemento por línea con «- ». Máximo ${f.spec.maxItems} elementos de ${f.spec.max} caracteres cada uno.`
      : `### Texto. Máximo ${f.spec.max} caracteres.`);
    const value = current?.[f.key];
    if (Array.isArray(value)) lines.push(...value.map(v => `- ${v}`));
    else if (value) lines.push(value);
    lines.push('');
  }
  return lines.join('\n');
}

export type IdentityChange ={ key: keyof BrandIdentity; label: string; before: string; after: string };

const show = (v: string | string[]) => (Array.isArray(v) ? v.map(x => `- ${x}`).join('\n') : v);

/** Los campos que cambian: antes → después. Los que no vienen en el archivo no aparecen. */
export function identityChanges(current: BrandIdentity, values: Partial<BrandIdentity>): IdentityChange[] {
  const out: IdentityChange[] = [];
  for (const f of IDENTITY_FIELDS) {
    if (!(f.key in values)) continue;
    const before = show(current[f.key]);
    const after = show(values[f.key] as string | string[]);
    if (before !== after) out.push({ key: f.key, label: f.label, before, after });
  }
  return out;
}
