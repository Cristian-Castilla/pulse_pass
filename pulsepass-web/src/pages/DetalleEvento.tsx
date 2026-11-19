import { Link, useParams } from 'react-router'
import { useEvento } from '../funcionalidades/eventos/hooks'
import { useSede } from '../funcionalidades/sedes/hooks'
import { PanelCompra } from '../funcionalidades/tickets/componentes/PanelCompra'
import { Consulta } from '../componentes/Consulta'
import { Badge } from '../componentes/ui'
import { PanelCategoria } from '../funcionalidades/eventos/componentes/PanelCategoria'
import { CodigoSchema } from '../lib/parametros'
import { RUTAS } from '../lib/rutas'
import { ApiError } from '../lib/http'
import { fecha } from '../lib/formato'

function SedeEvento({ code }: { code: string }) {
  const consulta = useSede(code)
  return (
    <Consulta consulta={consulta}>
      {(s) => (
        <div>
          <Link className="enlace" to={RUTAS.sede(s.code)}>
            {s.name}
          </Link>
          <p>{s.city}</p>
          <p>Capacidad: {s.capacity} personas</p>
        </div>
      )}
    </Consulta>
  )
}
export default function DetalleEvento() {
  const code = CodigoSchema.safeParse(useParams().eventCode)
  const consulta = useEvento(code.success ? code.data : '', code.success)
  if (
    !code.success ||
    (consulta.error instanceof ApiError &&
      [403, 404].includes(consulta.error.status))
  )
    return (
      <section className="estado">
        <h1>Evento no disponible</h1>
        <Link className="enlace" to={RUTAS.eventos}>
          Volver a la cartelera
        </Link>
      </section>
    )
  return (
    <section className="pb-20 md:pb-0">
      <Link className="enlace" to={RUTAS.eventos}>
        Volver a la cartelera
      </Link>
      <Consulta consulta={consulta}>
        {(e) => (
          <div className="mt-6 grid items-start gap-8 md:grid-cols-[minmax(0,2fr)_minmax(260px,1fr)]">
            <article className="tarjeta">
              <PanelCategoria categoria={e.category} />
              <div className="mb-3 flex flex-wrap gap-3 text-sm">
                <span>{e.category}</span>
                <Badge estado={e.status} />
              </div>
              <h1>{e.name}</h1>
              <p>{fecha(e.eventDate)}</p>
              <p>Edad mínima: {e.minimumAge} años</p>
              {e.description && (
                <p className="my-6 whitespace-pre-wrap">{e.description}</p>
              )}
              <h2>Artistas</h2>
              <div className="mb-8 flex flex-wrap gap-4">
                {e.artists.length ? (
                  e.artists.map((a) => (
                    <Link
                      key={a.id}
                      className="enlace"
                      to={RUTAS.artista(a.id)}
                    >
                      {a.stageName}
                    </Link>
                  ))
                ) : (
                  <p>Sin artistas asociados</p>
                )}
              </div>
              <h2>Sede</h2>
              <SedeEvento code={e.venueCode} />
            </article>
            <PanelCompra evento={e} />
          </div>
        )}
      </Consulta>
    </section>
  )
}
