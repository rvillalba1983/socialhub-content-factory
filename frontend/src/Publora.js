import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { api } from './api';
import { Pill } from './ui';
const PLATFORMS = [['INSTAGRAM', 'Instagram', 'instagram'], ['TIKTOK', 'TikTok', 'tiktok']];
/**
 * Publora, segunda vía de publicación: la clave de la API (vale para todas las marcas y se guarda en un archivo de
 * este ordenador, nunca en la base de datos) y la cuenta de Publora que usa esta marca en cada red.
 */
export function PubloraCard({ brandId, onSaved }) {
    const [status, setStatus] = useState(null);
    const [chosen, setChosen] = useState({});
    const [key, setKey] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    const load = async () => {
        try {
            const [s, a] = await Promise.all([api.publora(), api.brandPubloraAccounts(brandId)]);
            setStatus(s);
            setChosen(Object.fromEntries(a.items.map((x) => [x.platform, x.connection_id])));
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
    };
    useEffect(() => { void load(); }, [brandId]);
    const run = async (action, message) => {
        setBusy(true);
        setError(null);
        try {
            await action();
            await load();
            onSaved?.(message);
            return true;
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
            return false;
        }
        finally {
            setBusy(false);
        }
    };
    const saveAccounts = (e) => {
        e.preventDefault();
        const items = PLATFORMS.filter(([p]) => chosen[p]).map(([platform]) => ({ platform, connection_id: chosen[platform] }));
        void run(() => api.saveBrandPubloraAccounts(brandId, items), 'Cuentas de Publora guardadas');
    };
    return (_jsxs("div", { className: "card form", "aria-label": "Publora", children: [_jsx("h3", { children: "Publora" }), _jsx("p", { className: "src", children: "Segunda v\u00EDa de publicaci\u00F3n, si falla Publer. La clave vale para todas las marcas y se guarda solo en este ordenador." }), !status && !error && _jsx("p", { className: "src", children: "Cargando\u2026" }), status && (_jsxs("div", { className: "status-row", children: [status.configured ? _jsx(Pill, { s: "OK", label: `Clave ${status.key_hint ?? ''}` }) : _jsx(Pill, { s: "BLOCKED", label: "Sin clave" }), status.configured && (_jsx("button", { type: "button", className: "btn", disabled: busy, onClick: () => void run(() => api.deletePubloraKey(), 'Clave de Publora borrada'), children: "Borrar clave" }))] })), status?.error && _jsx("p", { className: "error", children: status.error }), _jsxs("form", { className: "file-add", onSubmit: e => { e.preventDefault(); void run(() => api.savePubloraKey(key.trim()), 'Clave de Publora guardada').then(ok => { if (ok)
                    setKey(''); }); }, children: [_jsx("input", { type: "password", autoComplete: "off", "aria-label": "Clave de la API de Publora", placeholder: status?.configured ? 'Pegar otra clave' : 'Pega aquí la clave (Settings › API Keys)', value: key, onChange: e => setKey(e.target.value) }), _jsx("button", { className: "btn", disabled: busy || key.trim().length < 10, children: "Comprobar y guardar" })] }), status?.configured && (_jsxs("form", { onSubmit: saveAccounts, "aria-label": "Cuentas de Publora de la marca", children: [PLATFORMS.map(([platform, label, prefix]) => (_jsxs("label", { className: "field", children: [_jsxs("span", { children: ["Cuenta de ", label] }), _jsxs("select", { "aria-label": `Cuenta de Publora para ${label}`, value: chosen[platform] ?? '', onChange: e => setChosen({ ...chosen, [platform]: e.target.value || undefined }), children: [_jsxs("option", { value: "", children: ["Sin cuenta (Publora no se usa en ", label, ")"] }), status.connections.filter(c => c.platform === prefix).map(c => (_jsxs("option", { value: c.id, children: [c.label, c.usable ? '' : ' (hay que volver a conectarla en Publora)'] }, c.id)))] })] }, platform))), _jsx("div", { className: "form-actions", children: _jsx("button", { className: "btn primary", disabled: busy, children: "Guardar cuentas" }) })] })), error && _jsx("p", { className: "error", role: "alert", children: error })] }));
}
