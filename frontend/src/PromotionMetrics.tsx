import { useEffect, useState } from 'react';
import { api } from './api';
import type { FormatMetrics, NetworkMetrics, PromotionMetrics } from './api/types';

/** Las seis cifras que se leen de cada publicación. */
const FIGURES = [
  ['views', 'Reproducciones'], ['reach', 'Alcance'], ['likes', 'Me gusta'],
  ['comments', 'Comentarios'], ['saves', 'Guardados'], ['shares', 'Compartidos'],
] as const;

const NETWORK_LABEL: Record<NetworkMetrics['network'], string> = { INSTAGRAM: 'Instagram', TIKTOK: 'TikTok', TOTAL: 'Total' };

const WHOLE = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 });
const MEAN = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 1 });

const figure = (value: number | undefined | null, format: Intl.NumberFormat) =>
  value == null ? <span className="nodata">Sin dato</span> : format.format(value);

const publications = (n: number) => `${n} ${n === 1 ? 'publicación' : 'publicaciones'}`;

/** Pestaña «Métricas» de la promoción: totales por red y comparación por formato con medias por publicación. */
export function PromotionMetricsTab({ promotionId, source }: { promotionId: string; source?: string }) {
  const [data, setData] = useState<PromotionMetrics | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let live = true;
    setData(null);
    setError(null);
    api.promotionMetrics(promotionId)
      .then(m => { if (live) setData(m); })
      .catch(e => { if (live) setError(e instanceof Error ? e.message : String(e)); });
    return () => { live = false; };
  }, [promotionId]);

  if (error) return <p className="error" role="alert">No se pudieron leer las métricas: {error}</p>;
  if (!data) return <p className="src">Cargando métricas…</p>;

  const networks = data.by_network;
  return (
    <div className="stack">
      <div className="card">
        <h3>Métricas de la promoción</h3>
        {source ? <p className="src">{source}</p> : null}
        {data.measured === 0 ? (
          <p className="src" role="status">
            Todavía no hay métricas leídas. Las métricas se leen a los 7 días de publicar.
            {data.pending > 0 ? ` Ahora esperan su lectura ${publications(data.pending)}.` : ' No hay publicaciones esperando su lectura.'}
          </p>
        ) : (
          <>
            <p className="src">
              {publications(data.measured)} medidas
              {data.pending > 0 ? ` · ${publications(data.pending)} esperan su lectura (se leen a los 7 días de publicar)` : ''}.
              Cada cifra suma solo las publicaciones donde se pudo ver.
            </p>
            <div className="tbl-wrap"><table aria-label="Totales por red">
              <thead><tr><th>Cifra</th>{networks.map(n => <th key={n.network} className="num">{NETWORK_LABEL[n.network]}</th>)}</tr></thead>
              <tbody>
                <tr><td>Publicaciones medidas</td>{networks.map(n => <td key={n.network} className="num">{WHOLE.format(n.publications)}</td>)}</tr>
                {FIGURES.map(([key, label]) => (
                  <tr key={key}><td>{label}</td>{networks.map(n => <td key={n.network} className="num">{figure(n[key], WHOLE)}</td>)}</tr>
                ))}
              </tbody>
            </table></div>
          </>
        )}
      </div>

      {data.measured > 0 && (
        <div className="card">
          <h3>Comparación por formato y red</h3>
          <p className="src">
            Media por publicación, para poder comparar grupos de distinto tamaño. Los grupos de menos de 5 publicaciones se
            marcan «pocos datos»: la cifra se enseña igual, pero no saques conclusiones de ella.
          </p>
          <div className="tbl-wrap"><table aria-label="Comparación por formato y red">
            <thead><tr><th>Formato</th><th>Red</th><th className="num">Publicaciones</th>
              {FIGURES.map(([key, label]) => <th key={key} className="num">{label}</th>)}</tr></thead>
            <tbody>{data.by_format.map((f: FormatMetrics) => (
              <tr key={`${f.type}/${f.network}`}>
                <td>{f.type}{f.low_sample && <span className="pill low-sample">pocos datos</span>}</td>
                <td>{NETWORK_LABEL[f.network]}</td>
                <td className="num">{WHOLE.format(f.publications)}</td>
                {FIGURES.map(([key]) => <td key={key} className="num">{figure(f[key], MEAN)}</td>)}
              </tr>
            ))}</tbody>
          </table></div>
        </div>
      )}

      <div className="card"><h3>Cómo se usarán</h3>
        <ul><li>Comparar por formato y red dentro de la misma marca.</li><li>No sacar conclusiones con muestras pequeñas.</li>
          <li>Las conclusiones vuelven a la estación de investigación y quedan registradas.</li></ul>
        <p className="src">Las métricas son opcionales. SocialHub no inventa cifras: aparecen cuando llegan datos reales.</p>
      </div>
    </div>
  );
}
