import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { api } from './api';
import { BrandNotices } from './BrandNotices';
import { IdentityForm } from './Identity';
import { identityTemplate } from './identityImport';
import type { BrandIdentity } from './model';

afterEach(() => vi.restoreAllMocks());

const identity = (o: Partial<BrandIdentity> = {}): BrandIdentity => ({
  tone: 'Cercano', audience: 'Lectores', forbidden_topics: [], preferred_expressions: [], forbidden_expressions: [],
  colors: [], font: '', logo: '', visual_style: '', photo_style: '', call_to_action: '', fixed_hashtags: '', ...o,
});
const folders = (available = true) => ({
  items: ['MATERIAL', 'DRAFTS', 'GENERATED', 'APPROVED', 'PUBLISHED', 'REPORTS', 'ORDERS'].map(role => (
    { role: role as 'ORDERS', location: `/x/${role}`, origin: 'BRAND' as const, available })),
});

function mockAll(o: {
  identity?: BrandIdentity; folders?: ReturnType<typeof folders>; publer?: string[]; metaConfigured?: boolean; ig?: string; shared?: boolean;
} = {}) {
  vi.spyOn(api, 'brandIdentity').mockResolvedValue(o.identity ?? identity());
  vi.spyOn(api, 'brandFolders').mockResolvedValue(o.folders ?? folders());
  vi.spyOn(api, 'brandPublerAccounts').mockResolvedValue({
    items: (o.publer ?? ['INSTAGRAM', 'TIKTOK']).map(p => ({ platform: p as 'TIKTOK', account_name: 'Cuenta' })),
  });
  vi.spyOn(api, 'instagram').mockResolvedValue({ configured: o.metaConfigured ?? false } as Awaited<ReturnType<typeof api.instagram>>);
  vi.spyOn(api, 'brandInstagramAccount').mockResolvedValue(o.ig ? { ig_user_id: o.ig } : {});
  vi.spyOn(api, 'brandPublicShare').mockResolvedValue({ shared: o.shared ?? false });
}

describe('Avisos de la ficha de la marca', () => {
  it('no sale el bloque si no falta nada', async () => {
    mockAll();
    render(<BrandNotices brandId="b" version={0} />);
    await waitFor(() => expect(api.brandPublicShare).toHaveBeenCalled());
    await new Promise(r => setTimeout(r, 20));
    expect(screen.queryByLabelText('Avisos de la marca')).toBeNull();
  });

  it('avisa de identidad, carpetas y cuentas de Publer, y cada botón baja a su tarjeta', async () => {
    mockAll({ identity: identity({ tone: '' }), folders: { items: [] }, publer: ['INSTAGRAM'] });
    const target = document.createElement('div');
    target.setAttribute('data-card', 'publer');
    target.scrollIntoView = vi.fn();
    document.body.appendChild(target);
    render(<BrandNotices brandId="b" version={0} />);
    expect(await screen.findByText(/Falta rellenar la identidad de la marca: el tono\./)).toBeInTheDocument();
    expect(screen.getByText(/Carpetas sin configurar/)).toBeInTheDocument();
    expect(screen.getByText('Falta indicar la cuenta de Publer de TikTok.')).toBeInTheDocument();
    expect(screen.queryByText(/de Instagram\./)).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Ir a Publer' }));
    expect(target.scrollIntoView).toHaveBeenCalled();
    target.remove();
  });

  it('carpetas no disponibles', async () => {
    mockAll({ folders: folders(false) });
    render(<BrandNotices brandId="b" version={0} />);
    expect(await screen.findByText(/Carpetas no disponibles ahora/)).toBeInTheDocument();
  });

  it('la cuenta de Instagram para la API oficial solo se pide si hay clave de Meta', async () => {
    mockAll({ metaConfigured: false });
    const { unmount } = render(<BrandNotices brandId="b" version={0} />);
    await waitFor(() => expect(api.brandInstagramAccount).toHaveBeenCalled());
    await new Promise(r => setTimeout(r, 20));
    expect(screen.queryByText(/API oficial/)).toBeNull();
    unmount();
    mockAll({ metaConfigured: true });
    render(<BrandNotices brandId="b" version={0} />);
    expect(await screen.findByText('Falta indicar la cuenta de Instagram para la API oficial.')).toBeInTheDocument();
  });

  it('el aviso de compartir «publico» se oculta con «Ya está compartida»', async () => {
    mockAll({ metaConfigured: true, ig: '123' });
    const set = vi.spyOn(api, 'setBrandPublicShare').mockResolvedValue({ shared: true });
    render(<BrandNotices brandId="b" version={0} />);
    expect(await screen.findByText(/Comparte la carpeta «publico»/)).toBeInTheDocument();
    vi.spyOn(api, 'brandPublicShare').mockResolvedValue({ shared: true });
    fireEvent.click(screen.getByRole('button', { name: 'Ya está compartida' }));
    await waitFor(() => expect(set).toHaveBeenCalledWith('b', true));
    await waitFor(() => expect(screen.queryByLabelText('Avisos de la marca')).toBeNull());
  });

  it('sin API oficial en la marca no se pide compartir «publico»', async () => {
    mockAll({ metaConfigured: true, ig: undefined });
    render(<BrandNotices brandId="b" version={0} />);
    await screen.findByText(/cuenta de Instagram para la API oficial/);
    expect(screen.queryByText(/Comparte la carpeta/)).toBeNull();
  });
});

