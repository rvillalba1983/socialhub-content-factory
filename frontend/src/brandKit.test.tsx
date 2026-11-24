import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { api } from './api';
import { BrandKitCard } from './BrandKit';
import { withoutBrandNotices } from './Recommendations';
import type { BrandKitVersion, Snapshot, Task } from './model';

afterEach(() => vi.restoreAllMocks());

const promotion = (id: string, brand: string, name = id) => ({
  id, brand_id: brand, name, description: '', scope: '', cadence: '', starts_on: '2026-10-27', ends_on: '2027-01-18',
  status: 'ACTIVE', metrics_source: '', goals: [], rules: [], human_todos: [], milestones: [], resources: [], automations: [],
  routes: [], settings: [], demo: false,
}) as Snapshot['promotions'][number];

const data = (promotions: Snapshot['promotions'], tasks: Task[] = []): Snapshot => ({ promotions, tasks }) as Snapshot;

const version = (o: Partial<BrandKitVersion> = {}): BrandKitVersion => ({
  id: 7, version: 2, status: 'PENDING', palette: [{ name: 'Rojo tinta', hex: '#D23A2E' }, { name: 'Crema', hex: '#F4EBDD' }],
  notes: 'Fondos suaves para que se lea la ilustración',
  templates: [
    { id: 'cierre-1', piece: 'cierre', name: 'Cierre claro', svg: '<svg/>', preview_png_base64: 'AAAA', warnings: ['El título es largo'], errors: [] },
    { id: 'historia-1', piece: 'historia', name: 'Historia con zona libre', svg: '<svg/>', preview_png_base64: null, warnings: [], errors: ['Falta el campo {{title}}'] },
  ],
  created_at: '2026-11-24T20:00:00Z', ...o,
});

const draw = (brandId: string, name: string, snapshot: Snapshot, onChanged = vi.fn()) =>
  render(<BrandKitCard brandId={brandId} brandName={name} data={snapshot} onChanged={onChanged} />);

