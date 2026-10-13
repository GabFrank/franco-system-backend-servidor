# Devoluciones: controles de stock — PLAN

Rama `fix/devolucion-controles-stock` en **central** y **desktop**, las dos desde `develop`.
Un PR por repo. Sin migraciones, sin cambios de schema GraphQL, sin enums nuevos.

## Origen

Prueba de runtime del 2026-10-13 contra `bodega3` local (central en `:8081`, desktop servido
como web en `:4200`), producto 32, devoluciones #4 a #11. El flujo base cuadra (separar,
revertir, colectar, retirar, descartar, acreditar). Lo que no cuadra:

| # | Hallazgo | Evidencia |
|---|---|---|
| H1 | El canje acepta reingresar más de lo devuelto | dev #5: devolvió 2 cajas x6, reingresó 5 → `-12` en suc 1, `+30` en suc 13 |
| H2 | El canje se confirma sin ningún reingreso | dev #7: `CANJEADO` con `-12` y cero entrada |
| H3 | `saveDevolucionItem` / `deleteDevolucionItem` no miran el estado | dev #6: cantidad 2→5 tras separar, cancelar devolvió `+30`. dev #10: ítem borrado, cancelar no devolvió nada |
| H4 | `saveDevolucion` sobre una existente pisa la entidad entera (estado, origen, ubicación, creadoEn) | dev #11: origen 3→4 tras separar; salió de la 3 y volvió a la 4 |
| H5 | El canje no se puede deshacer | `revertirEstado` no admite `CANJEADO` |
| H6 | La reversión del separado y el reingreso del canje se graban como `ENTRADA` con `referencia = devolucion_item.id` | en el historial parecen una entrada común y el desktop intenta abrir la Entrada con ese id |
| H7 | El vencimiento del canje tipeado invierte día y mes | `05/06/2027` → guarda `06/05/2027`; `31/12/2027` no se acepta |
| H8 | El dashboard muestra guaraníes con tres decimales | «₲ 318.445,504» |

**No es un bug y no se toca** (decidido por Franco el 2026-10-13): con la devolución colectada, la
salida queda en la sucursal de origen y el reingreso del canje entra en el depósito donde está.

## Reglas que quedan en el central

1. **Los ítems solo se editan en `PENDIENTE`.** Alta, edición y borrado de `devolucion_item`
   exigen la devolución en `PENDIENTE`. Única excepción: en `RETIRADO`, sobre un ítem existente,
   se pueden escribir `cantidadReingresada` y `vencimientoReingreso` (los datos del canje) y nada
   más — el resto del input se ignora.
2. **La cabecera solo se edita en `PENDIENTE`.** `saveDevolucion` con `id` carga la fila, exige
   `PENDIENTE` y copia solo los campos editables (tipo, proveedor, sucursal de origen, fecha,
   motivo, observación, caja virtual). Estado, identificador, ubicación, colecta, retiro,
   resolución, gasto, `creadoEn` y `finalizado` no salen del input. Si cambia el origen, la
   ubicación lo sigue.
3. **Tope del reingreso.** Por ítem `0 <= cantidadReingresada <= cantidad`. Se valida al guardar
   el dato del canje y de nuevo al pasar a `CANJEADO`.
4. **Un canje reingresa algo.** Pasar a `CANJEADO` exige al menos un ítem con reingreso mayor a
   cero. Si el proveedor no repuso nada, corresponde acreditar.
5. **El canje se revierte.** `revertirEstado` admite `CANJEADO → RETIRADO`. Lo que se saca del
   stock **no se calcula desde el ítem**: sale de las filas `producto_vencimiento` que dejó ese
   canje (`tipo_origen = DEVOLUCION_CANJE`, `origen_id = devolucion_item.id`), que guardan la
   cantidad en unidad base y la sucursal donde entró. Por cada una: movimiento negativo por esa
   cantidad en esa sucursal y borrado de la fila. Así se revierte exactamente lo que entró, también
   en canjes anteriores a este cambio (reingreso mayor a lo devuelto, o sin reingreso). Respeta
   `permitirStockNegativo`: si está apagado y el stock no alcanza, la reversión falla entera y el
   mensaje pide regularizar el stock con un ajuste. Limpia `resolucion` y `finalizado`. Las
   cantidades reingresadas quedan en el ítem para corregirlas y volver a confirmar.
