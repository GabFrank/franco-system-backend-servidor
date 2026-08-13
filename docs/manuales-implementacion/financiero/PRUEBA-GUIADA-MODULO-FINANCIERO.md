# Prueba guiada — Módulo Financiero / Tesorería

> Guion de prueba manual punta a punta, para ejecutar **en conjunto** (Gabriel + asistente)
> al terminar toda la implementación. Cada bloque tiene precondición, pasos y resultado esperado.
> Marcar ✅/❌ por paso. Idioma de dominio: español. Todo en guaraníes salvo que se indique.
>
> Setup: central perfil `dev` (`-Dspring-boot.run.profiles=dev`), desktop `npm start`, login ADMIN.
> Este doc se completa/afina a medida que se implementan las fases (si algo se desvía del roadmap).

## 0. Preparación
- [ ] Central levanta sin errores (Flyway aplica migraciones nuevas del módulo).
- [ ] Roles de tesorería asignados a un usuario de prueba (`TESORERIA VER/GESTIONAR`, `CPP_*`, `CPC_*`, `BANCOS_*`).
- [ ] Existe al menos una `CajaVirtual` tipo `CAJA_MAYOR` activa.
- [ ] Monedas cargadas (Gs principal, USD, BRL) con `decimales` y regla de redondeo.

## 1. Núcleo de caja mayor (F1)
- [ ] Registrar un **ingreso** manual de efectivo (Gs) → saldo `(caja, Gs)` sube; movimiento aparece en el historial con `saldoAnterior/Posterior` correctos.
- [ ] Registrar un **egreso** que deja saldo negativo → **bloqueado** si la caja no permite negativo (CN2); permitido si el flag está activo.
- [ ] **Anular** un movimiento → se crea contra-movimiento con signo opuesto; el original queda visible (tachado), no se borra; saldo vuelve al valor previo.
- [ ] Intentar anular un movimiento con origen en otro módulo (ej. RRHH) → **bloqueado** ("anular desde el módulo dueño").
- [ ] Anular un movimiento más viejo que `diasLimiteAnulacion` (CN4) → bloqueado.
- [ ] `recalcularSaldos` (admin) → el saldo no cambia (reconstrucción coincide).
- [ ] RRHH sigue funcionando: confirmar un vale descuenta de la caja mayor como antes.

## 2. Operación de caja mayor (F2)
- [ ] Crear una **entrada varia** (categoría, monto, moneda) → ingreso posteado; comprobante numerado (CN3) sin huecos.
- [ ] Registrar un **gasto** multi-detalle (2 monedas) → 2 movimientos, 1 por detalle.
- [ ] Adjuntar un comprobante (imagen/PDF) a un gasto → queda vinculado.
- [ ] Configurar visibilidad de la caja mayor (formas de pago / cuentas visibles, flags CPP/CPC) → se refleja en el detalle.
- [ ] **Egreso de caja inicial** (contar billetes) → genera conteo + movimiento; ese conteo queda disponible para abrir una caja PDV.
- [ ] Redondeo por moneda (CN7): un monto en Gs se redondea al múltiplo configurado.

## 3. Puente PDV → caja mayor (F3)
- [ ] Cerrar una caja PDV con efectivo contado → se genera un **retiro FLOTANTE** (idempotente: cerrar de nuevo no duplica).
- [ ] **Ingresar** ese retiro a la caja mayor → `INGRESO_CIERRE`, saldo sube; segundo intento → **bloqueado** (guard anti doble-ingreso).
- [ ] Devolución/merma con caja asignada → **EGRESO** en la caja mayor.

