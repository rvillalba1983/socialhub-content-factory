import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Factory } from './Factory';
import { useFactory } from './useFactory';
/** SocialHub solo se abre en este ordenador: no hay pantalla de acceso. */
export function App() {
    const { data, error, live, reload } = useFactory();
    if (error && !data)
        return _jsxs(Centered, { children: [_jsx("h1", { children: "No se puede conectar con SocialHub" }), _jsx("p", { children: error }), _jsx("button", { className: "btn primary", onClick: reload, children: "Reintentar" })] });
    if (!data)
        return _jsx(Centered, { children: _jsx("p", { className: "src", children: "Cargando la f\u00E1brica\u2026" }) });
    return _jsx(Factory, { data: data, live: live, onRefresh: reload });
}
function Centered({ children }) {
    return _jsx("main", { className: "centered", children: children });
}
