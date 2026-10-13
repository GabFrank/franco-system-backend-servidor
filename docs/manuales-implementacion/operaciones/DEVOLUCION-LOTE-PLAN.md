# Devoluciones: elegir el lote y descontarlo del stock por lote — PLAN

Ramas: central `feature/devolucion-lote` (apilada sobre `fix/devolucion-con-proveedor-exige-proveedor`),
desktop `feature/devolucion-lote`, mobile-pwa `feature/devolucion-elegir-proveedor` (misma pantalla;
se acumula ahí). Un PR por repo. **Central primero.**

## Qué pasa hoy

- `devolucion_item.lote` es texto libre. Nadie lo valida contra el maestro `operaciones.lote`.
- Al separar, `DevolucionService.bajarStock` graba el movimiento agregado y **no escribe
  `movimiento_stock_lote`**. Compra, venta, transferencia, ajuste e inventario sí. Para un producto
  con `producto.lote = true`, cada devolución deja el saldo por lote más alto que la existencia
  real: stock que FEFO sigue ofreciendo y ya no está.
- El reingreso de un canje tampoco entra a ningún lote.

## Decisiones (Franco, 2026-10-13)

1. Se elige un lote existente —como en el conteo de inventario— y la baja **se descuenta de ese lote**.
2. En el canje, para productos con lote, **se pide el número del lote nuevo** y el reingreso entra ahí.

## Reglas

Aplican solo a productos con `producto.lote = true`. Para el resto no cambia nada.

1. **El ítem puede llevar un lote del maestro** (`devolucion_item.lote_id`). Se valida que exista y
   sea de ese producto. Al guardarlo, el central copia su número a `lote` (el texto que ya muestran
   pantallas y etiquetas) y su vencimiento a `vencimiento` si el ítem no traía uno. Solo en
   `PENDIENTE`, como el resto del ítem.
2. **Separar desglosa la baja por lote** (`DevolucionLoteService.desglosarBaja`). Con lote elegido,
   **la baja va entera a ese lote**: no se recorta al saldo ni se descarta si el lote está bloqueado
   o en cuarentena — quien devuelve tiene ese lote en la mano, y un lote en recall es justamente lo
   que se devuelve. Por eso **no** se usa `asignarConPreferencia`, que haría las dos cosas y
   descontaría de otro lote en silencio. Sin lote elegido se reparte por FEFO (`LoteFefoService.asignar`,
   solo lotes liberados); si el saldo por lote no cubre la cantidad se desglosa lo que hay y no se
   bloquea, como en transferencias: el resto queda sin trazar y el agregado sigue siendo la verdad.
3. **Revertir el separado o cancelar devuelve a los mismos lotes.** El ítem guarda el id del
   movimiento de la baja (`movimiento_baja_id`); el reingreso espeja el desglose de ese movimiento y
   limpia el id. No se busca «la última baja» por tipo y referencia: la baja, su reversión, el canje
   y la reversión del canje comparten los dos, y tras un canje revertido el negativo más reciente es
   el de esa reversión.
4. **El canje pide el lote del reingreso** (`devolucion_item.lote_reingreso`, número normalizado como
   el maestro). Con reingreso mayor a cero y sin número, el canje se rechaza antes de mover stock. El
   lote se obtiene o se crea con `LoteService.obtenerOCrear`; si ya existía, sus fechas no se pisan.
   El reingreso se desglosa entero a ese lote y el ítem guarda el movimiento (`movimiento_canje_id`).
5. **Revertir el canje saca del mismo lote**, espejando el desglose de `movimiento_canje_id`. Un canje
   anterior a este cambio no lo tiene: se revierte como hoy, sin tocar el ledger.

Colectar y retirar siguen sin mover stock, ni agregado ni por lote.

## Esquema

`V248.3__devolucion_item_lote.sql` (mayor en todas las ramas remotas al 2026-10-13: `V247.1`):

```sql
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS lote_id BIGINT;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS lote_reingreso VARCHAR(255);
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS movimiento_baja_id BIGINT;
ALTER TABLE operaciones.devolucion_item ADD COLUMN IF NOT EXISTS movimiento_canje_id BIGINT;
-- FK a operaciones.lote(id), creada solo si no existe
CREATE INDEX IF NOT EXISTS idx_devolucion_item_lote ON operaciones.devolucion_item (lote_id);
```

Aditiva, nullable, sin datos. `devolucion_item` **no se replica** (no está en `replication_table` ni
en ninguna publicación; verificado en `bodega3`): **sin espejo en filial**. `movimiento_stock_lote`
sí se replica y ya la escribe el central desde transferencias y ajustes; se usa el mismo servicio.

GraphQL: **solo se agregan dos campos al input** `DevolucionItemInput`: `loteId: ID` y
`loteReingreso: String`. **El tipo `DevolucionItem` no cambia** — ver «Contrato».

## Datos nuevos

