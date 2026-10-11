# Cancelación en SIFEN: solo documentos aprobados (issue #408)

Estado: **plan auditado (paso 5) e implementado el 11/10/2026; pendiente de revisión de Franco**. Rama `fix/sifen-cancelar-solo-documento-aprobado`.

## El problema

SIFEN solo acepta el evento de cancelación sobre un documento electrónico aprobado.
`SifenEventoService.cancelarDE(cdc, motivo)` no mira el estado: busca el documento, verifica que no
tenga ya una cancelación aprobada, y manda el evento. Quién filtra hoy, llamador por llamador:

| Llamador | ¿Mira el estado antes? |
|---|---|
| `NotaCreditoGraphQL.cancelarEnSifen` | sí: si no está `APROBADO` no llama (la baja es solo local) |
| `NotaRemisionGraphQL.cancelarEnSifen` | sí, igual |
| `FacturaLegalGraphQL.cancelarFacturaLegal` | sí, desde el PR #407 (`validarEstadoParaCancelar`) |
| `EventosSifenGraphQL.cancelarDocumentoElectronico` | **no** |
| `VentaService.cancelarVenta` (bloque de la factura) | **no** |

Con el documento PENDIENTE, EN_LOTE o RECHAZADO, los dos últimos mandan igual el evento. Según lo
que responda SIFEN queda un `evento_cancelacion_de` RECHAZADO, o PENDIENTE (0300, o 0600 sin
protocolo) sobre un documento que nunca se aprobó, y los eventos activos anteriores del mismo CDC
ya se marcaron inactivos (paso 3 de `cancelarDE`).

## El cambio

Una sola fase, un solo commit.

`cancelarDE`, después de verificar que no hay una cancelación aprobada previa (así ese caso conserva
su mensaje) y **antes de cualquier escritura**: si `de.getEstado() != EstadoDE.APROBADO`, lanza
`IllegalStateException` con el estado actual. `NULL` cuenta como no aprobado, igual que en las notas.

Es seguro ponerlo adentro porque los tres llamadores que ya filtran lo hacen con la misma regla:
ninguno llega hoy a `cancelarDE` con un documento que no esté aprobado.

`IllegalStateException` ya está en el `noRollbackFor` del método y en su `catch` de errores
controlados; no hay nada escrito cuando se lanza.

### Qué ve cada llamador

| Llamador | Antes | Después |
|---|---|---|
| Notas de crédito y de remisión | solo llegan con `APROBADO` | sin cambio |
| `cancelarFacturaLegal` | la guarda propia corta antes con un mensaje claro; con `estado` en `NULL` seguía hasta SIFEN | con `NULL` devuelve `ERROR_SIFEN: …` y no toca la factura ni la venta |
| `cancelarDocumentoElectronico` | mandaba el evento | `RuntimeException("Error al cancelar documento electrónico: …")`, que es como ya reporta cualquier falla. Ningún cliente usa esta mutation (grep vacío en desktop, mobile-pwa y mobile) |
| `VentaService.cancelarVenta` | mandaba el evento; si fallaba lo registraba y seguía | ya no manda el evento ni deja la fila; registra el aviso (con el CDC y el estado, que van en el mensaje de la excepción) y sigue igual que antes |

⚠️ **Lo que este cambio no arregla y deja más a la vista.** Cancelar una venta recién hecha, con el
documento todavía PENDIENTE o EN_LOTE, deja la factura con `activo = false` y la venta cancelada,
mientras el documento sigue su camino y SIFEN lo puede aprobar después. Ese estado final ya existía
(el evento que se mandaba era rechazado o quedaba pendiente para siempre: ningún scheduler
reintenta una cancelación), pero antes quedaba una fila en `evento_cancelacion_de` como rastro y
ahora queda solo el log. El desktop muestra «Cancelado con éxito» en los dos casos.

## Fuera de alcance

- **Qué pasa con la factura al cancelar una venta.** `cancelarVenta` marca la factura con
  `activo = false` siempre, haya aceptado SIFEN el evento o no, y el método alterna (también
  reactiva una venta cancelada, y en ese caso vuelve a ejecutar el bloque de la factura). Este
  cambio no toca eso: es una decisión de negocio pendiente (tercer punto de la issue), y es la que
  cierra el caso del aviso de arriba. Opciones: dejar la factura activa salvo que SIFEN acepte el
  evento o el documento esté RECHAZADO; bloquear la cancelación de la venta, como hacen las notas;
  o dejarlo como está.
