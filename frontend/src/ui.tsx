import { Icon } from './icons';
import { AGENT_ICON, STATUS, agentLabel, type Brand, type StatusCode } from './model';

const reduced = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

export function Pill({ s, label }: { s: StatusCode; label?: string }) {
  const st = STATUS[s] ?? STATUS.idle;
  return (
    <span className={`pill ${st.cls}`}>
      <Icon name={st.icon} className={s === 'run' && !reduced ? 'spin' : ''} />
      <span>{label ?? st.label}</span>
    </span>
  );
}

export function Dot({ s }: { s: StatusCode }) {
  const st = STATUS[s] ?? STATUS.idle;
  return <span className={`dot ${st.cls}`} title={st.label}><Icon name={st.icon} /></span>;
}

export function BrandTag({ brand }: { brand?: Brand }) {
  if (!brand) return <span className="tag b-none">Común</span>;
  return <span className="tag" style={{ color: brand.color }}>{brand.name}</span>;
}

export function AgentChip({ t }: { t: { agentKind: string; agentName: string } }) {
  return <span className="agent"><Icon name={AGENT_ICON[t.agentKind] ?? 'plug'} />{agentLabel(t)}</span>;
}

export function StepState({ state }: { state: number }) {
  if (state >= 1) return <Icon name="check" />;
  if (state < 0) return <Icon name="alert" />;
  if (state > 0) return <Icon name="run" className={reduced ? '' : 'spin'} />;
  return <Icon name="dash" />;
}

export const stepClass = (state: number) => (state >= 1 ? 'd' : state < 0 ? 'f' : state > 0 ? 'c' : '');

export const prefersReducedMotion = () => reduced;
