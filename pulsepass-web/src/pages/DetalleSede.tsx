import { Link, useParams } from 'react-router'
import { useSede } from '../funcionalidades/sedes/hooks'
import { Consulta } from '../componentes/Consulta'
import { CodigoSchema } from '../lib/parametros'
import { RUTAS } from '../lib/rutas'
import { ApiError } from '../lib/http'
import NoEncontrada from './NoEncontrada'
export default function DetalleSede() {
  const code = CodigoSchema.safeParse(useParams().code)
  const consulta = useSede(code.success ? code.data : '', code.success)
  if (
    !code.success ||
    (consulta.error instanceof ApiError && consulta.error.status === 404)
  )
    return <NoEncontrada />
  return (
    <section className="estrecho">
      <Link className="enlace" to={RUTAS.sedes}>
        Volver a sedes
      </Link>
      <Consulta consulta={consulta}>
        {(s) => (
          <div className="tarjeta mt-6">
            <h1>{s.name}</h1>
            <p className="codigo">{s.code}</p>
            <dl className="datos">
              <div>
                <dt>Ciudad</dt>
                <dd>{s.city}</dd>
              </div>
              <div>
                <dt>Capacidad</dt>
                <dd>{s.capacity} personas</dd>
              </div>
              <div>
                <dt>Dirección</dt>
                <dd>{s.address ?? 'No informada'}</dd>
              </div>
              <div>
                <dt>Estado</dt>
                <dd>{s.active ? 'Activa' : 'Inactiva'}</dd>
              </div>
            </dl>
          </div>
        )}
      </Consulta>
    </section>
  )
}
