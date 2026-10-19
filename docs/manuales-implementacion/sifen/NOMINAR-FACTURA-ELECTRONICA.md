# Nominar una factura electrónica (issue #433)

Nominar es asignarle en SIFEN un receptor a una factura que se emitió innominada («SIN NOMBRE»).
Se hace desde el desktop, en «Lista de facturas» → ⋮ → «Modificar».

## Reglas

- **Una electrónica no se edita: se nomina.** `updateFacturaLegal` rechaza toda factura con CDC. El
  cliente, el nombre, el RUC y la dirección de una electrónica cambian solo cuando SIFEN aprueba la
  nominación (`SifenEventoService.aplicarReceptorNominado`), así la base no queda distinta de SIFEN.
- **Si la factura salió innominada lo dice el XML enviado**, no el cliente actual:
  `documento_electronico.xml_firmado` (o `xml_original`) con `<iTipIDRec>5</iTipIDRec>`. Hasta el
  17/09/2026 las innominadas se emitían con el cliente genérico 0 «SIN NOMBRE»; desde ahí, sin cliente.
  Las dos se pueden nominar.
- **No se nomina dos veces:** lo frena `nominarReceptor` si el DE ya tiene una nominación aprobada.
- **No se nomina a un cliente que va como innominado** (sin persona, «SIN NOMBRE» o sin documento),
  con la misma regla de `SifenReceptorHelper` que se usó al emitir.
- **Rol:** `nominarFacturaElectronica` exige `FACTURACION EMITIR` (o ADMIN), igual que emitir una
  nota de crédito. El desktop avisa antes de llamar.

## Qué devuelve `nominarFacturaElectronica`

| Evento que queda | Respuesta | Desktop |
|---|---|---|
| APROBADO | `true` | «Factura nominada en SIFEN» |
| PENDIENTE (0300, o 0600 sin protocolo) | `false` | «SIFEN recibió la nominación pero todavía no la aprobó» |
| RECHAZADO / ERROR_ENVIO | error con el motivo de SIFEN (hasta 200 caracteres) | «No se nominó la factura» + motivo |

El resolver no es `@Transactional` a propósito: `nominarReceptor` commitea el evento en su propia
transacción, y el error que se lanza después ante un rechazo no borra el evento ni la respuesta de
SIFEN. Los eventos anteriores del mismo CDC se marcan inactivos recién después de la respuesta.

## Lo que SIFEN aprueba más tarde

Un evento PENDIENTE no lo vuelve a consultar ningún scheduler. Se reconcilia cuando alguien consulta
el DE (`consultarDocumentoElectronico`): `SifenService.procesarEventoNominacion` pasa el evento a
APROBADO y aplica el receptor a la factura, también en la rama de un evento que ya existía.

## Datos que escribe al aprobar

`factura_legal`: `cliente_id`, `nombre` (el del receptor), `ruc` (número **sin DV**, como al emitir) y
`direccion` solo si la persona tiene una. `factura_legal` es `BRANCH_TO_MAIN` con
`replicate_central_to_branch_with_filter`: el cambio vuelve a la filial de la factura.

## Probarlo en local

Con SIFEN apagado la nominación no sale. Para recorrer aprobado, pendiente y rechazado sin tocar SIFEN,
se puede reemplazar en local (sin commitear) la respuesta de `Sifen.recepcionEvento` por un XML fijo.
Al terminar: borrar los `evento_nominacion_de` de prueba y devolver la factura a como estaba.