6. **Los movimientos de una devolución llevan el tipo de la devolución.** La reversión del
   separado usa el mismo tipo que la baja (`DEVOLUCION` o `DESCARTE`) con cantidad positiva; el
   reingreso del canje y su reversión usan `DEVOLUCION`. `referencia` sigue siendo el id del ítem.
   Las filas `ENTRADA` ya grabadas no se tocan.

Con 1 y 2, lo que `cancelar`/`revertir` recalcula desde los ítems coincide con lo que salió, así
que H3 y H4 quedan cerrados sin cambiar cómo se calcula la reversión.

## Fases

### Fase 1 — central: reglas de edición (H3, H4)

- `DevolucionEdicionValidator` (clase pura, como `DevolucionTransicionValidator`): decide si un
  ítem se puede crear/editar/borrar según el estado, y valida el tope del reingreso.
- `DevolucionItemGraphQL.saveDevolucionItem` y `deleteDevolucionItem`: aplican la regla 1.
- `DevolucionGraphQL.saveDevolucion`: aplica la regla 2.
- Tests: `DevolucionEdicionValidatorTest`.

### Fase 2 — central: canje (H1, H2, H5, H6)

- `DevolucionService.reingresarCanje`: valida reglas 3 y 4 antes de mover stock; tipo `DEVOLUCION`.
- `DevolucionService.reingresarSeparado`: tipo de la baja, positivo.
- `DevolucionService.revertirEstado`: caso `CANJEADO`.
- `ProductoVencimientoRepository.findByTipoOrigenAndOrigenId` para ubicar lo que generó el canje.
- Tests en `DevolucionCanjeTest` (Mockito, sin DB): rechaza reingreso mayor a lo devuelto; rechaza
  canje sin reingresos; canje parcial mueve lo reingresado en la ubicación; revertir el canje saca
  lo mismo y vuelve a `RETIRADO`; cancelar un separado reingresa con el tipo de la baja.
- Casos que sumó la auditoría: revertir un canje que reingresó de más saca lo que entró; revertir
  un canje sin reingresos vuelve a `RETIRADO` sin mover stock; con `permitirStockNegativo` apagado
  y stock insuficiente no queda ningún movimiento y sigue `CANJEADO`; la validación del canje lanza
  antes de cualquier `save`. `revertir_estadoNoReversible_lanza` deja de usar `CANJEADO`.
- **Cada test se corre contra el código viejo y tiene que fallar.**

### Fase 3 — desktop (H1, H5, H7, H8)

- Diálogo de reingreso: tope `Validators.max(item.cantidad)` con su mensaje.
- `FechaPyDateAdapter` (`shared/`): `parse` de `dd/MM/yyyy`. Se provee **a nivel de componente** en
  el diálogo de reingreso y en el de ítem de devolución. No se cambia el adapter global.
- `edit-devolucion`: `canRevertir` en `CANJEADO`; al volver a `RETIRADO` se recargan los ítems.
- Dashboard de devoluciones: los importes en guaraníes se redondean antes de formatear. No se
  toca `formatoMonedaPy` (59 usos, algunos con costos unitarios).
- Gate: `npm run check` al final.

### Fase 4 — prueba de runtime y documentación

- Repetir en el browser los casos H1 a H8 contra el central con el fix y dejar el resultado acá.
- Mudar las reglas a `docs/manuales-implementacion/operaciones/DEVOLUCIONES.md` y borrar este plan
  en el PR final.

## Auditoría del plan (paso 5, 2026-10-13)

Dos auditores, sin verse. Sin contradicciones entre ellos.

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| B | Revertir un canje desde `item.cantidadReingresada` no sirve para canjes viejos, y sumar movimientos por `referencia` es ambiguo (la usan el separado y su reversión; `ENTRADA` además la comparte con el módulo de entradas) | Regla 5 reescrita: la fuente es `producto_vencimiento` |
| B | Con `permitirStockNegativo` apagado la reversión puede no poder hacerse | Se documenta en la regla 5 y se cubre con test |
| A y B | La regla 6 cambia el resumen por tipo de movimiento (`findStockPorTipoMovimiento`): lo que caía en `ENTRADA` pasa a netear `DEVOLUCION`/`DESCARTE` | Aceptado: es el objetivo de H6. Anotado en Rollback |
| A y B | En `RETIRADO` el resolver reconstruye el ítem desde el input y pisaría campos en null | Fase 1: se carga la fila y se escriben solo los dos campos; el estado se lee de la devolución de la fila, no del input |
| A y B | El desktop guarda el canje en N mutations sueltas; si una falla quedan ítems a medias en pantalla | Fase 3: ante un error se recargan los ítems del servidor |
| A | Llamadores: ninguno queda rechazado. PWA solo da de alta con ítems y separa; `mobile` legacy no tiene devoluciones | Sin cambios |
| A | Filial y reportes no asumen el signo de `DEVOLUCION`; `producto_vencimiento` es central-only y nadie lo referencia; no hay `CHECK` ni triggers | Cierra el «sin verificar» |
| B | `CANJEADO` conserva su `retiro`: al volver a `RETIRADO` sigue en la misma operación | Correcto; el mensaje de `revertirRetiro` pasa a sugerir revertir el canje |

