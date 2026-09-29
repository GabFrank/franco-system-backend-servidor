# PLAN — Vale en cuotas y vale en especie

**Pedido (Franco, 2026-09-29):** poder cargar un vale que se descuente en varias liquidaciones.
Caso: uniforme en dos pagos → `VALE UNIFORME 1/2` en la liquidación del periodo actual y
`VALE UNIFORME 2/2` sola en la del siguiente. Además, que el vale pueda ser **en especie**
(el uniforme se entrega, no sale plata de la Caja Mayor).

Repos: **central** (`feature/rrhh-vale-en-cuotas`) + **desktop** (`feature/rrhh-vale-en-cuotas`).
Filial: `N/A para filial porque el schema rrhh es central-only [ev: V155.0 "gestion central-only";
gotchas rrhh #21: ninguna tabla rrhh está en central_pub]`.
Mobile / mobile-pwa: fuera de alcance (la solicitud desde el celular sigue creando vales de 1 cuota).

## Estado de hoy (verificado en código, develop 778d60eb)

- `rrhh.vale` no tiene cuotas. `LiquidacionSueldoService.construirItemsAutomaticos` toma **todo** vale
  `CONFIRMADO` sin `liquidacionId` y lo descuenta **entero**, sin mirar la fecha.
- Todo camino a `CONFIRMADO` mueve plata: `confirmar`/`crearConfirmado` (egreso directo a Caja Mayor)
  o `sincronizarDesdeSolicitudPago` (pago desde tesorería).
- El finiquito (`LiquidacionFinalService.agregarDescuentosAutomaticos`) descuenta `v.getMonto()` entero.
- Dashboard (`DashboardRrhhService` KPI + top exposición), resumen mobile (`RrhhMobileService`) y
  `ReporteRrhhService` suman `v.getMonto()` de los vales pendientes.

## Diseño

### Modelo

- `rrhh.vale` + `cantidad_cuotas int NOT NULL DEFAULT 1` + `en_especie boolean NOT NULL DEFAULT false`.
- Tabla nueva `rrhh.vale_cuota` (espejo de `prestamo_cuota`): `id`, `vale_id` FK, `numero`, `monto`,
  `fecha_descuento`, `estado` (`PENDIENTE`/`DESCONTADA`/`ANULADA`), `liquidacion_id`,
  `liquidacion_final_id`, `creado_en`; `UNIQUE (vale_id, numero)`.
- Enum `ValeCuotaEstado` en Java **y** en `vales-prestamos.graphqls` en el mismo commit.

**Un vale de 1 cuota no genera filas en `vale_cuota`** y sigue exactamente por el camino de hoy
(ítem `VALE`/`ADELANTO` sobre el vale). Así los vales existentes, los de tesorería y los de mobile
no cambian de comportamiento. Solo `cantidad_cuotas > 1` entra al camino nuevo.

### Generación de cuotas (y cuándo se congelan)

- En `ValeService.save`: si `cantidadCuotas > 1`, `sincronizarCuotas(vale)` genera las cuotas.
  **Solo se regeneran mientras el vale está `SOLICITADO`**: en ese estado ninguna liquidación puede
  tener un ítem que las referencie (solo entran vales `CONFIRMADO`). Desde `CONFIRMADO` en adelante,
  `saveVale` sobre un vale en cuotas o en especie **rechaza** cambios de monto, fecha, cantidad de cuotas
  o estado (ver "Puertas"). Se descartó "borrar y regenerar siempre": dejaba ítems de borradores
  apuntando a cuotas borradas (auditoría B-A2).
- Montos con `CuotaCalculator.calcularCuotas(monto, n)` (la última absorbe el redondeo, suma exacta).
- `fecha_descuento` de la cuota k = `vale.fecha.plusMonths(k-1)`. La k/n se descuenta en la liquidación
  cuyo periodo (según `DIA_CIERRE_MES`) contiene esa fecha. Con cierre 25 y vale del 28/09 la 1/2 cae
  en octubre: es correcto (el 28/09 ya es periodo de octubre), y nunca caen dos cuotas en un periodo
  ni se saltea uno (auditoría B-M3).
