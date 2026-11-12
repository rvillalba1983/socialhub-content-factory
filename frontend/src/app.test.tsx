import { act, fireEvent, render, renderHook, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { api } from './api';
import { Backups, Halted, ShutdownButton } from './Backups';
import { Claude } from './Claude';
import { ControlCenter } from './ControlCenter';
import { ExternalOperations } from './ExternalOperations';
import { FolderRoles } from './Folders';
import { InstagramAccountCard, InstagramKeyCard } from './Instagram';
import { MemoryCard } from './Memory';
import { PublicationEditor } from './PublicationEditor';
import { PromotionMetricsTab } from './PromotionMetrics';
import { PubloraCard } from './Publora';
import { PublerCard } from './Publer';
import { BrandForm, PromotionForm, Registration } from './Registration';
import { Factory } from './Factory';
import { useFactory } from './useFactory';
import { NextSteps, savedMessage, toastDuration, withoutBrandNotices } from './Recommendations';
import type { PromotionMetrics, Recommendation } from './api/schema';
import { PieceOrders } from './PieceOrders';
import type { ExternalOperationPage, Snapshot, Station, Task } from './model';
import { TaskPanel } from './Panel';
import { TodayCard, World, openAlert } from './World';
import type { Publication } from './model';

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
  recommendations: [],
};

afterEach(() => vi.restoreAllMocks());
// El panel de calidad de cada publicación pide sus avisos: por defecto, ninguno
beforeEach(() => { vi.spyOn(api, 'publicationQuality').mockResolvedValue({ issues: [], open: 0 }); });

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

  it('no repite claves con varias alertas de «sin vía para publicar» (todas con el mismo destino)', () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    const alert = (brand: string) => ({ kind: 'NO_PUBLISHING_ROUTE', target_type: 'ARMORY', target_id: 'publora', station_id: 'pub',
      title: `No hay forma de publicar ${brand}`, detail: '1 publicación' }) as unknown as Snapshot['alerts'][number];
    render(<World data={{ ...snapshot, alerts: [alert('El DeLito'), alert('Litoques')] }} sel={{ type: null, id: null }}
      onStation={() => {}} onTask={() => {}} onTaskAction={() => {}} onArmory={() => {}} onPromotion={() => {}} onRegister={() => {}} />);
    expect(screen.getByText('No hay forma de publicar El DeLito')).toBeInTheDocument();
    expect(screen.getByText('No hay forma de publicar Litoques')).toBeInTheDocument();
    expect(error.mock.calls.filter(c => String(c[0]).includes('same key'))).toHaveLength(0);
  });

  describe('cuenta de Publer llena', () => {
    const full = (action: boolean) => ({ kind: 'BLOCKED', target_type: 'STATION', target_id: 'pub', station_id: 'pub',
      title: 'La cuenta «Lito» de Publer está llena: 3 publicaciones en espera',
      detail: action ? '2 pueden pasar ya a Publora' : 'Publora no está configurada para Litoques/TikTok',
      ...(action ? { action: { type: 'PUBLER_TO_PUBLORA', label: 'Pasar a Publora', account_name: 'Lito', platform: 'TIKTOK' } } : {}) }) as unknown as Snapshot['alerts'][number];
    const draw = (a: Snapshot['alerts'][number], onAlertAction = vi.fn()) => {
      render(<World data={{ ...snapshot, alerts: [a] }} sel={{ type: null, id: null }} onStation={() => {}} onTask={() => {}}
        onTaskAction={() => {}} onArmory={() => {}} onAlertAction={onAlertAction} onPromotion={() => {}} onRegister={() => {}} />);
      return onAlertAction;
    };

    it('muestra la fila con el botón «Pasar a Publora» y lo pulsa con la cuenta y la red', () => {
      const onAction = draw(full(true));
      expect(screen.getByText('La cuenta «Lito» de Publer está llena: 3 publicaciones en espera')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Pasar a Publora' }));
      expect(onAction).toHaveBeenCalledWith({ type: 'PUBLER_TO_PUBLORA', label: 'Pasar a Publora', account_name: 'Lito', platform: 'TIKTOK' });
    });

    it('sin Publora para ninguna, la fila lo dice y no hay botón', () => {
      draw(full(false));
      expect(screen.getByText(/Publora no está configurada para Litoques\/TikTok/)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Pasar a Publora' })).toBeNull();
    });
  });

  describe('tarjeta «Necesita atención» con muchos avisos', () => {
    const many = (n: number) => Array.from({ length: n }, (_, i) => ({ kind: 'BLOCKED', target_type: 'ARMORY', target_id: `a${i}`, station_id: 'pub',
      title: `Aviso número ${i} con un título bastante largo`, detail: `Detalle ${i}` }) as unknown as Snapshot['alerts'][number]);
    const draw = (n: number) => render(<World data={{ ...snapshot, alerts: many(n) }} sel={{ type: null, id: null }}
      onStation={() => {}} onTask={() => {}} onTaskAction={() => {}} onArmory={() => {}} onPromotion={() => {}} onRegister={() => {}} />);
    const card = () => screen.getByRole('region', { name: 'Necesita tu atención' });

    it('con 2 avisos o menos no hay «Ver N más»', () => {
      draw(2);
      expect(card().querySelectorAll('.aitem')).toHaveLength(2);
      expect(screen.queryByRole('button', { name: /Ver .* más/ })).not.toBeInTheDocument();
    });

    it('con 6 enseña 2; «Ver 4 más» abre la lista entera y «Ver menos» la pliega', () => {
      draw(6);
      expect(card().querySelectorAll('.aitem')).toHaveLength(2);
      fireEvent.click(screen.getByRole('button', { name: 'Ver 4 más' }));
      expect(card().querySelectorAll('.aitem')).toHaveLength(6);
      expect(card().className).toContain('open');
      fireEvent.click(screen.getByRole('button', { name: 'Ver menos' }));
      expect(card().querySelectorAll('.aitem')).toHaveLength(2);
    });

    it('el primer clic despliega el aviso (uno a la vez) y el segundo abre su destino', () => {
      const onArmory = vi.fn();
      render(<World data={{ ...snapshot, alerts: many(3) }} sel={{ type: null, id: null }}
        onStation={() => {}} onTask={() => {}} onTaskAction={() => {}} onArmory={onArmory} onPromotion={() => {}} onRegister={() => {}} />);
      const items = () => [...card().querySelectorAll<HTMLElement>('.aitem')];
      expect(items()[0]).toHaveAttribute('title', 'Aviso número 0 con un título bastante largo. Detalle 0');
      expect(items()[0]).toHaveAttribute('aria-expanded', 'false');
      fireEvent.click(items()[0]);
      expect(onArmory).not.toHaveBeenCalled();
      expect(items()[0]).toHaveAttribute('aria-expanded', 'true');
      expect(card().querySelectorAll('.attn-row.full')).toHaveLength(1);
      // Desplegar otro pliega el anterior
      fireEvent.click(items()[1]);
      expect(items()[0]).toHaveAttribute('aria-expanded', 'false');
      expect(items()[1]).toHaveAttribute('aria-expanded', 'true');
      expect(onArmory).not.toHaveBeenCalled();
      // Segundo clic sobre el desplegado: navega
      fireEvent.click(items()[1]);
      expect(onArmory).toHaveBeenCalledWith('a1');
      expect(items()[1]).toHaveAttribute('aria-expanded', 'false');
    });
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

describe('Encargar piezas', () => {
  it('lista los spec.json de la carpeta vigilada y encarga uno', async () => {
    const promotion = { ...snapshot.promotions[0], resources: [{ kind: 'DRIVE' as const, location: 'G:/DeLito', description: '' }] };
    vi.spyOn(api, 'pieceSpecs').mockResolvedValue({ folder: 'G:/DeLito/SocialHub/encargos', items: [
      { path: 'G:/DeLito/SocialHub/encargos/semana2.json', name: 'semana2.json', modified_at: '2026-11-02T09:00:00Z' },
      { path: 'G:/DeLito/SocialHub/encargos/semana1.json', name: 'semana1.json', modified_at: '2026-10-26T09:00:00Z', task_id: 't-1' },
    ] });
    const order = vi.spyOn(api, 'orderPieces').mockResolvedValue({ task_id: 't-2', spec_path: 'G:/DeLito/SocialHub/encargos/semana2.json' });
    const onTask = vi.fn();
    render(<PieceOrders promotions={[promotion]} onOrdered={() => {}} onTask={onTask} />);
    expect(await screen.findByText('semana2.json')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver tarea' })).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Encargar' })[0]);
    await waitFor(() => expect(onTask).toHaveBeenCalledWith('t-2'));
    expect(order).toHaveBeenCalledWith('p', 'G:/DeLito/SocialHub/encargos/semana2.json');
  });

  it('pide una carpeta si ninguna promoción la tiene', () => {
    render(<PieceOrders promotions={snapshot.promotions} onOrdered={() => {}} onTask={() => {}} />);
    expect(screen.getByText(/añade a una promoción una carpeta/)).toBeInTheDocument();
  });
});

describe('Carpetas de Drive', () => {
  it('en la promoción solo guarda las excepciones y muestra lo heredado de la marca', async () => {
    vi.spyOn(api, 'promotionFolders').mockResolvedValue({ items: [
      { role: 'MATERIAL', location: 'G:/DeLito/material', origin: 'BRAND', available: true },
      { role: 'ORDERS', location: 'G:/DeLito/encargos', origin: 'PROMOTION', available: false },
    ] });
    const save = vi.spyOn(api, 'savePromotionFolders').mockResolvedValue({ items: [] });
    render(<FolderRoles owner="promotion" id="p" />);
    const material = await screen.findByLabelText('Carpeta de Material');
    expect(material).toHaveValue('');
    expect(material).toHaveAttribute('placeholder', 'G:/DeLito/material');
    expect(screen.getByLabelText('Carpeta de Encargos')).toHaveValue('G:/DeLito/encargos');
    expect(screen.getByText('No disponible')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Carpeta de Informes'), { target: { value: '  G:/DeLito/informes ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar carpetas' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith('p', [
      { role: 'REPORTS', location: 'G:/DeLito/informes' }, { role: 'ORDERS', location: 'G:/DeLito/encargos' },
    ]));
  });
});

