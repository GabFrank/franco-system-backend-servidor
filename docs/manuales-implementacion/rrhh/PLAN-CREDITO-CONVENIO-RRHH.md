# Plan — Cobro del crédito por convenio en RRHH (liquidación mensual + finiquito)

> Cierre de una deuda cross-módulo del MVP de RRHH: descontar en planilla las
> **compras a crédito** del funcionario (convenio de descuento). Dos flujos:
> **mensual** (cuotas vencidas) y **finiquito** (todo el saldo). Ambos unificados a
> nivel **cuota**, con un **puente** hasta que se finalice el módulo de Tesorería
> (Caja Mayor / Caja Virtual) — el próximo módulo después de RRHH.

## Contexto y alcance

- **Liquidación mensual** debe, cada fin de mes, incluir el cobro de las **cuotas
  vencidas** de las compras a crédito del funcionario. Es el **gemelo** del ítem
  `PRESTAMO_CUOTA` que ya existe (préstamos RRHH), pero sobre `financiero.venta_credito_cuota`.
- **Finiquito** ya descuenta el convenio (todo el saldo) pero **no lo salda** y
  además **sobre-cobra** (ver bug abajo). Se unifica al mismo mecanismo de cuota.
- **Tesorería aún no está consolidada.** No existe un flujo formal de "cobro de
  venta a crédito" (crear `Cobro`, a qué caja entra la plata, contrapartida
  contable). Eso llega con el módulo de Caja Mayor/Virtual. Por eso este plan es un
  **puente**: RRHH marca la cuota como cobrada-por-planilla y reduce el neto pagado;
  el asiento formal lo hará Tesorería después (ver §Puente).

### Decisiones de dominio (resueltas 2026-07-29)

1. **`INCOBRABLE`**: se cobra en planilla **igual que `ABIERTO`**. ✅
2. **Mensual**: cobro de convenio **siempre automático** (como `PRESTAMO_CUOTA`), pero
   con **tope por disponible + cobro parcial + carry-over** (ver §Cobro parcial). ✅
3. **Puente de Tesorería**: confirmado — marcado solo-RRHH sin `Cobro` es aceptable
   para el MVP; el asiento formal llega con Caja Mayor/Virtual. ✅
4. **`FINALIZADO` es una regla unificada, no "solo finiquito":** cualquier flujo
   marca el `VentaCredito` como `FINALIZADO` cuando queda **100% cobrado** (remanente 0
   en todas sus cuotas). El finiquito llega a cero casi siempre (cobra todo); la mensual
   llega a cero **solo al cobrar la última cuota** — en ese caso también flipa. En el
   caso común la mensual cobra cuotas sueltas y el crédito sigue abierto.

## Modelo financiero relevante (lo que hay hoy)

- `VentaCredito` (PK compuesta `id + sucursalId`): `valorTotal`, `saldoTotal`
  (**snapshot, nunca se muta** — grep = 0 escrituras), `estado`
  (`ABIERTO/FINALIZADO/EN_MORA/INCOBRABLE/CANCELADO`), `fechaCobro`.
- `VentaCreditoCuota` (PK `id + sucursalId`): `valor`, `parcial`, `activo`,
  `vencimiento`, y `cobro` (link a `Cobro`; **`null` = impaga**). No tiene estado
  ni montoPagado → "pagada" = `cobro != null`.
- "Cobrar/finalizar" un crédito = `estado=FINALIZADO` + `fechaCobro` (así lo hace
  `VentaCreditoGraphQL.finalizarVentaCredito` y el botón manual "cobrado"). **No**
  crea `Cobro` ni movimiento de caja. Las cuentas por cobrar filtran por **estado**.

### 🐞 Bug latente que este plan corrige
El finiquito actual descuenta `saldoTotal` (`LiquidacionFinalService:348-364`). Como
`saldoTotal` **nunca baja** al pagar cuotas en el POS, si el empleado ya abonó parte
de su crédito, el finiquito le **cobra de más** (el total original). Cobrar por
**cuotas impagas** (`cobro IS NULL`) da el saldo real y elimina el sobre-cobro.

## Diseño unificado (a nivel cuota)

Ambos flujos emiten ítems DESCUENTO **por cuota** (no agregado), tipo nuevo
`CREDITO_CONVENIO_CUOTA`, con la referencia a la cuota:
`referenciaId=cuota.id`, `referenciaSucursalId=cuota.sucursalId`,
`referenciaTipo="CREDITO_CONVENIO_CUOTA"`, y `referenciaEstadoPrevio` = estado del
`VentaCredito` (solo lo usa el finiquito para revertir el FINALIZADO).

