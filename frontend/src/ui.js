import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Icon } from './icons';
import { AGENT_ICON, STATUS, agentLabel } from './model';
const reduced = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
export function Pill({ s, label }) {
    const st = STATUS[s] ?? STATUS.IDLE;
    return (_jsxs("span", { className: `pill ${st.cls}`, children: [_jsx(Icon, { name: st.icon, className: s === 'RUNNING' && !reduced ? 'spin' : '' }), _jsx("span", { children: label ?? st.label })] }));
}
export function Dot({ s }) {
    const st = STATUS[s] ?? STATUS.IDLE;
    return _jsx("span", { className: `dot ${st.cls}`, title: st.label, children: _jsx(Icon, { name: st.icon }) });
}
export function BrandTag({ brand }) {
    if (!brand)
        return _jsx("span", { className: "tag b-none", children: "Com\u00FAn" });
    return _jsx("span", { className: "tag", style: { color: brand.color }, children: brand.name });
}
export function AgentChip({ agent }) {
    return _jsxs("span", { className: "agent", children: [_jsx(Icon, { name: AGENT_ICON[agent.kind] ?? 'plug' }), agentLabel(agent)] });
}
export function StepState({ state }) {
    if (state === 'DONE')
        return _jsx(Icon, { name: "check" });
    if (state === 'FAILED')
        return _jsx(Icon, { name: "alert" });
    if (state === 'IN_PROGRESS')
        return _jsx(Icon, { name: "run", className: reduced ? '' : 'spin' });
    return _jsx(Icon, { name: "dash" });
}
const STEP_CLASS = { DONE: 'd', FAILED: 'f', IN_PROGRESS: 'c', PENDING: '' };
export const stepClass = (state) => STEP_CLASS[state];
export const prefersReducedMotion = () => reduced;
