import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { api } from './api';
const lines = (v) => v.join('\n');
const parse = (t) => t.split('\n').map(x => x.trim()).filter(Boolean);
/** Identidad de la marca: la heredan sus promociones, va en cada encargo a Claude y la usa el control de calidad. */
export function IdentityForm({ brandId, onSaved }) {
    const [identity, setIdentity] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    useEffect(() => {
        api.brandIdentity(brandId).then(setIdentity).catch(e => setError(e instanceof Error ? e.message : String(e)));
    }, [brandId]);
    if (!identity)
        return _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Identidad de la marca" }), error ? _jsx("p", { className: "error", role: "alert", children: error }) : _jsx("p", { className: "src", children: "Cargando\u2026" })] });
    const set = (k, v) => setIdentity({ ...identity, [k]: v });
    const text = (k, label, rows = 1, max = 2000) => (_jsxs("label", { className: "field", children: [_jsx("span", { children: label }), rows > 1 ? _jsx("textarea", { rows: rows, maxLength: max, value: identity[k], onChange: e => set(k, e.target.value) })
                : _jsx("input", { maxLength: max, value: identity[k], onChange: e => set(k, e.target.value) })] }));
    const list = (k, label) => (_jsx(ListField, { label: label, value: identity[k], onChange: v => set(k, v) }));
    const save = async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
            setIdentity(await api.saveBrandIdentity(brandId, identity));
            onSaved?.('Identidad de la marca guardada');
        }
        catch (err) {
            setError(err instanceof Error ? err.message : String(err));
        }
        finally {
            setBusy(false);
        }
    };
    return (_jsxs("form", { className: "card form", onSubmit: save, "aria-label": "Identidad de la marca", children: [_jsx("h3", { children: "Identidad de la marca" }), _jsx("p", { className: "src", children: "La heredan sus promociones. Va en cada encargo a Claude y el control de calidad comprueba las expresiones prohibidas, la llamada a la acci\u00F3n y los hashtags fijos." }), _jsxs("div", { className: "form-grid", children: [text('tone', 'Tono de comunicación', 3), text('audience', 'Público objetivo', 3), list('forbidden_topics', 'Temas que no se tocan'), list('preferred_expressions', 'Expresiones preferidas'), list('forbidden_expressions', 'Expresiones prohibidas'), list('colors', 'Colores'), text('font', 'Tipografía', 1, 200), text('logo', 'Logotipo (ruta o descripción)', 1, 500), text('visual_style', 'Estilo visual', 3), text('photo_style', 'Estilo fotográfico', 3)] }), text('call_to_action', 'Llamada a la acción (al final de los textos, salvo historias)', 1, 500), text('fixed_hashtags', 'Hashtags fijos', 1, 1000), _jsxs("div", { className: "form-actions", children: [error && _jsx("p", { className: "error", role: "alert", children: error }), _jsx("button", { className: "btn primary", disabled: busy, children: busy ? 'Guardando…' : 'Guardar identidad' })] })] }));
}
function ListField({ label, value, onChange }) {
    const [text, setText] = useState(lines(value));
    return (_jsxs("label", { className: "field", children: [_jsxs("span", { children: [label, " (una por l\u00EDnea)"] }), _jsx("textarea", { rows: 3, value: text, onChange: e => { setText(e.target.value); onChange(parse(e.target.value)); } })] }));
}
