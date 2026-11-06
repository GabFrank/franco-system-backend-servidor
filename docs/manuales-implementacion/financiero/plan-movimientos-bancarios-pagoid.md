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
  `coalesce((select d.pago_id from financiero.pago_solicitud_detalle d where d.movimiento_bancario_id = id order by d.pago_id limit 1), 0)`
  (el `order by` lo hace determinista; el `findFirst` viejo no lo era. Sin `nulls last`: Hibernate le antepone el
  alias como si fueran columnas y el SQL no compila; un `asc` de PostgreSQL ya deja los nulos al final).
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
- Diff (paso 8, 3 ejes fijos): sin bloqueantes. Autorización igual (`movimientosBancarios` y
  `ajustarSaldoCuentaBancaria` conservan su `seg.*`; `pagoIdLeido` no está en el schema). Sin migración ni espejo.
  Contrato igual en los tres canales del desktop. Propuesta opcional `{alias}.id`: no se aplica, es la forma del
  precedente `esPagoConsolidado` y el SQL generado se verificó.
- Batería: `PrinterOutputStreamTest.elVigiaYElCierreNuncaSeContradicen` falló una vez con la batería entera y pasó 5 de
  5 corrido solo: intermitente y ajeno al cambio. Veredicto: `gh pr checks`.

## Resultados (05/11, central local 8081 + desktop web de `develop`, base `bodega` local)

- La primera versión de la fórmula (`nulls last`) rompía toda carga de la entidad con `SQLGrammarException`: lo
  detectó la prueba por API, no el test con mocks ni el CI. Corregida.
- Página de 15 movimientos bancarios: 5 sentencias SQL, la subconsulta va dentro del `select`. Antes, por código, una
  consulta más por fila (15).
- `pagoId` de los 140 movimientos de la cuenta 1 contra `pago_solicitud_detalle`: 0 diferencias (6 con pago).
- SQL generado: `d.movimiento_bancario_id = movimiento0_.id` (alias de la entidad). `lockById`:
  `... formula9_ from financiero.movimiento_bancario ... for update of movimiento0_`, sin error.
- API: `ajustarSaldoCuentaBancaria` +1 / −1 (movimientos 141 y 142, `pagoId` null por el fallback); `cobrarCheque` del
  cheque 6 (movimiento 143).
- Por pantalla (caja RRHH, fuente cuenta 000-REPORTE): pagar el gasto 130 desde la cuenta → la fila del banco trae
  `pagoId` 36 y su menú abre «Anular pago»; anularlo → «Pago anulado», contra-movimiento, solicitud 130 de vuelta en
  SOLICITADO, pago 36 CANCELADO.
- Test: `conElValorDeLaFormulaNoConsultaElDetalle` falla con el resolver viejo (`expected: <500> but was: <null>`).

## Qué queda sin verificar

- Tiempos en producción (lectura rechazada): se midió en la copia local de `bodega`.
- La cuenta «antes» (15 consultas extra por página) sale del código, no de una corrida del resolver viejo.

## Riesgo / rollback

Sin migración ni cambio de contrato: revertir el commit alcanza. Cada carga de `MovimientoBancario` (también la de los
servicios que postean y el `lockById`) suma una subconsulta indexada.
