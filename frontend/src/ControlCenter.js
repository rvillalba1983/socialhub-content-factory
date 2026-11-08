import { Fragment as _Fragment, jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useMemo, useRef, useState } from 'react';
import { Icon } from './icons';
import { PROMOTION_STATUS, PUBLICATION_STATUS, STATUS, addDays, dateTime, dayLabel, hhmm, isoDay, parseDay, shortDate, statusClass, weekday, } from './model';
import { AgentChip, Dot, Pill } from './ui';
import { FolderRoles } from './Folders';
import { PublicationEditor, ReasonForm } from './PublicationEditor';
import { api } from './api';
const TABS = [
    ['resumen', 'Resumen'], ['calendario', 'Calendario'], ['publicaciones', 'Publicaciones'], ['tareas', 'Tareas'],
    ['recursos', 'Recursos'], ['automatizacion', 'Automatización'], ['metricas', 'Métricas'], ['config', 'Configuración'],
];
const FILTERS = [
    ['all', 'Todas'], ['PROPOSAL', 'Propuestas'], ['pending', 'Pendientes'], ['IN_REVIEW', 'En revisión'], ['SCHEDULED', 'Programadas'],
    ['PUBLISHED', 'Publicadas'], ['issues', 'Con incidencia'],
];
export function ControlCenter({ p, brand, data, initialTab, highlight, onClose, onTask, onEdit, onMessage }) {
    /** Publicación abierta en el editor: su id, «new» para una nueva o null. */
    const [editing, setEditing] = useState(null);
    /** Propuesta que se está rechazando (pide el motivo). */
    const [rejecting, setRejecting] = useState(null);
    const [actionError, setActionError] = useState(null);
    const act = async (action, message) => {
        setActionError(null);
        try {
            await action();
            onMessage?.(message);
        }
        catch (e) {
            setActionError(e instanceof Error ? e.message : String(e));
        }
    };
    const [tab, setTab] = useState(initialTab);
    const [filter, setFilter] = useState('all');
    const [hl, setHl] = useState(highlight);
    const today = data.now.slice(0, 10);
    const pubs = data.publications.filter(x => x.promotion_id === p.id);
    const weeks = useMemo(() => {
        const start = parseDay(p.starts_on), end = parseDay(p.ends_on);
        const first = addDays(start, -((start.getUTCDay() + 6) % 7));
        const out = [];
        for (let d = first; d <= end; d = addDays(d, 7))
            out.push(d);
        return out;
    }, [p.starts_on, p.ends_on]);
    const [week, setWeek] = useState(() => {
        const t = parseDay(today).getTime();
        const i = weeks.findIndex(w => t >= w.getTime() && t < addDays(w, 7).getTime());
        return i < 0 ? 0 : i;
    });
    /** Lo programado o publicado fuera de SocialHub en la semana que se ve (Publora, Publer y la red). */
    const [outside, setOutside] = useState(null);
    useEffect(() => {
        if (tab !== 'calendario' || weeks.length === 0)
            return;
        const w = weeks[Math.min(week, weeks.length - 1)];
        let live = true;
        api.externalCalendar(p.id, isoDay(w), isoDay(addDays(w, 6)))
            .then(c => { if (live)
            setOutside(c); }).catch(() => { if (live)
            setOutside(null); });
        return () => { live = false; };
    }, [tab, week, weeks, p.id]);
    const ref = useRef(null);
    useEffect(() => { ref.current?.scrollTo?.({ top: 0 }); }, [p.id]);
    useEffect(() => {
        if (hl && tab === 'publicaciones')
            document.getElementById(`row-${hl}`)?.scrollIntoView?.({ block: 'center' });
    }, [hl, tab]);
    const jump = (id) => { setTab('publicaciones'); setFilter('all'); setHl(id); };
    const freeDays = p.milestones.find(m => m.ends_on && /gratis/i.test(m.label));
    return (_jsx("section", { className: "cc", ref: ref, children: _jsxs("div", { className: "cc-in", style: { ['--pc']: brand?.color }, children: [_jsx("div", { children: _jsxs("button", { className: "btn back", onClick: onClose, children: [_jsx(Icon, { name: "back" }), "Volver al mapa"] }) }), _jsxs("div", { className: "cc-head", children: [_jsxs("div", { children: [_jsxs("div", { className: "eyebrow", children: [brand?.name, " \u00B7 ", brand?.handle] }), _jsx("h1", { children: p.name }), _jsxs("p", { children: [shortDate(p.starts_on), " \u2013 ", shortDate(p.ends_on), " \u00B7 ", p.cadence] }), _jsx("p", { children: p.description })] }), _jsxs("div", { className: "promo-row", children: [_jsx(Pill, { s: PROMOTION_STATUS[p.status].s, label: PROMOTION_STATUS[p.status].label }), _jsxs("span", { className: "pill s-wait", children: [_jsx(Icon, { name: "human" }), _jsx("span", { children: "Supervisada" })] }), _jsx("button", { className: "btn", onClick: onEdit, children: "Editar" })] })] }), _jsx("nav", { className: "tabs", role: "tablist", children: TABS.map(([k, l]) => _jsx("button", { role: "tab", "aria-selected": tab === k, className: tab === k ? 'on' : '', onClick: () => { setTab(k); setHl(null); }, children: l }, k)) }), tab === 'resumen' && (() => {
                    const tasks = data.tasks.filter(t => t.promotion_id === p.id);
                    const incidents = tasks.filter(t => t.status === 'FAILED' || t.status === 'BLOCKED');
                    const next = pubs.filter(x => x.scheduled_on >= today && x.status !== 'PUBLISHED' && x.status !== 'CANCELLED').slice(0, 4);
                    return (_jsxs("div", { className: "grid2", children: [_jsxs("div", { className: "card", children: [_jsx("h3", { children: "Objetivos" }), _jsx("ul", { children: p.goals.map((g, i) => _jsx("li", { children: g }, i)) }), _jsx("p", { className: "src", children: p.scope })] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Hitos" }), _jsx("ul", { className: "ms-list", children: p.milestones.map((m, i) => (_jsxs("li", { children: [_jsxs("time", { children: [shortDate(m.starts_on), m.ends_on ? `–${shortDate(m.ends_on)}` : ''] }), _jsx("span", { children: m.label })] }, i))) })] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Pr\u00F3ximas publicaciones" }), next.length ? next.map(x => _jsx(PubEvent, { x: x, onClick: () => jump(x.id) }, x.id)) : _jsx("p", { children: "Nada pendiente." })] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Incidencias" }), incidents.length ? incidents.map(t => (_jsxs("button", { className: "qrow", onClick: () => onTask(t.id), children: [_jsx(Dot, { s: t.status }), _jsxs("span", { children: [t.title, _jsx("small", { children: t.error ?? t.reason })] })] }, t.id))) : _jsx("p", { children: "Ninguna." })] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Esperan a Ra\u00FAl" }), _jsx("ul", { children: p.human_todos.map((h, i) => _jsx("li", { children: h }, i)) }), _jsx("p", { className: "src", children: "Claude prepara, programa y avisa. Ra\u00FAl solo revisa y pulsa enviar." })] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Reglas de la promoci\u00F3n" }), _jsx("ul", { children: [...p.rules, ...data.common_rules].map((r, i) => _jsx("li", { children: r }, i)) })] })] }));
                })(), tab === 'calendario' && weeks.length > 0 && (() => {
                    const w = weeks[Math.min(week, weeks.length - 1)];
                    const days = [...Array(7)].map((_, i) => addDays(w, i));
                    return (_jsxs(_Fragment, { children: [_jsxs("div", { className: "cal-nav", children: [_jsx("button", { className: "btn", disabled: week === 0, onClick: () => setWeek(week - 1), children: "Semana anterior" }), _jsxs("b", { children: [dayLabel(days[0]), " \u2013 ", dayLabel(days[6])] }), _jsx("button", { className: "btn", disabled: week >= weeks.length - 1, onClick: () => setWeek(week + 1), children: "Semana siguiente" }), _jsxs("span", { className: "src", children: [p.cadence, " \u00B7 Europe/Madrid"] })] }), outside && _jsx(OutsideSources, { sources: outside.sources }), _jsx("div", { className: "cal", children: days.map(d => {
                                    const iso = isoDay(d);
                                    const ev = pubs.filter(x => x.scheduled_on === iso);
                                    const ext = (outside?.items ?? []).filter(x => !x.publication_id && madridDay(x.at) === iso);
                                    const free = freeDays && iso >= freeDays.starts_on && iso <= (freeDays.ends_on ?? freeDays.starts_on);
                                    return (_jsxs("div", { className: `day${iso === today ? ' today' : ''}`, children: [_jsxs("header", { children: [_jsxs("span", { children: [weekday(d), iso === today ? ' · hoy' : ''] }), _jsx("b", { children: d.getUTCDate() })] }), free && _jsx("span", { className: "pill s-ok", style: { justifySelf: 'start' }, children: "D\u00EDas gratis" }), ev.length ? ev.map(x => _jsx(PubEvent, { x: x, onClick: () => jump(x.id) }, x.id)) : !ext.length && _jsx("span", { className: "empty", children: "Sin publicaciones" }), ext.map((x, i) => _jsx(OutsideEvent, { x: x }, `${x.source}-${x.at}-${i}`))] }, iso));
                                }) })] }));
                })(), tab === 'publicaciones' && (() => {
                    const list = pubs.filter(x => filter === 'all'
                        || (filter === 'pending' && ['IDEA', 'PROPOSAL', 'IN_PRODUCTION', 'DRAFT'].includes(x.status))
                        || (filter === 'issues' && (x.flag || x.status === 'PUBLISH_ERROR'))
                        || x.status === filter);
                    return (_jsxs(_Fragment, { children: [_jsxs("div", { className: "row-between", children: [_jsx("div", { className: "filters", children: FILTERS.map(([k, l]) => _jsx("button", { className: `chip${filter === k ? ' on' : ''}`, onClick: () => setFilter(k), children: l }, k)) }), _jsxs("span", { children: [_jsx("button", { className: "btn", onClick: () => void act(() => api.orderFromClaude(p.id, {
                                                    type: 'WEEKLY_PROPOSALS', goal: `Propón las publicaciones de la semana que viene para «${p.name}», según su cadencia, objetivos, hitos y memoria editorial.`,
                                                }), 'Propuestas pedidas a Claude: llegarán como «Propuesta»'), children: "Pedir propuestas a Claude" }), ' ', _jsx("button", { className: "btn primary", onClick: () => setEditing('new'), children: "Nueva publicaci\u00F3n" })] })] }), actionError && _jsx("p", { className: "error", role: "alert", children: actionError }), rejecting && (_jsx(ReasonForm, { title: `Por qué rechazas «${pubs.find(x => x.id === rejecting)?.title ?? ''}»`, action: "Rechazar propuesta", onCancel: () => setRejecting(null), onSubmit: (reason, note) => {
                                    const id = rejecting;
                                    setRejecting(null);
                                    void act(() => api.changePublicationStatus(id, 'CANCELLED', reason, note), 'Propuesta rechazada');
                                } })), editing && (_jsx(PublicationEditor, { promotionId: p.id, publication: editing === 'new' ? undefined : pubs.find(x => x.id === editing), onClose: () => setEditing(null), onSaved: (saved, message) => { setEditing(saved.id); setHl(saved.id); onMessage?.(message); } }, editing)), _jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Fecha" }), _jsx("th", { children: "Plataforma" }), _jsx("th", { children: "Tipo" }), _jsx("th", { children: "Pieza" }), _jsx("th", { children: "Estado" }), _jsx("th", { children: "V\u00EDa" }), _jsx("th", { children: "Observaciones" }), _jsx("th", {})] }) }), _jsxs("tbody", { children: [list.map(x => {
                                                    const st = PUBLICATION_STATUS[x.status];
                                                    return (_jsxs("tr", { id: `row-${x.id}`, className: hl === x.id ? 'hl' : '', children: [_jsxs("td", { className: "nowrap mono", children: [dayLabel(parseDay(x.scheduled_on)), " ", hhmm(x.scheduled_time)] }), _jsx("td", { children: x.platform === 'TIKTOK' ? 'TikTok' : 'Instagram' }), _jsx("td", { children: x.type }), _jsx("td", { children: x.title }), _jsx("td", { children: _jsx(Pill, { s: st.s, label: st.label }) }), _jsx("td", { children: x.route }), _jsx("td", { children: x.flag ?? x.note ?? '' }), _jsxs("td", { className: "nowrap", children: [x.status === 'PROPOSAL' && _jsxs(_Fragment, { children: [_jsx("button", { className: "btn", "aria-label": `Aprobar ${x.title}`, onClick: () => void act(() => api.changePublicationStatus(x.id, 'IN_PRODUCTION'), `Propuesta aprobada · ${x.title}`), children: "Aprobar" }), ' ', _jsx("button", { className: "btn", "aria-label": `Rechazar ${x.title}`, onClick: () => setRejecting(x.id), children: "Rechazar" }), ' '] }), _jsx("button", { className: "btn", "aria-label": `Editar ${x.title}`, onClick: () => setEditing(x.id), children: "Editar" })] })] }, x.id));
                                                }), list.length === 0 && _jsx("tr", { children: _jsx("td", { colSpan: 8, children: "Ninguna publicaci\u00F3n con este filtro." }) })] })] }) }), _jsx("p", { className: "src", children: "Una publicaci\u00F3n solo pasa a \u00ABPublicada\u00BB cuando se confirma en la plataforma o en la vista semanal de Publer, nunca porque se haya generado el archivo." })] }));
                })(), tab === 'tareas' && (_jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Estaci\u00F3n" }), _jsx("th", { children: "Tarea" }), _jsx("th", { children: "Estado" }), _jsx("th", { children: "Agente" }), _jsx("th", { children: "Detalle" })] }) }), _jsx("tbody", { children: data.tasks.filter(t => t.promotion_id === p.id || !t.promotion_id).map(t => (_jsxs("tr", { children: [_jsx("td", { className: "nowrap", children: data.stations.find(s => s.id === t.station_id)?.name }), _jsx("td", { children: _jsx("button", { className: "btn", style: { padding: '3px 8px', textAlign: 'left' }, onClick: () => onTask(t.id), children: t.title }) }), _jsx("td", { children: _jsx(Pill, { s: t.status }) }), _jsx("td", { children: _jsx(AgentChip, { agent: t.agent }) }), _jsx("td", { children: t.status === 'RUNNING' ? `${Math.floor(t.progress)} %` : t.error ?? t.reason ?? t.result ?? '' })] }, t.id))) })] }) })), tab === 'recursos' && (_jsxs(_Fragment, { children: [_jsx(FolderRoles, { owner: "promotion", id: p.id, onSaved: onMessage }), _jsxs("div", { className: "grid2", children: [_jsxs("div", { className: "card", children: [_jsx("h3", { children: "Google Drive \u00B7 cositas artisticas" }), _jsx("div", { className: "tbl-wrap", children: _jsx("table", { children: _jsx("tbody", { children: p.resources.filter(r => r.kind === 'DRIVE').map((r, i) => _jsxs("tr", { children: [_jsx("td", { className: "mono", style: { fontSize: 12 }, children: r.location }), _jsx("td", { children: r.description })] }, i)) }) }) }), _jsx("p", { className: "src", children: "Solo se crean archivos nuevos dentro de la carpeta de la campa\u00F1a. Nunca se borra ni se modifica nada." })] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Planes y documentos" }), p.resources.filter(r => r.kind === 'DOC').map((r, i) => _jsxs("div", { children: [_jsx("b", { children: r.location }), _jsx("p", { children: r.description })] }, i))] })] })] })), tab === 'automatizacion' && (_jsxs(_Fragment, { children: [_jsxs("div", { className: "card", children: [_jsx("h3", { children: "Tareas programadas" }), _jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Nombre" }), _jsx("th", { children: "Cu\u00E1ndo" }), _jsx("th", { children: "Modelo" }), _jsx("th", { children: "D\u00F3nde" }), _jsx("th", { children: "Estado" })] }) }), _jsx("tbody", { children: p.automations.map((a, i) => (_jsxs("tr", { children: [_jsx("td", { children: a.name }), _jsx("td", { className: "nowrap", children: a.schedule }), _jsxs("td", { children: ["Claude ", a.model] }), _jsx("td", { children: a.runs_on === 'PC' ? 'PC de Raúl (Chrome activo)' : 'Nube' }), _jsx("td", { children: _jsx(Pill, { s: a.status }) })] }, i))) })] }) })] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "V\u00EDas de publicaci\u00F3n, por orden de preferencia" }), _jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "V\u00EDa" }), _jsx("th", { children: "Estado" }), _jsx("th", { children: "Notas" })] }) }), _jsx("tbody", { children: p.routes.map((r, i) => _jsxs("tr", { children: [_jsx("td", { children: r.name }), _jsx("td", { children: _jsx(Pill, { s: r.status, label: r.label }) }), _jsx("td", { children: r.notes })] }, i)) })] }) })] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "Elecci\u00F3n de modelo por tipo de trabajo" }), _jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Trabajo" }), _jsx("th", { children: "Base" }), _jsx("th", { children: "Sube un nivel si\u2026" })] }) }), _jsxs("tbody", { children: [_jsxs("tr", { children: [_jsx("td", { children: "Revisar correo, comprobar publicaciones, listar archivos, leer m\u00E9tricas" }), _jsx("td", { children: "Haiku" }), _jsx("td", { children: "Un correo pide dinero, datos o algo raro" })] }), _jsxs("tr", { children: [_jsx("td", { children: "Tanda semanal: piezas, textos, hashtags, spec de promo.py, programar, contactos" }), _jsx("td", { children: "Sonnet" }), _jsx("td", { children: "Falla dos veces; piezas de los d\u00EDas gratis; algo distinto de lo habitual" })] }), _jsxs("tr", { children: [_jsx("td", { children: "Estrategia, herramienta nueva, arreglar algo roto, balances, campa\u00F1a nueva" }), _jsx("td", { children: "Opus" }), _jsx("td", { children: "\u2014" })] })] })] }) }), _jsx("p", { className: "src", children: "Local primero: si promo.py, FFmpeg o ImageMagick resuelven la tarea, no se usa IA." })] })] })), tab === 'metricas' && (_jsxs("div", { className: "grid2", children: [_jsxs("div", { className: "card", children: [_jsx("h3", { children: "M\u00E9tricas de la promoci\u00F3n" }), _jsx("p", { className: "src", children: p.metrics_source }), ['Alcance', 'Impresiones', 'Interacciones', 'Reproducciones', 'Guardados', 'Compartidos', 'Seguidores ganados', 'Clics en el enlace']
                                    .map(m => _jsxs("div", { className: "metric", children: [_jsx("span", { children: m }), _jsx("span", { className: "v", children: "Sin dato" })] }, m))] }), _jsxs("div", { className: "card", children: [_jsx("h3", { children: "C\u00F3mo se usar\u00E1n" }), _jsxs("ul", { children: [_jsx("li", { children: "Comparar por formato, tema, d\u00EDa y hora dentro de la misma marca." }), _jsx("li", { children: "No sacar conclusiones con muestras peque\u00F1as." }), _jsx("li", { children: "Las conclusiones vuelven a la estaci\u00F3n de investigaci\u00F3n y quedan registradas." })] }), _jsx("p", { className: "src", children: "Las m\u00E9tricas son opcionales. SocialHub no inventa cifras: aparecer\u00E1n cuando lleguen datos reales." })] })] })), tab === 'config' && (_jsx("div", { className: "tbl-wrap", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Par\u00E1metro" }), _jsx("th", { children: "Valor" }), _jsx("th", { children: "Origen" })] }) }), _jsx("tbody", { children: p.settings.map((s, i) => (_jsxs("tr", { children: [_jsx("td", { className: "nowrap", children: s.label }), _jsx("td", { children: s.value }), _jsx("td", { children: s.origin === 'BRAND' ? 'Heredado de la marca' : 'Propio de la promoción' })] }, i))) })] }) }))] }) }));
}
const MADRID_DAY = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit' });
const MADRID_TIME = new Intl.DateTimeFormat('es-ES', { timeZone: 'Europe/Madrid', hour: '2-digit', minute: '2-digit' });
const madridDay = (at) => MADRID_DAY.format(new Date(at));
const OUTSIDE_STATUS = { SCHEDULED: 'Programada', PUBLISHED: 'Publicada', FAILED: 'Falló' };
/** Algo programado o publicado fuera de SocialHub: se ve, pero no se edita aquí. */
function OutsideEvent({ x }) {
    const label = `${x.source_label} · ${x.platform === 'INSTAGRAM' ? 'Instagram' : 'TikTok'}`;
    const body = (_jsxs(_Fragment, { children: [_jsxs("span", { className: "t", children: [_jsx("span", { className: "mono", children: MADRID_TIME.format(new Date(x.at)) }), " \u00B7 ", label] }), _jsx("span", { className: "x", children: x.text ?? 'Sin texto' }), _jsxs("span", { className: "t", children: [OUTSIDE_STATUS[x.status], " fuera de SocialHub"] })] }));
    return x.url
        ? _jsx("a", { className: "ev outside", href: x.url, target: "_blank", rel: "noreferrer", children: body })
        : _jsx("div", { className: "ev outside", role: "note", children: body });
}
/** Cuándo se leyó por última vez cada fuente de fuera y si falló. */
function OutsideSources({ sources }) {
    if (!sources.length)
        return _jsx("p", { className: "src", children: "Fuera de SocialHub: a\u00FAn no se ha le\u00EDdo Publora, Publer ni la red." });
    return (_jsxs("p", { className: "src outside-sources", children: ["Fuera de SocialHub: ", sources.map(s => (_jsxs("span", { children: [s.label, " ", s.error ? _jsxs("b", { className: "error-text", children: ["\u00B7 fall\u00F3: ", s.error] }) : s.synced_at ? `· leído ${dateTime(s.synced_at)}` : '· pendiente'] }, s.source)))] }));
}
function PubEvent({ x, onClick }) {
    const st = PUBLICATION_STATUS[x.status];
    return (_jsxs("button", { className: `ev ${statusClass(st.s)}`, onClick: onClick, children: [_jsxs("span", { className: "t", children: [_jsx(Icon, { name: STATUS[st.s].icon }), _jsx("span", { className: "mono", children: hhmm(x.scheduled_time) }), " \u00B7 ", x.type] }), _jsx("span", { className: "x", children: x.title }), _jsx("span", { className: "t", style: { fontWeight: 500 }, children: st.label }), x.flag && _jsx("span", { className: "f", children: x.flag })] }));
}
