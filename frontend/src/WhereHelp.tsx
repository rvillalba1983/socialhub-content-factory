import { HELP_TOPICS, type HelpLink, type HelpTopicId } from './helpTopics';

function ExternalLink({ link }: { link: HelpLink }) {
  return (
    <a href={link.url} target="_blank" rel="noopener noreferrer">
      {link.label}<span aria-hidden="true"> ↗</span><span className="sr-only"> (se abre en una pestaña nueva)</span>
    </a>
  );
}

/**
 * «¿Dónde lo saco?»: desplegable que va junto al campo con los pasos para conseguir el dato y los enlaces directos
 * (se abren en una pestaña nueva). Empieza cerrado. El contenido sale de `helpTopics.ts`.
 */
export function WhereHelp({ topic }: { topic: HelpTopicId }) {
  const t = HELP_TOPICS[topic];
  return (
    <details className="where-help" data-help={topic}>
      <summary>¿Dónde lo saco?</summary>
      <div className="where-body" role="group" aria-label={`Dónde sacar: ${t.label}`}>
        <p className="where-purpose"><b>{t.label}.</b> {t.purpose}</p>
        <ol>
          {t.steps.map((s, i) => (
            <li key={i}>{typeof s === 'string' ? s : <>{s.text} <ExternalLink link={s.link} /></>}</li>
          ))}
        </ol>
        {t.warning && <p className="where-warning">{t.warning}</p>}
        {t.links && t.links.length > 0 && (
          <ul className="where-links" aria-label="Enlaces útiles">
            {t.links.map(l => <li key={l.url}><ExternalLink link={l} /></li>)}
          </ul>
        )}
      </div>
    </details>
  );
}