- **El control de rol de `cancelarDocumentoElectronico`** va en otro PR
  (`fix/sifen-rol-para-cancelar-documento-electronico`).
- El plazo de 48 h en `cancelarVenta` (solo lo valida `cancelarFacturaLegal`).

## Tests

`SifenEventoServiceCancelarTest`, unitario, corre en CI:

- PENDIENTE, EN_LOTE, RECHAZADO, CANCELADO sin evento aprobado, y `NULL` → `IllegalStateException`,
  sin `save` de ningún evento y sin marcar inactivos los eventos anteriores. **Falla con el código
  de hoy** (hoy sigue de largo hasta el envío).
- Con cancelación aprobada previa se conserva el mensaje de «ya fue cancelado».
- `APROBADO` pasa la validación: llega a consultar los eventos activos (el envío a SIFEN no se
  ejercita en un unitario).

## Datos, migraciones, contrato

Ninguno. Sin migración, sin cambio en `.graphqls`, sin dato nuevo. `evento_cancelacion_de` es
central-only.

## Rollback

Revertir el JAR devuelve el comportamiento anterior. No hay datos que deshacer.

## Auditoría del plan (paso 5)

Dos auditores, sin verse. Ningún bloqueante.

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | Al cancelar una venta con el documento sin aprobar, la factura queda inactiva y ya no queda ni el evento como rastro | Declarado arriba; el mensaje de la excepción lleva CDC y estado para el log. La corrección de fondo es la decisión pendiente |
| A | No hay otros llamadores de `cancelarDE` ni otro envío de cancelación; ningún scheduler reintenta llamando a `cancelarDE` | Confirma el alcance |
| A | Un reintento manual puede quedar bloqueado si el documento pasó a RECHAZADO por una respuesta 0420 | Correcto: SIFEN dijo que el documento no existe |
| A | El `SifenEventoService` de la filial define `cancelarDE` pero no tiene llamadores | Sin cambios en la filial |
| B | No hay escritura antes del punto de la validación; la excepción no deja rollback-only la transacción de `cancelarVenta` (REQUIRES_NEW y se atrapa adentro) | Confirma el diseño. Lo segundo es razonamiento sobre Spring, no está probado |
| B | El unitario no debe llegar a `Sifen.recepcionEvento` | La consulta de eventos activos lanza un centinela que corta antes |
| B | La columna `estado` tiene `DEFAULT 'PENDIENTE'` y los constructores lo fijan: `NULL` no ocurre salvo edición manual | `NULL` se trata como no aprobado. `validarEstadoParaCancelar` no se toca |

## Resultado

- `SifenEventoServiceCancelarTest`: 3 casos, verde. Con la validación revertida falla
  `sinAprobarNoSeMandaNiSeEscribeNada` (sigue de largo hasta la consulta de eventos).
- Batería: `./mvnw clean verify -B -DskipFlyway=true`, 1738 tests, verde.
- Auditoría del diff (paso 8): autorización y contrato, sin cambios obligatorios. El eje de esquema
  es N/A: el diff no toca migraciones, entidades ni consultas. Un detalle cosmético anterior, en el
  desktop: `ERROR_SIFEN: ` se recorta con `substring(11)` y deja un «:» de más.
- Sin prueba de runtime contra SIFEN: la validación corta antes del envío y el camino aprobado no
  cambia.

## Sin verificar

1. **Atraso de réplica**: el estado del documento de una factura lo escribe la filial y llega al
   central por replicación. Una factura recién aprobada puede verse unos segundos como EN_LOTE en el
   central y la cancelación se rechazaría con el mensaje del estado. `cancelarFacturaLegal` ya se
   comporta así desde el #407. No medí cuánto dura esa ventana.
2. **Documentos aprobados en SIFEN que el central tenga en otro estado** de forma permanente. Si
   existen, hoy se pueden cancelar por estos dos caminos y después del cambio no. No lo medí en
   producción.