## Ejecución (2026-10-13)

| Paso | Resultado |
|---|---|
| 7 · central | `d5a2eaa9` (fases 1 y 2 en un commit: tocan el mismo servicio) y `6d22dca1` (hallazgos del paso 8) |
| 7 · desktop | `4ec69229` y `37780908` |
| 7 · test que falla con el código viejo | con el servicio vuelto al comportamiento anterior, 8 de los 11 tests de `DevolucionControlesStockTest` fallan |
| 8 · auditoría del diff | 3 ejes fijos, sin condicionales (no hay migraciones ni maquinaria de release). Aplicados: revertir el canje falla si no se borra el vencimiento; ítem con id inexistente se rechaza; la cabecera no pisa `cajaVirtualId`; el diálogo muestra el tope con un valor precargado. **Sin resolver, decisión de Franco:** ninguna mutation del módulo exige rol (ver `DEVOLUCIONES.md`) |
| 9 · batería central | `clean verify -B -DskipFlyway=true -DforkCount=0`: 1806 tests, 0 fallas (sobre `d5a2eaa9`); los de devolución se repitieron sobre `6d22dca1`: 51 en verde |
| 9 · runtime | contra `bodega3`, central en `:8081` con `d5a2eaa9`. dev #12: editar, agregar y borrar ítems y cambiar el origen tras separar → rechazados; canje sin reingreso → rechazado; reingreso 4 sobre 3 devueltas → rechazado; canje de 1 caja `+6`, revertir `-6`, cancelar `+18` tras separar `-18`: neto 0, todo `DEVOLUCION`. dev #7 (canje viejo sin reingreso): vuelve a `RETIRADO` sin movimiento. dev #5 por pantalla: Revertir en `CANJEADO` sacó los `30` que habían entrado; el diálogo mostró «No puede superar lo devuelto (2)»; `31/12/2027` tipeado se guardó como 2027-12-31; canje de 2 cajas `+12` en el depósito. Dashboard: «₲ 980.273» |
| 9 · sin probar en runtime | los tres ajustes de `6d22dca1` (cubiertos por tests unitarios; el central corría el commit anterior) |
| 10 · desktop | `ng build --configuration production`: exit 0 (sobre `4ec69229`) |
| 11 · documentación | `DEVOLUCIONES.md` |
| 12 · cierre | **Incumplido a propósito:** sin push ni PR hasta que Franco lo pida. Este plan se borra en ese PR |

## Datos nuevos

Ninguno. No hay columnas, claves ni campos GraphQL nuevos.

## Contrato con los clientes

| Cliente | Qué usa | Efecto |
|---|---|---|
| desktop | `saveDevolucion` y los ítems solo en `PENDIENTE`; `saveDevolucionItem` en `RETIRADO` para el canje | ninguno en el flujo normal; gana Revertir en `CANJEADO` |
| mobile-pwa | `saveDevolucion` (alta con ítems) y `avanzarEstadoDevolucion(SEPARADO)` | ninguno |
| desktop viejo contra central nuevo | igual que hoy | el canje con reingreso mayor a lo devuelto o sin reingresos ahora devuelve error del central, que la pantalla ya muestra |
| desktop nuevo contra central viejo | Revertir en `CANJEADO` | el central viejo responde «No se puede revertir…»; sin daño |

Orden de merge: **central primero**, desktop después.

## Rollback

Solo código. Volver al JAR anterior restaura el comportamiento previo. Los movimientos grabados
con tipo `DEVOLUCION` positivo son válidos para la versión anterior (el stock es la suma). Lo
que cambia y no vuelve atrás es el resumen por tipo de movimiento: esas filas siguen contando en
`DEVOLUCION`/`DESCARTE` y no en `ENTRADA`.

## Sin verificar

- Retiro y colecta **en bloque**, histórico y acreditación por retiro: no se probaron en runtime
  el 2026-10-13 y este plan no los modifica.
