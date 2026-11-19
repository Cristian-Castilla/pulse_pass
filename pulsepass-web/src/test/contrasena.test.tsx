import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { it, expect } from 'vitest'
import { Campo } from '../componentes/ui'
it('contraseña se muestra solo por acción explícita y se puede ocultar', async () => {
  render(<Campo etiqueta="Clave de prueba" type="password" />)
  const input = screen.getByLabelText('Clave de prueba')
  expect(input).toHaveAttribute('type', 'password')
  await userEvent.click(
    screen.getByRole('button', { name: 'Mostrar contraseña' }),
  )
  expect(input).toHaveAttribute('type', 'text')
  await userEvent.click(
    screen.getByRole('button', { name: 'Ocultar contraseña' }),
  )
  expect(input).toHaveAttribute('type', 'password')
})
