# Plan B14 + B15 — base remunerativa del aguinaldo y del finiquito

> **Estado:** plan auditado (2 auditores independientes, 2026-08-21) y corregido con sus hallazgos.

**Dos PRs, en este orden:**

1. **`refactor/funcionario-sueldo-bigdecimal`** — solo `Float` → `BigDecimal`. Chico, verificable,
   sin cambios de fórmula. **Va primero y se mergea antes de empezar el segundo.**
2. **`feat/rrhh-base-remunerativa`** — las fórmulas. Sale de `develop` con el PR 1 ya mergeado.

Separarlos es hallazgo de auditoría, no gusto: mezclar un refactor de tipos con dos cambios que
mueven plata hace imposible aislar la causa si algo sale mal en producción.

---

## Decisiones del usuario (2026-08-21)

| Decisión | Valor |
|---|---|
| Fuente del "percibido" | Liquidaciones **APROBADA o PAGADA** del año calendario. Un `BORRADOR` no cuenta |
| Base del aguinaldo | **Remuneración total** — sueldo + horas extra + bonos + comisiones |
| Divisor del prorrateo de IPS | **30 días fijos** |
| ¿Aguinaldo en la base del IPS del finiquito? | **Sí** |
| Base del IPS del finiquito | **Base compuesta**, reemplazando el promedio de 6 meses actual |
| Indemnización contaminada por aguinaldo | **Se corrige en este mismo PR** |

Asumido, decir si se quiere distinto:

- No se tocan los aguinaldos `APROBADO` / `PAGADO`; solo se recalculan los `CALCULADO`. Ya es el
  comportamiento de `AguinaldoService:89-91`.
- Solo se recalcula el **año en curso**, no años anteriores.
- `VACACION_VENTA` arranca como **remunerativo** (es plata que el funcionario percibió). Se corrige
  con un `UPDATE` al catálogo, sin deploy — pero por ahora a mano, porque no hay ABM.

---

## Hallazgos de la investigación

### H1 🔴 Ya existen dos fórmulas de aguinaldo, y la del finiquito es la correcta

No hay que inventar la fórmula: hay que unificarla.

`LiquidacionFinalService.calcularAguinaldoProporcional()` (`:503-537`) ya hace Σ `total_haberes` de
las liquidaciones `APROBADA`/`PAGADA` del año / 12, con fallback a `sueldo × meses / 12`.
`AguinaldoService.calcularAguinaldosAnio()` (`:83-85`) usa el sueldo actual y puntual.

**Hoy, en producción, el mismo funcionario cobra un aguinaldo distinto según si se queda o si se
va.** Con un aumento a mitad de año o con variables, los dos números no coinciden.

Como el predicado confirmado (`APROBADA || PAGADA`) es el que el finiquito ya usa, **el finiquito no
cambia de monto** por este punto: solo se le extrae la fórmula a un lugar común.

### H2 🔴 `total_haberes` es un saco: incluye el propio aguinaldo

| Código HABER | ¿Remunerativo? | Por qué |
|---|---|---|
| `SALARIO_BASE`, `HORA_EXTRA`, `BONO_MANUAL`, `BONIFICACION` | sí | decisión: remuneración total |
| `AGUINALDO` | **no** | `LiquidacionSueldoService:322` lo emite como ítem HABER. Sumarlo mete el aguinaldo del año dentro de la base del aguinaldo del año |
| `VIATICO` | **no** | compensa un gasto, no retribuye trabajo |
| `REINTEGRO` | **no** | devolución de plata que el funcionario adelantó |
| `VACACION_VENTA` | sí (revisable) | |
| `HABER_MANUAL` | sí (default) | genérico histórico |

**El defecto alcanza a la indemnización, no solo al aguinaldo.** `calcularSalarioPromedio()`
(`:472-489`) alimenta `LiquidacionFinal.salarioPromedio`, que es la base de la **indemnización**, y
también suma `total_haberes` crudo. La indemnización de quien se va el mismo año que cobró aguinaldo
está inflada por ese aguinaldo. **Decisión del usuario: se corrige acá** (baja las indemnizaciones
de ese caso).

### H3 🟠 Las comisiones no existen como concepto de haber

