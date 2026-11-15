import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { api } from './api';
import { FolderRoles } from './Folders';
import { InstagramAccountCard, InstagramKeyCard } from './Instagram';
import { PubloraCard } from './Publora';
import { PublerCard } from './Publer';
import { BrandForm } from './Registration';
import { HELP_TOPICS, allHelpLinks, type HelpTopicId } from './helpTopics';
import { WhereHelp } from './WhereHelp';
import manual from '../../MANUAL.md?raw';
import tutorial from '../../docs/TUTORIAL.md?raw';

// Todos los .tsx de la interfaz (menos las pruebas), para comprobar que cada ayuda está puesta en algún sitio
const sources = import.meta.glob('./*.tsx', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;

afterEach(() => vi.restoreAllMocks());

describe('«¿Dónde lo saco?»', () => {
  it('empieza cerrado y, abierto, enseña los pasos numerados y los enlaces en pestaña nueva', () => {
    const { container } = render(<WhereHelp topic="meta-token" />);
    const details = container.querySelector('details')!;
    expect(details.open).toBe(false);
    expect(screen.getByText('¿Dónde lo saco?')).toBeInTheDocument();
    fireEvent.click(screen.getByText('¿Dónde lo saco?'));
    const group = screen.getByRole('group', { name: /Dónde sacar: Clave de la API de Instagram/ });
    const steps = within(group).getAllByRole('listitem').filter(li => li.closest('ol'));
    expect(steps).toHaveLength(HELP_TOPICS['meta-token'].steps.length);
    const link = within(group).getByRole('link', { name: /Usuarios del sistema \(Meta Business\)/ });
    expect(link).toHaveAttribute('href', 'https://business.facebook.com/settings/system-users');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
    expect(within(group).getByText(/Es un secreto/)).toBeInTheDocument();
  });

  it('todos los enlaces son https, abren en pestaña nueva y ninguno lleva una clave', () => {
    const urls = allHelpLinks().map(l => l.url);
    expect(urls.length).toBeGreaterThan(10);
    for (const url of urls) {
      expect(url).toMatch(/^https:\/\/[a-z0-9.-]+\.[a-z]{2,}(\/|$)/);
      expect(url).not.toMatch(/token|secret|key=|access_token/i);
    }
    expect(new Set(urls).size).toBe(urls.length);
    for (const id of Object.keys(HELP_TOPICS) as HelpTopicId[]) {
      const { container, unmount } = render(<WhereHelp topic={id} />);
      const anchors = container.querySelectorAll('a');
      anchors.forEach(a => { expect(a.getAttribute('target')).toBe('_blank'); expect(a.getAttribute('rel')).toContain('noopener'); });
      expect(container.querySelectorAll('ol > li').length).toBeGreaterThanOrEqual(2);
      unmount();
    }
  });

  it('cada ayuda del catálogo está puesta junto a su campo en alguna pantalla', () => {
    const code = Object.entries(sources).filter(([f]) => !/\.test\.tsx$/.test(f) && !f.endsWith('WhereHelp.tsx')).map(([, s]) => s).join('\n');
    for (const id of Object.keys(HELP_TOPICS)) {
      expect(code, `falta usar la ayuda «${id}»`).toMatch(new RegExp(`(topic="${id}"|help: '${id}'|'${id}')`));
    }
  });

  it('MANUAL.md y docs/TUTORIAL.md repiten todos los enlaces y el dato de cada ayuda', () => {
    for (const { url } of allHelpLinks()) {
      expect(manual, `MANUAL.md sin ${url}`).toContain(url);
      expect(tutorial, `TUTORIAL.md sin ${url}`).toContain(url);
    }
    for (const t of Object.values(HELP_TOPICS)) {
      expect(manual, `MANUAL.md sin «${t.label}»`).toContain(t.label);
      expect(tutorial, `TUTORIAL.md sin «${t.label}»`).toContain(t.label);
    }
  });
});

describe('Ayuda junto a cada dato externo', () => {
  const helps = (container: HTMLElement) => [...container.querySelectorAll('[data-help]')].map(e => e.getAttribute('data-help'));

  it('clave y cuenta de Instagram', async () => {
    vi.spyOn(api, 'instagram').mockResolvedValue({ configured: false });
    vi.spyOn(api, 'brandInstagramAccount').mockResolvedValue({});
    const key = render(<InstagramKeyCard />);
    expect(helps(key.container)).toEqual(['meta-token']);
    key.unmount();
    const account = render(<InstagramAccountCard brandId="b" />);
    await screen.findByLabelText('Cuenta de Instagram (API oficial)');
    expect(helps(account.container)).toEqual(['instagram-account-id']);
  });

  it('una ayuda por red en las cuentas de Publer', async () => {
    vi.spyOn(api, 'brandPublerAccounts').mockResolvedValue({ items: [] });
    const { container } = render(<PublerCard brandId="b" />);
    await screen.findByLabelText('Cuenta de Publer para TikTok');
    expect(helps(container)).toEqual(['publer-account-instagram', 'publer-account-tiktok']);
  });

  it('clave y cuentas de Publora', async () => {
    vi.spyOn(api, 'publora').mockResolvedValue({ configured: true, key_hint: '…1234', connections: [] });
    vi.spyOn(api, 'brandPubloraAccounts').mockResolvedValue({ items: [], dismissed: false });
    const { container } = render(<PubloraCard brandId="b" />);
    await screen.findByText('Clave …1234');
    expect(helps(container)).toEqual(['publora-key', 'publora-account']);
  });

  it('carpetas de Drive de la marca', async () => {
    vi.spyOn(api, 'brandFolders').mockResolvedValue({ items: [] });
    const { container } = render(<FolderRoles owner="brand" id="b" />);
    await screen.findByLabelText('Carpeta raíz');
    expect(helps(container)).toEqual(['drive-root', 'drive-public', 'drive-role-paths']);
  });

  it('alta de la marca: cuenta y carpeta raíz', () => {
    const { container } = render(<BrandForm onCancel={() => {}} onSaved={() => {}} />);
    expect(helps(container)).toEqual(['brand-handle', 'drive-root']);
  });
});
