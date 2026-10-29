# Plan — las impresiones del central avisan cuando fallan (etapa 2 de los pendientes del #534)

Rama `fix/impresion-avisar-falla-servicios` (desde `develop`, que ya trae el #544). Pieza: **central**. Sin migración
ni cambios de tipos GraphQL (cambian valores: error donde antes volvía `true` o un objeto).

## Problema

Desde el #544 la salida de impresión avisa cuando el envío falla (`write` y `close` tiran
`IOException("No se pudo imprimir: <motivo>")`). Quedan dos huecos:

1. **Llamadores que se tragan el aviso** y el usuario ve éxito:

| Operación GraphQL | Dónde se pierde | Lo que ve el desktop hoy |
|---|---|---|
| `imprimirSolicitudPagoTicket` (Mutation, `Boolean!`) | `ImpresionService.printSolicitudPagoTicket`: `catch (IOException) { printStackTrace }` | «Ticket enviado a imprimir» |
| `imprimirTransferencia` con `ticket` (Query, `String`) | `ImpresionService.imprimirTransferencia`: `catch` vacío | nada (sin aviso de error) |
| `imprimirPedidoTicket` (Mutation, `Boolean!`) | `ImpresionService.imprimirPedido`: `log.error`, y el resolver devuelve `true` siempre | «Ticket enviado a impresora» |
| `imprimirCodigoBarra` (Query, `Boolean`) | `ImpresionService.imprimirCodigoDeBarra`: `printStackTrace`; el resolver devuelve `true` | sin llamador en el desktop |
| `imprimirBalance` (Query, `PdvCaja`) | `PdvCajaService.imprimirBalance` ignora el `false` de `printBalance` y devuelve la caja | PWA: «Balance enviado a imprimir» |
| `imprimirVentaCredito` (Query, `Boolean`) | el resolver ignora el `false`/`null` de `printTicket58mm` y devuelve `true` | nada |

   En los cuatro de `ImpresionService`, si no encuentra la impresora tampoco avisa: sale sin imprimir y el resolver
   devuelve éxito.

