import { describe, expect, it } from 'vitest';
import { identityChanges, identityTemplate, parseIdentityText } from './identityImport';
import type { BrandIdentity } from './model';

const base: BrandIdentity = {
  tone: 'Cercano', audience: 'Lectores', forbidden_topics: [], preferred_expressions: ['hola'], forbidden_expressions: [],
  colors: [], font: '', logo: '', visual_style: '', photo_style: '', call_to_action: '', fixed_hashtags: '',
};

describe('parseIdentityText', () => {
  it('lee textos y listas, ignora comentarios y admite mayúsculas y tildes distintas', () => {
    const r = parseIdentityText('### comentario\n## TONO\nIrónico\ny directo\n### otro\n## publico\nGente\n## Temas que no se tocan\n- política\n- religión\n\n## Tipografia\nInter\n');
    expect(r.errors).toEqual([]);
    expect(r.values).toEqual({ tone: 'Irónico\ny directo', audience: 'Gente', forbidden_topics: ['política', 'religión'], font: 'Inter' });
  });
  it('un campo vacío se queda vacío y los ausentes no vienen', () => {
    const r = parseIdentityText('## Tono\n\n## Colores\n');
    expect(r.errors).toEqual([]);
    expect(r.values).toEqual({ tone: '', colors: [] });
    expect('audience' in r.values).toBe(false);
  });
  it('avisa de campos desconocidos, repetidos, texto suelto y listas mal escritas', () => {
    expect(parseIdentityText('## Rareza\nx').errors[0]).toMatch(/Campo desconocido: «Rareza»/);
    expect(parseIdentityText('## Tono\na\n## Tono\nb').errors[0]).toMatch(/dos veces/);
    expect(parseIdentityText('suelto\n## Tono\na').errors[0]).toMatch(/antes del primer/);
    expect(parseIdentityText('## Colores\nrojo').errors[0]).toMatch(/lista/);
    expect(parseIdentityText('nada').errors.length).toBeGreaterThan(0);
  });
  it('respeta los máximos del contrato', () => {
    expect(parseIdentityText(`## Tipografía\n${'a'.repeat(201)}`).errors[0]).toMatch(/200/);
    expect(parseIdentityText(`## Colores\n${Array.from({ length: 21 }, (_, i) => `- c${i}`).join('\n')}`).errors[0]).toMatch(/20 elementos/);
    expect(parseIdentityText(`## Colores\n- ${'a'.repeat(41)}`).errors[0]).toMatch(/40/);
  });
});

describe('plantilla', () => {
  it('lo que sale de «Copiar plantilla» (sin la instrucción) se vuelve a importar sin errores y con los valores actuales', () => {
    const body = identityTemplate({ name: 'X', handle: '@x' }, base).split('\n').slice(2).join('\n');
    const r = parseIdentityText(body);
    expect(r.errors).toEqual([]);
    expect(r.values.tone).toBe('Cercano');
    expect(r.values.preferred_expressions).toEqual(['hola']);
    expect(Object.keys(r.values)).toHaveLength(12);
  });
});

describe('identityChanges', () => {
  it('solo enseña lo que cambia, antes y después', () => {
    const { values } = parseIdentityText('## Tono\nCercano\n## Público\nOtros\n## Expresiones preferidas\n- hola\n- adiós\n');
    expect(identityChanges(base, values)).toEqual([
      { key: 'audience', label: 'Público', before: 'Lectores', after: 'Otros' },
      { key: 'preferred_expressions', label: 'Expresiones preferidas', before: '- hola', after: '- hola\n- adiós' },
    ]);
  });
});
