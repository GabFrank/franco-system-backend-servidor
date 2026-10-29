# Plan — la impresión no se cuelga si falla el envío a la impresora (#531)

Rama `fix/impresion-no-cuelga` (desde `develop`). Pieza: **central**. Sin migración ni cambios de API. Es el mismo
arreglo del filial (GabFrank/franco-system-backend-filial#205, PR #206), ya auditado y probado allá.

## Problema

`utilitarios/print/output/PrinterOutputStream` (CUPS) y `TcpIpOutputStream` (red) son un `PipedOutputStream`: el
hilo del request escribe y otro hilo lee y manda a la impresora. Si ese hilo termina **sin leer** (la cola no acepta
trabajos, el socket no conecta), el `write` espera lugar en el pipe para siempre y el request no termina.

Además, `FacturaLegalGraphQL.printTicket58mmFactura` (dos variantes, líneas 376 y 743) reutiliza
`this.printerOutputStream` si no es `null`, y el campo solo vuelve a `null` después de un cierre exitoso: después de
una falla, las facturas siguientes usan el stream muerto hasta reiniciar.

## Cambio (una fase)

1. Clase base nueva `SalidaConEnvioEnHilo`, copiada del filial: el hilo de envío siempre cierra su punta del pipe
   (`finally`) y, si falló, el `write` pendiente sale con `IOException("No se pudo imprimir: <motivo>")`.
   `PrinterOutputStream` y `TcpIpOutputStream` la extienden.
2. `FacturaLegalGraphQL`: un `PrinterOutputStream` nuevo en cada factura.

Diferencia con el filial: el `PrinterOutputStream` del central no tiene `closeYEsperarEnvio`. El test de envío
normal cierra y hace `join` del hilo directamente, en vez de agregar ese método.

## Llamadores

En el central, `new PrinterOutputStream` / `new TcpIpOutputStream` aparece en 19 lugares. Todos escriben en un
try/catch de `Exception` o propagan `IOException`, igual que en el filial. Lo que cambia para ellos es que la falla
llega enseguida en vez de colgar.

## Tests

`PrinterOutputStreamTest` (5) y `TcpIpOutputStreamTest` (2), los mismos del filial. Batería:
`./mvnw clean verify -B -DskipFlyway=true`.

## Auditoría (paso 5)

Sin bloqueantes. Riesgos declarados, todos previos al cambio:
- `printTicket58mmFactura` asigna el stream al **campo** del bean (no a una variable local), igual que
  `ImpresionService`, `PrintingService`, `MovimientoPrintService` y `VentaGraphQL`: dos impresiones simultáneas se
  pisan el campo. Ahora se ve como error con motivo en vez de un cuelgue. Fuera de alcance, igual que en el filial.
- Si el envío falla con el ticket entero ya en el buffer del pipe (≤1024 bytes), `write` y `close` no avisan y la
  impresión se informa como hecha. Es el comportamiento de hoy para tickets chicos; no se cambia.
- Por TCP, una falla a mitad puede dejar un ticket cortado en la térmica y el reintento imprime otro entero.
- `closeYEsperarEnvio` se omitió a propósito: en el central nadie lo llama.

## Runtime (28/10)

Central de la rama en el 8093 contra `bodega` local, con Flyway y la replicación apagados. Colas CUPS `zz_rechaza`
(no acepta trabajos) y `zz_ok` hacia una impresora falsa local, sin reiniciar entre pasos:

| Paso | Resultado |
|---|---|
| `reimprimirFacturaLegal` 452581 → cola que rechaza | `false` en 3,3 s; log «No se pudo imprimir: Printer is not accepting job.» |
| `reimprimirFacturaLegal` 452581 → cola que acepta | `true`, 15538 bytes |
| `imprimirVentaCredito` 29202 → cola que rechaza | error «Error al imprimir» en 0,2 s |
| `imprimirVentaCredito` 29202 → cola que acepta | `true`, 7044 bytes |
| Hilos colgados al final (`jstack`) | 0 |

Build: `./mvnw clean verify -B -DskipFlyway=true` OK.

## Riesgo y vuelta atrás

- **Riesgo:** bajo. Toca las dos salidas de impresión y una línea en dos métodos; no hay base ni API.
- **Vuelta atrás:** revertir el commit.
