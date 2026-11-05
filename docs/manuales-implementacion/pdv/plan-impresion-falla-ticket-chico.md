# Plan — una falla al enviar un ticket chico ya no se informa como impresión exitosa (#534)

Rama `fix/impresion-falla-ticket-chico` (desde `develop`). Pieza: **central**. Sin migración ni cambios de API
(los tipos GraphQL no cambian; cambian valores: `false` o error donde antes volvía `true`).
Sigue al #532 (`SalidaConEnvioEnHilo`). El mismo caso en el filial es GabFrank/franco-system-backend-filial#213, que
queda para otro PR. El PDV imprime contra el filial: hasta el #213, este cambio alcanza a lo que imprime el central.

## Problema

`PrinterOutputStream` (CUPS) y `TcpIpOutputStream` (red) escriben en un pipe que lee otro hilo, el que manda el
trabajo. `close()` no espera a ese hilo. Si el envío falla cuando el ticket ya entró entero en el buffer del pipe
(1024 bytes), ni `write` ni `close` se enteran y el llamador informa la impresión como hecha. Desde el #532 los tickets
grandes sí avisan, porque el `write` queda esperando lugar y recibe el error.

## Medición (29/10, esta máquina)

`job.print` contra una cola CUPS local (`zz_ok`, hacia una impresora falsa), 5 corridas por tamaño:

| Ticket | Tiempo |
|---|---|
| 300 bytes | 80–208 ms |
| 15000 bytes | 57–621 ms (la primera corrida es la lenta) |
| Cola que rechaza | 0–35 ms, `PrintException` |

Es lo máximo que `close()` pasa a esperar con CUPS; parte de ese tiempo ya corre mientras se escribe. Por red, una IP
que no responde puede tardar hasta el timeout de conexión del sistema (~2 min): ahí corta el tiempo límite.

## Cambio (una fase)

En `SalidaConEnvioEnHilo`, la base de las dos salidas:

1. `close()` cierra el pipe y **espera a que termine el hilo de envío**, hasta 10 s.
   - Si el envío falló: `IOException("No se pudo imprimir: <motivo>")`, el mismo mensaje que ya da `write`.
   - Si no terminó en 10 s: `IOException("No se pudo confirmar la impresion en 10 s: revisar la impresora antes de
     reintentar")`. El hilo de envío no se interrumpe: el pipe ya tiene fin de datos y termina cuando CUPS o el
     socket respondan, así que el ticket puede salir igual; de ahí el «revisar antes de reintentar».
   - Si el hilo del request se interrumpe durante la espera: se vuelve a marcar la interrupción y se tira
     `InterruptedIOException`.
   - Si terminó bien: vuelve como hoy.
2. El aviso de fin pasa de un `volatile boolean` a un `CountDownLatch`, que sirve para esperar.
3. Solo el primer `close()` espera y avisa (`AtomicBoolean`); el segundo vuelve enseguida. Casi todos los llamadores
   cierran dos veces (`escpos.close()` y después el stream).
4. La espera es un campo package-private (10 s por defecto) para que los tests usen 300 ms.

Por qué en la base y no solo en `PrinterOutputStream`: la red tiene el mismo hueco y los dos comparten el pipe.

`FacturaLegalGraphQL.printTicket58mmFactura` y `printTicket58mmFacturaMonedaExtranjera`:
- El cierre está en un `try/catch (IOException) { printStackTrace }` propio, mientras que los `write` de arriba ya
  propagan (el método es `throws Exception`). Se saca ese `catch`: la falla al cerrar llega a
  `reimprimirFacturaLegal`, que devuelve `false`, igual que una falla al escribir.
- Hoy, después de `escpos.close()` (que ya cierra la salida local `salida`), se cierra además el **campo**
  `printerOutputStream` del singleton, que con dos reimpresiones simultáneas puede ser la salida de otro request. Ese
  segundo cierre se saca: con la espera nueva, además esperaría sobre la salida ajena.
