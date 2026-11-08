import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import { dateTime, type Backup, type BackupList } from './model';
import { Pill } from './ui';

const KIND: Record<Backup['kind'], string> = { DAILY: 'Diaria', MANUAL: 'A mano', BEFORE_RESTORE: 'Antes de restaurar' };

const size = (bytes: number) =>
  bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB`;

/**
 * Copias de seguridad de la base de datos real: carpeta de copias (ajuste general), lista, copia ahora y restaurar.
 * Restaurar pide escribir el nombre de la copia y, al terminar, SocialHub se para hasta que se vuelve a abrir.
 */
export function Backups({ onClose, onMessage, onHalted }: { onClose: () => void; onMessage: (m: string) => void; onHalted: (reason: string) => void }) {
  const [list, setList] = useState<BackupList | null>(null);
  const [folder, setFolder] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [restoring, setRestoring] = useState<Backup | null>(null);

  const load = useCallback(async () => {
    try {
      const l = await api.backups();
      setList(l);
      setFolder(l.folder ?? '');
      setError(null);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const run = async (what: string, action: () => Promise<void>) => {
    setBusy(what);
    setError(null);
    try { await action(); } catch (e) { setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(null); }
  };

  const where = !list ? '' : list.folder && list.folder_available ? list.folder : list.local_folder;

  return (
    <section className="cc" aria-label="Copias de seguridad">
      <div className="cc-in">
        <div><button className="btn back" onClick={onClose}><Icon name="back" />Volver al mapa</button></div>
        <div className="cc-head">
          <div>
            <div className="eyebrow">Ajustes generales</div>
            <h1>Copias de seguridad</h1>
            <p>Una copia al día de la base de datos real al abrir SocialHub. Se guardan las {list?.keep ?? 14} últimas de cada carpeta.</p>
          </div>
          <button className="btn primary" disabled={!!busy || !list}
            onClick={() => run('copia', async () => { const b = await api.createBackup(); onMessage(`Copia hecha: ${b.name}`); await load(); })}>
            {busy === 'copia' ? 'Haciendo copia…' : 'Hacer copia ahora'}
          </button>
        </div>

        {error && <p className="error" role="alert">{error}</p>}

        {list && (
          <form className="card form" aria-label="Carpeta de copias"
            onSubmit={e => { e.preventDefault(); run('carpeta', async () => { const l = await api.saveBackupFolder(folder); setList(l); onMessage(l.folder ? 'Carpeta de copias guardada' : 'Carpeta de copias quitada'); }); }}>
            <h3>Carpeta de copias</h3>
            {!list.folder && <p><Pill s="BLOCKED" label="Sin elegir" /> Mientras no elijas una, las copias se guardan solo en este ordenador ({list.local_folder}).</p>}
            {list.folder && !list.folder_available && <p><Pill s="BLOCKED" label="No disponible" /> No se encuentra la carpeta. Mientras tanto, las copias van a este ordenador. ¿Está abierto Google Drive?</p>}
            <label className="field"><span>Carpeta (por ejemplo, una de Google Drive en G:)</span>
              <input value={folder} onChange={e => setFolder(e.target.value)} placeholder="G:\Mi unidad\SocialHub\Copias" spellCheck={false} /></label>
            <div className="form-actions">
              <button className="btn primary" disabled={!!busy}>{busy === 'carpeta' ? 'Guardando…' : 'Guardar carpeta'}</button>
              <span className="src">Las próximas copias van a: <span className="mono">{where}</span></span>
            </div>
          </form>
        )}

        <div className="tbl-wrap"><table>
          <thead><tr><th>Copia</th><th>Cuándo</th><th>Tipo</th><th>Tamaño</th><th>Dónde</th><th></th></tr></thead>
          <tbody>
            {list?.items.map(b => (
              <tr key={b.name + b.local}>
                <td className="mono">{b.name}</td>
                <td className="nowrap">{dateTime(b.created_at)}</td>
                <td>{KIND[b.kind]}</td>
                <td className="nowrap mono">{size(b.size_bytes)}</td>
                <td>{b.local ? 'Este ordenador' : 'Carpeta de copias'}</td>
                <td><button className="btn" style={{ padding: '3px 8px' }} disabled={!!busy} onClick={() => setRestoring(b)}>Restaurar…</button></td>
              </tr>
            ))}
            {list && list.items.length === 0 && <tr><td colSpan={6}>Todavía no hay copias.</td></tr>}
            {!list && !error && <tr><td colSpan={6}>Cargando…</td></tr>}
          </tbody>
        </table></div>

        {restoring && <RestoreCard backup={restoring} busy={busy === 'restaurar'} onCancel={() => setRestoring(null)}
          onConfirm={confirmation => run('restaurar', async () => { const r = await api.restoreBackup(restoring.name, confirmation); onHalted(r.halted_reason); })} />}
      </div>
    </section>
  );
}

function RestoreCard({ backup, busy, onCancel, onConfirm }: { backup: Backup; busy: boolean; onCancel: () => void; onConfirm: (confirmation: string) => void }) {
  const [typed, setTyped] = useState('');
  return (
    <form className="card form" aria-label="Restaurar copia" onSubmit={e => { e.preventDefault(); onConfirm(typed); }}>
      <h3>Restaurar {backup.name}</h3>
      <p>Los datos actuales se sustituyen por los de esta copia ({dateTime(backup.created_at)}). Antes se hace una copia de lo actual («antes de restaurar»), por si hay que volver atrás.
        Al terminar, SocialHub se para y tendrás que cerrarlo y volver a abrirlo.</p>
      <label className="field"><span>Para confirmar, escribe el nombre de la copia</span>
        <input value={typed} onChange={e => setTyped(e.target.value)} placeholder={backup.name} spellCheck={false} autoComplete="off" /></label>
      <div className="form-actions">
        <button className="btn primary" disabled={busy || typed.trim() !== backup.name}>{busy ? 'Restaurando…' : 'Restaurar'}</button>
        <button type="button" className="btn" disabled={busy} onClick={onCancel}>Cancelar</button>
      </div>
    </form>
  );
}

/** Pantalla que tapa todo cuando SocialHub está parado (tras restaurar una copia). */
export function Halted({ reason }: { reason: string }) {
  return (
    <div className="halted" role="alertdialog" aria-modal="true" aria-label="SocialHub está parado">
      <div className="card">
        <h3>SocialHub está parado</h3>
        <p>{reason}</p>
        <p className="src">No se guarda ningún cambio y el motor no trabaja hasta que lo vuelvas a abrir.</p>
      </div>
    </div>
  );
}
