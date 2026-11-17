# Plan — #533: el estado de la impresora deja de ser un campo compartido

Rama: `fix/impresion-stream-por-impresion` (desde `origin/develop` `d20551d5`). Una sola fase.

## Problema (verificado sobre develop, no sobre las líneas de la issue)

Los beans son singleton. Guardan en **campos** lo que es de una sola impresión:

| Archivo | Campos | Métodos que los escriben |
|---|---|---|
| `graphql/financiero/FacturaLegalGraphQL` | `printService`, `printerOutputStream` | `printTicket58mmFactura(…, destino)`, `printTicket58mmFacturaMonedaExtranjera(…, destino)` |
| `graphql/operaciones/VentaGraphQL` | `printerOutputStream` | `printTicket58mm` |
| `service/impresion/ImpresionService` | `selectedPrintService`, `printService`, `printerOutputStream` | `printPdf…` (Jasper, l.181), `printGasto`, `printVueltoGasto`, `printRetiro` |
| `service/utils/PrintingService` | `printerOutputStream`; `printServiceList` (`ArrayList` sin sincronizar) | `printTicketCaja58mm`; `getPrintService`/`setPrintService` |
| `print/operaciones/MovimientoPrintService` | `printService`, `printerOutputStream` | `entrada58mm` |
| `graphql/print/PrintGraphQL` | `printService`, `printerOutputStream` | ninguno (sin uso) |

Con dos impresiones simultáneas por el mismo bean: la segunda pisa el stream (tickets mezclados o
cortados, `close()` del stream ajeno, NPE en Factura por el `= null` del final) y, por el
`PrintService` compartido, **puede imprimir en la impresora del otro pedido**. `printServiceList`
se recorre con `stream()` mientras otro hilo hace `add()` → `ConcurrentModificationException`.

La issue nombra el stream; el `PrintService` compartido y la lista son el mismo bug y están en las
mismas líneas: entran en este fix. El `if (true)` que cita la issue ya no existe en develop.

## Cambio (fase 1, un commit)

1. Borrar los campos `printerOutputStream`, `printService`, `selectedPrintService` de los 6 archivos.
2. En cada método: variable local en el mismo lugar donde hoy se asigna el campo
   (`PrinterOutputStream salida = new PrinterOutputStream(ps)` / `PrintService ps = …`).
   En Factura se borran los `this.printerOutputStream = null;` del final, y el `PrintService` se
   declara `PrintService ps = null;` **antes** del `if (destino == null)` porque se lee fuera de
   ese bloque (`destino != null || ps != null`).
3. `PrintingService.printServiceList` → `CopyOnWriteArrayList` (se lee mucho, se escribe una vez por
   impresora nueva).
4. Sin cambios de firma, GraphQL, schema ni migraciones. Comportamiento con un solo pedido: idéntico.

No se hace (fuera de alcance, se anota): pasar a try-with-resources los métodos que no cierran el
stream si una escritura lanza (fuga ya existente, no la introduce este fix).

## Datos nuevos

Ninguno (sin columnas, claves ni configuración).

## Tests (fase 1)

- `ImpresionConcurrenteTest` (unitario, el bug de verdad): dos `CapturaPrintService` (A y B, helper
  ya existente en tests), `printingService.getPrintService("a"/"b")` mockeado; dos hilos con
  `CountDownLatch` llaman N veces a `ImpresionService.printGasto` a "a" y a "b". Aserto: A recibe
  solo tickets de "a", B solo de "b", completos, sin excepción. Con el código viejo falla.
- `ImpresionSinEstadoCompartidoTest` (reflexión, red anti-regresión de las 6 clases): ningún campo
  (de instancia o `static`) asignable a `PrintService`, `OutputStream` o `EscPos`, y
  `printServiceList` es `CopyOnWriteArrayList`. Revertir el fix → falla.
- Batería: `./mvnw clean verify -B -DskipFlyway=true` (en copia fuera de `dev-frc`, por jdtls).

## Prueba de runtime

Central propio desde el worktree (8081 si está libre; replicación sync/refresh apagada), dos colas
CUPS de captura `zz_533a` / `zz_533b` (socket + `nc`), borradas al final. Se avisa a Franco que se
usan colas de captura y se cierra imprimiendo en `frc-ticket`.

1. **Concurrencia (el bug):** N pares de pedidos simultáneos de reimpresión de factura, uno a cada
   cola. Con develop: tickets cruzados, cortados o error. Con el fix: cada cola recibe solo sus
   tickets, completos.
2. **Por pantalla (desktop web contra el central local):** reimprimir una factura desde el listado
   de facturas, y otro flujo que use `ImpresionService` (retiro o gasto) → sale el ticket entero.
3. Ruta con `destino` (impresión ruteada a otra sucursal, `FacturaPrintRouterGraphQL` /
   `TicketEscposGraphQL`): una vez, debe salir igual que hoy.
4. Rama de error: impresora inexistente → mismo resultado que hoy (sin NPE nuevo).

Para que el solapamiento ocurra: pedidos en paralelo (`curl &`, N alto) y la base de comparación
con un central de develop en otro puerto. Reiniciar el central entre corridas si se recrean las
colas (`printServiceList` cachea el `PrintService`).

## Sin verificar / riesgos

- El método de impresión Jasper de `ImpresionService` (l.181) solo usa `printService` para ver si es null:
  Jasper imprime en la impresora por defecto de la JVM. Defecto previo, fuera de alcance; las colas
  de captura no lo prueban.
- `printServiceList` nunca se purga (una cola recreada devuelve el `PrintService` viejo). Previo.
- `PrintGraphQL` no tiene llamadores: solo se le borran campos muertos.

- `MovimientoPrintService.entrada58mm` y `PrintingService.printTicketCaja58mm` usan nombres fijos
  (`TICKET1`, `bematech`); se prueban creando una cola de captura con ese nombre.
- El filial tiene el mismo patrón: es la issue filial #212, otro PR.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | El test por reflexión solo cubría 3 tipos | Se suma `EscPos` y `static`, por `isAssignableFrom` |
| A | La ruta con `destino` no estaba en la prueba de runtime | Agregada (punto 3) |
| A | Tabla del plan verificada línea por línea; sin cambio de contrato, enum, env ni tablas | — |
| B | El `printService` local de Factura queda fuera de alcance si se declara dentro del `if` | Declararlo `= null` antes (cambio, punto 2) |
| B | El test por reflexión prueba forma, no comportamiento | Se suma test de concurrencia con `CapturaPrintService` |
| B | Sin solapamiento real la prueba con colas no reproduce el bug | Paralelo con N alto + baseline en otro puerto |
| B | El método de impresión Jasper ignora el `PrintService`; la lista cacheada no se purga | Anotado como previo, fuera de alcance |
| B | Rollback: sin migraciones ni schema; revertir el JAR alcanza | — |
