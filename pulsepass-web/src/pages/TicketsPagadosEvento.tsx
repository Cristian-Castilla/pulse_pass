import { useNavigate, useParams, useSearchParams } from 'react-router'
import { z } from 'zod'
import {
  useTicketsPagados,
  useUsarTicket,
} from '../funcionalidades/tickets/hooks'
import { CodigoSchema } from '../lib/parametros'
import { RUTAS } from '../lib/rutas'
import { FormularioCodigo } from '../componentes/FormularioCodigo'
import { Consulta } from '../componentes/Consulta'
import {
  Aviso,
  Badge,
  Boton,
  EstadoVacio,
  PrecioCOP,
  Tabla,
} from '../componentes/ui'
import { ApiError } from '../lib/http'
export default function TicketsPagadosEvento() {
  const param = useParams().eventCode
  const code = CodigoSchema.safeParse(param)
  const [params] = useSearchParams()
  const buscar = z.literal('1').safeParse(params.get('seleccionar')).success
  const consulta = useTicketsPagados(
    code.success ? code.data : '',
    code.success && !buscar,
  )
  const navegar = useNavigate()
  const usar = useUsarTicket()
  return (
    <section>
      <h1>Tickets pagados{!buscar && code.success ? ` · ${code.data}` : ''}</h1>
      <FormularioCodigo
        key={param}
        etiqueta="Código del evento"
        accion="Ir"
        inicial={!buscar && code.success ? code.data : ''}
        onBuscar={(c) => {
          usar.reset()
          void navegar(RUTAS.pagados(c))
        }}
      />
      {!buscar && code.success && (
        <Consulta consulta={consulta}>
          {(datos) =>
            datos.length ? (
              <>
                <p>Total: {datos.length}</p>
                <div className="tabla-responsive">
                  <Tabla
                    filas={datos}
                    clave={(t) => t.ticketCode}
                    columnas={[
                      {
                        encabezado: 'Código',
                        celda: (t) => <code>{t.ticketCode}</code>,
                      },
                      { encabezado: 'Tipo', celda: (t) => t.type },
                      {
                        encabezado: 'Titular',
                        celda: (t) => (
                          <span className="min-w-0 wrap-anywhere">
                            {t.userFullName ?? 'No informado'}
                          </span>
                        ),
                      },
                      {
                        encabezado: 'Precio',
                        celda: (t) => <PrecioCOP valor={t.price} />,
                      },
                      {
                        encabezado: 'Estado',
                        celda: (t) => <Badge estado={t.status} />,
                      },
                      {
                        encabezado: 'Acción',
                        celda: (t) => (
                          <Boton
                            disabled={usar.isPending}
                            cargando={
                              usar.isPending && usar.variables === t.ticketCode
                            }
                            onClick={() => usar.mutate(t.ticketCode)}
                          >
                            Usar
                          </Boton>
                        ),
                      },
                    ]}
                  />
                </div>
              </>
            ) : (
              <EstadoVacio mensaje="No hay tickets pagados" />
            )
          }
        </Consulta>
      )}
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
