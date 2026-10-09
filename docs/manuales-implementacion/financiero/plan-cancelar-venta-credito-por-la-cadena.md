# Plan: `cancelarVentaCredito` pasa por la cadena de `cancelarVenta` (issue #339)

Rama: `fix/venta-credito-cancelar-por-la-cadena` (desde `origin/develop` `f70e56ee`). Solo central.

## Problema

La mutation `cancelarVentaCredito(id, sucId)` (`VentaCreditoGraphQL`, ~227) llama a
`VentaCreditoService.cancelarVentaCredito(id, sucId, null)`. Con `venta == null` el servicio alterna
el estado de la venta (CANCELADA <-> CONCLUIDA) con un `ventaService.save(venta)` directo y no pasa
por `VentaService.cancelarVenta`. Queda sin tocar: movimientos de caja, movimientos de stock,
`venta_tarjeta`, delivery, cancelacion del documento electronico (SIFEN) y la factura legal.

## Quien la llama hoy

Nadie. Verificado el 2026-10-09:

- desktop: `list-venta-credito` usa `cancelarVenta` desde `9482c5ff` (2025-08-22, entro en
  `v3.1.0-alpha.1`); esta en `master`, `release/beta` y `develop`. Queda `onCancelarVentaCredito` en
  `venta-credito.service.ts` sin llamadores (codigo muerto, no se toca en este PR).
- filial: no tiene la mutation. `frc-mobile` y `frc-mobile-pwa`: no la llaman.
- Franco confirmo que ya no hay desktops anteriores a la 3.1.0 en uso.

El riesgo que se cierra es el endpoint expuesto: quien tenga CANCELACION DE VENTA y llame al GraphQL
a mano deja una venta cancelada con stock, caja y factura vivos.

## Cambio (una fase)

1. `VentaCreditoGraphQL.cancelarVentaCredito(id, sucId)`:
   - `seg.requireCancelarVenta()` sigue siendo la primera linea;
   - busca la venta credito (`service.findByIdAndSucursalId`); si no existe,
     `GraphQLException("Venta credito no encontrada")`, como hoy;
   - busca su venta (`ventaService.findByIdAndSucursalId(ventaCredito.getVenta().getId(),
     ventaCredito.getSucursalId())`); si la venta credito no tiene venta o la venta no existe,
     `GraphQLException` explicita (hoy es un NPE tapado por un mensaje generico);
   - **si la venta ya esta CANCELADA, `GraphQLException("La venta ya esta cancelada")`: esta
     mutation solo cancela** (ver «Solo cancela»);
   - devuelve `ventaService.cancelarVenta(venta)`. Esa cadena ya sincroniza la venta credito
     (`VentaService` ~320 llama a `cancelarVentaCredito(id, sucId, venta)`).
2. `VentaCreditoService.cancelarVentaCredito(id, sucId, venta)`: se borra la rama `venta == null`.
   El metodo queda solo como sincronizador del estado de la venta credito. La venta se exige con un
   chequeo **antes** del `try` (el `catch` generico de ese metodo taparia un NPE con «No se puedo
   cancelar la venta»).

Sin cambios de schema, de firma GraphQL, de entidades ni de migraciones.

### Solo cancela (cambio respecto del atajo viejo)

El atajo viejo alternaba, pero solo tocaba el estado. Pasando por la cadena, alternar deja de ser
inocuo: en `VentaService.cancelarVenta` el bloque de la factura legal (~325-358) no mira hacia donde
va la venta, asi que **reactivar** tambien manda la cancelacion del DE a SIFEN y deja
`factura_legal.activo = false`; y la venta credito vuelve siempre a ABIERTO aunque estuviera
FINALIZADO. Un doble clic o una doble llamada dejaria una venta CONCLUIDA con la factura anulada.
Los dos auditores del plan coincidieron en esto. Por eso esta mutation rechaza una venta ya
cancelada; la reactivacion queda donde ya estaba, en `cancelarVenta`. Coincide con lo que el
desktop decidio para esta pantalla en #390 («no ofrece reactivar»).

### Lo que se conserva

