# Plan — reactivar una venta no revive un cupón ya usado en otra (sigue a filial #195)

Rama `fix/venta-tarjeta-reactivar-cupon-usado`, desde `origin/develop` fd7d2d17. Solo central.
Reemplaza al índice único sobre `qr_crudo` (descartado con Franco el 21/11).

## Problema

`VentaService.cancelarVenta` alterna: cancela o reactiva (solo `VentaGraphQL.cancelarVenta:465`
reactiva; venta crédito y factura legal solo cancelan; el filial no reactiva). Al cancelar, sus
`venta_tarjeta` pasan a CANCELADO para que el cupón quede libre (decisión del 28/09). Al reactivar,
`estadoTarjetaParaVenta` vuelve a COMPLETADO toda fila con datos del cupón, **sin mirar si ese
cupón se volvió a usar** en otra venta mientras la primera estaba cancelada. Resultado: dos cobros
COMPLETADOS con el mismo cupón, y el cambio baja al filial por `central_filialN_pub`.

Después de filial #278/#279 es la vía del **flujo normal** que queda para duplicar un cupón. Queda
también `updateVentaTarjeta` del central (setter de la app móvil vieja, en mantenimiento), fuera de
alcance. Por esta misma reactivación el índice único no se puede: replicada, violaría el índice en
el filial y trabaría el apply worker de la suscripción.

## Arreglo (una fase)

- `VentaTarjetaRepository` (central), dos consultas que reproducen la regla de
  `motivoCuponNoUsable` del filial, **solo contra COMPLETADO**:
  - `existsQrCompletadoEnOtra(sucursalId, qr, id, cancelada)`: misma sucursal, otro id, estado
    COMPLETADO, `trim(qr_crudo) = :qr`, y `NOT EXISTS (SELECT 1 FROM Venta v WHERE v.id = vt.venta.id
    AND v.sucursalId = vt.sucursalId AND v.estado = :cancelada)` (el patrón del filial: el central
    no tiene `ventaId` escalar y ninguna query usa hoy el join por fórmula a `venta.estado`).
  - `completadosPorCodigo(sucursalId, codigo, terminalPosId, desde, id)`: misma sucursal, otro id,
    COMPLETADO, `upper(trim(codigo_autorizacion))`, terminal igual o nula como comodín, `creado_en >=
    desde`. El monto se filtra en Java (igual o desconocido = mismo cupón).
  **Solo COMPLETADO, a diferencia del filial** (que cuenta todo lo no CANCELADO): si contara el
  PENDIENTE que deja este mismo fix con su QR conservado, reactivar después a la venta dueña del
  cupón la bajaría también a PENDIENTE y ninguna de las dos se podría completar (ping-pong).
- `VentaTarjetaService.cuponCompletadoEnOtra(vt)`: QR no vacío → la primera consulta; código no
  vacío → la segunda + monto. Ventana: `horasVentanaDuplicado` leída con `findAllByOrderByIdAsc`
  (24 h si no hay fila o si vale ≤ 0); `findOrCreate()` escribiría la configuración dentro de una
  cancelación.
- `VentaService.estadoTarjetaParaVenta(vt, estadoVenta, BooleanSupplier cuponUsadoEnOtra)`, estática:
  si iba a devolver COMPLETADO y el supplier da true → **PENDIENTE**. El supplier solo se evalúa en
  ese caso (cancelar no consulta nada). La firma de dos argumentos queda como atajo.
- `cancelarVenta` pasa `() -> ventaTarjetaService.cuponCompletadoEnOtra(vt)` y deja un **WARN** por
  cada fila que baja a PENDIENTE (id, venta, sucursal): es el único rastro de cuáles se degradaron.
- Los datos del cupón se **conservan** en la fila PENDIENTE (rastro). El cajero la registra con su
  cupón real desde Conciliación (el filial valida); si no hay otro cupón (era el mismo cobro), la
  deja sin conciliar (NO_COMPLETADO) desde el filial.

## Comportamientos que quedan, y se aceptan

- **Dos cobros de la misma venta con el mismo QR** (el caso Itaipú): al reactivar, el primero vuelve
  a COMPLETADO y el segundo ve al primero (flush en la misma TX) y queda PENDIENTE. Deduplica.
- **PENDIENTE en una caja ya cerrada**: el cierre solo barrió los pendientes de ese momento. Lo
  muestran el contador por caja y la Conciliación; es lo que tiene que pasar.
- **No es atómico entre nodos**: si el filial completa B con el cupón y la fila no llegó todavía al
  central, la reactivación de A no la ve (ventana = lag de réplica). Tampoco entre dos
  reactivaciones simultáneas en el central. Acción manual y rara; un lock en el central no cierra la
  primera.

## Datos nuevos

Ninguno: sin migración, sin cambio de GraphQL.

## Tests

- `VentaServiceTarjetaCancelacionTest` (estática): reactivar con cupón usado → PENDIENTE; sin usar →
  COMPLETADO; cancelar no evalúa el supplier; reactivar una fila sin cupón no lo evalúa. Con el
  código viejo (supplier ignorado) fallan por lógica.
