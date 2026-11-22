import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { api } from './api';
import { OutcomeBadge, outcomeCounts, usePublicationActions } from './PublicationActions';
import { NextSteps } from './Recommendations';
import type { Publication, Recommendation, SlotProposal } from './model';

afterEach(() => vi.restoreAllMocks());

const pub = (id: string, o: Partial<Publication> = {}): Publication => ({
  id, promotion_id: 'p1', platform: 'INSTAGRAM', type: 'Reel', title: `Título ${id}`, scheduled_on: '2026-11-25',
  scheduled_time: '19:00', timezone: 'Europe/Madrid', status: 'DRAFT', route: 'Publer', demo: false, ...o,
});

const slot = (o: Partial<SlotProposal> = {}): SlotProposal => ({
  publication_id: 'a', reason: 'LOST_MARGIN', current_on: '2026-11-25', current_time: '19:00', new_on: '2026-11-25', new_time: '19:30',
  timezone: 'Europe/Madrid', changes_day: false, after_promotion_end: false, margin_minutes: 30, ...o,
});

function Host({ publications, recs, notify }: { publications: Publication[]; recs: Recommendation[]; notify: (m: string) => void }) {
  const actions = usePublicationActions(publications, notify);
  return <><NextSteps items={recs} onOpen={() => {}} publications={publications} onDiscard={actions.discard} onMove={actions.move} />{actions.dialogs}</>;
}

const duplicates: Recommendation = {
  id: 'duplicates:a:title', kind: 'DUPLICATE_PUBLICATIONS', title: 'Posible duplicado: «Poema»', publication_ids: ['a', 'b'], promotion_id: 'p1',
  target: { screen: 'PUBLICATIONS', promotion_id: 'p1' },
};

describe('Descartar un duplicado', () => {
  it('pide confirmación y solo entonces cancela', async () => {
    const change = vi.spyOn(api, 'changePublicationStatus').mockResolvedValue(pub('b'));
    const notify = vi.fn();
    render(<Host publications={[pub('a', { scheduled_time: '10:00' }), pub('b')]} recs={[duplicates]} notify={notify} />);
    fireEvent.click(screen.getByRole('button', { name: /Descartar esta: Título b/ }));
    expect(change).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog', { name: '¿Descartar esta publicación?' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Descartar esta' }));
    await waitFor(() => expect(change).toHaveBeenCalledWith('b', 'CANCELLED', 'REPEATED', expect.any(String)));
    await waitFor(() => expect(notify).toHaveBeenCalledWith('Descartada · Título b'));
  });

  it('volver no cambia nada', () => {
    const change = vi.spyOn(api, 'changePublicationStatus').mockResolvedValue(pub('b'));
    render(<Host publications={[pub('a'), pub('b')]} recs={[duplicates]} notify={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: /Descartar esta: Título a/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Volver' }));
    expect(change).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});

describe('Mover a la siguiente hora libre', () => {
  const tooSoon: Recommendation = {
    id: 'publication-too-soon:p1', kind: 'PUBLICATION_TOO_SOON', title: '«Título a» ya no deja 30 min de margen', publication_ids: ['a'],
    target: { screen: 'PUBLICATIONS', promotion_id: 'p1' },
  };

  it('enseña la hora actual y la nueva y no mueve hasta confirmar', async () => {
    vi.spyOn(api, 'nextFreeSlot').mockResolvedValue(slot());
    const move = vi.spyOn(api, 'moveToFreeSlot').mockResolvedValue(pub('a'));
    const notify = vi.fn();
    render(<Host publications={[pub('a')]} recs={[tooSoon]} notify={notify} />);
    fireEvent.click(screen.getByRole('button', { name: /Mover a la siguiente hora libre/ }));
    await screen.findByText('Hora actual');
    expect(screen.getByText(/jue 25 nov, 19:30|mar 25 nov, 19:30|mié 25 nov, 19:30/)).toBeTruthy();
    expect(move).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Mover' }));
    await waitFor(() => expect(move).toHaveBeenCalledWith('a', { new_on: '2026-11-25', new_time: '19:30', accept_after_end: false }));
    await waitFor(() => expect(notify).toHaveBeenCalled());
  });

  it('avisa si cambia de día y exige aceptar pasar del fin de la promoción', async () => {
    vi.spyOn(api, 'nextFreeSlot').mockResolvedValue(slot({ new_on: '2026-11-26', new_time: '00:15', changes_day: true,
      after_promotion_end: true, promotion_ends_on: '2026-11-25' }));
    const move = vi.spyOn(api, 'moveToFreeSlot').mockResolvedValue(pub('a'));
    render(<Host publications={[pub('a')]} recs={[tooSoon]} notify={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: /Mover a la siguiente hora libre/ }));
    await screen.findByText(/Cambia de día/);
    const button = screen.getByRole('button', { name: 'Mover' }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    fireEvent.click(screen.getByRole('checkbox'));
    expect(button.disabled).toBe(false);
    fireEvent.click(button);
    await waitFor(() => expect(move).toHaveBeenCalledWith('a', { new_on: '2026-11-26', new_time: '00:15', accept_after_end: true }));
  });
});

describe('Resultado de la publicación', () => {
  it('enseña estado, hora real, vía y enlace', () => {
    render(<OutcomeBadge outcome={{ result: 'PUBLISHED', label: 'Publicada', at: '2026-11-25T18:02:00Z', at_source: 'CALENDAR', route: 'API oficial', url: 'https://ig/p/1' }} />);
    expect(screen.getByText('Publicada ✓')).toBeTruthy();
    expect(screen.getByText(/API oficial/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ver post' }).getAttribute('href')).toBe('https://ig/p/1');
  });

  it('un fallo enseña el motivo y qué hacer', () => {
    render(<OutcomeBadge outcome={{ result: 'FAILED', label: 'Falló', reason: 'Publer rechazó el vídeo', advice: 'Vuelve a programarla' }} />);
    expect(screen.getByText('Falló')).toBeTruthy();
    expect(screen.getByText(/Publer rechazó el vídeo\. Vuelve a programarla/)).toBeTruthy();
  });

  it('cuenta cada resultado', () => {
    const c = outcomeCounts([
      pub('a', { outcome: { result: 'PUBLISHED', label: 'Publicada' } }), pub('b', { outcome: { result: 'UNCONFIRMED', label: 'Sin confirmar' } }),
      pub('c', { outcome: { result: 'UNCONFIRMED', label: 'Sin confirmar' } }), pub('d'),
    ]);
    expect(c).toEqual({ PUBLISHED: 1, FAILED: 0, DELAYED: 0, PENDING: 0, UNCONFIRMED: 2 });
  });
});
