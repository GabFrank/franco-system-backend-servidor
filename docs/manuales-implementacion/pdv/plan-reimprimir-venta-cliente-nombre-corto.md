# Plan — reimprimir una venta con un cliente de nombre corto (etapa 1 de los pendientes del #534)

Rama `fix/venta-reimprimir-cliente-nombre-corto` (desde `develop`). Pieza: **central**. Sin migración ni cambios de API.

## Problema

`VentaGraphQL.printTicket58mm` escribe `"Cliente: " + nombre.substring(0, 22)`. Si el nombre del cliente tiene menos de
22 caracteres, tira `StringIndexOutOfBoundsException` y la venta no se reimprime (`reimprimirVenta`) ni sale su ticket al
guardarla (`saveVenta`, que lo loguea y sigue). Encontrado en la prueba de runtime del #534: venta 770812 de `bodega`
local, «begin 0, end 22, length 15». Además el hilo de envío queda leyendo el pipe, porque la salida no se cierra (eso
es la etapa 2).

Barrido de `substring(0, N)` sobre nombres en los tickets del central: los otros 15 ya controlan el largo antes
(`ImpresionService`, y el cajero en la misma `printTicket58mm`). Este es el único sin control.

El filial tiene el mismo código en `FacturaLegalGraphQL.generarFacturaAutoImpreso` (línea 768): va en la etapa del
filial, junto con GabFrank/franco-system-backend-filial#213.

Alcance en `bodega` local (29/10): de 5936 clientes, **3454 tienen nombre de menos de 22 caracteres** (sus ventas sin
factura no se reimprimen nunca), 239 de exactamente 22, y 42 tienen la persona sin nombre (darían
`NullPointerException` en la misma línea, aun con el recorte arreglado).

## Cambio (una fase)

Helper estático `VentaGraphQL.lineaCliente(Cliente)`, como el `totalesDeMovimientos` que ya está en la clase:
- sin persona o sin nombre: `null`, y `printTicket58mm` no escribe la línea;
- si no: `"Cliente: "` + el nombre recortado a 22 caracteres solo si es más largo. «Cliente: » + 22 = 31 columnas,
  dentro de las 32 del ticket de 58 mm.

## Tests

`VentaGraphQLLineaClienteTest` (JUnit puro, al lado de `VentaGraphQLTotalesTest`): nombre de 15, de 22 y de 33
caracteres, nombre nulo y persona nula. El de 15 falla con el código viejo. Batería:
`./mvnw clean verify -B -DskipFlyway=true`.

## Runtime

Central de la rama contra `bodega` local, cola CUPS de prueba `zz_ok`, `reimprimirVenta` de ventas sin factura con
cliente de nombre de 15 (770812), 22 (758459) y 33 caracteres (770793): las tres imprimen. Con el código de `develop`,
la 770812 falla (visto en la prueba del #534).

## Fuera de alcance

- El cajero (línea 304) tampoco controla un nombre nulo (sí el largo). No verificado si hay usuarios sin nombre.
- `FacturaLegalGraphQL` (líneas 447 y 818) y `VentaCreditoGraphQL` (381 y 476) imprimen nombres sin recortar: se
  pasan de las 32 columnas pero no fallan.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Persona o nombre nulo siguen rompiendo la línea | A y B | el helper devuelve `null` y la línea no se escribe |
| El test sí es barato: la clase ya tiene un helper estático con test JUnit | B | `lineaCliente` + 5 casos |
| Probar también 22 exactos | B | venta 758459 en el runtime |
| Nombres sin recortar en factura y venta a crédito | A | fuera de alcance (no fallan) |
| No hay otros `substring` sin control en la impresión del central | A | confirmado, los 15 restantes controlan el largo |

## Riesgo y vuelta atrás

- **Riesgo:** bajo. Una línea en un ticket.
- **Vuelta atrás:** revertir el commit.
