# Plan — Caja Mayor, retiros y cheques (un PR por repo)

> Estado: **auditado** (paso 5: ejes A y B, hallazgos en §7), pendiente de aprobación. Fecha: 2026-11-12.
> Ramas: `feature/financiero-caja-mayor-cheques` en **central** y **desktop**, salidas de `develop`
> (central `58ebaaa9`, desktop `c8f4f3bd`). **El filial no se toca** (ver §2).

## 0 · Alcance y decisiones

| Issue | Repo | Qué | Decisión |
|---|---|---|---|
| srv #220 | central + desktop | Caja chica vs caja mayor | **Retirar el tipo `CAJA_CHICA`**. Límites y restricciones por caja → srv #620 (nueva, no urgente) |
| srv #540 | central + desktop | Orden de las cards de moneda | Orden por caja en `caja_virtual_configuracion` |
| desk #653 | central + desktop | Orden de sucursales en diálogos | **Por caja mayor** (columna en la config, no tabla global) |
| srv #603 §1 / desk #720 §1 | central + desktop | Orden y visibilidad de operaciones financieras | Por caja; ordena y oculta, **nunca habilita** |
| srv #578 | central | Caja del retiro en la descripción | **Solo movimientos nuevos**, sin backfill. Lo viejo se ve con «Ver detalle» (#721) |
| desk #670 | desktop (+ central chico) | Moneda no declarada al verificar retiro | Como la issue |
| srv #603 §2 / desk #720 §2-3 | central + desktop | Cotización «menor por 1 mayor» + motor con ancla | Como la issue, con backfill |
| desk #721 | central + desktop | Descripción con boleta + «Ver detalle» banco/caja | Como la issue, con reescritura de descripciones viejas |
| srv #601 / desk #708 | central + desktop | Emisión completa, cuotas atómicas, tablero | **Exportar sí, imprimir no** (→ desk #729). **`EMITIDO` se usa** (opción B, ver §4) |
| srv #603 §3 / desk #720 §4 | central + desktop | Inutilizar números de chequera | Como la issue |

