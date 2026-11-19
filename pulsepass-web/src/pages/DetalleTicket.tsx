import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useTicket } from '../funcionalidades/tickets/hooks'
import { CancelarTicket } from '../funcionalidades/tickets/componentes/CancelarTicket'
import { Consulta } from '../componentes/Consulta'
import { Badge, Boton, PrecioCOP, Aviso } from '../componentes/ui'
import { CodigoSchema } from '../lib/parametros'
import { RUTAS } from '../lib/rutas'
import { fecha } from '../lib/formato'
import { ApiError } from '../lib/http'
export default function DetalleTicket() {
  const code = CodigoSchema.safeParse(useParams().ticketCode)
  const consulta = useTicket(code.success ? code.data : '', code.success)
  const [copiado, setCopiado] = useState<'si' | 'error' | null>(null)
  if (
    !code.success ||
    (consulta.error instanceof ApiError &&
      [403, 404].includes(consulta.error.status))
  )
    return (
      <section className="estado">
        <h1>Ticket no disponible</h1>
        <Link className="enlace" to={RUTAS.tickets}>
          Volver a mis tickets
        </Link>
      </section>
    )
  return (
    <section className="estrecho">
      <Link className="enlace" to={RUTAS.tickets}>
        Volver a mis tickets
      </Link>
      <Consulta consulta={consulta}>
        {(t) => (
          <>
            <article className="tarjeta mt-6">
              <div className="mb-6 flex flex-wrap justify-between gap-4">
                <h1 className="mb-0 text-2xl">{t.eventName}</h1>
                <Badge estado={t.status} />
              </div>
              <dl className="datos">
                <div>
                  <dt>Titular</dt>
                  <dd className="wrap-anywhere">
                    {t.userFullName ?? 'No informado'}
                  </dd>
                </div>
                <div>
                  <dt>Código</dt>
                  <dd>
                    <code>{t.ticketCode}</code>{' '}
                    <Boton
                      variante="fantasma"
                      onClick={() => {
                        if (!navigator.clipboard) {
                          setCopiado('error')
                          return
                        }
                        void navigator.clipboard
                          .writeText(t.ticketCode)
                          .then(() => setCopiado('si'))
                          .catch(() => setCopiado('error'))
                      }}
                    >
                      Copiar
                    </Boton>
                  </dd>
                </div>
                <div>
                  <dt>Tipo</dt>
                  <dd>{t.type}</dd>
                </div>
                <div>
                  <dt>Precio</dt>
                  <dd>
                    <PrecioCOP valor={t.price} />
                  </dd>
                </div>
                <div>
                  <dt>Comprado el</dt>
                  <dd>{fecha(t.purchaseDate)}</dd>
                </div>
              </dl>

              <p className="mt-6">Presenta este código en el ingreso.</p>
            </article>
            {copiado && (
              <Aviso key={copiado} tipo={copiado === 'si' ? 'exito' : 'error'}>
                {copiado === 'si'
                  ? 'Código copiado'
                  : 'No pudimos copiarlo. Selecciona el código y cópialo manualmente.'}
              </Aviso>
            )}
            <div className="acciones justify-end">
              <CancelarTicket ticket={t} />
            </div>
          </>
        )}
      </Consulta>
    </section>
  )
}
