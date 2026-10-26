import { fireEvent, render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import type { Snapshot, Station, Task } from './model';
import { TaskPanel } from './Panel';
import { World } from './World';

const counts = (o: Partial<Station['counts']> = {}) => ({ total: 0, running: 0, waiting: 0, blocked: 0, errors: 0, completed: 0, idle: 0, cancelled: 0, ...o });
const ids = ['inv', 'est', 'gen', 'rev', 'prog', 'pub', 'ana', 'ret'];

const task = (o: Partial<Task>): Task => ({
  id: 't', stationId: 'gen', promotionId: 'p', type: 'generic', title: 'Tarea de prueba', goal: 'Objetivo', status: 'run',
  paused: false, progress: 40, agentKind: 'ai', agentName: 'Sonnet', why: null, reason: null, error: null, result: null,
  human: false, attempts: 1, maxAttempts: 2, startedAt: null, inputs: [], steps: [{ label: 'Paso', state: 0.5 }], checks: [],
  dependsOn: [], events: [], actions: [{ id: 'pause', label: 'Pausar', confirm: false }, { id: 'cancel', label: 'Cancelar', confirm: true }],
  demo: true, ...o,
});

const snapshot: Snapshot = {
  now: '2026-10-26T10:00:00+01:00', demo: true,
  brands: [{ id: 'b', name: 'El DeLito', handle: '@elde_lito', color: '#D23A2E', demo: true }],
  promotions: [{
    id: 'p', brandId: 'b', name: 'Lanzamiento del poemario', description: '', scope: '', cadence: '', startsOn: '2026-10-27',
    endsOn: '2027-01-18', status: 'active', metricsSource: '', goals: [], rules: [], humanTodos: [], milestones: [],
    resources: [], automations: [], routes: [], settings: [], demo: true,
  }],
  stations: ids.map((id, i) => ({
    id, position: i, name: `Estación ${id}`, description: '', status: id === 'prog' ? 'block' : 'ok', paused: false,
    note: null, limit: '', tools: [], counts: id === 'prog' ? counts({ total: 1, errors: 1 }) : counts(),
  })),
  tasks: [task({ id: 't13', stationId: 'prog', status: 'err', error: 'Publer no respondió' })],
  publications: [], armory: [], commonRules: [],
  alerts: [{ kind: 'err', target: 'task', id: 't13', stationId: 'prog', title: 'Estación prog', detail: 'Error: Tarea de prueba' }],
};

describe('World', () => {
  it('pinta las 8 estaciones del anillo y marca la que tiene errores', () => {
    const { container } = render(<World data={snapshot} sel={{ type: null, id: null }} onStation={() => {}} onTask={() => {}}
      onTaskAction={() => {}} onArmory={() => {}} onPromotion={() => {}} />);
    expect(container.querySelectorAll('.node')).toHaveLength(8);
    const prog = container.querySelector('.node[data-st="prog"]')!;
    expect(prog.className).toContain('attn');
    expect(prog.querySelector('.badge')).toHaveTextContent('1');
    expect(screen.getByText('Necesita atención')).toBeInTheDocument();
  });
});

describe('TaskPanel', () => {
  it('pide confirmación antes de cancelar', () => {
    const onAction = vi.fn();
    render(<TaskPanel t={task({})} tasks={[]} demo onClose={() => {}} onTask={() => {}} onAction={onAction} />);
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(onAction).not.toHaveBeenCalled();
    expect(screen.getByText(/¿Cancelar «Tarea de prueba»\?/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Sí, cancelar' }));
    expect(onAction).toHaveBeenCalledWith('cancel');
  });

  it('solo ofrece las acciones que permite el backend', () => {
    render(<TaskPanel t={task({ status: 'ok', actions: [{ id: 'rerun', label: 'Volver a ejecutar', confirm: false }] })}
      tasks={[]} demo onClose={() => {}} onTask={() => {}} onAction={() => {}} />);
    expect(screen.getByRole('button', { name: 'Volver a ejecutar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pausar' })).not.toBeInTheDocument();
  });
});
