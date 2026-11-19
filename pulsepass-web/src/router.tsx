import { lazy, Suspense } from 'react'
import type { RouteObject } from 'react-router'
import { AuthProvider } from './funcionalidades/auth/AuthContext'
import Layout from './layouts/Layout'
import RutaProtegida from './layouts/RutaProtegida'
import RutaPorRol from './layouts/RutaPorRol'
import SoloAnonimo from './layouts/SoloAnonimo'
import Inicio from './pages/Inicio'
import { Cargando } from './componentes/ui'
import { RUTAS, PATRONES } from './lib/rutas'
const ErrorPagina = lazy(() => import('./pages/ErrorPagina'))
export function crearRutas(): RouteObject[] {
  return [
    {
      element: (
        <AuthProvider>
          <Layout />
        </AuthProvider>
      ),
      errorElement: (
        <Suspense fallback={<Cargando />}>
          <ErrorPagina />
        </Suspense>
      ),
      HydrateFallback: Cargando,
      children: [
        { index: true, Component: Inicio },
        {
          path: RUTAS.eventos,
          lazy: async () => ({
            Component: (await import('./pages/CatalogoEventos')).default,
          }),
        },
        {
          path: PATRONES.evento,
          lazy: async () => ({
            Component: (await import('./pages/DetalleEvento')).default,
          }),
        },
        {
          path: RUTAS.artistas,
          lazy: async () => ({
            Component: (await import('./pages/ListaArtistas')).default,
          }),
        },
        {
          path: PATRONES.artista,
          lazy: async () => ({
            Component: (await import('./pages/DetalleArtista')).default,
          }),
        },
        {
          path: RUTAS.sedes,
          lazy: async () => ({
            Component: (await import('./pages/ListaSedes')).default,
          }),
        },
        {
          path: PATRONES.sede,
          lazy: async () => ({
            Component: (await import('./pages/DetalleSede')).default,
          }),
        },
        {
          element: <SoloAnonimo />,
          children: [
            {
              path: RUTAS.login,
              lazy: async () => ({
                Component: (await import('./pages/Login')).default,
              }),
            },
            {
              path: RUTAS.registro,
              lazy: async () => ({
                Component: (await import('./pages/Registro')).default,
              }),
            },
          ],
        },
        {
          element: <RutaProtegida />,
          children: [
            {
              path: RUTAS.cuenta,
              lazy: async () => ({
                Component: (await import('./pages/Cuenta')).default,
              }),
            },
            {
              element: <RutaPorRol roles={['USER']} />,
              children: [
                {
                  path: RUTAS.tickets,
                  lazy: async () => ({
                    Component: (await import('./pages/MisTickets')).default,
                  }),
                },
                {
                  path: PATRONES.ticket,
                  lazy: async () => ({
                    Component: (await import('./pages/DetalleTicket')).default,
                  }),
                },
                {
                  path: PATRONES.confirmacion,
                  lazy: async () => ({
                    Component: (await import('./pages/ConfirmacionCompra'))
                      .default,
                  }),
                },
              ],
            },
            {
              element: <RutaPorRol roles={['STAFF', 'ADMIN']} />,
              children: [
                {
                  path: RUTAS.validacion,
                  lazy: async () => ({
                    Component: (await import('./pages/ValidacionIngreso'))
                      .default,
                  }),
                },
                {
                  path: PATRONES.pagados,
                  lazy: async () => ({
                    Component: (await import('./pages/TicketsPagadosEvento'))
                      .default,
                  }),
                },
              ],
            },
            {
              element: <RutaPorRol roles={['ADMIN']} />,
              children: [
                {
                  path: RUTAS.admin,
                  lazy: async () => ({
                    Component: (await import('./pages/AdminEventos')).default,
                  }),
                },
                {
                  path: RUTAS.crear,
                  lazy: async () => ({
                    Component: (await import('./pages/AdminCrearEvento'))
                      .default,
                  }),
                },
                {
                  path: PATRONES.gestion,
                  lazy: async () => ({
                    Component: (await import('./pages/AdminGestionEvento'))
                      .default,
                  }),
                },
              ],
            },
          ],
        },
        {
          path: RUTAS.sinPermisos,
          lazy: async () => ({
            Component: (await import('./pages/SinPermisos')).default,
          }),
        },
        {
          path: PATRONES.noEncontrada,
          lazy: async () => ({
            Component: (await import('./pages/NoEncontrada')).default,
          }),
        },
      ],
    },
  ]
}
