import { useEffect, useState } from 'react';
import { api } from './api';
import { FOLDER_ROLES } from './model';

/** Lo que le falta por completar a la marca. Cada aviso lleva a su tarjeta (`data-card`). */
export type BrandNotice = { id: string; text: string; card: string; action: string; dismiss?: { label: string; run: () => Promise<void> } };

export function goToCard(card: string) {
  document.querySelector(`[data-card="${card}"]`)?.scrollIntoView?.({ block: 'start', behavior: 'smooth' });
}

/** Calcula los avisos de la marca. Lo que no se puede leer (error de red) no genera aviso: no se inventa. */
export async function loadBrandNotices(brandId: string): Promise<BrandNotice[]> {
  const settled = await Promise.allSettled([
    api.brandIdentity(brandId), api.brandFolders(brandId), api.brandPublerAccounts(brandId), api.instagram(),
    api.brandInstagramAccount(brandId), api.brandPublicShare(brandId),
  ]);
  const [identity, folders, publer, meta, igAccount, share] = settled.map(r => (r.status === 'fulfilled' ? r.value : null)) as [
    Awaited<ReturnType<typeof api.brandIdentity>> | null, Awaited<ReturnType<typeof api.brandFolders>> | null,
    Awaited<ReturnType<typeof api.brandPublerAccounts>> | null, Awaited<ReturnType<typeof api.instagram>> | null,
    Awaited<ReturnType<typeof api.brandInstagramAccount>> | null, { shared: boolean } | null,
  ];
  const out: BrandNotice[] = [];

  if (identity) {
    const missing = [identity.tone.trim() === '' && 'el tono', identity.audience.trim() === '' && 'el público'].filter(Boolean);
    if (missing.length) {
      out.push({ id: 'identity', card: 'identity', action: 'Ir a la identidad',
        text: `Falta rellenar la identidad de la marca: ${missing.join(' y ')}.` });
    }
  }
  if (folders) {
    const byRole = new Map(folders.items.map(f => [f.role, f]));
    const unset = FOLDER_ROLES.filter(([role]) => !byRole.has(role)).map(([, label]) => label);
    const down = FOLDER_ROLES.filter(([role]) => byRole.get(role) && !byRole.get(role)!.available).map(([, label]) => label);
    if (unset.length) {
      out.push({ id: 'folders-unset', card: 'folders', action: 'Ir a las carpetas',
        text: `Carpetas sin configurar: ${unset.join(', ')}.` });
    }
    if (down.length) {
      out.push({ id: 'folders-down', card: 'folders', action: 'Ir a las carpetas',
        text: `Carpetas no disponibles ahora: ${down.join(', ')}. ¿Está la unidad de Drive abierta?` });
    }
  }
  if (publer) {
    const has = new Set(publer.items.filter(a => a.account_name.trim() !== '').map(a => a.platform));
    for (const [platform, label] of [['INSTAGRAM', 'Instagram'], ['TIKTOK', 'TikTok']] as const) {
      if (!has.has(platform)) {
        out.push({ id: `publer-${platform}`, card: 'publer', action: 'Ir a Publer',
          text: `Falta indicar la cuenta de Publer de ${label}.` });
      }
    }
  }
  if (meta?.configured && igAccount && !igAccount.ig_user_id) {
    out.push({ id: 'instagram-account', card: 'instagram-account', action: 'Ir a Instagram',
      text: 'Falta indicar la cuenta de Instagram para la API oficial.' });
  }
  const orders = folders?.items.find(f => f.role === 'ORDERS');
  if (meta?.configured && igAccount?.ig_user_id && orders && share && !share.shared) {
    out.push({
      id: 'public-share', card: 'folders', action: 'Ir a las carpetas',
      text: 'Comparte la carpeta «publico» de esta marca con «cualquier persona con el enlace» (SocialHub no puede comprobarlo).',
      dismiss: { label: 'Ya está compartida', run: async () => { await api.setBrandPublicShare(brandId, true); } },
    });
  }
  return out;
}

/** Bloque de avisos de arriba de la ficha de la marca. Si no falta nada, no sale. `version` fuerza releer tras guardar. */
export function BrandNotices({ brandId, version }: { brandId: string; version: number }) {
  const [notices, setNotices] = useState<BrandNotice[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let cancelled = false;
    loadBrandNotices(brandId).then(n => { if (!cancelled) setNotices(n); });
    return () => { cancelled = true; };
  }, [brandId, version, reload]);

  if (notices.length === 0 && !error) return null;
  return (
    <section className="card brand-notices" aria-label="Avisos de la marca">
      <h3>Te falta por completar</h3>
      <ul>
        {notices.map(n => (
          <li key={n.id}>
            <span>{n.text}</span>
            <span className="notice-actions">
              <button type="button" className="btn" onClick={() => goToCard(n.card)}>{n.action}</button>
              {n.dismiss && (
                <button type="button" className="btn" onClick={() => {
                  setError(null);
                  n.dismiss!.run().then(() => setReload(r => r + 1)).catch(e => setError(e instanceof Error ? e.message : String(e)));
                }}>{n.dismiss.label}</button>
              )}
            </span>
          </li>
        ))}
      </ul>
      {error && <p className="error" role="alert">{error}</p>}
    </section>
  );
}