El único `COMISION` del sistema es `PenalizacionTipo.COMISION_DESCUENTO`, que es un **descuento**.
Hoy una comisión solo entra como `BONO_MANUAL` o ítem manual genérico.

### H4 🔴 `Funcionario.sueldo` es `Float`

`domain/personas/Funcionario.java:57`, columna `numeric`. Todo cálculo de RRHH arranca con
`new BigDecimal(f.getSueldo().toString())`.

### H5 ✅ Replicación: seguro, pero la propiedad no está blindada

Ninguna tabla `rrhh.*` está en `central_pub` ni en `configuraciones.replication_table` — verificado
por los dos caminos. Agregar columnas a `rrhh.liquidacion_concepto` y `rrhh.aguinaldo` no puede
cortar el apply worker de ninguna filial.

**Pero `personas.funcionario` sí está publicada** (`V0:15110`, `MAIN_TO_ALL`, `REPLICA IDENTITY
FULL`), y `V154.0:20-27` ya le agregó 8 columnas de RRHH que replican. Este plan no le agrega
ninguna — solo cambia el tipo Java de `sueldo`, no el tipo de columna. **"RRHH es central-only" no
es una propiedad del sistema, es una convención**: cualquiera que agregue una columna a
`personas.funcionario` en vez de a `rrhh.*` la rompe sin darse cuenta.

### H6 ✅ El divisor 30 ya es la convención

`DIAS_MES_PROMEDIO` (default `30`) existe y `salarioPorDiasTrabajados()` (`:252-256`) ya divide por
él. No hay que cambiar el divisor: hay que **aplicarlo a la base del IPS**, que hoy no lo usa.

---

## Diseño

### D1 — `es_remunerativo` como dato, no como `switch`

**`V205.5__rrhh_concepto_es_remunerativo.sql`**:

```sql
ALTER TABLE rrhh.liquidacion_concepto
    ADD COLUMN IF NOT EXISTS es_remunerativo BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE rrhh.liquidacion_concepto SET es_remunerativo = false
 WHERE codigo IN ('AGUINALDO', 'VIATICO', 'REINTEGRO');

INSERT INTO rrhh.liquidacion_concepto (codigo, descripcion, es_haber, es_calculado_auto, es_remunerativo, activo, creado_en)
SELECT 'COMISION', 'COMISION', true, false, true, true, now()
WHERE NOT EXISTS (SELECT 1 FROM rrhh.liquidacion_concepto WHERE codigo = 'COMISION');
```

Aditiva y reversible a mano sin pérdida de datos.

> **Riesgo reintroducido, y la mitigación que yo planeé no existe.** El default `TRUE` significa
> que un concepto nuevo cargado sin marcar `es_remunerativo = false` entra a la base del aguinaldo
> y del IPS **sin que nadie lo note** — el mismo patrón de falla silenciosa que motivó este plan.
>
> Yo había escrito que el ABM de conceptos lo pediría en el alta. **Ese ABM no existe**: el lote B
> agregó una query de lectura (`ConceptosParaItemManual.ts`) y nada más. Hoy el catálogo solo se
> edita por SQL. Queda como **follow-up obligatorio antes de que alguien agregue conceptos nuevos**,
> y mientras tanto el que agregue uno tiene que correr el `UPDATE` a mano.

### D2 — Un único punto que responde "cuánto percibió"

`service/rrhh/builder/BaseRemunerativa.java` (puro, testeable sin Spring) + el método de consulta:

```java
/** Σ de los items HABER remunerativos de las liquidaciones APROBADA o PAGADA del anio. */
PercibidoAnual percibidoAnual(Long funcionarioId, int anio);
```

- `LEFT JOIN` por `codigo` (`liquidacion_item.codigo` → `liquidacion_concepto.codigo`; es `String`,
  no FK). Código ausente o `NULL` → cuenta como remunerativo, igual que el `DEFAULT TRUE`.
- **Una sola query agregada.** No un `findByCodigo` por ítem — la lección del N+1 de `operacion()`.
- Devuelve `{ total, mesesConLiquidacion, primerMes, ultimoMes }`, no solo el total: los otros
  campos son los que permiten detectar huecos (D3).

Consumidores:

