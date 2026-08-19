# Bugs de testeo — sesión 2026-08-18

Registro de errores encontrados probando el módulo RRHH en desktop (rama `develop`)
contra central local. **Sin investigar todavía** — solo captura del síntoma.

Estados: 🔴 abierto · 🟡 en análisis · 🟢 corregido

## Índice

| # | Título | Sev |
|---|---|---|
| B1 | Ajuste salario mínimo global — `Integer cannot be cast to Long` al guardar | 🔴 |
| B2 | Mismo diálogo — "Sueldo actual" muestra `1`/`2`, "Cargo" vacía | 🔴 |
| B3 | Mismo diálogo — dice 15 seleccionados, lista 10 filas | 🔴 |
| B4 | No existe CRUD de cargos en desktop (backend listo) | 🔴 |
| B5 | Toggle IPS en `false` igual descuenta en liquidación mensual | 🔴 |
| B6 | No se puede eliminar un ítem automático del borrador | 🔴 |
| B7 | Recibo: total sin descuentos en la firma + 2 vías + línea punteada | 🟠 |
| B8 | Recibo: header con empresa emisora + "Recibí de … la suma de …" | 🟠 |
| B9 | Liquidación toma el salario anterior (probable consecuencia de B1) | 🔴 |
| B10 | Penalización automática: solo un día, falta rango de fechas | 🟠 |
| B11 | Amonestaciones/advertencias sin monto + contador + acta firmable | 🟠 |
| B12 | Ítem manual: select de operación (hoy todo sale "AJUSTE") | 🟠 |
| B13 | Penalizaciones consolidadas en un ítem, sin detalle | 🔴 |
| B14 | Aguinaldo sobre último sueldo, no sobre promedio percibido (INVESTIGADO) | 🔴 |
| B15 | IPS del finiquito: sin prorrateo por días ni vacaciones en la base | 🔴 |
| F1 | Ajuste de saldo en cuentas bancarias (solo existe en caja) | 🟠 |
| F2 | Pagar liquidación/finiquito desde el hub de egreso de Caja Mayor | 🟠 |
| F3 | Retiro de PDV: recepción parcial + selección de monedas | 🟠 |
| F4 | Todo pago figura "Pago Proveedor"; usar `origenTipo` para el label | 🔴 |
| F5 | Premisa: pagos de caja mayor solo desde la caja mayor (ocultar en RRHH) | 🟠 |
| **ACL** | **Acceso por caja: lista de usuarios R/W por caja virtual** — plan en `financiero/PLAN-ACL-CAJAS-VIRTUALES.md` | ⭐ **1ª** |

### Causas raíz agrupadas

1. **El sueldo se lee siempre del valor actual y es `Float`.** `FuncionarioSalarioHistorico` se
   escribe pero nadie lo lee para calcular. → **B14, B9**, sospecha en **B1/B2**.
2. **Los ítems automáticos no guardan referencia a su origen** ni se pueden editar. → **B6, B13**.
3. **El recibo no identifica a la empresa ni respeta el formato legal.** → **B7, B8**.
4. **Backend listo, falta UI** (quick wins): **B4** (`saveCargo`), **F1** (`AJUSTE_POSITIVO/NEGATIVO`),
   **F2** (`pagar(id, cajaVirtualId)`), **F4** (`origenTipo` ya persistido).

---

## B1 🔴 Actualización de salario mínimo global — `Integer cannot be cast to Long` al guardar

**Dónde:** Configuración RRHH → salario mínimo global → diálogo *"Funcionarios por debajo del nuevo mínimo"* → botón **Ajustar seleccionados (15)**.

**Síntoma:** el mutation falla:

```
[GraphQL error]: Message: Exception while fetching data (/data) :
class java.lang.Integer cannot be cast to class java.lang.Long
(java.lang.Integer and java.lang.Long are in module java.base of loader 'bootstrap'),
Location: [object Object], Path: data
```

**Efecto:** ningún ajuste salarial se persiste.

---

## B2 🔴 Mismo diálogo — columna "Sueldo actual" muestra `1` / `2` y "Cargo" viene vacía

**Dónde:** el mismo diálogo *"Funcionarios por debajo del nuevo mínimo"*.

**Síntoma:** de 10 filas visibles, 8 muestran `1` o `2` en **Sueldo actual** y la columna
**Cargo** está vacía en todas. Solo 2 filas traen sueldo plausible
(GABRIEL FRANCO AREVALOS 2.800.000, PAMELA STASHAKI 2.900.000).

Consecuencia directa: la columna **Diferencia** calcula sobre ese valor y muestra
`+3.043.999` / `+3.043.998` (mínimo nuevo 3.044.000 − 1 o − 2).

**Sospecha (sin verificar):** desalineación de columnas — el valor de `cargo` cayendo en
la celda de sueldo, o `sueldo` llegando null y renderizando otro campo.

---