- Validaciones: `cantidadCuotas` entre 1 y 12; `> 1` exige `fecha` y `monto > 0`.
- `mapInput` solo pisa `cantidadCuotas` si viene no nulo: un desktop viejo que edita un vale no lo
  vuelve a 1 cuota (auditoría A-M1).

### Descuento en la liquidación mensual

- `construirItemsAutomaticos`: vale `CONFIRMADO` sin `liquidacionId` con `cantidadCuotas > 1` →
  un ítem por cuota `PENDIENTE` con `fecha_descuento <= fin` del periodo. Código `VALE_DESCUENTO`
  (o `ADELANTO_DESCUENTO`), descripción `VALE <MOTIVO> k/n`, `referenciaTipo = "VALE_CUOTA"`,
  `referenciaId = cuota.id`. Una cuota atrasada (periodo anterior no liquidado) entra igual: ponerse al día.
- `aplicarEfectosCruzados`, caso `VALE_CUOTA`, en un servicio chico `ValeCuotaDescuentoService`
  (espejo de `PrestamoCuotaDescuentoService`, con lock pesimista sobre la cuota):
  - pagar → cuota `DESCONTADA` + `liquidacion_id`. **Si ya estaba DESCONTADA por otra liquidación →
    excepción**; si ya estaba DESCONTADA por **la misma** liquidación → no-op (el hook de tesorería puede
    llamar dos veces). Cuota inexistente o `ANULADA` → excepción, nunca `return` silencioso.
    Si todas las cuotas quedaron DESCONTADA → vale `DESCONTADO` + `liquidacionId`.
  - revertir (anular liquidación) → cuota `PENDIENTE`, `liquidacion_id = null`; vale vuelve a
    `CONFIRMADO`, `liquidacionId = null`.

### Finiquito

- `agregarDescuentosAutomaticos` (toggle `cobrarVales`): vale con cuotas → un ítem por **cada** cuota
  `PENDIENTE` (sin filtro de fecha: se va y salda todo), `referenciaTipo = "VALE_CUOTA"`.
- `aplicarEfectosCruzados` del finiquito: mismo servicio, marcando `liquidacion_final_id`.

### Vale en especie

- Mutation nueva `crearValeEnEspecie(vale: ValeRrhhInput!, autorizadoPorId: ID): Vale`,
  `seg.requireAnyRole(seg.APROBAR)` (mismo rol que `crearValeConfirmado`).
- Crea el vale `CONFIRMADO`, `enEspecie = true`, **sin** caja ni movimiento.
- `anular`: `revertirEgresoCaja` ya hace `return` sin `cajaVirtualId` → no toca caja. Correcto.
- **Puertas** que cambian el estado de un vale y tienen que respetar cuotas/especie:
  - `confirmar`: rechaza un vale en especie (ya nace confirmado).
  - `crearConfirmado`: fuerza `enEspecie=false`.
  - `saveVale` (`mapInput` copia `estado` del input, rol GESTIONAR): para vales en cuotas o en especie
    ignora `estado` y rechaza cambiar monto/fecha/cuotas fuera de `SOLICITADO`; `enEspecie` nunca se
    toma del input de `saveVale`. Así no hay "en especie encubierto" sin APROBAR ni un vale en especie
    que vuelva a `SOLICITADO` y aparezca en tesorería (auditoría A-A2, B-B2). Vales de 1 cuota comunes:
    sin cambios.
  - Tesorería: un vale en cuotas `SOLICITADO` **sí** se puede pagar desde el hub (el plan original decía
    lo contrario, auditoría A-A1). `sincronizarDesdeSolicitudPago` al anular el pago: si alguna cuota
    está `DESCONTADA` o en un ítem de liquidación/finiquito no anulado → excepción (mismo guard que
    `anular`), así el vale no vuelve a quedar pagable por el monto entero.

### Anular vale con cuotas

