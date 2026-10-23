# Plan: confirmar por caja un vale que tesorería tiene para pagar

Rama: `fix/rrhh-confirmar-vale-cierra-solicitud` (central, desde `develop` 147b39c8).

## Problema (bodega, vale #140)

`crearValeParaPago` (tesorería) crea el vale SOLICITADO **con** su obligación de pago
(`SolicitudPago` tipo RRHH). El vale sigue apareciendo en la lista de vales de RRHH con
**Confirmar**, y `ValeService.confirmar` lo paga por caja mayor sin mirar la obligación:

- la `SolicitudPago` queda SOLICITADO para siempre (SP-000062, 200.000) y suma en `agingCpp`;
- el vale queda CONFIRMADO con `solicitudPagoId` → `anular` lo rechaza con «se pagó desde
  tesorería», aunque la plata salió por el egreso directo.

No hay pago doble: `pagarValesMixto` exige vale SOLICITADO y los caminos genéricos rechazan RRHH (#302).

## Decisión (Franco, 23/10)

Rechazar: un vale con obligación de pago se paga solo desde tesorería, como ya decidió #302.

## Fase 1 — central

- `ValeService.confirmar`: si `vale.getSolicitudPagoId() != null` → `GraphQLException`
  «El vale #N tiene su pago pendiente en tesorería: pagalo desde tesorería (Pagar vales)».
  Va después de los guards de especie/estado, antes de `registrarEgresoCaja`.
- `crearConfirmado` no se afecta: el vale nuevo no tiene solicitud.
- Test `ValeServiceConfirmarTest`: (1) con solicitud → rechaza y no toca caja ni guarda;
  (2) sin solicitud → CONFIRMADO y egreso registrado. Revertir el fix y ver fallar (1).

- `ValeService.anular`: hoy rechaza todo vale CONFIRMADO con `solicitudPagoId` («anulá el pago
  desde tesorería»). Pasa a rechazar solo si la solicitud tiene un pago de tesorería vivo
  (`pagoSolicitudDetalleRepository`, detalle no anulado). Si no lo tiene, la plata salió por el
  egreso directo de `confirmar` y se revierte con `revertirEgresoCaja`. Destraba el vale #140 si
  algún día se anula la liquidación #216 (que lo devuelve a CONFIRMADO).
- `ValeGraphQL.saveVale`: si el vale tiene `solicitudPagoId`, conserva el estado previo (mismo
  trato que cuotas/especie). Hoy un `saveVale` con `estado: CONFIRMADO` lo confirma sin caja y deja
  la solicitud abierta: el mismo síntoma por otra puerta.
- Tests `ValeServiceConfirmarTest`: confirmar con/sin solicitud; anular CONFIRMADO con solicitud
  con pago vivo (rechaza) y sin pago (revierte la caja). Revertir el fix y ver fallar.

Sin migración, sin cambio de schema GraphQL, sin datos nuevos.

## Desktop

N/A para desktop: el botón Confirmar sigue visible y el error del backend lo muestra
`GenericCrudService.onSaveCustom`. Esconderlo exigiría exponer `solicitudPagoId` en el tipo
`Vale` del schema + otro PR; se deja fuera por tamaño.

## Dato de producción

SP-000062 cancelada en bodega el 23/10 (UPDATE de una fila con guardas, aprobado por Franco).
Era el único caso en la base.

## Pruebas de runtime

Central local 8081 + desktop `ng:serve`: crear un vale desde tesorería (Crear vale para pagar),
ir a RRHH › Vales › Confirmar → debe rechazar con el mensaje; pagarlo desde tesorería → CONFIRMADO.
Un vale creado desde RRHH se sigue confirmando por caja. Anular el pago de tesorería → el vale
vuelve a SOLICITADO y Confirmar sigue rechazando; tesorería lo puede volver a pagar.

## Verificado en bodega (23/10)

- Vales SOLICITADO con solicitud hoy: 0 (de 1 SOLICITADO). El rechazo no traba nada en curso.
- Vales pagados por caja con solicitud y sin pago de tesorería: 1 (el #140). No era una práctica.

## Auditoría del plan (paso 5)

- Eje A: sin otros llamadores de `confirmarVale` (solo el diálogo del desktop; PWA y mobile no lo
  usan); `crearConfirmado` no lleva solicitud. Hallazgo: `saveVale` cambia el estado sin caja →
  incorporado.
- Eje B: anular la liquidación #216 deja el vale #140 CONFIRMADO con solicitud cancelada y
  `anular` lo rechaza para siempre → incorporado (`anular` mira el pago vivo). El rollback del JAR
  no deja nada raro (sin migración). Menor: el guard de `confirmar` no mira si la solicitud está
  cancelada; un vale SOLICITADO con solicitud cancelada solo nace por SQL y tesorería lo paga igual
  (`asegurarSolicitud` emite una nueva) → se deja así.
