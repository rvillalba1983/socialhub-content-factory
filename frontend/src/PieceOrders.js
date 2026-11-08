import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { dateTime } from './model';
/**
 * Encargar piezas (lo que hacía promo.py) a partir de un spec.json. Se listan los de la carpeta vigilada de la
 * promoción («SocialHub/encargos» dentro de su primera carpeta); también se puede indicar otra ruta de sus carpetas.
 */
export function PieceOrders({ promotions, onOrdered, onTask }) {
    const withFolder = promotions.filter(p => p.resources.some(r => r.kind === 'DRIVE'));
    const [promo, setPromo] = useState(withFolder[0]?.id ?? '');
    const [list, setList] = useState(null);
    const [path, setPath] = useState('');
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);
    const load = useCallback(async () => {
        if (!promo)
            return;
        try {
            setList(await api.pieceSpecs(promo));
            setError(null);
        }
        catch (e) {
            setList(null);
            setError(e instanceof Error ? e.message : String(e));
        }
    }, [promo]);
    useEffect(() => { load(); }, [load]);
    const order = async (specPath) => {
        setBusy(true);
        setError(null);
        try {
            const o = await api.orderPieces(promo, specPath);
            onOrdered(`Piezas encargadas · ${specPath.split(/[\\/]/).pop()}`);
            setPath('');
            await load();
            onTask(o.task_id);
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
        finally {
            setBusy(false);
        }
    };
    if (withFolder.length === 0) {
        return _jsx("p", { className: "src", children: "Para encargar piezas, a\u00F1ade a una promoci\u00F3n una carpeta en \u00ABRecursos\u00BB (Marcas \u2192 editar promoci\u00F3n)." });
    }
    return (_jsxs("div", { className: "pieces", children: [_jsxs("label", { className: "field", children: [_jsx("span", { children: "Promoci\u00F3n" }), _jsx("select", { value: promo, onChange: e => setPromo(e.target.value), children: withFolder.map(p => _jsx("option", { value: p.id, children: p.name }, p.id)) })] }), list?.folder && _jsxs("p", { className: "src", children: ["Carpeta vigilada: ", _jsx("span", { className: "mono", children: list.folder }), ". Los spec.json nuevos que aparezcan ah\u00ED se encargan solos."] }), list && list.items.length === 0 && _jsx("p", { className: "src", children: "No hay spec.json en la carpeta vigilada." }), list?.items.map(s => (_jsxs("div", { className: "qrow spec-row", children: [_jsxs("span", { children: [s.name, _jsx("small", { children: dateTime(s.modified_at) })] }), s.task_id
                        ? _jsx("button", { className: "btn", onClick: () => onTask(s.task_id), children: "Ver tarea" })
                        : _jsx("button", { className: "btn primary", disabled: busy, onClick: () => order(s.path), children: "Encargar" })] }, s.path))), _jsxs("form", { className: "spec-path", onSubmit: e => { e.preventDefault(); if (path.trim())
                    order(path.trim()); }, children: [_jsxs("label", { className: "field", children: [_jsx("span", { children: "Otro spec.json (ruta completa, dentro de las carpetas de la promoci\u00F3n)" }), _jsx("input", { value: path, placeholder: "G:\\Mi unidad\\\u2026\\spec.json", onChange: e => setPath(e.target.value) })] }), _jsx("button", { className: "btn", disabled: busy || !path.trim(), children: "Encargar" })] }), error && _jsx("p", { className: "error", role: "alert", children: error })] }));
}
