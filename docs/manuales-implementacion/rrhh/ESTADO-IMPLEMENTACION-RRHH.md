# Estado de implementación — Módulo RRHH (FRC Comercial)

> Documento vivo. Refleja la **situación real** de la implementación del módulo
> nuevo de Recursos Humanos en `franco-system-backend-servidor` (central) y
> `frc-sistemas-integrados-angular` (desktop). Basado en el
> [PLAN-MODULO-RRHH.md](PLAN-MODULO-RRHH.md).

## Manuales del módulo

- **[manual-usuario-rrhh.html](manual-usuario-rrhh.html)** — Manual para el **usuario final**, en lenguaje simple (para qué sirve + pasos de cada función). Imprimible/repartible. El mismo contenido está dentro de la app en **R.R.H.H. → Manual de uso** (`manual-rrhh.component` en el desktop).
- **[manual-rrhh-referencia-tecnica.html](manual-rrhh-referencia-tecnica.html)** — Referencia **técnica** para el equipo de desarrollo: 22 funciones con links directos al código fuente (backend/desktop/mobile), buscador y navegación.

## Leyenda de estado de verificación

- **Compila** — backend: 0 errores en el paquete `rrhh` (el build completo solo
  falla por los archivos SIFEN, dependencia bloqueada en el entorno de trabajo,
  ajeno a RRHH). Desktop: `npm run check` (AOT producción) verde.
- **Tests** — tests unitarios de lógica pura en verde.
- **Pendiente runtime** — falta verificación funcional en un ambiente dev real
  (transacciones, GraphQL end-to-end, UI). Es el paso que sigue.

> **⚠️ Numeración real de migraciones (actualizado 2026-07-29).** Los números
> `V141.0`–`V150.0` citados en las secciones de abajo son los **originales
> pre-merge**. Al integrar `develop` (que ya tenía hasta ~V153), las migraciones
> RRHH se **renumeraron con offset +13 → `V154.0`–`V163.0`**, y las fases
> siguientes agregaron **hasta `V174.0`**. El rango real hoy es **V154.0–V174.0**
> (ver §14). De acá en adelante, **toda migración nueva usa sufijo `.5`**
> (`V175.5__…`) para no colisionar con la normalización de Flyway al mergear
> (`V176` == `V176.0`). La rama RRHH **sigue sin mergear a `develop`**: `develop`
> fue mergeado *hacia adentro* de la feature branch, no al revés.

> **⚠️ `MovimientoPersonas` desvinculado (2026-07, issue #159).** Varias secciones
> abajo describen doble escritura *Caja Mayor + `MovimientoPersonas`* (ledger del
> empleado) al confirmar vales, pagar liquidaciones, etc. **Ese ledger ya no se
> escribe:** resultó *write-only* (nadie lo leía) y mezclaba criterios de signo con
> `VENTA_CREDITO`. Hoy los movimientos de dinero pasan **solo por `CajaVirtual`**;
> la liquidación descuenta leyendo `rrhh.vale` / `rrhh.prestamo_cuota`
> directamente. Donde el texto histórico diga `MovimientoPersonas(...)`, léase
> "sin ledger de personas".

---

## 1. Fundaciones (Fase 0)

**Schema y parámetros base del módulo** — Se crea el schema `rrhh`, la tabla
`configuracion_rrhh` (parámetros key/value tipados) y `liquidacion_concepto`.
Se extiende `personas.funcionario` con 8 columnas aditivas (fecha/motivo de
egreso, código interno, IPS activo/número, valor jornal, moneda, cuenta
bancaria). Migración `V141.0`. Estado: Compila.

**Roles de RRHH** — Se siembran 6 roles: `RRHH VER`, `RRHH GESTIONAR`,
`RRHH LIQUIDAR`, `RRHH APROBAR`, `RRHH PAGAR`, `RRHH CONFIG` (granularidad
simplificada respecto a Gourmet). Estado: Compila.

**Configuración RRHH (CRUD)** — `ConfiguracionRrhh` + servicio + GraphQL, y
pantalla desktop de administración de parámetros. Semilla incluye IPS
(9%/16.5%), recargos de HE (diurna 50, nocturna/feriado 100), horas de jornada
(8), días de vacaciones por antigüedad (12/18/30), indemnización
(15 días/año, mínimo 90 días), penalización por tardanza, prescripción de
vacaciones, etc. Estado: Compila.

---

## 2. Núcleo funcionario ampliado (Fase 1)

**Histórico de cargos** — `funcionario_cargo_historico`: cada cambio de cargo
crea un registro nuevo y cierra el anterior con `fecha_hasta`. Estado: Compila.

**Histórico de salarios** — `funcionario_salario_historico`: snapshot de salario
anterior/nuevo en `numeric(18,2)` (porque `funcionario.sueldo` es `Float` y no
se puede cambiar el tipo). Estado: Compila.

**Legajo digital de documentos** — `funcionario_documento`: metadata + binario
en disco (`ImageService.getImagePath()/rrhh/funcionario-documentos`), lectura
como base64. Tipos (`FuncionarioDocumentoTipo`): cédula, contrato, certificado,
CV, antecedentes, carnet de salud, título, **foto de perfil (`FOTO_PERFIL`)**,
otro. El tipo `FOTO_PERFIL` es dedicado: la foto de perfil se guarda como un
`funcionario_documento` de ese tipo (no como campo en `funcionario`), y el legajo
la usa para el avatar. Estado: Compila.

**Mutaciones dedicadas (cambio de cargo/salario, egreso)** —
`FuncionarioRrhhService`: `cambiarCargo`, `cambiarSalario`, `egresar`
(marca inactivo + fecha/motivo). **No tocan `saveFuncionario`** (usado por el
desktop existente) — regla de retrocompatibilidad §18.1. Migración `V146.0`.
Estado: Compila.

**Pantalla desktop "Legajo funcionario"** (`rrhh/legajo/legajo-funcionario`) —
Cabecera con avatar (foto `FOTO_PERFIL`), identidad, KPIs y accesos rápidos
(cambiar cargo, cambiar salario, subir documento, egresar, liquidación final).
Debajo, `mat-tab-group` con estas pestañas **en este orden**:

1. **Información general** (`informacion-general` component) — **ES EL ALTA/EDICIÓN
   del funcionario**. Se movió acá desde el viejo `AdicionarFuncionarioDialog`
   (eliminado del menú de la lista, que ahora solo abre el legajo). Un funcionario
   **proviene de una persona**: el form busca la persona por documento y, si no
   existe, la crea (`savePersona` con `isFuncionario=true`) antes de guardar el
   funcionario. **No** gestiona cargo/sueldo/crédito/horario (tienen sus propias
   pestañas/diálogos); al editar preserva esos valores para no pisarlos. Campos:
   persona (doc/nombre/nac/sexo/ciudad/dirección/tel/email), datos laborales
   (ingreso/sucursal/supervisor/código interno/activo/fase prueba/diarista), IPS
   (activo + número + fecha ingreso), cuenta bancaria, contacto de emergencia, y
   **foto de perfil** (botón abre el selector del SO directo, sube en un paso como
   documento `FOTO_PERFIL`, avatar en vivo vía `@Output() fotoCambiada`). Layout
   persona 80% / foto 20%.
2. **Financiero** (`financiero-legajo` component, lazy `matTabContent`) — **VISTA
   CONSOLIDADA de solo lectura** de la deuda del funcionario. 4 KPIs (crédito
   convenio, vales pendientes, saldo préstamos, penalizaciones) + card "Exposición
   total" (crédito + vales + préstamos; las penalizaciones no suman, son descuentos
   puntuales) + 4 tablas de detalle. Reusa `ValeService`/`PrestamoService`/
   `PenalizacionService`/`VentaCreditoService`/`ClienteService` (cero backend
   nuevo). El crédito de convenio resuelve `persona → clientePorPersonaId →
   ventaCreditoPorCliente` (encadenado, porque `VentaCredito` cuelga del `Cliente`,
   no del `Funcionario`). No hace CRUD: alta/cobro viven en sus módulos propios.
