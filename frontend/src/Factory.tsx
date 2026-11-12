import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { api } from './api';
import { Claude } from './Claude';
import { ControlCenter } from './ControlCenter';
import { ExternalOperations } from './ExternalOperations';
import { Backups, Closed, Halted, ShutdownButton } from './Backups';
import { Registration } from './Registration';
import { NextSteps, savedMessage, toastDuration, type SavedRef } from './Recommendations';
import type { RecommendationTarget } from './api/schema';
import { Icon } from './icons';
import {
  AI_MODELS, STATUS, type AppMode, alertTone, byPriority, normalize, shortDate, type AiModel, type Snapshot, type StationCommandType, type StatusCode,
  type Task, type TaskCommandType,
} from './model';
import { ArmoryPanel, StationPanel, TaskPanel } from './Panel';
import { Dot, Pill, prefersReducedMotion } from './ui';
import { World, openAlert, type Selection } from './World';
import { clampView, fitScale, validBox, type Box } from './viewLimits';

interface View { s: number; tx: number; ty: number }
const clampS = (s: number) => Math.max(0.08, Math.min(3.5, s));
/** Lo que cuenta como contenido del mapa: con esto se encuadra y se limita el desplazamiento. */
const CONTENT = '.st,.hub,.res,.node,.seglabel';
const lodFor = (s: number) => (s < 0.72 ? 1 : s < 1.55 ? 2 : 3);

interface Hit { kind: string; label: string; s?: StatusCode; go: () => void }

