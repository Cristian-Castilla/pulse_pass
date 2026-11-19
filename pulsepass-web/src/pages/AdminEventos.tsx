import { Link, useNavigate } from 'react-router'
import { useState } from 'react'
import { leerCreados } from '../funcionalidades/admin/creados'
import { FormularioCodigo } from '../componentes/FormularioCodigo'
import { EstadoVacio, Badge } from '../componentes/ui'
import { Consulta } from '../componentes/Consulta'
import { useEvento } from '../funcionalidades/eventos/hooks'
import { RUTAS } from '../lib/rutas'
function EventoCreado({ codigo }: { codigo: string }) {
  const consulta = useEvento(codigo)
  return (
    <li className="tarjeta">
      <Consulta consulta={consulta}>
        {(evento) => (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <code>{evento.eventCode}</code>
              <h3>{evento.name}</h3>
            </div>
            <Badge estado={evento.status} />
            <Link
              className="enlace"
              aria-label={`Gestionar ${evento.eventCode}`}
              to={RUTAS.gestion(evento.eventCode)}
            >
              Gestionar
            </Link>
          </div>
        )}
      </Consulta>
    </li>
  )
}
export default function AdminEventos() {
  const navegar = useNavigate()
  const [creados] = useState(leerCreados)
  return (
    <section className="estrecho">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="mb-0">Administración de eventos</h1>
        <Link className="btn bg-marca-600 text-white" to={RUTAS.crear}>
          Nuevo evento
        </Link>
      </div>
      <FormularioCodigo
        etiqueta="Buscar evento por código"
        accion="Abrir"
        onBuscar={(c) => void navegar(RUTAS.gestion(c))}
      />
      <h2>Creados en esta sesión</h2>
      {creados.length ? (
        <ul className="space-y-3">
          {creados.map((c) => (
            <EventoCreado key={c} codigo={c} />
          ))}
        </ul>
      ) : (
        <EstadoVacio mensaje="No has creado eventos en esta sesión" />
      )}
      <p className="mt-6 text-sm text-slate-600 dark:text-slate-400">
        La API no ofrece un listado completo de eventos. Los códigos recordados
        se borran al cerrar sesión o la pestaña.
      </p>
    </section>
  )
}
