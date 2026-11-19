import { useEffect, useId, useRef, useState } from 'react'
import type { ComponentProps, ReactNode } from 'react'
import { clsx } from 'clsx'
import { ApiError } from '../lib/http'
import { precioCOP } from '../lib/formato'
interface BotonProps extends ComponentProps<'button'> {
  variante?: 'primario' | 'secundario' | 'peligro' | 'fantasma'
  cargando?: boolean
}
const variantes = {
  primario: 'bg-marca-600 text-white hover:bg-marca-700',
  secundario:
    'border border-slate-300 bg-white text-slate-900 hover:bg-slate-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100',
  peligro: 'bg-red-700 text-white hover:bg-red-800',
  fantasma:
    'bg-transparent text-marca-700 hover:bg-marca-50 dark:text-indigo-300 dark:hover:bg-slate-800',
}
export function Boton({
  variante = 'primario',
  cargando = false,
  disabled,
  className,
  children,
  type = 'button',
  ...props
}: BotonProps) {
  return (
    <button
      type={type}
      disabled={disabled || cargando}
      aria-busy={cargando || undefined}
      className={clsx('btn', variantes[variante], className)}
      {...props}
    >
      {cargando ? 'Cargando…' : children}
    </button>
  )
}
interface CampoProps extends ComponentProps<'input'> {
  etiqueta: string
  error?: string
}
export function Campo({ etiqueta, error, id, type, ...props }: CampoProps) {
  const propio = useId()
  const campoId = id ?? propio
  const [visible, setVisible] = useState(false)
  return (
    <div className="campo">
      <label htmlFor={campoId}>{etiqueta}</label>
      <div className="relative">
        <input
          id={campoId}
          type={type === 'password' && visible ? 'text' : type}
          className={type === 'password' ? 'pr-14' : undefined}
          aria-invalid={!!error}
          aria-describedby={error ? `${campoId}-error` : undefined}
          {...props}
        />
        {type === 'password' && (
          <button
            type="button"
            className="absolute inset-y-0 right-1 inline-flex min-h-11 min-w-11 items-center justify-center rounded text-slate-600 dark:text-slate-300"
            aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
            aria-pressed={visible}
            onClick={() => setVisible(!visible)}
          >
            <svg
              aria-hidden="true"
              width="22"
              height="22"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
            >
              <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          </button>
        )}
      </div>
      {error && (
        <p id={`${campoId}-error`} role="alert" className="error-campo">
          {error}
        </p>
      )}
    </div>
  )
}
interface SelectorProps extends ComponentProps<'select'> {
  etiqueta: string
  error?: string
}
export function Selector({
  etiqueta,
  error,
  id,
  children,
  ...props
}: SelectorProps) {
  const propio = useId()
  const campoId = id ?? propio
  return (
    <div className="campo">
      <label htmlFor={campoId}>{etiqueta}</label>
      <select
        id={campoId}
        aria-invalid={!!error}
        aria-describedby={error ? `${campoId}-error` : undefined}
        {...props}
      >
        {children}
      </select>
      {error && (
        <p id={`${campoId}-error`} role="alert" className="error-campo">
          {error}
        </p>
      )}
    </div>
  )
}
const estados: Record<string, { texto: string; clase: string }> = {
  DRAFT: { texto: 'Borrador', clase: 'bg-slate-200 text-slate-800' },
  PUBLISHED: { texto: 'Publicado', clase: 'bg-emerald-100 text-emerald-900' },
  SOLD_OUT: { texto: 'Agotado', clase: 'bg-amber-100 text-amber-900' },
  CANCELLED: { texto: 'Cancelado', clase: 'bg-red-100 text-red-900' },
  FINISHED: { texto: 'Finalizado', clase: 'bg-slate-200 text-slate-800' },
  RESERVED: { texto: 'Reservado', clase: 'bg-sky-100 text-sky-900' },
  PAID: { texto: 'Pagado', clase: 'bg-emerald-100 text-emerald-900' },
  USED: { texto: 'Usado', clase: 'bg-slate-200 text-slate-800' },
}
export function Badge({ estado }: { estado: string }) {
  const estilo = estados[estado]
  return (
    <span
      className={clsx('badge', estilo?.clase ?? 'bg-slate-200 text-slate-900')}
    >
      {estilo?.texto ?? estado}
    </span>
  )
}
export function Tarjeta({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return <div className={clsx('tarjeta', className)}>{children}</div>
}
export function Cargando() {
  return (
    <div role="status" aria-busy="true" className="space-y-4 py-6">
      <span className="sr-only">Cargando…</span>
      <div className="h-8 w-2/3 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
      <div className="h-28 animate-pulse rounded-xl bg-slate-200 dark:bg-slate-800" />
    </div>
  )
}
export function EstadoVacio({
  mensaje,
  children,
}: {
  mensaje: string
  children?: ReactNode
}) {
  return (
    <div className="estado">
      <Icono nombre="calendario" />
      <h2>{mensaje}</h2>
      {children}
    </div>
  )
}
export function EstadoError({
  error,
  onReintentar,
}: {
  error: unknown
  onReintentar?: () => void
}) {
  return (
    <div role="alert" className="estado">
      <Icono nombre="alerta" />
      <h2>
        {error instanceof ApiError
          ? error.message
          : 'Ocurrió un error inesperado. Inténtalo más tarde.'}
      </h2>
      {onReintentar && (
        <Boton variante="secundario" onClick={onReintentar}>
          Reintentar
        </Boton>
      )}
    </div>
  )
}
export function Aviso({
  children,
  tipo = 'info',
}: {
  children: ReactNode
  tipo?: 'exito' | 'error' | 'info'
}) {
  const [descartado, setDescartado] = useState<ReactNode>(null)
  useEffect(() => {
    if (tipo !== 'exito') return
    const timer = window.setTimeout(() => setDescartado(children), 4000)
    return () => window.clearTimeout(timer)
  }, [tipo, children])
  if (descartado === children) return null
  return (
    <div
      role={tipo === 'error' ? 'alert' : 'status'}
      aria-live={tipo === 'error' ? 'assertive' : 'polite'}
      className={clsx('aviso', {
        'bg-red-50 text-red-900 dark:bg-red-950 dark:text-red-100':
          tipo === 'error',
        'bg-emerald-50 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-100':
          tipo === 'exito',
        'bg-indigo-50 text-indigo-900 dark:bg-indigo-950 dark:text-indigo-100':
          tipo === 'info',
      })}
    >
      <div className="flex items-start justify-between gap-3">
        <div>{children}</div>
        <button
          type="button"
          className="inline-flex min-h-11 min-w-11 items-center justify-center rounded"
          aria-label="Cerrar aviso"
          onClick={() => setDescartado(children)}
        >
          <Icono nombre="cerrar" />
        </button>
      </div>
    </div>
  )
}
export function PrecioCOP({ valor }: { valor: number }) {
  return <span className="tabular-nums">{precioCOP(valor)}</span>
}
interface ModalProps {
  abierto: boolean
  titulo: string
  onCerrar: () => void
  children: ReactNode
  ocupado?: boolean
}
export function Modal({
  abierto,
  titulo,
  onCerrar,
  children,
  ocupado = false,
}: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const title = useId()
  useEffect(() => {
    const d = ref.current
    const anterior = document.activeElement
    if (abierto && !d?.open) d?.showModal()
    if (!abierto && d?.open) d.close()
    return () => {
      if (d?.open) d.close()
      if (anterior instanceof HTMLElement) anterior.focus()
    }
  }, [abierto])
  return (
    <dialog
      ref={ref}
      aria-labelledby={title}
      onCancel={(e) => {
        e.preventDefault()
        if (!ocupado) onCerrar()
      }}
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.preventDefault()
          if (!ocupado) onCerrar()
        }
      }}
      className="modal"
    >
      {abierto && (
        <>
          <div className="flex items-center justify-between gap-4">
            <h2 id={title}>{titulo}</h2>
            <Boton
              variante="fantasma"
              aria-label="Cerrar diálogo"
              disabled={ocupado}
              onClick={onCerrar}
            >
              <Icono nombre="cerrar" />
            </Boton>
          </div>
          {children}
        </>
      )}
    </dialog>
  )
}
export function Tabla<T>({
  columnas,
  filas,
  clave,
}: {
  columnas: { encabezado: string; celda: (fila: T) => ReactNode }[]
  filas: T[]
  clave: (fila: T) => string
}) {
  return (
    <div className="overflow-x-auto">
      <table>
        <thead>
          <tr>
            {columnas.map((c) => (
              <th key={c.encabezado} scope="col">
                {c.encabezado}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filas.map((f) => (
            <tr key={clave(f)}>
              {columnas.map((c) => (
                <td key={c.encabezado} data-label={c.encabezado}>
                  {c.celda(f)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

const trazos = {
  musica:
    'M9 18V5l12-2v13M9 18c0 1.7-1.8 3-4 3s-4-1.3-4-3 1.8-3 4-3 4 1.3 4 3Zm12-2c0 1.7-1.8 3-4 3s-4-1.3-4-3 1.8-3 4-3 4 1.3 4 3Z',
  calendario:
    'M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z',
  estrella: 'm12 2 3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1Z',
  ubicacion:
    'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  alerta: 'm12 3 10 18H2L12 3ZM12 9v5M12 17v1',
  cerrar: 'm6 6 12 12M18 6 6 18',
  menu: 'M3 6h18M3 12h18M3 18h18',
  sol: 'M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1 1M18 18l1 1M5 19l1-1M18 6l1-1M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
}
export function Icono({ nombre = 'musica' }: { nombre?: keyof typeof trazos }) {
  return (
    <svg
      aria-hidden="true"
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={trazos[nombre]} />
    </svg>
  )
}
