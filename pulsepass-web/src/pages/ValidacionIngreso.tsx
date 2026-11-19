import { useState } from 'react'
import { useTicket, useUsarTicket } from '../funcionalidades/tickets/hooks'
import { FormularioCodigo } from '../componentes/FormularioCodigo'
import { Consulta } from '../componentes/Consulta'
import { Aviso, Badge, Boton } from '../componentes/ui'
import { ApiError } from '../lib/http'
export default function ValidacionIngreso() {
  const [code, setCode] = useState('')
  const consulta = useTicket(code, !!code)
  const usar = useUsarTicket()
  return (
    <section className="estrecho">
      <h1>Validación de ingreso</h1>
      <FormularioCodigo
        etiqueta="Código de ticket"
        accion="Buscar"
        limpiar
        onBuscar={(c) => {
          usar.reset()
          setCode(c)
        }}
      />
      {code &&
        (consulta.error instanceof ApiError &&
        [403, 404].includes(consulta.error.status) ? (
          <Aviso tipo="error">
            {consulta.error.status === 404
              ? 'Ticket no encontrado'
              : 'Sin permisos'}
          </Aviso>
        ) : (
          <Consulta consulta={consulta}>
            {(t) => (
              <article className="tarjeta">
                <div className="flex flex-wrap justify-between gap-3">
                  <h2>{t.eventName}</h2>
                  <Badge estado={t.status} />
                </div>
                <p>
                  <code>{t.ticketCode}</code> · {t.type}
                </p>
                <p className="wrap-anywhere">
                  Titular: <span>{t.userFullName ?? 'No informado'}</span>
                </p>
                <Boton
                  className="w-full"
                  cargando={usar.isPending}
                  onClick={() => usar.mutate(t.ticketCode)}
                >
                  Marcar como usado
                </Boton>
              </article>
            )}
          </Consulta>
        ))}
      {usar.isSuccess && <Aviso tipo="exito">Ingreso registrado.</Aviso>}
      {usar.isError && (
        <Aviso tipo="error">
          {usar.error instanceof ApiError
            ? usar.error.message
            : 'No se pudo registrar el ingreso.'}
        </Aviso>
      )}
    </section>
  )
}
