# Plan de jornada — Módulo Financiero (2da tanda)

Basado en 4 investigaciones paralelas de frc-gourmet + estado actual. Rama `feat/modulo-financiero`.
Objetivo: cerrar los 7 ítems que pidió Gabriel. Se ordena por riesgo/valor y se marca qué se difiere.

---

## 1. Columna "Responsable" en la tabla de movimientos  · TRIVIAL · desktop
Insertar columna entre Fecha y Tipo = `usuario` que creó el movimiento (`row.usuario?.persona?.nombre`).
El campo `usuario` ya viene en `movimientoFields` del GraphQL. Solo tocar dashboard `.html` + `displayedColumns`.

## 2. Cuenta bancaria: nombre, alias, activo, flag operable  · MEDIO · central + desktop
- **`nombre`** de cuenta (≠ titular) — NO existe en ningún lado → agregar.
- **`alias`** — ya existe en backend (V180.5) → solo exponer en el dialog desktop.
- **`activo`** — ya existe en backend → exponer en el dialog desktop.
- **`disponible_operaciones_financieras`** (bool) — nuevo. Distingue cuentas propias (operables en caja mayor/finanzas) de cuentas de terceros (proveedor/cliente). Backfill: `false` si `persona_id IS NOT NULL`.
- Backend: **V187.5** (`nombre` + `disponible_operaciones_financieras`) + entidad + input + schema + query nueva `cuentasBancariasOperables` (activo && disponible).
- Desktop: modelo/input/dialog de alta (nombre requerido, alias, activo, toggle disponible) + `onGetAllOperables()` usado por `configurar-caja-virtual-dialog` y `add-operacion-financiera-dialog` (que hoy traen TODAS las cuentas).

## 3. Reducir botones del dashboard a: Ingreso · Egreso · Operaciones · Configurar  · TRIVIAL · desktop
Quitar Ingreso Vario / Egreso Vario / Transferir. Ingreso/Egreso pasan a abrir los selectores (ítem 4).
Transferir se absorbe en Operaciones Financieras (`TRANSFERENCIA_ENTRE_CAJAS`).

## 4. Selectores Ingreso / Egreso (tipo-cards)  · MEDIO · desktop
Nuevos `registrar-ingreso-dialog` / `registrar-egreso-dialog` (grid de tarjetas tipo, patrón gourmet, pero el
selector espera el afterClosed del sub-diálogo y se cierra con su resultado — sin el hack de `openDialogs`).
- **Ingreso — fase 1 (reusa lo existente):** Entrada Varia · Operación Financiera · Ajuste de Saldo.
- **Egreso — fase 1:** Operación Financiera · Ajuste · Registrar Vale (requiere exportar `ConfirmarValeDialogComponent` de `RrhhModule` e importarlo en `FinancieroModule`).
- **Diferido:** Retiro de Caja de Venta (puente automático por replicación), Gasto (circuito de aprobación distinto), Pagar Compras CPP en lote (falta backend), Compra Simplificada (feature nueva), Emitir Cheque (backend listo, falta dialog).

## 5. Operaciones Financieras con Tabs  · MEDIO-ALTO · desktop (+ backend opcional)
Rehacer `add-operacion-financiera-dialog` con `mat-tab-group` (una tab por tipo), campos dinámicos, y:
- **Cotización auto** (multiplicar/dividir según moneda principal) — hoy el usuario tipea ambos montos a mano.
- **Bloque "diferencia"** (IGNORAR/GASTO/VALE) — NO existe en backend ni desktop → requiere migración + entidad + service + enum `DiferenciaDestinoTipo`, o **diferir**.
- El backend ya postea los 5 tipos correctamente; la cotización es client-side (correcto).
- Extraer `CAMPOS_REQUERIDOS` a util testeable.
- **Decisiones:** cómo se determina "moneda principal" (¿existe flag en `Moneda`? ¿o Gs por convención?); "GASTO" por diferencia = ajuste etiquetado (como gourmet) vs Gasto real; ¿`comprobanteUrl`?