**Cuota "cobrable por planilla"** = del `Cliente` del funcionario (persona
compartida) · `activo=true` · `cobro IS NULL` · **y no cobrada ya por otra
liquidación RRHH no anulada** (ver exclusión abajo).

- **Mensual** (`LiquidacionSueldoService.construirItemsAutomaticos`): cuotas con
  `vencimiento <= fin del período`. → gemelo de `PRESTAMO_CUOTA`.
- **Finiquito** (`LiquidacionFinalService.agregarDescuentosAutomaticos`): **todas**
  las cuotas impagas (vencidas + por vencer, el empleado se va). Reemplaza al ítem
  agregado por `saldoTotal`.
- **`FINALIZADO` (ambos)**: al pagar, si al `VentaCredito` no le queda remanente en
  ninguna cuota → `estado=FINALIZADO` (reversible). El finiquito lo alcanza casi
  siempre; la mensual solo al cobrar la última cuota.

### Saldo remanente por cuota (sin tocar financiero)
El monto ya cobrado de una cuota se rastrea **en `rrhh`** como la **suma de los ítems
de liquidación** (mensual + final) que la referencian, en cabeceras **no ANULADAS**:

```
cobradoDeCuota(cuota) = Σ item.monto   -- ítems CREDITO_CONVENIO_CUOTA con referenciaId=cuota.id
                                       -- y cabecera.estado != ANULADA
remanenteCuota(cuota) = cuota.valor − cobradoDeCuota(cuota)
```

Una cuota es **cobrable** si `remanenteCuota > 0`. Esto da, gratis:
- **Cobro parcial**: `item.monto` puede ser `< cuota.valor` (ver §Cobro parcial).
- **Carry-over**: el remanente aparece solo en la siguiente liquidación.
- **Reversibilidad**: al **anular** una liquidación, sus ítems dejan de sumar → el
  remanente se libera automáticamente. No se toca `financiero`.

Repos: `LiquidacionItemRepository` + `LiquidacionFinalItemRepository` exponen la suma
cobrada por `cuota.id` (excluyendo ANULADA y la liquidación en curso).

### Cobro parcial y tope por disponible (el neto nunca queda negativo)

El convenio es el descuento **elástico**: se cobra solo hasta donde alcanza el
sueldo, y el remanente cae al mes siguiente. Nunca deja el neto en negativo.

1. Se computan **primero** todos los demás conceptos (salario, HE, bono / IPS,
   penalización, justificativo, vale, cuota de préstamo). El convenio se arma
   **último**.
2. `disponible = max(0, totalHaberes − totalDescuentos_sin_convenio)`.
3. Se recorren las cuotas cobrables **de la más vieja a la más nueva**; por cada una:
   `aCobrar = min(remanenteCuota, disponibleRestante)`. Si `aCobrar > 0` → ítem
   `CREDITO_CONVENIO_CUOTA` con `monto = aCobrar` (puede ser **parcial**), y
   `disponibleRestante −= aCobrar`. Se corta cuando `disponibleRestante = 0`.
4. Lo no cobrado queda como remanente y aparece en la próxima liquidación.

**Ejemplo:** cuota 200.000, disponible del funcionario 180.000 → se cobra 180.000
(ítem parcial), el neto queda en 0, y los 20.000 restantes se cobran el mes que
viene. Sin ítem "saldo anterior": el remanente vive en la propia cuota.

- **Finiquito**: mismo tope (`disponible = neto del finiquito sin convenio`). Como no
  hay "próximo mes", si queda remanente **no** se cobra y esas cuotas siguen impagas;
  el `VentaCredito` se marca `FINALIZADO` **solo si quedó todo cobrado** (remanente 0
  en todas sus cuotas). Si no, permanece abierto (el ex-empleado sigue debiendo).
- **Fuera de alcance (pre-existente):** si vales/cuotas de préstamo por sí solos ya
  superan los haberes, el neto puede ser negativo — ese tope no se aborda acá (esos
  descuentos tienen su propia semántica de saldado). Solo el convenio se limita.
  Anotarlo como posible mejora futura (aplicar el mismo tope a vales/préstamos).

## Puente de Tesorería (qué se hace ahora vs. qué llega después)

- **Ahora (MVP, puente):** al pagar la liquidación, el neto ya descuenta las cuotas
  de convenio (la empresa retiene esa plata). La cuota queda registrada como cobrada
  **solo del lado RRHH** (vía el ítem de liquidación). **No** se crea `Cobro`, **no**
  se toca `cuota.cobro`, **no** se postea ingreso a caja. En `financiero` la cuota
  sigue `cobro=null` hasta Tesorería. El finiquito sí flipa el `VentaCredito` a
  `FINALIZADO` (para sacarlo de cuentas por cobrar del ex-empleado).
