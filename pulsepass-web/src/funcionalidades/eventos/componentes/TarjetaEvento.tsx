import { Link } from 'react-router'
import { useEvento } from '../hooks'
import { useSede } from '../../sedes/hooks'
import type { EventoResumen } from '../tipos'
import { PanelCategoria } from './PanelCategoria'
import { Badge, Cargando, EstadoError } from '../../../componentes/ui'
import { fecha } from '../../../lib/formato'
import { RUTAS } from '../../../lib/rutas'
export function TarjetaEvento({ evento }: { evento: EventoResumen }) {
  const detalle = useEvento(evento.eventCode)
  const sede = useSede(detalle.data?.venueCode ?? '', !!detalle.data)
  if (detalle.isError)
    return (
      <EstadoError
        error={detalle.error}
        onReintentar={() => void detalle.refetch()}
      />
    )
  if (sede.isError)
    return (
      <EstadoError
        error={sede.error}
        onReintentar={() => void sede.refetch()}
      />
    )
  if (!detalle.data || !sede.data) return <Cargando />
  return (
    <Link
      to={RUTAS.evento(evento.eventCode)}
      className="tarjeta hover:border-marca-500 block transition-colors"
    >
      <PanelCategoria categoria={detalle.data.category} />
      <p className="text-xs font-semibold text-slate-600 dark:text-slate-400">
        {detalle.data.category}
      </p>
      <h3 className="mb-4">{detalle.data.name}</h3>
      <p className="text-sm">{fecha(detalle.data.eventDate)}</p>
      <p className="text-sm">{sede.data.city}</p>
      <Badge estado={detalle.data.status} />
    </Link>
  )
}
