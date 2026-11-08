import { jsx as _jsx } from "react/jsx-runtime";
const PATHS = {
    check: '<path d="M3 8.5l3 3 7-7"/>',
    run: '<path d="M8 2a6 6 0 1 1-6 6"/>',
    clock: '<circle cx="8" cy="8" r="6"/><path d="M8 5v3l2 1.5"/>',
    lock: '<rect x="3.5" y="7" width="9" height="6.5" rx="1.5"/><path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2"/>',
    alert: '<path d="M8 2.2l6.3 11H1.7z"/><path d="M8 6.5v3M8 11.4v.1"/>',
    dash: '<circle cx="8" cy="8" r="6" stroke-dasharray="2.4 2.6"/>',
    x: '<path d="M4 4l8 8M12 4l-8 8"/>',
    local: '<rect x="3.5" y="3.5" width="9" height="9" rx="1.5"/><path d="M6 1.5v2M10 1.5v2M6 12.5v2M10 12.5v2M1.5 6h2M1.5 10h2M12.5 6h2M12.5 10h2"/>',
    ai: '<path d="M8 1.8l1.6 4.6L14.2 8l-4.6 1.6L8 14.2l-1.6-4.6L1.8 8l4.6-1.6z"/>',
    human: '<circle cx="8" cy="5" r="2.6"/><path d="M3 14c.6-3 2.6-4.5 5-4.5s4.4 1.5 5 4.5"/>',
    browser: '<rect x="2" y="3" width="12" height="10" rx="1.5"/><path d="M2 6h12"/>',
    plug: '<path d="M6 2v3M10 2v3M4.5 5h7v2.5a3.5 3.5 0 0 1-7 0zM8 11v3"/>',
    back: '<path d="M10 3L5 8l5 5"/>',
    close: '<path d="M4 4l8 8M12 4l-8 8"/>',
    search: '<circle cx="7" cy="7" r="4.5"/><path d="M10.5 10.5L14 14"/>',
    minus: '<path d="M3.5 8h9"/>',
    plus: '<path d="M3.5 8h9M8 3.5v9"/>',
    fit: '<path d="M2.5 6V2.5H6M10 2.5h3.5V6M13.5 10v3.5H10M6 13.5H2.5V10"/>',
};
/** Iconos de las estaciones del anillo. */
export const STATION_ICON = {
    inv: '<circle cx="7" cy="7" r="4.5"/><path d="M10.5 10.5L14 14"/>',
    est: '<circle cx="8" cy="8" r="6"/><path d="M10.6 5.4L9.1 9.1 5.4 10.6 6.9 6.9z"/>',
    gen: '<path d="M8 2l1.4 4.6L14 8l-4.6 1.4L8 14l-1.4-4.6L2 8l4.6-1.4z"/>',
    rev: '<path d="M8 1.8l5 2v4c0 3-2.2 5.2-5 6.4C5.2 13 3 10.8 3 7.8v-4z"/><path d="M5.8 8l1.6 1.6 3-3.1"/>',
    prog: '<rect x="2" y="3" width="12" height="11" rx="2"/><path d="M2 6.5h12M5.5 1.5v3M10.5 1.5v3M8 9v2l1.3.8"/>',
    pub: '<path d="M14 2L7 9"/><path d="M14 2l-4.5 12-2.5-5-5-2.5z"/>',
    ana: '<path d="M2.5 13.5h11"/><path d="M4.5 11V8M8 11V4.5M11.5 11V7"/>',
    ret: '<path d="M13 8a5 5 0 1 1-1.5-3.6"/><path d="M13 2.5v3h-3"/>',
};
export function Icon({ name, className = '' }) {
    // Trazos fijos del propio código: no llevan datos de usuario.
    return _jsx("svg", { className: `ic ${className}`, viewBox: "0 0 16 16", "aria-hidden": "true", dangerouslySetInnerHTML: { __html: PATHS[name] } });
}
export function StationIcon({ id }) {
    return _jsx("svg", { className: "ic", viewBox: "0 0 16 16", "aria-hidden": "true", dangerouslySetInnerHTML: { __html: STATION_ICON[id] ?? PATHS.dash } });
}