## 4. Bancos + operaciones financieras (F4)
- [ ] Crear una **cuenta bancaria** (saldo inicial). Ver saldo actual/reservado.
- [ ] **Cambio de divisa** (Gs→USD entre cajas/monedas) → par de movimientos con cotización; saldos coherentes.
- [ ] **Depósito bancario** desde caja mayor → egreso en caja + suma en cuenta + `MovimientoBancario`.
- [ ] **Retiro bancario** → resta en cuenta + ingreso en caja.
- [ ] **Transferencia entre cajas** (mayor↔chica) → par transferencia.
- [ ] **Transferencia bancaria** (banco→banco) → mueve solo cuentas, **no** toca caja mayor.
- [ ] Un egreso/depósito que dejaría la cuenta bancaria en negativo → **bloqueado** (CN2).
- [ ] **Anular** un depósito/retiro bancario → contra-movimiento; saldo de la cuenta restaurado.
- [ ] Ledger unificado de la cuenta muestra todo sin duplicados.

## 5. CPC — cobros a clientes (F5)
- [ ] Cobrar una cuota de venta a crédito **parcial** (fuente caja) → cuota `PARCIAL`, `MovimientoCliente` + `Cliente.saldoActual` baja, `INGRESO_COBRO_CLIENTE` en caja.
- [ ] Cobrar el resto (fuente **banco**, moneda distinta) → conversión aplicada, saldo banco sube, cuota `COBRADO`, CPC `COBRADO`.
- [ ] **Anular** un cobro → idempotente (no revierte dos veces), saldos restaurados.
- [ ] **Cobro consolidado por convenio** (varios clientes) → un movimiento por cliente + recibo PDF (3/hoja).

## 6. CPP — pagos a proveedores (F6)
- [ ] Registrar una compra de **contado** → genera CPP con 1 cuota; pagarla → `PAGO_PROVEEDOR`/egreso, CPP `CONCLUIDO`.
- [ ] Compra a **crédito** (N cuotas) → pagar una cuota **simple**; pagar otra con **pago mixto** (2 líneas: caja Gs + banco USD) → montos convertidos correctos, cuota reduce.
- [ ] **Pago en lote** de varias cuotas del mismo proveedor.
- [ ] Préstamo a funcionario → desembolso (egreso) + cobro de cuota (ingreso, dirección invertida).

## 7. Cheques + POS (F7)
- [ ] Emitir cheque **diferido** → `saldoReservado` sube, caja/cuenta no se debitan aún; cobrarlo → debita `saldo`, libera reserva, `COBRADO`.
- [ ] Emitir cheque **contado** → debita inmediato, queda `COBRADO`.
- [ ] Anular un cheque no cobrado → `ANULADO` (libera reserva si era diferido); anular uno cobrado → **bloqueado**.
- [ ] Numeración de chequera avanza; al pasar el `numeroFinal` → `AGOTADA`.
- [ ] Venta con tarjeta → `AcreditacionPos` PENDIENTE; scheduler la acredita al vencer los minutos (`ACREDITADO_AUTO`); verificar con diferencia → ajuste diferencial idempotente.

## 8. Reportes, notificaciones, config (F8)
- [ ] Reporte "cierre de mes": aging CPC/CPP, flujo de caja, comisiones POS, vencimientos 30d — números coherentes con lo operado arriba.
- [ ] Dashboard de tesorería: **saldo consolidado** (caja(s) efectivo + todas las cuentas bancarias) — la vista unificada refleja el total real.
- [ ] Notificación: suscribir un receptor a `CUENTA_PAGAR_VENCE`; con una cuota por vencer dentro de `diasAnticipacion` (CN8) → se dispara (email/WhatsApp o log).
- [ ] Feature flag (CN9): apagar "operaciones financieras" → la opción desaparece/queda deshabilitada.
- [ ] Auditoría de config (CN10): cambiar un umbral → queda registrado quién/cuándo.

## 9. Seguridad por roles (transversal)
- [ ] Usuario **sin** rol de tesorería → no ve/opera nada del módulo; backend rechaza.
- [ ] Usuario con rol de solo lectura → ve, no modifica.
- [ ] Usuario con rol de gestión → opera lo permitido; ADMIN → todo.

## 10. Consolidación final
- [ ] Recorrer la vista de tesorería: caja(s) efectivo + bancos consolidados dan el panorama completo.
- [ ] Ningún dato "mezclado" (efectivo en banco o viceversa).
- [ ] Comprobantes térmicos imprimen (retiro, vale, recibo cobro/pago, acreditación, acta de conteo).