- `AguinaldoService.calcularAguinaldosAnio()`
- `LiquidacionFinalService.calcularAguinaldoProporcional()` — se le borra su copia
- `LiquidacionFinalService.calcularSalarioPromedio()` — mismo filtro `es_remunerativo` (indemnización)

### D3 — Los dos montos del aguinaldo, y el snapshot

**`calcularAguinaldosAnio()` produce dos montos, no uno** — el plan anterior lo ignoraba:

| Campo | Hoy | Después |
|---|---|---|
| `montoCalculado` (devengado a la fecha) | `sueldo × mesesDevengados / 12` | `percibido / 12` |
| `montoProyectado` (proyección al 31/12) | `sueldo × mesesProyectados / 12` | `(percibido / mesesConLiquidacion) × mesesProyectados / 12` |

El proyectado **no puede** salir del percibido directo: no existen liquidaciones de meses futuros.
Se proyecta el promedio mensual real observado sobre los meses que faltan. Así los dos montos salen
de la misma fuente y la unificación no queda a medias.

**`percibido / 12` a mitad de año no es un monto "bajo": es exactamente lo devengado.** Que el mes
en curso todavía no esté liquidado es el ciclo normal de nómina, no una anomalía.

Campos nuevos en `rrhh.aguinaldo` (**`V206.5__rrhh_aguinaldo_trazabilidad_base.sql`** — el plan
anterior daba por existente `origenBase` sin migración, era inimplementable):

| Campo | Para qué |
|---|---|
| `origen_base` | `PERCIBIDO` / `SUELDO_ACTUAL` — de dónde salió el número |
| `meses_con_liquidacion` | cuántos meses se encontraron |
| `monto_anterior` | **el `montoCalculado` que había antes de este recálculo** |
| `recalculado_en` | cuándo |

`monto_anterior` es hallazgo de auditoría: recalcular **pisa** el monto y el rollback del JAR no
devuelve ese dato. Sin snapshot, un error en la fórmula nueva no se puede deshacer.

**Aviso de huecos, no de cola faltante.** La alerta se dispara cuando falta un mes **entre** el
primero y el último encontrados — un hueco real. Que falte el mes en curso no avisa nada, porque
sería ruido todos los meses.

### D4 — B15: la base del IPS

`LiquidacionFinalService:209` pasa de `salarioPromedio` (promedio de 6 meses de `total_haberes`) a:

```
ipsBase = salarioDelMes                      // (sueldo / 30) x dias trabajados
        + montoVacacionesNoGozadas           // causadas + proporcionales
        + aguinaldoProporcional
```

**Corrección respecto del plan anterior:** afirmaba que los tres valores ya estaban disponibles en
el preview. Es falso. `previewDefaults()` (`:225-248`) calcula **días** de vacaciones, no el monto
en guaraníes — eso sale de `LiquidacionFinalCalculator.calcular()`, que el preview no invoca. Hay
que agregar el cálculo monetario y extender `LiquidacionFinalPreview`. En `generarBorrador()` sí
están los tres antes de la línea 209.

El override manual `in.getIpsBase()` se mantiene como escape.

**Vacaciones causadas vs. proporcionales:** `calcularDiasVacacionesNoGozadas()` (`:493-501`) suma
`diasGenerados - diasGozados` de las `Vacacion` no prescritas — **ya incluye ambas** en un número.
Para el monto del IPS da igual; distinguirlas solo haría falta para desglosarlas en el recibo, y
queda fuera de alcance.

### D5 — `Float` → `BigDecimal` (PR 1, separado)

Alcance real, medido por auditoría — **el desktop probablemente no se toca**: `totalHaberes` y
`montoCalculado` ya son `BigDecimal` en Java y se exponen como `Float` en el schema sin que nadie
haya tocado el cliente. `funcionario.model.ts:16` ya trata `sueldo` como `number`.

En central, ~10 archivos. Seis puntos **no compilan** tal cual (los detiene el build, no son roturas
silenciosas):