- **Después (módulo Caja Mayor/Virtual):** el asiento formal — crear `Cobro`,
  linkear `cuota.cobro`, definir a qué caja entra la plata y la contrapartida —
  reemplaza al marcador. RRHH llamará a un servicio de Tesorería
  (`tesoreria.cobrarCuotaCredito(cuota, origen=PLANILLA, liquidacionRef)`) en vez de
  solo emitir el ítem. La referencia (`CREDITO_CONVENIO_CUOTA` → cuota) ya queda
  lista para ese backfill. **Anotar como dependencia del módulo de Tesorería.**

## Regla crítica: NO tocar el DDL de `financiero` (replicación)

`venta_credito_cuota`/`venta_credito` viven en el schema **`financiero`, que se
replica a las filiales**. **No** agregamos columnas ahí (un cambio DDL en tabla
replicada es la regla más delicada del proyecto y exigiría migrar filial + romper la
suscripción si el subscriber no tiene la columna). Por eso:
- El marcador "cobrada por planilla" vive en **`rrhh`** (los ítems de liquidación),
  no en `financiero`.
- Lo único que se escribe en `financiero` es **DML** ya soportado: el flip de
  `venta_credito.estado` a `FINALIZADO` en el finiquito (misma operación que el cobro
  manual actual; replica sin problema, no es DDL).

## Migración `V175.5__rrhh_liq_item_referencia_credito.sql`
Solo tablas `rrhh` (central-only), aditiva/nullable:
```sql
ALTER TABLE rrhh.liquidacion_item
  ADD COLUMN IF NOT EXISTS referencia_sucursal_id BIGINT,
  ADD COLUMN IF NOT EXISTS referencia_estado_previo VARCHAR(30);
ALTER TABLE rrhh.liquidacion_final_item
  ADD COLUMN IF NOT EXISTS referencia_sucursal_id BIGINT,
  ADD COLUMN IF NOT EXISTS referencia_estado_previo VARCHAR(30);
```
(`referencia_sucursal_id` porque la PK de la cuota es compuesta; `referencia_estado_previo`
para revertir el FINALIZADO del finiquito.)

## Pasos de implementación

### 1. financiero — `VentaCreditoService` (refactor + reuso)
- Extraer del resolver (`VentaCreditoGraphQL.finalizarVentaCredito(s)` `:225-258`):
  `finalizarPorCobro(VentaCredito)` (idempotente: si ya FINALIZADO, no-op) y
  `revertirFinalizacion(VentaCredito, EstadoVentaCredito estadoPrevio)`. El resolver
  del cobro manual delega (sin regresión).
- `cuotasImpagas(clienteId, hastaVencimiento?)`: cuotas `activo=true`, `cobro IS NULL`,
  de las `VentaCredito` del cliente en `ABIERTO/EN_MORA/INCOBRABLE`, opcionalmente con
  `vencimiento <= hasta`. (Mensual pasa `hasta=fin período`; finiquito pasa `null`.)

### 2. Migración `V175.5` (arriba).

### 3. Entidades `rrhh`
- `LiquidacionItem` y `LiquidacionFinalItem`: agregar `Long referenciaSucursalId` +
  `String referenciaEstadoPrevio`.

### 4. rrhh — repos de saldo cobrado por cuota
- `LiquidacionItemRepository` / `LiquidacionFinalItemRepository`: query que devuelve la
  **suma de `monto`** agrupada por `referencia_id` para
  `referencia_tipo='CREDITO_CONVENIO_CUOTA'`, uniendo a la cabecera con `estado != ANULADA`
  y excluyendo la liquidación en curso. Un helper en el service arma
  `Map<Long, BigDecimal> cobradoPorCuota` para calcular `remanenteCuota`.

### 5. Mensual — `LiquidacionSueldoService.construirItemsAutomaticos`
- Armar el convenio **al final** (después de todos los demás ítems), calcular
  `disponible = max(0, haberes − descuentos_sin_convenio)`.
- Recorrer `cuotasImpagas(clienteId, finPeriodo)` (más vieja primero), tomando el
  `remanenteCuota` (valor − cobradoPorCuota); por cada una emitir un ítem
  `CREDITO_CONVENIO_CUOTA` con `monto = min(remanente, disponibleRestante)` mientras
  `disponibleRestante > 0`. Concepto `"CUOTA CREDITO — venta #<vcId> cuota <n>"`
  (agregar "(parcial)" si `monto < remanente`), refs seteadas.

