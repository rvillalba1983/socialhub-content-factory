import { Fragment as _Fragment, jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Icon, StationIcon } from './icons';
import { PROMOTION_STATUS, PUBLICATION_STATUS, STATUS, alertTone, byPriority, isoDay, parseDay, shortDate, hhmm, statusClass, } from './model';
import { AgentChip, BrandTag, Dot, Pill, StepState, prefersReducedMotion, stepClass } from './ui';
// Geometría del anillo (coordenadas del lienzo)
export const TR = { x0: 300, x1: 2260, y0: 620, y1: 1250, r: 150 };
const SX = [650, 1070, 1490, 1910];
export const WORLD = { w: 2780, h: 1700 };
const pos = (i) => {
    const row = i < 4 ? 0 : 1;
    const k = row ? 7 - i : i;
    return { row, cx: SX[k], cy: row ? TR.y1 : TR.y0 };
};
function segPath(i) {
    const { x0, x1, y0, y1, r } = TR, g = 50;
    if (i < 3)
        return `M${SX[i] + g} ${y0}H${SX[i + 1] - g}`;
    if (i === 3)
        return `M${SX[3] + g} ${y0}H${x1 - r}A${r} ${r} 0 0 1 ${x1} ${y0 + r}V${y1 - r}A${r} ${r} 0 0 1 ${x1 - r} ${y1}H${SX[3] + g}`;
    if (i < 7) {
        const k = 7 - i;
        return `M${SX[k] - g} ${y1}H${SX[k - 1] + g}`;
    }
    return `M${SX[0] - g} ${y1}H${x0 + r}A${r} ${r} 0 0 1 ${x0} ${y1 - r}V${y0 + r}A${r} ${r} 0 0 1 ${x0 + r} ${y0}H${SX[0] - g}`;
}
function segMid(i) {
    const { x0, x1, y0, y1 } = TR;
    if (i < 3)
        return [(SX[i] + SX[i + 1]) / 2, y0];
    if (i === 3)
        return [x1, (y0 + y1) / 2];
    if (i < 7) {
        const k = 7 - i;
        return [(SX[k] + SX[k - 1]) / 2, y1];
    }
    return [x0, (y0 + y1) / 2];
}
/** Vista de una estación: si alguna tarea tiene error o la estación está bloqueada, pide atención. */
export function stationVisual(s) {
    const alert = s.counts.failed > 0 ? 'err' : s.status === 'BLOCKED' ? 'block' : null;
    return { alert, running: s.counts.running > 0 && !s.paused };
}
export function World({ data, sel, onStation, onTask, onTaskAction, onArmory, onPublication, onPromotion, onRegister }) {
    const brands = new Map(data.brands.map(b => [b.id, b]));
    const brandOfPromotion = (pid) => {
        const p = data.promotions.find(x => x.id === pid);
        return p ? brands.get(p.brand_id) : undefined;
    };
    return (_jsxs(_Fragment, { children: [_jsx(Track, { data: data, brandOf: brandOfPromotion }), data.stations.slice(0, 7).map((s, i) => {
                const next = data.stations[i + 1];
                const queued = data.tasks.filter(t => t.station_id === next.id && ['WAITING', 'QUEUED', 'BLOCKED'].includes(t.status)).length;
                if (!queued)
                    return null;
                const [x, y] = segMid(i);
                return _jsxs("div", { className: "seglabel", style: { left: x, top: y }, children: [queued, " en cola"] }, s.id);
            }), (() => {
                const [x, y] = segMid(7);
                return (_jsxs("div", { className: "seglabel fbl", style: { left: x, top: y }, children: [_jsx(Icon, { name: "back" }), "Resultados \u2192 Investigaci\u00F3n"] }));
            })(), _jsxs("div", { className: "hub", style: { left: TR.x0 + 175, top: TR.y0 + 85, width: TR.x1 - TR.x0 - 350 }, children: [data.promotions.map(p => (_jsx(PromotionCard, { p: p, brand: brands.get(p.brand_id), data: data, onOpen: () => onPromotion(p.id) }, p.id))), data.promotions.length === 0 && (_jsxs("article", { className: "promo welcome", children: [_jsx("span", { className: "eyebrow", children: "Empieza aqu\u00ED" }), _jsx("h2", { children: data.brands.length === 0 ? 'Da de alta tu primera marca' : 'Da de alta tu primera promoción' }), _jsx("p", { className: "src", children: "Cada promoci\u00F3n tendr\u00E1 aqu\u00ED su tarjeta y su centro de control. Las estaciones del anillo ya est\u00E1n listas." }), _jsx("button", { className: "open-cc", onClick: e => { e.stopPropagation(); onRegister(); }, children: "Marcas y promociones \u2192" })] })), _jsx(AttentionCard, { data: data, onStation: onStation, onTask: onTask, onArmory: onArmory, onPublication: onPublication })] }), _jsxs("section", { className: "res", style: { left: TR.x1 + 140, top: TR.y0 - 300, width: 380 }, "aria-label": "Agentes y herramientas", children: [_jsx("h2", { children: "Agentes y herramientas" }), _jsx("span", { className: "sub", children: "Local primero \u00B7 solo gratuitas \u00B7 0 \u20AC de gasto" }), _jsxs("div", { className: "res-grid", children: [data.armory_items.length === 0 && _jsx("p", { className: "src", children: "Todav\u00EDa no hay herramientas registradas. Llegan con el motor de tareas." }), data.armory_items.map(r => (_jsxs("button", { className: "rc", onClick: () => onArmory(r.id), children: [_jsx(Dot, { s: r.status }), _jsx("b", { children: r.name }), _jsxs("small", { children: [r.description, _jsxs("span", { className: "min-2", children: [" \u00B7 ", r.note] })] })] }, r.id)))] })] }), data.stations.map((s, i) => (_jsx(StationBlock, { s: s, index: i, tasks: data.tasks.filter(t => t.station_id === s.id), sel: sel, brandOf: brandOfPromotion, onStation: onStation, onTask: onTask, onTaskAction: onTaskAction }, s.id)))] }));
}
function Track({ data, brandOf }) {
    const reduced = prefersReducedMotion();
    const st = data.stations;
    return (_jsxs("svg", { className: "track", width: WORLD.w, height: WORLD.h, viewBox: `0 0 ${WORLD.w} ${WORLD.h}`, "aria-hidden": "true", children: [st.map((_, i) => _jsx("path", { className: "seg-base", d: segPath(i) }, 'b' + i)), st.map((a, i) => {
                const b = st[(i + 1) % st.length];
                const fb = i === 7;
                const va = stationVisual(a), vb = stationVisual(b);
                const moving = fb ? a.status !== 'WAITING' && !a.paused : va.running || vb.running;
                const jam = !fb && !moving && vb.alert;
                const cls = moving ? 'flow' : jam ? `jam jam-${vb.alert}` : 'idle';
                const d = segPath(i);
                const packets = [];
                if (moving && !reduced) {
                    const colors = fb ? ['var(--ink-3)']
                        : data.tasks.filter(t => (t.station_id === a.id || t.station_id === b.id) && t.status === 'RUNNING')
                            .map(t => brandOf(t.promotion_id)?.color ?? 'var(--ink-3)');
                    const n = fb ? 2 : 3, dur = fb ? 10 : 5;
                    for (let k = 0; k < n; k++) {
                        packets.push(_jsx("circle", { className: "pk", r: fb ? 7 : 9, style: { fill: colors[k % colors.length] ?? 'var(--ink-3)' }, children: _jsx("animateMotion", { dur: `${dur}s`, repeatCount: "indefinite", begin: `-${(k * dur / n).toFixed(2)}s`, path: d }) }, `p${i}-${k}`));
                    }
                }
                return [_jsx("path", { className: `seg ${cls}${fb ? ' fb' : ''}`, d: d }, 's' + i), ...packets];
            })] }));
}
function PromotionCard({ p, brand, data, onOpen }) {
    const pubs = data.publications.filter(x => x.promotion_id === p.id);
    const count = (codes) => pubs.filter(x => codes.includes(x.status)).length;
    const groups = [
        ['Publicadas', count(['PUBLISHED']), 'var(--ok)'],
        ['Programadas', count(['SCHEDULED', 'PUBLISHING']), 'var(--wait)'],
        ['Aprobadas', count(['APPROVED']), 'var(--ink)'],
        ['En revisión', count(['IN_REVIEW']), 'var(--block)'],
        ['En producción', count(['IN_PRODUCTION', 'DRAFT']), 'var(--run)'],
        ['Ideas y propuestas', count(['IDEA', 'PROPOSAL']), 'var(--line-2)'],
        ['Con error', count(['PUBLISH_ERROR']), 'var(--err)'],
    ].filter(g => g[1] > 0);
    const total = groups.reduce((a, g) => a + g[1], 0) || 1;
    const today = data.now.slice(0, 10);
    const next = pubs.find(x => x.scheduled_on >= today && x.status !== 'PUBLISHED' && x.status !== 'CANCELLED');
    const incidents = pubs.filter(x => x.flag || x.status === 'PUBLISH_ERROR').length
        + data.tasks.filter(t => t.promotion_id === p.id && t.status === 'FAILED').length;
    const waitingRaul = data.tasks.filter(t => (t.promotion_id === p.id || !t.promotion_id) && t.human
        && t.status !== 'COMPLETED' && t.status !== 'CANCELLED').length;
    const start = parseDay(p.starts_on).getTime(), end = parseDay(p.ends_on).getTime(), span = Math.max(1, end - start);
    const done = Math.max(0, Math.min(1, (parseDay(today).getTime() - start) / span));
    const dayN = Math.floor((parseDay(today).getTime() - start) / 864e5) + 1;
    const totalDays = Math.round(span / 864e5) + 1;
    const phase = dayN < 1 ? `Empieza en ${1 - dayN} ${1 - dayN === 1 ? 'día' : 'días'}` : dayN > totalDays ? 'Terminada' : `Día ${dayN} de ${totalDays}`;
    return (_jsxs("article", { className: "promo", style: { ['--pc']: brand?.color }, tabIndex: 0, onClick: onOpen, onKeyDown: e => e.key === 'Enter' && onOpen(), "aria-label": `Abrir el centro de control de ${p.name}`, children: [_jsxs("div", { className: "p-top", children: [_jsxs("span", { className: "eyebrow", children: [brand?.name, " \u00B7 ", brand?.handle] }), _jsx(Pill, { s: PROMOTION_STATUS[p.status].s, label: PROMOTION_STATUS[p.status].label })] }), _jsx("h2", { children: p.name }), _jsxs("div", { className: "sub", children: [phase, _jsxs("span", { className: "min-2", children: [" \u00B7 ", shortDate(p.starts_on), " \u2013 ", shortDate(p.ends_on)] })] }), _jsxs("div", { className: "timeline", "aria-hidden": "true", children: [_jsx("i", { className: "rail" }), _jsx("i", { className: "done", style: { width: `${done * 100}%` } }), p.milestones.map((m, i) => {
                        const q = (parseDay(m.starts_on).getTime() - start) / span;
                        return q >= 0 && q <= 1 ? _jsx("i", { className: "ms", style: { left: `${q * 100}%` }, title: m.label }, i) : null;
                    }), _jsx("i", { className: "now", style: { left: `${done * 100}%` } })] }), _jsx("div", { className: "stack", role: "img", "aria-label": "Publicaciones por estado", children: groups.map(([l, n, c]) => _jsx("i", { style: { width: `${n / total * 100}%`, background: c }, title: `${l}: ${n}` }, l)) }), _jsx("div", { className: "legend-row min-2", children: groups.map(([l, n, c]) => _jsxs("span", { style: { ['--c']: c }, children: [l, " ", _jsx("b", { className: "mono", children: n })] }, l)) }), _jsxs("div", { className: "promo-facts", children: [_jsxs("div", { children: [_jsx("small", { children: "Pr\u00F3xima" }), _jsx("b", { children: next ? `${shortDate(next.scheduled_on)} ${hhmm(next.scheduled_time)}` : '—' })] }), _jsxs("div", { className: incidents ? 'bad' : '', children: [_jsx("small", { children: "Incidencias" }), _jsx("b", { children: incidents })] }), _jsxs("div", { children: [_jsx("small", { children: "Esperan a Ra\u00FAl" }), _jsx("b", { children: waitingRaul })] })] }), _jsx("button", { className: "open-cc", onClick: e => { e.stopPropagation(); onOpen(); }, children: "Abrir centro de control \u2192" })] }));
}
/**
 * Resumen de hoy (fase 8): lo publicado y lo que queda por publicar. Va dentro de «Necesita atención», que ya cuenta
 * los fallos y lo que espera por Raúl. Solo en la app, sin notificación (decisión de Raúl).
 */
