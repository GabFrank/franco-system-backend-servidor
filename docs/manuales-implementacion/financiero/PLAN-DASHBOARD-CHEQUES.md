# Plan — Dashboard de Cheques

_Financiero / Tesorería · rama `feat/modulo-financiero`._

## 1. Objetivo y patrón

Un **único acceso** ("Cheques") = un **dashboard**, en vez de dos pantallas separadas (chequeras + cheques). Sigue el patrón establecido (`caja-virtual-dashboard` / `financiero-dashboard`): **header con filtro → sidebar con cards de saldos → main con lista + gráfico → accesos directos**.

Pregunta guía: *¿qué necesita ver un usuario de tesorería de primera mano?* → **la posición de cheques por fecha de pago**, para decidir si puede emitir/negociar un cheque nuevo en una fecha dada.

**Regla central:** todo se ordena por **fecha de PAGO (vencimiento)**, no por fecha de emisión.

## 2. Layout por sección

### Header — Filtro
- **Rango de fechas por fecha de pago**: desde / hasta, con presets (Hoy · Este mes · Próximos 30/60 días). Default: **mes actual**.
- **Chequera / cuenta** (opcional): Todas · una chequera · una cuenta bancaria.
- **Estado**: Todos · **Diferidos (pendientes)** [default] · Cobrados · Anulados. Los DIFERIDO son los que comprometen saldo futuro.
- Un **selector de día puntual** (o clic en una barra del gráfico) enfoca un solo día → alimenta el KPI de "a pagar ese día".

### Sidebar — Saldos (depende del filtro)
- **Un card por chequera activa** (o por cuenta si se agrupa por cuenta):
  - Nombre de la chequera · cuenta bancaria · hojas disponibles.
  - **Cheques pendientes hasta {fecha hasta}**: Σ `total` de cheques DIFERIDO con `fecha_pago ≤ hasta` de esa chequera (el compromiso futuro).
  - **Saldo de la cuenta** (saldo real) y **reservado** (Σ diferidos pendientes de esa cuenta).
  - **Disponible proyectado** = saldo − pendientes hasta la fecha (para ver si la cuenta lo cubre).
- **Card consolidado "Posición general"**: Σ cheques pendientes (todas las chequeras) + Σ saldos de cuentas → saldo neto general.

### KPI destacado — "A pagar en la fecha"
- Cuando el filtro apunta a **un día** (o se hace clic en una barra): mostrar en grande **"Total en cheques a cobrar el {fecha}: Gs X"** (Σ cheques con `fecha_pago == ese día`), con la cantidad de cheques.
- Es el dato para negociar: *"quiero emitir un cheque de 100.000.000 para el 12/08 → filtro 12/08 → ya hay Gs Y ese día → decido"*.

### Main — Lista + Gráfico
- **Gráfico (echarts, barras)**: eje X = **días** del rango filtrado; eje Y = **monto total en cheques a pagar ese día** (por `fecha_pago`). Barra clicable → enfoca ese día en el KPI + filtra la lista. Muestra los picos de vencimiento de un vistazo.
- **Lista de cheques** según el filtro (ordenada por `fecha_pago`): número, chequera/cuenta, beneficiario, monto, fecha emisión, fecha pago, estado (chip), diferido/nominal. Acciones por fila (mat-menu ⋮):
  - **Cobrar** (solo DIFERIDO → COBRADO; debita el banco y libera la reserva).
  - **Anular** (con motivo; libera reserva o revierte el débito).
  - **Ir al pago origen** (si el cheque nació de un pago CPP: navegar/mostrar el evento) — reutiliza el patrón "ir al origen".

### Accesos directos (`accesos`)
- **Gestionar chequeras** → dialog/pantalla de alta/edición de chequera (cuenta, nombre, rango desde/hasta, siguiente número, estado). _(Acá sí tiene sentido "chequera" como acceso secundario, no como menú de primer nivel.)_
- **Cuentas bancarias** / **Bancos** (ya existen).
- (Opcional) **Emitir cheque manual** (cheque suelto, no ligado a CPP) reutilizando `emitirCheque`.

## 3. Backend necesario (nuevo)

El backend de cheques ya tiene emitir/cobrar/anular y CRUD de chequera. Falta **agregación por fecha de pago**:

1. **`chequesFilter(desde, hasta, cuentaBancariaId?, chequeraId?, estado?)`** → lista de cheques por `fecha_pago` en el rango. `ChequeRepository`: `findByFechaPagoBetween...` + filtros. Type `Cheque` ya expone estado/moneda/cuenta/beneficiario (Fase 0).
2. **`chequesResumenPorDia(desde, hasta, cuentaBancariaId?, chequeraId?, estado?)`** → `[{ fecha, total, cantidad }]` agrupado por `fecha_pago` (para el gráfico y el KPI por día). JPQL `GROUP BY date(fecha_pago)`.
3. **`chequesSaldosPorChequera(hasta, estado?)`** → por chequera activa: `{ chequera, cuenta, pendientesHastaFecha, saldoCuenta, saldoReservado, hojasDisponibles }` (para los cards del sidebar).
4. Reusar `cobrarCheque` / `anularCheque` (existen) y `chequerasPorCuenta` (Fase 0).
5. Fechas como String (convención) → `stringToDate`.

## 4. Frontend nuevo

- **`cheques-dashboard`** component (bajo `financiero/cheque/` o `financiero/cheques-dashboard/`): header filtro + sidebar cards + main (echarts + tabla con acciones), siguiendo `caja-virtual-dashboard`.
- **`cheque.service`** (existe): sumar `onGetChequesFilter`, `onGetResumenPorDia`, `onGetSaldosPorChequera`, `onCobrar`, `onAnular`.
- **Chequera CRUD** como acceso secundario: `list-chequera` + `edit-chequera-dialog` con `app-generic-list` (patrón del sistema). `ChequeraInput.nombre` ya agregado.
- **Menú**: entrada **"Cheques"** en el grupo Financiero de `side-mini-variant.component.ts` (import + item + `case` → `openTabIfAuthorized(ROLES.TESORERIA..., ChequesDashboardComponent, "Cheques")`).
- **Gráfico**: echarts (convención del sistema, 45 componentes lo usan). Barras verticales, tooltip con monto+cantidad, clic→enfoca día.
- Dark mode; textos con contraste (variantes claras, como en la tabla de movimientos).

## 5. Fases sugeridas

- **F1 — Backend**: repos + 3 queries de agregación + resolver + schema. Tests.
- **F2 — Dashboard UI**: header filtro + sidebar cards + KPI por fecha + tabla con Cobrar/Anular. (sin gráfico todavía)
- **F3 — Gráfico echarts** (monto por día de pago) + clic→enfoca.
- **F4 — Chequera CRUD** (acceso secundario) + menú.
- (Opcional) emitir cheque manual.

## 6. Decisiones (confirmadas)
- **D1 ✅** — Cards del sidebar **por chequera**, con el saldo de la cuenta vinculada (en la práctica una cuenta tiene una sola chequera activa a la vez).
- **D2 ✅** — Estado default del filtro: **Diferidos** (pendientes).
- **D3 ✅** — "Pendiente hasta la fecha" del card = hasta la `fecha hasta` del rango; el consolidado muestra el total.
- **D4 ✅** — **Incluir emitir cheque manual** (cheque suelto, no ligado a CPP) reutilizando `emitirCheque` — como acción del dashboard.
