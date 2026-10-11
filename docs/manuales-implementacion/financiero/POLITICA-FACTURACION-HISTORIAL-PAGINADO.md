# Historial de la política de facturación paginado

Rama `fix/facturacion-politica-dialogo-scroll-orden` en **central** y **desktop** (mismo nombre).
Sigue al plan de ajustes del diálogo del desktop
(`desktop:docs/manuales-implementacion/financiero/POLITICA-FACTURACION-DIALOGO-AJUSTES.md`, fases 1-3).

## Pedido (Franco, 2026-10-01)

Un paginador en el historial. Decidido: **paginado en el central**, no sobre los 200 que ya trae la
query (que corta los cambios más viejos en silencio).

## Fase 4a — central

| Archivo | Cambio |
|---|---|
| `ConfiguracionFacturacionHistorialRepository` | `Page<…> findBySucursalId(Long, Pageable)` y `Page<…> findBySucursalIsNull(Pageable)`; el `findAll(Pageable)` sale de `JpaRepository` |
| `ConfiguracionFacturacionService` | `historialPage(sucursalId, page, size)`: mismo filtro que `historial` (null = todo, -1 = la global), orden `id DESC` por `Sort`, `size` acotado a 1..100 (default 15), `page` < 0 → 0 |
| `ConfiguracionFacturacionGraphQL` | `historialConfiguracionFacturacionPage(sucursalId, page, size)` con `seg.requireVer()` como primera línea, igual que la query de lista |
| `configuracion-facturacion.graphqls` | `type ConfiguracionFacturacionHistorialPage { getTotalElements getContent }` (el patrón de `MovimientoClientePage`) y la query nueva con `page: Int = 0, size: Int = 15` |

**La query de lista `historialConfiguracionFacturacion` no se toca**: un desktop viejo del mismo
canal la sigue usando.

Tests (`ConfiguracionFacturacionServiceTest`): qué método del repo se llama por cada filtro, con el
`PageRequest` y el `Sort` esperados; acotado de `size` y `page`.

## Fase 4b — desktop

| Archivo | Cambio |
|---|---|
| `graphql/graphql-query.ts` + `graphql/getHistorialConfiguracionFacturacionPage.ts` | la query nueva (un GQL por archivo) |
| `configuracion-facturacion.service.ts` | `onGetHistorialPage(sucursalId, page, size)` |
| diálogo `.ts` / `.html` / `.scss` | `mat-paginator` debajo de la tabla del historial (15 por página, opciones 15/30/50); cambiar el filtro vuelve a la página 0; contador de petición para descartar respuestas viejas |

`MatPaginatorModule` ya llega: el diálogo se declara en `financiero.module.ts`, que importa
`SharedModule` → `MaterialModule`.

## Auditoría del plan (paso 5)

- **Eje A** (contrato): `Page<>` de Spring con `getTotalElements`/`getContent` es el patrón de
  `MovimientoClientePage` (`CobroCreditoGraphQL`); el nombre de la query no choca; ningún test de
  schema valida resolvers; mobile-pwa no usa el historial. Riesgo: el -1 («solo la global») mal
  mapeado daría una página vacía sin error. **Hecho:** el test cubre null, -1 e id real.
- **Eje B** (reversibilidad): sin migración ni replicación, el revert no deja nada. Riesgo: carrera —
  `cargarHistorial` no descarta respuestas viejas, y con paginador es más probable que una respuesta
  lenta pise la página nueva. **Hecho:** contador de petición en el diálogo. Contra un central sin la
  query nueva sale el snackbar genérico más el aviso del diálogo; sin fallback a la query vieja
  (mezclaría dos modelos; alcanza con el orden de PRs).

## Tabla de datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `ConfiguracionFacturacionHistorialPage.getTotalElements` / `getContent` | `ConfiguracionFacturacionService.historialPage` | `ConfiguracionFacturacionDialogComponent.cargarHistorial` (paginador) |

Sin columnas ni migración: lee la tabla que ya existe. Sin filial: `historial` no se publica a
las filiales y la query es solo del central.

## Orden (ciclo §3.2)

PR del central primero; el del desktop después, **en el mismo canal**. Un desktop con la fase 4b
contra un central sin 4a ve el aviso «No se pudo cargar el historial» (la query no existe), no
rompe la configuración.

## Gates

- central: `./mvnw clean verify -B -DskipFlyway=true`
- desktop: `npm run check`

## Sin verificar

- Prueba en pantalla: queda para Franco (no puedo apuntar el desktop al alpha).
