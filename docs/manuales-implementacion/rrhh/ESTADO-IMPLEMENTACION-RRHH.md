# Estado de implementación — Módulo RRHH (FRC Comercial)

> Documento vivo. Refleja la **situación real** de la implementación del módulo
> nuevo de Recursos Humanos en `franco-system-backend-servidor` (central) y
> `frc-sistemas-integrados-angular` (desktop). Basado en el
> [PLAN-MODULO-RRHH.md](PLAN-MODULO-RRHH.md).

## Leyenda de estado de verificación

- **Compila** — backend: 0 errores en el paquete `rrhh` (el build completo solo
  falla por los archivos SIFEN, dependencia bloqueada en el entorno de trabajo,
  ajeno a RRHH). Desktop: `npm run check` (AOT producción) verde.
- **Tests** — tests unitarios de lógica pura en verde.
- **Pendiente runtime** — falta verificación funcional en un ambiente dev real
  (transacciones, GraphQL end-to-end, UI). Es el paso que sigue.

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
como base64. Tipos: cédula, contrato, certificado, CV, antecedentes, carnet de
salud, título, otro. Estado: Compila.

**Mutaciones dedicadas (cambio de cargo/salario, egreso)** —
`FuncionarioRrhhService`: `cambiarCargo`, `cambiarSalario`, `egresar`
(marca inactivo + fecha/motivo). **No tocan `saveFuncionario`** (usado por el
desktop existente) — regla de retrocompatibilidad §18.1. Migración `V146.0`.
Estado: Compila.

**Pantalla desktop "Legajo funcionario"** — Selector de funcionario + resumen
(cargo/salario/estado), tablas de históricos de cargo y salario, documentos
(subir vía archivo→base64, ver inline, anular) y acciones (cambiar cargo,
cambiar salario, egresar, liquidación final). En el sidenav de R.R.H.H.
Estado: Compila (AOT).

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
DESCONTADO/ANULADO`. Al confirmar: `MovimientoPersonas(ANTICIPO)` + EGRESO real
en la Caja Mayor, en transacción; anular genera contra-asiento. Pantallas de
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
`aprobar`/`volverBorrador`; `pagar` (EGRESO neto en Caja Mayor +
`MovimientoPersonas(PAGO_SALARIO)` + efectos cruzados: VALE→DESCONTADO,
CUOTA→PAGADA, AGUINALDO→PAGADO, VACACION_VENTA→PAGADO); `anular` (contra-asiento
AJUSTE + reversión). Generación masiva mensual. Migración `V145.0`. Estado:
Compila.

**Recibo de sueldo (PDF)** — Plantilla Jasper `recibo-liquidacion.jrxml`
(cabecera + tabla de items + totales), `ReciboLiquidacionService.generarBase64`
y query `imprimirReciboLiquidacion(id)`. Fuentes fijadas a `SansSerif` (fuente
lógica, sin riesgo de instalación). Estado: Compila; plantilla **compila y hace
fill OK** (validado con la librería Jasper); **verificación visual del PDF
pendiente** (el export final usa iText, presente en el ambiente real).

**Pantallas desktop** — Lista de liquidaciones (por funcionario/período),
generar (individual/masivo), diálogo de detalle con items, acciones por estado,
pago vía Caja Mayor, y botón "Recibo". Estado: Compila (AOT).

---

## 7. Liquidación final / finiquito (Fase 6)

**Motor de finiquito** — `LiquidacionFinalCalculator` (puro): antigüedad
(días/meses/años), indemnización (solo DESPIDO_INJUSTIFICADO y antigüedad ≥
mínimo configurable: `salarioPromedio/30 × díasPorAño × max(1, años)`),
vacaciones no gozadas (`días × salarioPromedio/30`), aguinaldo proporcional, y
total. Estado: Compila + Tests.

**Servicio de finiquito** — `LiquidacionFinalService.generarBorrador`: reúne
salario promedio (últimas 6 liquidaciones aprobadas/pagadas), días de vacaciones
no gozadas, aguinaldo del año; arma items (INDEMNIZACION, VACACIONES_NO_GOZADAS,
AGUINALDO_PROPORCIONAL). Estados BORRADOR/APROBADA/PAGADA/ANULADA;
`pagar` (EGRESO Caja Mayor + `MovimientoPersonas` + `funcionario.activo=false`);
`anular` (contra-asiento). Migración `V147.0`. Estado: Compila.

**Pantalla desktop** — Diálogo de liquidación final lanzado desde el Legajo:
elige motivo de egreso + fecha, muestra el desglose (antigüedad, salario
promedio, indemnización, vacaciones, aguinaldo, total) y las acciones por
estado. Estado: Compila (AOT).

---

## 8. Elementos transversales

**Integración con Caja Mayor (fd-93)** — Todos los movimientos de dinero (vales,
préstamos, pago de liquidaciones y finiquitos) se registran como movimientos en
`CajaVirtual` tipo `CAJA_MAYOR` (`MovimientoCajaVirtualService.registrarMovimiento`
valida saldo) y en el ledger del empleado (`MovimientoPersonas`). El desktop
consulta las cajas activas por su propia query GraphQL. Estado: Compila.

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

**Diálogo de info en Configuración RRHH** — Botón de info por fila que abre un
diálogo con explicación curada de cada parámetro (título, descripción, ejemplo,
impacto) para las ~22 configs. Además, en cambio de salario: hint del mínimo
legal + confirmación si el salario queda por debajo. Estado: Compila (AOT).

## 8.2 Dashboard y reportes (Fase 8)

**Dashboard RRHH** — `dashboardRrhhKpis(periodo)` agrega sobre los repos
existentes: funcionarios activos, nómina del mes, liquidaciones pendientes,
vales/préstamos abiertos (cant+monto/saldo), penalizaciones y HE del mes,
cuotas vencidas y aguinaldo estimado. Pantalla desktop con tarjetas de KPIs.
Estado: Compila (backend + AOT desktop).

**Reportes RRHH** — Dos reportes Jasper: **nómina del mes** (liquidaciones
aprobadas/pagadas del período) y **resumen IPS** (base salarial + aporte
funcionario 9% y patronal 16.5% desde config). Fuente SansSerif; validados
(compilan y hacen fill OK con datos dummy). Pantalla desktop que genera y abre
el PDF. Estado: Compila; **verificación visual del PDF pendiente en dev**.

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
- **Fase 8 — Notificaciones** — El dashboard con KPIs y los reportes (nómina,
  IPS) **ya están hechos** (§8.2). Falta el **job de notificaciones RRHH**
  (cumpleaños, cuota vencida, vacación próxima, etc.) + FCM.

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
- **Recibo de finiquito (PDF)** — La Fase 6 no tiene aún un PDF de finiquito
  (análogo al recibo de sueldo); se puede reutilizar el patrón Jasper.
- **Historial propio de marcaciones (empleado)** — El servicio backend existe;
  falta la pantalla.
- **Planilla legal de IPS (REI)** — El plan (§20 #7) la deja como mejora
  posterior; solo se calcula el % de IPS.
- **`funcionario.sueldo` es `Float`** — Precisión monetaria pobre; las
  liquidaciones snapshottean en `numeric(18,2)`. Migrar a `sueldo_decimal` con
  estrategia de 2 versiones queda como mejora futura (§20 #3).
