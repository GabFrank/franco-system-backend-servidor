# Plan — `esPagoConsolidado` en la misma consulta de movimientos (issue desktop #646)

Parte central del plan del desktop `docs/manuales-implementacion/financiero/plan-caja-mayor-carga-panel.md`
(rama `fix/caja-mayor-carga-panel` en los dos repos).

## Qué pasa

`MovimientoCajaVirtualFieldResolver.esPagoConsolidado` hace un `existsByMovimientoCajaVirtualId` por fila. La página
de 15 movimientos del dashboard de caja mayor genera 26 sentencias SQL, 15 de ellas ese `exists` (medido en local,
base bodega, caja 1).

## Cambio

- `MovimientoCajaVirtual`: campo `esPagoConsolidado` con `@Formula` —
  `exists(select 1 from financiero.pago_solicitud_detalle d where d.movimiento_caja_virtual_id = id)` — que sale en el
  mismo `select`. Solo lectura, sin migración (usa el índice parcial `ix_pago_solicitud_detalle_mov_caja`, V239.1).
  El filial no tiene esta entidad. Excluida de `equals`/`hashCode`/`toString` (`@Data`): una entidad recién guardada
  (`null`) y la misma releída (`true`) no deben diferir.
- El resolver devuelve el valor de la fórmula y solo consulta si viene `null` (entidad recién guardada en una
  mutation, que Hibernate no relee). Mismo contrato GraphQL (`esPagoConsolidado: Boolean`).

## Tabla de datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `MovimientoCajaVirtual.esPagoConsolidado` (`@Formula`, no es columna) | nadie: lo calcula PostgreSQL al leer | `MovimientoCajaVirtualFieldResolver` |

## Tests

- `MovimientoCajaVirtualFieldResolverTest`: con el valor de la fórmula no consulta el repositorio; con `null` cae al
  `exists`. Con el resolver viejo el primero falla.
- `./mvnw clean verify -B -DskipFlyway=true`; veredicto `gh pr checks`.
- Medición antes/después con el mismo script (sentencias SQL por página de movimientos).
- Por pantalla: un pago CPP en la tabla de movimientos sigue abriendo su detalle; un movimiento común no.
- **Bloqueante antes del PR:** el SQL generado en el log de Hibernate compara contra el `id` de la entidad
  (`… = movimientoc0_.id`, no `d.id`; el render de Hibernate 5.6.15 ya lo da así) y `EXPLAIN` local usa
  `ix_pago_solicitud_detalle_mov_caja`.

## Riesgo / rollback

Sin migración ni cambio de contrato: revertir el commit alcanza. La fórmula agrega una subconsulta indexada a cada
carga de `MovimientoCajaVirtual` (incluidos los servicios que la leen para postear), a cambio de no hacer una
consulta aparte por fila al serializarla.
