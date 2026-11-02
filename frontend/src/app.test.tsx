import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, vi } from 'vitest';
import { api } from './api';
import { ExternalOperations } from './ExternalOperations';
import { BrandForm, PromotionForm } from './Registration';
import type { ExternalOperationPage, Snapshot, Station, Task } from './model';
import { TaskPanel } from './Panel';
import { World } from './World';

const counts = (o: Partial<Station['counts']> = {}): Station['counts'] =>
  ({ total: 0, running: 0, waiting: 0, blocked: 0, failed: 0, completed: 0, idle: 0, cancelled: 0, ...o });
const ids = ['inv', 'est', 'gen', 'rev', 'prog', 'pub', 'ana', 'ret'];

const task = (o: Partial<Task>): Task => ({
  id: 't', station_id: 'gen', promotion_id: 'p', type: 'GENERIC', title: 'Tarea de prueba', goal: 'Objetivo',
  status: 'RUNNING', paused: false, progress: 40, agent: { kind: 'AI', name: 'Sonnet' }, human: false, attempts: 1,
  max_attempts: 2, inputs: [], steps: [{ label: 'Paso', state: 'IN_PROGRESS' }], checks: [], depends_on: [], events: [],
  available_commands: [
    { type: 'PAUSE', label: 'Pausar', needs_confirmation: false },
    { type: 'CANCEL', label: 'Cancelar', needs_confirmation: true },
    { type: 'CHANGE_AGENT', label: 'Cambiar agente', needs_confirmation: false },
  ],
  demo: true, ...o,
});

const snapshot: Snapshot = {
  now: '2026-10-26T10:00:00+01:00', demo: true,
  brands: [{ id: 'b', name: 'El DeLito', handle: '@elde_lito', color: '#D23A2E', demo: true }],
  promotions: [{
    id: 'p', brand_id: 'b', name: 'Lanzamiento del poemario', description: '', scope: '', cadence: '', starts_on: '2026-10-27',
    ends_on: '2027-01-18', status: 'ACTIVE', metrics_source: '', goals: [], rules: [], human_todos: [], milestones: [],
    resources: [], automations: [], routes: [], settings: [], demo: true,
  }],
  stations: ids.map((id, i) => ({
    id, position: i, name: `Estación ${id}`, description: '', status: id === 'prog' ? 'BLOCKED' : 'OK', paused: false,
    limit_note: '', tools: [], counts: id === 'prog' ? counts({ total: 1, failed: 1 }) : counts(),
    available_commands: ['PAUSE'],
  })),
  tasks: [task({ id: 't13', station_id: 'prog', status: 'FAILED', error: 'Publer no respondió' })],
  publications: [], armory_items: [], common_rules: [],
  alerts: [{ kind: 'FAILED', target_type: 'TASK', target_id: 't13', station_id: 'prog', title: 'Tarea de prueba', detail: 'Publer no respondió' }],
};

afterEach(() => vi.restoreAllMocks());

describe('World', () => {
  it('pinta las 8 estaciones del anillo y marca la que tiene errores', () => {
    const { container } = render(<World data={snapshot} sel={{ type: null, id: null }} onStation={() => {}} onTask={() => {}}
      onTaskAction={() => {}} onArmory={() => {}} onPromotion={() => {}} onRegister={() => {}} />);
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
    expect(onAction).toHaveBeenCalledWith('CANCEL');
  });

  it('solo ofrece las órdenes que permite el backend', () => {
    render(<TaskPanel t={task({ status: 'COMPLETED', available_commands: [{ type: 'RERUN', label: 'Volver a ejecutar', needs_confirmation: false }] })}
      tasks={[]} demo onClose={() => {}} onTask={() => {}} onAction={() => {}} />);
    expect(screen.getByRole('button', { name: 'Volver a ejecutar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pausar' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Reasignar agente')).not.toBeInTheDocument();
  });

  it('cambia de agente con el modelo del contrato', () => {
    const onAction = vi.fn();
    render(<TaskPanel t={task({})} tasks={[]} demo onClose={() => {}} onTask={() => {}} onAction={onAction} />);
    fireEvent.change(screen.getByLabelText('Reasignar agente'), { target: { value: 'OPUS' } });
    expect(onAction).toHaveBeenCalledWith('CHANGE_AGENT', 'OPUS');
  });
});

describe('api', () => {
  it('envía las órdenes a /commands con la cabecera de protección', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(task({})), { status: 200 }));
    await api.taskCommand('t1', { type: 'CHANGE_AGENT', model: 'OPUS' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/tasks/t1/commands');
    expect(init?.method).toBe('POST');
    expect((init?.headers as Record<string, string>)['X-SocialHub-Request']).toBe('1');
    expect(JSON.parse(String(init?.body))).toEqual({ type: 'CHANGE_AGENT', model: 'OPUS' });
  });

  it('muestra el detalle del error que devuelve el backend (problem+json)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ title: 'Conflict', status: 409, detail: 'No se puede «Pausar» una tarea en estado «Completada».' }),
      { status: 409, headers: { 'Content-Type': 'application/problem+json' } }));
    await expect(api.stationCommand('gen', 'PAUSE')).rejects.toThrow('No se puede «Pausar»');
  });
});

