import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './icons';
import { AI_MODELS, dateTime, } from './model';
import { Pill } from './ui';
const PAGE = 50;
const INPUT_HINT = {
    WEEKLY_SPEC: 'Opcional: un día de la semana (2026-11-09). Sin fecha, la semana que viene. Sale solo cada domingo.',
    CAPTIONS: 'Los identificadores de las publicaciones (por ejemplo, 2026-11-10-instagram). También desde cada publicación.',
    WEEKLY_BALANCE: 'Opcional: un día de la semana. Sin fecha, la semana pasada. Sale solo cada lunes.',
    RESEARCH: 'Lo que Claude deba tener en cuenta: lugares, nombres, enlaces…',
    QUALITY_REVIEW: 'El identificador de una publicación. También desde la propia publicación.',
    WEEKLY_PROPOSALS: 'Opcional: un día de la semana. Sin fecha, la que viene. Sale solo los lunes a las 18:00.',
    PUBLER_SCHEDULE: 'Lo encarga SocialHub al pasar una publicación a «Programada».',
    PUBLISH_CHECK: 'Lo encarga SocialHub pasada la hora de publicación.',
    CALENDAR_READ: 'Lo encarga SocialHub cada día, desde las 7:00.',
    METRICS_READ: 'Lo encarga SocialHub a los 7 días de cada publicación.',
};
const REQUEST_STATUS = {
    SENT: { label: 'Esperando a Claude', s: 'WAITING' },
    COMPLETED: { label: 'Completado', s: 'OK' },
    FAILED: { label: 'Error', s: 'FAILED' },
    NEEDS_HUMAN: { label: 'Te pregunta algo', s: 'WAITING' },
    INVALID: { label: 'Resultado no válido', s: 'FAILED' },
};
const duration = (ms) => {
    if (ms == null)
        return '—';
    const s = Math.round(ms / 1000);
    return s < 60 ? `${s} s` : `${Math.floor(s / 60)} min ${s % 60} s`;
};
/**
 * Encargos a Claude: ajustes (límite diario y modelo de cada tipo), alta de un encargo y registro de envíos.
 * SocialHub no llama a la API de Claude: deja cada encargo en la carpeta de «Encargos» de la marca.
 */
