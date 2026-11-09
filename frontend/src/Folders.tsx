import { useEffect, useState } from 'react';
import { api } from './api';
import { FOLDER_ROLES, type Folder, type FolderRole } from './model';
import { Pill } from './ui';

/**
 * Qué carpeta de Drive hace cada papel. En la marca se fijan todos; en la promoción solo se escriben las
 * excepciones y el resto se hereda de la marca.
 */
export function FolderRoles({ owner, id, onSaved }: {
  owner: 'brand' | 'promotion'; id: string; onSaved?: (message: string) => void;
}) {
  const [folders, setFolders] = useState<Folder[] | null>(null);
  const [inherited, setInherited] = useState<Partial<Record<FolderRole, string>>>({});
  const [values, setValues] = useState<Partial<Record<FolderRole, string>>>({});
  const [busy, setBusy] = useState(false);
  const [base, setBase] = useState('');
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

  // Crea las carpetas asignadas que no existan (y, con carpeta base, las de los papeles vacíos)
  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await api.createBrandFolders(id, base.trim() || undefined);
      load(r.items);
      setBase('');
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
          ? 'Qué carpeta hace cada papel. Las promociones de la marca las heredan. SocialHub crea las que falten, pero nunca borra ni mueve nada.'
          : 'Las vacías se heredan de la marca. Escribe una ruta solo para el papel que cambie en esta promoción.'}
      </p>
      {folders === null && !error && <p className="src">Cargando…</p>}
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
      {owner === 'brand' && folders !== null && (
        <div className="folder-create">
          <label className="field"><span>Carpeta base <small className="src">· los papeles vacíos pasan a ser subcarpetas suyas</small></span>
            <input aria-label="Carpeta base" value={base} onChange={e => setBase(e.target.value)} placeholder="G:\Mi unidad\SocialHub\…" spellCheck={false} /></label>
          <button type="button" className="btn" disabled={busy} onClick={create}>Crear las que faltan</button>
        </div>
      )}
    </form>
  );
}