describe('Importar la identidad desde un .txt', () => {
  it('enseña los cambios antes de guardar y mantiene los campos que no vienen', async () => {
    vi.spyOn(api, 'brandIdentity').mockResolvedValue(identity({ colors: ['rojo'] }));
    const save = vi.spyOn(api, 'saveBrandIdentity').mockImplementation(async (_id, i) => i);
    render(<IdentityForm brandId="b" brand={{ name: 'Litoques', handle: '@litoques' }} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Importar desde .txt' }));
    fireEvent.change(screen.getByLabelText('Texto de la identidad'), { target: { value: '### nota\n## Tono\nIrónico\n## Colores\n- rojo\n- negro\n' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ver cambios' }));
    expect(await screen.findByLabelText('Tono antes')).toHaveTextContent('Cercano');
    expect(screen.getByLabelText('Tono después')).toHaveTextContent('Irónico');
    expect(screen.getByLabelText('Colores después')).toHaveTextContent('- negro');
    expect(save).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Guardar estos cambios' }));
    await waitFor(() => expect(save).toHaveBeenCalled());
    expect(save.mock.calls[0][1]).toMatchObject({ tone: 'Irónico', audience: 'Lectores', colors: ['rojo', 'negro'] });
  });

  it('el selector de archivo es un botón en español con el nombre del archivo elegido', async () => {
    vi.spyOn(api, 'brandIdentity').mockResolvedValue(identity());
    render(<IdentityForm brandId="b" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Importar desde .txt' }));
    expect(screen.getByRole('button', { name: 'Elegir archivo .txt' })).toBeInTheDocument();
    expect(screen.getByTestId('identity-file-name')).toHaveTextContent('Ningún archivo elegido');
    const input = screen.getByLabelText('Archivo .txt de la identidad') as HTMLInputElement;
    expect(input.hidden).toBe(true);
    const click = vi.spyOn(input, 'click');
    fireEvent.click(screen.getByRole('button', { name: 'Elegir archivo .txt' }));
    expect(click).toHaveBeenCalled();
    const file = new File(['## Tono\nIrónico\n'], 'identidad.txt', { type: 'text/plain' });
    Object.defineProperty(file, 'text', { value: () => Promise.resolve('## Tono\nIrónico\n') });
    fireEvent.change(input, { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId('identity-file-name')).toHaveTextContent('identidad.txt'));
    expect(await screen.findByLabelText('Tono después')).toHaveTextContent('Irónico');
  });

  it('la vista previa es una tabla con cabeceras Campo, Ahora y Después de importar, y las listas una por línea', async () => {
    vi.spyOn(api, 'brandIdentity').mockResolvedValue(identity({ colors: ['rojo'] }));
    render(<IdentityForm brandId="b" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Importar desde .txt' }));
    fireEvent.change(screen.getByLabelText('Texto de la identidad'), { target: { value: '## Tono\nIrónico\n## Colores\n- rojo\n- negro\n' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ver cambios' }));
    const table = await screen.findByRole('table');
    expect(within(table).getAllByRole('columnheader').map(h => h.textContent)).toEqual(['Campo', 'Ahora', 'Después de importar']);
    const row = within(table).getByRole('row', { name: /Colores/ });
    expect(within(row).getByLabelText('Colores después').querySelectorAll('div')).toHaveLength(2);
    expect(table.querySelector('pre')).toBeNull();
  });

  it('enseña un error claro y no deja guardar', async () => {
    vi.spyOn(api, 'brandIdentity').mockResolvedValue(identity());
    render(<IdentityForm brandId="b" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Importar desde .txt' }));
    fireEvent.change(screen.getByLabelText('Texto de la identidad'), { target: { value: '## Rareza\nx' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ver cambios' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Campo desconocido: «Rareza»');
    expect(screen.queryByRole('button', { name: 'Guardar estos cambios' })).toBeNull();
  });

  it('«Copiar plantilla» copia la plantilla con los valores actuales y avisa', async () => {
    vi.spyOn(api, 'brandIdentity').mockResolvedValue(identity({ forbidden_topics: ['política'] }));
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    render(<IdentityForm brandId="b" brand={{ name: 'Litoques', handle: '@litoques' }} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Copiar plantilla' }));
    expect(await screen.findByText('Plantilla copiada')).toBeInTheDocument();
    const copied = writeText.mock.calls[0][0] as string;
    expect(copied).toContain('identidad de la marca Litoques (@litoques)');
    expect(copied).toContain('## Hashtags fijos');
    expect(copied).toContain('Máximo 50 elementos de 200 caracteres');
    expect(copied).toContain('## Tono\n### Texto. Máximo 2000 caracteres.\nCercano');
    expect(copied).toContain('- política');
  });
});

describe('identityTemplate', () => {
  it('sin identidad lleva todos los campos vacíos', () => {
    const t = identityTemplate({ name: 'X', handle: '@x' });
    expect(t.match(/^## /gm)).toHaveLength(12);
    expect(t).toContain('Máximo 20 elementos de 40 caracteres');
  });
});
