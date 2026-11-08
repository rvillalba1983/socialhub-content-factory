import { Fragment as _Fragment, jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import { dateTime } from './model';
import { Pill } from './ui';
const PAGE = 50;
const SYSTEMS = { postgres: 'PostgreSQL', drive: 'Google Drive', publer: 'Publer', tiktok: 'TikTok', instagram: 'Instagram' };
const systemLabel = (s) => SYSTEMS[s] ?? s;
/**
 * Registro de las operaciones con sistemas externos (PostgreSQL, Drive, Publer, APIs…): qué se hizo, cuánto tardó
 * y, si falló, por qué y con qué identificador de traza buscarlo en los registros.
 */
export function ExternalOperations({ onClose }) {
    const [outcome, setOutcome] = useState(undefined);
    const [system, setSystem] = useState('');
    const [offset, setOffset] = useState(0);
    const [page, setPage] = useState(null);
    const [systems, setSystems] = useState([]);
    const [error, setError] = useState(null);
    const [open, setOpen] = useState(null);
    const load = useCallback(async () => {
        try {
            const result = await api.externalOperations({ system: system || undefined, outcome, offset, limit: PAGE });
            setPage(result);
            setSystems(prev => [...new Set([...prev, ...result.items.map(o => o.system)])].sort());
            setError(null);
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
    }, [system, outcome, offset]);
    useEffect(() => { load(); }, [load]);
    const filter = (o) => { setOutcome(o); setOffset(0); };
    return (_jsx("section", { className: "cc", "aria-label": "Operaciones externas", children: _jsxs("div", { className: "cc-in", children: [_jsx("div", { children: _jsxs("button", { className: "btn back", onClick: onClose, children: [_jsx(Icon, { name: "back" }), "Volver al mapa"] }) }), _jsxs("div", { className: "cc-head", children: [_jsxs("div", { children: [_jsx("div", { className: "eyebrow", children: "Observabilidad" }), _jsx("h1", { children: "Operaciones externas" }), _jsx("p", { children: "Cada llamada de SocialHub a un sistema externo, con su resultado. Se guardan 30 d\u00EDas." })] }), _jsx("button", { className: "btn", onClick: load, children: "Actualizar" })] }), _jsxs("div", { className: "filters ops-filters", children: [[[undefined, 'Todas'], ['FAILURE', 'Fallidas'], ['SUCCESS', 'Correctas']].map(([o, l]) => (_jsx("button", { className: `chip${outcome === o ? ' on' : ''}`, onClick: () => filter(o), children: l }, l))), _jsx("label", { className: "src", htmlFor: "ops-system", children: "Sistema" }), _jsxs("select", { id: "ops-system", className: "agent-sel", value: system, onChange: e => { setSystem(e.target.value); setOffset(0); }, children: [_jsx("option", { value: "", children: "Todos" }), systems.map(s => _jsx("option", { value: s, children: systemLabel(s) }, s))] })] }), error && _jsxs("p", { className: "error", role: "alert", children: ["No se pudo cargar el registro: ", error] }), _jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Cu\u00E1ndo" }), _jsx("th", { children: "Sistema" }), _jsx("th", { children: "Operaci\u00F3n" }), _jsx("th", { children: "Resultado" }), _jsx("th", { children: "Duraci\u00F3n" }), _jsx("th", { children: "Detalle" })] }) }), _jsxs("tbody", { children: [page?.items.map(o => (_jsx(Row, { o: o, open: open === o.id, onToggle: () => setOpen(open === o.id ? null : o.id) }, o.id))), page && page.items.length === 0 && _jsx("tr", { children: _jsx("td", { colSpan: 6, children: "No hay operaciones con este filtro." }) }), !page && !error && _jsx("tr", { children: _jsx("td", { colSpan: 6, children: "Cargando\u2026" }) })] })] }) }), page && page.total > PAGE && (_jsxs("div", { className: "cal-nav", children: [_jsx("button", { className: "btn", disabled: offset === 0, onClick: () => setOffset(Math.max(0, offset - PAGE)), children: "Anteriores" }), _jsxs("span", { className: "src", children: [offset + 1, "\u2013", Math.min(offset + PAGE, page.total), " de ", page.total] }), _jsx("button", { className: "btn", disabled: offset + PAGE >= page.total, onClick: () => setOffset(offset + PAGE), children: "Siguientes" })] }))] }) }));
}
function Row({ o, open, onToggle }) {
    const failed = o.outcome === 'FAILURE';
    return (_jsxs(_Fragment, { children: [_jsxs("tr", { className: failed ? 'op-fail' : '', children: [_jsx("td", { className: "nowrap mono", children: dateTime(o.started_at) }), _jsx("td", { children: systemLabel(o.system) }), _jsx("td", { className: "mono", children: o.operation }), _jsx("td", { children: _jsx(Pill, { s: failed ? 'FAILED' : 'OK', label: failed ? 'Fallida' : 'Correcta' }) }), _jsxs("td", { className: "nowrap mono", children: [o.duration_ms, " ms"] }), _jsx("td", { children: (failed || o.detail || o.trace_id)
                            ? _jsx("button", { className: "btn", style: { padding: '3px 8px' }, "aria-expanded": open, onClick: onToggle, children: open ? 'Ocultar' : 'Ver' })
                            : '—' })] }), open && (_jsx("tr", { className: "op-detail", children: _jsx("td", { colSpan: 6, children: _jsxs("dl", { className: "kv", children: [o.error_message && _jsxs(_Fragment, { children: [_jsx("dt", { children: "Error" }), _jsx("dd", { style: { color: 'var(--err)' }, children: o.error_message })] }), o.error_type && _jsxs(_Fragment, { children: [_jsx("dt", { children: "Tipo" }), _jsx("dd", { className: "mono", children: o.error_type })] }), o.detail && _jsxs(_Fragment, { children: [_jsx("dt", { children: "Detalle" }), _jsx("dd", { className: "mono", children: o.detail })] }), o.trace_id && _jsxs(_Fragment, { children: [_jsx("dt", { children: "Traza" }), _jsxs("dd", { className: "mono", children: [o.trace_id, " ", _jsx("span", { className: "src", children: "(b\u00FAscala en los registros de SocialHub)" })] })] })] }) }) }))] }));
}
