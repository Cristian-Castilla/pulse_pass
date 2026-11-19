import { z } from 'zod'
import { http } from '../../lib/http'
import { SedeSchema } from './tipos'
export const sedesApi = {
  activas: (signal?: AbortSignal) =>
    http('/venues/active', z.array(SedeSchema), { signal }),
  sede: (code: string, signal?: AbortSignal) =>
    http(`/venues/${encodeURIComponent(code)}`, SedeSchema, { signal }),
}
