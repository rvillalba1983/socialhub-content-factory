import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import { dateTime } from './model';
import { Pill } from './ui';
const KIND = { DAILY: 'Diaria', MANUAL: 'A mano', BEFORE_RESTORE: 'Antes de restaurar' };
const size = (bytes) => bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB`;
/**
 * Copias de seguridad de la base de datos real: carpeta de copias (ajuste general), lista, copia ahora y restaurar.
 * Restaurar pide escribir el nombre de la copia y, al terminar, SocialHub se para hasta que se vuelve a abrir.
 */
export function Backups({ onClose, onMessage, onHalted }) {
    const [list, setList] = useState(null);
    const [folder, setFolder] = useState('');
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(null);
    const [restoring, setRestoring] = useState(null);
    const load = useCallback(async () => {
        try {
            const l = await api.backups();
            setList(l);
            setFolder(l.folder ?? '');
            setError(null);
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
    }, []);
    useEffect(() => { load(); }, [load]);
    const run = async (what, action) => {
        setBusy(what);
        setError(null);
        try {
            await action();
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
        finally {
            setBusy(null);
        }
    };
    const where = !list ? '' : list.folder && list.folder_available ? list.folder : list.local_folder;
    return (_jsx("section", { className: "cc", "aria-label": "Copias de seguridad", children: _jsxs("div", { className: "cc-in", children: [_jsx("div", { children: _jsxs("button", { className: "btn back", onClick: onClose, children: [_jsx(Icon, { name: "back" }), "Volver al mapa"] }) }), _jsxs("div", { className: "cc-head", children: [_jsxs("div", { children: [_jsx("div", { className: "eyebrow", children: "Ajustes generales" }), _jsx("h1", { children: "Copias de seguridad" }), _jsxs("p", { children: ["Una copia al d\u00EDa de la base de datos real al abrir SocialHub. Se guardan las ", list?.keep ?? 14, " \u00FAltimas de cada carpeta."] })] }), _jsx("button", { className: "btn primary", disabled: !!busy || !list, onClick: () => run('copia', async () => { const b = await api.createBackup(); onMessage(`Copia hecha: ${b.name}`); await load(); }), children: busy === 'copia' ? 'Haciendo copia…' : 'Hacer copia ahora' })] }), error && _jsx("p", { className: "error", role: "alert", children: error }), list && (_jsxs("form", { className: "card form", "aria-label": "Carpeta de copias", onSubmit: e => { e.preventDefault(); run('carpeta', async () => { const l = await api.saveBackupFolder(folder); setList(l); onMessage(l.folder ? 'Carpeta de copias guardada' : 'Carpeta de copias quitada'); }); }, children: [_jsx("h3", { children: "Carpeta de copias" }), !list.folder && _jsxs("p", { children: [_jsx(Pill, { s: "BLOCKED", label: "Sin elegir" }), " Mientras no elijas una, las copias se guardan solo en este ordenador (", list.local_folder, ")."] }), list.folder && !list.folder_available && _jsxs("p", { children: [_jsx(Pill, { s: "BLOCKED", label: "No disponible" }), " No se encuentra la carpeta. Mientras tanto, las copias van a este ordenador. \u00BFEst\u00E1 abierto Google Drive?"] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Carpeta (por ejemplo, una de Google Drive en G:)" }), _jsx("input", { value: folder, onChange: e => setFolder(e.target.value), placeholder: "G:\\Mi unidad\\SocialHub\\Copias", spellCheck: false })] }), _jsxs("div", { className: "form-actions", children: [_jsx("button", { className: "btn primary", disabled: !!busy, children: busy === 'carpeta' ? 'Guardando…' : 'Guardar carpeta' }), _jsxs("span", { className: "src", children: ["Las pr\u00F3ximas copias van a: ", _jsx("span", { className: "mono", children: where })] })] })] })), _jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Copia" }), _jsx("th", { children: "Cu\u00E1ndo" }), _jsx("th", { children: "Tipo" }), _jsx("th", { children: "Tama\u00F1o" }), _jsx("th", { children: "D\u00F3nde" }), _jsx("th", {})] }) }), _jsxs("tbody", { children: [list?.items.map(b => (_jsxs("tr", { children: [_jsx("td", { className: "mono", children: b.name }), _jsx("td", { className: "nowrap", children: dateTime(b.created_at) }), _jsx("td", { children: KIND[b.kind] }), _jsx("td", { className: "nowrap mono", children: size(b.size_bytes) }), _jsx("td", { children: b.local ? 'Este ordenador' : 'Carpeta de copias' }), _jsx("td", { children: _jsx("button", { className: "btn", style: { padding: '3px 8px' }, disabled: !!busy, onClick: () => setRestoring(b), children: "Restaurar\u2026" }) })] }, b.name + b.local))), list && list.items.length === 0 && _jsx("tr", { children: _jsx("td", { colSpan: 6, children: "Todav\u00EDa no hay copias." }) }), !list && !error && _jsx("tr", { children: _jsx("td", { colSpan: 6, children: "Cargando\u2026" }) })] })] }) }), restoring && _jsx(RestoreCard, { backup: restoring, busy: busy === 'restaurar', onCancel: () => setRestoring(null), onConfirm: confirmation => run('restaurar', async () => { const r = await api.restoreBackup(restoring.name, confirmation); onHalted(r.halted_reason); }) })] }) }));
}
function RestoreCard({ backup, busy, onCancel, onConfirm }) {
    const [typed, setTyped] = useState('');
    return (_jsxs("form", { className: "card form", "aria-label": "Restaurar copia", onSubmit: e => { e.preventDefault(); onConfirm(typed); }, children: [_jsxs("h3", { children: ["Restaurar ", backup.name] }), _jsxs("p", { children: ["Los datos actuales se sustituyen por los de esta copia (", dateTime(backup.created_at), "). Antes se hace una copia de lo actual (\u00ABantes de restaurar\u00BB), por si hay que volver atr\u00E1s. Al terminar, SocialHub se para y tendr\u00E1s que cerrarlo y volver a abrirlo."] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Para confirmar, escribe el nombre de la copia" }), _jsx("input", { value: typed, onChange: e => setTyped(e.target.value), placeholder: backup.name, spellCheck: false, autoComplete: "off" })] }), _jsxs("div", { className: "form-actions", children: [_jsx("button", { className: "btn primary", disabled: busy || typed.trim() !== backup.name, children: busy ? 'Restaurando…' : 'Restaurar' }), _jsx("button", { type: "button", className: "btn", disabled: busy, onClick: onCancel, children: "Cancelar" })] })] }));
}
/** Pantalla que tapa todo cuando SocialHub está parado (tras restaurar una copia) o cerrado. */
export function Halted({ reason }) {
    const [closed, setClosed] = useState(false);
    const [error, setError] = useState(null);
    if (closed)
        return _jsx(Closed, {});
    return (_jsx("div", { className: "halted", role: "alertdialog", "aria-modal": "true", "aria-label": "SocialHub est\u00E1 parado", children: _jsxs("div", { className: "card", children: [_jsx("h3", { children: "SocialHub est\u00E1 parado" }), _jsx("p", { children: reason }), _jsx("p", { className: "src", children: "No se guarda ning\u00FAn cambio y el motor no trabaja hasta que lo vuelvas a abrir." }), error && _jsx("p", { className: "error", role: "alert", children: error }), _jsx("div", { className: "form-actions", children: _jsx("button", { className: "btn primary", onClick: () => api.shutdown().then(() => setClosed(true), e => setError(e instanceof Error ? e.message : String(e))), children: "Cerrar SocialHub" }) })] }) }));
}
/** SocialHub ya se ha cerrado: la pestaña se queda sin servidor. */
export function Closed() {
    return (_jsx("div", { className: "halted", role: "alertdialog", "aria-modal": "true", "aria-label": "SocialHub cerrado", children: _jsxs("div", { className: "card", children: [_jsx("h3", { children: "SocialHub se ha cerrado" }), _jsx("p", { children: "Puedes cerrar esta pesta\u00F1a. Para volver, \u00E1brelo con su acceso directo." })] }) }));
}
/** Botón «Cerrar» de la barra: pide confirmación en el mismo sitio. */
export function ShutdownButton({ onClosed }) {
    const [asking, setAsking] = useState(false);
    const [busy, setBusy] = useState(false);
    if (!asking)
        return _jsx("button", { className: "ibtn", onClick: () => setAsking(true), title: "Cerrar SocialHub: para el motor y el servidor", children: "Cerrar" });
    return (_jsxs("span", { className: "ask-close", role: "group", "aria-label": "\u00BFCerrar SocialHub?", children: [_jsx("span", { className: "src", children: "\u00BFCerrar SocialHub?" }), _jsx("button", { className: "ibtn", disabled: busy, onClick: () => { setBusy(true); api.shutdown().then(onClosed, () => { setBusy(false); setAsking(false); }); }, children: "S\u00ED, cerrar" }), _jsx("button", { className: "ibtn", disabled: busy, onClick: () => setAsking(false), children: "No" })] }));
}