export function Factory({ data, live, onRefresh }: {
  data: Snapshot; live: boolean; onRefresh: () => void;
}) {
  const vp = useRef<HTMLElement>(null);
  const world = useRef<HTMLDivElement>(null);
  const view = useRef<View>({ s: 0.5, tx: 0, ty: 0 });
  const anim = useRef<number>(0);
  // Caja del contenido (nivel de detalle 1) y límites de la vista; se rellenan al encuadrar y al cambiar los datos
  const bounds = useRef<Box | null>(null);
  const clampRef = useRef<(v: View) => View>(v => v);
  const [lod, setLod] = useState(1);
  const [zoom, setZoom] = useState(50);
  const [sel, setSel] = useState<Selection>({ type: null, id: null });
  const [cc, setCc] = useState<{ promo: string; tab: string; hl: string | null; filter?: string } | null>(null);
  const [ops, setOps] = useState(false);
  const [claude, setClaude] = useState(false);
  const [copias, setCopias] = useState(false);
  const [halted, setHalted] = useState<string | null>(null);
  const [closed, setClosed] = useState(false);
  const [reg, setReg] = useState<{ kind: 'brand' | 'promotion'; id: string | null; brandId?: string; focus?: 'folders' | 'publora' | 'publer' } | false | null>(false);
  const [demoAvailable, setDemoAvailable] = useState(true);
  useEffect(() => { api.appMode().then(m => { setDemoAvailable(m.demo_available); setHalted(m.halted_reason ?? null); }).catch(() => {}); }, []);
  // Abrir una promoción cierra el registro de operaciones
  useEffect(() => { if (cc) { setOps(false); setClaude(false); setCopias(false); } }, [cc]);
  const [toast, setToast] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [hitIndex, setHitIndex] = useState(0);
  const fitted = useRef(false);

  const brands = useMemo(() => new Map(data.brands.map(b => [b.id, b])), [data.brands]);
  const brandOf = useCallback((pid?: string | null) => {
    const p = data.promotions.find(x => x.id === pid);
    return p ? brands.get(p.brand_id) : undefined;
  }, [data.promotions, brands]);

  // ---------- vista: zoom, desplazamiento y zoom semántico ----------
  const apply = useCallback(() => {
    view.current = clampRef.current(view.current);
    const v = view.current;
    if (world.current) world.current.style.transform = `translate(${v.tx}px,${v.ty}px) scale(${v.s})`;
    const l = lodFor(v.s);
    if (vp.current && vp.current.dataset.lod !== String(l)) vp.current.dataset.lod = String(l);
    setLod(l);
    setZoom(Math.round(v.s * 100));
  }, []);

  const animateTo = useCallback((t: View) => {
    cancelAnimationFrame(anim.current);
    if (prefersReducedMotion()) { view.current = t; apply(); return; }
    const from = { ...view.current }, t0 = performance.now(), dur = 480;
    const step = (n: number) => {
      let p = Math.min(1, (n - t0) / dur); p = 1 - Math.pow(1 - p, 3);
      view.current = { s: from.s + (t.s - from.s) * p, tx: from.tx + (t.tx - from.tx) * p, ty: from.ty + (t.ty - from.ty) * p };
      apply();
      if (p < 1) anim.current = requestAnimationFrame(step);
    };
    anim.current = requestAnimationFrame(step);
  }, [apply]);

  const panelOpen = sel.type !== null;
  const avail = useCallback(() => {
    const el = vp.current!;
    const narrow = el.clientWidth <= 760;
    return { w: el.clientWidth - (panelOpen && !narrow ? 450 : 0), h: panelOpen && narrow ? el.clientHeight * 0.38 : el.clientHeight };
  }, [panelOpen]);

  /** Mide el contenido con un nivel de detalle concreto, sin que se vea el cambio. */
  const measure = useCallback((level: number, selector: string) => {
    const el = vp.current!, w = world.current!, prev = el.dataset.lod;
    el.dataset.lod = String(level);
    const wr = w.getBoundingClientRect(), s = view.current.s;
    const b = { x0: 1e9, y0: 1e9, x1: -1e9, y1: -1e9 };
    w.querySelectorAll(selector).forEach(e => {
      const q = e.getBoundingClientRect();
      b.x0 = Math.min(b.x0, (q.left - wr.left) / s); b.y0 = Math.min(b.y0, (q.top - wr.top) / s);
      b.x1 = Math.max(b.x1, (q.right - wr.left) / s); b.y1 = Math.max(b.y1, (q.bottom - wr.top) / s);
    });
    el.dataset.lod = prev;
    return b;
  }, []);

  const pads = useCallback(() => {
    const wide = vp.current!.clientWidth > 760;
    return { pt: wide ? 58 : 16, pb: wide ? 52 : 16 };
  }, []);

  // Límites: el contenido no sale del todo de la vista y no se aleja más de lo que se ve al «Encuadrar todo»
  const minScale = useCallback(() => {
    const b = bounds.current;
    if (!vp.current || !validBox(b)) return 0.08;
    const { pt, pb } = pads();
    return fitScale(b, avail(), pt, pb);
  }, [avail, pads]);
  const clampZoom = useCallback((s: number) => Math.max(minScale(), Math.min(3.5, s)), [minScale]);
  clampRef.current = v => {
    const b = bounds.current;
    return vp.current && validBox(b) ? clampView(v, b, avail(), minScale()) : v;
  };

  const fit = useCallback(() => {
    if (!vp.current || !world.current) return;
    const a = avail(), { pt, pb } = pads();
    const b = measure(1, CONTENT);
    bounds.current = b;
    const w = b.x1 - b.x0, h = b.y1 - b.y0, ah = a.h - pt - pb;
    const s = validBox(b) ? fitScale(b, a, pt, pb) : clampS(0.7);
    animateTo({ s, tx: (a.w - w * s) / 2 - b.x0 * s, ty: pt + (ah - h * s) / 2 - b.y0 * s });
  }, [avail, measure, animateTo]);

  const flyToStation = useCallback((id: string, scale = 1.15) => {
    if (!vp.current) return;
    const a = avail(), s = clampS(scale);
    const b = measure(lodFor(s), `.node[data-st="${id}"],.st[data-st="${id}"]`);
    const ty = (b.y1 - b.y0) * s <= a.h - 60 ? a.h / 2 - (b.y0 + b.y1) / 2 * s : 50 - b.y0 * s;
    animateTo({ s, tx: a.w / 2 - (b.x0 + b.x1) / 2 * s, ty });
  }, [avail, measure, animateTo]);

  const zoomAt = useCallback((cx: number, cy: number, f: number) => {
    cancelAnimationFrame(anim.current);
    const v = view.current, s = clampZoom(v.s * f), k = s / v.s;
    view.current = { s, tx: cx - (cx - v.tx) * k, ty: cy - (cy - v.ty) * k };
    apply();
  }, [apply, clampZoom]);

  useLayoutEffect(() => {
    if (!fitted.current && world.current) { fitted.current = true; apply(); fit(); }
  }, [apply, fit]);

  // El contenido cambia con los datos (promociones, tarjetas): se vuelve a medir para los límites
  useLayoutEffect(() => {
    if (fitted.current && world.current && vp.current) bounds.current = measure(1, CONTENT);
  }, [data, measure]);

  // La franja de «Qué hacer ahora» cambia el alto del mapa: se vuelve a encuadrar al aparecer o desaparecer
  const hasNext = data.recommendations.length > 0;
  const hadNext = useRef(hasNext);
  useEffect(() => {
    if (hadNext.current === hasNext) return;
    hadNext.current = hasNext;
    const id = requestAnimationFrame(fit);
    return () => cancelAnimationFrame(id);
  }, [hasNext, fit]);

  useEffect(() => {
    let t: number;
    const onResize = () => { clearTimeout(t); t = window.setTimeout(fit, 150); };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [fit]);

  // Rueda, arrastre y pellizco
  useEffect(() => {
    const el = vp.current!;
    const ptrs = new Map<number, { x: number; y: number }>();
    let drag: { x: number; y: number; tx: number; ty: number; moved: boolean } | null = null;
    let pinch: { d: number; cx: number; cy: number; s: number; tx: number; ty: number } | null = null;
    let suppress = false;
    const skip = (t: EventTarget | null) => (t as HTMLElement | null)?.closest?.('.panel,.cc,.float,input,select,.results');
    const down = (e: PointerEvent) => {
      if (skip(e.target) || (e.pointerType === 'mouse' && e.button !== 0)) return;
      ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (ptrs.size === 1) drag = { x: e.clientX, y: e.clientY, tx: view.current.tx, ty: view.current.ty, moved: false };
      if (ptrs.size === 2) {
        const [p1, p2] = [...ptrs.values()], r = el.getBoundingClientRect();
        pinch = { d: Math.hypot(p1.x - p2.x, p1.y - p2.y), cx: (p1.x + p2.x) / 2 - r.left, cy: (p1.y + p2.y) / 2 - r.top, ...view.current };
        drag = null;
      }
    };
    const move = (e: PointerEvent) => {
      if (!ptrs.has(e.pointerId)) return;
      ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pinch && ptrs.size === 2) {
        const [p1, p2] = [...ptrs.values()], s = clampZoom(pinch.s * Math.hypot(p1.x - p2.x, p1.y - p2.y) / pinch.d), k = s / pinch.s;
        view.current = { s, tx: pinch.cx - (pinch.cx - pinch.tx) * k, ty: pinch.cy - (pinch.cy - pinch.ty) * k };
        cancelAnimationFrame(anim.current); apply(); suppress = true; return;
      }
      if (drag) {
        const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
        if (!drag.moved && Math.hypot(dx, dy) > 5) { drag.moved = true; el.classList.add('grabbing'); try { el.setPointerCapture(e.pointerId); } catch { /* */ } }
        if (drag.moved) { cancelAnimationFrame(anim.current); view.current = { ...view.current, tx: drag.tx + dx, ty: drag.ty + dy }; apply(); }
      }
    };
    const up = (e: PointerEvent) => {
      if (drag?.moved) suppress = true;
      ptrs.delete(e.pointerId);
      if (ptrs.size < 2) pinch = null;
      if (!ptrs.size) { drag = null; el.classList.remove('grabbing'); setTimeout(() => { suppress = false; }, 0); }
    };
    const wheel = (e: WheelEvent) => {
      if (skip(e.target)) return;
      e.preventDefault();
      const r = el.getBoundingClientRect();
      if (!e.ctrlKey && e.deltaMode === 0 && Math.abs(e.deltaX) > Math.abs(e.deltaY) * 0.5 && Math.abs(e.deltaY) < 50) {
        cancelAnimationFrame(anim.current);
        view.current = { ...view.current, tx: view.current.tx - e.deltaX, ty: view.current.ty - e.deltaY }; apply(); return;
      }
      zoomAt(e.clientX - r.left, e.clientY - r.top, Math.exp(-e.deltaY * (e.ctrlKey ? 0.012 : e.deltaMode === 1 ? 0.05 : 0.0016)));
    };
    const click = (e: MouseEvent) => { if (suppress && el.contains(e.target as Node)) { e.stopPropagation(); e.preventDefault(); suppress = false; } };
    el.addEventListener('pointerdown', down); el.addEventListener('pointermove', move);
    el.addEventListener('pointerup', up); el.addEventListener('pointercancel', up);
    el.addEventListener('wheel', wheel, { passive: false });
    document.addEventListener('click', click, true);
    return () => {
      el.removeEventListener('pointerdown', down); el.removeEventListener('pointermove', move);
      el.removeEventListener('pointerup', up); el.removeEventListener('pointercancel', up);
      el.removeEventListener('wheel', wheel); document.removeEventListener('click', click, true);
    };
  }, [apply, zoomAt, clampZoom]);

  // ---------- navegación ----------
  const goStation = useCallback((id: string) => {
    setCc(null); setSel({ type: 'station', id });
    requestAnimationFrame(() => flyToStation(id));
  }, [flyToStation]);

  const goTask = useCallback((id: string) => {
    const t = data.tasks.find(x => x.id === id);
    if (!t) return;
    setCc(null); setSel({ type: 'task', id });
    requestAnimationFrame(() => flyToStation(t.station_id, Math.max(1.7, view.current.s)));
  }, [data.tasks, flyToStation]);

  const goPublication = useCallback((id: string) => {
    const pub = data.publications.find(x => x.id === id);
    if (!pub) return;
    setSel({ type: null, id: null }); setCc({ promo: pub.promotion_id, tab: 'publicaciones', hl: id });
  }, [data.publications]);
  /** Lleva a la pantalla donde se resuelve una recomendación de «Qué hacer ahora». */
  const goRecommendation = useCallback((t: RecommendationTarget) => {
    setSel({ type: null, id: null }); setCc(null); setOps(false); setClaude(false); setCopias(false); setReg(false);
    if (t.screen === 'PROPOSALS' && t.promotion_id) setCc({ promo: t.promotion_id, tab: 'publicaciones', hl: null, filter: 'PROPOSAL' });
    else if ((t.screen === 'BRAND_FOLDERS' || t.screen === 'PUBLORA' || t.screen === 'PUBLER') && t.brand_id) {
      setReg({ kind: 'brand', id: t.brand_id, focus: t.screen === 'PUBLORA' ? 'publora' : t.screen === 'PUBLER' ? 'publer' : 'folders' });
    }
    // Con promotion_id (activar una planificada o en pausa) se abre esa promoción; sin él, el alta de una nueva
    else if (t.screen === 'NEW_PROMOTION' && t.brand_id) {
      setReg(t.promotion_id ? { kind: 'promotion', id: t.promotion_id } : { kind: 'promotion', id: null, brandId: t.brand_id });
    }
    else if (t.screen === 'BACKUPS') setCopias(true);
    else if (t.screen === 'CLAUDE') setClaude(true);
  }, []);
  const home = useCallback(() => { setCc(null); setSel({ type: null, id: null }); requestAnimationFrame(fit); }, [fit]);

  const setLevel = (l: number) => {
    if (l === 1) { fit(); return; }
    const target = l === 2 ? 1.1 : 1.7;
    const focus = sel.type === 'task' ? data.tasks.find(t => t.id === sel.id)?.station_id
      : sel.type === 'station' ? sel.id : data.tasks.find(t => t.status === 'RUNNING')?.station_id;
    if (focus) { flyToStation(focus, target); return; }
    const a = avail(), k = target / view.current.s;
    animateTo({ s: target, tx: a.w / 2 - (a.w / 2 - view.current.tx) * k, ty: a.h / 2 - (a.h / 2 - view.current.ty) * k });
  };

  // ---------- acciones ----------
  const toastTimer = useRef<number | undefined>(undefined);
  // Un aviso nuevo cancela el temporizador del anterior, para que este no lo corte antes de tiempo
  const say = useCallback((m: string, ms = 2800) => {
    window.clearTimeout(toastTimer.current);
    setToast(m);
    toastTimer.current = window.setTimeout(() => setToast(null), ms);
  }, []);
  /** Al guardar, el paso siguiente sale del siguiente estado de la fábrica, no de lo que la interfaz supone. */
  const saved = useCallback(async (message: string, ref?: SavedRef) => {
    if (!ref) { say(message, message.includes('. ') ? 6000 : 2800); onRefresh(); return; }
    let text = message;
    try { text = savedMessage(message, ref, (await api.factory()).recommendations); } catch { /* se queda el mensaje simple */ }
    // Con siguiente paso o estado de propuestas el mensaje es más largo: se deja más tiempo para leerlo.
    say(text, toastDuration(message, text));
    onRefresh();
  }, [onRefresh, say]);
  /** «No uso Publora»: guarda el descarte y refresca para que la recomendación desaparezca. */
  const dismissPublora = useCallback(async (brandId: string) => {
    try {
      await api.setBrandPubloraDismissed(brandId, true);
      say('Anotado: no se te recomendará Publora para esa marca. Puedes volver a activarla en su tarjeta de Publora');
      onRefresh();
    } catch (e) {
      say(e instanceof Error ? e.message : 'No se pudo guardar.');
    }
  }, [onRefresh, say]);
  /** «Pedir contenido ya»: un spec urgente (a mano) de las publicaciones sin contenido de la promoción. */
  const requestContent = useCallback(async (promotionId: string, publicationIds: string[]) => {
    try {
      await api.orderFromClaude(promotionId, {
        type: 'WEEKLY_SPEC',
        title: `spec.json por lotes · ${publicationIds.length} ${publicationIds.length === 1 ? 'publicación' : 'publicaciones'}`,
        goal: 'Prepara el spec.json de piezas de estas publicaciones sin contenido (las de «inputs.publications», con sus fechas), '
          + 'con el material de sus carpetas y sus normas.',
        inputs: publicationIds,
      });
      say('Spec pedido a Claude como urgente: se hace en la siguiente pasada');
      onRefresh();
    } catch (e) {
      say(e instanceof Error ? e.message : 'No se pudo pedir el contenido.');
    }
  }, [onRefresh, say]);
  /** «Pasar a Publora» en una cuenta de Publer llena: manda ya las retenidas y refresca. */
  const alertAction = useCallback(async (action: NonNullable<Snapshot['alerts'][number]['action']>) => {
    try {
      const r = await api.moveHeldToPublora({ account_name: action.account_name, platform: action.platform });
      say(r.moved === 1 ? '1 publicación pasada a Publora' : `${r.moved} publicaciones pasadas a Publora`);
      onRefresh();
    } catch (e) {
      say(e instanceof Error ? e.message : 'No se pudo pasar a Publora.');
    }
  }, [onRefresh, say]);
  const runTask = useCallback(async (t: Task, type: TaskCommandType, model?: AiModel) => {
    try {
      await api.taskCommand(t.id, { type, model });
      const label = type === 'CHANGE_AGENT'
        ? `Agente: Claude ${AI_MODELS.find(m => m.id === model)?.label ?? model}`
        : t.available_commands.find(a => a.type === type)?.label ?? 'Hecho';
      say(`${label} · ${t.title}`);
      onRefresh();
    } catch (e) {
      say(e instanceof Error ? e.message : 'No se pudo completar la acción.');
    }
  }, [onRefresh, say]);

  const switchMode = useCallback(async (mode: AppMode) => {
    try {
      await api.changeAppMode(mode);
      // Lo seleccionado pertenece a la otra base de datos
      setSel({ type: null, id: null }); setCc(null); setOps(false); setReg(false); setClaude(false);
      say(mode === 'DEMO' ? 'Modo demo: datos de ejemplo' : 'Modo real');
      onRefresh();
    } catch (e) { say(e instanceof Error ? e.message : 'No se pudo cambiar de modo.'); }
  }, [onRefresh, say]);

  const runStation = useCallback(async (id: string, type: StationCommandType) => {
    try {
      await api.stationCommand(id, type);
      say(type === 'PAUSE' ? 'Estación pausada' : 'Estación reanudada');
      onRefresh();
    } catch (e) { say(e instanceof Error ? e.message : 'No se pudo completar la acción.'); }
  }, [onRefresh, say]);

  // ---------- teclado ----------
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement).closest('input,select,textarea')) return;
      const a = avail();
      if (e.key === '0') fit();
      if (e.key === '+' || e.key === '=') zoomAt(a.w / 2, a.h / 2, 1.25);
      if (e.key === '-') zoomAt(a.w / 2, a.h / 2, 0.8);
      if (e.key === 'Escape') { if (cc) setCc(null); else if (sel.type) setSel({ type: null, id: null }); }
    };
    document.addEventListener('keydown', key);
    return () => document.removeEventListener('keydown', key);
  }, [avail, fit, zoomAt, cc, sel.type]);

  // ---------- búsqueda ----------
  const hits: Hit[] = useMemo(() => {
    const k = normalize(query.trim());
    if (!k) return [];
    const out: Hit[] = [];
    data.stations.forEach(s => normalize(`${s.name} ${s.description}`).includes(k) && out.push({ kind: 'Estación', label: s.name, s: s.status, go: () => goStation(s.id) }));
    data.promotions.forEach(p => normalize(`${p.name} ${brands.get(p.brand_id)?.name}`).includes(k)
      && out.push({ kind: 'Promoción', label: `${brands.get(p.brand_id)?.name} · ${p.name}`, go: () => { setSel({ type: null, id: null }); setCc({ promo: p.id, tab: 'resumen', hl: null }); } }));
    data.tasks.forEach(t => normalize(`${t.title} ${t.goal}`).includes(k)
      && out.push({ kind: `Tarea · ${data.stations.find(s => s.id === t.station_id)?.name}`, label: t.title, s: t.status, go: () => goTask(t.id) }));
    data.publications.forEach(x => normalize(`${x.title} ${x.type}`).includes(k)
      && out.push({ kind: `Publicación · ${brandOf(x.promotion_id)?.name} · ${shortDate(x.scheduled_on)}`, label: x.title,
        go: () => { setSel({ type: null, id: null }); setCc({ promo: x.promotion_id, tab: 'publicaciones', hl: x.id }); } }));
    data.armory_items.forEach(r => normalize(`${r.name} ${r.description}`).includes(k)
      && out.push({ kind: 'Herramienta', label: r.name, s: r.status, go: () => { setCc(null); setSel({ type: 'armory', id: r.id }); } }));
    return out.slice(0, 10);
  }, [query, data, brands, brandOf, goStation, goTask]);
  const pick = (h?: Hit) => { setQuery(''); h?.go(); };

  // ---------- render ----------
  const selStation = sel.type === 'station' ? data.stations.find(s => s.id === sel.id) : undefined;
  const selTask = sel.type === 'task' ? data.tasks.find(t => t.id === sel.id) : undefined;
  const selArmory = sel.type === 'armory' ? data.armory_items.find(a => a.id === sel.id) : undefined;
  const ccPromotion = cc ? data.promotions.find(p => p.id === cc.promo) : undefined;

  const crumbs: { label: string; go?: () => void }[] = [{ label: 'Fábrica', go: home }];
  if (ccPromotion) {
    crumbs.push({ label: brands.get(ccPromotion.brand_id)?.name ?? '' }, { label: ccPromotion.name });
  } else if (selStation) crumbs.push({ label: selStation.name });
  else if (selTask) crumbs.push({ label: data.stations.find(s => s.id === selTask.station_id)?.name ?? '', go: () => goStation(selTask.station_id) }, { label: selTask.title });
  else if (selArmory) crumbs.push({ label: selArmory.name });

  return (
    <>
      <header className="top">
        <button className="mark" onClick={home} aria-label="Volver a la vista general"><i />SocialHub</button>
        <nav className="crumbs" aria-label="Ruta de navegación">
          {crumbs.map((c, i) => (
            <span key={i} style={{ display: 'contents' }}>
              {i > 0 && <span className="sep">/</span>}
              <button className={i === crumbs.length - 1 ? 'cur' : ''} onClick={c.go}>{c.label}</button>
            </span>
          ))}
        </nav>
        <div className="seg mode-seg" role="group" aria-label="Modo">
          <button className={!data.demo ? 'on' : ''} aria-pressed={!data.demo} onClick={() => data.demo && switchMode('REAL')}
            title="Tus datos reales">Real</button>
          {demoAvailable && <button className={data.demo ? 'on demo' : ''} aria-pressed={data.demo} onClick={() => !data.demo && switchMode('DEMO')}
            title="Datos de ejemplo en una base de datos aparte. Nada actúa sobre servicios reales.">Demo</button>}
        </div>
        <span className="demo" title={live ? 'Recibiendo cambios en directo' : 'Sin conexión en directo: se recarga al actuar'}>{live ? '● En directo' : '○ Sin directo'}</span>
        <div className="search">
          <Icon name="search" />
          <input id="q" type="search" placeholder="Buscar estación, tarea, publicación…" autoComplete="off" aria-label="Buscar"
            value={query} onChange={e => { setQuery(e.target.value); setHitIndex(0); }}
            onKeyDown={e => {
              if (e.key === 'Escape') setQuery('');
              if (!hits.length) return;
              if (e.key === 'ArrowDown') { e.preventDefault(); setHitIndex((hitIndex + 1) % hits.length); }
              if (e.key === 'ArrowUp') { e.preventDefault(); setHitIndex((hitIndex - 1 + hits.length) % hits.length); }
              if (e.key === 'Enter') { e.preventDefault(); pick(hits[hitIndex]); }
            }} />
          {query.trim() && (
            <div className="results">
              {hits.length ? hits.map((h, i) => (
                <button key={i} className={i === hitIndex ? 'on' : ''} onClick={() => pick(h)}>
                  {h.s && <Dot s={h.s} />}<span><small>{h.kind}</small>{h.label}</span>
                </button>
              )) : <div className="none">Sin resultados.</div>}
            </div>
          )}
        </div>
        <div className="ctrls">
          <div className="seg" role="group" aria-label="Nivel de detalle">
            {['Fábrica', 'Estación', 'Tarea'].map((l, i) => <button key={l} className={lod === i + 1 ? 'on' : ''} onClick={() => setLevel(i + 1)}>{l}</button>)}
          </div>
          <button className="ibtn" onClick={() => { const a = avail(); zoomAt(a.w / 2, a.h / 2, 0.8); }} aria-label="Alejar"><Icon name="minus" /></button>
          <span className="zpct mono">{zoom}%</span>
          <button className="ibtn" onClick={() => { const a = avail(); zoomAt(a.w / 2, a.h / 2, 1.25); }} aria-label="Acercar"><Icon name="plus" /></button>
          <button className="ibtn" onClick={fit} aria-label="Encuadrar todo" title="Encuadrar todo (0)"><Icon name="fit" /></button>
          <button className={`ibtn${claude ? ' on' : ''}`} onClick={() => { setCc(null); setOps(false); setReg(false); setCopias(false); setClaude(true); }} title="Encargos a Claude: ajustes, límite diario y registro">Claude</button>
          <button className="ibtn" onClick={() => { setCc(null); setOps(false); setClaude(false); setCopias(false); setReg(null); }} title="Dar de alta y editar marcas y promociones">Marcas</button>
          <button className={`ibtn${ops ? ' on' : ''}`} onClick={() => { setCc(null); setReg(false); setClaude(false); setCopias(false); setOps(true); }} title="Operaciones externas: llamadas a PostgreSQL, Drive, Publer… y sus fallos">Operaciones</button>
          <button className={`ibtn${copias ? ' on' : ''}`} onClick={() => { setCc(null); setReg(false); setClaude(false); setOps(false); setCopias(true); }} title="Copias de seguridad: carpeta, copia ahora y restaurar">Copias</button>
          <ShutdownButton onClosed={() => setClosed(true)} />
        </div>
      </header>

      <div className="stage">
      <NextSteps items={data.recommendations} onOpen={goRecommendation} onDismissPublora={dismissPublora} onRequestContent={requestContent} />
      <main className="vp" ref={vp} data-lod="1">
        <div className="world" ref={world}>
          <World data={data} sel={sel} onStation={goStation} onTask={goTask}
            onTaskAction={(t, a) => (t.available_commands.find(x => x.type === a)?.needs_confirmation ? goTask(t.id) : runTask(t, a))}
            onArmory={id => { setCc(null); setSel({ type: 'armory', id }); }}
            onPublication={goPublication}
            onAlertAction={alertAction}
            onPromotion={id => { setSel({ type: null, id: null }); setCc({ promo: id, tab: 'resumen', hl: null }); }}
            onRegister={() => setReg(null)} />
        </div>

        {data.alerts.length > 0 && (
          <div className="float avisos" aria-label="Avisos">
            <h4>Atención</h4>
            {byPriority(data.alerts).slice(0, 3).map(a => (
              <button key={`${a.kind}|${a.target_id}|${a.title}`} className={`aviso s-${alertTone(a.kind)}`} title={a.detail}
                onClick={() => openAlert(a, { onTask: goTask, onStation: goStation, onPublication: goPublication, onArmory: id => { setCc(null); setSel({ type: 'armory', id }); } })}>
                <Dot s={a.kind} /><span><b>{a.title}</b> · {a.detail}</span>
              </button>
            ))}
            {data.alerts.length > 3 && <span className="src">+{data.alerts.length - 3}</span>}
          </div>
        )}
        <div className="float legend" aria-label="Leyenda de estados">
          {(['OK', 'RUNNING', 'WAITING', 'BLOCKED', 'FAILED', 'IDLE', 'CANCELLED'] as StatusCode[]).map(s => <Pill key={s} s={s} label={STATUS[s].label} />)}
        </div>
        <div className="float hint">Rueda: zoom · Arrastra: mover · Clic: detalle · <span className="mono">0</span>: encuadrar</div>

        {selStation && (
          <StationPanel s={selStation} index={data.stations.indexOf(selStation)} tasks={data.tasks.filter(t => t.station_id === selStation.id)}
            brandOf={brandOf} onClose={() => setSel({ type: null, id: null })} onTask={goTask}
            onPause={() => runStation(selStation.id, 'PAUSE')} onResume={() => runStation(selStation.id, 'RESUME')}
            onZoom={() => flyToStation(selStation.id, 1.7)} data={data} onMessage={m => { say(m); onRefresh(); }} />
        )}
        {selTask && (
          <TaskPanel key={selTask.id} t={selTask} station={data.stations.find(s => s.id === selTask.station_id)} brand={brandOf(selTask.promotion_id)}
            tasks={data.tasks} demo={data.demo} onClose={() => setSel({ type: null, id: null })} onTask={goTask}
            onAction={(a, model) => runTask(selTask, a, model)} />
        )}
        {selArmory && <ArmoryPanel item={selArmory} data={data} onClose={() => setSel({ type: null, id: null })} onStation={goStation} onRefresh={onRefresh} />}

        {cc && ccPromotion && (
          <ControlCenter key={cc.promo + cc.tab + (cc.hl ?? '') + (cc.filter ?? '')} p={ccPromotion} brand={brands.get(ccPromotion.brand_id)} data={data}
            initialTab={cc.tab} initialFilter={cc.filter} highlight={cc.hl} onClose={() => setCc(null)} onTask={goTask}
            onEdit={() => { const id = cc.promo; setCc(null); setReg({ kind: 'promotion', id }); }} onMessage={say} />
        )}
        {ops && <ExternalOperations onClose={() => setOps(false)} />}
        {copias && <Backups onClose={() => setCopias(false)} onMessage={say} onHalted={setHalted} />}
        {halted && !closed && <Halted reason={halted} />}
        {closed && <Closed />}
        {claude && <Claude data={data} onClose={() => setClaude(false)} onMessage={say} onTask={id => { setClaude(false); goTask(id); }} />}
        {reg !== false && (
          <Registration data={data} initial={reg} onClose={() => setReg(false)}
            onSaved={saved} />
        )}
        {toast && <div className="toast" role="status">{toast}</div>}
      </main>
      </div>
    </>
  );
}
