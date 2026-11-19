import { Link, useParams } from 'react-router'
import { useArtista } from '../funcionalidades/artistas/hooks'
import { useEventosPorArtista } from '../funcionalidades/eventos/hooks'
import { Consulta } from '../componentes/Consulta'
import { EstadoVacio } from '../componentes/ui'
import { TarjetaEvento } from '../funcionalidades/eventos/componentes/TarjetaEvento'
import { IdSchema } from '../lib/parametros'
import { RUTAS } from '../lib/rutas'
import NoEncontrada from './NoEncontrada'
import { ApiError } from '../lib/http'
function EventosArtista({ nombre }: { nombre: string }) {
  const consulta = useEventosPorArtista(nombre)
  return (
    <Consulta consulta={consulta}>
      {(datos) =>
        datos.length ? (
          <div className="rejilla">
            {datos.map((e) => (
              <TarjetaEvento key={e.eventCode} evento={e} />
            ))}
          </div>
        ) : (
          <EstadoVacio mensaje="Sin eventos próximos" />
        )
      }
    </Consulta>
  )
}
export default function DetalleArtista() {
  const id = IdSchema.safeParse(useParams().id)
  const consulta = useArtista(id.success ? id.data : 0, id.success)
  if (
    !id.success ||
    (consulta.error instanceof ApiError && consulta.error.status === 404)
  )
    return <NoEncontrada />
  return (
    <section>
      <Link className="enlace" to={RUTAS.artistas}>
        Volver a artistas
      </Link>
      <Consulta consulta={consulta}>
        {(artista) => (
          <>
            <div className="my-8 flex flex-wrap items-center gap-6">
              <span className="font-titulo flex size-24 items-center justify-center rounded-full bg-indigo-100 text-3xl text-indigo-900">
                {artista.stageName.slice(0, 2).toUpperCase()}
              </span>
              <div>
                <h1 className="mb-2">{artista.stageName}</h1>
                <p>
                  {artista.country} · {artista.genre}
                </p>
              </div>
            </div>
            <h2>Próximos eventos de {artista.stageName}</h2>
            <EventosArtista nombre={artista.stageName} />
          </>
        )}
      </Consulta>
    </section>
  )
}