## B3 🔴 Mismo diálogo — el botón dice 15 seleccionados pero la lista muestra 10 filas

**Síntoma:** **"Ajustar seleccionados (15)"** con solo 10 funcionarios listados y sin
paginador visible. No se puede ver ni deseleccionar los 5 restantes.

**Sospecha (sin verificar):** lista truncada en el render, o paginador faltante.

---

## B4 🔴 No existe acceso a creación/edición de cargos en desktop

**Dónde:** ningún lado — el CRUD de cargos no está construido en el frontend.

**Estado por capa:**

| Capa | Qué hay |
|---|---|
| central | Completo: `empresarial/cargo.graphqls` expone `saveCargo(cargo: CargoInput!)` y `deleteCargo(id)` |
| desktop | Solo lectura: `app/modules/empresarial/cargo/` tiene `cargo.model.ts`, `cargo.service.ts` (`onGetAll` / `onSearch`) y queries `Cargos` / `CargosSearch` / `cargo(id)`. Sin componentes, sin mutation, sin entrada de acceso |
| mobile / mobile-pwa | Nada (`saveCargo` no aparece) |

Sus hermanos de `empresarial` sí tienen UI: `sector/` (`list-sector` + `adicionar-sector-dialog`),
`sucursal/` (`list-sucursal` + `edit-sucursal-dialog`), `zona/` (`adicionar-zona-dialog`).
Cargo se quedó sin la capa de UI.

**Efecto:** los cargos solo se pueden crear por SQL. RRHH puede *asignarlos*
(`CambioCargoDialogComponent` → `cargoService.onSearch()`) pero no crearlos.

**Dónde iría el acceso:** los CRUDs de `empresarial` no cuelgan del menú lateral ni de rutas,
se abren desde el **buscador global** — array `componenteList` en
`app/shared/widgets/search-bar-dialog/search-bar.service.ts:47`. Falta la entrada análoga a:

```ts
{ title: 'Lista de sectores', component: ListSectorComponent, visibilityRoles: [ROLES.ADMIN] },
```

**Piezas a construir (patrón de sector):** mutation `SaveCargo` en `cargo/graphql/`,
`list-cargo` + `adicionar-cargo-dialog`, y la entrada en `componenteList`.

---

## B5 🔴 Legajo — toggle de IPS en `false` igual genera descuento de IPS en la liquidación mensual

**Dónde:** Legajo del funcionario → toggle de IPS desactivado → liquidación mensual del período.

**Síntoma:** la liquidación arrastra el descuento de IPS aunque el funcionario tenga el
toggle en `false`. La bandera del legajo no se respeta al armar el borrador.

**Efecto:** neto mal calculado (de menos) para todo funcionario sin IPS.

---

## B6 🔴 Liquidación mensual — no se puede eliminar un ítem generado automáticamente

**Dónde:** Liquidaciones → generar borrador de liquidación mensual → detalle del borrador.

**Síntoma:** los ítems que el motor arma solos (vales, cuotas de préstamo, bonos, aguinaldo,
horas extra, penalizaciones) no tienen forma de quitarse del borrador. Solo se pueden agregar
o editar manuales.

**Efecto:** si el motor arrastra un ítem que no corresponde (ver B5 con IPS), no hay salida por
UI — hay que corregir el dato de origen y regenerar el borrador, o tocar la DB.

**Pedido:** evaluar agregar un **icono de eliminar por fila** en la tabla de ítems del borrador,
habilitado también para los automáticos.

**A analizar antes de implementar** (no investigado todavía):
- ¿el borrador es reversible? Si se elimina la cuota de un préstamo, ¿qué pasa con el estado de
  la cuota al pagar la liquidación (cuota→PAGADA, vale→DESCONTADO)?
- ¿regenerar el borrador vuelve a insertar el ítem eliminado? Si sí, hace falta marca de
  exclusión, no borrado físico.
- ¿debe quedar rastro (auditoría / motivo) de quién quitó un ítem automático?
- ¿gating por rol — `RRHH LIQUIDAR` alcanza o hace falta uno más alto?

---

## B7 🟠 MEJORA — Recibo de liquidación mensual: mostrar total recibido sin descuentos y duplicar el bloque de firma (2 vías)

**Dónde:** documento PDF de la liquidación mensual — plantilla candidata
`central/src/main/resources/reports/recibo-liquidacion.jrxml` (confirmar cuál usa el flujo mensual;
también existen `recibo-rrhh.jrxml`, `recibo-ticket-58/80.jrxml`).

**Pedido (3 cambios):**

1. En la **descripción del sector de firma del funcionario**, que figure el **total recibido sin
   descontar nada** (bruto / suma de haberes), no el neto.
2. **Duplicar** al final del documento el bloque completo: *total recibido · descontado · final ·
   descripción · firma* — para tener **2 vías** (una queda para el funcionario, la otra para la
   empresa).
3. **Línea punteada** de corte entre las dos vías.

