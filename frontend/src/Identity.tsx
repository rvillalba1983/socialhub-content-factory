import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import type { BrandIdentity } from './model';
import { identityChanges, identityTemplate, parseIdentityText, type IdentityChange } from './identityImport';

const lines = (v: string[]) => v.join('\n');
const parse = (t: string) => t.split('\n').map(x => x.trim()).filter(Boolean);

/** Identidad de la marca: la heredan sus promociones, va en cada encargo a Claude y la usa el control de calidad. */
export function IdentityForm({ brandId, brand, onSaved }: {
  brandId: string; brand?: { name: string; handle: string }; onSaved?: (message: string) => void;
}) {
  const [identity, setIdentity] = useState<BrandIdentity | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [importing, setImporting] = useState(false);
  const [raw, setRaw] = useState('');
  const [preview, setPreview] = useState<{ values: Partial<BrandIdentity>; changes: IdentityChange[]; errors: string[] } | null>(null);
  const [copied, setCopied] = useState(false);
  const [fileName, setFileName] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    api.brandIdentity(brandId).then(setIdentity).catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, [brandId]);

  if (!identity) return <div className="card" data-card="identity"><h3>Identidad de la marca</h3>{error ? <p className="error" role="alert">{error}</p> : <p className="src">Cargando…</p>}</div>;
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

  const copyTemplate = async () => {
    setError(null);
    try {
      await navigator.clipboard.writeText(identityTemplate(brand ?? { name: brandId, handle: '' }, identity));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 4000);
    } catch { setError('No he podido copiar al portapapeles. Prueba con otro navegador o permite el acceso.'); }
  };

  const check = (t: string) => {
    const parsed = parseIdentityText(t);
    setPreview({ values: parsed.values, errors: parsed.errors, changes: parsed.errors.length ? [] : identityChanges(identity, parsed.values) });
  };

  const onFile = async (file: File | undefined) => {
    if (!file) return;
    setFileName(file.name);
    const t = await file.text();
    setRaw(t);
    check(t);
  };

  // Los campos del archivo sustituyen a los de la ficha; los que no vienen se quedan como estaban
  const saveImported = async () => {
    if (!preview) return;
    setBusy(true);
    setError(null);
    try {
      setIdentity(await api.saveBrandIdentity(brandId, { ...identity, ...preview.values }));
      setVersion(v => v + 1);
      setPreview(null);
      setRaw('');
      setFileName(null);
      setImporting(false);
      onSaved?.('Identidad importada y guardada');
    } catch (err) { setError(err instanceof Error ? err.message : String(err)); } finally { setBusy(false); }
  };

  return (
    <form className="card form" data-card="identity" onSubmit={save} aria-label="Identidad de la marca">
      <h3>Identidad de la marca</h3>
      <p className="src">La heredan sus promociones. Va en cada encargo a Claude y el control de calidad comprueba las expresiones prohibidas, la llamada a la acción y los hashtags fijos.</p>
      <div className="form-actions">
        <button type="button" className="btn" onClick={() => setImporting(i => !i)}>Importar desde .txt</button>
        <button type="button" className="btn" onClick={() => void copyTemplate()}>Copiar plantilla</button>
        {copied && <span role="status" className="src">Plantilla copiada</span>}
      </div>
      {importing && (
        <div className="identity-import" aria-label="Importar identidad">
          <p className="src">Sube un .txt o pega el texto (formato de «Copiar plantilla»). Antes de guardar verás qué campos cambian; los que no vengan en el archivo se quedan como están.</p>
          <div className="file-pick">
            <input ref={fileInput} type="file" accept=".txt,text/plain" hidden aria-label="Archivo .txt de la identidad"
              onChange={e => { void onFile(e.target.files?.[0]); e.target.value = ''; }} />
            <button type="button" className="btn" onClick={() => fileInput.current?.click()}>Elegir archivo .txt</button>
            <span className="src" data-testid="identity-file-name">{fileName ?? 'Ningún archivo elegido'}</span>
          </div>
          <textarea rows={8} aria-label="Texto de la identidad" placeholder="## Tono&#10;…" value={raw} onChange={e => { setRaw(e.target.value); setPreview(null); }} />
          <div className="form-actions">
            <button type="button" className="btn" disabled={raw.trim() === ''} onClick={() => check(raw)}>Ver cambios</button>
          </div>
          {preview && preview.errors.length > 0 && (
            <div role="alert" className="error"><b>No se puede importar:</b>
              <ul>{preview.errors.map((m, i) => <li key={i}>{m}</li>)}</ul></div>
          )}
          {preview && preview.errors.length === 0 && (
            <div className="identity-diff" role="region" aria-label="Cambios de la identidad">
              {preview.changes.length === 0 && <p className="src">No cambia ningún campo.</p>}
              {preview.changes.length > 0 && (
                <table className="diff-table">
                  <thead><tr><th scope="col">Campo</th><th scope="col">Ahora</th><th scope="col">Después de importar</th></tr></thead>
                  <tbody>
                    {preview.changes.map(c => (
                      <tr key={c.key}>
                        <th scope="row">{c.label}</th>
                        <td aria-label={`${c.label} antes`}><DiffValue text={c.before} /></td>
                        <td aria-label={`${c.label} después`}><DiffValue text={c.after} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {preview.changes.length > 0 && (
                <div className="form-actions">
                  <button type="button" className="btn primary" disabled={busy} onClick={() => void saveImported()}>Guardar estos cambios</button>
                </div>
              )}
            </div>
          )}
        </div>
      )}
      <div className="form-grid" key={version}>
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

/** Valor de la vista previa con la letra normal de la interfaz; las listas, una línea por elemento. */
function DiffValue({ text }: { text: string }) {
  if (text === '') return <span className="src">(vacío)</span>;
  return <>{text.split('\n').map((l, i) => <div key={i}>{l}</div>)}</>;
}