describe('Kit de marca en la ficha', () => {
  it('enseña el kit aprobado con la paleta, las notas y la vista previa de cada plantilla', async () => {
    vi.spyOn(api, 'brandKit').mockResolvedValue({ approved: version({ status: 'APPROVED' }) });
    draw('litoques', 'Litoques', data([promotion('p1', 'litoques')]));
    const approved = await screen.findByRole('region', { name: 'Kit aprobado' });
    expect(within(approved).getByText('Rojo tinta')).toBeInTheDocument();
    expect(within(approved).getByText(/Fondos suaves/)).toBeInTheDocument();
    expect(within(approved).getByAltText('Vista previa de Cierre claro')).toHaveAttribute('src', 'data:image/png;base64,AAAA');
    expect(within(approved).getByText('Sin vista previa')).toBeInTheDocument();
    expect(within(approved).getByRole('list', { name: 'Avisos de Cierre claro' })).toHaveTextContent('El título es largo');
    expect(within(approved).getByRole('alert')).toHaveTextContent('Falta el campo {{title}}');
    expect(screen.queryByRole('region', { name: 'Kit pendiente de aprobación' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Aprobar' })).toBeNull();
  });

  it('el kit pendiente se aprueba con «Aprobar»', async () => {
    const approve = vi.spyOn(api, 'approveBrandKit').mockResolvedValue(version({ status: 'APPROVED' }));
    const brandKit = vi.spyOn(api, 'brandKit').mockResolvedValueOnce({ pending: version() })
      .mockResolvedValue({ approved: version({ status: 'APPROVED' }) });
    const onChanged = vi.fn();
    draw('litoques', 'Litoques', data([promotion('p1', 'litoques')]), onChanged);
    await screen.findByRole('region', { name: 'Kit pendiente de aprobación' });
    fireEvent.click(screen.getByRole('button', { name: 'Aprobar' }));
    await waitFor(() => expect(approve).toHaveBeenCalledWith('litoques'));
    expect(await screen.findByRole('region', { name: 'Kit aprobado' })).toBeInTheDocument();
    expect(brandKit).toHaveBeenCalledTimes(2);
    expect(onChanged).toHaveBeenCalledWith('Kit de marca aprobado · Litoques');
  });

  it('«Pedir otra versión» exige un comentario y pide ya otra versión', async () => {
    const reject = vi.spyOn(api, 'rejectBrandKit').mockResolvedValue({ kit: version({ status: 'REJECTED' }), task_id: 't9' });
    vi.spyOn(api, 'brandKit').mockResolvedValue({ pending: version() });
    draw('litoques', 'Litoques', data([promotion('p1', 'litoques')]));
    await screen.findByRole('region', { name: 'Kit pendiente de aprobación' });
    fireEvent.click(screen.getByRole('button', { name: 'Pedir otra versión' }));
    const send = screen.getByRole('button', { name: 'Enviar y pedir otra versión' });
    expect(send).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/¿Qué quieres que cambie/), { target: { value: '  Más contraste  ' } });
    expect(send).toBeEnabled();
    fireEvent.click(send);
    await waitFor(() => expect(reject).toHaveBeenCalledWith('litoques', 'Más contraste', true, undefined));
    expect(await screen.findByText(/Encargo en curso/)).toBeInTheDocument();
  });

  it('con una sola promoción el encargo va con ella; cierre e historia por defecto y sin cita fuera de El DeLito', async () => {
    const request = vi.spyOn(api, 'requestBrandKit').mockResolvedValue({ task_id: 't1' } as never);
    vi.spyOn(api, 'brandKit').mockResolvedValue({});
    draw('litoques', 'Litoques', data([promotion('p1', 'litoques', 'Disco'), promotion('x', 'otra')]));
    await screen.findByText('Esta marca todavía no tiene kit.');
    expect(screen.getByText(/«Disco»/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Cita/)).toBeNull();
    expect(screen.getByLabelText('Cierre del carrusel')).toBeChecked();
    expect(screen.getByLabelText('Historia')).toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: 'Pedir kit' }));
    await waitFor(() => expect(request).toHaveBeenCalledWith('litoques', { promotion_id: 'p1', pieces: ['cierre', 'historia'] }));
    expect(await screen.findByText(/Encargo en curso/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pedir kit' })).toBeNull();
  });

  it('con varias promociones hay que elegir una; El DeLito lleva la cita marcada por defecto', async () => {
    const request = vi.spyOn(api, 'requestBrandKit').mockResolvedValue({ task_id: 't1' } as never);
    vi.spyOn(api, 'brandKit').mockResolvedValue({});
    draw('el-delito', 'El DeLito', data([promotion('a', 'el-delito', 'Lanzamiento'), promotion('b', 'el-delito', 'Navidad')]));
    await screen.findByText('Esta marca todavía no tiene kit.');
    expect(screen.getByLabelText(/Cita/)).toBeChecked();
    const button = screen.getByRole('button', { name: 'Pedir kit' });
    expect(button).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/Promoción de la que cuelga/), { target: { value: 'b' } });
    expect(button).toBeEnabled();
    fireEvent.click(screen.getByLabelText('Historia'));
    fireEvent.click(button);
    await waitFor(() => expect(request).toHaveBeenCalledWith('el-delito', { promotion_id: 'b', pieces: ['cita', 'cierre'] }));
  });

  it('sin promociones explica que hace falta una y no deja pedir', async () => {
    vi.spyOn(api, 'brandKit').mockResolvedValue({});
    draw('litoques', 'Litoques', data([]));
    expect(await screen.findByText(/aún no tiene ninguna promoción/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pedir kit' })).toBeDisabled();
  });

  it('un encargo de kit abierto de la marca muestra «Encargo en curso»; uno terminado, no', async () => {
    vi.spyOn(api, 'brandKit').mockResolvedValue({});
    const open = { id: 't', type: 'AI_BRAND_KIT', promotion_id: 'p1', status: 'RUNNING' } as Task;
    const { unmount } = draw('litoques', 'Litoques', data([promotion('p1', 'litoques')], [open]));
    expect(await screen.findByText(/Encargo en curso/)).toBeInTheDocument();
    unmount();
    draw('litoques', 'Litoques', data([promotion('p1', 'litoques')], [{ ...open, status: 'COMPLETED' } as Task]));
    await screen.findByRole('button', { name: 'Pedir kit' });
    expect(screen.queryByText(/Encargo en curso/)).toBeNull();
  });

  it('enseña el error del servidor al pedir el kit', async () => {
    vi.spyOn(api, 'brandKit').mockResolvedValue({});
    vi.spyOn(api, 'requestBrandKit').mockRejectedValue(new Error('Esta marca aún no tiene ninguna promoción'));
    draw('litoques', 'Litoques', data([promotion('p1', 'litoques')]));
    fireEvent.click(await screen.findByRole('button', { name: 'Pedir kit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Esta marca aún no tiene ninguna promoción');
  });

  it('dentro de la ficha, «Qué hacer ahora» no repite las recomendaciones del kit de esa marca', () => {
    const recs = [
      { id: 'brand-kit:b', kind: 'BRAND_KIT_MISSING', title: 'Genera el kit de X', target: { screen: 'BRAND_KIT', brand_id: 'b' } },
      { id: 'brand-kit-pending:c', kind: 'BRAND_KIT_PENDING', title: 'Revisa el kit de Y', target: { screen: 'BRAND_KIT', brand_id: 'c' } },
    ] as Snapshot['recommendations'];
    expect(withoutBrandNotices(recs, 'b').map(r => r.id)).toEqual(['brand-kit-pending:c']);
  });
});