export function TodayCard({ data }) {
    const today = data.now.slice(0, 10);
    const pubs = data.publications.filter(p => p.scheduled_on === today);
    const published = pubs.filter(p => p.status === 'PUBLISHED').length;
    const pending = pubs.filter(p => p.status === 'SCHEDULED' || p.status === 'PUBLISHING').length;
    const failed = pubs.filter(p => p.status === 'PUBLISH_ERROR').length;
    const tomorrow = new Date(Date.parse(`${today}T12:00:00Z`) + 86_400_000).toISOString().slice(0, 10);
    const next = data.publications.filter(p => p.scheduled_on === tomorrow && p.status === 'SCHEDULED').length;
    const rows = [
        ['Publicadas hoy', published, false],
        ['Por publicar hoy', pending, false],
        ['Para mañana', next, false],
    ];
    return (_jsxs("div", { className: "today", "aria-label": "Resumen de hoy", children: [_jsx("div", { className: "promo-facts", children: rows.map(([label, n, bad]) => _jsxs("div", { className: bad ? 'bad' : '', children: [_jsx("small", { children: label }), _jsx("b", { children: n })] }, label)) }), failed > 0 && _jsx("p", { className: "src error-text", children: failed === 1 ? '1 publicación de hoy con error' : `${failed} publicaciones de hoy con error` })] }));
}
/** Cuántos avisos caben en la tarjeta sin tapar las estaciones de abajo. */
const ATTENTION_MAX = 2;
/** Abre lo que señala un aviso: la tarea, la estación, el elemento de la armería (por ejemplo, Drive) o la publicación. */
export function openAlert(a, go) {
    if (a.target_type === 'TASK')
        go.onTask(a.target_id);
    else if (a.target_type === 'PUBLICATION')
        (go.onPublication ? go.onPublication(a.target_id) : go.onStation(a.station_id));
    else if (a.target_type === 'ARMORY')
        (go.onArmory ? go.onArmory(a.target_id) : go.onStation(a.station_id));
    else
        go.onStation(a.target_id);
}
export function AttentionCard({ data, onStation, onTask, onArmory, onPublication }) {
    const blockedByDeps = data.tasks.filter(t => t.status === 'BLOCKED').length;
    const waitingRaul = data.tasks.filter(t => t.human && t.status !== 'COMPLETED' && t.status !== 'CANCELLED').length;
    return (_jsxs("section", { className: "attn-card", "aria-label": "Necesita tu atenci\u00F3n", children: [_jsxs("h2", { children: ["Necesita atenci\u00F3n ", data.alerts.length > 0 && _jsx("span", { className: "count", children: data.alerts.length })] }), _jsx(TodayCard, { data: data }), data.alerts.length === 0 && _jsx("p", { className: "src", children: "Todo en orden." }), byPriority(data.alerts).slice(0, ATTENTION_MAX).map(a => (_jsxs("button", { className: `aitem s-${alertTone(a.kind)}`, onClick: () => openAlert(a, { onTask, onStation, onArmory, onPublication }), children: [_jsx(Dot, { s: a.kind }), _jsxs("span", { children: [_jsx("b", { children: a.title }), a.detail] })] }, a.kind + a.target_id))), _jsxs("p", { className: "src", children: [data.alerts.length > ATTENTION_MAX ? `Y ${data.alerts.length - ATTENTION_MAX} más en las estaciones marcadas · ` : '', blockedByDeps, " tareas bloqueadas \u00B7 ", waitingRaul, " esperan a Ra\u00FAl"] })] }));
}
function StationBlock({ s, index, tasks, sel, brandOf, onStation, onTask, onTaskAction }) {
    const p = pos(index);
    const v = stationVisual(s);
    const selected = sel.type === 'station' && sel.id === s.id;
    const c = s.counts;
    const kpi = (cls, n, label, icon) => (_jsxs("div", { className: `kpi k-${cls}${n ? '' : ' zero'}`, title: label, children: [_jsx("span", { className: "ki", children: _jsx(Icon, { name: icon }) }), _jsx("b", { children: n }), _jsx("span", { className: "kl", children: label })] }));
    return (_jsxs(_Fragment, { children: [_jsxs("button", { className: `node ${v.alert ? `s-${v.alert} attn` : statusClass(s.status)}${v.running ? ' running' : ''}${selected ? ' sel' : ''}`, "data-st": s.id, style: { left: p.cx - 40, top: p.cy - 40 }, onClick: () => onStation(s.id), "aria-label": `${s.name}: ${s.status}`, children: [_jsx(StationIcon, { id: s.id }), v.alert && _jsx("span", { className: "badge", children: c.failed || c.blocked || '!' })] }), _jsxs("article", { className: `st row${p.row} ${statusClass(s.status)}${selected ? ' sel' : ''}${v.alert ? ` attn-${v.alert}` : ''}`, "data-st": s.id, style: { left: p.cx - 170, top: p.row ? p.cy + 64 : p.cy - 64 }, tabIndex: 0, onClick: () => onStation(s.id), onKeyDown: e => e.key === 'Enter' && onStation(s.id), "aria-label": s.name, children: [_jsxs("header", { className: "st-h", children: [_jsx("span", { className: "st-n", children: String(index + 1).padStart(2, '0') }), _jsx("h3", { children: s.name }), _jsx(Pill, { s: s.status })] }), _jsx("p", { className: "st-what min-2", children: s.description }), s.note && _jsx("p", { className: "st-note min-2", children: s.note }), _jsxs("div", { className: "kpis", children: [kpi('run', c.running, 'En curso', 'run'), kpi('wait', c.waiting, 'En espera', 'clock'), kpi('block', c.blocked, 'Bloqueadas', 'lock'), kpi('err', c.failed, 'Errores', 'alert')] }), _jsx("ul", { className: "tasks min-2", children: tasks.map(t => (_jsx(TaskRow, { t: t, brand: brandOf(t.promotion_id), selected: sel.type === 'task' && sel.id === t.id, onOpen: () => onTask(t.id), onAction: a => onTaskAction(t, a) }, t.id))) }), _jsxs("div", { className: "st-res only-3", children: [_jsx("div", { className: "chips", children: s.tools.map(x => _jsx("span", { className: "agent", children: x }, x)) }), _jsx("div", { children: s.limit_note })] })] })] }));
}
function TaskRow({ t, brand, selected, onOpen, onAction }) {
    return (_jsxs("li", { className: `tk ${statusClass(t.status)}${selected ? ' sel' : ''}`, onClick: e => { e.stopPropagation(); onOpen(); }, children: [_jsxs("div", { className: "tk-top", children: [_jsx(Dot, { s: t.status }), _jsx("span", { className: "tk-title", children: t.title })] }), _jsxs("div", { className: "tk-meta", children: [_jsx(BrandTag, { brand: brand }), _jsx(AgentChip, { agent: t.agent })] }), t.status === 'RUNNING' ? (_jsxs("div", { className: "prog", children: [_jsx("div", { className: "bar", children: _jsx("i", { style: { width: `${t.progress}%` } }) }), _jsxs("span", { className: "mono", children: [Math.floor(t.progress), "%"] })] })) : t.status === 'FAILED' ? _jsx("div", { className: "why", style: { color: 'var(--err)' }, children: t.error })
                : (t.reason || t.result) ? _jsx("div", { className: "why", children: t.reason ?? t.result }) : null, _jsxs("div", { className: "deep only-3", children: [_jsx("ol", { className: "steps", children: t.steps.map((s, i) => _jsxs("li", { className: stepClass(s.state), children: [_jsx(StepState, { state: s.state }), s.label] }, i)) }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Estado" }), _jsx("dd", { children: t.paused ? 'En espera · pausada' : STATUS[t.status].label }), _jsx("dt", { children: "Coste" }), _jsxs("dd", { children: ["0 \u20AC \u00B7 ", t.agent.kind === 'AI' ? 'incluido en Claude Pro' : 'gratuito'] }), t.agent.kind === 'AI' && _jsxs(_Fragment, { children: [_jsx("dt", { children: "Cuota" }), _jsx("dd", { children: "No expuesta por el proveedor" })] })] }), _jsx("div", { className: "mini-acts", children: t.available_commands.filter(a => a.type !== 'CHANGE_AGENT').slice(0, 3).map(a => (_jsx("button", { onClick: e => { e.stopPropagation(); onAction(a.type); }, children: a.label }, a.type))) })] })] }));
}
export const publicationState = (code) => PUBLICATION_STATUS[code];
export const todayIso = (data) => isoDay(parseDay(data.now.slice(0, 10)));