| Archivo:línea | Qué |
|---|---|
| `FuncionarioRrhhService:80` | `BigDecimal.valueOf(f.getSueldo())` |
| `FuncionarioRrhhService:93` | `f.setSueldo(nuevoSalario.floatValue())` |
| `AjusteSalarioMinimoService:60` | `Float anterior = f.getSueldo();` |
| `AjusteSalarioMinimoService:65` | `anterior >= minimo.floatValue()` |
| `AjusteSalarioMinimoService:80` | `f.setSueldo(minimo.floatValue())` |
| `DashboardRrhhService:287` | `f.getSueldo() <= 0` |

Más `FuncionarioRepository.findConSueldoMenorA(Float)` (`:54`), que cambia de firma.

> **El punto frágil es el implícito:** `FuncionarioInput.sueldo` es `Float` y se mapea a la entidad
> con `ModelMapper` genérico (`FuncionarioGraphQL:91`). Esa conversión `Float`→`BigDecimal` **no la
> cubre ningún test**. Va con test propio antes de tocar nada más.

`Cargo.sueldoBase` queda `Float`: no se encontró asignación cruzada con `Funcionario.sueldo`.

---

## Alcance honesto: esto NO unifica todas las bases

El nombre del plan promete más de lo que entrega. Después de esto conviven:

| Base | Quién la usa | Estado |
|---|---|---|
| Percibido remunerativo | aguinaldo (anual y proporcional), IPS del finiquito, indemnización | **unificada acá** |
| `Funcionario.getSueldo()` puntual | `HoraExtraService:69`, `VacacionService:238` (venta de vacaciones) | **sin tocar** |

Queda declarado como cabo suelto conocido, no como olvido.

---

## Fases

**PR 1 — `refactor/funcionario-sueldo-bigdecimal`**

| # | Qué | Gate |
|---|---|---|
| 1 | Test del mapeo `FuncionarioInput` → entidad vía ModelMapper | test verde (antes de tocar el tipo) |
| 2 | `Float` → `BigDecimal` en entidad, repo, y los 6 puntos que no compilan | CI |

**PR 2 — `feat/rrhh-base-remunerativa`**

| # | Qué | Gate |
|---|---|---|
| 1 | `V205.5` + `es_remunerativo` en entidad y schema GraphQL | migración en dev |
| 2 | `V206.5` + los 4 campos de trazabilidad en `Aguinaldo` | migración en dev |
| 3 | `BaseRemunerativa` + query agregada + tests puros | tests |
| 4 | `AguinaldoService`: calculado **y** proyectado desde la misma fuente, con snapshot | recálculo en dev |
| 5 | `LiquidacionFinalService`: aguinaldo proporcional + `calcularSalarioPromedio` (indemnización) | tests |
| 6 | B15: base compuesta del IPS + monto de vacaciones en `previewDefaults` | tests |
| 7 | Desktop: aviso de huecos y base del IPS en el diálogo | AOT |

**Fuera de este PR, y hay que hacerlo:** el **ABM de conceptos de liquidación** no existe en el
desktop. Sin él, `es_remunerativo` solo se edita por SQL y el default `TRUE` deja pasar conceptos
nuevos sin que nadie lo decida. Es un PR propio, del tamaño del ABM de cargos del lote B, y depende
de que el lote B esté mergeado.

Commit + push por fase. **El gate real del backend es `gh pr checks`, no `mvnw verify` local** — los
tests de Jasper tardan de 2 a 10 minutos acá y `ActaAdvertenciaJrxmlTest` mata la VM forked por
presión de memoria; en CI el mismo `clean verify` corre en 1m36s.

---

## Antes de deployar

1. **Comunicar a RRHH que el criterio del aguinaldo cambió** antes de que alguien apriete
   "recalcular". El botón (`AguinaldoGraphQL.calcularAguinaldosAnio`) es manual y rutinario en
   diciembre; nadie va a notar por sí solo que la fórmula cambió por debajo.
2. **`reporteAguinaldoAnualBase64`** (`ReporteRrhhService:270-282`) lee `montoCalculado` persistido.
   Corrido antes y después de un recálculo da dos totales distintos para el mismo año. Si
   contabilidad ya cerró el período con el número viejo, hay discrepancia — por eso el reporte pasa
   a mostrar `recalculado_en`.
3. Revisar el catálogo de conceptos instancia por instancia: qué quedó `es_remunerativo`.
