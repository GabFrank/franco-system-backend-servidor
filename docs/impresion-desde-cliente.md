# Impresión desde el cliente (central)

> Desde 2026-09-29. Rama `feat/impresion-central-cliente`. Es la continuación, del lado del
> central, de la impresión del POS desde la PC (filial PR #157, desktop PR #371).

## Para qué

Un desktop en modo **"Imprimir desde esta PC"** (Configuración) imprime en su impresora USB local.
Para las impresiones que van al **central**, el central genera el comprobante en ESC/POS y lo
devuelve en base64; el desktop lo imprime con Electron.

Cubre **solo** dos flujos (lo demás del central sigue imprimiendo por servidor, sin cambios):

| Flujo en el desktop | Tipo | Equivale a |
|---|---|---|
| Lista de facturas → **Reimprimir** | `FACTURA` | `reimprimirFacturaLegal` |
| Editar caja → **Imprimir Cierre** | `BALANCE` | `imprimirBalance` |

"Imprimir ticket/PDF en sucursal" no cambia (usa su propio ruteo por `PrintRouterService`).

## Cómo está hecho

- **`ImpresionService.printBalance(..., OutputStream destino)`**: con `destino == null` hace lo de
  siempre (la firma vieja delega con `null`); con destino escribe ahí sin buscar impresora ni tocar
  el campo compartido `printerOutputStream`. El contenido del ticket no se tocó.
- La factura ya tenía `FacturaLegalGraphQL.printTicket58mmFactura(..., destino)` (la usa
  "Imprimir ticket en sucursal").
- **Consulta nueva** (`graphql/impresion/ticket-escpos.graphqls`, `TicketEscposGraphQL`):

```graphql
ticketEscpos(tipo: TicketEscposTipo!, id: ID!, sucId: ID!, local: String): String   # FACTURA | BALANCE
```

  Devuelve `null` si no se escribió nada y lanza `GraphQLException` si el registro no existe.
  Nunca busca impresora.

## Compatibilidad y despliegue

- Desplegar el central **antes** de usar esos dos botones en una PC con "Imprimir desde esta PC":
  contra un central sin esta versión la consulta falla (aviso "no se pudo imprimir en esta PC") y
  no sale papel. No se rompe nada más.
- En modo servidor el desktop manda exactamente lo mismo que antes.

## Tests

- `ImpresionServiceBalanceDestinoTest`: el balance en memoria es **byte por byte** igual al que le
  llega a la impresora (impresora falsa `CapturaPrintService`), sin buscar impresora; sin
  impresora, el central sigue sin imprimir y el cliente igual recibe el balance.
- `TicketEscposGraphQLTest`: `FACTURA` y `BALANCE` usan los mismos datos y renderers que
  Reimprimir e Imprimir Cierre; errores y vacío.
- `./mvnw test`: 1141 OK. El SDL completo no suma errores respecto de `develop`.
