import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../auth/hooks'
import { ticketsApi } from '../api'
import { CompraSchema, TipoTicketSchema } from '../tipos'
import type { Compra } from '../tipos'
import type { Evento } from '../../eventos/tipos'
import { Boton, Selector, Modal, Aviso } from '../../../componentes/ui'
import { ApiError, tokenActual } from '../../../lib/http'
import { RUTAS } from '../../../lib/rutas'
export function PanelCompra({ evento }: { evento: Evento }) {
  const { sesion, tieneRol } = useAuth()
  const [abierto, setAbierto] = useState(false)
  const navegar = useNavigate()
  const location = useLocation()
  const client = useQueryClient()
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<Compra>({
    resolver: zodResolver(CompraSchema),
    defaultValues: { type: 'GENERAL' },
  })
  const tipo = useWatch({ control, name: 'type' })
  const compra = useMutation({
    mutationFn: async () => {
      if (sesion.estado !== 'autenticado') throw new Error('Sesión requerida')
      const actual = sesion.token
      const ticket = await ticketsApi.comprar(
        sesion.usuario.email,
        evento.eventCode,
        tipo,
      )
      if (tokenActual() !== actual) return
      await Promise.all([
        client.invalidateQueries({
          queryKey: ['tickets', 'usuario', sesion.usuario.email],
        }),
        client.invalidateQueries({ queryKey: ['evento', evento.eventCode] }),
        client.invalidateQueries({ queryKey: ['eventos', 'publicados'] }),
        client.invalidateQueries({
          queryKey: ['tickets', 'pagados', evento.eventCode],
        }),
      ])
      if (tokenActual() === actual)
        await navegar(RUTAS.confirmacion(ticket.ticketCode))
    },
  })
  if (tieneRol('STAFF', 'ADMIN') || sesion.estado === 'cargando') return null
  const disponible = evento.status === 'PUBLISHED'
  return (
    <section className="tarjeta">
      <h2>Entradas</h2>
      <form
        noValidate
        onSubmit={handleSubmit(() => {
          if (sesion.estado === 'anonimo') {
            void navegar(RUTAS.login, {
              state: { desde: location.pathname + location.search },
            })
          } else setAbierto(true)
        })}
      >
        <Selector
          etiqueta="Tipo de entrada"
          error={errors.type?.message}
          {...register('type')}
        >
          {TipoTicketSchema.options.map((t) => (
            <option key={t}>{t}</option>
          ))}
        </Selector>
        <Boton type="submit" disabled={!disponible} className="w-full">
          {sesion.estado === 'anonimo'
            ? 'Inicia sesión para comprar'
            : 'Comprar entrada'}
        </Boton>
        {!disponible && (
          <p className="mt-3 text-sm">
            Compra no disponible: el evento no está publicado.
          </p>
        )}
        <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
          El precio lo define el sistema al confirmar.
        </p>
      </form>
      {disponible && (
        <div className="barra-compra fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white p-4 shadow-lg md:hidden dark:border-slate-700 dark:bg-slate-900">
          <Boton
            className="w-full"
            onClick={() => {
              if (sesion.estado === 'anonimo')
                void navegar(RUTAS.login, {
                  state: { desde: location.pathname },
                })
              else setAbierto(true)
            }}
          >
            Comprar
          </Boton>
        </div>
      )}
      <Modal
        abierto={abierto}
        titulo="Confirmar compra"
        ocupado={compra.isPending}
        onCerrar={() => {
          setAbierto(false)
          compra.reset()
        }}
      >
        <p>Evento: {evento.name}</p>
        <p>Tipo: {tipo}</p>
        {sesion.estado === 'autenticado' && (
          <p>Cuenta: {sesion.usuario.email}</p>
        )}
        {compra.isError && (
          <Aviso tipo="error">
            {compra.error instanceof ApiError
              ? compra.error.message
              : 'No se pudo completar la compra.'}
          </Aviso>
        )}
        <div className="acciones">
          <Boton
            variante="secundario"
            disabled={compra.isPending}
            onClick={() => {
              setAbierto(false)
              compra.reset()
            }}
          >
            Volver
          </Boton>
          <Boton cargando={compra.isPending} onClick={() => compra.mutate()}>
            Confirmar compra
          </Boton>
        </div>
      </Modal>
    </section>
  )
}
