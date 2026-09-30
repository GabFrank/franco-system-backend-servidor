# PLAN — Número de operación para cargar ítems de liquidación

**Pedido (Franco, 2026-09-30):** en "Agregar ítem" de la liquidación, poder escribir un número y que se
elija sola la operación (ej. `1` → AJUSTE (HABER)). Decisión: **número fijo por operación**, guardado en
el catálogo, para que no se corra al agregar o desactivar operaciones.

Repos: **central** (rama `feature/rrhh-numero-operacion-liquidacion` desde develop) + **desktop** (misma
rama, **encima de `feature/rrhh-item-liquidacion-otro-periodo`**, PR #374 sin mergear: edita el mismo
panel). Filial `N/A: rrhh.liquidacion_concepto es central-only [ev: V205.5:25]`. Mobile/PWA `N/A`.

## Estado de hoy

- `rrhh.liquidacion_concepto` (V154.0, V205.5, V211.5): `codigo`, `descripcion`, `es_haber`,
  `es_calculado_auto`, `es_remunerativo`, `activo`. Sin número.
- El select "Operación" lista `liquidacionConceptosParaItemManual` = activos y no automáticos, ordenados
  `es_haber desc, descripcion` (`LiquidacionConceptoRepository:23`). En bodega local: AJUSTE (HABER),
  BONIFICACION, BONO MANUAL, REINTEGRO, VENTA DE VACACIONES, AJUSTE (DESCUENTO), DESCUENTO JUDICIAL,
  FALTANTE DE CAJA.
- El catálogo se edita en RRHH → Configuración → Conceptos (`edit-liquidacion-concepto-dialog`), rol
  `RRHH CONFIG` o `GESTIONAR`.

## Diseño

### Central

- `V235.1__rrhh_liquidacion_concepto_numero.sql` (en este orden):
  1. `ADD COLUMN IF NOT EXISTS numero INTEGER` (nullable);
  2. seed: numera los conceptos **activos y no automáticos** sin número, en el orden actual del select con
     desempate determinista (`es_haber` es NOT NULL desde V154.0:67; `descripcion` es nullable):
     `ROW_NUMBER() OVER (ORDER BY es_haber DESC, descripcion, codigo)` + `COALESCE(MAX(numero), 0)` como
     base, así un re-run (out-of-order/repair) no choca con números ya puestos;
  3. recién después, índice **único parcial solo entre activos**:
     `CREATE UNIQUE INDEX ... ON rrhh.liquidacion_concepto(numero) WHERE numero IS NOT NULL AND activo`.
  Desactivar una operación **libera** su número (el ABM no borra, solo desactiva: con unicidad sobre todas
  las filas el número quedaría quemado). Automáticos e inactivos quedan sin número. Una migración futura
  que agregue un concepto lo deja sin número hasta que se lo asignen en el ABM.
- Entidad `numero`; `LiquidacionConceptoInput.java` + `configuracion-rrhh.graphqls` (`type` e `input`).
- `saveLiquidacionConcepto`: `numero` > 0 y único **entre activos**, validado al crear, al cambiar el número
  **y al reactivar** ("El número 3 ya lo usa BONO MANUAL"). El `DataIntegrityViolationException` del índice
  (dos ediciones simultáneas) se traduce al mismo mensaje. `setSkipNullEnabled(true)` hace que un cliente
  viejo sin el campo lo conserve; para **quitar** un número el desktop nuevo manda `0` (= sin número).
  Intercambiar dos números: pasar uno por `0` y después asignarlos.
- Orden explícito con `Sort` (no método derivado): `findParaItemManual` y la lista del ABM por
  `numero` (sin número al final), después `es_haber desc, descripcion, codigo`.

### Desktop

- Catálogo: columna **N°** en la lista (ordenada por número) y campo **Número** en el diálogo de edición
  (vacío = sin número, se manda `0`).