- Si alguna cuota está `DESCONTADA` o en un ítem de liquidación/finiquito no anulado → excepción
  ("la cuota k/n está en la liquidación #X").
- Si no → cuotas `ANULADA` + lo de hoy (contra-asiento si hubo caja).

### Saldo pendiente en lecturas

`ValeService.saldoPendiente(vale)` = monto si no tiene cuotas; si tiene, suma de cuotas `PENDIENTE`.
Lo usan: `DashboardRrhhService` (KPI y top exposición), `RrhhMobileService` (resumen) y
`ReporteRrhhService` (reporte de vales pendientes: fila **y** total). Lugares: `DashboardRrhhService:125`
y `:209`, `RrhhMobileService:152`, `ReporteRrhhService:303-304` (auditoría A-M2).
`ReciboLiquidacionService.fechaItem` suma el caso `VALE_CUOTA` con `cuota.fechaDescuento` (A-B1).

### GraphQL

- `type Vale` + `cantidadCuotas: Int`, `enEspecie: Boolean`, `saldoPendiente: Float`
  (field resolver `ValeResolver`).
- `input ValeRrhhInput` + `cantidadCuotas: Int`.
- `type ValeCuota`, `enum ValeCuotaEstado`, query `valeCuotas(valeId: ID!): [ValeCuota]` con `seg.requireVer()`.
- Todo aditivo: un desktop viejo no pide los campos nuevos y sigue andando.

### Desktop

