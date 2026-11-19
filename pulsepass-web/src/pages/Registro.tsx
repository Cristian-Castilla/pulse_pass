import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from 'react-router'
import { authApi } from '../funcionalidades/auth/api'
import { RegistroSchema } from '../funcionalidades/auth/tipos'
import type { Registro as DatosRegistro } from '../funcionalidades/auth/tipos'
import { erroresFormulario } from '../funcionalidades/auth/componentes/Formulario'
import { ApiError } from '../lib/http'
import { RUTAS } from '../lib/rutas'
import { Boton, Campo, Aviso } from '../componentes/ui'
export default function Registro() {
  const navegar = useNavigate()
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<DatosRegistro>({ resolver: zodResolver(RegistroSchema) })
  const password = useWatch({ control, name: 'password', defaultValue: '' })
  return (
    <section className="tarjeta mx-auto max-w-xl">
      <h1 className="text-center text-2xl">Crea tu cuenta</h1>
      <form
        noValidate
        onSubmit={handleSubmit(async (datos) => {
          try {
            await authApi.registrar(datos)
            await navegar(RUTAS.login, { state: { registrado: true } })
          } catch (error) {
            if (error instanceof ApiError && error.status === 409)
              setError('root', {
                message: 'El usuario o correo ya está registrado',
              })
            else
              erroresFormulario(error, setError, [
                'username',
                'email',
                'firstName',
                'lastName',
                'birthDate',
                'password',
              ])
          }
        })}
      >
        <Campo
          etiqueta="Nombre de usuario"
          autoComplete="username"
          error={errors.username?.message}
          {...register('username')}
        />
        <Campo
          etiqueta="Correo electrónico"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo
            etiqueta="Nombres"
            autoComplete="given-name"
            error={errors.firstName?.message}
            {...register('firstName')}
          />
          <Campo
            etiqueta="Apellidos"
            autoComplete="family-name"
            error={errors.lastName?.message}
            {...register('lastName')}
          />
        </div>
        <Campo
          etiqueta="Fecha de nacimiento"
          type="date"
          autoComplete="bday"
          error={errors.birthDate?.message}
          {...register('birthDate')}
        />
        <Campo
          etiqueta="Contraseña (mínimo 12 caracteres)"
          type="password"
          autoComplete="new-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Hasta 72 bytes UTF-8. Tu cuenta se crea con el rol USER.
        </p>
        <label className="text-sm" htmlFor="longitud-clave">
          Longitud:{' '}
          {password.length >= 12 ? 'mínimo alcanzado' : 'mínimo 12 caracteres'}
        </label>
        <meter
          id="longitud-clave"
          min={0}
          max={12}
          value={Math.min(12, password.length)}
          className="mb-4 block w-full"
        />
        {errors.root && <Aviso tipo="error">{errors.root.message}</Aviso>}
        <Boton type="submit" cargando={isSubmitting} className="w-full">
          Crear cuenta
        </Boton>
      </form>
      <p className="mt-6 text-center text-sm">
        ¿Ya tienes cuenta?{' '}
        <Link className="enlace" to={RUTAS.login}>
          Inicia sesión
        </Link>
      </p>
    </section>
  )
}
