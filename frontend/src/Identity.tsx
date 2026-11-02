import { useEffect, useState } from 'react';
import { api } from './api';
import type { BrandIdentity } from './model';

const lines = (v: string[]) => v.join('\n');
const parse = (t: string) => t.split('\n').map(x => x.trim()).filter(Boolean);

/** Identidad de la marca: la heredan sus promociones, va en cada encargo a Claude y la usa el control de calidad. */
export function IdentityForm({ brandId, onSaved }: { brandId: string; onSaved?: (message: string) => void }) {
  const [identity, setIdentity] = useState<BrandIdentity | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.brandIdentity(brandId).then(setIdentity).catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, [brandId]);

  if (!identity) return <div className="card"><h3>Identidad de la marca</h3>{error ? <p className="error" role="alert">{error}</p> : <p className="src">Cargando…</p>}</div>;
  const set = <K extends keyof BrandIdentity>(k: K, v: BrandIdentity[K]) => setIdentity({ ...identity, [k]: v });
  const text = (k: 'tone' | 'audience' | 'font' | 'logo' | 'visual_style' | 'photo_style' | 'call_to_action' | 'fixed_hashtags',
    label: string, rows = 1, max = 2000) => (
    <label className="field"><span>{label}</span>
      {rows > 1 ? <textarea rows={rows} maxLength={max} value={identity[k]} onChange={e => set(k, e.target.value)} />
        : <input maxLength={max} value={identity[k]} onChange={e => set(k, e.target.value)} />}</label>
  );
  const list = (k: 'forbidden_topics' | 'preferred_expressions' | 'forbidden_expressions' | 'colors', label: string) => (
    <ListField label={label} value={identity[k]} onChange={v => set(k, v)} />
  );

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try { setIdentity(await api.saveBrandIdentity(brandId, identity)); onSaved?.('Identidad de la marca guardada'); }
    catch (err) { setError(err instanceof Error ? err.message : String(err)); } finally { setBusy(false); }
  };

  return (
    <form className="card form" onSubmit={save} aria-label="Identidad de la marca">
      <h3>Identidad de la marca</h3>
      <p className="src">La heredan sus promociones. Va en cada encargo a Claude y el control de calidad comprueba las expresiones prohibidas, la llamada a la acción y los hashtags fijos.</p>
      <div className="form-grid">
        {text('tone', 'Tono de comunicación', 3)}
        {text('audience', 'Público objetivo', 3)}
        {list('forbidden_topics', 'Temas que no se tocan')}
        {list('preferred_expressions', 'Expresiones preferidas')}
        {list('forbidden_expressions', 'Expresiones prohibidas')}
        {list('colors', 'Colores')}
        {text('font', 'Tipografía', 1, 200)}
        {text('logo', 'Logotipo (ruta o descripción)', 1, 500)}
        {text('visual_style', 'Estilo visual', 3)}
        {text('photo_style', 'Estilo fotográfico', 3)}
      </div>
      {text('call_to_action', 'Llamada a la acción (al final de los textos, salvo historias)', 1, 500)}
      {text('fixed_hashtags', 'Hashtags fijos', 1, 1000)}
      <div className="form-actions">
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn primary" disabled={busy}>{busy ? 'Guardando…' : 'Guardar identidad'}</button>
      </div>
    </form>
  );
}

function ListField({ label, value, onChange }: { label: string; value: string[]; onChange: (v: string[]) => void }) {
  const [text, setText] = useState(lines(value));
  return (
    <label className="field"><span>{label} (una por línea)</span>
      <textarea rows={3} value={text} onChange={e => { setText(e.target.value); onChange(parse(e.target.value)); }} /></label>
  );
}