- Los otros dos llamadores (`FacturaPrintRouterGraphQL`, `TicketEscposGraphQL`) pasan un `ByteArrayOutputStream` como
  destino, que no falla al cerrar.

`VentaGraphQL.reimprimirVenta`: si la venta tiene factura, llama a `reimprimirFacturaLegal`, ignora su resultado y
devuelve `true`. Pasa a tirar `GraphQLException("No se pudo reimprimir la factura")` cuando vuelve `false`, igual que
la rama sin factura, que ya propaga el error. El desktop lo muestra como error sin cambios de su lado.

## Llamadores

Barrido de los 18 lugares que cierran una salida de impresión (todos en el hilo del request, ninguno imprime varios
tickets por request):

| Llamador | Backend con el cambio | Lo que ve el usuario |
|---|---|---|
| `TicketRetiroService`, `EtiquetaDevolucionService` | error | el error |
| `VentaGraphQL.reimprimirVenta` (con y sin factura) | error | el error |
| `VentaGraphQL.saveVenta` | la venta se guarda; la falla queda en el log (a propósito, comentario en el código) | nada, como hoy con una falla al escribir |
| `FacturaLegalGraphQL.reimprimirFacturaLegal` | `false` | desktop: **nada** (antes «guardado con éxito») |
| `VentaCreditoGraphQL.imprimirVentaCredito` | `false` | desktop: nada (solo se suscribe a `error`) |
| `ImpresionService.printBalance` | `false`, pero `PdvCajaService.imprimirBalance` lo ignora y devuelve la caja | PWA: «Balance enviado a imprimir» igual |
| `PrintRouterService.imprimirLocal`, `PrintingService.printTicketCaja58mm`, `MovimientoPrintService.entrada58mm` | `false` | según el cliente |
| `ImpresionService`: `printGasto`, `printVueltoGasto`, `printSolicitudPagoTicket`, `printRetiro`, `imprimirTransferencia`, `imprimirCodigoDeBarra`, `imprimirPedido` | **sin cambio**: su `catch (IOException)` ya se traga cualquier falla, también la de escritura | nada |

Fuera de alcance, previos a este cambio (para issues aparte): el desktop no avisa cuando una impresión devuelve
`false`; `PdvCajaService.imprimirBalance` ignora el resultado; los siete métodos de `ImpresionService` que se tragan la
falla; el campo compartido `printerOutputStream` en los singletons de impresión.

`TicketRetiroService` y `EtiquetaDevolucionService` corren en `@Transactional(readOnly=true)`: la espera retiene la
conexión mientras CUPS toma el trabajo (≤1 s medido), y hasta 10 s solo si la impresora no responde.

## Tests

`PrinterOutputStreamTest`:
- envío que falla tarde con un ticket chico: `close()` tira `IOException` con el motivo (reemplaza a
  `unTicketChicoConElEnvioFalladoNoCuelgaAlCerrar`, que esperaba que `close()` no tirara);
- envío que no termina: `close()` tira al vencer la espera (300 ms en el test); el `job.print` bloqueado se libera
  en un `finally`;
- envío normal: `close()` vuelve sin error y el hilo ya terminó (`isAlive() == false` sin `join`);
- segundo `close()` después de una falla: vuelve enseguida sin tirar.

`TcpIpOutputStreamTest`: ticket chico a un puerto cerrado: `write` + `close` tiran `IOException` «No se pudo
imprimir» (en un solo `assertThrows`, porque según la carrera tira uno u otro).

`VentaGraphQL.reimprimirVenta`: N/A como test unitario porque el resolver arma la venta, el cobro y la factura desde
cinco servicios sin test existente; se cubre por pantalla en el runtime.

Los tests nuevos de la salida se corren contra el código viejo para ver que fallan. Batería completa:
`./mvnw clean verify -B -DskipFlyway=true`.

## Runtime