3. **Cargos** — histórico de cargos.
4. **Salarios** — histórico de salarios.
5. **Asistencia** — placeholder (Fase futura).
6. **Puntuaciones** — placeholder (evaluación 360°).
7. **Documentos** — subir (archivo→base64), ver inline, anular.

En el sidenav de R.R.H.H. Estado: Compila (AOT).

---

## 3. Feriados, novedades, penalizaciones y horas extra (Fase 2)

**Feriados** — CRUD de `feriado` + pantalla desktop. Estado: Compila.

**Novedades de jornada** — `jornada_novedad` (justificado, vacación, permiso,
etc.) + CRUD + pantalla. Estado: Compila.

**Penalizaciones + generación automática** — `penalizacion` + CRUD; job
`PenalizacionScheduler` (diario 5:00 AM) que recorre jornadas con llegada tardía
sin novedad justificada y genera penalización automática. Fórmula
`monto = fijo + porMinuto × minutos` extraída a `PenalizacionCalculator`.
Anular / justificar jornada. Estado: Compila + Tests.

**Horas extra + valorización** — `hora_extra` + CRUD + pantalla. Valorización
`monto = (sueldo/30/horasJornada) × horas × (1 + recargo/100)` en
`HoraExtraCalculator`; `HoraExtraService` valoriza automáticamente al guardar,
tomando el recargo por tipo (diurna/nocturna/feriado) y las horas de jornada de
la configuración RRHH. Migración `V142.0`. Estado: Compila + Tests.

---

## 4. Vales y préstamos (Fase 3)

**Motivos de vale** — CRUD de `motivo_vale` + pantalla. Estado: Compila.

**Vales / anticipos** — `vale` con ciclo `SOLICITADO → CONFIRMADO →
DESCONTADO/ANULADO`. Al confirmar: EGRESO real en la Caja Mayor, en transacción
(sin ledger `MovimientoPersonas`, ver nota arriba); anular desde CONFIRMADO
genera contra-asiento AJUSTE, anular un vale ya DESCONTADO en liquidación tira
excepción. El descuento en liquidación lee `rrhh.vale` directamente (vales
CONFIRMADO sin `liquidacionId`). **Tipo GraphQL renombrado a `ValeRrhhInput`**
(la clase Java sigue siendo `ValeInput`) por colisión con el `input ValeInput`
de `print/print-data.graphqls` (usado por `printVale`, producción). Pantallas de
lista/crear/confirmar/anular. Estado: Compila.

**Préstamos + cuotas** — `prestamo` + `prestamo_cuota`: creación con N cuotas y
desembolso (EGRESO), cobro directo de cuota (INGRESO), plan de cuotas con
`CuotaCalculator` (la última absorbe el redondeo). Job `PrestamoCuotaScheduler`
(diario 6:00 AM) marca cuotas vencidas. Migración `V143.0`. Estado: Compila + Tests.

---

## 5. Vacaciones, aguinaldo y bonos (Fase 4)

**Vacaciones** — `vacacion` + `vacacion_periodo` + `vacacion_venta`.
Devengamiento por antigüedad (`VacacionDevengamientoCalculator`: <5 años 12
días, 5–10 años 18, >10 años 30, parametrizable). Al marcar período GOZADO
genera `jornada_novedad` VACACION por día. Venta de días. Job
`VacacionPrescripcionScheduler` (diario 4:00 AM). Estado: Compila + Tests.

**Aguinaldo** — `aguinaldo`: cálculo anual `sueldo × meses / 12` en
`AguinaldoCalculator`, idempotente por (funcionario, año). Aprobar. Estado:
Compila + Tests.

