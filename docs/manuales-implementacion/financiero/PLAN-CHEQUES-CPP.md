# Plan — Pagar compras (y gastos) con CHEQUE

_Financiero / CPP · rama `feat/modulo-financiero`._

## 1. Contexto y hallazgos

### Lo que YA existe en central (reutilizable)
- **`Cheque`** (`financiero.cheque`): `chequera`, `numero`, `fechaEntrega`, `fechaPago` (vencimiento), `orden`, `concepto`, `diferido`, `total`, `estado` (`EstadoCheque` = EMITIDO/DIFERIDO/COBRADO/ANULADO), `moneda`, `cuentaBancaria`, `fechaCobro`, `motivoAnulacion`, `movimientoBancarioId`, `firmante`. **No tiene** `beneficiario` ni `nominal`.
- **`Chequera`** (`financiero.chequera`): `cuentaBancaria`, `rangoDesde`/`rangoHasta` (**límite de hojas**), `siguienteNumero` (**correlativo**), `estado` (`EstadoChequera` = ACTIVA/AGOTADA/ANULADA), `nombre`.
- **`ChequeGestionService`** (el flujo correcto, con seguridad por rol): `emitir` / `cobrar` / `anular`.
  - **Diferido → reserva** `cuentaBancaria.saldoReservado` (NO debita). **Contado → debita** `saldo` + `MovimientoBancario` SALIDA_MANUAL y queda COBRADO.
  - Avanza el correlativo; si `numero >= rangoHasta` → chequera `AGOTADA`.
  - `anular`: bloquea si COBRADO; libera reserva si era diferido.
- **`BancoLedgerService`**: `ajustarReservado(cuentaId, delta)` (único mutador de `saldoReservado`); descubierto se controla contra **disponible = saldo − reservado** → **la reserva ya impide sobre-comprometer la cuenta** con cheques diferidos.
- **`proximosVencimientosTesoreria(dias)`**: listado read-only que ya incluye cheques diferidos por vencer.
- **Impresión**: `ImpresionService.imprimir(nombre, generar, soloPdf?)` + `ImprimirDialogComponent` (PDF A4 / ticket). Se integra definiendo un `generar` contra un endpoint backend (patrón `imprimirSolicitudPagoPDF`).
- Migración base ya aplicada: **`V183.5__financiero_cheques_pos.sql`**.

### Referencia frc-gourmet (qué aporta y qué no)
- **Aporta el patrón** de `saldoReservado` por cheque diferido, correlativo/agotamiento de chequera, y las transiciones de estado. **Central ya lo tiene igual o mejor.**
- **NO aporta**: cheque ligado a un pago de CPP (en gourmet el cheque es un instrumento bancario standalone), cuotas por intervalos de días, vigilancia de vencimiento, ni impresión. Todo eso es **diseño nuevo**.

### Brechas a cubrir
1. `PagoProveedorService.procesarEvento:244` tira `throw ... "F7"` para `FuentePago.CHEQUE`.
2. `PagoProveedorService.LineaPago` no tiene campos del cheque (`chequeraId`, `diferido`, `fechaPago`, `beneficiario`, `nominal`, `numero`).
3. `financiero.PagoSolicitudDetalle` no tiene `cheque_id`.
4. La **consolidación por `(fuente, caja/cuenta, moneda)`** no calza con "1 cheque = 1 hoja física".
5. El **schema GraphQL de `Cheque`/`Chequera` está desactualizado** (no expone `estado`, `moneda`, `cuentaBancaria`, `siguienteNumero`, `nombre`, `estado` chequera…).
6. No hay query **`chequerasPorCuenta` / activas / hojas disponibles**.
7. `Cheque` no tiene `beneficiario` ni `nominal`.
8. No hay **scheduler/alerta** de vencimiento (solo listado read-only).
9. Entidad legacy `operaciones.SolicitudPagoDetalle` tiene campos de cheque (nominal/portador/diferido) **desconectados** del flujo real → decidir deprecar.

---

## 2. Decisiones de diseño