## 6. Dashboard Financiero + rename tesorería→financiero  · MEDIO · desktop (backend rename diferido)
- **Colisión:** ya existe un `FinancieroDashboardComponent` HUÉRFANO (viejo, desconectado del menú, con TODOs). Resolver: eliminarlo tras rescatar lo útil, o renombrarlo.
- Renombrar `tesoreria-dashboard/` → `financiero-dashboard/` (componente + service + model + 3 GraphQL). Mantener los nombres de campo del backend (`saldoConsolidadoTesoreria`, etc.) — **rename de backend es breaking, se DIFIERE**.
- Agregar sección **"Cajas Virtuales"** con cards clickeables de cajas activas (`cajaVirtualesActivas` ya existe) que abren el detalle. NO portar el sistema de shortcuts persistidos de gourmet (entidad+resolvers nuevos) — diferido.
- Mantener los 3 paneles read-only (saldo consolidado, aging CPP, vencimientos).
- Roles `TESORERIA_*` se mantienen internos (rename de rol en BD es riesgoso, diferido); solo cambia la etiqueta visible.

## 7. Reorganizar side menu "Financiero" con submenús  · MEDIO · desktop
El menú soporta anidamiento (RRHH ya usa 3 niveles). Reagrupar:
- **Quitar** item "Tesorería" (list-caja-virtual) — acceso a cajas SOLO desde el dashboard.
- **Quitar** "Operaciones Financieras" del menú — acceso desde el dashboard (quick-action).
- Submenús propuestos: Dashboard · Caja/Operativa (Gastos, Retiros, arreglar "Pagos" roto) · Cuentas/Bancos (Cuentas Bancarias, Bancos) · Configuración (Cotización, Monedas*, Timbrado, Doc. electrónico) · Reportes/Análisis (Análisis diferencias, Lucros, Terminales POS, Facturas).
- **"Monedas"**: `MonedaComponent` es un STUB — hay que implementar un `list-moneda` real antes de cablearlo (o diferir).
- Regla de 3 ediciones por item (import + nodo + case).

---

## Estado: TODOS los ítems IMPLEMENTADOS y pusheados (2026-08-03)
Central `bdf8aa86`, desktop `c38435fe` (+ fix 42P18 `c234ce5f`). 54 tests backend verdes, AOT desktop verde,
queries validadas end-to-end (cuentasBancariasOperables, monedas.principal, operacion.diferencia).
Diferido según lo previsto: Pagar Compras CPP en lote, Compra Simplificada, Emitir Cheque (dialog),
Retiro-caja manual, shortcuts persistidos, rename de campos GraphQL/roles backend.
Vale en el selector de egreso quedó diferido (evitar cross-import RrhhModule por ahora).

## Orden de ejecución recomendado (fase 1)
1. Ítem 1 (responsable) + Ítem 3 (botones) — triviales, desktop, inmediatos.
2. Ítem 2 (cuenta bancaria) — backend V187.5 + desktop; desbloquea el filtro operable.
3. Ítem 4 (selectores) — fase 1 con lo existente.
4. Ítem 6 (dashboard financiero + rename UI) — resolver colisión + cards de cajas.
5. Ítem 7 (menú) — reorg con submenús (Monedas condicionado a implementar el CRUD).
6. Ítem 5 (operaciones tabs) — el más pesado; hacer al final, diferir "diferencia" si se decide.

## Decisiones (resueltas)
- **D1 (ítem 5):** `Moneda.principal` existe (flag único, CN1 — GUARANI). La cotización lo usa.
- **D2 (ítem 5):** **Diferencia SE IMPLEMENTA AHORA** — migración + campos + enum `DiferenciaDestinoTipo` + lógica de ajuste etiquetado (como gourmet: no crea Gasto real, solo AJUSTE en caja).
- **D3 (ítem 6):** **Descartar TODO** — se elimina el `FinancieroDashboardComponent` huérfano viejo Y el `tesoreria-dashboard` nuevo. Nada que rescatar. Se crea un `financiero-dashboard` NUEVO siguiendo el `FinancieroDashboardComponent` de gourmet (KPIs, quick actions, cards de cajas, gráficos, acordeón operativa/config, próximos vencimientos, cajas abiertas — adaptado al backend disponible).
- **D4 (ítem 7):** **Construir `list-moneda` real** (denominación, símbolo, decimales, principal, activo) y agregarlo al submenú Configuración.
- **D5 (ítem 4):** RrhhModule NO importa FinancieroModule → sin ciclo; el cross-import para Registrar Vale es seguro.