**A definir:** si el cambio aplica también a la versión **Ticket ESC/POS** (58/80 mm) o solo al
PDF A4 — en ticket la doble vía se resuelve normalmente imprimiendo dos veces, no duplicando el
cuerpo.

---

## B8 🟠 MEJORA — Recibo de liquidación: header con datos de la empresa emisora + descripción en primera persona

**Dónde:** misma plantilla que B7 (`recibo-liquidacion.jrxml` y las variantes de recibo RRHH).

**Pedido (2 cambios):**

1. **Header:** agregar los datos de la **empresa emisora** (razón social, RUC, dirección,
   teléfono — definir el set exacto). Hoy el recibo no identifica quién paga.
2. **Descripción:** redactarla como declaración del funcionario, nombrando a la empresa.
   Formato pedido:

   > *"Recibí de la Franco Arevalos S.A. la suma de ......... por concepto de ........."*

   Se combina con B7: el monto de esa frase es el **total recibido sin descuentos**.

**A definir:**
- ¿de dónde sale la empresa emisora — entidad `Empresa`/`Sucursal` del central, o parámetro de
  configuración RRHH? Si el recibo se emite por sucursal, ¿los datos son de la sucursal o de la
  razón social única?
- ¿el header va también en la versión ticket 58/80 mm (ancho limitado)?
- si son 2 vías (B7), el header, ¿se repite en la segunda vía o va una sola vez arriba?

---

## B9 🔴 Liquidación mensual — en algunos casos toma el salario anterior y no el nuevo

**Dónde:** Liquidaciones → generar borrador mensual, sobre funcionarios con cambio de salario.

**Síntoma:** el haber de sueldo sale con el **salario anterior** en lugar del vigente. No pasa
siempre — solo en algunos casos.

**Efecto:** neto mal calculado. Grave, porque es plata pagada de menos (o de más) sin que salte
ningún error.

**Pistas a chequear** (sin investigar todavía):
- ¿el motor lee `funcionario.salario` o el **histórico salarial** vigente al período? Si lee el
  histórico, ¿compara por `fechaDesde <= fin del período` o por fecha de creación del registro?
- relación con **B1/B2**: los ajustes por salario mínimo global no persisten y la lista muestra
  sueldos basura — ¿el histórico salarial quedó inconsistente para esos funcionarios?
- ¿el borrador ya generado antes del cambio de salario se recalcula al regenerar, o queda con el
  valor viejo congelado?
- identificar **qué funcionarios** lo reproducen y qué tienen en común (cambio de salario dentro
  del propio período vs. antes del período).

---

## B10 🟠 MEJORA — Penalizaciones: la generación automática solo acepta un día, hace falta rango de fechas

**Dónde:** RRHH → Penalizaciones → generación automática (por tardanzas/ausencias sobre marcaciones).

**Síntoma:** el selector permite elegir **un solo día**. Para cubrir un período hay que correr el
proceso día por día.

**Pedido:** permitir **rango de fechas** (desde / hasta), típicamente el período de liquidación
completo en una sola corrida.

**A definir:**
- ¿genera **una penalización por día** dentro del rango, o una consolidada por el rango?
  (afecta el detalle del recibo y el arrastre a la liquidación)
- **idempotencia:** si el rango se solapa con una corrida anterior, ¿saltea los días ya
  penalizados o duplica? Hoy con un día suelto el riesgo es bajo; con rango se vuelve crítico.
- ¿respeta feriados y justificativos ya cargados en todo el rango?
- ¿preview antes de confirmar (lista de lo que va a generar) o generación directa?

---

## B11 🟠 FEATURE — Penalizaciones: amonestaciones / advertencias (sin monto), acumulador y documento firmable

**Dónde:** RRHH → Penalizaciones. Feature nuevo, no es un fix.

**Pedido (3 partes):**

1. **Generar amonestaciones de advertencia** como tipo de penalización **sin descuento en dinero**
   — hoy toda penalización termina en un monto que descuenta la liquidación.
2. **Registrar y acumular la cantidad de advertencias** por funcionario (contador consultable,
   visible en el legajo / dashboard).
3. **Documento firmable de advertencia** — recibo/acta imprimible que el funcionario firma,
   mismo patrón que los 7 recibos existentes (PDF A4 + evaluar ticket).

**A definir:**
- ¿entidad nueva o un campo `tipo` en `Penalizacion` (`DESCUENTO` / `ADVERTENCIA`)? Si es lo
  segundo, hay que garantizar que las de tipo advertencia **nunca** entren al borrador de
  liquidación.
- **escalamiento:** ¿N advertencias acumuladas disparan algo (penalización con monto, aviso,
  bloqueo)? ¿el contador se resetea por año / por período?
- contenido del acta: motivo, fecha del hecho, descripción, quién la emite, número de advertencia
  ("2ª advertencia"), espacio de firma de funcionario y de la empresa.