- `VentaTarjetaServiceCuponTest` (mocks del repositorio): QR → consulta por QR; QR vacío no consulta;
  código igual con monto distinto → false; sin monto → true; ventana ≤ 0 → 24 h.
- Las dos consultas JPQL no tienen test que las ejecute en CI (los IT del repo están apagados): las
  ejercita la prueba de runtime contra Postgres. Revertir y comprobar que fallan.

## Compatibilidad, replicación y despliegue

- Mismo contrato y mismas columnas: solo cambia el `estado` que escribe el central al reactivar, un
  valor que ya viaja hoy por `central_filialN_pub`. Sin orden de despliegue.
- Rollback = revertir el commit. Las filas que hayan quedado PENDIENTE siguen así (el código viejo no
  toca un PENDIENTE al reactivar); se ubican por el WARN.
- Central: `Deploy` manual de la instancia (el merge a `develop` no despliega).

## Prueba de runtime

Central propio (perfil `dev`, 8081, `bodega@5551` local, replicación apagada); jar de `develop` como
control:
1. Por SQL en la copia local: venta A CANCELADA con su `venta_tarjeta` CANCELADO y QR Q; venta B
   CONCLUIDA con `venta_tarjeta` COMPLETADO y el mismo Q.
2. `cancelarVenta(A)` (reactiva): fix → la fila de A PENDIENTE y el WARN; control → COMPLETADO.
3. B CANCELADA (su venta): la fila de B no cuenta → A vuelve a COMPLETADO.
4. Código: B con el mismo código y monto en la ventana → PENDIENTE; otro monto → COMPLETADO; B
   creada fuera de la ventana → COMPLETADO.
5. Dos cobros de A con el mismo Q → uno COMPLETADO y otro PENDIENTE.
6. Por pantalla: reactivar A desde la lista de ventas del desktop contra el central propio.
7. Restaurar los datos.

## Resultado (21/11)

- `./mvnw clean verify -DskipFlyway=true`: 3918 tests sin fallas. Con el código viejo (la decisión
  ignora el supplier y el servicio devuelve false) fallan los 4 que exigen detectar el cupón usado.
- Runtime: central propio en el 8081 contra `bodega@5551` local (perfil `dev`, réplica apagada),
  cobros sembrados con ids impares (el central rechaza los pares, que son del filial), ventas 79276,
  79277, 79279 y 79282 de la 24 canceladas y reactivadas con la mutation real `cancelarVenta`:

  | Caso | `develop` | con el fix |
  |---|---|---|
  | QR ya COMPLETADO en otra venta | COMPLETADO | PENDIENTE + WARN |
  | La otra venta cancelada | COMPLETADO | COMPLETADO |
  | Reactivar después la venta dueña del cupón | COMPLETADO | PENDIENTE (sin ping-pong) |
  | Mismo código y monto | COMPLETADO | PENDIENTE |
  | Mismo código, otro monto / fuera de la ventana | COMPLETADO | COMPLETADO |
  | Cobro sin terminal (parámetro nulo) | COMPLETADO | PENDIENTE, sin error de SQL |
  | Dos cobros de la misma venta con el mismo QR | COMPLETADO y COMPLETADO | COMPLETADO y PENDIENTE |

- Por pantalla (desktop de `develop` contra el central propio): Operaciones → Ventas, la 79279 →
  Cancelar → «Venta cancelada con éxito»; otra venta usa el cupón; → Habilitar → «Venta reactivada
  con éxito»; en base la venta CONCLUIDA y su cobro PENDIENTE, con el WARN en el log.
- Al arrancar, el central aplicó a la `bodega` local la V288.3 de `develop` (ajena a este fix).
- Hibernate avisa `HHH000174` (plantilla de `trim`) una vez por consulta nueva; las consultas
  funcionan (lo prueba el runtime).

## Auditoría del diff (paso 8)

Ejes fijos 1, 2 y 3, sin críticos. Verificado: las consultas filtran por sucursal; el único `new
VentaTarjetaService(` es el test nuevo; sin cambio de GraphQL; el auto-flush hace que la consulta vea
la venta recién reactivada y las hermanas ya procesadas (y lo confirmó el runtime); el WARN sale solo
cuando se degrada. Anotado sin cambio: `trim()` de PostgreSQL saca solo espacios, no `\n` (en la
`bodega` local hay 0 QR con espacios en los bordes y el filial ya los guarda recortados desde #278);
el WARN se escribe antes del commit; el PENDIENTE huérfano en caja cerrada (ya escrito arriba).

## Auditoría del plan (paso 5)

Eje A (REVISE) y eje B (REVISE). Aplicado: solo COMPLETADO (ping-pong), consulta con `NOT EXISTS`
sobre `vt.venta.id`, decisión en función estática con supplier perezoso (tests por lógica), WARN por
fila degradada, piso de la ventana, frase de «última vía» corregida, comportamientos aceptados
escritos. Verificado sin hallazgo: solo `VentaGraphQL:465` reactiva; el filial no reactiva; la mutation
devuelve Boolean y ningún cliente asume COMPLETADO tras reactivar.

## Sin verificar

- La réplica del cambio de estado hacia el filial (suscripciones locales apagadas). PENDIENTE ya viaja
  hoy.
