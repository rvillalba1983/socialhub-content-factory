import { Icon } from './icons';
import { AGENT_ICON, STATUS, agentLabel, type Agent, type Brand, type StatusCode, type TaskStepState } from './model';

const reduced = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

export function Pill({ s, label }: { s: StatusCode; label?: string }) {
  const st = STATUS[s] ?? STATUS.IDLE;
  return (
    <span className={`pill ${st.cls}`}>
      <Icon name={st.icon} className={s === 'RUNNING' && !reduced ? 'spin' : ''} />
      <span>{label ?? st.label}</span>
    </span>
  );
}

export function Dot({ s }: { s: StatusCode }) {
  const st = STATUS[s] ?? STATUS.IDLE;
  return <span className={`dot ${st.cls}`} title={st.label}><Icon name={st.icon} /></span>;
}

export function BrandTag({ brand }: { brand?: Brand }) {
  if (!brand) return <span className="tag b-none">Común</span>;
  return <span className="tag" style={{ color: brand.color }}>{brand.name}</span>;
}

export function AgentChip({ agent }: { agent: Agent }) {
  return <span className="agent"><Icon name={AGENT_ICON[agent.kind] ?? 'plug'} />{agentLabel(agent)}</span>;
}

export function StepState({ state }: { state: TaskStepState }) {
  if (state === 'DONE') return <Icon name="check" />;
  if (state === 'FAILED') return <Icon name="alert" />;
  if (state === 'IN_PROGRESS') return <Icon name="run" className={reduced ? '' : 'spin'} />;
  return <Icon name="dash" />;
}

const STEP_CLASS: Record<TaskStepState, string> = { DONE: 'd', FAILED: 'f', IN_PROGRESS: 'c', PENDING: '' };
export const stepClass = (state: TaskStepState) => STEP_CLASS[state];

export const prefersReducedMotion = () => reduced;