- Mismo rol (`CANCELACION DE VENTA` o ADMIN, #340). Misma firma.

### Lo que cambia

- La mutation ahora desactiva movimientos de caja y stock, cancela `venta_tarjeta`, delivery, el DE
  en SIFEN e inactiva la factura legal, dentro de la transaccion de `cancelarVenta`.
- Ya no reactiva.

## Riesgos heredados de `cancelarVenta` (no los introduce ni los arregla este PR)

- **SIFEN no se revierte.** `SifenEventoService.cancelarDE` corre en `REQUIRES_NEW`: el evento queda
  enviado aunque despues falle `facturaLegalService.save` y la transaccion externa haga rollback
  (ventana chica: es lo ultimo antes del save). Y si SIFEN falla, el error se traga y la factura
  queda inactiva con el DE vivo.
- **Reactivar no reactiva la factura** ni el DE (motivo de «Solo cancela»).
- Tablas que el central pasa a escribir desde esta mutation: `movimiento_caja`, `movimiento_stock`,
  `venta_tarjeta`, `delivery`, `venta_credito`, `factura_legal`. Es exactamente lo que ya escribe
  `cancelarVenta` desde el desktop; no se verifico la direccion de replicacion tabla por tabla.

## Tests

- `CancelarVentaYFacturaRolTest`:
  - `conRolCancelaLaVentaACredito`: verifica `ventaService.cancelarVenta(venta)` y que el resolver
    **no** llama a `ventaCreditoService.cancelarVentaCredito(..)`.
  - `sinRolNoCancelaPorLaVentaACredito`: ademas `verifyNoInteractions(ventaService)`.
  - nuevos: venta ya CANCELADA, venta credito inexistente, venta credito sin venta y venta
    inexistente -> `GraphQLException` y `verify(ventaService, never()).cancelarVenta(any())`.
- `VentaCreditoServiceCancelarTest` (nuevo, sin Spring: `new VentaCreditoService(ventaService,
  repo)` con mocks): con `venta == null` lanza con su mensaje **y** no guarda la venta ni la venta
  credito; con venta CANCELADA deja CANCELADO, con CONCLUIDA deja ABIERTO.
- Prueba del test: revertir el fix y comprobar que los tests nuevos fallan.
- Bateria: `./mvnw clean verify -B -DskipFlyway=true`.

## Auditoria del plan (paso 5, 2026-10-09)

| Hallazgo | Eje | Que se hizo |
|---|---|---|
| Alternar por la cadena cancela el DE y deja la factura inactiva tambien al reactivar; pierde FINALIZADO | A y B | Aplicado: la mutation solo cancela |
| El `catch` de `VentaCreditoService` tapa la excepcion de «venta requerida» | B | Aplicado: chequeo antes del `try` + assert del mensaje |
| Casos sin test: venta ya cancelada, venta credito sin venta | B | Aplicado |
| La seccion Rollback decia que todo se deshace reactivando | A | Corregido |
| SIFEN en `REQUIRES_NEW` no se revierte con el rollback | A y B | Anotado como heredado. Mover la llamada al final o a `afterCommit` toca `cancelarVenta`, el camino que usa todo el sistema: otro PR |
| `venta.getCobro().getId()` sin null: la mutation fallaria en ventas a credito sin cobro | B | Descartado con datos: 0 de 78.864 ventas a credito sin cobro en la copia local de bodega (datos al 2026-09-07) |
| Direccion de replicacion de las tablas que ahora se escriben | A | Anotado; mismo camino que `cancelarVenta`. Se mira en la prueba de runtime |
| Buscar la venta con la sucursal de la venta credito, no con el argumento | A | Aplicado |
| Probar con una venta sin factura electronica | A y B | Aplicado en «Que queda sin verificar» (en `dev`, `sifen.enabled=false`) |

## Tabla de datos nuevos

N/A: no nace ningun campo, columna ni clave.

## Fuera de alcance (anotado, no se arregla aca)

- Al reactivar una venta por `cancelarVenta`, la venta credito queda siempre ABIERTO: una que estaba
  FINALIZADO (cobrada) pierde ese estado tras cancelar + reactivar. Es del camino que si se usa.
- `VentaService.cancelarVenta` hace `venta.getCobro().getId()` sin mirar null.
- Borrar la mutation del schema y el codigo muerto del desktop.
- Mover la llamada a SIFEN de `cancelarVenta` fuera de la transaccion, y que reactivar no cancele
  la factura.

## Que queda sin verificar

- Si en produccion quedaron ventas a credito canceladas por el atajo antes de 2025-08 (venta
  CANCELADA con movimientos de stock/caja activos o factura activa). Se verifica con una consulta de
  solo lectura sobre el central; este PR no corrige datos.
- Prueba de runtime: en local (central `dev` :8081), llamando la mutation por GraphQL sobre una
  venta a credito de prueba **sin factura electronica** (`factura_legal.cdc` nulo; en `dev`
  ademas `sifen.enabled=false`) y mirando `movimiento_stock.estado`, `movimiento_caja.activo` y
  `venta_credito.estado`. La UI no tiene boton que la dispare.

## Rollback

Revertir el commit: no hay esquema ni estado nuevo, el backend anterior entiende todo lo que deja el
nuevo. Lo que una llamada haya cancelado no lo deshace el rollback del JAR. Caja, stock, tarjeta,
delivery y venta credito se pueden reactivar con `cancelarVenta`; **la factura legal y el DE en
SIFEN no vuelven**.