| Dato | Lo escribe | Lo lee |
|---|---|---|
| `devolucion_item.lote_id` | `DevolucionItemGraphQL.saveDevolucionItem` en `PENDIENTE`, desde el diálogo de ítem del desktop y el de la PWA | `DevolucionService.bajarStock` (preferencia del desglose) |
| `devolucion_item.lote_reingreso` | `DevolucionItemGraphQL.guardarDatosDeCanje` en `RETIRADO`, desde el diálogo de reingreso del desktop | `DevolucionService.reingresarCanje` y `revertirCanje` |

| `devolucion_item.movimiento_baja_id` | `DevolucionService.bajarStock` | `DevolucionService.reingresarSeparado` (y lo limpia) |
| `devolucion_item.movimiento_canje_id` | `DevolucionService.reingresarCanje` | `DevolucionService.revertirCanje` (y lo limpia) |

La PWA no hace canjes: no escribe `lote_reingreso`.

## Auditoría del plan (paso 5, 2026-10-13)

Dos auditores, sin verse. Coincidieron en los dos bloqueantes.

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A y B | «La última baja activa» es ambigua tras un canje revertido | Reglas 3 y 5 reescritas: el ítem guarda los ids de los movimientos |
| A y B | `asignarConPreferencia` descarta el lote elegido si está bloqueado o sin saldo y descuenta de otro | Regla 2 reescrita: el lote elegido se usa tal cual |
| B | Revertir el canje resolviendo el lote por texto es frágil y podría crear un lote fantasma | Regla 5: se espeja el movimiento, sin resolver por texto |
| B | `obtenerOCrear` devuelve null con número vacío | Se valida con `normalizarNumeroLote` antes de mover stock |
| A | Un cliente viejo que reenvía un ítem borraría `lote_id` o `lote_reingreso` | Se conservan si el texto del lote no cambió / si el campo no viene |
| A | Replicación, ids y contrato | Confirmado: `devolucion_item` no se replica ni existe en la filial; `movimiento_stock_lote` se escribe por el servicio que ya usa la secuencia impar; el saldo por lote no mira el tipo del padre; `V248.3` libre en todas las ramas |
| A | En farmacia el maestro `operaciones.lote` puede no bajar a las filiales | Preexistente; no lo empeora (el saldo usa LEFT JOIN al maestro) |
| B | El flush entre dos ítems del mismo producto no se puede probar con Mockito | Queda para la prueba de runtime |

## Contrato

- Los clientes **no piden campos nuevos en ninguna query**: lo que muestran es `lote` (texto), que el
  central completa. Así un cliente nuevo contra un central viejo sigue leyendo todo.
- Los clientes mandan `loteId` / `loteReingreso` **solo cuando hay valor**. Contra un central viejo,
  lo único que falla es guardar un ítem con lote elegido (campo de input desconocido): productos con
  control de lote, que hoy son muy pocos. El resto de las devoluciones no se entera.
- Al editar un ítem en el desktop, el lote se vuelve a marcar comparando `item.lote` con el número
  de los lotes cargados, sin leer `loteId`.
- Cliente viejo contra central nuevo: no manda lote → la baja de un producto con lote se desglosa
  por FEFO. Es el cambio buscado.

## Fases

1. **central — esquema y alta del ítem.** Migración, entidad, input, `.graphqls`, validación del lote
   del ítem. Tests.
2. **central — stock por lote.** Separar, revertir/cancelar, canje y su reversión. Tests con Mockito
   sobre `DevolucionLoteTest`, cada uno fallando contra el código anterior.
3. **desktop.** Diálogo de ítem: con producto con lote, selector de lotes del producto en la sucursal
   de origen (`LoteService.onBuscarLotesDeProducto`, número · vencimiento · saldo) en lugar del texto
   libre. Diálogo de reingreso: campo «Lote del reingreso», obligatorio si el producto lleva lote.
4. **mobile-pwa.** Diálogo de ítem: con producto con lote, «Elegir lote» abre el
   `BuscadorLoteDialogComponent` del inventario (necesita la sucursal de origen); muestra el elegido
   con «Cambiar». Sin lote elegido se puede guardar igual (FEFO).
5. **Prueba de runtime y documentación** (`DEVOLUCIONES.md`, módulo y bloque 13 del plan de testeo de
   la PWA). Este plan se borra en el PR.

## Rollback

Volver al JAR anterior funciona contra el esquema nuevo (columnas nullable que el código viejo no
conoce). Las filas de `movimiento_stock_lote` ya escritas quedan y son correctas. Las devoluciones
separadas con desglose y revertidas con el JAR viejo **no** devolverían al lote: quedaría el saldo
por lote por debajo del real, corregible con el ajuste de stock por lote.

## Sin verificar

- Dry-run de la migración contra una copia de farmacia (la red que usa lotes). Se corre en `bodega3`.
- Productos con lote y devolución colectada: el canje reingresa en el depósito, a ese lote, en esa
  sucursal.
- Etiquetas y remitos: siguen leyendo `lote` (texto); no se tocan.
