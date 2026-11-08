import { Fragment as _Fragment, jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { ApiError, api } from './api';
import { Icon } from './icons';
import { PUBLICATION_NEXT, PUBLICATION_STATUS, REJECTION_REASONS, dateTime, } from './model';
import { Pill } from './ui';
const INITIAL = ['IDEA', 'PROPOSAL', 'IN_PRODUCTION', 'DRAFT'];
const toInput = (p) => ({
    platform: p?.platform ?? 'INSTAGRAM', type: p?.type ?? '', title: p?.title ?? '',
    scheduled_on: p?.scheduled_on ?? '', scheduled_time: p ? p.scheduled_time.slice(0, 5) : '19:00',
    timezone: p?.timezone ?? 'Europe/Madrid', route: p?.route ?? 'Publer', note: p?.note ?? '',
    caption: p?.caption ?? '', hashtags: p?.hashtags ?? '',
});
/** Alta y edición de una publicación, su estado y sus archivos. */
export function PublicationEditor({ promotionId, publication, onClose, onSaved }) {
    const [form, setForm] = useState(toInput(publication));
    const [initial, setInitial] = useState('IDEA');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    const [asked, setAsked] = useState(null);
    const [textsTouched, setTextsTouched] = useState(false);
    /** Pide el motivo antes de cancelar la publicación o descartar el texto (memoria editorial). */
    const [rejecting, setRejecting] = useState(null);
    // Si Claude deja textos nuevos mientras el editor está abierto y Raúl no ha tocado los suyos, se ven al momento
    useEffect(() => {
        if (!textsTouched)
            setForm(f => ({ ...f, caption: publication?.caption ?? '', hashtags: publication?.hashtags ?? '' }));
    }, [publication?.caption, publication?.hashtags, textsTouched]);
    /** Programada, publicándose o publicada: la vía ya tiene esta versión y no se cambia. */
    const locked = publication?.status === 'SCHEDULED' || publication?.status === 'PUBLISHING' || publication?.status === 'PUBLISHED';
    const set = (k, v) => setForm({ ...form, [k]: v });
    const run = async (action, message) => {
        setBusy(true);
        setError(null);
        try {
            onSaved(await action(), message);
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
        finally {
            setBusy(false);
        }
    };
    const submit = (e) => {
        e.preventDefault();
        const input = { ...form, note: form.note?.trim() || undefined, caption: form.caption?.trim() || undefined,
            hashtags: form.hashtags?.trim() || undefined };
        void run(() => (publication ? api.updatePublication(publication.id, input)
            : api.createPublication(promotionId, { ...input, initial_status: initial })), publication ? 'Publicación guardada' : 'Publicación creada');
        setTextsTouched(false);
    };
    const askClaude = async () => {
        if (!publication)
            return;
        setError(null);
        try {
            await api.orderFromClaude(promotionId, {
                type: 'CAPTIONS', title: `Textos · ${publication.title}`, inputs: [publication.id],
                goal: `Escribe el texto y los hashtags de «${publication.title}» (${publication.type}, ${publication.platform === 'TIKTOK' ? 'TikTok' : 'Instagram'}).`,
            });
            setAsked('Pedido a Claude: los textos llegarán aquí para que los revises.');
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
        }
    };
    return (_jsxs("div", { className: "card form", "aria-label": publication ? 'Editar publicación' : 'Nueva publicación', children: [_jsxs("div", { className: "row-between", children: [_jsx("h3", { children: publication ? `Publicación · ${publication.title}` : 'Nueva publicación' }), _jsx("button", { type: "button", className: "ibtn", "aria-label": "Cerrar", title: "Cerrar", onClick: onClose, children: _jsx(Icon, { name: "x" }) })] }), _jsxs("form", { onSubmit: submit, children: [_jsxs("div", { className: "form-grid", children: [_jsxs("label", { className: "field", children: [_jsx("span", { children: "Plataforma" }), _jsxs("select", { disabled: locked, value: form.platform, onChange: e => set('platform', e.target.value), children: [_jsx("option", { value: "INSTAGRAM", children: "Instagram" }), _jsx("option", { value: "TIKTOK", children: "TikTok" })] })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Tipo" }), _jsx("input", { required: true, disabled: locked, maxLength: 40, list: "pub-types", placeholder: "Carrusel", value: form.type, onChange: e => set('type', e.target.value) }), _jsxs("datalist", { id: "pub-types", children: [_jsx("option", { value: "Carrusel" }), _jsx("option", { value: "Historia" }), _jsx("option", { value: "Reel" }), _jsx("option", { value: "Clip" })] })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "T\u00EDtulo" }), _jsx("input", { required: true, disabled: locked, maxLength: 200, value: form.title, onChange: e => set('title', e.target.value) })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "D\u00EDa" }), _jsx("input", { required: true, disabled: locked, type: "date", value: form.scheduled_on, onChange: e => set('scheduled_on', e.target.value) })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Hora (Espa\u00F1a)" }), _jsx("input", { required: true, disabled: locked, type: "time", value: form.scheduled_time, onChange: e => set('scheduled_time', e.target.value) })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "V\u00EDa" }), _jsx("input", { required: true, disabled: locked, maxLength: 40, value: form.route, onChange: e => set('route', e.target.value) })] }), !publication && (_jsxs("label", { className: "field", children: [_jsx("span", { children: "Empieza como" }), _jsx("select", { value: initial, onChange: e => setInitial(e.target.value), children: INITIAL.map(s => _jsx("option", { value: s, children: PUBLICATION_STATUS[s].label }, s)) })] }))] }), _jsxs("label", { className: "field", children: [_jsxs("span", { children: ["Texto (", (form.caption ?? '').length, " / 2200)"] }), _jsx("textarea", { rows: 4, disabled: locked, maxLength: 2200, value: form.caption ?? '', onChange: e => { setTextsTouched(true); set('caption', e.target.value); } })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Hashtags" }), _jsx("input", { disabled: locked, maxLength: 1000, placeholder: "#poesia #eldelito", value: form.hashtags ?? '', onChange: e => { setTextsTouched(true); set('hashtags', e.target.value); } })] }), publication && !locked && (_jsxs("p", { className: "src", children: [_jsx("button", { type: "button", className: "btn", onClick: () => void askClaude(), children: "Pedir textos a Claude" }), ' ', (publication.caption || publication.hashtags) && (_jsx("button", { type: "button", className: "btn", onClick: () => setRejecting('TEXT'), children: "Descartar texto" })), asked && _jsxs(_Fragment, { children: [" ", asked] })] })), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Observaciones" }), _jsx("textarea", { rows: 2, disabled: locked, maxLength: 2000, value: form.note ?? '', onChange: e => set('note', e.target.value) })] }), publication?.status === 'SCHEDULED' && (_jsx("p", { className: "src", children: "Est\u00E1 programada: para cambiar algo, vuelve a \u00ABAprobada\u00BB y progr\u00E1mala otra vez." })), _jsxs("div", { className: "form-actions", children: [error && _jsx("p", { className: "error", role: "alert", children: error }), !locked && _jsx("button", { className: "btn primary", disabled: busy, children: busy ? 'Guardando…' : 'Guardar' }), _jsx("button", { type: "button", className: "btn", onClick: onClose, children: "Cerrar" })] })] }), publication && (_jsxs(_Fragment, { children: [_jsxs("div", { className: "status-row", children: [_jsx("span", { children: "Estado" }), _jsx(Pill, { s: PUBLICATION_STATUS[publication.status].s, label: PUBLICATION_STATUS[publication.status].label }), PUBLICATION_NEXT[publication.status].map(next => (_jsxs("button", { type: "button", className: "btn", disabled: busy, onClick: () => (next === 'CANCELLED' ? setRejecting('CANCEL')
                                    : void run(() => api.changePublicationStatus(publication.id, next), `Estado: ${PUBLICATION_STATUS[next].label}`)), children: ["Pasar a \u00AB", PUBLICATION_STATUS[next].label, "\u00BB"] }, next)))] }), rejecting && (_jsx(ReasonForm, { title: rejecting === 'CANCEL' ? 'Por qué la cancelas' : 'Por qué descartas el texto', action: rejecting === 'CANCEL' ? 'Cancelar publicación' : 'Descartar texto', onCancel: () => setRejecting(null), onSubmit: (reason, note) => {
                            setRejecting(null);
                            void run(() => (rejecting === 'CANCEL'
                                ? api.changePublicationStatus(publication.id, 'CANCELLED', reason, note)
                                : api.rejectPublicationText(publication.id, reason, note)), rejecting === 'CANCEL' ? 'Publicación cancelada' : 'Texto descartado');
                            if (rejecting === 'TEXT')
                                setTextsTouched(false);
                        } })), _jsx("p", { className: "src", children: "Programar exige al menos 6 horas de margen. \u00ABPublicada\u00BB solo llega con la confirmaci\u00F3n de la plataforma." }), publication.flag && (_jsxs("p", { className: "flag-row", role: "status", children: [_jsx(Icon, { name: "alert" }), " ", _jsx("span", { children: publication.flag }), ' ', _jsx("button", { type: "button", className: "btn", disabled: busy, onClick: () => void run(() => api.clearPublicationFlag(publication.id).then(() => ({ ...publication, flag: undefined })), 'Incidencia resuelta'), children: "Resuelto" })] })), _jsx(PublishPanel, { publication: publication }), _jsx(MetricsPanel, { publication: publication }), _jsx(QualityPanel, { publication: publication, promotionId: promotionId }), _jsx(PublicationFiles, { id: publication.id, locked: locked })] }))] }));
}
function PublicationFiles({ id, locked }) {
    const [files, setFiles] = useState(null);
    const [path, setPath] = useState('');
    const [error, setError] = useState(null);
    const load = () => api.publicationFiles(id).then(r => setFiles(r.items)).catch(e => setError(String(e.message ?? e)));
    useEffect(() => { void load(); }, [id]);
    const act = async (action) => {
        setError(null);
        try {
            await action();
            await load();
            return true;
        }
        catch (e) {
            setError(e instanceof Error ? e.message : String(e));
            return false;
        }
    };
    return (_jsxs("fieldset", { className: "rows", children: [_jsx("legend", { children: "Archivos" }), files?.length === 0 && _jsx("p", { className: "src", children: "Sin archivos. Llegan solos si el spec.json indica esta publicaci\u00F3n en \u00ABpublicacion\u00BB, o a\u00F1\u00E1delos aqu\u00ED." }), files?.map(f => (_jsxs("div", { className: "file-row", children: [_jsx("span", { className: "mono file-path", title: f.path, children: f.path }), _jsxs("small", { className: "src nowrap", children: [f.origin === 'SPEC' ? 'Del spec' : 'A mano', " \u00B7 ", dateTime(f.added_at)] }), !f.available && _jsx(Pill, { s: "BLOCKED", label: "No disponible" }), !locked && _jsx("button", { type: "button", className: "ibtn", "aria-label": "Quitar archivo", title: "Quitar de la publicaci\u00F3n (no se borra)", onClick: () => void act(() => api.detachPublicationFile(id, f.path)), children: _jsx(Icon, { name: "x" }) })] }, f.path))), !locked && (_jsxs("form", { className: "file-add", onSubmit: e => { e.preventDefault(); void act(() => api.attachPublicationFile(id, path.trim())).then(ok => { if (ok)
                    setPath(''); }); }, children: [_jsx("input", { "aria-label": "Ruta del archivo", placeholder: "G:\\Mi unidad\\\u2026\\pieza.jpg", value: path, onChange: e => setPath(e.target.value) }), _jsx("button", { className: "btn", disabled: !path.trim(), children: "A\u00F1adir archivo" })] })), error && _jsx("p", { className: "error", role: "alert", children: error })] }));
}
const JOB_STATUS = {
    WORKING: 'RUNNING', RETRY_WAIT: 'WAITING', SCHEDULED: 'OK', CHECKING: 'RUNNING', MANUAL: 'BLOCKED',
    PUBLISHED: 'OK', FAILED: 'FAILED', CANCELLED: 'WAITING',
};
/** Publicación: vía en uso, próximo intento, LEEME si hay que hacerla a mano e historial. */
function PublishPanel({ publication }) {
    const [job, setJob] = useState(null);
    const [error, setError] = useState(null);
    const live = ['SCHEDULED', 'PUBLISHING', 'PUBLISHED', 'PUBLISH_ERROR'].includes(publication.status);
    useEffect(() => {
        if (!live) {
            setJob(null);
            return;
        }
        api.publishJob(publication.id).then(setJob)
            .catch(e => { if (e instanceof ApiError && e.status === 404)
            setJob(null);
        else
            setError(e instanceof Error ? e.message : String(e)); });
    }, [publication, live]);
    if (!live || (!job && !error))
        return null;
    return (_jsxs("fieldset", { className: "rows", "aria-label": "Publicaci\u00F3n", children: [_jsx("legend", { children: "Publicaci\u00F3n" }), error && _jsx("p", { className: "error", children: error }), job && (_jsxs(_Fragment, { children: [_jsxs("div", { className: "status-row", children: [_jsx(Pill, { s: JOB_STATUS[job.status], label: job.status_label }), job.route && _jsxs("span", { children: ["V\u00EDa: ", _jsx("b", { children: job.route })] }), job.attempts > 1 && _jsxs("small", { className: "src", children: ["Ronda ", job.attempts] }), job.next_try_at && _jsxs("small", { className: "src", children: ["Pr\u00F3ximo intento: ", dateTime(job.next_try_at)] })] }), _jsxs("p", { className: "src", children: ["V\u00EDas por orden: ", job.routes.map(r => `${r.label}${r.configured ? '' : ' (no configurada)'}`).join(' → ')] }), job.url && _jsxs("p", { children: ["Enlace: ", _jsx("a", { href: job.url, target: "_blank", rel: "noreferrer", children: job.url })] }), job.external_id && _jsxs("p", { className: "src", children: ["Identificador: ", _jsx("span", { className: "mono", children: job.external_id })] }), job.manual_file && _jsxs("p", { children: ["LEEME para publicarla a mano: ", _jsx("span", { className: "mono", children: job.manual_file })] }), job.last_error && job.status !== 'PUBLISHED' && _jsxs("p", { className: "src", children: ["\u00DAltimo problema: ", job.last_error] }), _jsxs("details", { children: [_jsx("summary", { children: "Historial" }), _jsx("ul", { className: "src", children: job.log.map((line, n) => _jsx("li", { children: line }, n)) })] })] }))] }));
}
const METRICS = [
    ['views', 'Reproducciones'], ['reach', 'Alcance'], ['likes', 'Me gusta'], ['comments', 'Comentarios'],
    ['saves', 'Guardados'], ['shares', 'Compartidos'],
];
/** Métricas: se leen una vez, a los 7 días de publicarse. Lo que no se vio queda «Sin dato». */
function MetricsPanel({ publication }) {
    const [m, setM] = useState(null);
    useEffect(() => {
        if (publication.status !== 'PUBLISHED') {
            setM(null);
            return;
        }
        api.publicationMetrics(publication.id).then(setM).catch(() => setM(null));
    }, [publication]);
    if (publication.status !== 'PUBLISHED')
        return null;
    return (_jsxs("fieldset", { className: "rows", "aria-label": "M\u00E9tricas", children: [_jsx("legend", { children: "M\u00E9tricas" }), !m && _jsx("p", { className: "src", children: "Se leen solas a los 7 d\u00EDas de publicarse." }), m?.status === 'REQUESTED' && _jsxs("p", { className: "src", children: ["Pedidas a Claude el ", dateTime(m.requested_at), "."] }), m?.status === 'FAILED' && _jsxs("p", { className: "src", children: ["No se pudieron leer", m.note ? `: ${m.note}` : '', ". Se reintenta al d\u00EDa siguiente (m\u00E1ximo 3 veces)."] }), m?.status === 'DONE' && (_jsxs(_Fragment, { children: [_jsx("div", { className: "metrics", children: METRICS.map(([k, label]) => (_jsxs("div", { children: [_jsx("small", { children: label }), _jsx("b", { children: m[k] == null ? 'Sin dato' : m[k].toLocaleString('es-ES') })] }, k))) }), _jsxs("p", { className: "src", children: ["Le\u00EDdas el ", dateTime(m.measured_at), m.note ? ` · ${m.note}` : ''] })] }))] }));
}
/** Control de calidad: avisos del código y de Claude. Cada uno se corrige o se acepta con su motivo. */
function QualityPanel({ publication, promotionId }) {
    const [report, setReport] = useState(null);
    const [accepting, setAccepting] = useState(null);
    const [reason, setReason] = useState('');
    const [note, setNote] = useState(null);
    const [error, setError] = useState(null);
    const load = () => api.publicationQuality(publication.id).then(setReport).catch(e => setError(e instanceof Error ? e.message : String(e)));
    // Se vuelve a comprobar cuando cambia la publicación (texto, estado…)
    useEffect(() => { void load(); }, [publication]);
    const accept = async (e) => {
        e.preventDefault();
        if (!accepting)
            return;
        setError(null);
        try {
            setReport(await api.acceptQualityIssue(publication.id, accepting, reason.trim()));
            setAccepting(null);
            setReason('');
        }
        catch (err) {
            setError(err instanceof Error ? err.message : String(err));
        }
    };
    const review = async () => {
        setError(null);
        try {
            await api.orderFromClaude(promotionId, {
                type: 'QUALITY_REVIEW', title: `Revisión · ${publication.title}`, inputs: [publication.id],
                goal: `Revisa la calidad de «${publication.title}»: coherencia, tono de la marca, ortografía y afirmaciones sin fuente.`,
            });
            setNote('Revisión pedida a Claude: sus avisos aparecerán aquí.');
        }
        catch (err) {
            setError(err instanceof Error ? err.message : String(err));
        }
    };
    return (_jsxs("fieldset", { className: "rows", "aria-label": "Control de calidad", children: [_jsx("legend", { children: "Control de calidad" }), report && report.issues.length === 0 && _jsx("p", { className: "src", children: "Sin avisos." }), report?.issues.map(i => (_jsxs("div", { className: "quality-row", children: [_jsx("span", { className: `pill ${i.accepted ? 's-ok' : 's-block'}`, children: i.source === 'CLAUDE' ? 'Claude' : 'Comprobación' }), _jsxs("span", { children: [i.message, i.accepted && _jsxs("small", { className: "src", children: [" \u00B7 Aceptado: ", i.accepted_reason] })] }), !i.accepted && accepting !== i.key && (_jsx("button", { type: "button", className: "btn", onClick: () => { setAccepting(i.key); setReason(''); }, children: "Aceptar" })), accepting === i.key && (_jsxs("form", { className: "file-add", onSubmit: accept, children: [_jsx("input", { "aria-label": "Por qu\u00E9 lo aceptas", placeholder: "Por qu\u00E9 lo aceptas", value: reason, onChange: e => setReason(e.target.value) }), _jsx("button", { className: "btn", disabled: reason.trim().length < 3, children: "Aceptar aviso" })] }))] }, i.key))), report && report.open > 0 && (_jsxs("p", { className: "src", children: [report.open === 1 ? '1 aviso abierto' : `${report.open} avisos abiertos`, ": no puede pasar a \u00ABAprobada\u00BB ni a \u00ABProgramada\u00BB hasta corregirlos o aceptarlos."] })), _jsxs("p", { className: "src", children: [_jsx("button", { type: "button", className: "btn", onClick: () => void load(), children: "Comprobar de nuevo" }), ' ', _jsx("button", { type: "button", className: "btn", onClick: () => void review(), children: "Revisar con Claude" }), note && _jsxs(_Fragment, { children: [" ", note] })] }), error && _jsx("p", { className: "error", role: "alert", children: error })] }));
}
/** Motivo fijo y nota: queda en la memoria editorial de la marca. */
export function ReasonForm({ title, action, onSubmit, onCancel }) {
    const [reason, setReason] = useState('TONE');
    const [note, setNote] = useState('');
    return (_jsxs("div", { className: "card form reason-form", role: "group", "aria-label": title, children: [_jsx("b", { children: title }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Motivo" }), _jsx("select", { value: reason, onChange: e => setReason(e.target.value), children: REJECTION_REASONS.map(([k, l]) => _jsx("option", { value: k, children: l }, k)) })] }), _jsxs("label", { className: "field", children: [_jsx("span", { children: "Nota (opcional)" }), _jsx("input", { maxLength: 1000, value: note, onChange: e => setNote(e.target.value) })] }), _jsx("p", { className: "src", children: "Queda en la memoria editorial: Claude lo tendr\u00E1 en cuenta en los pr\u00F3ximos encargos." }), _jsxs("div", { className: "form-actions", children: [_jsx("button", { type: "button", className: "btn primary", onClick: () => onSubmit(reason, note.trim() || undefined), children: action }), _jsx("button", { type: "button", className: "btn", onClick: onCancel, children: "Volver" })] })] }));
}
