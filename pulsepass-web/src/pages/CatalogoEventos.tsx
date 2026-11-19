import { useSearchParams } from 'react-router'
import {
  useEventosPublicados,
  useEventosPorArtista,
} from '../funcionalidades/eventos/hooks'
import { CategoriaSchema } from '../funcionalidades/eventos/tipos'
import {
  useArtistasActivos,
  useArtistaPorNombre,
} from '../funcionalidades/artistas/hooks'
import { BusquedaSchema } from '../lib/parametros'
import { Campo, Selector, Boton, EstadoVacio } from '../componentes/ui'
import { Consulta } from '../componentes/Consulta'
import { TarjetaEvento } from '../funcionalidades/eventos/componentes/TarjetaEvento'
export default function CatalogoEventos() {
  const [params, setParams] = useSearchParams()
  const q = BusquedaSchema.safeParse(params.get('q') ?? '')
  const cat = CategoriaSchema.safeParse(params.get('categoria'))
  const artista = BusquedaSchema.safeParse(params.get('artista') ?? '')
  const nombre = artista.success ? artista.data : ''
  const publicados = useEventosPublicados()
  const porArtista = useEventosPorArtista(nombre)
  const artistas = useArtistasActivos()
  const resuelto = useArtistaPorNombre(nombre)
  const consulta = nombre ? porArtista : publicados
  function filtro(clave: string, valor: string) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (valor) next.set(clave, valor)
        else next.delete(clave)
        return next
      },
      { replace: true },
    )
  }
  return (
    <section>
      <h1>Cartelera</h1>
      <div className="mb-6 grid items-end gap-4 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_auto]">
        <Campo
          etiqueta="Buscar por nombre"
          type="search"
          value={q.success ? q.data : ''}
          onChange={(e) => filtro('q', e.target.value)}
        />
        <Selector
          etiqueta="Categoría"
          value={cat.success ? cat.data : ''}
          onChange={(e) => filtro('categoria', e.target.value)}
        >
          <option value="">Todas las categorías</option>
          {CategoriaSchema.options.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </Selector>
        <Selector
          etiqueta="Artista"
          value={nombre}
          onChange={(e) => filtro('artista', e.target.value)}
        >
          <option value="">Todos los artistas</option>
          {artistas.data?.map((a) => (
            <option key={a.id} value={a.stageName}>
              {a.stageName}
            </option>
          ))}
        </Selector>
        <Boton
          className="mb-4"
          variante="secundario"
          onClick={() => setParams({})}
        >
          Limpiar
        </Boton>
      </div>
      {nombre && resuelto.isError && (
        <p role="alert">No se pudo resolver el artista seleccionado.</p>
      )}
      <Consulta consulta={consulta}>
        {(datos) => {
          const filtrados = datos.filter(
            (e) =>
              (!q.success ||
                e.name
                  .toLocaleLowerCase('es')
                  .includes(q.data.toLocaleLowerCase('es'))) &&
              (!cat.success || e.category === cat.data),
          )
          return filtrados.length ? (
            <>
              <p>{filtrados.length} eventos</p>
              <div className="rejilla">
                {filtrados.map((e) => (
                  <TarjetaEvento key={e.eventCode} evento={e} />
                ))}
              </div>
            </>
          ) : (
            <EstadoVacio mensaje="Sin resultados para tu búsqueda" />
          )
        }}
      </Consulta>
    </section>
  )
}