- ¿el funcionario puede **negarse a firmar** — hace falta registrar ese estado?
- plantilla nueva `.jrxml` (aplican los mismos cambios de header/empresa de B8).

---

## B12 🟠 MEJORA — Liquidación mensual: el ítem manual necesita un select de tipo de operación (hoy sale todo como "AJUSTE")

**Dónde:** Liquidaciones → detalle del borrador mensual → formulario *agregar ítem*.

**Síntoma:** todo ítem cargado a mano se emite como **AJUSTE** en el documento. El recibo no
explica qué se pagó o descontó, y el signo (haber vs descuento) se elige aparte.

**Pedido:**
- **select con una lista de operaciones** en el formulario de agregar ítem.
- que la operación elegida **determine sola si es haber o descuento** — sin campo separado para
  el signo.
- que en el documento generado figure el **nombre de la operación**, no "AJUSTE".

**A definir:**
- ¿lista **parametrizable** (catálogo tipo motivos de vale, editable desde Configuración RRHH) o
  **enum fijo** en código? Recomendable catálogo: cada entrada con nombre, naturaleza
  (`HABER`/`DESCUENTO`) y activo/inactivo.
- ¿se deja "AJUSTE" como entrada genérica para lo que no encaje, o desaparece?
- **retrocompatibilidad:** los ítems ya guardados como AJUSTE — ¿migran a una operación por
  defecto o se dejan como están?
- ¿la naturaleza es fija por operación o admite override manual (con confirmación)?
- ¿aparecen agrupados por naturaleza en el recibo (haberes arriba, descuentos abajo)?

---

## B13 🔴 Liquidación mensual — las penalizaciones se consolidan en un solo ítem y no se sabe de qué se tratan

**Dónde:** Liquidaciones → borrador mensual → ítems de descuento por penalización.

**Síntoma:** todas las penalizaciones del período colapsan en **un único ítem consolidado**. Ni en
la UI ni en el recibo se puede saber qué penalizaciones lo componen.

**Efecto:** el funcionario firma un descuento que no puede auditar, y RRHH no puede justificarlo
sin ir a la DB. Agravado por B6: el ítem consolidado tampoco se puede eliminar.

**Pedido:**
- **un ítem por penalización** (no uno consolidado).
- la **observación** de cada ítem con el formato **`tipo: descripción`**
  (ej. `TARDANZA: 35 min del 2026-07-08`).

**A definir:**
- ¿aplica el mismo criterio a los otros grupos automáticos (vales, cuotas de préstamo, bonos,
  horas extra)? ¿o solo penalizaciones? Si el recibo queda muy largo, evaluar detalle por ítem en
  UI + agrupado en el PDF (decidir explícitamente, no por accidente).
- **trazabilidad:** cada ítem debería guardar el `penalizacionId` de origen — necesario para B6
  (borrado selectivo) y para reversar efectos al pagar.
- se combina con **B12**: el tipo de penalización debería ser una operación del catálogo, así el
  documento muestra el nombre real en vez de "AJUSTE".
- ¿las advertencias sin monto de **B11** quedan fuera de la liquidación (sí) pero visibles en algún
  anexo informativo del recibo?

---

## B14 🔴 Aguinaldo — se calcula sobre el último sueldo, nunca sobre el promedio de lo percibido (INVESTIGADO)

**Reportado como:** "el aguinaldo debe ser el promedio del total recibido mes a mes; parece que
cuando el funcionario no tiene historial toma el último salario".

**Hallazgo — el matiz del historial no existe: NUNCA mira historial.** El cálculo toma siempre el
sueldo actual del funcionario, tenga historial salarial o no.

`AguinaldoService.calcularAguinaldosAnio()` (`service/rrhh/AguinaldoService.java:83`):

```java
BigDecimal sueldo = new BigDecimal(f.getSueldo().toString());   // <-- sueldo ACTUAL
BigDecimal monto = AguinaldoCalculator.calcularMonto(sueldo, mesesDevengados);
```

`AguinaldoCalculator.calcularMonto()` = `sueldo × meses / 12`. La única entrada de dinero es ese
sueldo puntual: no hay promedio, no hay suma de percibido, no se leen las liquidaciones del año.

**Alcance del problema — es transversal, no solo aguinaldo.** `FuncionarioSalarioHistorico`
(con `fechaVigencia` + `salarioNuevo`) **se escribe pero nadie lo lee para calcular**: solo lo
consume `FuncionarioRrhhGraphQL.funcionarioSalarioHistoricos()` para mostrarlo en el legajo.
Todos los cálculos de dinero de RRHH leen `f.getSueldo()` (valor actual, puntual):

| Servicio | Línea |
|---|---|
| `AguinaldoService` | :83 |
| `LiquidacionSueldoService` | :198 |
| `LiquidacionFinalService` | :167, :240, :483, :518 |
| `VacacionService` | :237-238 |
| `HoraExtraService` | :67-69 |
| `ReporteRrhhService` | :168 |