export function Claude({ data, onClose, onTask, onMessage }) {
    const [settings, setSettings] = useState(null);
    const [page, setPage] = useState(null);
    const [offset, setOffset] = useState(0);
    const [error, setError] = useState(null);
    const load = useCallback(async () => {
        try {
            const [s, p] = await Promise.all([api.aiSettings(), api.aiRequests(offset, PAGE)]);
            setSettings(s);
            setPage(p);
            setError(null);
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
    }, [offset]);
    useEffect(() => { void load(); }, [load]);
    const labels = new Map(settings?.models.map(m => [m.type, m.label]) ?? []);
    return (_jsx("section", { className: "cc", "aria-label": "Encargos a Claude", children: _jsxs("div", { className: "cc-in", children: [_jsx("div", { children: _jsxs("button", { className: "btn back", onClick: onClose, children: [_jsx(Icon, { name: "back" }), "Volver al mapa"] }) }), _jsxs("div", { className: "cc-head", children: [_jsxs("div", { children: [_jsx("div", { className: "eyebrow", children: "Orquestador de IA" }), _jsx("h1", { children: "Encargos a Claude" }), _jsx("p", { children: "SocialHub deja cada encargo en \u00ABEncargos/para-claude\u00BB de la marca. La tarea programada \u00ABEncargos de SocialHub\u00BB lo hace y deja el resultado en \u00ABde-claude\u00BB." })] }), _jsx("button", { className: "btn", onClick: load, children: "Actualizar" })] }), error && _jsx("p", { className: "error", role: "alert", children: error }), _jsxs("div", { className: "grid2", children: [settings && _jsx(SettingsCard, { settings: settings, onSaved: s => { setSettings(s); onMessage('Ajustes de Claude guardados'); } }), settings && _jsx(OrderCard, { data: data, settings: settings, onOrdered: id => { onMessage('Encargo en cola'); void load(); onTask(id); } })] }), _jsx("h3", { children: "Registro de env\u00EDos" }), _jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Enviado" }), _jsx("th", { children: "Tipo" }), _jsx("th", { children: "Modelo" }), _jsx("th", { children: "Intento" }), _jsx("th", { children: "Estado" }), _jsx("th", { children: "Duraci\u00F3n" }), _jsx("th", { children: "Tokens" }), _jsx("th", { children: "Coste" }), _jsx("th", { children: "Detalle" })] }) }), _jsxs("tbody", { children: [page?.items.map(r => {
                                        const st = REQUEST_STATUS[r.status];
                                        return (_jsxs("tr", { children: [_jsx("td", { className: "nowrap mono", children: dateTime(r.sent_at) }), _jsx("td", { children: labels.get(r.type) ?? r.type }), _jsxs("td", { children: ["Claude ", AI_MODELS.find(m => m.id === r.model)?.label] }), _jsx("td", { children: r.attempt }), _jsx("td", { children: _jsx(Pill, { s: st.s, label: st.label }) }), _jsx("td", { className: "nowrap", children: duration(r.duration_ms) }), _jsx("td", { className: "src", children: "desconocido" }), _jsx("td", { className: "src", children: "desconocido" }), _jsxs("td", { children: [r.error && _jsxs("span", { className: "error-text", children: [r.error, " "] }), _jsx("button", { className: "btn", style: { padding: '3px 8px' }, onClick: () => onTask(r.task_id), children: "Ver tarea" })] })] }, r.id));
                                    }), page && page.items.length === 0 && _jsx("tr", { children: _jsx("td", { colSpan: 9, children: "Todav\u00EDa no se ha enviado ning\u00FAn encargo." }) })] })] }) }), _jsx("p", { className: "src", children: "Tokens y coste figuran como desconocidos: Claude Pro no los da. El l\u00EDmite diario cuenta los env\u00EDos desde las 0:00 (hora de Espa\u00F1a)." }), page && page.total > PAGE && (_jsxs("div", { className: "cal-nav", children: [_jsx("button", { className: "btn", disabled: offset === 0, onClick: () => setOffset(Math.max(0, offset - PAGE)), children: "Anteriores" }), _jsxs("span", { className: "src", children: [offset + 1, "\u2013", Math.min(offset + PAGE, page.total), " de ", page.total] }), _jsx("button", { className: "btn", disabled: offset + PAGE >= page.total, onClick: () => setOffset(offset + PAGE), children: "Siguientes" })] }))] }) }));
}
function SettingsCard({ settings, onSaved }) {
    const [limit, setLimit] = useState(settings.daily_limit);
    const [models, setModels] = useState(settings.models);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    const save = async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
            onSaved(await api.saveAiSettings({ daily_limit: limit, models: models.map(({ type, model }) => ({ type, model })) }));
        }
        catch (err) {
            setError(err instanceof Error ? err.message : String(err));
        }
        finally {
            setBusy(false);
        }
    };
    return (_jsxs("form", { className: "card form", onSubmit: save, "aria-label": "Ajustes de Claude", children: [_jsx("h3", { children: "Ajustes" }), _jsxs("label", { className: "field", children: [_jsxs("span", { children: ["Encargos al d\u00EDa (hoy: ", settings.used_today, ")"] }), _jsx("input", { type: "number", min: 0, max: 100, required: true, value: limit, onChange: e => setLimit(Number(e.target.value)) })] }), models.map(m => (_jsxs("label", { className: "field", children: [_jsxs("span", { children: ["Modelo para \u00AB", m.label, "\u00BB", m.on_pc && _jsx("small", { className: "src", children: " \u00B7 con Chrome en tu PC; no cuenta en el l\u00EDmite" })] }), _jsx("select", { value: m.model, onChange: e => setModels(models.map(x => (x.type === m.type ? { ...x, model: e.target.value } : x))), children: AI_MODELS.map(a => _jsx("option", { value: a.id, children: a.label }, a.id)) })] }, m.type))), _jsx("p", { className: "src", children: "Si un encargo falla dos veces, el tercer intento sube un nivel (Sonnet \u2192 Opus)." }), _jsxs("div", { className: "form-actions", children: [error && _jsx("p", { className: "error", role: "alert", children: error }), _jsx("button", { className: "btn primary", disabled: busy, children: busy ? 'Guardando…' : 'Guardar ajustes' })] })] }));
}
function OrderCard({ data, settings, onOrdered }) {
    const [promotion, setPromotion] = useState(data.promotions[0]?.id ?? '');
    const [type, setType] = useState('RESEARCH');
    const [title, setTitle] = useState('');
    const [goal, setGoal] = useState('');
    const [inputs, setInputs] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    if (data.promotions.length === 0) {
        return _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Nuevo encargo" }), _jsx("p", { children: "Primero da de alta una promoci\u00F3n." })] });
    }
    const submit = async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
            const r = await api.orderFromClaude(promotion, {
                type, title: title.trim() || undefined, goal: goal.trim(), inputs: inputs.split('\n').map(x => x.trim()).filter(Boolean),
            });
            setGoal('');
            setTitle('');
            setInputs('');
            onOrdered(r.task_id);
        }
        catch (err) {
            setError(err instanceof Error ? err.message : String(err));
        }
        finally {
            setBusy(false);
        }
    };
    return (_jsxs("form", { className: "card form", onSubmit: submit, "aria-label": "Nuevo encargo a Claude", children: [_jsx("h3", { children: "Nuevo encargo" }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Promoci\u00F3n" }), _jsx("select", { value: promotion, onChange: e => setPromotion(e.target.value), children: data.promotions.map(p => _jsx("option", { value: p.id, children: p.name }, p.id)) })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Tipo" }), _jsx("select", { value: type, onChange: e => setType(e.target.value), children: settings.models.filter(m => !m.on_pc).map(m => _jsxs("option", { value: m.type, children: [m.label, " \u00B7 ", AI_MODELS.find(a => a.id === m.model)?.label] }, m.type)) })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "T\u00EDtulo (opcional)" }), _jsx("input", { maxLength: 120, value: title, onChange: e => setTitle(e.target.value) })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Qu\u00E9 tiene que hacer" }), _jsx("textarea", { required: true, rows: 3, maxLength: 4000, value: goal, onChange: e => setGoal(e.target.value) })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Datos de entrada (uno por l\u00EDnea)" }), _jsx("textarea", { rows: 3, value: inputs, onChange: e => setInputs(e.target.value) })] }), _jsx("p", { className: "src", children: INPUT_HINT[type] }), _jsxs("div", { className: "form-actions", children: [error && _jsx("p", { className: "error", role: "alert", children: error }), _jsx("button", { className: "btn primary", disabled: busy || !goal.trim(), children: busy ? 'Encargando…' : 'Encargar a Claude' })] })] }));
}
