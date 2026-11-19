import { Link } from 'react-router'
import { useSedesActivas } from '../funcionalidades/sedes/hooks'
import { Consulta } from '../componentes/Consulta'
import { EstadoVacio, Icono } from '../componentes/ui'
import { RUTAS } from '../lib/rutas'
export default function ListaSedes() {
  const consulta = useSedesActivas()
  return (
    <section>
      <h1>Sedes</h1>
      <Consulta consulta={consulta}>
        {(datos) =>
          datos.length ? (
            <div className="rejilla">
              {datos.map((s) => (
                <Link
                  key={s.code}
                  to={RUTAS.sede(s.code)}
                  className="tarjeta hover:border-marca-500"
                >
                  <div className="text-marca-700 mb-4 dark:text-indigo-300">
                    <Icono nombre="ubicacion" />
                  </div>
                  <h2>{s.name}</h2>
                  <p>
                    {s.city} · Capacidad: {s.capacity}
                  </p>
                  <span className="badge bg-emerald-100 text-emerald-900">
                    Activa
                  </span>
                </Link>
              ))}
            </div>
          ) : (
            <EstadoVacio mensaje="Aún no hay sedes activas" />
          )
        }
      </Consulta>
    </section>
  )
}
