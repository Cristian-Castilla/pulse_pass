import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../funcionalidades/auth/hooks'
import { useMisTickets } from '../funcionalidades/tickets/hooks'
import { CancelarTicket } from '../funcionalidades/tickets/componentes/CancelarTicket'
import { Consulta } from '../componentes/Consulta'
import { Badge, Boton, EstadoVacio, PrecioCOP } from '../componentes/ui'
import { RUTAS } from '../lib/rutas'
import { fecha } from '../lib/formato'
export default function MisTickets() {
  const { sesion } = useAuth()
  const consulta = useMisTickets(
    sesion.estado === 'autenticado' ? sesion.usuario.email : '',
  )
  const [estado, setEstado] = useState('')
  const filtros = [
    ['Todos', ''],
    ['Pagados', 'PAID'],
    ['Usados', 'USED'],
    ['Cancelados', 'CANCELLED'],
  ]
  return (
    <section>
      <h1>Mis tickets</h1>
      <div
        className="mb-6 flex flex-wrap gap-2"
        role="group"
        aria-label="Filtrar tickets por estado"
      >
        {filtros.map(([texto, valor]) => (
          <Boton
            key={texto}
            variante={estado === valor ? 'primario' : 'secundario'}
            aria-pressed={estado === valor}
            onClick={() => setEstado(valor ?? '')}
          >
            {texto}
          </Boton>
        ))}
      </div>
      <Consulta consulta={consulta}>
        {(datos) => {
          const visibles = datos.filter((t) => !estado || t.status === estado)
          return !datos.length ? (
            <EstadoVacio mensaje="Aún no tienes tickets">
              <Link className="enlace" to={RUTAS.eventos}>
                Explorar cartelera
              </Link>
            </EstadoVacio>
          ) : !visibles.length ? (
            <EstadoVacio mensaje="Sin tickets con este estado" />
          ) : (
            <div className="space-y-4">
              {visibles.map((t) => (
                <article className="tarjeta" key={t.ticketCode}>
                  <div className="flex flex-wrap justify-between gap-4">
                    <h2>{t.eventName}</h2>
                    <Badge estado={t.status} />
                  </div>
                  <p>
                    <code>{t.ticketCode}</code> · {t.type} ·{' '}
                    <PrecioCOP valor={t.price} />
                  </p>
                  <p className="text-sm text-slate-600 dark:text-slate-400">
                    {fecha(t.purchaseDate)}
                  </p>
                  <div className="acciones justify-end">
                    <Link className="enlace" to={RUTAS.ticket(t.ticketCode)}>
                      Ver detalle
                    </Link>
                    <CancelarTicket ticket={t} />
                  </div>
                </article>
              ))}
            </div>
          )
        }}
      </Consulta>
    </section>
  )
}
