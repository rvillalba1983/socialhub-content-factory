import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { FOLDER_ROLES, type Folder, type FolderRole } from './model';
import { Pill } from './ui';

/** La carpeta raíz no se guarda aparte: es la de Material cuando la de Encargos cuelga de ella como «encargos». */
function deriveRoot(own: Partial<Record<FolderRole, string>>): string {
  const material = own.MATERIAL ?? '';
  const orders = own.ORDERS ?? '';
  if (!material || !orders.startsWith(material)) return '';
  return /^[/\\]encargos$/i.test(orders.slice(material.length)) ? material : '';
}

/**
 * Qué carpeta de Drive hace cada papel. En la marca se fijan todos (o se crean desde una carpeta raíz); en la
 * promoción solo se escriben las excepciones y el resto se hereda de la marca.
 */
export function FolderRoles({ owner, id, onSaved }: {
  owner: 'brand' | 'promotion'; id: string; onSaved?: (message: string) => void;
}) {
  const [folders, setFolders] = useState<Folder[] | null>(null);
  const [inherited, setInherited] = useState<Partial<Record<FolderRole, string>>>({});
  const [values, setValues] = useState<Partial<Record<FolderRole, string>>>({});
  const [busy, setBusy] = useState(false);
  const [base, setBase] = useState('');
  const touched = useRef(false);
  const [error, setError] = useState<string | null>(null);

  const load = (list: Folder[]) => {
    setFolders(list);
    const own: Partial<Record<FolderRole, string>> = {};
    const brand: Partial<Record<FolderRole, string>> = {};
    list.forEach(f => {
      if (owner === 'brand' || f.origin === 'PROMOTION') own[f.role] = f.location;
      else brand[f.role] = f.location;
    });
    setValues(own);
    setInherited(brand);
    if (owner === 'brand') setBase(prev => (touched.current ? prev : deriveRoot(own)));
  };

  useEffect(() => {
    (owner === 'brand' ? api.brandFolders(id) : api.promotionFolders(id))
      .then(r => load(r.items))
      .catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, [owner, id]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const items = FOLDER_ROLES
      .map(([role]) => ({ role, location: (values[role] ?? '').trim() }))
      .filter(f => f.location !== '');
    try {
      const r = owner === 'brand' ? await api.saveBrandFolders(id, items) : await api.savePromotionFolders(id, items);
      load(r.items);
      onSaved?.('Carpetas guardadas');
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  // Crea la estructura desde la carpeta raíz (las que ya existen se respetan) y rellena los 7 papeles
  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await api.createBrandFolders(id, base.trim() || undefined);
      touched.current = false;
      load(r.items);
      onSaved?.('Carpetas creadas');
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const byRole = new Map((folders ?? []).map(f => [f.role, f]));
  return (
    <form className="card form" data-card="folders" onSubmit={save} aria-label="Carpetas de Drive">
      <h3>Carpetas de Drive</h3>
      <p className="src">
        {owner === 'brand'
          ? 'Escribe la carpeta raíz de la marca y SocialHub crea en ese disco (el sincronizado con Drive) las carpetas que falten y rellena los 7 papeles. Las que ya existen se respetan: nunca borra ni mueve nada. Las promociones de la marca las heredan.'
          : 'Las vacías se heredan de la marca. Escribe una ruta solo para el papel que cambie en esta promoción.'}
      </p>
      {folders === null && !error && <p className="src">Cargando…</p>}
      {owner === 'brand' && folders !== null && (
        <div className="folder-create">
          <label className="field"><span>Carpeta raíz <small className="src">· Material es la propia raíz; dentro van borradores, generados, aprobados, publicados, informes y encargos (con para-claude, para-claude-pc, de-claude y publico)</small></span>
            <input aria-label="Carpeta raíz" value={base} onChange={e => { setBase(e.target.value); touched.current = true; }}
              placeholder="G:\Mi unidad\socialhub\eldelito" spellCheck={false} /></label>
          <button type="button" className="btn primary" disabled={busy || base.trim() === ''} onClick={create}>Crear carpetas</button>
        </div>
      )}
      {owner === 'brand' && folders !== null && <h4>Rutas de cada papel <small className="src">· avanzado: cámbialas solo si quieres otra ruta</small></h4>}
      {folders !== null && FOLDER_ROLES.map(([role, label, hint]) => {
        const f = byRole.get(role);
        return (
          <label key={role} className="field folder-row">
            <span>{label} <small className="src">· {hint}</small></span>
            <span className="folder-in">
              <input aria-label={`Carpeta de ${label}`} placeholder={inherited[role] ?? 'G:\\Mi unidad\\…'}
                value={values[role] ?? ''} onChange={e => setValues({ ...values, [role]: e.target.value })} />
              {f && (f.available ? <Pill s="OK" label={f.origin === 'BRAND' && owner === 'promotion' ? 'De la marca' : 'Accesible'} />
                : <Pill s="BLOCKED" label="No disponible" />)}
            </span>
          </label>
        );
      })}
      <div className="form-actions">
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn primary" disabled={busy || folders === null}>{busy ? 'Guardando…' : 'Guardar carpetas'}</button>
      </div>
    </form>
  );
}
