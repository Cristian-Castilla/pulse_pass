import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '../funcionalidades/auth/hooks'
import { LoginSchema } from '../funcionalidades/auth/tipos'
import type { Login as DatosLogin } from '../funcionalidades/auth/tipos'
import { erroresFormulario } from '../funcionalidades/auth/componentes/Formulario'
import { Boton, Campo, Aviso, Icono } from '../componentes/ui'
import { destinoSeguro } from '../lib/parametros'
import { RUTAS } from '../lib/rutas'
export default function Login() {
  const { iniciarSesion } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const parsed = z
    .object({
      desde: z.unknown().optional(),
      motivo: z.string().optional(),
      registrado: z.boolean().optional(),
    })
    .safeParse(location.state)
  const state = parsed.success ? parsed.data : null
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DatosLogin>({ resolver: zodResolver(LoginSchema) })
  return (
    <section className="mx-auto max-w-md">
      <div className="tarjeta">
        <div className="text-marca-700 mb-6 flex justify-center">
          <Icono />
        </div>
        <h1 className="text-center text-2xl">Inicia sesión</h1>
        {state?.registrado && (
          <Aviso tipo="exito">Cuenta creada, inicia sesión</Aviso>
        )}
        {state?.motivo === 'expirada' && <Aviso>Tu sesión expiró</Aviso>}
        <form
          noValidate
          onSubmit={handleSubmit(async (datos) => {
            try {
              await iniciarSesion(datos)
              await navigate(destinoSeguro(state?.desde), { replace: true })
            } catch (error) {
              erroresFormulario(error, setError, ['identifier', 'password'])
            }
          })}
        >
          <Campo
            etiqueta="Usuario o correo"
            autoComplete="username"
            error={errors.identifier?.message}
            {...register('identifier')}
          />
          <Campo
            etiqueta="Contraseña"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />
          {errors.root && <Aviso tipo="error">{errors.root.message}</Aviso>}
          <Boton type="submit" cargando={isSubmitting} className="w-full">
            Ingresar
          </Boton>
        </form>
        <p className="mt-6 text-center text-sm">
          ¿No tienes cuenta?{' '}
          <Link className="enlace" to={RUTAS.registro}>
            Regístrate
          </Link>
        </p>
      </div>
    </section>
  )
}
