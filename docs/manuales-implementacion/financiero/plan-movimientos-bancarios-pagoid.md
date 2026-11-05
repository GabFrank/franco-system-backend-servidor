# Plan — `pagoId` de movimientos bancarios en la misma consulta

Rama `fix/caja-mayor-consultas-repetidas`, solo central. Sigue a la issue desktop #646 (cerrada): mismo patrón
que el #568 (`esPagoConsolidado`), ahora para la tabla de movimientos de una cuenta bancaria en el panel de caja mayor.
La doble recarga del panel en el desktop se evaluó y se descartó en la auditoría (el arreglo correcto cuesta más de
lo que ahorra: 3 pedidos de menos de 1 ms por operación propia).

## Qué pasa

`MovimientoBancarioFieldResolver.pagoId` hace un `findFirstByMovimientoBancarioId` por fila. La página de 15
movimientos bancarios (`movimientosBancarios`) suma hasta 15 consultas aparte. La otra operación que devuelve el tipo,
`ajustarSaldoCuentaBancaria`, devuelve un movimiento recién guardado: cae al fallback.

## Cambio (una fase)

- `MovimientoBancario`: campo `pagoIdLeido` con `@Formula` —
  `coalesce((select d.pago_id from financiero.pago_solicitud_detalle d where d.movimiento_bancario_id = id order by d.pago_id nulls last limit 1), 0)`
  (el `order by` lo hace determinista; el `findFirst` viejo no lo era).
  Sale en el mismo `select`; usa el índice parcial `ix_pago_solicitud_detalle_mov_banco` (V239.1). Sin migración.
  Excluido de `equals`/`hashCode`/`toString` (`@Data`). Javadoc: vale para lecturas frescas; un movimiento cargado
  antes de crear su detalle en la misma sesión trae el valor viejo (hoy ningún flujo lo devuelve así).
- **`0` = sin pago, `null` = no leído.** `pagoId` null ya significa «no pertenece a un pago», así que la fórmula
  no puede usar null para eso: con null el resolver no distinguiría y volvería a consultar en casi todas las filas.
  Los ids de `operaciones.pago` arrancan en 1.
- El resolver: valor de la fórmula si viene (`0` → `null`); si viene `null` (entidad recién guardada, que Hibernate no
  relee) cae al `findFirst` de hoy. Mismo contrato GraphQL (`pagoId: Int`).
- El filial no tiene la entidad. La PWA no pide el campo.

## Tabla de datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `MovimientoBancario.pagoIdLeido` (`@Formula`, no es columna) | nadie: lo calcula PostgreSQL al leer | `MovimientoBancarioFieldResolver.pagoId` |

## Tests

- `MovimientoBancarioFieldResolverTest`: con valor de fórmula (`5` y `0`) no consulta el repositorio y devuelve
  `5` / `null`; con `null` cae al `findFirst`; sin id devuelve `null` sin consultar. Con el resolver viejo el primero
  falla.
- `./mvnw clean verify -B -DskipFlyway=true` (en una copia fuera de `dev-frc`, por jdtls); veredicto `gh pr checks`.
- **Puerta antes del PR (la fórmula falla en tiempo de consulta, no al arrancar: el health check no la ve).** En local,
  por pantalla o API, ejercitar cada carga de la entidad: página de movimientos bancarios, `lockById` (anular un pago
  bancario), cobrar un cheque, `ajustarSaldoCuentaBancaria`. El tag SQL del log tiene que calificar `id` con el alias
  de la entidad, no `d.id` (si no, todo `pagoId` sería null y «Anular» desaparecería sin error).
- Runtime: central local 8081 + desktop. Contar `org.hibernate.SQL` (sin `notificaci`) de una página de movimientos
  bancarios antes y después; SQL generado compara contra el `id` de la entidad; `EXPLAIN` usa el índice.
- Por pantalla: un pago CPP 100 % bancario sigue mostrando «Anular» en su fila de banco (lo decide `pagoId`); un
  movimiento manual no lo confunde con un pago.

## Hallazgos de la auditoría (paso 5) y qué se hizo

- Eje A: la mutation de ajuste también devuelve el tipo → cubierta por el fallback, anotada. Sin cambio de contrato, sin
  filial, PWA ni variables de entorno.
- Eje B: fórmula rota no la ve el health check → puerta de pruebas de escritura arriba. `limit 1` sin orden → `order by`.
  Valor viejo en la misma sesión → Javadoc.

## Qué queda sin verificar

- Tiempos en producción (lectura rechazada): se mide en la copia local de `bodega`.

## Riesgo / rollback

Sin migración ni cambio de contrato: revertir el commit alcanza. Cada carga de `MovimientoBancario` (también la de los
servicios que postean y el `lockById`) suma una subconsulta indexada.
