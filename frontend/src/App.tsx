import { Factory } from './Factory';
import { useFactory } from './useFactory';

/** SocialHub solo se abre en este ordenador: no hay pantalla de acceso. */
export function App() {
  const { data, error, live, reload } = useFactory();
  if (error && !data) return <Centered><h1>No se puede conectar con SocialHub</h1><p>{error}</p><button className="btn primary" onClick={reload}>Reintentar</button></Centered>;
  if (!data) return <Centered><p className="src">Cargando la fábrica…</p></Centered>;
  return <Factory data={data} live={live} onRefresh={reload} />;
}

function Centered({ children }: { children: React.ReactNode }) {
  return <main className="centered">{children}</main>;
}