**Consecuencias:**
- Si hubo **aumento** durante el año, el aguinaldo paga **de más**; si hubo baja, **de menos**.
- No entra al promedio nada de lo demás percibido (horas extra, bonos, comisiones), que por ley
  cuenta como remuneración.
- **Relación con B9:** que la liquidación "tome el salario anterior" es coherente con esto — el
  motor lee `f.getSueldo()`, así que si el ajuste no se persistió (B1) el valor viejo es el que
  queda. B9 puede ser consecuencia de B1, no un bug independiente.

**Hallazgo colateral 🔴 `Funcionario.sueldo` es `Float`** (`domain/personas/Funcionario.java:57`,
columna `numeric` en DB). Dinero en punto flotante binario: todo cálculo de RRHH arranca de un
`new BigDecimal(float.toString())`. Candidato a los descuadres de redondeo y sospechoso también de
B1/B2 (mezcla de tipos numéricos).

**A definir antes de corregir:**
- **fórmula legal exacta:** aguinaldo = total percibido en el año calendario / 12. Equivale a
  (promedio mensual × meses trabajados) / 12 — hay que fijar cuál se implementa y qué conceptos
  entran al "percibido" (¿solo sueldo? ¿+ HE, bonos, comisiones?).
- **fuente del percibido:** ¿sumar las `LiquidacionSueldo` pagadas del año (real, pero rompe si
  falta algún mes cargado), o reconstruir desde `FuncionarioSalarioHistorico` por
  `fechaVigencia` (siempre disponible, pero ignora variables)?
- **funcionarios sin historial ni liquidaciones** (los nuevos): fallback al sueldo actual —
  documentarlo como decisión, no dejarlo implícito.
- ¿se **recalculan** los aguinaldos ya `CALCULADO` del año en curso, y qué pasa con los
  `APROBADO`/`PAGADO` que quedaron con el monto viejo congelado?
- migrar `Funcionario.sueldo` a `BigDecimal` (toca las 6 clases de la tabla + GraphQL + desktop).

---

## B15 🔴 Liquidación final — el IPS no es proporcional a los días trabajados ni incluye vacaciones causadas/proporcionales en la base

**Dónde:** Liquidación final / finiquito → descuento automático de IPS.

**Comportamiento actual** (`service/rrhh/LiquidacionFinalService.java:209-314`):

```java
BigDecimal ipsBase = in.getIpsBase() != null ? in.getIpsBase() : salarioPromedio;   // :209
...
BigDecimal ips = base.multiply(ipsPct).divide(new BigDecimal("100"), 0, HALF_UP);   // :314
```

O sea: **base = salario promedio mensual completo** (o el override manual del diálogo), por el
`IPS_PORCENTAJE_FUNCIONARIO` (default 9%). No prorratea por días trabajados en el mes de egreso, y
no suma las vacaciones al base.

**Comportamiento requerido:**

1. **Proporcional a los días trabajados** del mes de egreso — no el mes completo.
2. La **base** debe ser la suma de:
   - **sueldo** (la parte proporcional trabajada),
   - **vacaciones causadas** — las ya vencidas / devengadas y no gozadas del funcionario,
   - **vacaciones proporcionales** — las devengadas del período en curso, que todavía no llegaron
     a la fecha en que podían usarse.

**A definir:**
- **divisor del prorrateo:** ¿30 días fijos, o días reales del mes de egreso? (fijarlo, porque
  cambia el monto).
- ¿el override manual `ipsBase` del diálogo se mantiene como escape, y qué muestra por defecto —
  la base ya calculada con las 3 partes?
- ¿el aguinaldo entra o no a la base de IPS? (no está en el pedido — confirmar con contabilidad,
  porque suele estar exento).
- se combina con **B5**: hay que respetar `ipsActivo=false` acá también (`:238` usa
  `!Boolean.FALSE.equals(f.getIpsActivo())`, revisar que el default no fuerce el descuento).
- ¿el recibo de finiquito debe **desglosar** la base del IPS (sueldo + causadas + proporcionales)
  o mostrar solo el monto? Empalma con B13 (trazabilidad de descuentos).

---

# Módulo Financiero

## F1 🟠 MEJORA — Ajuste de saldo en cuentas bancarias (hoy solo existe para caja)

**Dónde:** Financiero → cuentas bancarias. El ajuste existe únicamente en el hub de ingreso/egreso
de **caja virtual**.

**Situación actual:**
- Hub de caja: `registrar-ingreso-dialog.component.ts:29` ofrece
  `{ tipo: 'AJUSTE', titulo: 'Ajuste de Saldo', descripcion: 'Corrección positiva del saldo con motivo' }`
  → genera `CajaVirtualTipoMovimiento.AJUSTE` (`:55`). Idem en el hub de egreso.
- **Backend bancario ya soporta el ajuste:** `MovimientoBancarioTipo` tiene `AJUSTE_POSITIVO` y
  `AJUSTE_NEGATIVO`, y el dashboard de caja virtual ya los sabe renderizar
  (`caja-virtual-dashboard.component.ts:86-96` → "Ajuste +" / "Ajuste −" con color).