**Aguinaldo — pago separado (V174.0, nuevo)** — Además de sumarse en la
liquidación de diciembre, el aguinaldo aprobado se puede **pagar por separado**:
`AguinaldoService.pagar(id, cajaVirtualId)` (solo desde `APROBADO`) registra un
EGRESO en la Caja Mayor, setea `caja_virtual_id`/`movimiento_caja_virtual_id`
(columnas nuevas de `V174.0__rrhh_aguinaldo_pago_separado.sql`) y pasa el estado
a `PAGADO`. **Regla de no-doble-pago:** el item automático `AGUINALDO` de la
liquidación mensual de diciembre solo se agrega si el aguinaldo está en estado
**exactamente `APROBADO`** y sin `liquidacionId`. Como `pagar()` lo mueve a
`PAGADO`, un aguinaldo pagado por separado **deja de matchear el filtro y no se
vuelve a sumar** — no hay flag "excluir" explícito, es efecto del filtro de
estado. Frontend: `PagarAguinaldoDialogComponent` (elige Caja Mayor) + botón en
la lista de aguinaldos, que tras pagar ofrece el recibo. Estado: Compila +
verificado runtime.

**Bonos** — `bono` (incluye recurrentes) + CRUD + pantalla. Migración `V144.0`.
Estado: Compila.

---

## 6. Liquidación de sueldo (Fase 5) — el motor

**Motor de liquidación** — `LiquidacionSueldoService.generarBorrador`: arma los
items automáticos del período — SALARIO_BASE, IPS, HORA_EXTRA (de las HE
consolidadas), PENALIZACION, BONO, VALE/ADELANTO, PRESTAMO_CUOTA (cuotas
vencidas), AGUINALDO, VACACION_VENTA — preservando los items manuales.
Totales con `LiquidacionCalculator` (neto = Σ haberes − Σ descuentos). Estado:
Compila + Tests.

**Rama jornalero** — Para funcionarios `diarista`, el salario base se calcula
como `valor_jornal × días trabajados` (de las jornadas del período), vía
`JornaleroCalculator`, en lugar del sueldo fijo mensual. Estado: Compila + Tests.

**Ciclo de estados y pago** — `BORRADOR → APROBADA → PAGADA / ANULADA`.
`aprobar`/`volverBorrador`; `pagar` (EGRESO neto en Caja Mayor + efectos
cruzados: VALE→DESCONTADO, CUOTA→PAGADA, AGUINALDO→PAGADO, VACACION_VENTA→PAGADO;
sin ledger `MovimientoPersonas`, ver nota arriba); `anular` (contra-asiento
AJUSTE + reversión). Generación masiva mensual. Migración `V145.0`. Estado:
Compila.

**Recibo de sueldo (PDF)** — Plantilla Jasper `recibo-liquidacion.jrxml`
(cabecera + tabla de items + totales), `ReciboLiquidacionService.generarBase64`
y query `imprimirReciboLiquidacion(id)`. Fuentes fijadas a `SansSerif` (fuente
lógica, sin riesgo de instalación). Estado: Compila; plantilla **compila y hace
fill OK** (validado con la librería Jasper); **verificación visual del PDF
pendiente** (el export final usa iText, presente en el ambiente real).

**Pantallas desktop** — Lista de liquidaciones (por funcionario/período),
generar (individual/masivo), **detalle en TAB** con items, acciones por estado,
pago vía Caja Mayor, y "Ver Recibo" (visor PDF integrado). Estado: Compila (AOT).

**Ítems editables + auditoría ("todo es negociable")** — En BORRADOR se puede
**editar cualquier ítem** (auto o manual), agregar ítems HABER/DESCUENTO y
eliminar. Mutations: `agregarItemLiquidacion`, **`editarItemLiquidacion`**
(nueva), `eliminarItemLiquidacion`. Editar registra auditoría: `editado`,
`editado_por_id`, `editado_en` y `monto_original` (valor pre-edición) — migración
`V171.0`. El total sigue siendo Σ haberes − Σ descuentos (`aplicarTotales`). En
la UI: panel dual alta/edición + ícono "editado" con tooltip (quién / monto
original). Estado: Compila.

---

## 7. Liquidación final / finiquito (Fase 6)

**Motor de finiquito** — `LiquidacionFinalCalculator` (puro): antigüedad
(días/meses/años), indemnización (solo DESPIDO_INJUSTIFICADO y antigüedad ≥
mínimo configurable: `salarioPromedio/30 × díasPorAño × max(1, años)`),
vacaciones no gozadas (`días × salarioPromedio/30`), aguinaldo proporcional, y
total. Estado: Compila + Tests.

**Preaviso (ley laboral Paraguay)** — Días configurables por antigüedad (Hasta
1 año = 30, 1–5 años = 45, 5–10 años = 60, +10 años = 90; 4 claves de config
nuevas). Matriz legal: despido injustificado **sin** preaviso → concepto
**HABER** (paga los días completos); renuncia **sin** preaviso → concepto
**DESCUENTO** de la **mitad** de los días. Columnas nuevas en
`liquidacion_final`: `preaviso_otorgado`, `preaviso_dias`, `preaviso_monto`,
`preaviso_es_descuento`. Editable en el diálogo de generación (toggle "¿se
otorgó?" + días). Migración `V173.0`. Estado: Compila (verificado en dev, ver
más abajo).

**Salario del mes (días trabajados)** — El primer ítem HABER del finiquito ya
no es fijo: es el sueldo base mensual (`funcionario.sueldo`) / 30 × días
trabajados del mes de egreso (default = día del mes de la fecha de egreso,
editable en el diálogo). Concepto `SALARIO_MES`.

**Servicio de finiquito** — `LiquidacionFinalService.generarBorrador`: reúne
salario promedio (últimas 6 liquidaciones aprobadas/pagadas), días de vacaciones
no gozadas, aguinaldo del año, salario del mes y preaviso; arma items HABER
(`LiquidacionFinalConcepto`: SALARIO_MES, INDEMNIZACION, PREAVISO,
VACACIONES_NO_GOZADAS, AGUINALDO_PROPORCIONAL, MANUAL). Estados
BORRADOR/APROBADA/PAGADA/ANULADA; `pagar` (EGRESO Caja Mayor +
`funcionario.activo=false`); `anular` (contra-asiento). Migración `V147.0`.
Estado: Compila.

