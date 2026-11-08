import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { api } from './api';
import { FOLDER_ROLES } from './model';
import { Pill } from './ui';
/**
 * Qué carpeta de Drive hace cada papel. En la marca se fijan todos; en la promoción solo se escriben las
 * excepciones y el resto se hereda de la marca.
 */
export function FolderRoles({ owner, id, onSaved }) {
    const [folders, setFolders] = useState(null);
    const [inherited, setInherited] = useState({});
    const [values, setValues] = useState({});
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    const load = (list) => {
        setFolders(list);
        const own = {};
        const brand = {};
        list.forEach(f => {
            if (owner === 'brand' || f.origin === 'PROMOTION')
                own[f.role] = f.location;
            else
                brand[f.role] = f.location;
        });
        setValues(own);
        setInherited(brand);
    };
    useEffect(() => {
        (owner === 'brand' ? api.brandFolders(id) : api.promotionFolders(id))
            .then(r => load(r.items))
            .catch(e => setError(e instanceof Error ? e.message : String(e)));
    }, [owner, id]);
    const save = async (e) => {
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
        }
        catch (err) {
            setError(err instanceof Error ? err.message : String(err));
        }
        finally {
            setBusy(false);
        }
    };
    const byRole = new Map((folders ?? []).map(f => [f.role, f]));
    return (_jsxs("form", { className: "card form", onSubmit: save, "aria-label": "Carpetas de Drive", children: [_jsx("h3", { children: "Carpetas de Drive" }), _jsx("p", { className: "src", children: owner === 'brand'
                    ? 'Qué carpeta hace cada papel. Las promociones de la marca las heredan. SocialHub no crea ni mueve carpetas.'
                    : 'Las vacías se heredan de la marca. Escribe una ruta solo para el papel que cambie en esta promoción.' }), folders === null && !error && _jsx("p", { className: "src", children: "Cargando\u2026" }), folders !== null && FOLDER_ROLES.map(([role, label, hint]) => {
                const f = byRole.get(role);
                return (_jsxs("label", { className: "field folder-row", children: [_jsxs("span", { children: [label, " ", _jsxs("small", { className: "src", children: ["\u00B7 ", hint] })] }), _jsxs("span", { className: "folder-in", children: [_jsx("input", { "aria-label": `Carpeta de ${label}`, placeholder: inherited[role] ?? 'G:\\Mi unidad\\…', value: values[role] ?? '', onChange: e => setValues({ ...values, [role]: e.target.value }) }), f && (f.available ? _jsx(Pill, { s: "OK", label: f.origin === 'BRAND' && owner === 'promotion' ? 'De la marca' : 'Accesible' })
                                    : _jsx(Pill, { s: "BLOCKED", label: "No disponible" }))] })] }, role));
            }), _jsxs("div", { className: "form-actions", children: [error && _jsx("p", { className: "error", role: "alert", children: error }), _jsx("button", { className: "btn primary", disabled: busy || folders === null, children: busy ? 'Guardando…' : 'Guardar carpetas' })] })] }));
}
