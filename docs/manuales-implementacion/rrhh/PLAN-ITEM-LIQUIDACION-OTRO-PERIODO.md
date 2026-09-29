# PLAN — Ítem de liquidación para otro periodo

**Pedido (Franco, 2026-09-29):** al agregar un ítem manual en la liquidación de un funcionario,
poder elegir el **periodo** en que se aplica. Ej.: desde la liquidación de septiembre, cargar un
DESCUENTO UNIFORME de Gs. 300.000 para que salga en la de noviembre. Alcance elegido: **un solo
periodo, sin cuotas** (las cuotas ya las cubre el vale en cuotas, PR central #350).

Repos: **central** + **desktop**, rama `feature/rrhh-item-liquidacion-otro-periodo` en los dos.
Filial: `N/A para filial porque el schema rrhh es central-only [ev: V155.0; rrhh.* no está en
central_pub ni en replication_table]`. Mobile / mobile-pwa: `N/A, no liquidan`.

## Estado de hoy (develop 778d60eb)

- `agregarItemLiquidacion(liquidacionId, descripcion, monto, tipo, liquidacionConceptoId)` →
  `LiquidacionSueldoService.agregarItemManual`: el ítem queda atado a esa liquidación (`manual=true`),
  solo en `BORRADOR`. El signo sale del catálogo `liquidacion_concepto` (`esHaber`).
- La liquidación de un periodo futuro todavía no existe, así que no hay dónde colgar el ítem.
- `generarBorrador` preserva los ítems `manual=true` y recalcula los automáticos.

## Diseño

### Modelo

Tabla nueva `rrhh.liquidacion_item_programado`: `id`, `funcionario_id`, `periodo` (`YYYY-MM`),
`liquidacion_concepto_id` (nullable), `codigo`, `descripcion`, `monto`, `tipo`
(`HABER`/`DESCUENTO`), `estado` (`PENDIENTE`/`APLICADO`/`ANULADO`), `liquidacion_id` (la que lo
pagó), `liquidacion_final_id`, `origen_liquidacion_id` (desde dónde se cargó), `usuario_id`,
`creado_en`. Enum `LiquidacionItemProgramadoEstado` en Java **y** `.graphqls` en el mismo commit.

### Programar

- Mutation nueva `programarItemLiquidacion(liquidacionId, periodo, descripcion, monto, tipo,
  liquidacionConceptoId)`, `seg.requireAnyRole(seg.LIQUIDAR)` (mismo rol que agregar ítem).
  Mismas reglas que `agregarItemManual`: el signo sale del catálogo, descripción en mayúsculas.
  **El método viejo no se toca.**
- `periodo` tiene que ser **posterior** al de la liquidación de origen y a lo sumo 12 meses después.
  Mismo periodo → el desktop usa `agregarItemLiquidacion` como hoy.
- Si la liquidación del periodo destino ya existe:
  - `BORRADOR` → el ítem también se agrega ahí en el momento, **con el mismo constructor que
    `construirItemsAutomaticos`** (`manual=false`, `ITEM_PROGRAMADO`): si fuera `manual=true`, regenerar
    lo conservaría y además lo reconstruiría → duplicado (A-M4, B);
  - `APROBADA`/`PAGADA` → rechazo ("la liquidación de 2026-11 ya está APROBADA");
  - `ANULADA` → como si no existiera: `generarBorrador` la vuelve a borrador y ahí entra.

### Aplicar

- `construirItemsAutomaticos`: por cada programado `PENDIENTE` del funcionario con
  `periodo == liq.periodo` → ítem con el código y el tipo del programado, `manual=false`,
  `referenciaTipo = "ITEM_PROGRAMADO"`, `referenciaId = programado.id`. Al ser automático, regenerar
  el borrador lo recrea igual; no se duplica. **Se agregan antes de `disponibleParaConvenio(items)`**:
  si fueran al final, el tope del crédito por convenio ignoraría el programado y el neto podría quedar
  negativo (A-A1).
- **Los ítems `ITEM_PROGRAMADO` no se editan ni se eliminan** desde la liquidación (`editarItem` /
  `eliminarItem` rechazan: "anulalo desde Ítems programados"). Si se pudiera eliminar, al aprobar sin
  regenerar el programado quedaría `PENDIENTE` con un periodo que ya pasó, huérfano para siempre; si
  se pudiera editar, el pago se rechazaría por monto distinto. Para cambiar el monto: anular y volver a
  programar (A-M1, A-M2, B).
- `aplicarEfectosCruzados`: pagar → `APLICADO` + `liquidacion_id`; anular la liquidación →
  `PENDIENTE`. Validación antes de mover plata en los 4 lugares de siempre: el programado existe,
  está `PENDIENTE` y el monto coincide (mismo patrón que las cuotas de vale). `aplicar` y `anular`
  toman el programado con lock pesimista. Aplicado por el **mismo** documento → no-op.
- **Finiquito:** toma todos los `PENDIENTE` del funcionario, sin importar el periodo (se va y no hay
  liquidaciones futuras), **salvo los que ya están en una liquidación mensual no anulada** (si no,
  mensual y finiquito los cuentan dos veces y el segundo pago falla en pleno cierre). Concepto
  `MANUAL` con el **tipo del programado** (hoy `descItem` fija DESCUENTO: un HABER programado necesita
  su propio constructor). Referencia `ITEM_PROGRAMADO`, efectos cruzados con `liquidacion_final_id`.
  `liquidacion-final.graphqls` expone `referenciaTipo` del ítem para que el desktop diga PROGRAMADO
  (A-M5, A-B2, B).
- **Un HABER programado cuenta como remunerativo** (aguinaldo, promedio del finiquito) igual que un
  HABER manual de hoy, salvo que su concepto tenga `es_remunerativo=false`. El IPS mensual se calcula
  sobre `salarioBase` y no cambia (A-B1).

### Anular un programado

- Mutation `anularItemProgramado(id)`, rol LIQUIDAR.
- `APLICADO` o en una liquidación `APROBADA`/`PAGADA`/finiquito no anulado → rechazo.
- En un `BORRADOR` → se borra ese ítem y se recalculan los totales; el programado queda `ANULADO`.

### Consultar

Query `itemsProgramadosPorFuncionario(funcionarioId, estado)`, `seg.requireVer()`. La sección del
desktop marca **vencidos** los `PENDIENTE` con periodo ya pasado (funcionario inactivo, o periodo que
nadie liquidó): quedan visibles para anularlos o para que los tome el finiquito (A-M3).

### Desktop (`liquidacion-detalle-dialog`)

- Panel "Agregar ítem": select **Periodo** (el de la liquidación + los 12 siguientes; default el de
  la liquidación). Mismo periodo → `agregarItemLiquidacion` (como hoy); otro → `programarItemLiquidacion`
  y aviso "se aplicará en la liquidación de 2026-11".
- Sección nueva **"Ítems programados"** del funcionario (pendientes), con periodo, concepto, monto y
  **Anular** (con `LIQUIDAR`, flag en `ngOnInit`).
- Columna Origen: los ítems `ITEM_PROGRAMADO` dicen **PROGRAMADO** en vez de AUTO.

## Tabla de datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `liquidacion_item_programado.*` (funcionario, periodo, concepto, codigo, descripcion, monto, tipo, origen, usuario) | `LiquidacionItemProgramadoService.programar` ← `programarItemLiquidacion` ← panel del desktop | `construirItemsAutomaticos`, finiquito, `itemsProgramadosPorFuncionario` → sección "Ítems programados" |
| `estado`, `liquidacion_id`, `liquidacion_final_id` | efectos cruzados de mensual y finiquito, `anularItemProgramado` | validación antes de pagar, `construirItemsAutomaticos`, sección "Ítems programados" |

## Migración

`V234.1__rrhh_liquidacion_item_programado.sql`: solo `CREATE TABLE` + índice
`(funcionario_id, periodo, estado)`. `V233.1` la usa el PR del vale (#350, sin mergear): hay que
volver a verificar el número después de cualquier rebase. **Rollback — no es gratis (B).** El JAR anterior arranca, pero: regenerar un borrador borra los
ítems `ITEM_PROGRAMADO` (no son manuales) y pagar no toca el programado, que sigue `PENDIENTE`; de
vuelta al JAR nuevo, el finiquito lo cobraría otra vez. **Después del primer ítem programado, se
arregla hacia adelante.** Si igual hay que volver, antes:
`select id, funcionario_id, periodo from rrhh.liquidacion_item_programado where estado = 'PENDIENTE'`
y revisarlos a mano al volver.

## Fases

| # | Repo | Qué | Tests |
|---|---|---|---|
| 1 | central | Migración, entidad, repo, enum, servicio `programar`/`anular`/consulta, mutations y query con guard, integración con borrador existente | `LiquidacionItemProgramadoServiceTest`: periodo pasado o > 12 meses rechaza; destino APROBADA rechaza; destino BORRADOR recibe el ítem `manual=false`; destino ANULADA acepta; signo desde el catálogo; anular en BORRADOR borra el ítem; anular APLICADO rechaza; `SchemaEnumsSincronizadosTest` |
| 2 | central | `construirItemsAutomaticos` (antes del convenio), bloqueo de `editarItem`/`eliminarItem`, efectos cruzados con lock, validación en los 4 lugares, finiquito (con HABER, sin los que están en un mensual vivo), `referenciaTipo` en el ítem del finiquito | por `generarBorrador` real: programado para 2026-11 no sale en octubre, sale en noviembre, regenerar no duplica (tampoco el agregado en el acto); `generarLote` lo incluye; pagar → APLICADO; anular → PENDIENTE; aprobar → volver a borrador → regenerar no duplica; editar/eliminar el ítem rechaza; programado cambiado → pago rechazado; finiquito no toma el que está en un mensual vivo y sí un HABER |
| 3 | desktop | Select de periodo, sección "Ítems programados", origen PROGRAMADO | `npm run check`; prueba en UI |
| 4 | los dos | Docs de dominio y plan de testeo; borrar este plan | — |

Orden de PRs: **central primero**, desktop después (el desktop pide una mutation nueva).

## Prueba manual

1. Liquidación de septiembre de un funcionario (BORRADOR) → Agregar ítem → DESCUENTO, 300.000,
   Periodo **2026-11** → aviso "se aplicará en 2026-11"; el total de septiembre no cambia.
2. "Ítems programados" lo muestra PENDIENTE.
3. Generar octubre → no aparece. Generar noviembre → aparece como PROGRAMADO. Regenerar → una sola vez.
4. Pagar noviembre → APLICADO. Anular noviembre → vuelve a PENDIENTE.
5. Programar para un periodo cuya liquidación está APROBADA → rechazo.
6. Anular un programado pendiente → desaparece de "Ítems programados" y del borrador de su periodo.

## Qué queda sin verificar

- Dry-run de la migración contra una copia de la base real (paso 10).
- La reimpresión del recibo de origen no muestra nada del programado (no es parte de esa liquidación).
- **Conflicto textual con el PR #350** (mismos métodos: `construirItemsAutomaticos`, `pagar`,
  `aplicarEfectosCruzados`, finiquito, hub de tesorería): rebasar después de que se mergee #350,
  re-verificar el número de migración y correr la batería.
- **Anular una liquidación pagada 100% por banco/cheque** no revierte efectos cruzados (bug previo,
  pendiente aparte): el programado quedaría `APLICADO` para siempre. Franco decidió arreglarlo antes, en su propio PR.

## Auditoría del plan (paso 5)

Dos auditores sin verse, hallazgos verificados contra el código. Sin contradicciones entre ellos.

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| A-A1 | A | Programados agregados después del tope del convenio → neto negativo | Se insertan antes de `disponibleParaConvenio` |
| A-A2 / B | A, B | Anular liquidación pagada sin caja no revierte → programado APLICADO para siempre | Franco (2026-09-29): se arregla antes, en su propio PR (`fix/rrhh-anular-liquidacion-pago-bancario`); esta feature se rebasa encima |
| A-M1 / A-M2 / B | A, B | Eliminar o editar el ítem en el borrador lo deja huérfano o rechaza el pago | `editarItem`/`eliminarItem` rechazan `ITEM_PROGRAMADO` |
| A-M3 | A | Programado de un periodo que nadie liquida queda PENDIENTE | La sección marca vencidos; el finiquito los toma |
| A-M4 / B | A, B | Ítem agregado en el acto como manual se duplica al regenerar | `manual=false`, mismo constructor |
| A-M5 / B | A, B | El finiquito solo arma descuentos y contaría doble con un mensual vivo | Constructor con el tipo del programado; excluye los que están en un mensual vivo |
| A-B1 | A | Un HABER programado entra al aguinaldo/promedio | Documentado (igual que un HABER manual) |
| A-B2 | A | El finiquito no expone `referenciaTipo` | Se expone para el label PROGRAMADO |
| B | B | Rollback del JAR pierde el ítem y el finiquito cobra doble al volver | Rollback reescrito: arreglar hacia adelante |
| B | B | Destino ANULADA sin definir; falta lock | Definido; lock pesimista en aplicar/anular |
| A-4 / B-1 | A, B | Conflicto textual con #350; `V234.1` libre | Rebasar tras el merge de #350 |
