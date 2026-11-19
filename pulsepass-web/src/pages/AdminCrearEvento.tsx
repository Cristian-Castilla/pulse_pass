import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import {
  CrearEventoSchema,
  CategoriaSchema,
} from '../funcionalidades/eventos/tipos'
import type { CrearEvento } from '../funcionalidades/eventos/tipos'
import { eventosApi } from '../funcionalidades/eventos/api'
import { useSedesActivas } from '../funcionalidades/sedes/hooks'
import { recordarCreado } from '../funcionalidades/admin/creados'
import { erroresFormulario } from '../funcionalidades/auth/componentes/Formulario'
import { Campo, Selector, Boton, Aviso } from '../componentes/ui'
import { Consulta } from '../componentes/Consulta'
import { ApiError, tokenActual } from '../lib/http'
import { RUTAS } from '../lib/rutas'
export default function AdminCrearEvento() {
  const sedes = useSedesActivas()
  const crear = useMutation({ mutationFn: eventosApi.crear })
  const client = useQueryClient()
  const navegar = useNavigate()
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CrearEvento>({
    resolver: zodResolver(CrearEventoSchema),
    defaultValues: { category: 'MUSIC', minimumAge: 0, description: '' },
  })
  const descripcion = useWatch({
    control,
    name: 'description',
    defaultValue: '',
  })
  return (
    <section className="estrecho">
      <Link className="enlace" to={RUTAS.admin}>
        Volver a eventos
      </Link>
      <h1 className="mt-6">Nuevo evento</h1>
      <form
        noValidate
        className="tarjeta"
        onSubmit={handleSubmit(async (datos) => {
          const actual = tokenActual()
          try {
            const evento = await crear.mutateAsync(datos)
            if (!actual || tokenActual() !== actual) return
            client.setQueryData(['evento', evento.eventCode], evento)
            recordarCreado(evento.eventCode)
            await navegar(RUTAS.gestion(evento.eventCode), {
              state: { creado: true },
            })
          } catch (error) {
            if (error instanceof ApiError && error.status === 403)
              await navegar(RUTAS.sinPermisos)
            else
              erroresFormulario(error, setError, [
                'eventCode',
                'name',
                'description',
                'category',
                'eventDate',
                'minimumAge',
                'venueCode',
              ])
          }
        })}
      >
        <Campo
          etiqueta="Código del evento"
          error={errors.eventCode?.message}
          {...register('eventCode')}
        />
        <Campo
          etiqueta="Nombre"
          error={errors.name?.message}
          {...register('name')}
        />
        <div className="campo">
          <label htmlFor="descripcion">Descripción</label>
          <textarea
            id="descripcion"
            maxLength={500}
            aria-invalid={!!errors.description}
            aria-describedby={
              errors.description ? 'descripcion-error' : 'descripcion-ayuda'
            }
            {...register('description')}
          />
          <p id="descripcion-ayuda" className="text-sm">
            {descripcion?.length ?? 0}/500 caracteres
          </p>
          {errors.description && (
            <p id="descripcion-error" role="alert">
              {errors.description.message}
            </p>
          )}
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Selector
            etiqueta="Categoría"
            error={errors.category?.message}
            {...register('category')}
          >
            {CategoriaSchema.options.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </Selector>
          <Campo
            etiqueta="Edad mínima"
            type="number"
            min={0}
            step={1}
            error={errors.minimumAge?.message}
            {...register('minimumAge', { valueAsNumber: true })}
          />
        </div>
        <Campo
          etiqueta="Fecha y hora"
          type="datetime-local"
          error={errors.eventDate?.message}
          {...register('eventDate')}
        />
        <Consulta consulta={sedes}>
          {(lista) => (
            <Selector
              etiqueta="Sede"
              error={errors.venueCode?.message}
              {...register('venueCode')}
            >
              <option value="">Selecciona una sede</option>
              {lista.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.name} ({s.code})
                </option>
              ))}
            </Selector>
          )}
        </Consulta>
        {sedes.data?.length === 0 && (
          <Aviso>No hay sedes activas. No es posible crear un evento.</Aviso>
        )}
        {errors.root && <Aviso tipo="error">{errors.root.message}</Aviso>}
        <div className="acciones justify-end">
          <Link className="enlace" to={RUTAS.admin}>
            Cancelar
          </Link>
          <Boton
            type="submit"
            cargando={isSubmitting}
            disabled={!sedes.data?.length}
          >
            Crear evento
          </Boton>
        </div>
      </form>
    </section>
  )
}