- **Falta la UI** en `cuenta-bancaria/` (hoy solo `add-cuenta-bancaria-dialog`) para emitirlos.
  Verificar si el resolver/mutation de movimiento bancario acepta esos tipos o si también falta ahí.

**Pedido:** poder ajustar el saldo de una cuenta bancaria, con el mismo criterio que el ajuste de
caja (positivo/negativo + motivo obligatorio).

**A definir:**
- ¿desde dónde se dispara — un hub de ingreso/egreso propio de la cuenta bancaria, o un botón
  "Ajustar saldo" en el detalle/lista de cuentas?
- **motivo obligatorio** y **gating por rol** (un ajuste de saldo bancario es plata creada de la
  nada: debería exigir rol alto y quedar auditado con usuario + fecha + motivo).
- ¿el ajuste se ingresa como **monto de corrección** (+/−) o como **saldo objetivo** y el sistema
  calcula la diferencia? La segunda es más natural para conciliar contra el extracto.
- ¿impacta conciliación bancaria / algún reporte que asuma que todo movimiento tiene contrapartida?

---

## F2 🟠 MEJORA — Pagar liquidación mensual y finiquito desde el hub de egreso de Caja Mayor (patrón "Pagar Compras" / "Pagar Gasto")

**Dónde:** Financiero → Caja Virtual/Mayor → hub de **Registrar Egreso**. Cruza Financiero ↔ RRHH.

**Situación actual:**
- El pago **ya existe del lado RRHH**, pero solo desde RRHH:
  `LiquidacionSueldoService.pagar(id, cajaVirtualId)` (`:457`) y
  `LiquidacionFinalService.pagar(id, cajaVirtualId)` (`:561`).
- El hub de egreso (`registrar-egreso-dialog.component.ts:26-32`) ya expone el patrón pedido:
  `PAGO_CPP` → "Pagar Compras" (con `pagar-compras-dialog`), `GASTO` → "Pagar Gasto",
  `VALE` → "Registrar Vale". **No hay entrada para liquidaciones.**

**Pedido:** agregar al hub de egreso las opciones para **pagar liquidación mensual** y **pagar
finiquito**, con el mismo flujo que compras/gastos: elegir de una lista de pendientes → confirmar →
egreso de caja + cambio de estado.

**A definir:**
- ¿una sola entrada "Pagar Liquidaciones" con tabs mensual/finiquito, o dos entradas separadas?
- **pago múltiple:** `pagar-compras-dialog` permite seleccionar varias solicitudes. ¿Se puede pagar
  la nómina de varios funcionarios en un solo egreso, o uno por uno? Si es múltiple, ¿un movimiento
  de caja consolidado o uno por funcionario? (afecta la trazabilidad y el recibo).
- **filtro de estado:** solo liquidaciones aprobadas/pendientes de pago; no mostrar borradores.
- **reutilizar la mutation existente** (`pagar(id, cajaVirtualId)`) en lugar de duplicar lógica —
  ahí ya viven los efectos cruzados (vale→DESCONTADO, cuota→PAGADA).
- ¿el hub ofrece imprimir el recibo al terminar, como se hace hoy desde RRHH?
- **gating por rol:** el operador de caja no necesariamente tiene rol RRHH. Definir si pagar desde
  el hub exige `RRHH LIQUIDAR` o alcanza el rol de caja (implica ver montos de sueldos —
  sensible, ver `seguridad-roles.md` de la skill rrhh-expert).

---

## F3 🟠 MEJORA — Ingreso de retiro de PDV a Caja Mayor: recepción parcial y selección de monedas a recibir

**Dónde:** Caja Mayor → hub Registrar Ingreso → "Ingresar Retiro de PDV"
(`caja-virtual/ingresar-retiro-caja-mayor-dialog/`).

**Situación actual — es todo o nada:** se tildan retiros de una lista y se llama
`retiroService.onIngresarACajaMayor(r.id, r.sucursalId, caja.id)` por cada uno
(`ingresar-retiro-caja-mayor-dialog.component.ts:150-153`). La mutation **no recibe monto ni
moneda**: entra el retiro completo con sus 3 monedas juntas. El diálogo solo *muestra* el desglose
(`formatMontos()` → `retiroGs` / `retiroRs` / `retiroDs`) y acumula totales por moneda, pero no
permite elegir.

**Pedido:**
1. **Recepción parcial** — poder recibir menos que el total del retiro (lo que efectivamente llegó
   en el maletín).
2. **Selección de monedas** — elegir cuáles de Gs / R$ / US$ se reciben, y cuánto de cada una.

