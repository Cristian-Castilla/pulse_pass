import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '../../../lib/http'
export function erroresFormulario<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  campos: string[],
) {
  if (error instanceof ApiError && error.status === 400) {
    let generales = ''
    for (const [campo, message] of Object.entries(error.details)) {
      if (campos.includes(campo)) setError(campo as Path<T>, { message })
      else generales += `${message} `
    }
    if (generales) setError('root', { message: generales })
    else if (!Object.keys(error.details).length)
      setError('root', { message: error.message })
  } else
    setError('root', {
      message:
        error instanceof ApiError
          ? error.message
          : 'No pudimos completar la solicitud. Inténtalo de nuevo.',
    })
}
