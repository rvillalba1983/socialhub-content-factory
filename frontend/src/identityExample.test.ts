import { describe, expect, it } from 'vitest';
import { identityChanges, parseIdentityText } from './identityImport';
import type { BrandIdentity } from './model';
import example from '../../docs/ejemplos/identidad-marca.txt?raw';
import expected from '../../backend/src/test/resources/flows/identidad-marca.json?raw';

/**
 * Primera mitad de la prueba de flujo «Importar identidad desde .txt»: el ejemplo que se entrega con el manual
 * (`docs/ejemplos/identidad-marca.txt`) se lee sin errores y sale justo lo que hay en
 * `backend/src/test/resources/flows/identidad-marca.json`. La segunda mitad la hace el backend
 * (`IdentityImportFlowTest`): guarda ese mismo JSON por la API y comprueba que se guarda idéntico y que llega al
 * control de calidad y a los encargos. Si cambia el formato del .txt o la API, una de las dos se rompe.
 */
describe('el ejemplo de identidad del manual', () => {
  it('se lee sin errores y da exactamente lo que el backend guarda en su prueba de flujo', () => {
    const parsed = parseIdentityText(example);
    expect(parsed.errors).toEqual([]);
    expect(parsed.values).toEqual(JSON.parse(expected));
  });

  it('en una marca sin identidad, la vista previa enseña los 12 campos como cambios (antes vacío)', () => {
    const empty: BrandIdentity = {
      tone: '', audience: '', forbidden_topics: [], preferred_expressions: [], forbidden_expressions: [], colors: [],
      font: '', logo: '', visual_style: '', photo_style: '', call_to_action: '', fixed_hashtags: '',
    };
    const changes = identityChanges(empty, parseIdentityText(example).values);
    expect(changes).toHaveLength(12);
    expect(changes.every(c => c.before === '' && c.after !== '')).toBe(true);
  });

  it('si ya está guardado, el mismo archivo no cambia nada', () => {
    const current = JSON.parse(expected) as BrandIdentity;
    expect(identityChanges(current, parseIdentityText(example).values)).toEqual([]);
  });
});
