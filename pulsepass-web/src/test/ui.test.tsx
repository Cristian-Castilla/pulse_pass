import { it, expect, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Boton, Campo, Modal, Badge, Aviso } from '../componentes/ui'
it('botón cargando no permite segundo envío', async () => {
  const click = vi.fn()
  render(
    <Boton cargando onClick={click}>
      Guardar
    </Boton>,
  )
  await userEvent.click(screen.getByRole('button'))
  expect(click).not.toHaveBeenCalled()
  expect(screen.getByRole('button')).toBeDisabled()
})
it('campo tiene etiqueta y error accesibles', () => {
  render(<Campo etiqueta="Nombre" error="Requerido" />)
  expect(screen.getByLabelText('Nombre')).toHaveAttribute(
    'aria-invalid',
    'true',
  )
  expect(screen.getByRole('alert')).toHaveTextContent('Requerido')
})
it('modal se identifica como diálogo y cierra con Escape', async () => {
  const cerrar = vi.fn()
  render(
    <Modal titulo="Confirmar" abierto onCerrar={cerrar}>
      <p>Contenido</p>
    </Modal>,
  )
  expect(screen.getByRole('dialog')).toHaveAccessibleName('Confirmar')
  await userEvent.keyboard('{Escape}')
  expect(cerrar).toHaveBeenCalled()
})
it('badges muestran estado traducido sin depender del color', () => {
  render(<Badge estado="PAID" />)
  expect(screen.getByText('Pagado')).toBeInTheDocument()
})
it('éxito desaparece a los cuatro segundos pero error permanece', async () => {
  vi.useFakeTimers()
  render(
    <>
      <Aviso tipo="exito">Guardado</Aviso>
      <Aviso tipo="error">Rechazado</Aviso>
    </>,
  )
  expect(screen.getByText('Guardado')).toBeInTheDocument()
  await act(async () => {
    vi.advanceTimersByTime(4000)
  })
  expect(screen.queryByText('Guardado')).not.toBeInTheDocument()
  expect(screen.getByText('Rechazado')).toBeInTheDocument()
})