**A definir (esto es lo que hace el cambio no trivial):**
- **¿qué pasa con el remanente?** Un retiro parcialmente recibido no puede quedar "cerrado". Opciones:
  (a) queda flotante por el saldo, (b) se genera una **diferencia** con destino
  (ver `DiferenciaDestinoTipo`, que ya existe), (c) queda en estado `PARCIAL` nuevo.
  **Decidir esto primero** — condiciona el modelo de datos.
- **estado del retiro:** hoy `EstadoRetiro` probablemente no contempla parcial. Cambio de enum +
  migración + revisar todo lo que filtra "flotantes" (`RetiroService.findFlotantes`).
- **replicación:** el retiro nace en el filial/PDV y llega al central por replicación (ver
  `runbooks/replication.md`). Si el central marca recepción parcial, hay que definir si eso
  vuelve al origen o queda solo en central.
- ¿la Caja Mayor maneja las 3 monedas o solo Gs? Si es solo Gs, "seleccionar monedas" implica
  decidir qué pasa con R$/US$ (¿quedan flotando? ¿van a otra caja?).
- **auditoría:** quién recibió, cuánto declaró el PDV vs cuánto se recibió, y motivo de la
  diferencia. Es el punto donde aparecen los faltantes de efectivo — tiene que quedar registrado.

---

## F4 🔴 Caja Mayor — todo pago figura como "Pago Proveedor"; el tipo debería reflejar el concepto (Gasto / Compra / Vale / …)

**Dónde:** Caja Mayor → historial de movimientos y dashboard. Se ve al pagar un gasto: sale
**"Pago Proveedor"** en lugar de "Gasto".

**Causa (identificada, no corregida) — hay dos enums y la UI muestra el grueso:**

| Enum | Granularidad | Valores |
|---|---|---|
| `CajaVirtualTipoMovimiento` | grueso (**el que se muestra**) | `INGRESO`, `EGRESO`, `TRANSFERENCIA_ENTRADA/SALIDA`, `PAGO_PROVEEDOR`, `AJUSTE` |
| `OrigenMovimientoTipo` | **fino, ya existe y ya se persiste** | `GASTO`, `PAGO_CPP`, `RRHH_VALE`, `RRHH_PRESTAMO`, `RRHH_AGUINALDO`, `RRHH_LIQUIDACION_SUELDO`, `RRHH_LIQUIDACION_FINAL`, `RETIRO_CAJA`, `VENTA_CREDITO_COBRO`, `DEVOLUCION`, `CHEQUE`, `ACREDITACION_POS`, `MALETIN`, `ENTRADA_VARIA`, `OPERACION_FINANCIERA`, `MANUAL`, `ANULACION` |

`PagoProveedorService:298` setea `tipoMovimiento = PAGO_PROVEEDOR` y `:304` setea
`origenTipo = PAGO_CPP`. El dato fino **ya está guardado**; la UI simplemente no lo usa:
`historial-movimientos-caja-virtual.component.ts:46` y `caja-virtual-dashboard.component.ts:122`
mapean labels/colores desde `tipoMovimiento`.

**Pedido:** que el movimiento muestre el concepto real — "Gasto", "Compra", "Vale", "Liquidación",
"Aguinaldo", etc.

**Fix probable — cambio de UI, no de modelo:** mapear label y color desde **`origenTipo`**
(con fallback a `tipoMovimiento` cuando sea `MANUAL`), en los dos componentes. No hace falta
migración ni enum nuevo.

**A verificar:**
- que **todos** los flujos de pago seteen `origenTipo` correctamente. Confirmado en
  `PagoProveedorService`; **falta verificar el pago de gastos** — en `GastoTesoreriaService` /
  `GastoService` no aparecen `setTipoMovimiento` ni `setOrigenTipo`, así que hay que rastrear por
  dónde crea el movimiento (si va por `PagoProveedorService`, ahí está el porqué del label).
- el **filtro por tipo** del dashboard (`caja-virtual-dashboard.component.ts:113`) — si el label
  pasa a ser el origen, el filtro debería filtrar por origen también.
- `TesoreriaService:49` usa `tipo == PAGO_PROVEEDOR` como condición lógica: **no** tocar
  `tipoMovimiento` sin revisar ese punto (por eso conviene cambiar solo la presentación).

---

## F5 🟠 PREMISA — "Pagos desde caja mayor solamente estando en caja mayor": ocultar el pago desde las pantallas de RRHH

**Decisión tomada 2026-08-19.** Hoy hay 7 caminos para sacar plata de una caja mayor: el hub de
egreso, y 6 diálogos de RRHH que traen su propio selector de caja. Se unifica en el hub.

**Componentes que hoy pagan sin estar en la caja** (desktop, todos con selector de caja propio):

| Componente | Concepto |
|---|---|
| `rrhh/vale/confirmar-vale-dialog` | vale / adelanto |
| `rrhh/aguinaldo/pagar-aguinaldo-dialog` | aguinaldo (pago separado) |
| `rrhh/prestamo/edit-prestamo-dialog` | desembolso de préstamo |
| `rrhh/prestamo/prestamo-cuotas-dialog` | cobro de cuota (**ingreso**, no egreso) |
| `rrhh/liquidacion/liquidacion-detalle-dialog` | liquidación mensual |
| `rrhh/liquidacion-final/liquidacion-final-dialog` | finiquito |