describe('Publicaciones', () => {
  const publication: Publication = {
    id: '2026-11-10-instagram', promotion_id: 'p', platform: 'INSTAGRAM', type: 'Carrusel', title: 'Poema 1',
    scheduled_on: '2026-11-10', scheduled_time: '19:00', timezone: 'Europe/Madrid', status: 'APPROVED', route: 'Publer', demo: false,
  };

  it('da de alta una publicación con el estado inicial elegido', async () => {
    const create = vi.spyOn(api, 'createPublication').mockResolvedValue({ ...publication, status: 'DRAFT' });
    const onSaved = vi.fn();
    render(<PublicationEditor promotionId="p" onClose={() => {}} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText('Tipo'), { target: { value: 'Carrusel' } });
    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Poema 1' } });
    fireEvent.change(screen.getByLabelText('Día'), { target: { value: '2026-11-10' } });
    fireEvent.change(screen.getByLabelText('Empieza como'), { target: { value: 'DRAFT' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(create).toHaveBeenCalledWith('p', expect.objectContaining({
      platform: 'INSTAGRAM', type: 'Carrusel', title: 'Poema 1', scheduled_on: '2026-11-10', scheduled_time: '19:00',
      initial_status: 'DRAFT', note: undefined,
    }));
  });

  it('solo ofrece los cambios de estado permitidos y nunca «Publicada»', async () => {
    vi.spyOn(api, 'publicationFiles').mockResolvedValue({ items: [
      { path: 'G:/DeLito/Semana 2/poema_1.jpg', origin: 'SPEC', added_at: '2026-11-02T09:00:00Z', available: true },
    ] });
    const change = vi.spyOn(api, 'changePublicationStatus').mockRejectedValue(new Error('al menos a 6 horas de ahora'));
    render(<PublicationEditor promotionId="p" publication={publication} onClose={() => {}} onSaved={() => {}} />);
    expect(await screen.findByText('G:/DeLito/Semana 2/poema_1.jpg')).toBeInTheDocument();
    expect(screen.getByText(/Del spec/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Publicada/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Pasar a «Programada»' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('6 horas');
    expect(change).toHaveBeenCalledWith('2026-11-10-instagram', 'SCHEDULED');
  });
});

describe('Publicación programada', () => {
  const publication: Publication = {
    id: 'p-3', promotion_id: 'p', platform: 'INSTAGRAM', type: 'Historia', title: 'Historia del jueves',
    scheduled_on: '2026-11-12', scheduled_time: '11:00', timezone: 'Europe/Madrid', status: 'PUBLISHING', route: 'Publer',
    flag: 'Posible duplicado: se programó tarde', demo: false,
  };

  it('muestra la vía, el LEEME y el historial; la incidencia se da por resuelta', async () => {
    vi.spyOn(api, 'publicationFiles').mockResolvedValue({ items: [] });
    vi.spyOn(api, 'publicationQuality').mockResolvedValue({ open: 0, issues: [] });
    vi.spyOn(api, 'publishJob').mockResolvedValue({
      publication_id: 'p-3', status: 'MANUAL', status_label: 'Publicar a mano', route: 'A mano', attempts: 2,
      routes: [{ id: 'PUBLER', label: 'Publer', configured: true }, { id: 'PUBLORA', label: 'Publora', configured: false }],
      manual_file: 'G:/Aprobados/LEEME-publicar-a-mano-p-3.txt', last_error: 'Claude no lo hizo a tiempo',
      log: ['03/11 09:00 · Programada por Raúl: empieza la publicación'],
    });
    const clear = vi.spyOn(api, 'clearPublicationFlag').mockResolvedValue(undefined);
    const onSaved = vi.fn();
    render(<PublicationEditor promotionId="p" publication={publication} onClose={() => {}} onSaved={onSaved} />);
    expect(await screen.findByText('Publicar a mano')).toBeInTheDocument();
    expect(screen.getByText('A mano')).toBeInTheDocument();
    expect(screen.getByText(/Publer → Publora \(no configurada\)/)).toBeInTheDocument();
    expect(screen.getByText('G:/Aprobados/LEEME-publicar-a-mano-p-3.txt')).toBeInTheDocument();
    expect(screen.getByText(/Programada por Raúl/)).toBeInTheDocument();
    // Publicándose no se cambia nada
    expect(screen.queryByRole('button', { name: 'Guardar' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Añadir archivo' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Resuelto' }));
    await waitFor(() => expect(clear).toHaveBeenCalledWith('p-3'));
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ id: 'p-3', flag: undefined }), 'Incidencia resuelta'));
  });
});

describe('Resumen de hoy', () => {
  it('cuenta lo publicado hoy, lo pendiente y lo de mañana', () => {
    const pub = (id: string, day: string, status: Publication['status']): Publication => ({
      id, promotion_id: 'p', platform: 'INSTAGRAM', type: 'Reel', title: id, scheduled_on: day, scheduled_time: '19:00',
      timezone: 'Europe/Madrid', status, route: 'Publer', demo: false,
    });
    render(<TodayCard data={{ ...snapshot, now: '2026-11-03T10:00:00+01:00', tasks: [], publications: [
      pub('a', '2026-11-03', 'PUBLISHED'), pub('b', '2026-11-03', 'SCHEDULED'), pub('c', '2026-11-03', 'PUBLISH_ERROR'),
      pub('d', '2026-11-04', 'SCHEDULED'), pub('e', '2026-11-05', 'SCHEDULED'),
    ] }} />);
    const value = (label: string) => screen.getByText(label).nextSibling?.textContent;
    expect(value('Publicadas hoy')).toBe('1');
    expect(value('Por publicar hoy')).toBe('1');
    expect(value('Para mañana')).toBe('1');
    expect(screen.getByText('1 publicación de hoy con error')).toBeInTheDocument();
  });
});

describe('Métricas', () => {
  it('muestra las cifras leídas y «Sin dato» en lo que no se vio', async () => {
    const publication: Publication = {
      id: 'p-9', promotion_id: 'p', platform: 'INSTAGRAM', type: 'Historia', title: 'Historia publicada',
      scheduled_on: '2026-11-12', scheduled_time: '11:00', timezone: 'Europe/Madrid', status: 'PUBLISHED', route: 'Publer', demo: false,
    };
    vi.spyOn(api, 'publicationFiles').mockResolvedValue({ items: [] });
    vi.spyOn(api, 'publicationQuality').mockResolvedValue({ open: 0, issues: [] });
    vi.spyOn(api, 'publishJob').mockResolvedValue({ publication_id: 'p-9', status: 'PUBLISHED', status_label: 'Publicada', attempts: 1, routes: [], log: [] });
    vi.spyOn(api, 'publicationMetrics').mockResolvedValue({
      publication_id: 'p-9', status: 'DONE', requested_at: '2026-11-19T10:00:00Z', measured_at: '2026-11-19T12:00:00Z', attempts: 1,
      reach: 1812, likes: 64, comments: 5, saves: 9, shares: 3, note: 'Historia: sin reproducciones',
    });
    render(<PublicationEditor promotionId="p" publication={publication} onClose={() => {}} onSaved={() => {}} />);
    expect(await screen.findByText('64')).toBeInTheDocument();
    expect(screen.getByText('Alcance')).toBeInTheDocument();
    expect(screen.getByText('Sin dato')).toBeInTheDocument();
    expect(screen.getByText(/Historia: sin reproducciones/)).toBeInTheDocument();
  });
});

describe('Pestaña Métricas de la promoción', () => {
  const metrics = (o: Partial<PromotionMetrics> = {}): PromotionMetrics => ({
    measured: 0, pending: 0,
    by_network: [{ network: 'INSTAGRAM', publications: 0 }, { network: 'TIKTOK', publications: 0 }, { network: 'TOTAL', publications: 0 }],
    by_format: [], ...o,
  });

  it('sin nada medido explica que se lee a los 7 días y cuántas esperan', async () => {
    vi.spyOn(api, 'promotionMetrics').mockResolvedValue(metrics({ pending: 3 }));
    render(<PromotionMetricsTab promotionId="p" />);
    expect(await screen.findByText(/Las métricas se leen a los 7 días de publicar/)).toBeInTheDocument();
    expect(screen.getByText(/3 publicaciones/)).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('enseña totales por red y medias por formato, con «pocos datos» solo en grupos pequeños', async () => {
    vi.spyOn(api, 'promotionMetrics').mockResolvedValue(metrics({
      measured: 7, pending: 1,
      by_network: [
        { network: 'INSTAGRAM', publications: 6, views: 1500, reach: 320, likes: 60 },
        { network: 'TIKTOK', publications: 1, views: 900 },
        { network: 'TOTAL', publications: 7, views: 2400, reach: 320, likes: 60 },
      ],
      by_format: [
        { type: 'Carrusel', network: 'INSTAGRAM', publications: 1, low_sample: true, reach: 70 },
        { type: 'Reel', network: 'INSTAGRAM', publications: 5, low_sample: false, views: 300, reach: 50, likes: 10 },
      ],
    }));
    render(<PromotionMetricsTab promotionId="p" />);
    const totals = await screen.findByRole('table', { name: 'Totales por red' });
    expect(within(totals).getByText('Instagram')).toBeInTheDocument();
    expect(within(totals).getByText('TikTok')).toBeInTheDocument();
    expect(within(totals).getByText('Total')).toBeInTheDocument();
    expect(within(totals).getByText('2.400')).toBeInTheDocument();
    for (const label of ['Reproducciones', 'Alcance', 'Me gusta', 'Comentarios', 'Guardados', 'Compartidos'])
      expect(within(totals).getByText(label)).toBeInTheDocument();
    for (const gone of ['Impresiones', 'Interacciones', 'Seguidores ganados', 'Clics en el enlace'])
      expect(screen.queryByText(gone)).toBeNull();
    expect(within(totals).getAllByText('Sin dato').length).toBeGreaterThan(0);
    const formats = screen.getByRole('table', { name: 'Comparación por formato y red' });
    expect(within(formats).getAllByText('pocos datos')).toHaveLength(1);
    const reel = within(formats).getByText('Reel').closest('tr')!;
    expect(within(reel).getByText('300')).toBeInTheDocument();
    expect(within(reel).queryByText('pocos datos')).toBeNull();
  });

  it('muestra el error si el backend falla', async () => {
    vi.spyOn(api, 'promotionMetrics').mockRejectedValue(new Error('boom'));
    render(<PromotionMetricsTab promotionId="p" />);
    expect(await screen.findByRole('alert')).toHaveTextContent('boom');
  });
});

describe('Calendario con lo de fuera', () => {
  it('muestra lo programado fuera de SocialHub y cuándo se leyó cada fuente', async () => {
    const ext = vi.spyOn(api, 'externalCalendar').mockResolvedValue({
      items: [
        { source: 'PUBLER', source_label: 'Publer', platform: 'INSTAGRAM', at: '2026-10-28T18:00:00Z', status: 'SCHEDULED', text: 'Reel del miércoles' },
        { source: 'PUBLORA', source_label: 'Publora', platform: 'INSTAGRAM', at: '2026-10-29T18:00:00Z', status: 'SCHEDULED', text: 'Ya está en SocialHub', publication_id: 'x' },
        { source: 'NETWORK', source_label: 'La red', platform: 'TIKTOK', at: '2026-10-27T11:00:00Z', status: 'PUBLISHED', url: 'https://www.tiktok.com/@litoques/video/1' },
      ],
      sources: [
        { source: 'PUBLORA', label: 'Publora', synced_at: '2026-10-26T09:00:00Z' },
        { source: 'PUBLER', label: 'Publer', error: 'Publer pide iniciar sesión' },
      ],
    });
    render(<ControlCenter p={snapshot.promotions[0]} data={snapshot} initialTab="calendario"
      highlight={null} onClose={() => {}} onTask={() => {}} onEdit={() => {}} onMessage={() => {}} />);
    expect(await screen.findByText('Reel del miércoles')).toBeInTheDocument();
    expect(ext).toHaveBeenCalledWith('p', '2026-10-26', '2026-11-01');
    expect(screen.getByText('19:00')).toBeInTheDocument();
    expect(screen.queryByText('Ya está en SocialHub')).toBeNull();
    expect(screen.getByRole('link', { name: /La red · TikTok/ })).toHaveAttribute('href', 'https://www.tiktok.com/@litoques/video/1');
    expect(screen.getByText(/falló: Publer pide iniciar sesión/)).toBeInTheDocument();
  });
});

describe('Publora', () => {
  it('guarda la clave sin mostrarla y elige la cuenta de cada red', async () => {
    const connections = [
      { id: 'instagram-111', platform: 'instagram', label: 'instagram · elde_lito', usable: true },
      { id: 'tiktok-222', platform: 'tiktok', label: 'tiktok · litoques', usable: false },
    ];
    const status = vi.spyOn(api, 'publora')
      .mockResolvedValueOnce({ configured: false, connections: [] })
      .mockResolvedValue({ configured: true, key_hint: '…6789', connections });
    vi.spyOn(api, 'brandPubloraAccounts').mockResolvedValue({ items: [], dismissed: false });
    const saveKey = vi.spyOn(api, 'savePubloraKey').mockResolvedValue({ configured: true, key_hint: '…6789', connections });
    const saveAccounts = vi.spyOn(api, 'saveBrandPubloraAccounts').mockResolvedValue({ items: [], dismissed: false });
    render(<PubloraCard brandId="el-delito" />);
    expect(await screen.findByText('Sin clave')).toBeInTheDocument();
    const input = screen.getByLabelText('Clave de la API de Publora');
    expect(input).toHaveAttribute('type', 'password');
    fireEvent.change(input, { target: { value: ' sk_123.abcdef0123456789 ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Comprobar y guardar' }));
    await waitFor(() => expect(saveKey).toHaveBeenCalledWith('sk_123.abcdef0123456789'));
    expect(await screen.findByText('Clave …6789')).toBeInTheDocument();
    expect(status).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('option', { name: /tiktok · litoques \(hay que volver a conectarla/ })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Cuenta de Publora para Instagram'), { target: { value: 'instagram-111' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cuentas' }));
    await waitFor(() => expect(saveAccounts).toHaveBeenCalledWith('el-delito', [{ platform: 'INSTAGRAM', connection_id: 'instagram-111' }]));
  });
});

describe('Cuenta en Publer', () => {
  it('muestra las cuentas guardadas, con ejemplos de ayuda, y guarda solo las que tienen nombre', async () => {
    vi.spyOn(api, 'brandPublerAccounts').mockResolvedValue({ items: [{ platform: 'TIKTOK', account_name: 'Lito · Litoques · El DeLito' }] });
    const save = vi.spyOn(api, 'saveBrandPublerAccounts').mockResolvedValue({
      items: [{ platform: 'INSTAGRAM', account_name: 'Litoques' }, { platform: 'TIKTOK', account_name: 'Lito · Litoques · El DeLito' }],
    });
    const onSaved = vi.fn();
    render(<PublerCard brandId="litoques" onSaved={onSaved} />);
    const tiktok = await screen.findByLabelText('Cuenta de Publer para TikTok');
    expect(tiktok).toHaveValue('Lito · Litoques · El DeLito');
    const instagram = screen.getByLabelText('Cuenta de Publer para Instagram');
    expect(instagram).toHaveValue('');
    expect(instagram).toHaveAttribute('placeholder', 'Por ejemplo: «Litoques»');
    expect(tiktok).toHaveAttribute('placeholder', 'Por ejemplo: «Lito · Litoques · El DeLito»');
    fireEvent.change(instagram, { target: { value: ' Litoques ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cuentas' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith('litoques', [
      { platform: 'INSTAGRAM', account_name: 'Litoques' }, { platform: 'TIKTOK', account_name: 'Lito · Litoques · El DeLito' }]));
    expect(onSaved).toHaveBeenCalledWith('Cuentas de Publer guardadas');
  });

  it('vaciar un campo quita la cuenta de esa red', async () => {
    vi.spyOn(api, 'brandPublerAccounts').mockResolvedValue({ items: [{ platform: 'INSTAGRAM', account_name: 'Litoques' }] });
    const save = vi.spyOn(api, 'saveBrandPublerAccounts').mockResolvedValue({ items: [] });
    render(<PublerCard brandId="litoques" />);
    fireEvent.change(await screen.findByLabelText('Cuenta de Publer para Instagram'), { target: { value: '  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cuentas' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith('litoques', []));
  });
});

describe('Instagram · API oficial', () => {
  it('guarda la clave sin mostrarla, la comprueba y enseña el límite de cada marca', async () => {
    vi.spyOn(api, 'instagram').mockResolvedValueOnce({ configured: false })
      .mockResolvedValue({ configured: true, key_hint: '…6789' });
    const save = vi.spyOn(api, 'saveInstagramKey').mockResolvedValue({ configured: true, key_hint: '…6789' });
    const check = vi.spyOn(api, 'checkInstagram').mockResolvedValue({
      ok: false, name: 'Usuario del sistema',
      brands: [{ brand_id: 'el-delito', brand_name: 'El DeLito', username: 'elde_lito', publishing_used: 3, publishing_total: 100 },
        { brand_id: 'litoques', brand_name: 'Litoques', error: 'Meta respondió 400: cuenta no encontrada' }],
    });
    const onChanged = vi.fn();
    render(<InstagramKeyCard onChanged={onChanged} />);
    expect(await screen.findByText('Sin clave')).toBeInTheDocument();
    const input = screen.getByLabelText('Clave de la API de Instagram');
    expect(input).toHaveAttribute('type', 'password');
    fireEvent.change(input, { target: { value: ' EAAB.clave.falsa.1234567 ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Comprobar y guardar' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith('EAAB.clave.falsa.1234567'));
    expect(await screen.findByText('Clave …6789')).toBeInTheDocument();
    expect(onChanged).toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Comprobar clave' }));
    await waitFor(() => expect(check).toHaveBeenCalled());
    const result = await screen.findByRole('status', { name: 'Resultado de la comprobación' });
    expect(within(result).getByText('Hay algo que revisar')).toBeInTheDocument();
    expect(within(result).getByText(/3 de 100 publicaciones en 24 h/)).toBeInTheDocument();
    expect(within(result).getByText(/cuenta no encontrada/)).toBeInTheDocument();
  });

  it('enseña el error de Meta si la clave no vale', async () => {
    vi.spyOn(api, 'instagram').mockResolvedValue({ configured: false });
    vi.spyOn(api, 'saveInstagramKey').mockRejectedValue(new Error('No se ha guardado: Meta no acepta la clave'));
    render(<InstagramKeyCard />);
    await screen.findByText('Sin clave');
    fireEvent.change(screen.getByLabelText('Clave de la API de Instagram'), { target: { value: 'clave-falsa-123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Comprobar y guardar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Meta no acepta la clave');
  });

  it('el campo de la marca comprueba el identificador y enseña el @usuario', async () => {
    vi.spyOn(api, 'brandInstagramAccount').mockResolvedValue({});
    const save = vi.spyOn(api, 'saveBrandInstagramAccount')
      .mockResolvedValue({ ig_user_id: '17841400000000000', username: 'elde_lito' });
    const onSaved = vi.fn();
    render(<InstagramAccountCard brandId="el-delito" onSaved={onSaved} />);
    const field = await screen.findByLabelText('Cuenta de Instagram (API oficial)');
    fireEvent.change(field, { target: { value: ' 17841400000000000 ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Comprobar y guardar' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith('el-delito', '17841400000000000'));
    expect(await screen.findByText('@elde_lito')).toBeInTheDocument();
    expect(onSaved).toHaveBeenCalledWith('Cuenta de Instagram guardada · @elde_lito');
  });

  it('el campo de la marca enseña el error de Meta', async () => {
    vi.spyOn(api, 'brandInstagramAccount').mockResolvedValue({});
    vi.spyOn(api, 'saveBrandInstagramAccount').mockRejectedValue(new Error('No se ha guardado: Meta respondió 400'));
    render(<InstagramAccountCard brandId="el-delito" />);
    fireEvent.change(await screen.findByLabelText('Cuenta de Instagram (API oficial)'), { target: { value: '123456789' } });
    fireEvent.click(screen.getByRole('button', { name: 'Comprobar y guardar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Meta respondió 400');
  });
});

describe('No uso Publora', () => {
  it('la tarjeta de la marca descartada permite volver a activar Publora', async () => {
    vi.spyOn(api, 'publora').mockResolvedValue({ configured: false, connections: [] });
    const accounts = vi.spyOn(api, 'brandPubloraAccounts')
      .mockResolvedValueOnce({ items: [], dismissed: true })
      .mockResolvedValue({ items: [], dismissed: false });
    const set = vi.spyOn(api, 'setBrandPubloraDismissed').mockResolvedValue({ items: [], dismissed: false });
    const onSaved = vi.fn();
    render(<PubloraCard brandId="el-delito" onSaved={onSaved} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Volver a activar Publora' }));
    await waitFor(() => expect(set).toHaveBeenCalledWith('el-delito', false));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Volver a activar Publora' })).toBeNull());
    expect(accounts).toHaveBeenCalledTimes(2);
    expect(onSaved).toHaveBeenCalledWith('Publora vuelve a recomendarse para esta marca');
  });

  it('la tarjeta no ofrece reactivar si no está descartada', async () => {
    vi.spyOn(api, 'publora').mockResolvedValue({ configured: false, connections: [] });
    vi.spyOn(api, 'brandPubloraAccounts').mockResolvedValue({ items: [], dismissed: false });
    render(<PubloraCard brandId="el-delito" />);
    expect(await screen.findByText('Sin clave')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Volver a activar Publora' })).toBeNull();
  });
});

describe('Avisos', () => {
  it('un aviso de publicación abre la publicación', () => {
    const go = { onTask: vi.fn(), onStation: vi.fn(), onPublication: vi.fn() };
    openAlert({ kind: 'FAILED', target_type: 'PUBLICATION', target_id: '2026-11-12-instagram', station_id: 'pub', title: 'Error', detail: 'x' }, go);
    expect(go.onPublication).toHaveBeenCalledWith('2026-11-12-instagram');
    expect(go.onStation).not.toHaveBeenCalled();
  });

  it('un aviso de Drive abre la armería', () => {
    const go = { onTask: vi.fn(), onStation: vi.fn(), onArmory: vi.fn() };
    openAlert({ kind: 'BLOCKED', target_type: 'ARMORY', target_id: 'google-drive', station_id: 'gen', title: 'Drive', detail: 'No disponible' }, go);
    expect(go.onArmory).toHaveBeenCalledWith('google-drive');
    expect(go.onStation).not.toHaveBeenCalled();
  });
});

describe('Encargos a Claude', () => {
  it('guarda el límite y encarga un trabajo; tokens y coste figuran como desconocidos', async () => {
    const settings = {
      daily_limit: 10, used_today: 3, models: [
        { type: 'RESEARCH' as const, label: 'Investigación', model: 'SONNET' as const, on_pc: false },
        { type: 'WEEKLY_BALANCE' as const, label: 'Balance semanal', model: 'OPUS' as const, on_pc: false },
        { type: 'PUBLER_SCHEDULE' as const, label: 'Programar en Publer', model: 'SONNET' as const, on_pc: true },
      ],
    };
    vi.spyOn(api, 'aiSettings').mockResolvedValue(settings);
    vi.spyOn(api, 'aiRequests').mockResolvedValue({ offset: 0, limit: 50, total: 1, items: [
      { id: 'r1', task_id: 't-9', brand_id: 'b', type: 'RESEARCH', model: 'OPUS', attempt: 3, status: 'FAILED',
        request_path: 'G:/Encargos/para-claude/t-9-3.json', sent_at: '2026-11-02T09:40:00Z', duration_ms: 125000, error: 'Sin acceso a la web' },
    ] });
    const save = vi.spyOn(api, 'saveAiSettings').mockResolvedValue({ ...settings, daily_limit: 5 });
    const order = vi.spyOn(api, 'orderFromClaude').mockResolvedValue({ task_id: 't-10' });
    const onTask = vi.fn();
    render(<Claude data={snapshot} onClose={() => {}} onTask={onTask} onMessage={() => {}} />);
    expect(await screen.findByText('Sin acceso a la web')).toBeInTheDocument();
    expect(screen.getByText('2 min 5 s')).toBeInTheDocument();
    expect(screen.getAllByText('desconocido')).toHaveLength(2);
    expect(screen.getByText(/Encargos al día \(hoy: 3\)/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Encargos al día/), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar ajustes' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith({ daily_limit: 5, models: [
      { type: 'RESEARCH', model: 'SONNET' }, { type: 'WEEKLY_BALANCE', model: 'OPUS' }, { type: 'PUBLER_SCHEDULE', model: 'SONNET' },
    ] }));
    // Los encargos con Chrome en el PC no se piden a mano
    expect(screen.getByText(/con Chrome en tu PC; no cuenta en el límite/)).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Programar en Publer/ })).toBeNull();

    fireEvent.change(screen.getByLabelText('Qué tiene que hacer'), { target: { value: 'Salas de poesía' } });
    fireEvent.change(screen.getByLabelText('Datos de entrada (uno por línea)'), { target: { value: 'Granada\n\nJaén' } });
    fireEvent.click(screen.getByRole('button', { name: 'Encargar a Claude' }));
    await waitFor(() => expect(onTask).toHaveBeenCalledWith('t-10'));
    expect(order).toHaveBeenCalledWith('p', { type: 'RESEARCH', title: undefined, goal: 'Salas de poesía', inputs: ['Granada', 'Jaén'] });
  });
});

describe('Textos de una publicación', () => {
  it('muestra los textos que llegan de Claude y los pide para esa publicación', async () => {
    const publication: Publication = {
      id: '2026-11-10-instagram', promotion_id: 'p', platform: 'INSTAGRAM', type: 'Carrusel', title: 'Poema 1',
      scheduled_on: '2026-11-10', scheduled_time: '19:00', timezone: 'Europe/Madrid', status: 'DRAFT', route: 'Publer', demo: false,
    };
    vi.spyOn(api, 'publicationFiles').mockResolvedValue({ items: [] });
    const order = vi.spyOn(api, 'orderFromClaude').mockResolvedValue({ task_id: 't-1' });
    const { rerender } = render(<PublicationEditor promotionId="p" publication={publication} onClose={() => {}} onSaved={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: 'Pedir textos a Claude' }));
    await waitFor(() => expect(order).toHaveBeenCalledWith('p', expect.objectContaining({ type: 'CAPTIONS', inputs: ['2026-11-10-instagram'] })));
    expect(await screen.findByText(/llegarán aquí/)).toBeInTheDocument();

    rerender(<PublicationEditor promotionId="p" publication={{ ...publication, caption: 'Hola', hashtags: '#poesia' }}
      onClose={() => {}} onSaved={() => {}} />);
    expect(screen.getByLabelText(/^Texto/)).toHaveValue('Hola');
    expect(screen.getByLabelText('Hashtags')).toHaveValue('#poesia');
    expect(screen.getByText('Texto (4 / 2200)')).toBeInTheDocument();
  });
});

describe('Control de calidad', () => {
  it('muestra los avisos y acepta uno con su motivo', async () => {
    const publication: Publication = {
      id: 'p-1', promotion_id: 'p', platform: 'INSTAGRAM', type: 'Carrusel', title: 'Poema 1',
      scheduled_on: '2026-11-10', scheduled_time: '19:00', timezone: 'Europe/Madrid', status: 'IN_REVIEW', route: 'Publer', demo: false,
    };
    vi.spyOn(api, 'publicationFiles').mockResolvedValue({ items: [] });
    vi.spyOn(api, 'publicationQuality').mockResolvedValue({ open: 2, issues: [
      { key: 'FILES_MISSING', source: 'CODE', message: 'No tiene archivos asociados.', accepted: false },
      { key: 'CLAUDE:abc', source: 'CLAUDE', message: 'El tono es demasiado serio.', accepted: false },
    ] });
    const accept = vi.spyOn(api, 'acceptQualityIssue').mockResolvedValue({ open: 1, issues: [
      { key: 'FILES_MISSING', source: 'CODE', message: 'No tiene archivos asociados.', accepted: true, accepted_reason: 'Se sube en Publer' },
      { key: 'CLAUDE:abc', source: 'CLAUDE', message: 'El tono es demasiado serio.', accepted: false },
    ] });
    render(<PublicationEditor promotionId="p" publication={publication} onClose={() => {}} onSaved={() => {}} />);
    expect(await screen.findByText('El tono es demasiado serio.')).toBeInTheDocument();
    expect(screen.getByText(/2 avisos abiertos/)).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Aceptar' })[0]);
    fireEvent.change(screen.getByLabelText('Por qué lo aceptas'), { target: { value: 'Se sube en Publer' } });
    fireEvent.click(screen.getByRole('button', { name: 'Aceptar aviso' }));
    await waitFor(() => expect(accept).toHaveBeenCalledWith('p-1', 'FILES_MISSING', 'Se sube en Publer'));
    expect(await screen.findByText(/Aceptado: Se sube en Publer/)).toBeInTheDocument();
    expect(screen.getByText(/1 aviso abierto/)).toBeInTheDocument();
  });
});

describe('Memoria editorial', () => {
  const publication: Publication = {
    id: 'p-2', promotion_id: 'p', platform: 'INSTAGRAM', type: 'Reel', title: 'Reel sobrante',
    scheduled_on: '2026-11-10', scheduled_time: '19:00', timezone: 'Europe/Madrid', status: 'DRAFT', route: 'Publer',
    caption: 'Texto de Claude', demo: false,
  };

  it('pide el motivo al cancelar y al descartar el texto', async () => {
    vi.spyOn(api, 'publicationFiles').mockResolvedValue({ items: [] });
    const change = vi.spyOn(api, 'changePublicationStatus').mockResolvedValue({ ...publication, status: 'CANCELLED' });
    const discard = vi.spyOn(api, 'rejectPublicationText').mockResolvedValue({ ...publication, caption: undefined });
    render(<PublicationEditor promotionId="p" publication={publication} onClose={() => {}} onSaved={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: 'Pasar a «Cancelada»' }));
    expect(change).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'REPEATED' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar publicación' }));
    await waitFor(() => expect(change).toHaveBeenCalledWith('p-2', 'CANCELLED', 'REPEATED', undefined));

    fireEvent.click(screen.getByRole('button', { name: 'Descartar texto' }));
    fireEvent.change(screen.getByLabelText('Nota (opcional)'), { target: { value: 'Muy serio' } });
    // El primero abre el formulario; el último es el del formulario
    fireEvent.click(screen.getAllByRole('button', { name: 'Descartar texto' }).at(-1)!);
    await waitFor(() => expect(discard).toHaveBeenCalledWith('p-2', 'TONE', 'Muy serio'));
  });

  it('muestra la memoria de la marca y añade decisiones', async () => {
    const memory = {
      decisions: [{ id: 1, text: 'Nada de precios', created_at: '2026-11-03T09:00:00Z' }],
      rejections: [{ kind: 'TEXT' as const, reason: 'TONE' as const, title: 'Poema 1', note: 'Muy serio', created_at: '2026-11-03T09:00:00Z' }],
      corrections: [{ before_caption: 'Antes', after_caption: 'Después', created_at: '2026-11-03T09:00:00Z' }],
      published: [],
    };
    vi.spyOn(api, 'editorialMemory').mockResolvedValue(memory);
    const add = vi.spyOn(api, 'addDecision').mockResolvedValue({ id: 2, text: 'Máximo 2 reels', created_at: '2026-11-03T10:00:00Z' });
    render(<MemoryCard brandId="b" />);
    expect(await screen.findByText('Nada de precios')).toBeInTheDocument();
    expect(screen.getByText(/«Muy serio»/)).toBeInTheDocument();
    expect(screen.getByText(/Después/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Nueva decisión'), { target: { value: 'Máximo 2 reels' } });
    fireEvent.click(screen.getByRole('button', { name: 'Añadir' }));
    await waitFor(() => expect(add).toHaveBeenCalledWith('b', 'Máximo 2 reels'));
  });
});

describe('Propuestas de la semana', () => {
  it('se aprueban o se rechazan con motivo desde Publicaciones', async () => {
    const proposal: Publication = {
      id: '2030-11-18-instagram', promotion_id: 'p', platform: 'INSTAGRAM', type: 'Carrusel', title: 'Tres versos',
      scheduled_on: '2030-11-18', scheduled_time: '19:00', timezone: 'Europe/Madrid', status: 'PROPOSAL', route: 'Publer',
      note: 'Idea: tres versos', demo: false,
    };
    const change = vi.spyOn(api, 'changePublicationStatus').mockResolvedValue(proposal);
    const order = vi.spyOn(api, 'orderFromClaude').mockResolvedValue({ task_id: 't-1' });
    render(<ControlCenter p={snapshot.promotions[0]} data={{ ...snapshot, publications: [proposal] }} initialTab="publicaciones"
      highlight={null} onClose={() => {}} onTask={() => {}} onEdit={() => {}} onMessage={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: 'Propuestas' }));
    fireEvent.click(screen.getByRole('button', { name: 'Aprobar Tres versos' }));
    await waitFor(() => expect(change).toHaveBeenCalledWith('2030-11-18-instagram', 'IN_PRODUCTION'));

    fireEvent.click(screen.getByRole('button', { name: 'Rechazar Tres versos' }));
    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'OFF_BRAND' } });
    fireEvent.click(screen.getByRole('button', { name: 'Rechazar propuesta' }));
    await waitFor(() => expect(change).toHaveBeenCalledWith('2030-11-18-instagram', 'CANCELLED', 'OFF_BRAND', undefined));

    fireEvent.click(screen.getByRole('button', { name: 'Pedir propuestas a Claude' }));
    await waitFor(() => expect(order).toHaveBeenCalledWith('p', expect.objectContaining({ type: 'WEEKLY_PROPOSALS' })));
  });
});

describe('Copias', () => {
  const list = {
    local_folder: '/datos/copias', folder_available: false, keep: 14,
    items: [{ name: 'socialhub-2026-11-08-150012-diaria.zip', created_at: '2026-11-08T15:00:12+01:00', size_bytes: 52000, kind: 'DAILY' as const, local: true }],
  };

  it('avisa si no hay carpeta, guarda la carpeta y hace una copia ahora', async () => {
    vi.spyOn(api, 'backups').mockResolvedValue(list);
    const save = vi.spyOn(api, 'saveBackupFolder').mockResolvedValue({ ...list, folder: 'G:\\Copias', folder_available: true });
    const create = vi.spyOn(api, 'createBackup').mockResolvedValue(list.items[0]);
    const onMessage = vi.fn();
    render(<Backups onClose={() => {}} onMessage={onMessage} onHalted={() => {}} />);
    expect(await screen.findByText(/las copias se guardan solo en este ordenador/)).toBeInTheDocument();
    expect(screen.getByText('socialhub-2026-11-08-150012-diaria.zip')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Carpeta \(por ejemplo/), { target: { value: 'G:\\Copias' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar carpeta' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith('G:\\Copias'));
    fireEvent.click(screen.getByRole('button', { name: 'Hacer copia ahora' }));
    await waitFor(() => expect(create).toHaveBeenCalled());
  });

  it('solo restaura al escribir el nombre de la copia y luego se para', async () => {
    vi.spyOn(api, 'backups').mockResolvedValue(list);
    const restore = vi.spyOn(api, 'restoreBackup').mockResolvedValue({ restored: list.items[0].name, safety_backup: 'x', halted_reason: 'Cierra y abre' });
    const onHalted = vi.fn();
    render(<Backups onClose={() => {}} onMessage={() => {}} onHalted={onHalted} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar…' }));
    const go = screen.getByRole('button', { name: 'Restaurar' });
    expect(go).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/escribe el nombre de la copia/), { target: { value: list.items[0].name } });
    expect(go).toBeEnabled();
    fireEvent.click(go);
    await waitFor(() => expect(restore).toHaveBeenCalledWith(list.items[0].name, list.items[0].name));
    expect(onHalted).toHaveBeenCalledWith('Cierra y abre');
    render(<Halted reason="Cierra y abre" />);
    expect(screen.getByRole('alertdialog', { name: 'SocialHub está parado' })).toHaveTextContent('Cierra y abre');
  });
});

describe('Cerrar SocialHub', () => {
  it('pide confirmación y luego cierra', async () => {
    const shutdown = vi.spyOn(api, 'shutdown').mockResolvedValue(undefined);
    const onClosed = vi.fn();
    render(<ShutdownButton onClosed={onClosed} />);
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar' }));
    expect(shutdown).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Sí, cerrar' }));
    await waitFor(() => expect(onClosed).toHaveBeenCalled());
  });

  it('se puede cerrar desde la pantalla de parado', async () => {
    vi.spyOn(api, 'shutdown').mockResolvedValue(undefined);
    render(<Halted reason="Copia restaurada" />);
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar SocialHub' }));
    expect(await screen.findByRole('alertdialog', { name: 'SocialHub cerrado' })).toBeInTheDocument();
  });
});

describe('Carpetas de la marca', () => {
  it('crea las carpetas desde una carpeta raíz', async () => {
    const { FolderRoles } = await import('./Folders');
    vi.spyOn(api, 'brandFolders').mockResolvedValue({ items: [] });
    const created = { items: [{ role: 'ORDERS' as const, location: 'G:\\SH\\Litoques\\Encargos', origin: 'BRAND' as const, available: true }] };
    const create = vi.spyOn(api, 'createBrandFolders').mockResolvedValue(created);
    const onSaved = vi.fn();
    render(<FolderRoles owner="brand" id="litoques" onSaved={onSaved} />);
    fireEvent.change(await screen.findByLabelText('Carpeta raíz'), { target: { value: 'G:\\SH\\Litoques' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear carpetas' }));
    await waitFor(() => expect(create).toHaveBeenCalledWith('litoques', 'G:\\SH\\Litoques'));
    expect(onSaved).toHaveBeenCalledWith('Carpetas creadas');
    expect(screen.getByLabelText('Carpeta de Encargos')).toHaveValue('G:\\SH\\Litoques\\Encargos');
  });

  it('las promociones no tienen el botón de crear', async () => {
    const { FolderRoles } = await import('./Folders');
    vi.spyOn(api, 'promotionFolders').mockResolvedValue({ items: [] });
    render(<FolderRoles owner="promotion" id="test" />);
    await screen.findByRole('button', { name: 'Guardar carpetas' });
    expect(screen.queryByRole('button', { name: 'Crear carpetas' })).toBeNull();
  });
});

const rec = (o: Partial<Recommendation> & Pick<Recommendation, 'id' | 'kind' | 'title'>): Recommendation => ({ ...o });
const three: Recommendation[] = [
  rec({ id: 'proposals-review:p', kind: 'PROPOSALS_TO_REVIEW', title: 'Tienes 3 propuestas de «Lanzamiento del poemario» para revisar',
    target: { screen: 'PROPOSALS', brand_id: 'b', promotion_id: 'p' } }),
  rec({ id: 'brand-folders:b', kind: 'BRAND_FOLDERS_MISSING', title: 'Crea las carpetas de El DeLito', target: { screen: 'BRAND_FOLDERS', brand_id: 'b' } }),
  rec({ id: 'orders-not-collected', kind: 'ORDERS_NOT_COLLECTED', title: 'No ha llegado el resultado de los encargos de las 9:40. Revisa la tarea programada' }),
];
const withRecs = (recommendations: Recommendation[]): Snapshot => ({ ...snapshot, recommendations });
const world = (data: Snapshot, onRecommendation = vi.fn()) => render(
  <>
    <NextSteps items={data.recommendations} onOpen={onRecommendation} />
    <World data={data} sel={{ type: null, id: null }} onStation={() => {}} onTask={() => {}} onTaskAction={() => {}}
      onArmory={() => {}} onPromotion={() => {}} onRegister={() => {}} />
  </>);

describe('Qué hacer ahora dentro de la ficha de una marca', () => {
  const publer = (brand: string, platform: string) => rec({ id: `publer-account:${brand}:${platform}`, kind: 'PUBLER_ACCOUNT_MISSING',
    title: `Indica la cuenta de Publer de ${brand} para ${platform}`, target: { screen: 'PUBLER', brand_id: brand } });
  const all: Recommendation[] = [
    ...three, publer('b', 'INSTAGRAM'), publer('b', 'TIKTOK'), publer('b', 'YOUTUBE'), publer('otra', 'INSTAGRAM'),
    rec({ id: 'publora:b', kind: 'PUBLORA_MISSING', title: 'Publora de b', target: { screen: 'PUBLORA', brand_id: 'b' } }),
    rec({ id: 'brand-folders:otra', kind: 'BRAND_FOLDERS_MISSING', title: 'Crea las carpetas de otra', target: { screen: 'BRAND_FOLDERS', brand_id: 'otra' } }),
  ];

  it('fuera de la ficha salen todas', () => {
    expect(withoutBrandNotices(all, null)).toHaveLength(all.length);
  });

  it('en la ficha de b quita sus carpetas y sus cuentas de Publer de Instagram y TikTok, y deja lo demás', () => {
    const ids = withoutBrandNotices(all, 'b').map(r => r.id);
    expect(ids).not.toContain('brand-folders:b');
    expect(ids).not.toContain('publer-account:b:INSTAGRAM');
    expect(ids).not.toContain('publer-account:b:TIKTOK');
    expect(ids).toEqual(expect.arrayContaining(['proposals-review:p', 'orders-not-collected', 'publer-account:b:YOUTUBE',
      'publer-account:otra:INSTAGRAM', 'publora:b', 'brand-folders:otra']));
  });

  it('si no queda ninguna, la franja no se pinta', () => {
    render(<NextSteps items={withoutBrandNotices([three[1]], 'b')} onOpen={() => {}} />);
    expect(screen.queryByRole('region', { name: 'Qué hacer ahora' })).toBeNull();
  });
});

describe('Qué hacer ahora', () => {
  it('no se muestra con la lista vacía', () => {
    world(withRecs([]));
    expect(screen.queryByText('Qué hacer ahora')).not.toBeInTheDocument();
    expect(screen.getByText('Necesita atención')).toBeInTheDocument();
  });

  it('pinta las 3 recomendaciones, fuera del mapa con zoom', () => {
    world(withRecs(three));
    const block = screen.getByRole('region', { name: 'Qué hacer ahora' });
    expect(block.querySelectorAll('.aitem')).toHaveLength(3);
    expect(block.compareDocumentPosition(screen.getByRole('region', { name: 'Necesita tu atención' })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('con más de 3 enseña 3 y «Ver N más» despliega el resto; «Ver menos» lo pliega', () => {
    const many = [...three, rec({ id: 'x4', kind: 'BACKUP_FOLDER_MISSING', title: 'Cuarta' }), rec({ id: 'x5', kind: 'BACKUP_FOLDER_MISSING', title: 'Quinta' })];
    const { rerender } = render(<NextSteps items={many} onOpen={() => {}} />);
    const block = screen.getByRole('region', { name: 'Qué hacer ahora' });
    expect(block.querySelectorAll('.aitem')).toHaveLength(3);
    fireEvent.click(screen.getByRole('button', { name: 'Ver 2 más' }));
    expect(block.querySelectorAll('.aitem')).toHaveLength(5);
    // Una recarga con datos nuevos no pliega la lista
    rerender(<NextSteps items={[...many]} onOpen={() => {}} />);
    expect(block.querySelectorAll('.aitem')).toHaveLength(5);
    fireEvent.click(screen.getByRole('button', { name: 'Ver menos' }));
    expect(block.querySelectorAll('.aitem')).toHaveLength(3);
  });

  it('«Ver N más» va dentro de la franja, justo después de la lista', () => {
    const many = [...three, rec({ id: 'x4', kind: 'BACKUP_FOLDER_MISSING', title: 'Cuarta' })];
    render(<NextSteps items={many} onOpen={() => {}} />);
    const block = screen.getByRole('region', { name: 'Qué hacer ahora' });
    const more = screen.getByRole('button', { name: 'Ver 1 más' });
    expect(more.parentElement).toBe(block);
    expect(more.previousElementSibling).toBe(block.querySelector('.next-list'));
    expect(more).toBe(block.lastElementChild);
  });

  it('con 3 o menos no hay botón «Ver más»', () => {
    render(<NextSteps items={three} onOpen={() => {}} />);
    expect(screen.queryByRole('button', { name: /Ver .* más/ })).not.toBeInTheDocument();
  });

  it('en la fábrica, la franja queda fuera del mundo con zoom', async () => {
    vi.spyOn(api, 'appMode').mockResolvedValue({ mode: 'REAL', demo_available: false } as never);
    const { container } = render(<Factory data={withRecs(three)} live={false} onRefresh={() => {}} />);
    const strip = screen.getByRole('region', { name: 'Qué hacer ahora' });
    expect(container.querySelector('.world')!.contains(strip)).toBe(false);
    expect(container.querySelector('.vp')!.contains(strip)).toBe(false);
  });

  it('el botón navega según el destino', () => {
    const onRecommendation = vi.fn();
    world(withRecs(three), onRecommendation);
    fireEvent.click(screen.getByRole('button', { name: /Tienes 3 propuestas/ }));
    expect(onRecommendation).toHaveBeenCalledWith({ screen: 'PROPOSALS', brand_id: 'b', promotion_id: 'p' });
    fireEvent.click(screen.getByRole('button', { name: /Crea las carpetas/ }));
    expect(onRecommendation).toHaveBeenLastCalledWith({ screen: 'BRAND_FOLDERS', brand_id: 'b' });
  });

  it('la recomendación de Publora lleva un botón «No uso Publora» aparte, sin anidar botones', () => {
    const onDismiss = vi.fn(), onOpen = vi.fn();
    const publora = rec({ id: 'publora:b', kind: 'PUBLORA_MISSING', title: 'Conecta Publora para El DeLito', target: { screen: 'PUBLORA', brand_id: 'b' } });
    render(<NextSteps items={[publora, three[1]]} onOpen={onOpen} onDismissPublora={onDismiss} />);
    const side = screen.getByRole('button', { name: /No uso Publora/ });
    expect(side.closest('button.aitem')).toBeNull();
    expect(screen.getAllByRole('button', { name: /No uso Publora/ })).toHaveLength(1); // solo en la de Publora
    fireEvent.click(side);
    expect(onDismiss).toHaveBeenCalledWith('b');
    expect(onOpen).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /Conecta Publora para El DeLito\. Ir a Publora/ }));
    expect(onOpen).toHaveBeenCalledWith({ screen: 'PUBLORA', brand_id: 'b' });
  });

  it('en la fábrica, «No uso Publora» guarda el descarte y refresca', async () => {
    vi.spyOn(api, 'appMode').mockResolvedValue({ mode: 'REAL', demo_available: false } as never);
    const set = vi.spyOn(api, 'setBrandPubloraDismissed').mockResolvedValue({ items: [], dismissed: true });
    const onRefresh = vi.fn();
    const publora = rec({ id: 'publora:b', kind: 'PUBLORA_MISSING', title: 'Conecta Publora para El DeLito', target: { screen: 'PUBLORA', brand_id: 'b' } });
    render(<Factory data={withRecs([publora])} live={false} onRefresh={onRefresh} />);
    fireEvent.click(screen.getByRole('button', { name: /No uso Publora/ }));
    await waitFor(() => expect(set).toHaveBeenCalledWith('b', true));
    await waitFor(() => expect(onRefresh).toHaveBeenCalled());
  });

  it('una recomendación sin destino es solo texto, sin botón', () => {
    world(withRecs(three));
    expect(screen.getByText(/No ha llegado el resultado de los encargos/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /No ha llegado el resultado/ })).not.toBeInTheDocument();
  });

  it('en la fábrica, el botón de propuestas abre la promoción con el filtro «Propuestas»', async () => {
    vi.spyOn(api, 'appMode').mockResolvedValue({ mode: 'REAL', demo_available: false } as never);
    render(<Factory data={withRecs(three)} live={false} onRefresh={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: /Tienes 3 propuestas/ }));
    expect(await screen.findByRole('button', { name: 'Propuestas' })).toHaveClass('on');
  });

  it('en la fábrica, el botón de carpetas abre la marca para crearlas', async () => {
    vi.spyOn(api, 'appMode').mockResolvedValue({ mode: 'REAL', demo_available: false } as never);
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {})); // las tarjetas de la marca se quedan cargando
    render(<Factory data={withRecs(three)} live={false} onRefresh={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: /Crea las carpetas/ }));
    expect(await screen.findByRole('form', { name: 'Editar marca' })).toBeInTheDocument();
  });

  it('al abrir desde una recomendación de carpetas o de Publora, baja hasta su tarjeta', async () => {
    vi.spyOn(api, 'appMode').mockResolvedValue({ mode: 'REAL', demo_available: false } as never);
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {}));
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;
    try {
      const publora = rec({ id: 'publora:b', kind: 'PUBLORA_MISSING', title: 'Conecta Publora para El DeLito', target: { screen: 'PUBLORA', brand_id: 'b' } });
      const { unmount } = render(<Factory data={withRecs([publora])} live={false} onRefresh={() => {}} />);
      fireEvent.click(screen.getByRole('button', { name: /Conecta Publora para El DeLito\. Ir a Publora/ }));
      await screen.findByRole('form', { name: 'Editar marca' });
      await waitFor(() => expect(scroll).toHaveBeenCalledTimes(1));
      expect((scroll.mock.contexts[0] as HTMLElement).getAttribute('data-card')).toBe('publora');
      unmount();
      scroll.mockClear();
      const publer = rec({ id: 'publer-account:b:TIKTOK', kind: 'PUBLER_ACCOUNT_MISSING', title: 'Indica la cuenta de Publer de El DeLito para TikTok',
        detail: '2 publicaciones retenidas', target: { screen: 'PUBLER', brand_id: 'b' } });
      const second = render(<Factory data={withRecs([publer])} live={false} onRefresh={() => {}} />);
      fireEvent.click(screen.getByRole('button', { name: /Indica la cuenta de Publer de El DeLito para TikTok\. Indicar la cuenta de Publer/ }));
      await screen.findByLabelText('Cuenta en Publer');
      await waitFor(() => expect(scroll).toHaveBeenCalledTimes(1));
      expect((scroll.mock.contexts[0] as HTMLElement).getAttribute('data-card')).toBe('publer');
      second.unmount();
      scroll.mockClear();
      render(<Factory data={withRecs(three)} live={false} onRefresh={() => {}} />);
      fireEvent.click(screen.getByRole('button', { name: /Crea las carpetas/ }));
      await screen.findByRole('form', { name: 'Editar marca' });
      await waitFor(() => expect(scroll).toHaveBeenCalledTimes(1));
      expect((scroll.mock.contexts[0] as HTMLElement).getAttribute('data-card')).toBe('folders');
    } finally {
      delete (Element.prototype as { scrollIntoView?: unknown }).scrollIntoView;
    }
  });

  it('avisa de qué marca tiene la ficha abierta y lo retira al cerrarla', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {}));
    const onBrandOpen = vi.fn();
    const { unmount } = render(<Registration data={snapshot} initial={{ kind: 'brand', id: 'b' }} onClose={() => {}} onSaved={() => {}} onBrandOpen={onBrandOpen} />);
    await screen.findByRole('form', { name: 'Editar marca' });
    expect(onBrandOpen).toHaveBeenLastCalledWith('b');
    unmount();
    expect(onBrandOpen).toHaveBeenLastCalledWith(null);
  });

  it('mientras la ficha crece al cargar, vuelve a colocar la tarjeta; deja de hacerlo si la persona se desplaza', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {}));
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;
    let resized: () => void = () => {};
    const disconnect = vi.fn();
    vi.stubGlobal('ResizeObserver', class { constructor(cb: () => void) { resized = cb; } observe() {} disconnect = disconnect; });
    try {
      render(<Registration data={snapshot} initial={{ kind: 'brand', id: 'b', focus: 'folders' }} onClose={() => {}} onSaved={() => {}} />);
      await screen.findByRole('form', { name: 'Editar marca' });
      await waitFor(() => expect(scroll).toHaveBeenCalledTimes(1));
      resized(); resized();
      expect(scroll).toHaveBeenCalledTimes(3);
      expect((scroll.mock.contexts[2] as HTMLElement).getAttribute('data-card')).toBe('folders');
      fireEvent.wheel(document.body);
      expect(disconnect).toHaveBeenCalled();
    } finally {
      vi.unstubAllGlobals();
      delete (Element.prototype as { scrollIntoView?: unknown }).scrollIntoView;
    }
  });

  it('el destino CLAUDE abre la pantalla de encargos a Claude', async () => {
    vi.spyOn(api, 'appMode').mockResolvedValue({ mode: 'REAL', demo_available: false } as never);
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {}));
    const limit = rec({ id: 'ai-limit', kind: 'HELD_PROPOSALS', title: 'Se ha gastado el límite diario de encargos', target: { screen: 'CLAUDE' } });
    render(<Factory data={withRecs([limit])} live={false} onRefresh={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: /límite diario de encargos\. Ir a Claude/ }));
    expect(await screen.findByRole('heading', { name: 'Encargos a Claude' })).toBeInTheDocument();
  });

  it('pinta las recomendaciones nuevas y las filas y avisos nuevos de Agentes y herramientas', () => {
    const base = { demo: false, capabilities: [] };
    const data: Snapshot = {
      ...withRecs([
        rec({ id: 'spec-waiting', kind: 'SPEC_WAITING', title: 'La especificación semanal espera publicaciones aprobadas' }),
        rec({ id: 'pc-silent', kind: 'PC_TASK_SILENT', title: 'La tarea del PC no contesta', target: { screen: 'CLAUDE' } }),
        rec({ id: 'no-route', kind: 'NO_PUBLISHING_ROUTE', title: 'No hay forma de publicar', target: { screen: 'PUBLORA', brand_id: 'b' } }),
      ]),
      armory_items: [
        { ...base, id: 'claude-cloud', kind: 'AGENT', name: 'Claude (nube)', description: 'Encargos', status: 'OK', note: 'Al día' },
        { ...base, id: 'claude-pc', kind: 'AGENT', name: 'Claude (PC)', description: 'Tarea del PC', status: 'WAITING', note: 'Sin respuesta' },
        { ...base, id: 'publora', kind: 'SERVICE', name: 'Publora', description: 'Publicación', status: 'FAILED', note: 'Sin clave' },
        { ...base, id: 'otro', kind: 'TOOL', name: 'Otro', description: 'Inactivo', status: 'IDLE', note: '-' },
      ],
      alerts: [{ kind: 'BLOCKED', target_type: 'ARMORY', target_id: 'claude-pc', station_id: 'pub', title: 'La tarea del PC no contesta', detail: 'Sin respuesta' }],
    };
    world(data);
    const strip = screen.getByRole('region', { name: 'Qué hacer ahora' });
    expect(within(strip).getAllByRole('button', { name: /Ir a/ })).toHaveLength(2);
    expect(within(strip).getByText('La especificación semanal espera publicaciones aprobadas').closest('.aitem')).toHaveClass('static');
    const panel = screen.getByRole('region', { name: 'Agentes y herramientas' });
    for (const n of ['Claude (nube)', 'Claude (PC)', 'Publora']) expect(within(panel).getByText(n)).toBeInTheDocument();
    expect(panel.querySelectorAll('.dot.s-ok, .dot.s-wait, .dot.s-err, .dot.s-idle')).toHaveLength(4);
  });

  it('el aviso de Publora o del PC abre ese elemento de la armería', () => {
    const go = { onTask: vi.fn(), onStation: vi.fn(), onArmory: vi.fn() };
    openAlert({ kind: 'FAILED', target_type: 'ARMORY', target_id: 'publora', station_id: 'pub', title: 'Publora', detail: 'x' }, go);
    expect(go.onArmory).toHaveBeenCalledWith('publora');
  });

  it('al guardar una promoción con proposals_note, el aviso la incluye', async () => {
    vi.spyOn(api, 'updatePromotion').mockResolvedValue({ ...snapshot.promotions[0], proposals_note: 'Sus propuestas se pedirán el lunes 16 a las 18:00' });
    const onSaved = vi.fn();
    render(<Registration data={snapshot} initial={{ kind: 'promotion', id: 'p' }} onClose={() => {}} onSaved={onSaved} />);
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith('Promoción guardada · Lanzamiento del poemario. Sus propuestas se pedirán el lunes 16 a las 18:00'));
  });

  it('«Activa una promoción» abre esa promoción; sin promotion_id, el alta de una nueva', async () => {
    vi.spyOn(api, 'appMode').mockResolvedValue({ mode: 'REAL', demo_available: false } as never);
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {}));
    const activate = rec({ id: 'no-promotion:b', kind: 'NO_ACTIVE_PROMOTION', title: 'Activa una promoción de El DeLito',
      target: { screen: 'NEW_PROMOTION', brand_id: 'b', promotion_id: 'p' } });
    const { unmount } = render(<Factory data={withRecs([activate])} live={false} onRefresh={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: /Activa una promoción de El DeLito\. Abrir la promoción/ }));
    expect(await screen.findByRole('form', { name: 'Editar promoción' })).toBeInTheDocument();
    unmount();
    const first = rec({ id: 'no-promotion:b', kind: 'NO_ACTIVE_PROMOTION', title: 'Da de alta la primera promoción de El DeLito',
      target: { screen: 'NEW_PROMOTION', brand_id: 'b' } });
    render(<Factory data={withRecs([first])} live={false} onRefresh={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: /Da de alta la primera promoción de El DeLito\. Dar de alta la promoción/ }));
    expect(await screen.findByRole('form', { name: 'Nueva promoción' })).toBeInTheDocument();
  });
});

