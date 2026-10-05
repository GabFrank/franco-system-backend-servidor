# Plan — el lucro usa el costo medio cuando la venta no trae costo

Rama: `fix/pdv-costo-medio-venta` (desde `develop` @ `ca5b1711`). Pieza: **central**, sola.
Acompaña al PR del desktop de la misma rama (el PDV pasa a guardar el costo medio en el ítem).

## Qué resuelve

Los dos reportes de lucro calculan el costo de cada ítem como
`COALESCE(costo_unitario, último ultimo_precio_compra del producto, 0)`. Cuando el ítem no trae
costo, el respaldo es el precio de **una** compra: una promoción a 42,5 Gs (CERVEPAR, 17/09)
valúa todas esas ventas a 42,5. Además un ítem guardado con costo **0** cuenta como mercadería
gratis y el lucro sale igual a la venta.

Bodega desde el 01/09: 325.972 ítems; 4.306 sin costo y 4.506 con costo 0.

## Fase única — commit `fix(reportes): usar el costo medio como respaldo del lucro`

| Consulta | Hoy | Queda |
|---|---|---|
| `ProductoRepository.findLucroPorProducto` (JPQL, l.121) | `COALESCE(vi.precioCosto, cpp.ultimoPrecioCompra, 0)` | `COALESCE(vi.precioCosto, NULLIF(cpp.costoMedio, 0), cpp.ultimoPrecioCompra, 0)` |
| `VentaItemRepository` lucro por funcionario (nativa, l.60 y subconsulta l.74) | `COALESCE(vi.costo_unitario, cpp.ultimo_precio_compra, 0)` | `COALESCE(vi.costo_unitario, NULLIF(cpp.costo_medio, 0), cpp.ultimo_precio_compra, 0)` + `costo_medio` en la subconsulta |

- El costo **0** del ítem se respeta tal cual (ver auditoría: son productos propios, decisión de
  negocio). Solo el ítem **sin** costo cae al respaldo.
- `cpp` sigue siendo la **última** fila del producto (el costo vigente hoy, no el del día de la
  venta). Es preexistente y no se cambia acá.
- Fuera de alcance: `VentaItemService` (evolución de costo de compra y ranking de inflación) usa
  `ultimo_precio_compra` a propósito: mide precios de compra, no costo de lo vendido.

## Datos nuevos

Ninguno: sin migración, sin campo GraphQL, sin cambio de contrato. Solo cambia el valor de
`costoTotal` / `costo_total` (y por arrastre lucro, margen, costo unitario) en
`lucroPorProducto` y en el lucro por funcionario.

## Tests (paso 9)

- `./mvnw clean verify -B -DskipFlyway=true`.
- Test de bug: las consultas viven en `@Query`; no hay test que levante contexto en CI. Se agrega
  un test unitario que lee el texto de las dos anotaciones y exige el orden del respaldo
  (falla con el código viejo). Es un candado de regresión, no prueba de ejecución.
- Prueba de runtime: levantar el central local con perfil `dev` (Hibernate valida el JPQL al
  arrancar) y comparar el reporte de lucro antes y después sobre `bodega@5551`.

### Resultados (2026-10-01)

- `LucroRespaldoCostoMedioTest`: 2/2 con el fix; 2/2 **fallan** con los repositorios de `develop`.
- `./mvnw clean verify -B -DskipFlyway=true`: 1284 tests, 0 fallas.
- Central local con perfil `dev` sobre `bodega@5551`: arranca (el JPQL nuevo pasa la validación
  de Spring Data) y `lucroPorProductoList` / `lucroPorFuncionarioList` (sucursal 24, ago–sep)
  devuelven sin error 16 productos y costo total 1.355.006, igual al cálculo en SQL.
- **No ejercitado en runtime:** el respaldo en sí. Ningún ítem local está sin costo y la base local
  replica (1 suscripción, 2 walsenders), así que no se escribieron datos de prueba. El efecto está
  medido con la fórmula sobre producción (arriba) y fijado por el test.

## Qué queda sin verificar

- El reporte sobre producción solo se verá tras el deploy.
- Ítems con costo grabado mal pero positivo (como los 1.813 de la BRAHMITA a 42,5) no cambian:
  el respaldo solo aplica al ítem sin costo. Corregirlos es la tarea de datos que quedó afuera.

## Auditoría del plan (paso 5) y qué se hizo

| Eje | Hallazgo | Verificación (bodega, solo lectura, 2026-10-01) | Decisión |
|---|---|---|---|
| B | `NULLIF(costo_unitario, 0)` inventaría costo a ítems que quizá no lo tienen | de 4.506 ítems con costo 0 desde 01/09, 4.177 son HIELO CHICO y el resto marca propia DON FRANCO; todos con costo medio vigente | **fuera**: cambiar el lucro histórico de productos propios es decisión de negocio |
| A/B | `costo_medio` en moneda extranjera | 192 últimas filas con moneda ≠ Gs; mediana del costo medio 6.480 → ya están en Gs | sin cambio; consistente con el javadoc de `CostosPorProductoService` |
| B | La subconsulta nativa pierde el index-only scan de `idx_costo_por_producto_producto_id_desc` (V130.3) | `EXPLAIN ANALYZE`: mismos 200.499 buffers, 253 ms → 228 ms | sin índice nuevo |
| A | `reporte-ventas-detallado` (`VentaGraphQL.java:850`) usa `precioCosto` o 0, sin respaldo | leído | preexistente, fuera de alcance; anotado |
| A | Consumidores: `ProductoGraphQL`, `VentaGraphQL`, `ImpresionService.imprimirReporteLucroPorProducto`, desktop `lucro-por-producto` / `lucro-por-funcionario`; PWA no consume; filial no tiene copia | grep | sin cambio de contrato |
| B | El test por texto es frágil y no ejecuta la nativa | patrón existente `FuncionarioRepositoryBusquedaNombreTest`; se asertan fragmentos mínimos y la subconsulta | + prueba de runtime |

Efecto medido sobre septiembre en bodega (todas las sucursales): costo de lo vendido
4.106.229.124 → 4.121.325.124 Gs con la regla que incluía el costo 0; sin esa parte el cambio es
menor.

## Despliegue

Solo central. Sin migración: el rollback es volver al JAR anterior. Orden con el desktop:
indiferente (no comparten contrato nuevo).
