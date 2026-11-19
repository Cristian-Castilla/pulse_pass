import { Link, isRouteErrorResponse, useRouteError } from 'react-router'
import { RUTAS } from '../lib/rutas'
import { Boton } from '../componentes/ui'
export default function ErrorPagina() {
  const error = useRouteError()
  const status = isRouteErrorResponse(error) ? error.status : 500
  return (
    <main className="contenedor estado">
      <h1>
        {status === 404
          ? 'No encontramos esta página'
          : status === 403
            ? 'No tienes permisos'
            : '¡Ups! Algo salió mal.'}
      </h1>
      <p>
        No pudimos cargar esta página. No se muestran detalles internos del
        servidor.
      </p>
      <div className="acciones">
        <Boton onClick={() => window.location.reload()}>Reintentar</Boton>
        <Link className="enlace" to={RUTAS.inicio}>
          Volver al inicio
        </Link>
      </div>
    </main>
  )
}
