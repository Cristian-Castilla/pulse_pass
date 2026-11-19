import { Link } from 'react-router'
import { RUTAS } from '../lib/rutas'
export default function NoEncontrada() {
  return (
    <section className="estado">
      <h1 className="text-marca-700 text-7xl dark:text-indigo-300">404</h1>
      <h2>No encontramos esta página</h2>
      <Link className="btn bg-marca-600 text-white" to={RUTAS.inicio}>
        Volver al inicio
      </Link>
    </section>
  )
}