**Fuera de alcance:** srv #203 (decisión DA8 pendiente), srv #224 (recibo de pago), srv #225 (plan Gastos),
impresión de cheques (desk #729).

### ⚠️ Desvío consciente del ciclo

El repo pide PR < 400 líneas netas y una responsabilidad por PR, y el paso 8 dice partir el PR si
necesita más de 5 lentes. **Gabriel decidió un PR por repo con las 10 issues** (2026-11-12). Se
mantiene, pero cada fase es un commit aislado y revisable por separado; si la auditoría del diff
(paso 8) pide más de 5 lentes, se vuelve a consultar antes de abrir el PR.

### Dependencia externa

`fix/financiero-string-to-decimal` (desktop, Mauro, #714, sin PR al 2026-11-12) cambia
`numbersUtils.stringToDecimal`, que usa la máscara de cotización de F4. **F4-desktop no arranca hasta
que eso esté en develop** (o se rebasa encima).

## 1 · Fases

Cada fase: commit en central → build + tests → commit en desktop → `npm run check` al final de la
fase (no en cada cambio). Una migración por fase con sufijo `.5`; los números se reconfirman al
mergear (última en develop: `V273.1`).

### F1 · Retirar `CAJA_CHICA` (srv #220)

**Central**
- `V274.5__financiero_retirar_caja_chica.sql`: `ADD COLUMN IF NOT EXISTS tipo_original varchar(20)`, copia
  `tipo_original = tipo` donde `tipo='CAJA_CHICA'`, y recién después `UPDATE … SET tipo='CAJA_MAYOR'`. Conteo
  esperado del dry-run con `RAISE EXCEPTION` si no coincide. **Sin** `DROP` del valor del enum ni de `limite_gs`
  (2 versiones). Reversible: `UPDATE … SET tipo = tipo_original WHERE tipo_original IS NOT NULL`.
- **Efecto visible (confirmado por Gabriel 2026-11-12):** «CAJA CHICA ADMINSTRACION» de bodega pasa a
  aparecer en los 6 diálogos de RRHH y como fuente de pagos. Es lo buscado (desk #230).
- `CajaVirtualService` / `CajaVirtualGraphQL.saveCajaVirtual`: si llega `CAJA_CHICA` (desktop viejo) se guarda `CAJA_MAYOR`.
- `CAJA_CHICA` queda en Java y `.graphqls` con comentario `@deprecated` (lo exige `SchemaEnumsSincronizadosTest`).

**Desktop**
- `add-caja-virtual-dialog`: sin selector de tipo (siempre `CAJA_MAYOR`), sin `limiteGs`.
- `add-movimiento-caja-virtual-dialog:135-140`: quitar el aviso de límite.
- `list-caja-virtual`: quitar filtro/etiqueta de tipo.
- 6 diálogos de RRHH (`confirmar-vale`, `pagar-aguinaldo`, `edit-prestamo`, `prestamo-cuotas`,
  `liquidacion-detalle`, `liquidacion-final`): quitar el filtro `tipo === CAJA_MAYOR`. Ajustar
  `confirmar-vale-dialog.component.spec.ts`.

**Tests:** central — guardar con `CAJA_CHICA` devuelve `CAJA_MAYOR`. Migración contra copia de bodega
(la única caja activa allí es «CAJA CHICA ADMINSTRACION»).

### F2 · Configuración de la caja: orden de monedas, sucursales y operaciones (srv #540, desk #653, srv #603 §1, desk #720 §1)

**Central**
- `V275.5__financiero_config_ordenes.sql`: en `financiero.caja_virtual_configuracion`, `ADD COLUMN IF NOT EXISTS`
  `monedas_orden text`, `sucursales_orden text`, `operaciones_financieras_orden text`,
  `operaciones_financieras_ocultas text` (JSON, nulas = orden de siempre).
- `CajaVirtualConfiguracion` + type/input `.graphqls` + `CajaVirtualConfiguracionGraphQL`: semántica
  PATCH como `cuentasBancariasOrden`. Validación: operaciones solo valores de `TipoOperacionFinanciera`
  y sin repetir; monedas/sucursales ids existentes.
- `cajaVirtualSaldos`: ordenado por `monedas_orden`, después el resto por `moneda.id` (reusar `ordenarCuentas`).
- Sucursales: query `sucursalesOrdenadasDeCaja(cajaVirtualId, soloDeLaCaja)` que aplica `sucursales_orden`
  y, sin orden, nombre. Sirve a los dos caminos de `IngresarRetiroCajaMayorDialog` (todas / acotado).
- Operaciones: la config solo ordena y oculta. **El servicio no la usa para autorizar** (el rol sigue mandando).

**Desktop**
- `configurar-caja-virtual-dialog`: tres listas CDK drag&drop (monedas, sucursales, operaciones +
  casilla «visible»), mismo patrón que cuentas bancarias.
- `caja-virtual-dashboard.construirCards`: fallback por `moneda.id` si el central es viejo;
  `onConfigurar` relee saldos.
- `caja-virtual-dashboard.onOperacionFinanciera()`: pasar la caja; `add-operacion-financiera-dialog`
  ordena `tipoOperacionList`, selecciona el primer visible y precarga la caja como origen.
- `ingresar-retiro-caja-mayor-dialog`: usar la query ordenada en los dos caminos.

**Puertas de la bandera (regla del paso 7):** `operaciones_financieras_ocultas` se respeta en
`add-operacion-financiera-dialog` abierto **desde la caja**. Abierto desde el listado de operaciones
(sin caja) usa el orden por defecto. Son las dos únicas entradas (verificar con grep al implementar).

**Tests:** central — orden de saldos con/sin config, moneda nueva al final, validación de JSON inválido.

### F3 · Retiros (srv #578, desk #670)

**Central**
- Helper único `RetiroDescripcion.de(retiro, nombreSucursal)` → `Retiro #123 - SUC X - Caja #456`
  (sin caja si `cajaSalidaId` es null). Usado en `RetiroTesoreriaProcesador:80` y `RetiroVerificacionService:233`.
  **Sin backfill.**
- `RetiroVerificacionService.verificar()`: rechazar `monedaId` inexistente o inactiva (hoy `orElse(null)`).

**Desktop**
- `verificar-retiro-dialog`: botón «Agregar moneda no declarada» (monedas activas que no están en
  filas), fila con `declarado = 0`, sin «Usar declarado», quitable mientras no tenga conteo.
  `armarFilas()` recrea desde `borrador.cantidades` las monedas no declaradas.

**Tests:** central — las dos rutas dan la misma descripción; moneda inactiva rechazada. Prueba manual
del caso #4745 (Gs declarado + R$ 500 sobrante).

### F4 · Cotización sin ambigüedad (srv #603 §2, desk #720 §2-3) — espera #714

**Central**
- `V276.5__financiero_operacion_cotizacion_moneda.sql`: `operacion_financiera.cotizacion_moneda_id bigint`
  (FK moneda) + `cotizacion_original numeric` (valor previo). Backfill de CAMBIO_DIVISA: la moneda con
  monto menor es la mayor; `cotizacion = montoMenor / montoMayor`. Dry-run de la sentencia exacta
  contra copia de bodega y farmacia, con `RAISE EXCEPTION` si alguna fila no cierra (regla de UPDATE productivo).
- `OperacionFinancieraService`: en CAMBIO_DIVISA con cotización **y `cotizacionMonedaId` presente** (cliente
  nuevo), validar `montoMenor ≈ montoMayor × cot` (tolerancia de redondeo de la moneda menor) y rechazar con
  mensaje claro. **Sin `cotizacionMonedaId` (desktop viejo) no se rechaza:** se normaliza desde los montos, se
  graba y se loguea un aviso. Test de contrato: input del desktop viejo → aceptado y normalizado.
- Rollback: el backend viejo lee la cotización normalizada con la semántica vieja solo para mostrarla
  (no recalcula montos de operaciones guardadas). Se declara en «impacto en rollback» del PR.
- Query `cotizacionCruzada(monedaAId, monedaBId)`: las dos contra Gs desde `cambio`.

**Desktop**
- Motor de cálculo extraído a una clase pura (`cotizacion-motor.ts`) con la tabla de casos de #720 §3
  como spec (Gs↔US$, R$↔US$, Gs→R$; fija/calculada; todos los órdenes de escritura).
- `add-operacion-financiera-dialog`: etiqueta «R$ por 1 US$» + inversa debajo, máscara de miles,
  candado fija/calculada, «usar cotización del sistema» con cruzada. Manda `cotizacionMonedaId`.

**Tests:** central — validación rechaza la cotización invertida; backfill con los 3 casos reales
REAL→DOLAR de bodega. Desktop — spec del motor (ejecutable con `npm test`, aunque el CI no lo corra).

### F5 · Descripciones y «Ver detalle» (desk #721)

**Central**
- `OperacionFinancieraDescripcion`: un armador por tipo para las dos patas (banco y caja):
  «Depósito · Boleta N · contraparte»; sin comprobante «(op #N)». Reemplaza los textos fijos de
  `OperacionFinancieraService` (incluido el enum crudo `DEPOSITO_BANCARIO (op #116)`).
- `V277.5__financiero_reescribir_descripcion_operaciones.sql`: `ADD COLUMN IF NOT EXISTS descripcion_original text`
  en `movimiento_bancario` y `movimiento_caja_virtual`; copia la descripción vieja **solo donde la nueva difiere**
  y después reescribe con la misma regla en SQL. **Solo la columna descripción**, nunca montos ni saldos.
  Dry-run + conteo esperado (bodega: 64 bancarios) con `RAISE EXCEPTION`. Reversible desde `descripcion_original`.
- Efecto visible: quien buscaba «op #116» en el texto libre ya no lo encuentra en los movimientos con
  comprobante. La búsqueda (`MovimientoCajaVirtualRepository:152`) suma `descripcion_original` al `like`.
- Editar el Nº de comprobante de una operación actualiza la descripción de sus movimientos.
- Detalle de retiro para la caja: query `retiroDetalleCajaMayor(retiroId, sucursalId)` (sucursal, caja,
  responsable, monedas, verificación), con `requireLecturaCaja`.

**Desktop**
- `caja-virtual-dashboard`: `menuBanco` con «Ver detalle» por `origenTipo` (tabla de #721 §2),
  precalculado por fila; visible también en anuladas; doble clic; respeta
  `ORIGENES_SIN_DETALLE_EN_MODO_ACOTADO`. Caja: sumar RETIRO_CAJA, GASTO/RRHH/PAGO_CPP sueltos,
  ANULACION, MANUAL. Texto unificado «Ver detalle».
- `OperacionFinancieraDetalleDialog`: descripción, categoría, usuario, diferencia y destino, anulación;
  cotización en el formato de F4.

**Tests:** central — armador por tipo, con y sin comprobante; reescritura idempotente.

### F6 · Emisión de cheques, `EMITIDO` y tablero (srv #601, desk #708)

**Central**
- `V278.5__financiero_cheque_proveedor_emision.sql`: `cheque.proveedor_id` (FK `personas.proveedor`,
  índice), índice por `fecha_entrega`, `fecha_entrega_backfill boolean` y backfill `fecha_entrega = creado_en`
  donde sea NULL, marcando esas filas. Bandera nueva en `configuracion_general` (o la tabla de config
  financiera vigente): `cheque.al_dia_emitido`, **apagada**.
- Orden de locks fijo en todo el módulo de cheques: **chequera → cheque → cuenta** (ids ascendentes si hay
  varias). Lo cumplen `emitir`, `emitirCheques`, `cobrar`, `anular`, `anularPorPago` e `inutilizarNumeroCheque`.
- `emitirCheque` acepta `proveedorId`, `beneficiario`, `nominal`, `fechaEmision` (en la huella de
  idempotencia). `PagoProveedorService.emitirCheque` setea `proveedor_id`.
- `emitirCheques(...)` atómico: un lock de chequera, control de hojas para N, una clave para el lote,
  reenvío devuelve los mismos N. Reusa `ChequeGestionService.emitir`.
- `chequesDashboard` / `chequesResumenPorDia`: `tipoFecha: EMISION|PAGO|COBRO` (default PAGO), `texto`,
  `proveedorId`, tope/paginación. Campo `origen` en `Cheque` (pago + proveedor o «manual»).
- Anular desde el tablero: motivo del usuario; un cheque de un pago se rechaza con el enlace al pago.
- **`EMITIDO` (opción B, §4).**

**Desktop**
- `cheque-emision-editor` compartido (extraído de `pagar-compras-dialog` `.cheque-panel` / `agregarCheques()`),
  usado por el pago y por `emitir-cheque-dialog`. Proveedor o beneficiario libre, nominal, fecha de
  emisión, cuotas 1-12 + intervalo, vista previa editable con total controlado. Llama `emitirCheques`.
- `cheques-dashboard`: sin gráfico (fuera echarts/`TemaService` del componente); por defecto «Emitidos hoy»
  (emisión = hoy, todos los estados); selector de tipo de fecha; estados Todos / Pendientes
  (EMITIDO + DIFERIDO) / Cobrados / Anulados; búsqueda; columnas Concepto, Origen, Usuario;
  acciones Anular con motivo, Ver detalle, **Exportar** (xlsx). `_cobrable` / `_anulable` incluyen EMITIDO.

**Tests:** central — `emitirCheques` todo o nada, reenvío no duplica, concurrencia de dos lotes sobre
la misma chequera, faltan hojas → rechazo; `tipoFecha: EMISION` trae el diferido de hoy con pago el mes
siguiente; clientes sin parámetros nuevos iguales. Ciclo EMITIDO (§4).

### F7 · Inutilizar números de chequera (srv #603 §3, desk #720 §4)

**Central**
- `V279.5__financiero_chequera_numero_inutilizado.sql`: tabla como la issue, único parcial
  `(chequera_id, numero) WHERE revertido_en IS NULL`.
- Mutations `inutilizarNumeroCheque` (ANULADO solo cheques sueltos, con la lógica de `anularPorPago`;
  RENUMERADO a un número libre del rango) y `revertirInutilizacion`; query `proximosNumerosCheque`.
- `ChequeGestionService.emitir` y `emitirCheques` saltean inutilizados vigentes al tomar número y al avanzar
  `siguiente_numero`; AGOTADA cuando no quedan libres. `hojasDisponibles` (resolver y dashboard) los descuenta.
- Permiso del rol que gestiona chequeras; auditoría con usuario y motivo.

**Desktop**
- Gestionar chequeras: «Inutilizar número» (pasado/futuro, con cheque → anular o renumerar), lista con «Revertir».
- «Próximo Nº» y cuotas desde `proximosNumerosCheque` (en el editor compartido de F6 y en `pagar-compras-dialog`).
- Tablero: estado «Inutilizado» filtrable.

**Tests:** central — salteo de 1 y varios seguidos; último del rango → AGOTADA; revertir; ANULADO sobre
DIFERIDO, EMITIDO y COBRADO; RENUMERADO a ocupado → rechazo; cheque de un pago con ANULADO → rechazo.

### F8 · Cierre

- Build central (`./mvnw clean package`; los tests Jasper se validan por CI, no en local).
- `npm run check` en desktop, con `NG_CLI_ANALYTICS=false` (el build se cuelga sin eso).
- Guía de prueba manual: `PRUEBA-MANUAL-CAJA-MAYOR-CHEQUES.md` (central) con un caso por issue.
- Actualizar `ARQUITECTURA-MODULO-FINANCIERO.md` (cheques y EMITIDO, config de caja, cotización) y la
  skill `frc-financiero-expert` (regla 8 de cheques y caja chica retirada), en la misma tanda que el código.
- PRs: **central primero**, desktop después. Despliegue en el mismo orden y en el mismo canal.

## 2 · Replicación y filial

Ninguna tabla tocada está en la réplica: el filial no tiene `caja_virtual*`, `chequera`, `cheque` ni
`operacion_financiera` (grep en `filial/src/main` vacío). `retiro` es BRANCH_TO_MAIN y **solo se lee**
(F3 cambia texto en central, no la tabla). → **N/A para filial**: sin PR ni espejo de migraciones.

## 3 · Datos nuevos: quién escribe, quién lee

| Dato | Escribe | Lee |
|---|---|---|
| `caja_virtual_configuracion.monedas_orden` | `CajaVirtualConfiguracionGraphQL.save` ← `configurar-caja-virtual-dialog` | `cajaVirtualSaldos` → `construirCards` |
| `…sucursales_orden` | idem | `sucursalesOrdenadasDeCaja` → `ingresar-retiro-caja-mayor-dialog` |
| `…operaciones_financieras_orden` / `_ocultas` | idem | `add-operacion-financiera-dialog` (desde la caja) |
| `operacion_financiera.cotizacion_moneda_id` | `OperacionFinancieraService` (input o deducido) + backfill V276.5 | `OperacionFinancieraDetalleDialog`, armador de descripción (F5) |
| `operacion_financiera.cotizacion_original` | backfill V276.5 | nadie en la app: es la traza del backfill (auditoría por SQL) |
| `cheque.proveedor_id` | `ChequeGestionService` vía `emitirCheque(s)` y `PagoProveedorService.emitirCheque` | `chequesDashboard` (filtro `proveedorId`, columna Origen) |
| `cheque.fecha_entrega` (ahora siempre) | `ChequeGestionService.emitir` + backfill V278.5 | `tipoFecha: EMISION`, columna Emisión |
| `cheque.estado = EMITIDO` | `ChequeGestionService.emitir` (al día) | `cobrar`, `anular`, `anularPorPago`, `TesoreriaReporteService` vencimientos, dashboard |
| `chequera_numero_inutilizado.*` | `inutilizarNumeroCheque` / `revertirInutilizacion` | `emitir`/`emitirCheques`, `proximosNumerosCheque`, `hojasDisponibles`, tablero |
| `movimiento_*.descripcion` (nuevo formato) | armadores de F3 y F5 + V277.5 | tablas de caja y banco |

## 4 · `EMITIDO` — cheque al día entregado y no debitado (opción B, detrás de bandera)

Hoy el cheque al día debita al emitir (`ChequeGestionService.emitir:170-176`, queda COBRADO). `EMITIDO` ya
existe en Java, `.graphqls` y la columna (`varchar(20)` sin CHECK, V183.5): **no se agrega valor de enum**.

**Dos etapas en el mismo PR (decidido 2026-11-12, por la auditoría):**

*Etapa 1 — código tolerante, siempre activo:*
1. **`cobrar`** acepta `DIFERIDO` y `EMITIDO`; libera la reserva **por estado** (`DIFERIDO`/`EMITIDO`), no por
   el flag `diferido`, antes de debitar. Si el cheque sale de un pago, guarda el movimiento en
   `pago_solicitud_detalle.movimiento_bancario_id` **solo para visualización** (`pagoIdLeido`); la anulación
   del pago sigue entrando por la rama del cheque (`anularPagoCpp:874` → `anularPorPago`), que revierte por
   `cheque.movimientoBancarioId`. Test: «pago con cheque EMITIDO → cobrar → anular pago» revierte **una sola vez**.
2. **`anular`** y **`anularPorPago`**: `EMITIDO` libera la reserva, sin reversa.
3. **`ajustarReservado`**: guarda `reservado ≥ 0` (rechaza dejarlo negativo).
4. **Consumidores**: `TesoreriaReporteService` vencimientos incluye `EMITIDO`; `ChequeDashboardService:59`
   default = pendientes (`DIFERIDO` + `EMITIDO`); `ChequeResolver`, `ChequeraResolver.hojasDisponibles` y
   `PagoDetalleCuotaResolver.cheque` revisados (grep completo en F6).
5. **Consolidado de tesorería** (`saldoConsolidado`, `CuentaBancariaRepository.saldoBancarioPorMoneda:29`):
   muestra **saldo contable y disponible** (saldo − reservado) con la reserva desglosada (decidido).

*Etapa 2 — `emitir` al día → `EMITIDO`, solo con la bandera `cheque.al_dia_emitido` prendida:*
- Reserva `+monto`, `fechaPago = fechaEntrega`, sin movimiento.
- **Control de fondos: no bloquea en esta etapa** (decidido 2026-11-12). Si el saldo real no alcanza, se
  emite igual y la respuesta lleva un aviso que el desktop muestra. El bloqueo real sigue al cobrar
  (`registrar` controla contra el saldo real salvo `permiteSaldoNegativo`). **⚠️ A revisar a futuro.**
- Con la bandera apagada, `emitir` hace exactamente lo de hoy.
- Puertas de la bandera: `ChequeGestionService.emitir` es el **único** punto que decide el estado inicial
  (lo llaman `ChequePosGraphQL:45`, `PagoProveedorService:832` y el nuevo `emitirCheques`). Verificar por grep.

**Cuándo se prende:** con central **y** desktop de este PR estables en el canal. Primero alpha, después farmacia
y bodega, cada una por separado.

**Si hay que volver atrás con la bandera prendida:** apagarla **antes** del rollback del JAR y correr
`docs/manuales-implementacion/financiero/reconciliar-saldo-reservado.sql` (nuevo en F6): recalcula
`cuenta_bancaria.saldo_reservado = Σ total` de los cheques `DIFERIDO` + `EMITIDO` no anulados, con dry-run.
Los `EMITIDO` que queden: el script los pasa a `DIFERIDO` con `diferido = true` para que el código viejo los
cobre y libere bien. Los cheques ya `COBRADO` no se tocan.

**Riesgo residual:** alguien tiene que cobrar los cheques al día, y sin conciliación quedan EMITIDO. Mitigación:
lista de pendientes en el tablero con «Cobrar» masivo (F6).

## 5 · Qué queda sin verificar

- Si algún reporte o pantalla fuera de `financiero` lee `cheque.estado = COBRADO` para cheques al día
  (grep completo en F6 antes de cambiar `emitir`).
- La tolerancia exacta de la validación de cotización con montos reales de farmacia (se mide en el dry-run de F4).
- Si `operacion_financiera.numeroComprobante` es editable hoy desde algún camino (F5 asume que sí).
- ~~Las dos entradas de `add-operacion-financiera-dialog`~~: **verificado** por la auditoría —
  `list-operacion-financiera.component.ts:86` y `caja-virtual-dashboard.component.ts:724`.
- **Control de fondos del cheque EMITIDO**: en esta etapa solo avisa. Revisar a futuro si bloquea.

## 6 · Persistencia, PK y rollback

- Tablas tocadas: `caja_virtual`, `caja_virtual_configuracion`, `operacion_financiera`, `movimiento_caja_virtual`,
  `movimiento_bancario`, `cheque`, `chequera`, `cuenta_bancaria` y la nueva `chequera_numero_inutilizado`.
  **PK simple `bigserial id`, central-only** (V112.1, V177.5, V180.5). Los UPDATE por `id` son seguros.
- Todo UPDATE de datos lleva su valor anterior en una columna (`tipo_original`, `cotizacion_original`,
  `descripcion_original`, `fecha_entrega_backfill`), dry-run de la sentencia exacta
  (`frc-cicd/.claude/skills/frc-cicd/runbooks/dry-run-migration.md`) y `RAISE EXCEPTION` si el conteo no coincide.
- **Rollback del JAR con datos nuevos** (va en la descripción del PR):
  - Cajas ex-CAJA_CHICA: el backend viejo las trata como CAJA_MAYOR sin problema.
  - Cotización normalizada: solo se muestra distinta; no se recalcula nada guardado.
  - Números inutilizados: el backend viejo no los saltea y puede emitir uno inutilizado. Aceptado: tabla nueva,
    dato nuevo.
  - `EMITIDO`: ver §4 (apagar la bandera + script de reconciliación).
- **IT con datos previos** (`-Dit.financiero=true`, en local contra una copia, porque el CI no corre Flyway):
  carga cajas CAJA_CHICA, cheque al día COBRADO, DIFERIDO y CAMBIO_DIVISA vieja, aplica V274.5–V279.5 y
  verifica saldos, reservas y montos intactos.

## 7 · Auditoría del plan (paso 5) — hallazgos y qué se hizo

| # | Eje | Sev. | Hallazgo | Acción |
|---|---|---|---|---|
| 1 | A+B | alta | Rollback del central con cheques EMITIDO deja `saldo_reservado` inflado | Etapa 1 tolerante + bandera apagada + script de reconciliación (§4) |
| 2 | A | alta | Desktop viejo no ve ni cobra EMITIDO | La bandera se prende con el desktop ya en el canal (§4) |
| 3 | B | alta | Sin control de fondos al emitir EMITIDO | Decidido: avisa y no bloquea en esta etapa, a revisar (§4) |
| 4 | B | alta | V274.5 y V277.5 no guardan el valor anterior | `tipo_original`, `descripcion_original` (F1, F5) |
| 5 | A | media | Consolidado de tesorería cambia de semántica | Muestra saldo y disponible (§4) |
| 6 | A | media | Consumidores de cheque no nombrados | Agregados al grep de F6 (§4) |
| 7 | A | media | Caja chica de bodega pasa a ser elegible para RRHH | Confirmado por Gabriel (F1) |
| 8 | B | media | Desktop viejo con cotización vieja rechazado | Sin `cotizacionMonedaId` no se rechaza (F4) |
| 9 | B | media | Orden de locks no especificado | chequera → cheque → cuenta (F6) |
| 10 | B | media | Doble reversa al anular un pago con cheque cobrado | Enlace solo visual + test (§4) |
| 11 | B | media | Faltan tests de rollback y datos existentes | IT con datos previos (§6) |
| 12 | B | media | PK no declarada / backfill de fecha no distinguible | §6 + `fecha_entrega_backfill` |
| 13 | A | baja | Búsqueda «op #N» deja de encontrar | `descripcion_original` en el `like` (F5) |
| 14 | B | baja | Backend viejo emite números inutilizados | Aceptado y documentado (§6) |
| — | A | — | Replicación, mobile-pwa, mobile, variables de entorno | Verificado sin hallazgos |
