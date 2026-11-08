import { Fragment as _Fragment, jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import { REJECTION_REASONS, dateTime } from './model';
const reasonLabel = (r) => REJECTION_REASONS.find(([k]) => k === r)?.[1] ?? r;
/** Memoria editorial de la marca: decisiones (editables), rechazos y correcciones. Va en cada encargo a Claude. */
export function MemoryCard({ brandId, onSaved }) {
    const [memory, setMemory] = useState(null);
    const [text, setText] = useState('');
    const [error, setError] = useState(null);
    const load = useCallback(() => api.editorialMemory(brandId).then(setMemory)
        .catch(e => setError(e instanceof Error ? e.message : String(e))), [brandId]);
    useEffect(() => { void load(); }, [load]);
    const act = async (action, message) => {
        setError(null);
        try {
            await action();
            await load();
            onSaved?.(message);
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
    };
    return (_jsxs("div", { className: "card form", "aria-label": "Memoria editorial", children: [_jsx("h3", { children: "Memoria editorial" }), _jsx("p", { className: "src", children: "Va en cada encargo a Claude: las decisiones, los \u00FAltimos rechazos y correcciones, y lo publicado en las \u00FAltimas 8 semanas." }), _jsx("h4", { children: "Decisiones editoriales" }), memory?.decisions.length === 0 && _jsx("p", { className: "src", children: "Ninguna todav\u00EDa." }), memory?.decisions.map(d => (_jsxs("div", { className: "file-row", children: [_jsx("span", { children: d.text }), _jsx("small", { className: "src nowrap", children: dateTime(d.created_at) }), _jsx("span", {}), _jsx("button", { type: "button", className: "ibtn", "aria-label": `Borrar «${d.text}»`, title: "Borrar", onClick: () => void act(() => api.deleteDecision(brandId, d.id), 'Decisión borrada'), children: _jsx(Icon, { name: "x" }) })] }, d.id))), _jsxs("form", { className: "file-add", onSubmit: e => { e.preventDefault(); void act(() => api.addDecision(brandId, text.trim()), 'Decisión añadida').then(() => setText('')); }, children: [_jsx("input", { "aria-label": "Nueva decisi\u00F3n", placeholder: "Por ejemplo: m\u00E1ximo dos reels por semana", maxLength: 1000, value: text, onChange: e => setText(e.target.value) }), _jsx("button", { className: "btn", disabled: !text.trim(), children: "A\u00F1adir" })] }), _jsx("h4", { children: "Rechazos recientes" }), memory?.rejections.length === 0 && _jsx("p", { className: "src", children: "Ninguno." }), _jsx("ul", { className: "memory-list", children: memory?.rejections.map((r, i) => (_jsxs("li", { children: [_jsx("b", { children: r.kind === 'TEXT' ? 'Texto descartado' : 'Publicación cancelada' }), " \u00B7 ", r.title, " \u00B7 ", reasonLabel(r.reason), r.note && _jsxs(_Fragment, { children: [" \u00B7 \u00AB", r.note, "\u00BB"] }), " ", _jsx("small", { className: "src", children: dateTime(r.created_at) })] }, i))) }), _jsx("h4", { children: "Correcciones a textos de Claude" }), memory?.corrections.length === 0 && _jsx("p", { className: "src", children: "Ninguna." }), _jsx("ul", { className: "memory-list", children: memory?.corrections.map((c, i) => (_jsxs("li", { children: [_jsx("span", { className: "src", children: "Claude:" }), " ", c.before_caption ?? '—', _jsx("br", {}), _jsx("span", { className: "src", children: "T\u00FA:" }), " ", c.after_caption ?? '—', ' ', _jsx("small", { className: "src", children: dateTime(c.created_at) })] }, i))) }), error && _jsx("p", { className: "error", role: "alert", children: error })] }));
}
