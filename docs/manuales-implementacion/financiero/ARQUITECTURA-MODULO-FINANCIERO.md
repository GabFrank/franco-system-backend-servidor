# Módulo Financiero / Tesorería — Arquitectura

> Documento de referencia del módulo tal como quedó implementado (F1–F8, backend).
> Complementa `ESTADO-Y-PLAN-MODULO-FINANCIERO.md` (estado/roadmap) y
> `PRUEBA-GUIADA-MODULO-FINANCIERO.md` (test manual). Rama: `feat/modulo-financiero`.

## 1. Modelo mental (cash-only + ledgers separados)

- **Caja mayor (`CajaVirtual`) = solo efectivo.** Saldo por `(caja, moneda)` en tabla
  `caja_virtual_saldo` (fuente de verdad). Las columnas `saldo_gs/rs/ds` quedan como
  **shim** derivado (compat RRHH + UI vieja), sincronizadas por `TesoreriaService`.
- **Cuenta bancaria (`CuentaBancaria`) = ledger de primer nivel, independiente.** Saldo
  propio (`saldo`, `saldo_reservado`), su ledger `MovimientoBancario`. No es "hija" de la caja.
- **Forma de pago = router.** La UI de operación elige el destino: efectivo → caja mayor;
  no-efectivo → cuenta bancaria (config en `FormaPago`).
- **Tesorería = vista consolidada de solo lectura** (`TesoreriaReporteService.saldoConsolidado`):
  suma efectivo (todas las cajas) + bancos por moneda. Presentación; cero mezcla de datos.
- **Terceros:** cuenta corriente de cliente (`MovimientoCliente` + `Cliente.saldoActual`) y de
  proveedor (`MovimientoProveedor` + `Proveedor.saldoActual`).

Modelo portado de **frc-gourmet** (app hermana), adaptado a JPA/Postgres multi-usuario.

## 2. Núcleo — `TesoreriaService` (el único que toca el saldo de caja)

- `registrar(MovimientoCajaVirtual)` — aplica el movimiento con signo (`signedDelta`), saldo por
  `(caja, moneda)`, **lock pesimista** (`ensureRow` upsert + `lockByCajaVirtualIdAndMonedaId`),
  control de descubierto (`permite_saldo_negativo`, CN2), sincroniza el shim.
- `transferir(...)` — 2 movimientos, **lock en orden canónico (id caja asc)** → sin deadlock.
- `anular(id)` — bloquea si el movimiento no es MANUAL (**anulación cross-módulo**: se anula desde
  el dominio dueño); valida CN4 (límite de antigüedad). `revertir(mov)` — hook para los módulos dueños.
- `recalcularSaldos(caja)` — red de seguridad (reconstruye desde movimientos activos, con lock).
- **Ledger inmutable:** nunca se edita/borra un movimiento; se revierte con contra-movimiento `AJUSTE` firmado.
- **Trazabilidad:** `origen_tipo` (`OrigenMovimientoTipo`) + `origen_id` → habilita el bloqueo cross-módulo.

`BancoLedgerService` es el análogo para cuentas bancarias (mismo patrón: lock + descubierto contra
`saldo − reservado` + `ajustarReservado` para cheques diferidos).

## 3. Fuentes que alimentan la caja mayor (el circuito de dinero)

| Fuente | Servicio | Movimiento | Origen |
|---|---|---|---|
| RRHH (vale/préstamo/aguinaldo/liquidación/finiquito) | `service/rrhh/*` | EGRESO/INGRESO/AJUSTE | RRHH_* |
| Entrada/salida varia | `EntradaVariaService` | INGRESO/EGRESO | ENTRADA_VARIA |
| Retiro caja PDV → caja mayor | `RetiroTesoreriaProcesador` (poller `@Scheduled`) | INGRESO | RETIRO_CAJA |
| Devolución/merma | `DevolucionService` | EGRESO | DEVOLUCION |
| Operación financiera (5 tipos) | `OperacionFinancieraService` | según tipo | OPERACION_FINANCIERA |
| Cobro venta a crédito (banco) | `CobroCreditoService` | (banco) + MovimientoCliente | VENTA_CREDITO_COBRO |
| Pago proveedor (CPP) | `PagoProveedorService` | PAGO_PROVEEDOR / (banco) + MovimientoProveedor | PAGO_CPP |
| Cheque / acreditación POS | `ChequeGestionService` / `AcreditacionPosService` | (banco) | CHEQUE / ACREDITACION_POS |