2. **Hilo de envío colgado** si el armado del ticket falla antes del `close` (una excepción cualquiera: la etapa 1
   fue un caso). Nadie cierra la salida y el hilo queda leyendo el pipe para siempre (visto con `jstack` en la prueba
   del #544). Pasa en todos los llamadores: ninguno cierra en un `finally` salvo los tres que usan try-with-resources.
   Cerrar en un `finally` no sirve: el fin de datos haría imprimir medio ticket.

## Cambio

### Fase 1 — la salida cancela un envío abandonado

En `SalidaConEnvioEnHilo`: si pasan **30 s sin actividad** y la salida no se cerró, interrumpe el hilo de envío.

- **Actividad** = creación de la salida, entrada y salida de cada `write` (`System.nanoTime()`). Un `write` en curso
  (bloqueado esperando lugar porque la impresora lee lento) nunca cuenta como abandono. El reloj arranca al crear la
  salida: cubre un armado que falla antes de la primera escritura (logo, datos nulos).
- **Un solo estado** ABIERTA / CERRADA / CANCELADA con compare-and-set: o gana `close()` (y el vigía no hace nada) o
  gana el vigía (y `close()` tira «No se pudo imprimir: el ticket quedo a medio armar»). El vigía solo interrumpe si
  ganó, así que nunca toca un envío que ya recibió el fin de datos.
- El vigía registra el motivo antes de interrumpir, y `registrarFalla` guarda la **primera** falla: el usuario ve
  «el ticket quedo a medio armar», no `InterruptedIOException`.
- Revisión cada 1 s con una tarea de una sola vez que se reprograma, en un hilo daemon compartido; se da de baja en
  `terminarEnvio()`, así una salida terminada no queda referenciada. El
hilo sale de la lectura del pipe con `InterruptedIOException` (verificado en un experimento: 8 ms), el envío falla y
no llega a la impresora el ticket a medias por CUPS (verificado el 29/10 con CUPS real: 640 bytes escritos sin cerrar,
interrupción, `job.print` falla con `PrintException: InterruptedIOException` y la cola no recibe ningún trabajo). Un vigía programado por salida (un solo hilo daemon
compartido), sin cambios en los llamadores.

- Las subclases le pasan su hilo a la base al crearlo (`vigilarEnvio(hilo)`).
- El tiempo es un campo package-private (30 s; los tests usan 300 ms).
- Por red (`TcpIpOutputStream`) lo ya leído del pipe ya salió por el socket: un ticket abandonado puede imprimirse
  cortado. Hoy también, y además queda el hilo colgado.

### Fase 2 — los llamadores avisan

- `ImpresionService.printSolicitudPagoTicket`, `imprimirTransferencia` (rama ticket), `imprimirPedido` (rama ticket),
  `imprimirCodigoDeBarra`:
  - la salida y la impresora pasan de los campos compartidos `printerOutputStream` y `selectedPrintService` a
    variables locales (dos impresiones simultáneas se pisan los campos); también en `printBalance`;
  - el `catch` tira `GraphQLException` con el motivo («No se pudo imprimir: …») en vez de tragarlo;
  - sin impresora: `GraphQLException("No se encontro la impresora <nombre>")`, como `TicketRetiroService`. En
    `printSolicitudPagoTicket` el respaldo `getPrintServiceByName("TICKET58")` tira `IllegalArgumentException`: se
    convierte en el mismo mensaje;
  - las ramas PDF (Jasper) de `imprimirTransferencia` e `imprimirPedido` no cambian.
- `SolicitudPagoGraphQL.imprimirSolicitudPagoTicket` deja pasar la `GraphQLException` tal cual (hoy la envuelve en un
  `RuntimeException` con otro prefijo).
- `PdvCajaService.imprimirBalance`: si `printBalance` no devuelve `true`, `GraphQLException("No se pudo imprimir el
  balance")`.
- `VentaCreditoGraphQL.imprimirVentaCredito`: si `printTicket58mm` no devuelve `true`, el mismo
  `GraphQLException("Error al imprimir")` que ya tira para las excepciones.

`printGasto`, `printVueltoGasto` y `printRetiro` no tienen llamadores en el central: no se tocan.

## Llamadores y clientes

| Operación | Desktop con el cambio | PWA |
|---|---|---|
| `imprimirSolicitudPagoTicket` (mutation) | snackbar «Ups! Algo salió mal: …» y `openAlgoSalioMal('Error al imprimir ticket')` | — |
| `imprimirTransferencia` ticket (query) | snackbar genérico «Ups! Algo salió mal: …» (el aviso propio del servicio es solo para red) | — |
| `imprimirPedidoTicket` (mutation) | snackbar genérico y «Error al imprimir ticket» | — |
| `imprimirBalance` (query) | snackbar genérico | aviso «No se pudo imprimir el balance»; al cerrar caja, «La caja se cerró, pero no se pudo imprimir» y sigue |
| `imprimirVentaCredito` (query) | snackbar genérico | — |

`onCustomQuery` del desktop muestra el error GraphQL en el snackbar genérico y emite `null`; `onCustomMutation` además
llama al `error:` del componente. Con un desktop o una PWA viejos pasa lo mismo: el esquema no cambia.

Fuera de alcance: `printerOutputStream` sigue siendo un campo compartido en los métodos muertos de `ImpresionService`,
en `VentaGraphQL` y en `FacturaLegalGraphQL` (este último ya cierra su salida local desde el #544).

Ninguna corre dentro de un guardado: son acciones de imprimir sueltas, sin `@Transactional` (el balance es una query;
`saveConteo` no imprime). `saveVenta` sigue tragando la falla a propósito: la venta ya está guardada.

`imprimirBalance` con `printerName` vacío y sucursal con IP va al filial por `FilialCajaProxyService`, que ya devuelve
`null` ante un error: no cambia.

## Tests

- Fase 1 (plazo de 300 ms en los tests):
  - escrita a medias y sin cerrar: el hilo de envío termina y `close()` tira «…a medio armar» (falla con el código
    viejo);
  - creada y nunca escrita ni cerrada: igual;
  - un `write` bloqueado más que el plazo (el job lee lento) no se cancela y el ticket llega entero;
  - `close()` contra un plazo vencido, 200 veces con plazo de 1 ms: un envío que terminó bien nunca se informa como
    fallido;
  - lo mismo del abandono con `TcpIpOutputStream`.
- Fase 2: `PdvCajaService.imprimirBalance` con mocks (`printBalance` → `false` ⇒ error) e
  `ImpresionService.imprimirCodigoDeBarra` con un `PrintingService` mock que no encuentra la impresora ⇒ error. El
  resto de los `catch` por runtime (abren CUPS por nombre).
- Batería: `./mvnw clean verify -B -DskipFlyway=true`.

## Runtime

Central de la rama contra `bodega` local, colas `zz_ok` / `zz_rechaza` (se borran al final):
- cada operación de la tabla contra las dos colas y con una impresora inexistente;
- abandono con CUPS real: armar un ticket a medias sin cerrar (programita con la clase de la rama), esperar el plazo,
  ver que el hilo terminó (`jstack`), que **no** entró ningún trabajo a la cola y que no quedó un archivo de spool en
  el directorio temporal;
- por pantalla en el desktop web: solicitud de pago (Imprimir ticket), pedido (Imprimir ticket) y transferencia.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Un `write` bloqueado más de 30 s (impresora lenta) contaría como abandono | B | la actividad incluye el `write` en curso |
| Si el armado falla antes de la primera escritura el reloj no arranca | B | el reloj arranca al crear la salida |
| Carrera vigía / `close()` podría cortar un envío ya completo | B | estado único con compare-and-set |
| El motivo sería «InterruptedIOException» | B | el vigía registra «a medio armar» y gana la primera falla |
| `selectedPrintService` también es compartido | B | pasa a local, también en `printBalance` |
| `SolicitudPagoGraphQL` reenvuelve el error; `TICKET58` tira `IllegalArgumentException` | B | pasa tal cual; se convierte a «No se encontro la impresora» |
| La tabla de clientes prometía avisos propios que no salen | A | corregida: snackbar genérico en las queries |
| Ramas PDF de transferencia y pedido | A | dicho explícito: no cambian |
| Ningún llamador oculto ni `@Transactional` en el camino | A y B | verificado |
| Interrumpir a mitad podría dejar trabajo parcial en CUPS | B | verificado con CUPS real: no entra ningún trabajo; falta mirar el spool en el runtime |

## Riesgo y vuelta atrás

- **Riesgo:** medio-bajo. Operaciones que hoy dicen «impreso» pasan a dar error cuando la impresión falla; es el
  objetivo. El vigía interrumpe solo salidas sin escrituras ni cierre durante 30 s.
- **Vuelta atrás:** revertir los commits. Sin base de datos.
