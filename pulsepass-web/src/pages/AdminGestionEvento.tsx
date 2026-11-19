import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useParams, useLocation } from 'react-router'
import { useEvento } from '../funcionalidades/eventos/hooks'
import { eventosApi } from '../funcionalidades/eventos/api'
import { useArtistasActivos } from '../funcionalidades/artistas/hooks'
import { Consulta } from '../componentes/Consulta'
import { Badge, Boton, Selector, Aviso, Modal } from '../componentes/ui'
import { CodigoSchema } from '../lib/parametros'
import { RUTAS } from '../lib/rutas'
import { ApiError } from '../lib/http'
import { fecha } from '../lib/formato'
const Schema = z.object({
  artistId: z.number().int().positive('Selecciona un artista'),
})
export default function AdminGestionEvento() {
  const location = useLocation()
  const creado = z
    .object({ creado: z.literal(true) })
    .safeParse(location.state).success
  const code = CodigoSchema.safeParse(useParams().eventCode)
  const codigo = code.success ? code.data : ''
  const consulta = useEvento(codigo, code.success)
  const artistas = useArtistasActivos()
  const [modal, setModal] = useState(false)
  const client = useQueryClient()
  const refrescar = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['evento', codigo] }),
      client.invalidateQueries({ queryKey: ['eventos'] }),
    ])
  }
  const publicar = useMutation({
    mutationFn: () => eventosApi.publicar(codigo),
    onSuccess: async () => {
      await refrescar()
      setModal(false)
    },
  })
  const asociar = useMutation({
    mutationFn: (id: number) => eventosApi.asociar(codigo, id),
    onSuccess: refrescar,
  })
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<{ artistId: number }>({ resolver: zodResolver(Schema) })
  if (!code.success) return <h1>Evento no disponible</h1>
  return (
    <section className="estrecho">
      <Link className="enlace" to={RUTAS.admin}>
        Volver a eventos
      </Link>
      {creado && <Aviso tipo="exito">Evento creado.</Aviso>}
      {publicar.isSuccess && <Aviso tipo="exito">Evento publicado.</Aviso>}
      <Consulta consulta={consulta}>
        {(e) => (
          <article className="tarjeta mt-6">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <h1 className="mb-2 text-2xl">
                {e.name} · <code>{e.eventCode}</code>
              </h1>
              <Badge estado={e.status} />
            </div>
            <p>
              {e.category} · {fecha(e.eventDate)} · Mín. {e.minimumAge} ·{' '}
              {e.venueName}
            </p>
            <section className="mt-8 border-t border-slate-200 pt-6 dark:border-slate-700">
              <h2>Publicación</h2>
              <p>El evento es visible al público una vez publicado.</p>
              <Boton
                disabled={e.status !== 'DRAFT'}
                onClick={() => {
                  publicar.reset()
                  setModal(true)
                }}
              >
                Publicar evento
              </Boton>
            </section>
            <section className="mt-8 border-t border-slate-200 pt-6 dark:border-slate-700">
              <h2>Artistas asociados</h2>
              <ul className="mb-6 list-inside list-disc">
                {e.artists.map((a) => (
                  <li key={a.id} aria-label={a.stageName}>
                    {a.stageName}
                  </li>
                ))}
              </ul>
              <form
                noValidate
                onSubmit={handleSubmit(({ artistId }) =>
                  asociar.mutate(artistId),
                )}
              >
                <Consulta consulta={artistas}>
                  {(lista) => (
                    <Selector
                      etiqueta="Agregar artista"
                      error={errors.artistId?.message}
                      {...register('artistId', { valueAsNumber: true })}
                    >
                      <option value="">Selecciona un artista</option>
                      {lista.map((a) => (
                        <option
                          key={a.id}
                          value={a.id}
                          disabled={e.artists.some((x) => x.id === a.id)}
                        >
                          {a.stageName}
                        </option>
                      ))}
                    </Selector>
                  )}
                </Consulta>
                <Boton type="submit" cargando={asociar.isPending}>
                  Asociar
                </Boton>
              </form>
              {asociar.isSuccess && (
                <Aviso tipo="exito">Artista asociado.</Aviso>
              )}
              {asociar.isError && (
                <Aviso tipo="error">
                  {asociar.error instanceof ApiError
                    ? asociar.error.message
                    : 'No se pudo asociar el artista.'}
                </Aviso>
              )}
            </section>
            <Link
              className="enlace mt-8 inline-block"
              to={RUTAS.pagados(e.eventCode)}
            >
              Tickets pagados
            </Link>
            <Modal
              abierto={modal}
              ocupado={publicar.isPending}
              titulo="Publicar evento"
              onCerrar={() => setModal(false)}
            >
              <p>¿Confirmas publicar {e.name}?</p>
              {publicar.isError && (
                <Aviso tipo="error">
                  {publicar.error instanceof ApiError
                    ? publicar.error.message
                    : 'No se pudo publicar el evento.'}
                </Aviso>
              )}
              <div className="acciones">
                <Boton
                  variante="secundario"
                  disabled={publicar.isPending}
                  onClick={() => setModal(false)}
                >
                  Volver
                </Boton>
                <Boton
                  cargando={publicar.isPending}
                  onClick={() => publicar.mutate()}
                >
                  Confirmar publicación
                </Boton>
              </div>
            </Modal>
          </article>
        )}
      </Consulta>
    </section>
  )
}