describe('Recarga periódica', () => {
  it('useFactory recarga cada 60 s además de los avisos', async () => {
    vi.useFakeTimers();
    try {
      const factory = vi.spyOn(api, 'factory').mockResolvedValue(snapshot);
      const { unmount } = renderHook(() => useFactory());
      await act(async () => { await vi.advanceTimersByTimeAsync(0); });
      expect(factory).toHaveBeenCalledTimes(1);
      await act(async () => { await vi.advanceTimersByTimeAsync(59_000); });
      expect(factory).toHaveBeenCalledTimes(1);
      await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
      expect(factory).toHaveBeenCalledTimes(2);
      unmount();
      await act(async () => { await vi.advanceTimersByTimeAsync(120_000); });
      expect(factory).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('Avisos de confirmación', () => {
  it('un aviso nuevo no se corta por el temporizador del anterior', async () => {
    vi.spyOn(api, 'appMode').mockResolvedValue({ mode: 'REAL', demo_available: false } as never);
    vi.spyOn(api, 'setBrandPubloraDismissed').mockResolvedValue({ items: [], dismissed: true });
    const publora = rec({ id: 'publora:b', kind: 'PUBLORA_MISSING', title: 'Conecta Publora para El DeLito', target: { screen: 'PUBLORA', brand_id: 'b' } });
    render(<Factory data={withRecs([publora])} live={false} onRefresh={() => {}} />);
    vi.useFakeTimers();
    try {
      const press = async () => { fireEvent.click(screen.getByRole('button', { name: /No uso Publora/ })); await act(async () => { await vi.advanceTimersByTimeAsync(0); }); };
      await press();
      expect(screen.getByRole('status')).toBeInTheDocument();
      await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
      await press(); // segundo aviso a los 2 s: el primero habría caducado a los 2,8 s
      await act(async () => { await vi.advanceTimersByTimeAsync(1_500); });
      expect(screen.getByRole('status')).toBeInTheDocument();
      await act(async () => { await vi.advanceTimersByTimeAsync(1_500); });
      expect(screen.queryByRole('status')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('Mensajes al guardar', () => {
  const pending = rec({ id: 'proposals-pending:p', kind: 'PROPOSALS_PENDING', promotion_id: 'p', eta_text: 'sobre las 9:40',
    title: 'Propuestas de «Lanzamiento del poemario» pedidas. Llegarán sobre las 9:40' });
  it('promoción con propuestas en camino: dice la hora de la recomendación', () => {
    expect(savedMessage('Promoción guardada', { promotionId: 'p' }, [pending])).toBe('Promoción guardada. Propuestas pedidas: llegarán sobre las 9:40');
    expect(savedMessage('Promoción guardada', { promotionId: 'p' }, [{ ...pending, eta_text: 'mañana sobre las 9:40' }]))
      .toContain('llegarán mañana sobre las 9:40');
  });
  it('promoción con propuestas retenidas: dice el motivo', () => {
    const held = rec({ id: 'held-proposals:p', kind: 'HELD_PROPOSALS', promotion_id: 'p', title: 'x', detail: 'La marca no tiene carpeta de Encargos' });
    expect(savedMessage('Promoción guardada', { promotionId: 'p' }, [held])).toBe('Promoción guardada. Las propuestas no pueden salir: La marca no tiene carpeta de Encargos');
  });
  it('promoción activa sin recomendación todavía: no inventa hora', () => {
    const m = savedMessage('Promoción guardada', { promotionId: 'p', status: 'ACTIVE' }, [{ ...pending, id: 'proposals-pending:otra', promotion_id: 'otra' }]);
    expect(m).toBe('Promoción guardada. Te avisaremos aquí del estado de sus propuestas en «Qué hacer ahora»');
    expect(m).not.toMatch(/\d:\d\d/);
    // Sin ninguna fila (lista vacía) pasa lo mismo: no se rompe ni se inventa nada
    expect(savedMessage('Promoción guardada', { promotionId: 'p', status: 'ACTIVE' }, [])).toBe(m);
  });
  it('promoción planificada, en pausa o terminada: no promete ningún aviso', () => {
    for (const status of ['PLANNED', 'PAUSED', 'FINISHED']) {
      expect(savedMessage('Promoción guardada', { promotionId: 'p', status }, [])).toBe('Promoción guardada');
    }
  });
  it('marca: añade el siguiente paso, o nada si no hay', () => {
    expect(savedMessage('Marca guardada', { brandId: 'b' }, three)).toBe('Marca guardada · Siguiente paso: Tienes 3 propuestas de «Lanzamiento del poemario» para revisar');
    expect(savedMessage('Marca guardada', { brandId: 'otra' }, three)).toBe('Marca guardada');
    // Con más de 3 recomendaciones, la de la marca guardada se encuentra aunque esté escondida tras «Ver N más»
    const hidden = rec({ id: 'brand-folders:z', kind: 'BRAND_FOLDERS_MISSING', title: 'Crea las carpetas de Z', target: { screen: 'BRAND_FOLDERS', brand_id: 'z' } });
    expect(savedMessage('Marca guardada', { brandId: 'z' }, [...three, three[0], three[1], hidden])).toBe('Marca guardada · Siguiente paso: Crea las carpetas de Z');
  });
  it('el aviso dura 6 s con siguiente paso o estado de propuestas, y 2,8 s si no', () => {
    const long = savedMessage('Promoción guardada', { promotionId: 'p' }, [pending]);
    expect(toastDuration('Promoción guardada', long)).toBe(6000);
    const brand = savedMessage('Marca guardada', { brandId: 'b' }, three);
    expect(toastDuration('Marca guardada', brand)).toBe(6000);
    const none = savedMessage('Marca guardada', { brandId: 'otra' }, three);
    expect(toastDuration('Marca guardada', none)).toBe(2800);
    expect(toastDuration('Hecho', 'Hecho')).toBe(2800);
  });
  it('el registro avisa de qué se ha guardado', async () => {
    vi.spyOn(api, 'updatePromotion').mockResolvedValue(snapshot.promotions[0]);
    const onSaved = vi.fn();
    render(<Registration data={snapshot} initial={{ kind: 'promotion', id: 'p' }} onClose={() => {}} onSaved={onSaved} />);
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith('Promoción guardada · Lanzamiento del poemario', { promotionId: 'p', status: 'ACTIVE' }));
  });
  it('el alta de promoción desde una recomendación preselecciona la marca', () => {
    const two = { ...snapshot, brands: [...snapshot.brands, { id: 'l', name: 'Litoques', handle: '@l', color: '#C98A1B', demo: false }] };
    render(<Registration data={two} initial={{ kind: 'promotion', id: null, brandId: 'l' }} onClose={() => {}} onSaved={() => {}} />);
    expect(screen.getByLabelText('Marca')).toHaveValue('l');
  });
});