**Ítems editables (paridad con la mensual, "todo es negociable")** — El
`liquidacion_final_item` ganó `tipo` (HABER/DESCUENTO), `manual`, auditoría de
edición (`editado`/`editado_por_id`/`editado_en`/`monto_original`) y
`referencia_id`/`referencia_tipo` — migraciones `V170.0` + `V172.0`. **El total
pasó de fijo a Σ haberes − Σ descuentos** (`recalcularTotal`). Mutations:
`agregarItemLiquidacionFinal`, `editarItemLiquidacionFinal`,
`eliminarItemLiquidacionFinal` (todas guard BORRADOR).

**Descuentos automáticos del funcionario** — Al generar el borrador se agregan
como ítems DESCUENTO (editables) las mismas obligaciones que la mensual:
**IPS** (`IPS% × salario promedio`, sobre base configurable), **vales/adelantos**
CONFIRMADO, **cuotas de préstamo** pendientes (con `referencia_tipo=CPP_CUOTA`), y
**crédito por convenio** (saldo de `VentaCredito` ABIERTO/EN_MORA/INCOBRABLE del
funcionario como cliente, agregado sin referencia). Cada uno con su propio toggle
en el diálogo de generación. Al **pagar**, `aplicarEfectosCruzados` salda los
orígenes (vales→DESCONTADO, cuotas→PAGADA); al **anular** los revierte. El crédito
por convenio se descuenta pero su reconciliación en el módulo financiero queda
como follow-up (cross-módulo). **Guaraníes enteros:** todos los montos se
redondean a escala 0 en el cálculo (sin decimales para Gs); display con pipe
`1.0-0` e inputs con `currencyMask` guaraní.

**Diálogo de generación enriquecido** — `LiquidacionFinalGenerarInput` + query
`previewLiquidacionFinal`: precarga editable de fecha de ingreso, salario
promedio (formato Gs), días trabajados del mes, preaviso otorgado + días, días
de vacaciones no gozadas, aguinaldo proporcional (Gs), base IPS (Gs), y toggles
`descontarIps` (default según `funcionario.ipsActivo`) / `cobrarVales` /
`cobrarConvenios` / `cobrarPrestamos` / `descontarPenalizaciones` (default true).
Botón **"Regenerar"** reabre este diálogo sobre un finiquito en BORRADOR.

**Verificación end-to-end en DB (ESTEBAN, finiquito id 4)** — Despido
injustificado sin preaviso: SALARIO_MES + indemnización 8.654.167 + preaviso
HABER 6.490.625 + vacaciones 1.009.653 + aguinaldo 360.590 − IPS 389.437 − 2
cuotas 400.000 − penalización 80.000, todos los montos enteros. Renuncia sin
preaviso: preaviso DESCUENTO 3.245.313 (mitad de los días), sin indemnización,
22 días trabajados (egreso 2026-07-22). **Pendiente:** probar el pago end-to-end
con saldado de vales/cuotas si aún no se hizo.

