import { Link } from 'react-router'
import { useArtistasActivos } from '../funcionalidades/artistas/hooks'
import { Consulta } from '../componentes/Consulta'
import { EstadoVacio } from '../componentes/ui'
import { RUTAS } from '../lib/rutas'
export default function ListaArtistas() {
  const consulta = useArtistasActivos()
  return (
    <section>
      <h1>Artistas</h1>
      <Consulta consulta={consulta}>
        {(datos) =>
          datos.length ? (
            <div className="rejilla">
              {datos.map((a) => (
                <Link
                  key={a.id}
                  to={RUTAS.artista(a.id)}
                  className="tarjeta hover:border-marca-500 text-center transition-colors"
                >
                  <span className="font-titulo mx-auto mb-4 flex size-20 items-center justify-center rounded-full bg-indigo-100 text-2xl text-indigo-900">
                    {a.stageName.slice(0, 2).toUpperCase()}
                  </span>
                  <h2>{a.stageName}</h2>
                  <p className="text-sm text-slate-600 dark:text-slate-400">
                    {a.genre ?? 'Género no informado'}
                  </p>
                </Link>
              ))}
            </div>
          ) : (
            <EstadoVacio mensaje="Aún no hay artistas activos" />
          )
        }
      </Consulta>
    </section>
  )
}
