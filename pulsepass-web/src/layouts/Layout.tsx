import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { z } from 'zod'
import { useAuth } from '../funcionalidades/auth/hooks'
import { RUTAS } from '../lib/rutas'
import { Boton, Icono } from '../componentes/ui'
function temaInicial() {
  try {
    const raw = sessionStorage.getItem('pulsepass.tema')
    const t = z.enum(['claro', 'oscuro']).safeParse(raw)
    if (t.success) return t.data === 'oscuro'
  } catch {
    /* Usar preferencia del sistema si storage no está disponible. */
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}
export default function Layout() {
  const { sesion, tieneRol, cerrarSesion } = useAuth()
  const [menu, setMenu] = useState(false)
  const [oscuro, setOscuro] = useState(temaInicial)
  const location = useLocation()
  useEffect(() => {
    document.documentElement.classList.toggle('dark', oscuro)
    try {
      sessionStorage.setItem('pulsepass.tema', oscuro ? 'oscuro' : 'claro')
    } catch {
      /* El tema funciona sin persistencia. */
    }
  }, [oscuro])
  useEffect(() => {
    document.title = 'PulsePass · Eventos y entradas'
    document.getElementById('contenido')?.focus({ preventScroll: true })
  }, [location.pathname])
  const enlaces = [
    ['Eventos', RUTAS.eventos],
    ['Artistas', RUTAS.artistas],
    ['Sedes', RUTAS.sedes],
    ...(tieneRol('USER') ? [['Mis tickets', RUTAS.tickets]] : []),
    ...(tieneRol('STAFF', 'ADMIN')
      ? [
          ['Validar ingreso', RUTAS.validacion],
          ['Tickets pagados', RUTAS.seleccionarPagados],
        ]
      : []),
    ...(tieneRol('ADMIN') ? [['Administrar eventos', RUTAS.admin]] : []),
  ]
  return (
    <>
      <a
        href="#contenido"
        className="bg-marca-700 sr-only z-50 p-3 text-white focus:not-sr-only focus:fixed"
      >
        Saltar al contenido
      </a>
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="contenedor flex min-h-20 flex-wrap items-center gap-3 py-3">
          <NavLink
            end
            to={RUTAS.inicio}
            aria-label="PulsePass · Inicio"
            className="font-titulo text-marca-700 mr-auto flex items-center gap-2 text-xl font-bold dark:text-indigo-300"
          >
            <Icono />
            PulsePass
          </NavLink>
          <Boton
            variante="fantasma"
            aria-label={oscuro ? 'Activar modo claro' : 'Activar modo oscuro'}
            aria-pressed={oscuro}
            onClick={() => setOscuro(!oscuro)}
          >
            <Icono nombre="sol" />
          </Boton>
          <Boton
            className="md:hidden"
            variante="fantasma"
            aria-label={menu ? 'Cerrar menú' : 'Abrir menú'}
            aria-expanded={menu}
            aria-controls="navegacion"
            onClick={() => setMenu(!menu)}
          >
            <Icono nombre={menu ? 'cerrar' : 'menu'} />
          </Boton>
          <nav
            id="navegacion"
            aria-label="Principal"
            className={`${menu ? 'flex' : 'hidden'} w-full flex-col gap-1 md:flex md:w-auto md:flex-row md:flex-wrap md:items-center`}
            onKeyDown={(e) => {
              if (e.key === 'Escape') setMenu(false)
            }}
          >
            {enlaces.map(([texto, ruta]) => (
              <NavLink
                key={ruta}
                to={ruta ?? RUTAS.inicio}
                className="nav-enlace"
                onClick={() => setMenu(false)}
              >
                {texto}
              </NavLink>
            ))}
            {sesion.estado === 'anonimo' ? (
              <>
                <NavLink
                  to={RUTAS.login}
                  className="nav-enlace"
                  onClick={() => setMenu(false)}
                >
                  Ingresar
                </NavLink>
                <Link
                  to={RUTAS.registro}
                  className="btn bg-marca-600 hover:bg-marca-700 text-white"
                  onClick={() => setMenu(false)}
                >
                  Registrarse
                </Link>
              </>
            ) : sesion.estado === 'autenticado' ? (
              <details className="relative">
                <summary className="nav-enlace">
                  {sesion.usuario.username}
                </summary>
                <div className="absolute right-0 z-40 min-w-44 rounded-xl border border-slate-200 bg-white p-2 shadow-lg dark:border-slate-700 dark:bg-slate-900">
                  <Link
                    className="nav-enlace w-full"
                    to={RUTAS.cuenta}
                    onClick={() => setMenu(false)}
                  >
                    Mi cuenta
                  </Link>
                  <Boton
                    className="w-full"
                    variante="fantasma"
                    onClick={() => {
                      setMenu(false)
                      cerrarSesion()
                    }}
                  >
                    Salir
                  </Boton>
                </div>
              </details>
            ) : null}
          </nav>
        </div>
      </header>
      <main
        id="contenido"
        tabIndex={-1}
        className="contenedor min-h-[calc(100vh-12rem)] py-8 focus:outline-none md:py-12"
      >
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 bg-white py-8 text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400">
        <div className="contenedor flex flex-wrap justify-between gap-4">
          <p className="m-0">© 2026 PulsePass · Caso de estudio académico</p>
          <div className="flex gap-6">
            <Link to={RUTAS.sedes}>Sedes</Link>
            <Link to={RUTAS.artistas}>Artistas</Link>
          </div>
        </div>
      </footer>
    </>
  )
}