Central de la rama contra `bodega` local, con Flyway y la replicación apagados, colas `zz_ok` y `zz_rechaza` (se
borran al final):
- `reimprimirFacturaLegal` a cada cola: `true` / `false`, con el tiempo de respuesta;
- `reimprimirVenta` de una venta con factura y de una sin factura a la cola que rechaza: error;
- `imprimirVentaCredito` y un ticket de retiro a cada cola;
- `jstack` al final: sin hilos de envío colgados.

### Resultado (29/10)

Central de la rama en el 8093 contra `bodega` local (Flyway, replicación y schedulers apagados), colas `zz_ok` y
`zz_rechaza` hacia una impresora falsa local. Las colas se borraron al terminar.

| Prueba | `zz_ok` | `zz_rechaza` |
|---|---|---|
| `reimprimirFacturaLegal` 452581 | `true`, 0,35 s | `false`, 0,33 s |
| `reimprimirVenta` 770819 (con factura) | `true`, 0,77 s | error «No se pudo reimprimir la factura» (antes `true`) |
| `reimprimirVenta` 379827 (sin factura) | `true`, 0,30 s | error |
| `imprimirVentaCredito` 29202 | `true`, 0,09 s | error «Error al imprimir» |
| `imprimirTicketRetiroProveedor` [42] (ticket chico) | `true`, 0,07 s | error «No se pudo imprimir: Printer is not accepting job.» las 10 veces |
| Pantalla: Reportes y Análisis → Facturas → Reimprimir 452581 (desktop web contra el 8093) | «Guardado con éxito», trabajo de 16 KB en la cola | sin aviso de éxito; «No se pudo imprimir» en el log |

Clases de salida de `develop` contra las de la rama, ticket de 300 bytes:

| | `develop` | rama |
|---|---|---|
| CUPS `zz_rechaza`, 20 veces | 19 informados como impresos | 20 con error |
| Red a una IP que no responde | informado como impreso en 11 ms | error «No se pudo confirmar…» a los 10,0 s |

`jstack` al final: un solo hilo de envío vivo, de la venta 770812, cuyo armado del ticket reventó antes del `close`
(ver abajo). Ninguno de las pruebas del cambio.

Encontrado en la prueba, previo y fuera de alcance:
- `VentaGraphQL.printTicket58mm` hace `getNombre().substring(0, 22)` del cliente: una venta con un cliente de nombre
  más corto no se puede reimprimir (`StringIndexOutOfBoundsException`), y el hilo de envío queda leyendo el pipe.
- Una falla de escritura en `reimprimirVenta` sin factura le llega al cliente como «null» (la envuelve un proxy).
- `reimprimirVenta` no es alcanzable desde el desktop contra el central: «Últimas ventas» la llama con
  `servidor=false` (filial).

## Riesgo y vuelta atrás

- **Riesgo:** bajo. Toca la base de las dos salidas de impresión, dos bloques de cierre y una línea de
  `reimprimirVenta`; no hay base de datos ni API.
- **Vuelta atrás:** revertir el commit.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| El test del timeout no entra en el límite de 5 s de los tests | B | espera inyectable (punto 4) |
| El test de TCP con ticket chico puede ser inestable | B | un solo `assertThrows` sobre `write` + `close` |
| `FacturaLegalGraphQL` cierra el campo compartido, no la salida local | A y B | se saca ese cierre |
| Interrupción durante la espera | B | `InterruptedIOException` y se vuelve a marcar |
| Doble ticket si se vence la espera y el envío sale después | A y B | mensaje «revisar la impresora antes de reintentar» |
| `reimprimirVenta` con factura ignora el `false` | A | tira `GraphQLException` |
| El desktop no avisa ante `false`; `imprimirBalance` ignora el resultado | A | fuera de alcance, anotado arriba |
| Si el armado del ticket falla antes del `close`, el hilo de envío queda leyendo | B | previo, fuera de alcance |
