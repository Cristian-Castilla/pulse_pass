import { useState } from 'react'
import { useCancelarTicket } from '../hooks'
import type { Ticket } from '../tipos'
import { Boton, Modal, Aviso } from '../../../componentes/ui'
import { ApiError } from '../../../lib/http'
export function CancelarTicket({ ticket }: { ticket: Ticket }) {
  const [abierto, setAbierto] = useState(false)
  const cancelar = useCancelarTicket()
  if (ticket.status !== 'PAID') return null
  return (
    <>
      <Boton
        variante="peligro"
        onClick={() => {
          cancelar.reset()
          setAbierto(true)
        }}
      >
        Cancelar
      </Boton>
      <Modal
        abierto={abierto}
        titulo="Cancelar ticket"
        ocupado={cancelar.isPending}
        onCerrar={() => {
          setAbierto(false)
          cancelar.reset()
        }}
      >
        <p>
          ¿Confirmas cancelar el ticket <code>{ticket.ticketCode}</code> de{' '}
          {ticket.eventName}?
        </p>
        <p>El servidor comprobará si la cancelación está permitida.</p>
        {cancelar.isError && (
          <Aviso tipo="error">
            {cancelar.error instanceof ApiError
              ? cancelar.error.message
              : 'No se pudo cancelar el ticket.'}
          </Aviso>
        )}
        <div className="acciones">
          <Boton
            variante="secundario"
            disabled={cancelar.isPending}
            onClick={() => setAbierto(false)}
          >
            Conservar ticket
          </Boton>
          <Boton
            variante="peligro"
            cargando={cancelar.isPending}
            onClick={() =>
              cancelar.mutate(ticket.ticketCode, {
                onSuccess: () => setAbierto(false),
              })
            }
          >
            Confirmar cancelación
          </Boton>
        </div>
      </Modal>
    </>
  )
}