describe('ExternalOperations', () => {
  it('lista las operaciones y filtra las fallidas', async () => {
    const page: ExternalOperationPage = {
      offset: 0, limit: 50, total: 2, items: [
        { id: 2, system: 'publer', operation: 'schedule', outcome: 'FAILURE', started_at: '2026-11-02T09:00:00Z', duration_ms: 1200,
          trace_id: 'abc123', error_type: 'java.net.SocketTimeoutException', error_message: 'Publer no respondió' },
        { id: 1, system: 'postgres', operation: 'start', outcome: 'SUCCESS', started_at: '2026-11-02T08:00:00Z', duration_ms: 900 },
      ],
    };
    const list = vi.spyOn(api, 'externalOperations').mockResolvedValue(page);
    render(<ExternalOperations onClose={() => {}} />);
    expect(await screen.findByRole('cell', { name: 'Publer' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'PostgreSQL' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Publer' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Ver' }));
    expect(screen.getByText('Publer no respondió')).toBeInTheDocument();
    expect(screen.getByText(/abc123/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Fallidas' }));
    await waitFor(() => expect(list).toHaveBeenLastCalledWith({ system: undefined, outcome: 'FAILURE', offset: 0, limit: 50 }));
  });
});

describe('Modo real vacío', () => {
  it('invita a dar de alta la primera marca', () => {
    const onRegister = vi.fn();
    render(<World data={{ ...snapshot, demo: false, brands: [], promotions: [], tasks: [], alerts: [] }} sel={{ type: null, id: null }}
      onStation={() => {}} onTask={() => {}} onTaskAction={() => {}} onArmory={() => {}} onPromotion={() => {}} onRegister={onRegister} />);
    expect(screen.getByText('Da de alta tu primera marca')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Marcas y promociones →' }));
    expect(onRegister).toHaveBeenCalled();
  });
});

describe('Altas', () => {
  it('crea una marca con el color elegido', async () => {
    const create = vi.spyOn(api, 'createBrand').mockResolvedValue({ id: 'litoques', name: 'Litoques', handle: '@litoques', color: '#C98A1B', demo: false });
    const onSaved = vi.fn();
    render(<BrandForm onCancel={() => {}} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Litoques' } });
    fireEvent.change(screen.getByLabelText('Cuenta'), { target: { value: '@litoques' } });
    fireEvent.change(screen.getByLabelText('Color de la marca'), { target: { value: '#c98a1b' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(create).toHaveBeenCalledWith({ name: 'Litoques', handle: '@litoques', color: '#C98A1B' });
  });

  it('edita una promoción con sus hitos y muestra el error del backend', async () => {
    const update = vi.spyOn(api, 'updatePromotion').mockRejectedValue(new Error('La promoción no puede terminar antes de empezar'));
    render(<PromotionForm data={snapshot} promotion={snapshot.promotions[0]} onCancel={() => {}} onSaved={() => {}} />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Añadir' })[0]);
    fireEvent.change(screen.getByLabelText('Hito'), { target: { value: 'Días gratis' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('no puede terminar antes de empezar');
    expect(update).toHaveBeenCalledWith('p', expect.objectContaining({
      brand_id: 'b', name: 'Lanzamiento del poemario', milestones: [{ starts_on: '2026-10-27', label: 'Días gratis' }],
    }));
  });
});
