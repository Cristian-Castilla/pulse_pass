import { Link } from 'react-router'
import { useEventosPublicados } from '../funcionalidades/eventos/hooks'
import { useArtistasActivos } from '../funcionalidades/artistas/hooks'
import { useAuth } from '../funcionalidades/auth/hooks'
import { Consulta } from '../componentes/Consulta'
import { EstadoVacio } from '../componentes/ui'
import { TarjetaEvento } from '../funcionalidades/eventos/componentes/TarjetaEvento'
import { RUTAS } from '../lib/rutas'
export default function Inicio() {
  const eventos = useEventosPublicados()
  const artistas = useArtistasActivos()
  const { sesion } = useAuth()
  return (
    <>
      <section className="from-marca-600 to-acento-500 mb-12 rounded-2xl bg-linear-to-r px-6 py-12 text-white md:px-12 md:py-16">
        <h1 className="max-w-xl text-4xl md:text-5xl">
          Descubre los mejores eventos
        </h1>
        <p className="max-w-xl text-indigo-50">
          Música, cultura, deporte y tecnología en un solo lugar.
        </p>
        <div className="acciones">
          <Link
            className="btn text-marca-700 bg-white hover:bg-indigo-50"
            to={RUTAS.eventos}
          >
            Ver cartelera
          </Link>
          {sesion.estado === 'anonimo' && (
            <Link
              className="btn hover:bg-marca-700 border border-white text-white"
              to={RUTAS.registro}
            >
              Crear cuenta
            </Link>
          )}
        </div>
      </section>
      <section className="mb-12">
        <div className="mb-5 flex flex-wrap justify-between gap-3">
          <h2>Próximos eventos</h2>
          <Link className="enlace" to={RUTAS.eventos}>
            Ver todos
          </Link>
        </div>
        <Consulta consulta={eventos}>
          {(datos) =>
            datos.length ? (
              <div className="rejilla">
                {datos.slice(0, 3).map((e) => (
                  <TarjetaEvento key={e.eventCode} evento={e} />
                ))}
              </div>
            ) : (
              <EstadoVacio mensaje="Aún no hay eventos publicados" />
            )
          }
        </Consulta>
      </section>
      <section>
        <div className="mb-5 flex flex-wrap justify-between gap-3">
          <h2>Artistas destacados</h2>
          <Link className="enlace" to={RUTAS.artistas}>
            Ver todos los artistas
          </Link>
        </div>
        <Consulta consulta={artistas}>
          {(datos) =>
            datos.length ? (
              <div className="flex flex-wrap gap-8">
                {datos.slice(0, 6).map((a) => (
                  <Link
                    className="text-center"
                    key={a.id}
                    to={RUTAS.artista(a.id)}
                  >
                    <span className="font-titulo mx-auto mb-3 flex size-16 items-center justify-center rounded-full bg-indigo-100 text-xl text-indigo-900">
                      {a.stageName.slice(0, 2).toUpperCase()}
                    </span>
                    <span className="text-sm font-semibold">{a.stageName}</span>
                  </Link>
                ))}
              </div>
            ) : (
              <EstadoVacio mensaje="Aún no hay artistas activos" />
            )
          }
        </Consulta>
      </section>
    </>
  )
}
