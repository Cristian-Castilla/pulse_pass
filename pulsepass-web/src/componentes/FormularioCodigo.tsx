import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { CodigoSchema } from '../lib/parametros'
import { Campo, Boton } from './ui'
const Schema = z.object({ codigo: CodigoSchema })
interface FormularioCodigoProps {
  etiqueta: string
  accion: string
  inicial?: string
  onBuscar: (codigo: string) => void
  limpiar?: boolean
}
export function FormularioCodigo({
  etiqueta,
  accion,
  inicial = '',
  onBuscar,
  limpiar = false,
}: FormularioCodigoProps) {
  const {
    register,
    handleSubmit,
    reset,
    setFocus,
    formState: { errors },
  } = useForm<{ codigo: string }>({
    resolver: zodResolver(Schema),
    defaultValues: { codigo: inicial },
  })
  return (
    <form
      noValidate
      className="mb-6 flex flex-wrap items-start gap-3"
      onSubmit={handleSubmit(({ codigo }) => {
        onBuscar(codigo)
        if (limpiar) reset({ codigo: '' })
        setFocus('codigo')
      })}
    >
      <div className="min-w-0 flex-1">
        <Campo
          etiqueta={etiqueta}
          error={errors.codigo?.message}
          {...register('codigo')}
          autoComplete="off"
          autoFocus
        />
      </div>
      <Boton className="mt-7" type="submit">
        {accion}
      </Boton>
    </form>
  )
}
