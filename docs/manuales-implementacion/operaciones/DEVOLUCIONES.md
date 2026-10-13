# Devoluciones: reglas de stock

Qué mueve stock en una devolución y qué se puede editar en cada estado. La lógica vive en
`DevolucionService`; las reglas de edición, en `DevolucionEdicionValidator`.

## Flujo

- Con proveedor: `PENDIENTE → SEPARADO → [COLECTADO] → RETIRADO → CANJEADO | ACREDITADO`
- Sin proveedor: `PENDIENTE → SEPARADO → DESCARTADO`
- `CANCELADA` desde `PENDIENTE` o `SEPARADO`.

## Qué mueve stock

| Paso | Movimiento | Sucursal |
|---|---|---|
| Separar | baja de lo devuelto (`DEVOLUCION`, o `DESCARTE` si es sin proveedor) | origen |
| Revertir el separado / cancelar desde `SEPARADO` | reingreso de lo mismo, con el mismo tipo en positivo | origen |
| Colectar (enviar a depósito) y revertirlo | ninguno | — |
| Retirar y revertirlo | ninguno | — |
| Canjear | reingreso de lo que repuso el proveedor (`DEVOLUCION` positivo) + un `producto_vencimiento` | donde está la mercadería |
| Revertir el canje | salida de lo que había reingresado + borrado de ese `producto_vencimiento` | donde entró |
| Acreditar, descartar | ninguno (el stock ya salió al separar) | — |

**El canje reingresa donde está la devolución, no en el origen.** Si se colectó a un depósito, la
salida queda en la sucursal de origen y el reingreso entra en el depósito: ahí es donde el
proveedor entrega el reemplazo. Es intencional (decidido el 2026-10-13).

Todos los movimientos llevan `referencia = devolucion_item.id`. Las devoluciones anteriores al
2026-10-13 tienen sus reingresos grabados como `ENTRADA`; no se migraron.

## Stock por lote

Solo para productos con `producto.lote = true`. Cada movimiento de la tabla de arriba lleva además
su desglose en `movimiento_stock_lote`; lo escribe `DevolucionLoteService`.

| Paso | Qué se anota |
|---|---|
| Separar, con lote elegido en el ítem | la baja **entera** a ese lote, aunque esté bloqueado o sin saldo: es el que se tiene en la mano |
| Separar, sin lote elegido | reparto por FEFO entre lotes liberados; si el saldo por lote no alcanza, lo que haya (el resto queda sin trazar) |
| Revertir el separado / cancelar | el espejo del desglose de la baja: vuelve a los mismos lotes |
| Canjear | la entrada entera al lote que entrega el proveedor (`lote_reingreso`), que se crea si no existe |
| Revertir el canje | el espejo del desglose del canje |

- El ítem guarda `lote_id` (lote del maestro; su número se copia a `lote`), `lote_reingreso`,
  `movimiento_baja_id` y `movimiento_canje_id`. Los dos ids existen porque **deshacer espeja un
  movimiento puntual**: buscarlo por tipo y `referencia` es ambiguo, ya que la baja, su reversión,
  el canje y su reversión comparten los dos.
- Canjear un producto con lote **exige** `lote_reingreso` cuando hay reingreso.
- `loteId` y `loteReingreso` existen solo en el input GraphQL. Los clientes los mandan únicamente
  con valor y no piden nada nuevo en sus consultas, así un cliente nuevo sigue andando contra un
  central anterior a `V249.3` (salvo para guardar un ítem con lote elegido).
- Un cliente que no conoce los campos y reenvía un ítem conserva el lote si no cambió el texto.
- Lo separado o canjeado antes de `V249.3` no tiene ids de movimiento: deshacerlo no toca el ledger.

## Qué se puede editar

- **Cabecera e ítems: solo en `PENDIENTE`.** El stock sale al separar a partir de los ítems; si
  después cambian los ítems, el origen o el tipo, cancelar o revertir reingresaría otra cosa.
  `saveDevolucion` con `id` copia solo los campos editables: el estado no sale del input.
- **En `RETIRADO`, de un ítem solo se cargan `cantidadReingresada` y `vencimientoReingreso`.** El
  resto del input se ignora.

## Reglas del canje

- Por ítem, `0 <= cantidadReingresada <= cantidad` (las dos en unidades de la presentación).
- Un canje reingresa algo: si ningún ítem tiene reingreso mayor a cero, se rechaza. Si el
  proveedor no repuso nada, corresponde acreditar.
- Revertir un canje no se calcula desde el ítem: saca lo que dicen los `producto_vencimiento` con
  `tipo_origen = DEVOLUCION_CANJE` y `origen_id` = id del ítem, que guardan la cantidad en unidad
  base y la sucursal. Sirve también para canjes viejos que reingresaron de más.
- Con `permitirStockNegativo` apagado, revertir un canje falla si lo reingresado ya no está: hay
  que regularizar el stock con un ajuste y volver a intentar.
- Para revertir un retiro que tiene líneas canjeadas, primero se revierte el canje de cada una.

## Pendiente

Ninguna mutation del módulo exige rol: el central solo pide sesión iniciada (issue #177) y el
desktop abre el módulo con `VER_TRANSFERENCIA`. Falta decidir qué rol puede separar, cancelar y
revertir —en especial revertir un canje, que saca stock— y aplicarlo con `InventarioSecurityService`.

## Tests

`DevolucionLoteTest`, `DevolucionControlesStockTest`, `DevolucionEdicionValidatorTest`, `DevolucionOperacionesTest`,
`DevolucionTransicionValidatorTest`, `DevolucionRetiroEnBloqueTest`. `DevolucionFlujoIT` corre
contra una base real con `-Dit.devolucion=true`.