- **D1 — Un cheque por línea, sin consolidar.** Una línea `FuentePago.CHEQUE` = **1 cheque físico** (su propio número de hoja). Las líneas CHEQUE **se saltan** la consolidación por moneda/cuenta; cada una emite su propio `Cheque`. _(Asumido.)_
- **D2 — Cuotas en el frontend. ✅ CONFIRMADO.** El diálogo genera **N líneas CHEQUE** a partir de `cuotas` (1-12) + `intervalo` (7/15/30/45 días): la 1ª en la `fechaPago` cargada, las siguientes `+intervalo` días; el monto se reparte (última cuota absorbe el redondeo). El backend recibe N líneas ya calculadas y solo emite cada cheque.
- **D3 — Cheque como forma de pago = SIEMPRE vía `ChequeGestionService.emitir`.** Nunca el CRUD crudo `saveCheque`. Diferido → reserva saldo futuro; contado → debita. _(Asumido.)_
- **D4 — Beneficiario / nominal.** Agregar a `Cheque`: `beneficiario` (varchar, editable, default = nombre del proveedor) y `nominal` (boolean). "Al portador" = `nominal=false` + beneficiario null. _(Asumido.)_
- **D5 — Vencimiento. ✅ CONFIRMADO.** Fase 1 se apoya en la **reserva** (ya impide sobre-comprometer) + el **listado read-only** existente. El **scheduler de alerta** queda para **Fase 3**.
- **D6 — Impresión. ✅ CONFIRMADO (stub).** El botón "Imprimir" en Revisar **abre `ImprimirDialogComponent`**; el contenido/formato del comprobante se define en otra iteración.
- **D7 — Gastos con cheque.** Depende de que exista el módulo **Gasto** (tarea #35, no empezada). Se reutiliza el mismo mecanismo cuando exista. **Fuera de esta fase.**
- **D8 — Legacy `operaciones.SolicitudPagoDetalle`** (campos nominal/portador/diferido, solo para el PDF declarativo): **deprecar** (issue aparte), no migrar — el flujo real usará `financiero.Cheque`. _(Asumido.)_
- **D9 — Cancelación.** `anularPagoCpp(pagoId)` revierte también las líneas cheque (`ChequeGestionService.anular`). La cancelación **individual** de un cheque desde la lista que reabra la solicitud es **Fase 3** (requiere link inverso cheque→solicitud). _(Asumido.)_

---

## 3. Plan por fases

### FASE 0 — Alinear el schema GraphQL de Cheque/Chequera (deuda pre-existente)
_Necesario para que el front lea estado/hojas._
- `cheque.graphqls`: exponer `estado`, `moneda`, `cuentaBancaria`, `fechaCobro`, `motivoAnulacion`, `movimientoBancarioId` (ya en la entidad).
- `chequera.graphqls`: exponer `nombre`, `siguienteNumero`, `estado`; agregar campo derivado **`hojasDisponibles`** (`rangoHasta − siguienteNumero + 1`) vía resolver.
- Nueva query **`chequerasPorCuenta(cuentaBancariaId: ID!, soloActivas: Boolean = true): [Chequera]`** + `ChequeraRepository.findByCuentaBancariaId(...)` / `findByCuentaBancariaIdAndEstado(...)`.
- Sin migración (todo ya existe en DB).

### FASE 1 — Backend: cheque como forma de pago CPP
- **Migración `V19x.5`** (aditiva):
  - `ALTER TABLE financiero.pago_solicitud_detalle ADD COLUMN cheque_id bigint;` (+ índice).
  - `ALTER TABLE financiero.cheque ADD COLUMN beneficiario varchar(200); ADD COLUMN nominal boolean DEFAULT true;`
- **`PagoSolicitudDetalle`**: campo `chequeId`.
- **`PagoProveedorService.LineaPago`**: + `chequeraId`, `diferido`, `fechaPago`, `beneficiario`, `nominal`, `numeroCheque` (opcional, si null usa `siguienteNumero`).
- **`procesarEvento`**:
  - En el armado de grupos (`claveGrupo`): las líneas CHEQUE **no se agrupan** — se procesan aparte, 1 cheque por línea.
  - Nueva rama: por cada línea CHEQUE → construir `Cheque` (chequera, cuenta, moneda, total, diferido, fechaPago, beneficiario, nominal, concepto="Pago a {proveedor} — solicitud #X") → `chequeGestionService.emitir(cheque, usuario)`. Guardar `detalle.chequeId = cheque.id` y `detalle.movimientoBancarioId = cheque.movimientoBancarioId` (no null solo si contado).
  - Inyectar `ChequeGestionService` en `PagoProveedorService`.
- **`anularPagoCpp`**: para detalles con `chequeId` → `chequeGestionService.anular(chequeId, motivo, usuario)` (libera reserva o bloquea si ya cobrado — en ese caso, decidir: no permitir anular el pago, o dejar el cheque cobrado y solo revertir el resto).
- **GraphQL**: `LineaPagoInput` + campos cheque; el resolver los mapea.
- **Tests**: emisión diferida (reserva, no debita), contado (debita), 1 línea = 1 cheque, anular libera reserva, chequera agota, sin hojas → error.

### FASE 2 — Frontend: UI de pago con cheque en `pagar-compras-dialog`
_Paso "Formas de pago" del stepper._
- Al elegir **fuente = Cuenta bancaria**, cargar `chequerasPorCuenta(cuentaId, soloActivas)`. Si hay ≥1 chequera con `hojasDisponibles > 0` → mostrar toggle **"Pagar con cheque"**.
- Al activar cheque, habilitar campos: **chequera** (si hay varias), **número** (default `siguienteNumero`, editable), **fecha emisión**, **fecha pago/vencimiento**, **diferido** (toggle), **nominal** (toggle) + **beneficiario** (default nombre proveedor, editable), **cuotas** (select 1-12) y, si >1, **intervalo** (7/15/30/45 días).
- **Generación de cuotas (front)**: al "Agregar", si cuotas>1 → generar N líneas CHEQUE encadenadas (fechas +intervalo, montos repartidos, correlativos consecutivos desde `siguienteNumero`).
- **Balance / ajuste FX**: el cheque cuenta como pago en la moneda de la cuenta; si ≠ moneda de la deuda, aplica la misma cotización que ya maneja el diálogo.
- **Revisar y confirmar**: sección "Cheques" listando cada uno (número, fecha pago, monto, diferido/nominal, beneficiario) + botón **Imprimir** → abre `ImprimirDialogComponent` (**stub** D6: contenido a definir en otra iteración; no requiere endpoint backend por ahora).

### FASE 3 — Vencimiento proactivo (opcional)
- `@Scheduled` (patrón `RrhhNotificacionScheduler`, `@ConditionalOnProperty` off por default) que, para cheques `DIFERIDO` por vencer en ≤N días con **disponible < total**, dispare una notificación (FCM/campana). Config `tesoreria.cheque-vencimiento.enabled`.
- Cancelación individual de cheque desde la lista que reabra la solicitud (link inverso cheque→pago_solicitud_detalle ya existe vía `cheque_id`).

### FASE 4 — Cheque en gastos
- Cuando exista el módulo Gasto (#35), reutilizar el mismo mecanismo de líneas CHEQUE.

---

## 4. Riesgos / notas
- **Descubierto**: la reserva ya protege (no se puede emitir diferido si dejaría el disponible negativo, salvo cuenta con `permiteSaldoNegativo`). Confirmar UX del error.
- **Correlativos concurrentes**: `emitir` toma lock pesimista de la chequera → seguro para cuotas múltiples en la misma transacción (se emiten secuencialmente).
- **Anular pago con cheque ya cobrado**: caso borde — definir política (D9).
- **No romper el pago actual**: las ramas CAJA_MAYOR/CUENTA_BANCARIA/AJUSTE quedan intactas; CHEQUE es aditivo.
- **Schema desalineado (Fase 0)** es una corrección de deuda pre-existente independiente — se puede hacer/mergear sola.

## 5. Orden sugerido
Fase 0 → Fase 1 (backend + tests) → Fase 2 (UI) → probar en DB → (Fase 3/4 después).