### 6. Finiquito — `LiquidacionFinalService.agregarDescuentosAutomaticos` (`:348-364`)
- Reemplazar el bloque `saldoTotal` agregado por el mismo mecanismo con
  `cuotasImpagas(clienteId, null)` (todas las impagas) y el mismo tope por
  `disponible` (neto del finiquito sin convenio). `referenciaEstadoPrevio=vc.estado`.

### 7. `aplicarEfectosCruzados` (en **ambos** services, misma lógica)
- `case "CREDITO_CONVENIO_CUOTA":`
  - **pagar**: el cobro (parcial o total) ya queda registrado por el ítem (marcador
    RRHH). Luego, **regla unificada**: si al `VentaCredito` de la cuota no le queda
    **ningún** remanente en ninguna de sus cuotas → `finalizarPorCobro(vc)`. (El
    finiquito lo alcanza casi siempre; la mensual solo al cobrar la última cuota.)
  - **anular**: si el ítem había flipado el `VentaCredito`, `revertirFinalizacion(vc,
    referenciaEstadoPrevio)`. El remanente se libera solo porque el ítem deja de sumar.
- **Nota**: `referenciaEstadoPrevio` se guarda en **todo** ítem `CREDITO_CONVENIO_CUOTA`
  (no solo del finiquito), porque cualquier flujo puede ser el que cierre el crédito.

## Casos borde

- **Multi-sucursal**: resuelto con `referenciaSucursalId`.
- **`INCOBRABLE`**: se cobra igual que `ABIERTO` (decisión confirmada).
- **Disponible = 0**: no se emite ningún ítem de convenio ese mes; todo el remanente
  cae al siguiente. El neto no baja de 0 por convenio.
- **Cobro parcial**: `item.monto < remanenteCuota`; el resto queda vivo en la cuota
  (`remanenteCuota` lo refleja). Sin ítem "saldo anterior".
- **Crédito sin cuotas generadas** (dato viejo): fallback — si `cantidadCuotas` es
  null/0 pero hay `saldoTotal>0`, tratar el saldo como una "cuota" lógica única con ref
  al `VentaCredito` (no a la cuota). Verificar si aparece en la data real.
- **Cuota en dos liquidaciones vivas**: el saldo cobrado suma sobre cabeceras
  `!= ANULADA` y `!=` la actual → no se cobra dos veces lo mismo.
- **Mensual**: cobro automático (sin toggle), como `PRESTAMO_CUOTA`. El finiquito
  mantiene su toggle `cobrarConvenios`.
- **Idempotencia/reversibilidad**: pagar/anular espeja el patrón `VALE`/`CPP_CUOTA`.

## Testing (dev DB)

- Sembrar un funcionario con un `VentaCredito` de 3 cuotas, 1 vencida.
- **Mensual (caso normal)**: generar liquidación del período → 1 ítem
  `CREDITO_CONVENIO_CUOTA` (la vencida). Pagar → neto la descuenta; regenerar el mes
  siguiente → no la re-cobra. Anular → vuelve a aparecer.
- **Mensual (cobro parcial / tope)**: cuota vencida de 200.000 con disponible 180.000
  → ítem parcial de 180.000, neto = 0; el mes siguiente cobra los 20.000 restantes.
  Verificar que `remanenteCuota` llega a 0 recién tras el segundo cobro.
- **Finiquito**: generar → ítems por las cuotas impagas (arregla el sobre-cobro vs
  `saldoTotal`). Si el finiquito cubre todo → `VentaCredito` FINALIZADO, sale de
  cuentas por cobrar. Si queda remanente (finiquito insuficiente) → NO FINALIZADO, el
  crédito sigue abierto. Anular → estado restaurado.
- **Regresión**: cobro manual de crédito (`finalizarVentaCredito`) intacto tras el
  refactor.
- Actualizar T14/T15/T18 en `PLAN-TESTEO-MANUAL-RRHH.md` con el sub-caso convenio
  (incluido el parcial/carry-over).

## Esfuerzo estimado

~2 días: refactor financiero + migración rrhh + entidades + 2 bloques de armado (con
tope/parcial) + `aplicarEfectosCruzados` en 2 services + queries de saldo cobrado +
prueba integración (incluye el caso parcial). Riesgo bajo/medio (aditivo y reversible;
sin DDL en tablas replicadas; espeja un patrón ya probado). El asiento formal de
Tesorería queda como dependencia del próximo módulo, no bloquea.
