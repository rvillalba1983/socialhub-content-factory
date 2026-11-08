import { useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import {
  shortDate, type Automation, type Brand, type BrandInput, type Milestone, type Promotion, type PromotionInput,
  type PromotionResource, type PublishingRoute, type Setting, type Snapshot,
} from './model';
import { Pill } from './ui';
import { PubloraCard } from './Publora';
import { FolderRoles } from './Folders';
import { IdentityForm } from './Identity';
import { MemoryCard } from './Memory';

type Editing = { kind: 'brand'; id: string | null } | { kind: 'promotion'; id: string | null } | null;

const PROMOTION_STATUS: [PromotionInput['status'], string][] = [
  ['PLANNED', 'Planificada'], ['ACTIVE', 'Activa'], ['PAUSED', 'En pausa'], ['FINISHED', 'Terminada'],
];

/** Alta y edición de marcas y promociones. */
export function Registration({ data, initial, onClose, onSaved }: {
  data: Snapshot; initial: Editing; onClose: () => void; onSaved: (message: string) => void;
}) {
  const [editing, setEditing] = useState<Editing>(initial);
  const brands = new Map(data.brands.map(b => [b.id, b]));

  return (
    <section className="cc" aria-label="Marcas y promociones">
      <div className="cc-in">
        <div><button className="btn back" onClick={onClose}><Icon name="back" />Volver al mapa</button></div>
        <div className="cc-head">
          <div>
            <div className="eyebrow">{data.demo ? 'Modo demo · datos de ejemplo' : 'Modo real'}</div>
            <h1>Marcas y promociones</h1>
            <p>Da de alta tus marcas y sus promociones. Cada promoción aparece en el anillo con su centro de control.</p>
          </div>
        </div>

        {editing?.kind === 'brand' && (
          <BrandForm brand={editing.id ? brands.get(editing.id) : undefined} onCancel={() => setEditing(null)}
            onSaved={(b, note) => { setEditing(null); onSaved(`Marca guardada · ${b.name}${note ? ` · ${note}` : ''}`); }} />
        )}
        {editing?.kind === 'brand' && editing.id && <IdentityForm brandId={editing.id} onSaved={onSaved} />}
        {editing?.kind === 'brand' && editing.id && <MemoryCard brandId={editing.id} onSaved={onSaved} />}
        {editing?.kind === 'brand' && editing.id && <FolderRoles owner="brand" id={editing.id} onSaved={onSaved} />}
        {editing?.kind === 'brand' && editing.id && <PubloraCard brandId={editing.id} onSaved={onSaved} />}
        {editing?.kind === 'promotion' && (
          <PromotionForm data={data} promotion={editing.id ? data.promotions.find(p => p.id === editing.id) : undefined}
            onCancel={() => setEditing(null)} onSaved={p => { setEditing(null); onSaved(`Promoción guardada · ${p.name}`); }} />
        )}
        {editing?.kind === 'promotion' && editing.id && <FolderRoles owner="promotion" id={editing.id} onSaved={onSaved} />}

        {!editing && (
          <div className="grid2">
            <div className="card">
              <div className="row-between"><h3>Marcas</h3>
                <button className="btn primary" onClick={() => setEditing({ kind: 'brand', id: null })}>Nueva marca</button></div>
              {data.brands.length === 0 && <p>Todavía no hay marcas. Empieza por aquí.</p>}
              {data.brands.map(b => (
                <button key={b.id} className="qrow" onClick={() => setEditing({ kind: 'brand', id: b.id })}>
                  <span className="swatch" style={{ background: b.color }} /><span>{b.name}<small>{b.handle}</small></span>
                </button>
              ))}
            </div>
            <div className="card">
              <div className="row-between"><h3>Promociones</h3>
                <button className="btn primary" disabled={data.brands.length === 0} title={data.brands.length === 0 ? 'Primero da de alta una marca' : undefined}
                  onClick={() => setEditing({ kind: 'promotion', id: null })}>Nueva promoción</button></div>
              {data.promotions.length === 0 && <p>Todavía no hay promociones.</p>}
              {data.promotions.map(p => (
                <button key={p.id} className="qrow" onClick={() => setEditing({ kind: 'promotion', id: p.id })}>
                  <span className="swatch" style={{ background: brands.get(p.brand_id)?.color }} />
                  <span>{p.name}<small>{brands.get(p.brand_id)?.name} · {shortDate(p.starts_on)} – {shortDate(p.ends_on)}</small></span>
                  <Pill s={p.status === 'ACTIVE' ? 'OK' : p.status === 'PAUSED' ? 'WAITING' : 'IDLE'}
                    label={PROMOTION_STATUS.find(([k]) => k === p.status)?.[1]} />
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function useSave<T>(save: () => Promise<T>, onSaved: (value: T) => void) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try { onSaved(await save()); } catch (err) { setError(err instanceof Error ? err.message : String(err)); } finally { setBusy(false); }
  };
  return { busy, error, submit };
}

export function BrandForm({ brand, onCancel, onSaved }: { brand?: Brand; onCancel: () => void; onSaved: (b: Brand, note?: string) => void }) {
  const [form, setForm] = useState<BrandInput>({ name: brand?.name ?? '', handle: brand?.handle ?? '@', color: brand?.color ?? '#D23A2E' });
  const [base, setBase] = useState('');
  // Al dar de alta, con una carpeta base se crean sus 7 carpetas. Si eso falla, la marca ya está creada: se avisa sin repetir el alta.
  const { busy, error, submit } = useSave(async (): Promise<[Brand, string | undefined]> => {
    if (brand) return [await api.updateBrand(brand.id, form), undefined];
    const created = await api.createBrand(form);
    if (!base.trim()) return [created, undefined];
    try {
      await api.createBrandFolders(created.id, base.trim());
      return [created, 'carpetas creadas'];
    } catch (e) {
      return [created, `no se pudieron crear las carpetas (${e instanceof Error ? e.message : String(e)}); créalas en «Carpetas de Drive»`];
    }
  }, ([b, note]) => onSaved(b, note));
  return (
    <form className="card form" onSubmit={submit} aria-label={brand ? 'Editar marca' : 'Nueva marca'}>
      <h3>{brand ? `Editar marca · ${brand.name}` : 'Nueva marca'}</h3>
      <Field label="Nombre"><input required maxLength={80} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
      <Field label="Cuenta"><input required maxLength={80} value={form.handle} onChange={e => setForm({ ...form, handle: e.target.value })} /></Field>
      <Field label="Color de la marca">
        <span className="color-row"><input type="color" aria-label="Color de la marca" value={form.color} onChange={e => setForm({ ...form, color: e.target.value.toUpperCase() })} />
          <span className="mono">{form.color}</span></span>
      </Field>
      {!brand && <Field label="Carpeta base de Drive (opcional)">
        <input value={base} onChange={e => setBase(e.target.value)} placeholder="G:\Mi unidad\SocialHub\Litoques" spellCheck={false} />
        <small className="src">Dentro se crean Material, Borradores, Generados, Aprobados, Publicados, Informes y Encargos.</small>
      </Field>}
      <FormActions busy={busy} error={error} onCancel={onCancel} />
    </form>
  );
}

const emptyPromotion = (brandId: string): PromotionInput => ({
  brand_id: brandId, name: '', description: '', scope: '', cadence: '', starts_on: '', ends_on: '', status: 'PLANNED',
  metrics_source: '', goals: [], rules: [], human_todos: [], milestones: [], resources: [], automations: [], routes: [], settings: [],
});

const toInput = (p: Promotion): PromotionInput => {
  const { id: _id, demo: _demo, ...input } = p;
  return input;
};

export function PromotionForm({ data, promotion, onCancel, onSaved }: {
  data: Snapshot; promotion?: Promotion; onCancel: () => void; onSaved: (p: Promotion) => void;
}) {
  const [form, setForm] = useState<PromotionInput>(promotion ? toInput(promotion) : emptyPromotion(data.brands[0]?.id ?? ''));
  const set = <K extends keyof PromotionInput>(k: K, v: PromotionInput[K]) => setForm({ ...form, [k]: v });
  const { busy, error, submit } = useSave(
    () => (promotion ? api.updatePromotion(promotion.id, form) : api.createPromotion(form)), onSaved);

  return (
    <form className="card form" onSubmit={submit} aria-label={promotion ? 'Editar promoción' : 'Nueva promoción'}>
      <h3>{promotion ? `Editar promoción · ${promotion.name}` : 'Nueva promoción'}</h3>
      <div className="form-grid">
        <Field label="Marca">
          <select required value={form.brand_id} onChange={e => set('brand_id', e.target.value)}>
            {data.brands.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </Field>
        <Field label="Nombre"><input required maxLength={120} value={form.name} onChange={e => set('name', e.target.value)} /></Field>
        <Field label="Empieza"><input required type="date" value={form.starts_on} onChange={e => set('starts_on', e.target.value)} /></Field>
        <Field label="Termina"><input required type="date" min={form.starts_on || undefined} value={form.ends_on} onChange={e => set('ends_on', e.target.value)} /></Field>
        <Field label="Estado">
          <select value={form.status} onChange={e => set('status', e.target.value as PromotionInput['status'])}>
            {PROMOTION_STATUS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
        </Field>
        <Field label="Cadencia"><input maxLength={500} placeholder="Posts L/X/V 19:00 · historias M/J 13:00" value={form.cadence ?? ''} onChange={e => set('cadence', e.target.value)} /></Field>
      </div>
      <Field label="Descripción"><textarea rows={2} maxLength={2000} value={form.description ?? ''} onChange={e => set('description', e.target.value)} /></Field>
      <Field label="Alcance"><textarea rows={2} maxLength={2000} value={form.scope ?? ''} onChange={e => set('scope', e.target.value)} /></Field>
      <Field label="De dónde salen las métricas"><input maxLength={500} value={form.metrics_source ?? ''} onChange={e => set('metrics_source', e.target.value)} /></Field>
      <div className="form-grid">
        <Lines label="Objetivos" value={form.goals ?? []} onChange={v => set('goals', v)} />
        <Lines label="Normas de la promoción" value={form.rules ?? []} onChange={v => set('rules', v)} />
        <Lines label="Lo que espera a Raúl" value={form.human_todos ?? []} onChange={v => set('human_todos', v)} />
      </div>

      <Rows<Milestone> label="Hitos" value={form.milestones ?? []} onChange={v => set('milestones', v)}
        blank={{ starts_on: form.starts_on, label: '' }}
        render={(m, up) => (<>
          <input type="date" required aria-label="Desde" value={m.starts_on} onChange={e => up({ ...m, starts_on: e.target.value })} />
          <input type="date" aria-label="Hasta" value={m.ends_on ?? ''} onChange={e => up({ ...m, ends_on: e.target.value || undefined })} />
          <input required aria-label="Hito" placeholder="Hito" value={m.label} onChange={e => up({ ...m, label: e.target.value })} />
        </>)} />
      <Rows<PromotionResource> label="Recursos (carpetas de Drive y documentos)" value={form.resources ?? []} onChange={v => set('resources', v)}
        blank={{ kind: 'DRIVE', location: '', description: '' }}
        render={(r, up) => (<>
          <select aria-label="Tipo" value={r.kind} onChange={e => up({ ...r, kind: e.target.value as PromotionResource['kind'] })}>
            <option value="DRIVE">Carpeta</option><option value="DOC">Documento</option></select>
          <input required aria-label="Ubicación" placeholder="G:\Mi unidad\…" value={r.location} onChange={e => up({ ...r, location: e.target.value })} />
          <input aria-label="Para qué" placeholder="Para qué" value={r.description} onChange={e => up({ ...r, description: e.target.value })} />
        </>)} />
      <Rows<Automation> label="Tareas programadas" value={form.automations ?? []} onChange={v => set('automations', v)}
        blank={{ name: '', schedule: '', model: 'SONNET', runs_on: 'CLOUD', status: 'IDLE' }}
        render={(a, up) => (<>
          <input required aria-label="Nombre" placeholder="Nombre" value={a.name} onChange={e => up({ ...a, name: e.target.value })} />
          <input required aria-label="Cuándo" placeholder="Lunes 9:00" value={a.schedule} onChange={e => up({ ...a, schedule: e.target.value })} />
          <select aria-label="Modelo" value={a.model} onChange={e => up({ ...a, model: e.target.value as Automation['model'] })}>
            <option value="HAIKU">Haiku</option><option value="SONNET">Sonnet</option><option value="OPUS">Opus</option></select>
          <select aria-label="Dónde" value={a.runs_on} onChange={e => up({ ...a, runs_on: e.target.value as Automation['runs_on'] })}>
            <option value="CLOUD">Nube</option><option value="PC">PC de Raúl</option></select>
        </>)} />
      <Rows<PublishingRoute> label="Vías de publicación, por orden de preferencia" value={form.routes ?? []} onChange={v => set('routes', v)}
        blank={{ name: '', status: 'IDLE', label: '', notes: '' }}
        render={(r, up) => (<>
          <input required aria-label="Vía" placeholder="Publer" value={r.name} onChange={e => up({ ...r, name: e.target.value })} />
          <input aria-label="Situación" placeholder="Vía principal" value={r.label} onChange={e => up({ ...r, label: e.target.value })} />
          <input aria-label="Notas" placeholder="Notas" value={r.notes} onChange={e => up({ ...r, notes: e.target.value })} />
        </>)} />
      <Rows<Setting> label="Configuración" value={form.settings ?? []} onChange={v => set('settings', v)}
        blank={{ label: '', value: '', origin: 'PROMOTION' }}
        render={(s, up) => (<>
          <input required aria-label="Parámetro" placeholder="Parámetro" value={s.label} onChange={e => up({ ...s, label: e.target.value })} />
          <input required aria-label="Valor" placeholder="Valor" value={s.value} onChange={e => up({ ...s, value: e.target.value })} />
          <select aria-label="Origen" value={s.origin} onChange={e => up({ ...s, origin: e.target.value as Setting['origin'] })}>
            <option value="PROMOTION">Propio</option><option value="BRAND">De la marca</option></select>
        </>)} />
      <FormActions busy={busy} error={error} onCancel={onCancel} />
    </form>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="field"><span>{label}</span>{children}</label>;
}

/** Lista de textos: uno por línea. */
function Lines({ label, value, onChange }: { label: string; value: string[]; onChange: (v: string[]) => void }) {
  const [text, setText] = useState(value.join('\n'));
  return (
    <Field label={`${label} (uno por línea)`}>
      <textarea rows={4} value={text} onChange={e => { setText(e.target.value); onChange(e.target.value.split('\n').map(x => x.trim()).filter(Boolean)); }} />
    </Field>
  );
}

function Rows<T>({ label, value, onChange, blank, render }: {
  label: string; value: T[]; onChange: (v: T[]) => void; blank: T; render: (row: T, update: (row: T) => void) => React.ReactNode;
}) {
  return (
    <fieldset className="rows">
      <legend>{label}</legend>
      {value.map((row, i) => (
        <div key={i} className="rows-row">
          {render(row, r => onChange(value.map((x, j) => (j === i ? r : x))))}
          <button type="button" className="ibtn" aria-label="Quitar" title="Quitar" onClick={() => onChange(value.filter((_, j) => j !== i))}><Icon name="x" /></button>
        </div>
      ))}
      <button type="button" className="btn" onClick={() => onChange([...value, blank])}>Añadir</button>
    </fieldset>
  );
}

function FormActions({ busy, error, onCancel }: { busy: boolean; error: string | null; onCancel: () => void }) {
  return (
    <div className="form-actions">
      {error && <p className="error" role="alert">{error}</p>}
      <button className="btn primary" disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</button>
      <button type="button" className="btn" onClick={onCancel}>Cancelar</button>
    </div>
  );
}