**Pantalla desktop** — Lanzada desde el Legajo (acceso rápido "Liquidación
final"): **diálogo enriquecido** de parámetros → al generar abre el
**finiquito en TAB** (si ya existe uno vigente, abre el tab directo). En el tab:
desglose legal sugerido + **tabla de ítems editable** (editar/agregar
HABER-DESCUENTO/eliminar), total por suma, pago vía Caja Mayor y **"Ver Recibo"
en el visor PDF integrado** (se corrigió el `window.open`, bloqueado en Electron).
Estado: Compila (AOT).

**Recibo del finiquito (PDF) — rediseñado** — `recibo-finiquito.jrxml`:
"LIQUIDACION FINAL DE HABERES", tabla bordeada de datos (Empresa/Trabajador/
Entrada/Salida/Antigüedad/Salario/Jornal diario), tabla Concepto/Monto
(descuentos entre paréntesis), TOTAL A LIQUIDAR, cláusula con el monto en
letras, firma con C.I. El nombre de empresa sale de la razón social
(`empresarial.configuracion_general`, no la sucursal). Sin total en la esquina
superior. Fuente `SansSerif`. Se muestra en el visor PDF integrado (no
`window.open`, bloqueado en Electron). Estado: Compila; validado con test
automatizado (`ReciboFiniquitoJrxmlTest`: compila + fill + export);
verificación visual manual en dev pendiente.

---

## 8. Elementos transversales

**Integración con Caja Mayor (fd-93)** — Todos los movimientos de dinero (vales,
préstamos, pago de liquidaciones y finiquitos, y el pago separado de aguinaldo)
se registran como movimientos en `CajaVirtual` tipo `CAJA_MAYOR`
(`MovimientoCajaVirtualService.registrarMovimiento` valida saldo). **Ya NO se
escribe en `MovimientoPersonas`** (ledger del empleado desvinculado en 2026-07,
issue #159 — ver nota al inicio del doc): la liquidación descuenta leyendo
`rrhh.vale`/`rrhh.prestamo_cuota` directamente. El desktop consulta las cajas
activas por su propia query GraphQL. Estado: Compila.

**Calculadoras puras + tests unitarios** — 8 clases en `service/rrhh/builder`
(Liquidacion, LiquidacionFinal, Penalizacion, Aguinaldo, Cuota, Jornalero,
HoraExtra, VacacionDevengamiento), con **39 tests JUnit5 en verde** (validados
de forma aislada; la lógica de dinero queda cubierta sin depender de Spring/DB).

**Jobs programados** — 3 schedulers: penalizaciones (5:00), cuotas de préstamo
(6:00), prescripción de vacaciones (4:00). Cron parametrizable por properties.
Estado: Compila.

**Convención de reportes Jasper** — Documentada en `CLAUDE.md` y
`.cursor/rules/jasper-reports.mdc`: nunca introducir fuentes nuevas (usar solo
`SansSerif`/`Verdana` ya en uso; preferir `SansSerif`), validar la plantilla
localmente antes de pushear.

**E2E desktop (esqueleto)** — `e2e/rrhh-liquidacion.spec.ts` (Playwright+Electron)
del flujo de liquidación, guardado con `RRHH_E2E=1` para correr en dev real.
Doc de estrategia de tests en `docs/.../rrhh/TESTING-RRHH.md` (desktop).

---

## 8.1 Configuración y flexibilidad

**Valores parametrizables (dejaron de estar hardcodeados)** — Se exponen en la
configuración RRHH: `TOLERANCIA_TARDANZA_MIN` (corrige un bug: antes la
penalización automática ignoraba la tolerancia), `DIA_CIERRE_MES` (día de cierre
de nómina; 28–31 = mes calendario), `MESES_PROMEDIO_LIQUIDACION_FINAL` (antes
"6" fijo), `DIAS_MES_PROMEDIO` (30) y `DIAS_ANIO_ANTIGUEDAD` (365). Migración
`V148.0`. Calculadoras parametrizadas + tests (40 casos). Estado: Compila + Tests.

**Pantalla de Configuración RRHH — rediseñada (curada, no CRUD)** —
`PanelConfiguracionRrhhComponent` reemplazó la lista editable con crear/eliminar.
Las 28 claves (24 + las 4 de preaviso sumadas por `V173.0`) son un **catálogo
fijo de desarrollo** (nacen por migración/seed):
el usuario final **solo ajusta valores**, no crea ni elimina claves. UI: **tabs
por sección** (IPS y aportes · Indemnización y finiquito (incluye preaviso) ·
Vacaciones · Aguinaldo · Horas extra · Tardanza y penalización · General), cada
campo con su **widget**
según tipo (número+unidad, `%`, `Gs`, toggle boolean, selector de mes), label
legible + texto de ayuda + ícono info. Guarda al cambiar cada campo. La metadata
(secciones + widgets) vive en el frontend (`configuracion-rrhh-catalogo.ts`); los
valores siguen en el backend (cero migración). Clave nueva no mapeada → cae en la
tab "Otros". Preserva el **diálogo de info** curado por clave y la **cascada
guiada del salario mínimo** (al subirlo, ofrece —no impone— ajustar a los
funcionarios que quedan por debajo, TODO-8). Los componentes viejos (lista/editar/
eliminar) quedan declarados pero sin uso. Estado: Compila (AOT).

## 8.2 Dashboard y reportes (Fase 8)

**Dashboard RRHH — reconstruido al padrón `desktop/docs/DASHBOARDS.md`** — El
dashboard original era una grilla plana de 11 cards custom (colores propios,
sin filtro de rango, sin chart, sin rankings, sin accesos), fuera del padrón
del producto. Se reconstruyó siguiendo el dashboard de Devoluciones
(implementación de referencia): `dash-header` (título "RRHH" + subtítulo +
filtro de **período mensual** + botón Aplicar) → `dash-stats-row` con **4 KPIs
headline** (`dash-stat-chip`, colores semánticos primary/success/warning/error/
info): funcionarios activos (info), nómina del mes (primary, moneda),
liquidaciones pendientes (warning si >0), vacaciones por vencer (warning si >0)
→ `dash-main` en 2 columnas. Izquierda: `dash-chart-card` (echarts) con
**nómina por mes, últimos 12 meses** (barras = monto neto, línea = nº
liquidaciones; serie desacoplada del filtro de período) + ranking "Cumpleaños
del mes". Derecha: ranking "Top exposición financiera" (vales pendientes +
saldo de préstamos por funcionario) + "Top horas extra del mes". `dash-quick-
actions`: Liquidaciones · Vales · Préstamos + botón **Reportes** (mat-menu,
ver abajo). SCSS del componente reducido a `:host` scroll (el look vive en
`_dashboard.scss` global). Backend nuevo, aditivo, sin migración: en
`dashboard-rrhh.graphqls` se agregaron tipos `RrhhSeriePunto`/
`RrhhRankingItem`/`RrhhCumpleanos` y queries `nominaSeriePorMes(periodoInicio,
periodoFin)`, `rrhhTopExposicion(limite)`, `rrhhTopHorasExtra(periodo, limite)`,
`rrhhCumpleanosDelMes(periodo)` y **`rrhhFuncionariosIncompletos(page, size)`**.
Resolver `DashboardRrhhGraphQL` + `DashboardRrhhService` (agregación) + DTOs
`RrhhSeriePuntoDto`/`RrhhRankingItemDto`/`RrhhCumpleanosDto` + native query
`nominaSeriePorMesRaw` en `LiquidacionSueldoRepository` (agrupa
`liquidacion_sueldo` por período, solo APROBADA/PAGADA). La query KPI previa
`dashboardRrhhKpis(periodo)` se mantiene (alimenta los 4 KPIs headline). Service
con `silentLoad=true` (`onCustomQuery(..., true)`), sin diálogo de carga global.

**Card "Legajos por completar"** (`rrhhFuncionariosIncompletos`, paginada) —
Reemplazó al "Funcionario del mes". El backend calcula un **score 1-10** por
funcionario evaluando 8 campos clave del legajo (cargo, salario>0, fecha ingreso,
documento, nacimiento, sexo, dirección, teléfono): `score = round(cumplidos/8 ×
10)`, mínimo 1. **Filtra inactivos y egresados** server-side
(`if (!activo || fechaEgreso != null) continue`). El frontend lista los peores
primero (badge rojo si score≤3), paginado (8/pág), y **click en la fila abre el
legajo** (`new TabData(id, {id})`) para completarlo. **Cumpleaños del mes** se
reubicó a la columna derecha de "tops" con `maxAlto="240px"` (scroll interno).
Estado: Compila (backend + AOT desktop); **falta test manual del usuario** (ver
T16 en `PLAN-TESTEO-MANUAL-RRHH.md`).

**Reportes RRHH — reubicados dentro del dashboard (sin tab dedicado)** — Se
**eliminó** el componente/tab dedicado `ReportesRrhhComponent` (era un
componente entero solo para 5 botones), junto con su entrada en el menú
lateral (`side-mini-variant`, action `reportes-rrhh`) y su declaración de
módulo. Los 5 reportes Jasper (**nómina del mes** — liquidaciones aprobadas/
pagadas del período —, **resumen IPS** — base salarial + aporte funcionario 9%
y patronal 16.5% desde config —, **vales pendientes** — solicitados/
confirmados —, **préstamos activos** — con saldo pendiente — y **aguinaldo
anual**; los tres últimos reusan la plantilla genérica de 4 columnas
`reporte-rrhh-generico.jrxml`, evitando triplicar templates) ahora se acceden
desde un **mat-menu** en el quick-action "Reportes" del dashboard, usando el
período del dashboard. La lógica de generación (`ReportesRrhhService` + sus
queries GraphQL) se conserva intacta. **Bug arreglado (B29):** el
`ReportesRrhhComponent` mostraba el PDF con `window.open()`, que está
**bloqueado en Electron** (mismo caso que B22/B4) — el tab no mostraba el PDF.
Ahora los reportes se muestran en el **visor integrado**
(`ReporteService.onAdd` + tab `ReportesComponent`). Fuente SansSerif;
validados (compilan y hacen fill OK con datos dummy). Estado: Compila; **falta
test manual del usuario** (5 PDF con datos reales, ver T17 en
`PLAN-TESTEO-MANUAL-RRHH.md`).

**Recibo de finiquito (PDF)** — `recibo-finiquito.jrxml` + `finiquitoBase64(id)`
en `ReporteRrhhService`, **rediseñado** (ver detalle en §7): tabla legal
Empresa/Trabajador/Entrada/Salida/Antigüedad/Salario/Jornal + tabla
Concepto/Monto (descuentos entre paréntesis) + TOTAL A LIQUIDAR + monto en
letras + firma con C.I. Botón **Imprimir recibo** en el diálogo de liquidación
final (estados APROBADA/PAGADA), visor PDF integrado. SansSerif; validado con
test `ReciboFiniquitoJrxmlTest` (compila+fill+export).
Estado: Compila; **verificación visual manual del PDF pendiente en dev**.

**Recibos firmables (7) + impresión PDF / Ticket ESC/POS** — Todo comprobante
firmable de RRHH se imprime con el **componente oficial de impresión**
(`shared/components/imprimir/`, ver [desktop/docs/IMPRESION.md](../../../../desktop/docs/IMPRESION.md)),
que ofrece **PDF (A4) o Ticket térmico (58/80mm)**. Los **7 recibos**:
vale, penalización, aguinaldo, préstamo (entrega/desembolso), bono, **finiquito**
y **liquidación mensual**.

Backend — cada recibo expone **una** query con `anchoMm: Int` y `escpos: Boolean`
(nullables). Firma uniforme de los métodos de servicio:
`(Long id, Integer anchoMm, boolean escpos)`. Tres salidas:
- `escpos=false, anchoMm=null` → **PDF A4** (Jasper, plantilla dedicada o
  `recibo-rrhh.jrxml` genérica).
- `escpos=false, anchoMm=58|80` → **PDF ticket angosto** (preview en el visor,
  `recibo-ticket-58.jrxml`/`recibo-ticket-80.jrxml`).
- `escpos=true` → **payload ESC/POS crudo** (base64), generado por
  `utilitarios/print/ReciboTicketEscPos.build(...)`, para imprimir directo en la
  térmica local del cliente vía `electron print-local` (`lp -o raw`, sin
  PostScript). `cols = anchoMm>=80 ? 48 : 32`; **el concepto se envuelve a varias
  líneas sin truncar** (`filaConcepto`/`wrapAncho`); `na()` quita acentos (térmica
  RAW no soporta codepages con tildes). Los recibos de los 5 conceptos pasan por
  el builder común `ReporteRrhhService.reciboRrhh`; liquidación mensual por
  `ReciboLiquidacionService`. Resolvers `ReporteRrhhGraphQL`/
  `LiquidacionSueldoGraphQL` reciben `Boolean` y hacen `Boolean.TRUE.equals(escpos)`.

Los **reportes agregados** (nómina del mes, resumen IPS, vales pendientes,
préstamos activos, aguinaldo anual) quedan **solo PDF** (no tiene sentido un
listado tabular en ticket). Estado: Compila + **verificado runtime** (PDF y
ticket ESC/POS probados, incluido el wrap de conceptos largos en penalización).

**Notificaciones RRHH (job)** — `RrhhNotificacionScheduler` genera alertas
diarias resolviendo destinatarios por rol vía
`NotificacionPreferenciaService`/`PushNotificationService`. Cubre: cuotas de
préstamo vencidas, **cumpleaños del día**, **documentos del legajo por vencer**
(`DOCUMENTO_AVISO_VENCIMIENTO_DIAS`, default 30) y **vacaciones por prescribir**
(`VACACION_AVISO_PRESCRIPCION_DIAS`, default 60). Config sembrada en `V150.0`.
Estado: Compila.

**Historial de marcaciones (desktop)** — Acceso **Historial de marcaciones** en
el menú RRHH que reutiliza `ListMarcacionComponent` (ya existente en
`administrativo`, con filtros por usuario/fechas e impresión), sin duplicar
pantalla. Estado: AOT desktop verde.

## 8.3 Self-service mobile — backend

**Endpoints `*Mobile`** — Base backend para el mobile (regla del sufijo, sin
modificar lo del desktop). Consulta: `misRecibosMobile` (liquidaciones pagadas),
`misValesMobile`, `misVacacionesMobile`, `misMarcacionesMobile`,
`miResumenRrhhMobile` (saldo vacaciones + vales pendientes + último recibo).
Solicitudes: `solicitarValeMobile` (→ SOLICITADO), `solicitarVacacionMobile`
(→ período SOLICITADA). Aprobaciones (directivo): listas de pendientes +
`aprobarVacacionMobile` (sin movimiento de dinero). Todo scopea por `usuarioId`.
Estado: Compila.

## 8.4 Self-service mobile — pantallas Ionic (`frc-mobile`)

**Pantalla "Mis RRHH"** — Resumen (saldo de vacaciones, vales pendientes,
último recibo) + segmentos Recibos / Vales / Vacaciones / Marcaciones.
Ver recibo en PDF (`pdf-viewer.service` desde el base64 del backend).
Solicitar vale y solicitar vacaciones (alerts) → quedan SOLICITADO/SOLICITADA.
Estado: **Build verde** (`npm run build`).

**Pantalla "Aprobaciones RRHH"** (rol DIRECTIVO/ADMIN) — Vacaciones pendientes
con aprobar; vales pendientes en solo lectura (la confirmación/pago se hace en
desktop, requiere Caja Mayor). Acceso desde el home gateado por rol.
Estado: **Build verde**. Solo código Angular → se propaga por OTA
(CapacitorUpdater), sin release nativo a Play Store.

## 8.5 Seguridad por roles (RRHH-only)

**Contexto (issue #177):** el backend GraphQL del producto **no tiene autorización
por roles funcional** — el pipeline JWT→Authentication está roto (`JwtUser.setRoles`
no asigna, `JwtValidator` no lee el claim `roles`), así que `@AdminSecured` nunca
matchea y todo método sin anotación queda protegido **solo por login**. Es un
problema **sistémico** (no solo RRHH) a resolver en sesión aparte (toca auth
compartido, ver `REPORTE_VULNERABILIDADES.md`).

**Mitigación RRHH-only aplicada** (`service/rrhh/RrhhSecurityService`): control de
acceso self-contained que resuelve al usuario autenticado desde el principal del
`SecurityContext` (el nickname sí se setea) y lee sus roles desde `personas.usuario_role`,
**sin tocar el pipeline JWT**. Bypass de superusuario: rol `ADMIN` o nickname `ADMIN`.

- **Mutations** (todas las sensibles): `requireAnyRole(...)` según la matriz —
  `RRHH PAGAR` (pagar/anular liq/finiquito/aguinaldo), `RRHH APROBAR` (aprobar
  liq/finiquito/aguinaldo, confirmar vale, aprobar vacaciones), `RRHH LIQUIDAR`
  (generar/items/calcular), `RRHH GESTIONAR` (vales/préstamos/bonos/HE/penalizaciones/
  vacaciones/documentos/cargo/catálogos), `RRHH CONFIG` (configuración/ajuste salario
  mínimo/cambiar salario).
- **Queries sensibles**: `requireVer()` (cualquier rol RRHH) — liquidaciones,
  finiquitos, aguinaldos, dashboard, configuración, historial de cargo/salario,
  documentos, y los listados de vale/préstamo/vacación/bono/HE/penalización. Los
  recibos por-id y los endpoints `*Mobile` **no** se gatean (self-service; el mobile
  además ignora el `usuarioId` del cliente y usa el autenticado).
- **Frontend**: el menú ya gateaba por `visibilityRoles`; se agregó gating de
  **botones** (Aprobar/Pagar/Anular en liquidación/finiquito, pagar aguinaldo,
  confirmar vale, aprobar vacaciones, cambiar cargo/salario/egresar en el legajo,
  ajuste salario mínimo) con flags calculados en `ngOnInit` vía
  `mainService.tieneAlgunRol([...])` (mismo bypass ADMIN). Es **UX**: el backend es
  la seguridad real.

> ⚠️ **Operativo:** al desplegar RRHH hay que **asignar los roles** a los usuarios
> (`RRHH VER/GESTIONAR/LIQUIDAR/APROBAR/PAGAR/CONFIG`), o quedan bloqueados. ADMIN
> mantiene acceso por el bypass. `COMISION GESTIONAR/APROBAR` siguen sin uso (no hay
> módulo de comisiones).

> **Regla para nuevas implementaciones RRHH:** toda mutation nueva debe llamar
> `seg.requireAnyRole(...)` con el rol adecuado, y toda query que exponga datos de
> nómina/personales `seg.requireVer()`; en el frontend, gatear el botón con un flag
> de `mainService.tieneAlgunRol([...])`. Ver skill `rrhh-expert` → `seguridad-roles.md`.

---

## 9. Ramas de trabajo

Cada fase quedó en su feature branch (backend y desktop, mismo nombre):
`feature/rrhh-fase-1-funcionario-ampliado`,
`feature/rrhh-fase-6-liquidacion-final`,
`feature/rrhh-pendientes-motor-recibo` (jornalero + valorización HE + recibo +
refuerzo de tests + alineación de config keys), además de las fases 0/2/3/4/5
previas. **Ninguna está mergeada a `develop` todavía.**

---

## 10. Pendiente (o a considerar pendiente)

### Verificación (bloqueante antes de producción)
- **Pruebas funcionales en ambiente dev real** — Todo el módulo está compilado y
  con tests de lógica pura, pero **falta verificación runtime**: transacciones de
  caja, efectos cruzados del pago de liquidaciones, generación real de jornadas
  por vacación, jobs programados, y round-trips GraphQL.
- **Verificación visual del recibo PDF** — La plantilla Jasper compila y hace
  fill, pero el PDF final (render + itext) debe revisarse en el ambiente dev.
- **Tests de integración del motor** — `generarBorrador` armando items desde
  vales/préstamos/bonos/aguinaldos y los efectos cruzados requieren Spring + DB;
  correr contra el dev real.

### Fases del plan no implementadas
- **Fase 7 — Comisiones** — Motor de reglas sobre ventas, equipos con reparto,
  liquidación de comisiones e integración como HABER en la liquidación de sueldo
  (el hook ya existe en Fase 5). No implementado. Alto riesgo: depende del modelo
  de ventas real (`operaciones.venta`, campo vendedor). **Se deja para el final.**
- **Fase 8 — Notificaciones** — **Hecha** (§8.2). Dashboard con KPIs (incluidos
  cumpleaños del mes y vacaciones por vencer), reportes (nómina, IPS, vales,
  préstamos, aguinaldo), recibo de finiquito PDF y el **job de notificaciones
  RRHH** (cuota vencida, cumpleaños, documento por vencer, vacación por
  prescribir) vía FCM/rol. Falta solo verificación runtime en dev.

### Mobile — pendientes puntuales
- **Push FCM al aprobador** — Las solicitudes (vale/vacación) desde el mobile
  se crean en estado SOLICITADO/SOLICITADA, pero el backend **todavía no emite
  el push** al aprobador. Falta wirear la emisión FCM (el módulo de
  notificaciones existe). Va junto con el job de notificaciones de la Fase 8.
- **Verificación en device/emulador** — Las pantallas Ionic compilan
  (`npm run build`), pero falta probarlas en un device/emulador contra el
  backend real (login, datos, apertura de PDF con FileOpener).
- Nota histórica: las pantallas Ionic (Mis RRHH + Aprobaciones) **ya están
  hechas** (§8.4). Lo que sigue abajo aplica solo al repo
  mobile.

### Mejoras / deuda menor
- ~~**Recibo de finiquito (PDF)**~~ — **Hecho** (§7/§8.2): plantilla Jasper
  rediseñada (formato legal Empresa/Trabajador/Entrada/Salida/Antigüedad/Salario/
  Jornal, descuentos entre paréntesis, TOTAL A LIQUIDAR, monto en letras, firma
  con C.I.), botón Imprimir recibo en el diálogo de liquidación final, validada
  con test `ReciboFiniquitoJrxmlTest`. Verificación visual manual en dev pendiente.
- **Reconciliación del crédito por convenio en el finiquito** — El finiquito
  descuenta el saldo de compras a crédito del funcionario, pero **no salda las
  `VentaCredito`** al pagar (cross-módulo financiero). Hoy queda como conciliación
  manual. Evaluar finalizar esas ventas a crédito automáticamente al pagar.
- ~~**Historial propio de marcaciones**~~ — **Hecho** (§8.2): acceso en el menú
  RRHH reutilizando `ListMarcacionComponent`.
- **Planilla legal de IPS (REI)** — El plan (§20 #7) la deja como mejora
  posterior; solo se calcula el % de IPS.
- **`funcionario.sueldo` es `Float`** — Precisión monetaria pobre; las
  liquidaciones snapshottean en `numeric(18,2)`. Migrar a `sueldo_decimal` con
  estrategia de 2 versiones queda como mejora futura (§20 #3).

---

## 11. Gotchas técnicos (sesión legajo / alta-in-legajo / financiero)

Lecciones y fixes de la iteración que movió el alta al legajo y agregó el tab
Financiero. Todos están **arreglados en código**.

1. **`saveFuncionario` nulaba persona/cargo/sucursal en update parcial**
   (`FuncionarioGraphQL`). ModelMapper mapea los `null` del input sobre la entidad
   ya cargada. Fix: capturar `personaActual/cargoActual/sucursalActual` **antes**
   de nular las relaciones y restaurarlas si el input no las trae. **Gotcha de
   testeo:** probar con mutations crudas parciales sobre un funcionario real le
   nula datos (nos pasó con ESTEBAN #8: sueldo/activo/cargo/sucursal). El form real
   es seguro porque manda todos los campos; **no testear con mutations parciales
   sobre el sujeto principal.**

2. **`savePersona` no persistía `ciudadId`** (`PersonaGraphQL`). El resolver nunca
   seteaba la ciudad (mapeaba nombre/doc/tel/etc. pero no ciudad). Fix: inyectar
   `CiudadService` + `if (input.getCiudadId() != null) e.setCiudad(...)`. `savePersona`
   usa guards `if != null` en cada campo → es update parcial seguro, **nunca nula**.

3. **NPE `Cliente.getCredito()` null** en `saveFuncionario` al comparar crédito de
   un cliente viejo sin crédito. Fix: `java.util.Objects.equals(...)` null-safe.

4. **Campo inexistente en la query GraphQL tumba TODO el resultado.** Un
   `imagenPrincipal` pedido en `funcionarioQuery` (no existe en el tipo `Funcionario`)
   hacía fallar la query entera → `funcionario` null → el legajo se ocultaba salvo
   el primer tab. Apollo no ignora campos inexistentes: es error de validación, no
   omisión. Al agregar campos a una query, verificar que existan en el schema.

5. **[ARREGLADO] `application.properties:19` tenía una corrupción commiteada
   desde `56113ee5`:** `spring.datasource.username=francospring.profiles.active=user-dev`
   (dos líneas fusionadas), que hacía FATAL el arranque sin el profile `dev`
   (`FATAL: role "francospring.profiles.active=user-dev" not exist`; solo se
   "tapaba" cuando `application-user-dev.properties` sobreescribía el username a
   `franco`). Fix: se corrigió la línea a `spring.datasource.username=franco`,
   eliminando el fragmento fusionado (nunca debió estar en el base).

6. **Persona es compartida entre roles.** Editar nombre/doc/etc. desde el tab
   Información general modifica la **misma fila `persona`**, visible en todos sus
   roles (cliente/proveedor/usuario). Es por diseño ("un funcionario proviene de
   una persona"). No hay forma de editar "solo el legajo" sin tocar la persona
   compartida.

7. **`mat-tab` renderiza su contenido eager por default.** Para no disparar las
   queries del tab Financiero en cada apertura del legajo, se envuelve en
   `<ng-template matTabContent>` (lazy: instancia al entrar al tab).