- **Orden de despliegue obligatorio: central primero.** El desktop nuevo pide `numero`: contra un central
  viejo fallan la lista de conceptos, su guardado y el select de Operación (el panel queda en modo "sin
  catálogo"). Va en los dos PRs.
- "Agregar ítem" de la liquidación: campo **N°** (chico, numérico) antes de "Operación". Al tipear un
  número se elige esa operación (y su signo); si no existe, error "No hay operación N° 9". Elegir en el
  select completa el N°. Las opciones muestran `1 · AJUSTE (HABER)`. Sin funciones en el template
  (precalculado en `valueChanges`).
- Solo la liquidación mensual: el finiquito no usa el catálogo en su alta manual (`N/A`).

## Tabla de datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `liquidacion_concepto.numero` | seed `V235.1`; `saveLiquidacionConcepto` ← diálogo de concepto | `findParaItemManual` (orden) → select y campo N° del panel de ítem; lista de conceptos |

## Migración

`V235.1` (mayor en develop: `V234.1`). Aditiva + `UPDATE` de datos de referencia de una tabla central-only.
Rollback: el JAR viejo ignora la columna; el índice único parcial no afecta a sus inserts (numero NULL).

## Fases

| # | Repo | Qué | Tests |
|---|---|---|---|
| 1 | central | Migración, entidad, schema, validación de unicidad entre activos, orden | `LiquidacionConceptoNumeroTest`: número repetido entre activos rechaza; repetido con uno inactivo acepta; reactivar con número tomado rechaza; negativo rechaza; `0` lo quita; update sin número conserva el existente; violación del índice → mensaje; orden por número con los sin número al final |
| 2 | desktop | N° en el catálogo (lista + edición) y en el panel de ítem | `npm run check`; prueba en UI |
| 3 | los dos | Docs (estado del módulo, plan de testeo); borrar este plan | — |

Orden de PRs: central primero (el desktop pide `numero`).

## Prueba manual

1. Liquidación en borrador → Agregar Item → N° `1` → Operación AJUSTE (HABER), hint "Suma al total".
2. N° `6` → AJUSTE (DESCUENTO). N° `99` → "No hay operación N° 99", no guarda.
3. Elegir FALTANTE DE CAJA en el select → N° se completa con su número.
4. Configuración → Conceptos: cambiarle el número a uno → el panel lo toma; repetir un número → rechazo.

## Qué queda sin verificar

- La numeración inicial en **farmacia** y **bodega producción** sale del orden de su propio catálogo y queda
  fija desde el deploy: **consulta obligatoria de solo lectura antes del deploy** (con el ok de Franco) que
  muestre cómo quedaría numerado cada ambiente.
- El CI no valida migraciones: la `V235.1` se prueba arrancando el central local contra la base real.
- Dry-run de la migración contra una copia real (paso 10).

## Auditoría del plan (paso 5)

Dos auditores sin verse; sin contradicciones.

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| B-5 | B | Único sobre todas las filas quema el número de un concepto desactivado (el ABM no borra) | Único solo entre activos; se valida también al reactivar |
| B-2 / A-5 | A, B | Seed sin desempate; re-run choca con números puestos | `ORDER BY es_haber DESC, descripcion, codigo` + base `MAX(numero)`; índice después del update |
| A-P1 | A | Violación del índice por edición simultánea sale como error crudo | Se traduce al mismo mensaje |
| A-P2 / A-P4 | A | `findParaItemManual` sin desempate; la lista del ABM sin orden | `Sort` explícito en los dos |
| A-P3 | A | Con skipNull no se puede quitar un número | `0` = sin número |
| A-4 / A-P5 | A | Desktop nuevo contra central viejo rompe conceptos y el select | Central primero, en los dos PRs |
| A-P6 | A | Falta `numero` en `LiquidacionConceptoInput.java` | Agregado al plan |
| B-2 / B-6 | B | Numeración distinta por ambiente; CI no valida la migración | Consulta de solo lectura obligatoria antes del deploy; prueba arrancando el central local |
