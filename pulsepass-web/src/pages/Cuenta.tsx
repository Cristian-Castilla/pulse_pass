import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../funcionalidades/auth/hooks'
import { useUsuario } from '../funcionalidades/usuarios/hooks'
import { Boton } from '../componentes/ui'
import { Consulta } from '../componentes/Consulta'
import { RUTAS } from '../lib/rutas'
export default function Cuenta() {
  const { sesion, cerrarSesion, tieneRol } = useAuth()
  const [por, setPor] = useState<'email' | 'username'>('email')
  const usuario = sesion.estado === 'autenticado' ? sesion.usuario : null
  const consulta = useUsuario(
    usuario ? (por === 'email' ? usuario.email : usuario.username) : '',
    por,
  )
  return (
    <section className="estrecho">
      <h1>Mi cuenta</h1>
      <Consulta consulta={consulta}>
        {(u) => (
          <article className="tarjeta break-words">
            <h2>
              {u.firstName && u.lastName
                ? `${u.firstName} ${u.lastName}`
                : 'Nombre no informado'}
            </h2>
            <p>
              @{u.username} · {u.email}
            </p>
            <dl className="datos">
              <div>
                <dt>Fecha de nacimiento</dt>
                <dd>
                  {u.birthDate ? (
                    <time dateTime={u.birthDate}>
                      {u.birthDate.split('-').reverse().join('/')}
                    </time>
                  ) : (
                    'No informada'
                  )}
                </dd>
              </div>
              <div>
                <dt>Estado</dt>
                <dd>{u.active ? 'Activa' : 'Inactiva'}</dd>
              </div>
              <div>
                <dt>Roles</dt>
                <dd className="flex flex-wrap gap-2">
                  {usuario?.roles.map((rol) => (
                    <span
                      key={rol}
                      className="badge bg-slate-200 text-slate-900"
                    >
                      {rol}
                    </span>
                  ))}
                </dd>
              </div>
            </dl>
          </article>
        )}
      </Consulta>
      <div className="acciones">
        <Boton
          variante="secundario"
          onClick={() => setPor(por === 'email' ? 'username' : 'email')}
        >
          Consultar mi identidad por {por === 'email' ? 'usuario' : 'correo'}
        </Boton>
        {tieneRol('USER') && (
          <Link className="enlace" to={RUTAS.tickets}>
            Ver mis tickets
          </Link>
        )}
        <Boton variante="fantasma" onClick={cerrarSesion}>
          Cerrar sesión
        </Boton>
      </div>
    </section>
  )
}
