# Solicitud de pago — estados + pago con cheques del plan

_Financiero / CPP · rama `feat/modulo-financiero` · 2026-08-06._

Cierra el ciclo **borrador → validada → pagada** de una solicitud de pago, y el pago
automático de sus cheques planificados.

## 1. Estados (`operaciones.solicitud_pago_estado`)

`PENDIENTE → SOLICITADO → PARCIAL → CONCLUIDO` (+ `CANCELADO`).

| Estado | Significado | Editable | Pagable |
|---|---|---|---|
| **PENDIENTE** | Borrador. Creada, aún no validada. | sí | **no** |
| **SOLICITADO** | Validada, lista para pagar. Es lo que ven los diálogos de pago. | no (read-only, con "Reabrir") | sí |
| **PARCIAL** | Pago parcial hecho. | no | sí (resto) |
| **CONCLUIDO** | Pagada. (se muestra "Pagada") | no | no |
| **CANCELADO** | Cancelada. | no | no |

**Gotcha crítico:** agregar un valor al enum `SolicitudPagoEstado` requiere **3 lugares** o falla:
1. enum Java `domain/operaciones/enums/SolicitudPagoEstado.java`
2. enum del schema GraphQL `resources/graphql/operaciones/solicitud-pago.graphqls` (¡archivo aparte!)
3. enum PostgreSQL vía migración `ALTER TYPE operaciones.solicitud_pago_estado ADD VALUE ...` (migración `V194.5`)

Y la validación de transiciones `SolicitudPagoService.isValidStateTransition` debe permitir la nueva transición.

## 2. Transiciones

- **Solicitar** (`PENDIENTE → SOLICITADO`): botón en el diálogo, habilitado con **≥1 nota** (no exige que las formas cubran el total). Vía `actualizarEstadoSolicitudPago(id, SOLICITADO)`.
- **Reabrir** (`SOLICITADO → PENDIENTE`): botón en el diálogo (read-only), vuelve a borrador editable in-place.
- **Pago** (`SOLICITADO/PARCIAL → PARCIAL/CONCLUIDO`): NO usa `actualizarEstado`; `PagoProveedorService` setea el estado directo al registrar el `Pago`.
- **Anular pago**: revierte a **SOLICITADO** (no a PENDIENTE — ya estaba validada).

`PagoProveedorService.listarPendientes` filtra `findByEstadoIn([SOLICITADO, PARCIAL])` — PENDIENTE (borrador) **no** aparece en el pago.

## 3. Diálogo de solicitud (`create-edit-solicitud-pago-dialog`)

Reformulado al patrón del sistema (denso antes): cabecera compacta (proveedor + observaciones) + **strip de balance** (A pagar · Cubierto · Diferencia) + **tabs** Notas / Formas de pago con la tabla que scrollea y ocupa el alto. Acciones: Cancelar/Cerrar · Reabrir (si SOLICITADO) · Guardar (si PENDIENTE) · Solicitar (si PENDIENTE + ≥1 nota).

**Adicionar forma de pago — cheque en cuotas:** al elegir CHEQUE aparecen **Cuotas (1–12)** + **Intervalo (7/15/30/45 días)**. Con N cuotas se genera **una forma de pago por cheque** (valor dividido, última cuota absorbe redondeo; fecha encadenada por intervalo). El diálogo devuelve un **array** de detalles; el padre los agrega/persiste.

## 4. Pago con cheques del plan (`pagar-compras-dialog`)

Al seleccionar una solicitud SOLICITADA cuyo plan (`SolicitudPagoDetalle` con forma CHEQUE) tiene N cheques, aparece un **panel de plan**:

1. Muestra los N cheques (montos + fechas ya definidos en el plan).
2. **Se pregunta la chequera una sola vez** (dropdown de chequeras activas, en la moneda de la deuda, con hojas ≥ N). **El banco sale de la chequera** (no se pregunta aparte).
3. **"Generar N cheques"** → crea las líneas de pago CHEQUE: monto+fecha del plan, **números consecutivos** desde la chequera, diferido/nominal del plan, beneficiario = proveedor.
4. Revisás (editables) → Confirmar → emite los N cheques.

**Fondos NO bloquean:** los cheques diferidos solo **reservan** saldo (`ChequeGestionService.emitir` no valida saldo en diferido; el front valida por **hojas**, no por saldo). El disponible proyectado puede quedar negativo — es esperado, como máximo un aviso.

**Gotcha de fecha:** parsear la fecha del plan con `stringToLocalDate(pc.fechaPago)`, NO `new Date('yyyy-MM-dd')` (medianoche UTC → corrimiento de un día hacia atrás en UTC-3).

Verificado en DB: solicitud → CONCLUIDO, N cheques DIFERIDO con números consecutivos, `saldo_reservado` += total, un `PagoSolicitudDetalle` (fuente CHEQUE) por cheque.

## 5. Campo `firmantes` en chequera

Texto libre en `financiero.chequera.firmantes` (migración `V193.5`) — personas autorizadas a firmar. Editable en el diálogo de chequera. `saveChequera` preserva `creado_en/fecha_retiro/usuario` al actualizar (el ModelMapper los dejaba en null).