- `vale.model.ts`: `cantidadCuotas`, `enEspecie`, `saldoPendiente`; `toInput()` manda `cantidadCuotas`.
- `EditValeDialog`: campo **Cuotas** (1–12, default 1) y vista previa con **fechas**, no meses
  ("1/2 Gs. 150.000 — 15/09/2026; 2/2 Gs. 150.000 — 15/10/2026; se descuenta en la liquidación que
  cubra esa fecha"), calculada en `valueChanges` (sin getters en el template). Con cierre < 28 un mes
  calendario sería mentira (auditoría B-M3). Toggle **Entrega en
  especie** visible solo con `RRHH APROBAR` (flag en `ngOnInit`); con el toggle prendido, guarda con
  `crearValeEnEspecie` en vez de `saveVale`.
- `ListVale`: columna Cuotas (`1/2 descontada`, `2 cuotas`) y marca "En especie"; acción **Ver cuotas**
  (diálogo con la query `valeCuotas`). El botón **Confirmar** no se muestra para vales en especie.
- `financiero-legajo.component.ts:83-86`: "pendiente de descontar" y `exposicionTotal` pasan a sumar
  `saldoPendiente` (auditoría A-A3).
- Queries del desktop que piden los campos nuevos: **se despliegan después del central** (gotcha rrhh #8:
  un campo que el schema no tiene tumba la query entera).

## Tabla de datos nuevos (quién escribe / quién lee)

| Dato | Escribe | Lee |
|---|---|---|
| `vale.cantidad_cuotas` | `ValeGraphQL.mapInput` ← `EditValeDialog` | `ValeService.sincronizarCuotas`, `construirItemsAutomaticos`, `LiquidacionFinalService`, `ListVale` |
| `vale.en_especie` | `ValeService.crearEnEspecie` ← `EditValeDialog` (toggle) | `ValeService.confirmar` (guard), `ListVale` (marca y oculta Confirmar) |
| `vale_cuota.*` (numero, monto, fecha_descuento) | `ValeService.sincronizarCuotas` | `construirItemsAutomaticos`, finiquito, `saldoPendiente`, diálogo Ver cuotas |
| `vale_cuota.estado` / `liquidacion_id` / `liquidacion_final_id` | `ValeCuotaDescuentoService` (pagar/revertir), `ValeService.anular` | ídem + guard de doble descuento |
| `saldoPendiente` (calculado) | — | Dashboard, resumen mobile, reporte, `ListVale` |

## Migración

`V233.1__rrhh_vale_cuotas.sql` (mayor en develop: `V232.5`). Solo aditiva: 2 `ADD COLUMN` con
default + `CREATE TABLE` + índices. Sin espejo en filial (schema rrhh no publicado).
**Rollback — no es gratis (auditoría B-A1).** El JAR viejo arranca contra el esquema nuevo
(`ignore-missing-migrations=true`), pero ve un vale en cuotas `CONFIRMADO` como un vale común y en la
próxima liquidación lo descuenta **entero** aunque ya se haya descontado una cuota; tampoco revierte
ítems `VALE_CUOTA` al anular. El rollback automático de `deploy.sh` solo ocurre si el health check falla
(ahí todavía no hay vales en cuotas). **Después del primer vale en cuotas, se arregla hacia adelante.**
Si igual hay que volver al JAR anterior: antes listar
`select v.id from rrhh.vale v where v.cantidad_cuotas > 1 and v.estado = 'CONFIRMADO' and exists
(select 1 from rrhh.vale_cuota c where c.vale_id = v.id and c.estado = 'DESCONTADA')` y sacarlos a mano de
los borradores que genere el JAR viejo.

DDL de `vale_cuota` calcada de `prestamo_cuota` (`id BIGSERIAL`, entidad con `AssignedIdentityGenerator`),
índices `(vale_id)` y `(estado, fecha_descuento)`.

## Fases

| # | Repo | Qué | Tests |
|---|---|---|---|
| 1 | central | Migración + entidad `ValeCuota` + repo + enum (Java+graphqls) + campos en `Vale`/`ValeInput`/schema + `sincronizarCuotas` + `crearEnEspecie` + puertas (`confirmar`, `crearConfirmado`, `saveVale`, `anular`, `sincronizarDesdeSolicitudPago`) + query `valeCuotas` + `ValeResolver.saldoPendiente` | `ValeCuotasTest`: montos y fechas (incl. fecha 31), regeneración solo en SOLICITADO, editar CONFIRMADO en cuotas rechaza, editar sin `cantidadCuotas` conserva las cuotas, `saveVale` no cambia estado de un vale en especie, anular con cuota descontada falla, en especie sin movimiento de caja, confirmar en especie falla, anular pago de tesorería con cuota descontada falla; `SchemaEnumsSincronizadosTest` |
| 2 | central | Liquidación mensual + finiquito: ítems `VALE_CUOTA`, exclusión del camino viejo, exclusión de cuotas ya en otro documento, `ValeCuotaDescuentoService` (validar/aplicar/revertir) llamado en los 4 lugares, efectos cruzados; `saldoPendiente` en dashboard/mobile/reporte; `fechaItem` del recibo | `ValeCuotaDescuentoServiceTest`: pagar, revertir, vale→DESCONTADO en la última, doble descuento falla, misma liquidación es no-op, cuota ANULADA/inexistente falla antes del egreso. Ítems: vale 2 cuotas → 1/2 en sep, 2/2 en oct, nada en nov (cierre 30) y con cierre 25; cuota en otro borrador no se duplica; vale de 1 cuota sale como hoy; finiquito no emite VALE entero + cuota |
| 3 | desktop | Modelo, queries, `EditValeDialog` (cuotas + especie), `ListVale` (columna, Ver cuotas, sin Confirmar en especie), legajo financiero con `saldoPendiente` | `npm run check`; prueba manual en `ng serve -c web` contra central local |
| 4 | central + desktop | Docs: `rrhh-expert`-equivalente en `docs/manuales-implementacion/rrhh/ESTADO-IMPLEMENTACION-RRHH.md` y `PLAN-TESTEO-MANUAL-RRHH.md` (caso nuevo); borrar este plan en el PR final | — |

Una fase = commit + push de la rama de feature. PR recién con la prueba y el ok de Franco.
Orden de PRs: **central primero**, desktop después (el desktop pide campos nuevos del schema).

## Prueba manual (local, central :8081 dev + desktop web)

1. Vale UNIFORME Gs. 300.000, 2 cuotas, fecha 15/09/2026, en especie → CONFIRMADO, la Caja Mayor no cambia.
2. Liquidación septiembre del funcionario → ítem `VALE UNIFORME 1/2` Gs. 150.000. Pagar.
3. Liquidación octubre → `VALE UNIFORME 2/2` Gs. 150.000 y nada más de ese vale. Pagar → vale DESCONTADO.
4. Anular la de octubre → cuota 2 PENDIENTE, vale CONFIRMADO.
5. Intentar anular el vale → rechazo (cuota 1/2 ya descontada).
6. Vale de 1 cuota con caja → igual que hoy (egreso, ítem `VALE`).
7. Finiquito con un vale 3 cuotas con 1 descontada → dos ítems (2/3 y 3/3).

## Qué queda sin verificar

- Migración contra copia de la base real (paso 10): dry-run con dump de bodega en local antes del PR.
- Recibo del vale (`imprimirReciboVale`) sigue mostrando el monto total, sin detalle de cuotas
  (el `.jrxml` no se toca en este trabajo).
- Vales creados desde el hub de tesorería y desde mobile: siempre 1 cuota (pero el hub sí **paga**
  vales en cuotas cargados desde el desktop).
- `misValesMobile` (app Android y PWA) muestra el monto total y el estado, sin cuotas (A-M3). El
  `valesPendientesMonto` de la PWA sí queda bien porque lo calcula el backend.
- **Bug previo, no se arregla acá (B-M1):** `anular` de una liquidación o finiquito pagado 100% por banco
  o cheque (sin `cajaVirtualId`) **no revierte ningún efecto cruzado** (`LiquidacionSueldoService:573`,
  `LiquidacionFinalService:747`). Hoy ya afecta a vales, préstamos y aguinaldo. Con cuotas, la cuota
  queda DESCONTADA y el vale no se puede anular. Se documenta y se propone como fix aparte.

## Auditoría del plan (paso 5)

Dos auditores sin verse. Hallazgos verificados contra el código antes de aplicarlos.

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| A-A1 | A | Anular el pago de tesorería devuelve a SOLICITADO un vale con cuota descontada → pagable de nuevo por el total | Guard en `sincronizarDesdeSolicitudPago` |
| A-A2 / B-B2 | A, B | `saveVale` copia `estado` (GESTIONAR): confirmar sin caja o desconfirmar un vale en especie | `saveVale` no cambia estado de vales en cuotas/especie; `enEspecie` fuera del input |
| A-A3 | A | Legajo financiero del desktop suma `v.monto` como pendiente | Fase 3 usa `saldoPendiente` |
| A-M1 | A | Desktop viejo sin `cantidadCuotas` resetea a 1 | `mapInput` solo si no nulo + test |
| A-M2 | A | Reporte: fila y total con `getMonto()` | Los 4 lugares listados |
| A-M3 | A | `misValesMobile` sin cuotas | Queda sin verificar (fuera de alcance) |
| A-M4 / B-A3 | A, B | Camino viejo tiene que excluir vales en cuotas; falta validar antes de mover plata | `continue` en mensual y finiquito; `validar*` en 4 lugares |
| A-B1 | A | `fechaItem` sin `VALE_CUOTA` | Agregado en fase 2 |
| B-A1 | B | Rollback del JAR descuenta dos veces | Sección rollback reescrita: arreglar hacia adelante |
| B-A2 | B | "Borrar y regenerar" deja ítems huérfanos | Regenerar solo en SOLICITADO; `aplicar` falla si la cuota no existe |
| B-M1 | B | Anular liquidación pagada por banco no revierte efectos (bug previo) | Documentado; Franco (2026-09-29): fix aparte, después de este trabajo |
| B-M2 | B | Misma cuota en dos borradores | Excluida al generar + no-op si es la misma liquidación |
| B-M3 | B | Vista previa por mes calendario miente con cierre < 28 | Vista previa por fecha + tests con cierre 25 y fecha 31 |
| B-B1 | B | DDL calcada de `prestamo_cuota` + índice `(estado, fecha_descuento)` | Aplicado |

Sin contradicciones entre auditores.
