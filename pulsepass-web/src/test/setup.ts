import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, vi } from 'vitest'
import { cleanup, configure } from '@testing-library/react'
configure({ asyncUtilTimeout: 5000 })
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-11-18T12:00:00'))
})
afterEach(() => {
  cleanup()
  sessionStorage.clear()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
  value: function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
    this.querySelector<HTMLElement>('button,input')?.focus()
  },
})
Object.defineProperty(HTMLDialogElement.prototype, 'close', {
  value: function (this: HTMLDialogElement) {
    this.removeAttribute('open')
  },
})
window.matchMedia = vi.fn().mockImplementation((query: string) => ({
  matches: false,
  media: query,
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
}))