**Replicación (clave):** `retiro`, `venta_credito`, `venta_credito_cuota`, `cobro` son **BRANCH_TO_MAIN**
(llegan a central por replicación lógica PG, NO por Spring). Por eso el puente Retiro→caja mayor es un
**poller `@Scheduled`** reconciliador (patrón SIFEN), no un evento. `RetiroTesoreriaProcesador` es un bean
separado del scheduler para que `@Transactional` aplique (no self-invocation). **DA8:** el cobro en efectivo
es exclusivo del POS filial; central cobra solo banco/cheque.

## 4. Operaciones financieras (5 tipos)
CAMBIO_DIVISA (egreso+ingreso caja), DEPOSITO_BANCARIO (egreso caja + entrada banco), RETIRO_BANCARIO
(salida banco + ingreso caja), TRANSFERENCIA_ENTRE_CAJAS (salida+entrada caja), TRANSFERENCIA_BANCARIA
(banco→banco, **no toca caja**). Los que tocan dos lados lockean en orden canónico.

## 5. CPC / CPP (doble ledger)
- **CPC** (`CobroCreditoService`): `VentaCredito`/`Cuota` como cuenta por cobrar; cobro por banco,
  parcial nativo (`monto_cobrado`/`estado_cobro`), tolerancia de redondeo, doble ledger
  (banco + `MovimientoCliente`), finaliza la venta si todas las cuotas quedan cobradas.
- **CPP** (`PagoProveedorService`): `SolicitudPago` como cuenta por pagar; pago **mixto**
  (varias líneas caja/banco), `PAGO_PROVEEDOR`, doble ledger (egreso + `MovimientoProveedor`),
  transición PENDIENTE→PARCIAL→CONCLUIDO.

## 6. Cheques + POS
- **Cheque** (`ChequeGestionService`): diferido reserva saldo; contado debita + COBRADO; cobrar
  diferido libera la reserva **antes** de debitar; anular bloquea si cobrado. Chequera con
  numeración incremental + AGOTADA.
- **POS** (`AcreditacionPosService` + `AcreditacionPosScheduler`): crea PENDIENTE (comisión + minutos),
  scheduler acredita las vencidas (idempotente por estado + lock), verificación con ajuste diferencial.

## 7. Concurrencia (regla del módulo)
**Todo servicio que muta un saldo toma lock pesimista** (`lockById`/`lockByCajaVirtualIdAndMonedaId`)
antes de leer-modificar-escribir: `TesoreriaService`, `BancoLedgerService`, `ClienteCuentaService`,
`ProveedorCuentaService`, `ChequeGestionService`, `AcreditacionPosService`, `CobroCreditoService`,
`PagoProveedorService`. Transferencias/operaciones de dos lados lockean en **orden canónico (id asc)**.

## 8. Seguridad por rol
`TesoreriaSecurityService` (patrón self-contained, issue #177): resuelve el usuario por el nickname del
SecurityContext, lee roles de DB, bypass ADMIN. Roles `TESORERIA VER`/`TESORERIA GESTIONAR` (migración
`V176.5`). **Todos** los resolvers financieros llaman `seg.requireVer()` (queries) / `seg.requireGestionar()`
(mutations). `cajaVirtualesActivas` es lectura compartida tesorería **o** RRHH.

## 9. Migraciones (todas aditivas, sufijo `.5`)
`V176.5` roles · `V177.5` núcleo (saldo por moneda, origen, backfill) · `V178.5` config base ·
`V179.5` puente retiro · `V180.5` bancos + operaciones · `V181.5` CPC/cuenta cliente ·
`V182.5` CPP/cuenta proveedor · `V183.5` cheques + POS · `V184.5` config (CN4/CN10).

## 10. Schedulers (off por default, `@ConditionalOnProperty`)
- `tesoreria.retiro-poller.enabled` → `RetiroTesoreriaScheduler` (puente PDV→caja mayor).
- `tesoreria.acreditacion-pos.enabled` → `AcreditacionPosScheduler` (acreditación POS automática).

## 11. Tests
Suite en `src/test/.../service/financiero/` (38 tests): `TesoreriaServiceTest`, `ComprobanteSerieServiceTest`,
`RetiroTesoreriaProcesadorTest`, `OperacionFinancieraServiceTest`, `CobroCreditoServiceTest`,
`PagoProveedorServiceTest`, `ChequeGestionServiceTest`, `TesoreriaReporteServiceTest`.

## 12. Pendiente (follow-up, no bloqueante del backend MVP)
UI desktop de tesorería completa; notificaciones (reusar `PushNotificationService` + canal saliente, AJ-4);
reportes cierre-mes (aging CPC/CPP, flujo de caja); `pago_solicitud_detalle` persistencia línea-a-línea;
"toda compra → CPP" wiring en Compras (DA2); migrar `VentaTarjeta.estado` a enum; wiring venta-tarjeta →
`crearAcreditacionPos`; cobro consolidado por convenio; permisos `CPP_*` dedicados (DA6); limpieza dead-code.