Todos se alimentan de `rrhh/caja-virtual/graphql/CajaVirtualesActivas.ts` →
`cajaVirtualesActivas()`, la query de caja compartida entre Tesorería y RRHH.

> Corrección 2026-08-19: en una lectura anterior figuraba como "sin gate de seguridad". **Sí lo
> tiene**: exige un rol de Tesorería *o* uno de RRHH. Lo que no tenía era acotamiento por caja
> — eso lo resuelve el ACL, que ahora la filtra por las cajas visibles del usuario.

**Por qué:**
1. **Separación de funciones** — RRHH liquida y aprueba, tesorería paga. Que la misma persona
   liquide y saque la plata en la misma pantalla es lo que un control interno evita.
2. **Habilita el ACL por caja** (ver `financiero/PLAN-ACL-CAJAS-VIRTUALES.md` §7.5): con la premisa,
   RRHH **no necesita escritura sobre ninguna caja**. Sin ella habría que dársela a todo liquidador.
3. **Un solo camino auditable** en vez de siete.

**Huecos del hub a completar ANTES de ocultar** (si no, RRHH queda sin poder pagar):

| Concepto | Estado en el hub |
|---|---|
| Vale | ✅ egreso → "Registrar Vale" |
| Liquidación mensual / finiquito | ❌ → **F2** |
| Aguinaldo (pago separado) | ❌ falta |
| Desembolso de préstamo | ❌ falta |
| Cobro de cuota de préstamo | ❌ falta — va al hub de **ingreso** |

**Orden obligatorio:** completar el hub → recién después ocultar los selectores de RRHH.

**Alcance:** es cambio de UI. Las mutations (`pagarAguinaldo`, `pagarLiquidacion`,
`confirmarVale`, …) siguen expuestas en GraphQL — aceptable como paso intermedio; el ACL de cajas
las cubre cuando entre.

---

## F6 🔴 Pago consolidado — la descripción del movimiento muestra solo el primer documento, y el `origenTipo` es siempre PAGO_CPP

**Dónde:** cualquier pago desde el hub que seleccione **más de un** documento. Reportado con
gastos; aplica igual a compras y vales.

**Síntoma 1 — la etiqueta miente** (`PagoProveedorService.java:257-265`):

```java
SolicitudPago gastoSol = spById.values().stream()
    .filter(s -> s.getTipo() == TipoSolicitudPago.GASTO)
    .findFirst().orElse(null);          // <-- el PRIMERO
etiquetaPago = "#" + gastoSol.getId() + " - " + cat + " - " + benef + " - " + desc;
```

Se paga N gastos, el movimiento consolidado queda descrito como si fuera solo el primero.
**Los datos están bien**: cada documento tiene su fila en `PagoSolicitudDetalle`
(`pago_id`, `solicitud_pago_id`, `monto_solicitud`). Es la etiqueta, no el asiento.

Los vales ya lo tenían resuelto a medias (`:274` → `"Pago de vales (N)"`); a gastos y compras
nunca se les aplicó.

**Síntoma 2 — `origenTipo` incorrecto** (`:307`): todos los pagos por este motor postean
`OrigenMovimientoTipo.PAGO_CPP`, sea gasto, vale, compra o (ahora) liquidación. Con **F4** ya
aplicado, un pago de gasto se muestra como **"Compra"**. Regresión introducida por F4.

**Fix (3 partes):**

1. **`origenTipo` según el concepto real** del evento: `GASTO` para gastos, `RRHH_*` para los
   documentos de RRHH, `PAGO_CPP` para compras. Un evento no mezcla conceptos (el diálogo
   agrupa por modo), pero si llegara a mezclarse se cae a `PAGO_CPP`.
2. **Descripción por cardinalidad:** 1 documento → descripción específica de siempre;
   N documentos → `"Pago consolidado de N gastos"` (o vales / compras / liquidaciones).
   Generaliza lo que los vales ya hacían.
3. **Detalle navegable:** el dashboard ya tiene el registro genérico *"Ir al origen"*
   (`caja-virtual-dashboard.component.ts:371`, `origenNav`), que mapea cada `origenTipo` a su
   pantalla. Se agrega la entrada para los pagos → diálogo **"Detalle del pago"** que lista los
   documentos del evento con su monto imputado. El movimiento ya lleva
   `referenciaId = origenId = pago.id` y `PagoSolicitudDetalle` tiene todo por `pago_id`.

**Por qué no basta con cambiar el título:** el asiento tiene que seguir siendo **un** movimiento
consolidado (es lo contablemente correcto, y la anulación ya opera sobre el evento entero). El
desglose va aparte, leído de datos reales, no embutido en un string.
