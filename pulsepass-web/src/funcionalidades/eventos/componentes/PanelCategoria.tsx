import type { Categoria } from '../tipos'
import { Icono } from '../../../componentes/ui'
import { clsx } from 'clsx'
const paneles: Record<Categoria, string> = {
  MUSIC:
    'bg-indigo-100 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-200',
  SPORTS:
    'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200',
  TECHNOLOGY: 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-200',
  EDUCATION:
    'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200',
  CULTURE: 'bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-200',
  ENTERTAINMENT:
    'bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-200',
}
export function PanelCategoria({ categoria }: { categoria: Categoria }) {
  return (
    <div className={clsx('panel-categoria', paneles[categoria])}>
      <Icono
        nombre={
          categoria === 'MUSIC'
            ? 'musica'
            : categoria === 'SPORTS'
              ? 'estrella'
              : categoria === 'CULTURE'
                ? 'calendario'
                : 'estrella'
        }
      />
    </div>
  )
}
