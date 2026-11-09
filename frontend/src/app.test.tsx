import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { api } from './api';
import { Backups, Halted, ShutdownButton } from './Backups';
import { Claude } from './Claude';
import { ControlCenter } from './ControlCenter';
import { ExternalOperations } from './ExternalOperations';
import { FolderRoles } from './Folders';
import { MemoryCard } from './Memory';
import { PublicationEditor } from './PublicationEditor';
import { PubloraCard } from './Publora';
import { BrandForm, PromotionForm, Registration } from './Registration';
import { Factory } from './Factory';
import { savedMessage } from './Recommendations';
import type { Recommendation } from './api/schema';
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
    vi.spyOn(api, 'brandPubloraAccounts').mockResolvedValue({ items: [] });
    const saveKey = vi.spyOn(api, 'savePubloraKey').mockResolvedValue({ configured: true, key_hint: '…6789', connections });
    const saveAccounts = vi.spyOn(api, 'saveBrandPubloraAccounts').mockResolvedValue({ items: [] });
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
  it('crea las que faltan desde una carpeta base', async () => {
    const { FolderRoles } = await import('./Folders');
    vi.spyOn(api, 'brandFolders').mockResolvedValue({ items: [] });
    const created = { items: [{ role: 'ORDERS' as const, location: 'G:\\SH\\Litoques\\Encargos', origin: 'BRAND' as const, available: true }] };
    const create = vi.spyOn(api, 'createBrandFolders').mockResolvedValue(created);
    const onSaved = vi.fn();
    render(<FolderRoles owner="brand" id="litoques" onSaved={onSaved} />);
    fireEvent.change(await screen.findByLabelText('Carpeta base'), { target: { value: 'G:\\SH\\Litoques' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear las que faltan' }));
    await waitFor(() => expect(create).toHaveBeenCalledWith('litoques', 'G:\\SH\\Litoques'));
    expect(onSaved).toHaveBeenCalledWith('Carpetas creadas');
    expect(screen.getByLabelText('Carpeta de Encargos')).toHaveValue('G:\\SH\\Litoques\\Encargos');
  });

  it('las promociones no tienen el botón de crear', async () => {
    const { FolderRoles } = await import('./Folders');
    vi.spyOn(api, 'promotionFolders').mockResolvedValue({ items: [] });
    render(<FolderRoles owner="promotion" id="test" />);
    await screen.findByRole('button', { name: 'Guardar carpetas' });
    expect(screen.queryByRole('button', { name: 'Crear las que faltan' })).toBeNull();
  });
});

const rec = (o: Partial<Recommendation> & Pick<Recommendation, 'id' | 'kind' | 'title'>): Recommendation => ({ ...o });
const three: Recommendation[] = [
  rec({ id: 'proposals-review:p', kind: 'PROPOSALS_TO_REVIEW', title: 'Tienes 3 propuestas de «Lanzamiento del poemario» para revisar',
    target: { screen: 'PROPOSALS', brand_id: 'b', promotion_id: 'p' } }),
  rec({ id: 'brand-folders:b', kind: 'BRAND_FOLDERS_MISSING', title: 'Crea las carpetas de El DeLito', target: { screen: 'BRAND_FOLDERS', brand_id: 'b' } }),
  rec({ id: 'orders-not-collected', kind: 'ORDERS_NOT_COLLECTED', title: 'Claude no ha recogido los encargos de las 9:40. Revisa la tarea programada' }),
];
const withRecs = (recommendations: Recommendation[]): Snapshot => ({ ...snapshot, recommendations });
const world = (data: Snapshot, onRecommendation = vi.fn()) => render(
  <World data={data} sel={{ type: null, id: null }} onStation={() => {}} onTask={() => {}} onTaskAction={() => {}}
    onArmory={() => {}} onPromotion={() => {}} onRegister={() => {}} onRecommendation={onRecommendation} />);

describe('Qué hacer ahora', () => {
  it('no se muestra con la lista vacía', () => {
    world(withRecs([]));
    expect(screen.queryByText('Qué hacer ahora')).not.toBeInTheDocument();
    expect(screen.getByText('Necesita atención')).toBeInTheDocument();
  });

  it('pinta las 3 recomendaciones encima de «Necesita atención»', () => {
    world(withRecs(three));
    const block = screen.getByRole('region', { name: 'Qué hacer ahora' });
    expect(block.querySelectorAll('.aitem')).toHaveLength(3);
    expect(block.compareDocumentPosition(screen.getByRole('region', { name: 'Necesita tu atención' })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('el botón navega según el destino', () => {
    const onRecommendation = vi.fn();
    world(withRecs(three), onRecommendation);
    fireEvent.click(screen.getByRole('button', { name: /Tienes 3 propuestas/ }));
    expect(onRecommendation).toHaveBeenCalledWith({ screen: 'PROPOSALS', brand_id: 'b', promotion_id: 'p' });
    fireEvent.click(screen.getByRole('button', { name: /Crea las carpetas/ }));
    expect(onRecommendation).toHaveBeenLastCalledWith({ screen: 'BRAND_FOLDERS', brand_id: 'b' });
  });

  it('una recomendación sin destino es solo texto, sin botón', () => {
    world(withRecs(three));
    expect(screen.getByText(/Claude no ha recogido los encargos/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Claude no ha recogido/ })).not.toBeInTheDocument();
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
});

describe('Mensajes al guardar', () => {
  const pending = rec({ id: 'proposals-pending:p', kind: 'PROPOSALS_PENDING', title: 'Propuestas de «Lanzamiento del poemario» pedidas. Llegarán sobre las 9:40' });
  it('promoción con propuestas en camino: dice la hora de la recomendación', () => {
    expect(savedMessage('Promoción guardada', { promotionId: 'p' }, [pending])).toBe('Promoción guardada. Propuestas pedidas: llegarán sobre las 9:40');
    expect(savedMessage('Promoción guardada', { promotionId: 'p' }, [{ ...pending, title: pending.title.replace('sobre', 'mañana sobre') }]))
      .toContain('llegarán mañana sobre las 9:40');
  });
  it('promoción con propuestas retenidas: dice el motivo', () => {
    const held = rec({ id: 'held-proposals:p', kind: 'HELD_PROPOSALS', title: 'x', detail: 'La marca no tiene carpeta de Encargos' });
    expect(savedMessage('Promoción guardada', { promotionId: 'p' }, [held])).toBe('Promoción guardada. Las propuestas no pueden salir: La marca no tiene carpeta de Encargos');
  });
  it('promoción sin recomendación todavía: no inventa hora', () => {
    const m = savedMessage('Promoción guardada', { promotionId: 'p' }, [{ ...pending, id: 'proposals-pending:otra' }]);
    expect(m).toBe('Promoción guardada. Te avisaremos aquí del estado de sus propuestas en «Qué hacer ahora»');
    expect(m).not.toMatch(/\d:\d\d/);
  });
  it('marca: añade el siguiente paso, o nada si no hay', () => {
    expect(savedMessage('Marca guardada', { brandId: 'b' }, three)).toBe('Marca guardada · Siguiente paso: Tienes 3 propuestas de «Lanzamiento del poemario» para revisar');
    expect(savedMessage('Marca guardada', { brandId: 'otra' }, three)).toBe('Marca guardada');
  });
  it('el registro avisa de qué se ha guardado', async () => {
    vi.spyOn(api, 'updatePromotion').mockResolvedValue(snapshot.promotions[0]);
    const onSaved = vi.fn();
    render(<Registration data={snapshot} initial={{ kind: 'promotion', id: 'p' }} onClose={() => {}} onSaved={onSaved} />);
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith('Promoción guardada · Lanzamiento del poemario', { promotionId: 'p' }));
  });
  it('el alta de promoción desde una recomendación preselecciona la marca', () => {
    const two = { ...snapshot, brands: [...snapshot.brands, { id: 'l', name: 'Litoques', handle: '@l', color: '#C98A1B', demo: false }] };
    render(<Registration data={two} initial={{ kind: 'promotion', id: null, brandId: 'l' }} onClose={() => {}} onSaved={() => {}} />);
    expect(screen.getByLabelText('Marca')).toHaveValue('l');
  });
});
