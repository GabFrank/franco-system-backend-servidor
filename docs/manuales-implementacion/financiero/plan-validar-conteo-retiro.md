# Plan — validar el conteo de una verificación de retiro

Rama: `fix/financiero-validar-conteo-retiro` (central, desde `origin/develop`, 2026-11-04).

## Qué resuelve

`RetiroVerificacionService.verificar` acepta cualquier `contado` que le mande el cliente:

- **Negativo:** queda registrado como un faltante mayor que lo declarado. No postea, porque
  `acreditar` saltea montos ≤ 0, pero el caso y el detalle quedan mal.
- **Con más decimales de los que admite la moneda** (por ejemplo, 1.000,5 Gs): se compara exacto
  contra lo declarado, abre un caso por una diferencia de centavos que no existen y acredita una
  fracción de guaraní.

El desktop nuevo (PR #674) ya los impide con la máscara del input. Un cliente viejo o un pedido
armado a mano no pasan por esa máscara.

## Cambio

- En el bucle de detalles de `verificar`, antes de armar cada `RetiroVerificacionDetalle`, se
  valida el contado de esa moneda:
  - `contado < 0` → `GraphQLException`: «El conteo de {moneda} del retiro #N no puede ser negativo.»
  - más decimales que la moneda (`stripTrailingZeros().scale() > decimales`) → `GraphQLException`:
    «El conteo de {moneda} del retiro #N admite hasta {d} decimales.»
- **Ruido de punto flotante (auditoría, ejes A y B):** el contado llega como `Float` y se convierte con
  `new BigDecimal(contado.toString())`. Un desktop anterior suma doubles: la grilla en R$ o «usar
  declarado» pueden mandar 30.299999999999997.
  - Si `|contado − contado.setScale(d, HALF_UP)| < 0,000001`, se toma el redondeado: es ruido, no un
    monto.
  - Se rechaza solo lo que quede por encima de esa tolerancia.
  - Así no se traba a un tesorero con el desktop de stable.
- **Moneda inexistente** (el `monedaId` viene del cliente): se rechaza con «La moneda N no existe»,
  en vez de un NPE.
- Decimales de la moneda: `moneda.decimales`. Si es null, el mismo respaldo que
  `ReporteMovimientosCajaService.monto` y el desktop (guaraní 0, el resto 2).
- Se valida **antes del `save`**: un rechazo no deja verificación, movimiento ni caso. El lock y los
  controles de antes no cambian.
- Se reutiliza la `Moneda` que ya se busca para el detalle (`monedaService.findById`), así que no
  agrega consultas.
- **Solo se redondea el ruido**: por debajo de la tolerancia se normaliza; por encima se rechaza. El
  central compara exacto, y redondear montos reales escondería un cliente que manda mal.

## Tabla de datos nuevos

Ninguno: no hay columnas, campos ni GraphQL nuevos.

## Tests

En `RetiroVerificacionConteoValidoTest`, con el mismo armado de mocks que `RetiroVerificacionRolCajaTest`:

| Caso | Resultado esperado |
|---|---|
| Contado negativo | rechaza, sin `save` ni `TesoreriaService` |
| 1000,5 Gs | rechaza |
| 10,005 R$ | rechaza |
| 10,50 R$ | pasa la validación |
| 1000,000 Gs (ceros de más) | pasa la validación |
| Moneda con `decimales` null | usa el respaldo |
| 30.299999999999997 R$ (lo que produce `mapearConteos` con un Double ruidoso) | pasa y se guarda 30,30 |
| `monedaId` inexistente | rechaza con mensaje claro |

Cada caso que rechaza **falla con el código de hoy**.

## Orden y compatibilidad

- Independiente del PR #563. Toca otro tramo del mismo método, así que no deberían chocar al mergear.
- Clientes:
  - El desktop actual manda Gs enteros, y en R$/US$ puede mandar ruido de float, que la tolerancia
    normaliza.
  - El nuevo (#674) redondea a los decimales de la moneda.
  - Ningún cliente legítimo manda negativos.
  - La PWA no llama `verificarRetiro`.
- Sin migración y sin cambios de esquema. Requiere el restart del central que hace el workflow Deploy.

## Datos

Copia local de bodega (2026-11-04): 79.000 `retiro_detalle` entre Gs, R$ y US$, **ninguno** con
decimales de más ni negativo. El ruido solo puede venir de las sumas del cliente, que la tolerancia
cubre.

## Auditoría del plan (2026-11-04)

Dos auditores, los dos con veredicto REVISE, y coincidieron en el ruido de float. Se verificó contra
`RetiroVerificacionGraphQL.mapearConteos` y contra las sumas del desktop de develop. Se incorporaron
la tolerancia y el rechazo de una moneda inexistente.

## Qué queda sin verificar

- Que ningún dato histórico dependa de esto: la regla se aplica solo a verificaciones nuevas.
