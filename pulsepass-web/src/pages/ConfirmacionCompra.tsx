import { Link, useParams } from 'react-router'
import { useTicket } from '../funcionalidades/tickets/hooks'
import { Consulta } from '../componentes/Consulta'
import { Badge, PrecioCOP } from '../componentes/ui'
import { CodigoSchema } from '../lib/parametros'
import { RUTAS } from '../lib/rutas'
import { ApiError } from '../lib/http'
export default function ConfirmacionCompra() {
  const code = CodigoSchema.safeParse(useParams().ticketCode)
  const consulta = useTicket(code.success ? code.data : '', code.success)
  if (
    !code.success ||
    (consulta.error instanceof ApiError &&
      [403, 404].includes(consulta.error.status))
  )
    return (
      <section className="estado">
        <h1>Ticket no disponible</h1>
      </section>
    )
  return (
    <section className="estrecho">
      <Consulta consulta={consulta}>
        {(t) => (
          <div className="tarjeta py-12 text-center">
            <svg
              className="mx-auto mb-6 text-emerald-700 dark:text-emerald-300"
              aria-hidden="true"
              width="56"
              height="56"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
            >
              <circle cx="12" cy="12" r="10" />
              <path d="m7 12 3 3 7-7" />
            </svg>
            <h1 className="text-2xl">
              {t.status === 'PAID'
                ? '¡Compra realizada con éxito!'
                : 'Estado actual de tu ticket'}
            </h1>
            <h2>{t.eventName}</h2>
            <p>
              Ticket <code>{t.ticketCode}</code> · {t.type} ·{' '}
              <PrecioCOP valor={t.price} />
            </p>
            <Badge estado={t.status} />
            <div className="acciones justify-center">
              <Link className="btn bg-marca-600 text-white" to={RUTAS.tickets}>
                Ver mis tickets
              </Link>
              <Link className="enlace" to={RUTAS.eventos}>
                Seguir explorando
              </Link>
            </div>
          </div>
        )}
      </Consulta>
    </section>
  )
}
