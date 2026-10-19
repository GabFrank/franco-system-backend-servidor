# Cancelar una venta: la factura sigue a SIFEN (issue #408, tercer punto)

Estado: **plan auditado (paso 5) e implementado el 11/10/2026; pendiente de revisión de Franco**. Rama `fix/ventas-factura-segun-sifen-al-cancelar`.
Decidido con Franco el 11/10/2026: **la venta se cancela igual; la factura queda inactiva solo si
fiscalmente dejó de valer.**

## El problema

`VentaService.cancelarVenta` termina con un bloque que busca la factura de la venta y:

1. si es electrónica, llama a `sifenEventoService.cancelarDE` y **traga cualquier excepción**;
2. marca la factura con `activo = false`, **siempre**.

Consecuencias:

- **Documento PENDIENTE o EN_LOTE** (venta recién hecha): la factura queda inactiva y el documento
  sigue su camino; SIFEN lo aprueba después y nadie lo cancela. Factura vigente en SIFEN, inactiva
  en el sistema.
- **Documento APROBADO y SIFEN rechaza el evento** (p. ej. pasadas las 48 h): igual, factura
  vigente en SIFEN e inactiva acá. Lo que corresponde es una nota de crédito, y el sistema la
  esconde porque la factura figura cancelada.
- **Reactivar una venta** (el método alterna CANCELADA ↔ CONCLUIDA): vuelve a ejecutar el bloque, o
  sea intenta cancelar la factura en SIFEN otra vez. Ya está anotado en `VentaCreditoGraphQL`
  («al reactivar tambien cancela la factura en SIFEN», issue #339).
- **`cancelarFacturaLegal(cancelarVenta = true)`** manda el evento y, aceptado, llama a
  `cancelarVenta`, que lo **manda de nuevo**: el paso 3 de `cancelarDE` inactiva el evento anterior
  y reenvía.

## El cambio

Una fase, un commit de código.

### Regla

Al **cancelar** (la venta queda en `CANCELADA`):

| Factura | Documento electrónico | Qué se hace | Factura queda |
|---|---|---|---|
| Papel (sin CDC) | — | nada en SIFEN | inactiva (igual que hoy) |
| Electrónica | `CANCELADO` | nada | inactiva |
| Electrónica | `RECHAZADO` | nada: nunca valió | inactiva |
| Electrónica | `APROBADO` | `cancelarDE`; si no lanza, SIFEN aceptó el evento | inactiva |
| Electrónica | `APROBADO` | `cancelarDE` lanza (rechazo, plazo, error de envío) | **activa** |
| Electrónica | `PENDIENTE`, `EN_LOTE`, `NULL` | nada | **activa** |
| Electrónica | no está en el central | nada | **activa** |
| Electrónica | cualquiera, y quien llama ya la canceló en SIFEN | nada | inactiva |

«SIFEN aceptó» es que `cancelarDE` vuelve sin excepción (evento aprobado, o registrado y
pendiente), el mismo criterio que ya usa `cancelarFacturaLegal`.

Al **reactivar** (la venta queda en `CONCLUIDA`): **no se toca la factura**. Hoy el resultado es el
mismo en el caso normal (ya estaba inactiva desde la cancelación), pero sin el reintento en SIFEN.
Reactivar la factura no entra: un documento cancelado en SIFEN no revive.

### Código

- `VentaService`
  - `static BajaFactura bajaDeFactura(boolean electronica, EstadoDE estadoDe, boolean yaCanceladaEnSifen)`:
    la tabla de arriba como función pura (`INACTIVAR`, `CANCELAR_EN_SIFEN`, `DEJAR_ACTIVA`), al
    estilo de `estadoTarjetaParaVenta`.
  - `darDeBajaFactura(Venta venta, boolean yaCanceladaEnSifen)`: busca la factura y su documento
    (`DocumentoElectronicoService.findByFacturaLegalId`, dependencia nueva del service), aplica la
    regla y registra en el log qué decidió y por qué.
  - `cancelarVenta(Venta venta)` queda como está para los llamadores y delega en
    `cancelarVenta(venta, false)`; la variante nueva recibe `yaCanceladaEnSifen`.
  - El bloque de la factura corre solo si la venta quedó `CANCELADA`.
- `FacturaLegalGraphQL.cancelarFacturaLegal`, rama electrónica con `cancelarVenta = true`: pasa
  `true`, así el evento se manda una sola vez.
- `FacturaLegalGraphQL.cancelarFacturaLegal`, las dos ramas: **si la venta ya está `CANCELADA` no se
  llama a `cancelarVenta`** (`ventaPorCancelar`). `cancelarVenta` alterna, así que «la factura y la
  venta» sobre una venta ya cancelada la reactivaba (caja y stock de vuelta) después de cancelar la
  factura en SIFEN. Antes casi no ocurría; con la regla nueva es justo el camino normal para
  resolver una factura que quedó activa. Se cancela solo la factura y la respuesta lo dice:
  `EXITO: … (la venta ya estaba cancelada)`.

Sin cambios en `VentaGraphQL.cancelarVenta` ni en `VentaCreditoGraphQL.cancelarVentaCredito`.

No depende del PR #409 (validación dentro de `cancelarDE`): acá solo se llama a `cancelarDE` con
el documento aprobado. Se mergean en cualquier orden.

## Lo que el usuario ve

`cancelarVenta` devuelve `Boolean` y el desktop muestra «Cancelado con éxito». **Eso no cambia**: si
la factura quedó activa, el aviso no lo dice. Se ve en el listado de facturas (sigue APROBADO /
activa) y ahí se puede cancelar o emitir la nota de crédito. Avisarlo en el momento necesita cambiar
el contrato (un resultado con detalle) y el desktop: queda como seguimiento, no entra acá.

## Tests

`VentaServiceFacturaCancelacionTest`, unitario, corre en CI:

- `bajaDeFactura`: las ocho filas de la tabla.
- `darDeBajaFactura` con mocks: PENDIENTE y EN_LOTE no llaman a `cancelarDE` y no guardan la
  factura; APROBADO con `cancelarDE` que lanza no la guarda; APROBADO aceptado la deja inactiva;
  papel la deja inactiva; `yaCanceladaEnSifen` no llama a `cancelarDE` y la deja inactiva.
  **Con el código de hoy fallan** los casos PENDIENTE, EN_LOTE y «`cancelarDE` lanza» (hoy siempre
  inactiva) y el de `yaCanceladaEnSifen` (hoy reenvía).

`CancelarFacturaLegalPlazoTest` / `CancelarVentaYFacturaRolTest`: ajustar los `verify` de
`cancelarVenta` a la firma nueva donde haga falta.

## Datos, migraciones, contrato

Ninguno. Sin migración, sin cambio en `.graphqls`. `factura_legal.activo` lo escribe el central en
este camino como hoy; la tabla sube filial → central y no baja.

## Rollback

Revertir el JAR devuelve el comportamiento anterior. Las facturas que este cambio dejó activas
quedan activas (es su estado real en SIFEN).

## Auditoría del plan (paso 5) y del diff (paso 8)

Sin bloqueantes. El eje de esquema del paso 8 es N/A: el diff no toca migraciones, entidades ni
consultas.

| Paso · eje | Hallazgo | Qué se hizo |
|---|---|---|
| 5 · A y B (los dos) | «La factura y la venta» sobre una venta ya cancelada la reactivaba, y con la regla nueva la factura quedaba activa con el documento cancelado | `ventaPorCancelar` en `cancelarFacturaLegal`, con tests |
| 5 · A | Ningún lector asume «venta cancelada ⇒ factura inactiva»: el resumen fiscal, la nota de crédito, `FacturaSimilarService` y el correo de facturas no miran la venta; los totales de ventas no suman facturas. La nota de crédito sobre una factura con la venta cancelada se puede emitir | Confirma la regla |
| 5 · A | El filial no cancela ventas; el único que escribe `activo = false` por cancelación de venta es el central | Sin espejo en la filial |
| 5 · B | Si falla la lectura del documento, el `catch` de `cancelarVenta` abortaba la venta | La lectura va en su propio `try`: la factura queda activa y la venta sigue |
| 5 · B | La transacción externa no pisa lo que escribe `cancelarDE` (`REQUIRES_NEW`): el documento no queda dirty y `cancelarDE` no toca la factura | Razonado sobre el código, no probado en runtime |
| 5 · B | `cancelarDE` sin excepción no siempre es evento aprobado | Declarado en «Sin verificar» 3 |
| 8 · corrección | La respuesta decía «venta mantiene activa» con la venta cancelada | `notaSobreLaVenta` |
| 8 · corrección | Nada protegía la condición «solo al cancelar» | Tests de `cancelarVenta` con espía: cancelar llama a la baja, reactivar no |
| 8 · contrato | Cajas y cierres no suman facturas; el desktop solo mira el prefijo `EXITO`/`ERROR…`; un desktop viejo no se rompe | Sin cambios |

## Resultado

- `./mvnw clean verify -B -DskipFlyway=true`: 1752 tests, verde.
- `VentaServiceFacturaCancelacionTest` (12) y `CancelarFacturaLegalConVentaTest` (5), corren en CI.
  Con el comportamiento anterior fallan 9 de los 13 primeros; sin la condición «solo al cancelar»
  falla `alReactivarLaVentaNoSeTocaLaFactura`.
- Contexto de Spring: levanta con la dependencia nueva de `VentaService` (corrido un IT manual,
  `FacturaLegalActivoQueriesIT`, contra `bodega` local).

## Sin verificar

1. **El Excel de facturas**: no se revisó si cruza `factura_legal` con el estado de la venta. El
   resto de los lectores sí (ver la auditoría).
2. **Cuántas ventas se cancelan con el documento sin aprobar** en producción. No lo medí.
3. **Evento aceptado como pendiente que SIFEN termina rechazando**: `cancelarDE` vuelve sin
   excepción con el evento PENDIENTE (0300, 0600 sin protocolo) y la factura queda inactiva; si el
   scheduler después lo marca RECHAZADO, nadie reactiva la factura. Es el mismo criterio y el mismo
   hueco que ya tiene `cancelarFacturaLegal`. Cerrarlo es inactivar recién con el evento aprobado,
   desde el scheduler: otro cambio.
4. **Sin prueba de runtime contra SIFEN ni en pantalla.** Probado por unitarios y batería.
