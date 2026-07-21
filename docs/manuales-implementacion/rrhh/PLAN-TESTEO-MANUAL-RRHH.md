# Plan de testeo manual — Módulo RRHH

> Pruebas manuales end-to-end del módulo RRHH desde la app **desktop**, contra el
> backend central local (`localhost:8081`) y la DB `bodega_producto_devoluciones` (`:5551`).
> **Flujo de trabajo:** Claude da los pasos de una prueba → el operador la ejecuta en la UI →
> avisa → Claude verifica en DB → si está OK, se pasa a la siguiente.

## Entorno y convenciones

| Ítem | Valor |
|---|---|
| **Login** | `GABRIEL` (único usuario con rol ADMIN → ve todo el menú RRHH) |
| **Sujeto principal** (ciclo laboral) | Funcionario **#8 — ESTEBAN ARISTIDES FERNANDES ESTECHE** (ingreso 2022-03-05, sueldo 2.550.000, limpio) |
| **Sujeto finiquito** | Funcionario **#100 — WILIAN ISMAEL MARTINEZ** (ingreso 2019-11-28, ~6.5 años) |
| **Diarista (test opcional)** | Funcionario **#286 — ARTURO VERA** (necesita `valor_jornal` + jornadas) |
| **Caja Mayor** | **#1 — "CAJA MAYOR TEST RRHH"**, saldo 50.142.000 Gs |
| **Período de liquidación** | **2026-07** (fechar HE/vale/bono/penalización dentro de julio 2026) |

**Regla clave:** para que la liquidación de sueldo (T14) arrastre todos los ítems, **todos los
eventos de ESTEBAN (HE, vale, préstamo, bono, penalización) deben fecharse en el período 2026-07**.

**Datos ya precargados:** 10 feriados, 4 motivos de vale, 24 parámetros de configuración RRHH,
Caja Mayor fondeada. Cada prueba indica si necesita datos previos adicionales (Claude los carga
antes de habilitar la prueba).

Leyenda de estado: ⬜ pendiente · ✅ OK · ❌ falla · ⏭️ diferida

---

## FASE 1 — Catálogos y configuración

### ✅ T1 — Configuración RRHH  *(guardado OK; bugs B1 backend arreglado, B2 UI pendiente)*
- **Objetivo:** ver y editar los ~22 parámetros de RRHH.
- **Datos previos:** 24 params ya sembrados. Ninguno adicional.
- **Pasos UI:** Menú `R.R.H.H.` → `Configuración RRHH`. Revisar la lista. Editar un parámetro
  (ej. `TOLERANCIA_TARDANZA_MIN` de 5 a 10) y guardar.
- **Verificación (DB):** `rrhh.configuracion_rrhh` refleja el nuevo valor.

### ✅ T2 — Motivos de vale  *(alta/edición/desactivación OK; sin bug creado_en. Nota UX: sin toggle directo de activo, se desactiva editando)*
- **Objetivo:** CRUD de motivos de vale.
- **Datos previos:** 4 motivos ya existen.
- **Pasos UI:** `R.R.H.H.` → `Motivos de vale` → `Nuevo` → descripción "VALE FARMACIA" → guardar.
  Editar uno, desactivar otro.
- **Verificación (DB):** `rrhh.motivo_vale` con el nuevo registro / cambios.

### ✅ T3 — Feriados  *(alta/desactivación OK. Ver TODO-1: feriados avanzados por ciudad/turno/tipo/plan)*
- **Objetivo:** CRUD de feriados y su efecto en valorización de HE.
- **Datos previos:** 10 feriados existen.
- **Pasos UI:** `R.R.H.H.` → `Feriados` → `Nuevo` → fecha 2026-07-20, descripción "FEST PRUEBA",
  guardar.
- **Verificación (DB):** `rrhh.feriado` con la fecha 2026-07-20.

---

## FASE 2 — Legajo

### ✅ T4 — Legajo funcionario  *(cargo/salario/documento OK en DB; legajo rediseñado como dashboard; bugs B3-B6 arreglados)*
- **Objetivo:** ver el legajo (histórico de cargos/salarios, documentos, datos de egreso).
- **Datos previos:** ESTEBAN (#8) existe. Claude puede sembrar 1 cargo + 1 salario histórico si
  el legajo aparece vacío, para que haya algo que mostrar.
- **Pasos UI:** `R.R.H.H.` → `Legajo funcionario` → buscar/seleccionar ESTEBAN. Revisar pestañas
  (histórico cargos, salarios, documentos). Agregar un documento (ej. tipo "CONTRATO", con
  vencimiento) y guardar.
- **Verificación (DB):** `rrhh.funcionario_documento` / `funcionario_cargo_historico` /
  `funcionario_salario_historico` con lo cargado.

---

## FASE 3 — Asistencia (sujeto: ESTEBAN #8)

### ⬜ T5 — Justificativos (ex "Novedades") + catálogo de tipos
> **El módulo se rediseñó durante el testeo** (commits `c3a2385c` backend / `518db17c` desktop).
> "Novedades" no reflejaba su función: son las **justificaciones** de por qué un día no fue una
> jornada normal. Además los tipos eran un enum fijo y 3 de los 5 no producían ningún efecto.
>
> Ahora: tabla `rrhh.justificativo` + catálogo `rrhh.tipo_justificativo` con ABM propio, donde
> cada tipo define **evitaPenalizacion**, **descuentaSalario** (NO/MEDIO_DIA/DIA_COMPLETO),
> **requiereDocumento** y **generadoPorSistema**. Se pueden agregar tipos sin desplegar código.
>
> Efectos cableados: la penalización usa `evitaPenalizacion`; **la liquidación ahora descuenta**
> (antes ignoraba las novedades por completo). Verificado: 1,5 días → 175.000,01 sobre un sueldo
> de 3.500.000 con `DIAS_MES_PROMEDIO=30`.
>
> Vacaciones **no** se gestiona acá: sus justificativos son una proyección del módulo de
> vacaciones, quedan de solo lectura (tipo marcado como generado por el sistema).

- **Datos previos:** 8 tipos sembrados; 3 justificativos de ESTEBAN cargados en la verificación.
- **Pasos UI:**
  1. `R.R.H.H.` → `Tipos de justificativo`: revisar el catálogo, crear un tipo nuevo, editar el
     descuento de uno, verificar que los del sistema (VACACION/FERIADO) no se puedan eliminar.
  2. `R.R.H.H.` → `Justificativos` → buscar por ESTEBAN → `Nuevo` → tipo, fecha, observación.
- **Verificación (DB):** `rrhh.justificativo` (con `tipo_justificativo_id`) y `rrhh.tipo_justificativo`.

### ⬜ T6 — Historial de marcaciones
- **Objetivo:** consultar el historial de marcaciones de un funcionario.
- **Datos previos:** requiere jornadas/marcaciones. Claude sembrará 2–3 jornadas de ESTEBAN en
  julio 2026 (una con tardanza) antes de la prueba.
- **Pasos UI:** `R.R.H.H.` → `Historial de marcaciones` → seleccionar ESTEBAN, rango julio 2026.
  Revisar las jornadas (entrada/salida, minutos trabajados, tardanza).
- **Verificación (DB):** las jornadas mostradas coinciden con `administrativo.jornada`.

### ⬜ T7 — Horas extra
- **Objetivo:** cargar HE manual y ver la valorización (monto calculado).
- **Datos previos:** ESTEBAN existe.
- **Pasos UI:** `R.R.H.H.` → `Horas extra` → `Nuevo` → funcionario ESTEBAN, fecha 2026-07-12,
  minutos 120, tipo DIURNA, recargo 50% → guardar. Verificar el monto calculado.
- **Verificación (DB):** `rrhh.hora_extra` con la HE de ESTEBAN, `monto_calculado` coherente.

### ⬜ T8 — Penalizaciones
- **Objetivo:** cargar una penalización manual.
- **Datos previos:** ESTEBAN existe.
- **Pasos UI:** `R.R.H.H.` → `Penalizaciones` → `Nuevo` → funcionario ESTEBAN, fecha 2026-07-14,
  tipo (ej. QUEJA_CLIENTE), monto 80.000, descripción → guardar.
- **Verificación (DB):** `rrhh.penalizacion` con la penalización de ESTEBAN, `auto_generada=false`.

---

## FASE 4 — Anticipos vía Caja Mayor (sujeto: ESTEBAN #8)

### ⬜ T9 — Vales
- **Objetivo:** solicitar y confirmar un vale (egreso real de Caja Mayor + asiento en cuenta
  corriente del empleado); anular otro (contra-asiento).
- **Datos previos:** Caja Mayor fondeada ✅, motivos de vale ✅.
- **Pasos UI:** `R.R.H.H.` → `Vales` → `Nuevo` → funcionario ESTEBAN, motivo, monto 400.000,
  fecha 2026-07-15 → confirmar eligiendo la Caja Mayor. Luego crear un segundo vale y anularlo.
- **Verificación (DB):** vale CONFIRMADO; `financiero.movimiento_caja_virtual` EGRESO 400.000 con
  `saldo_anterior/posterior` correctos y descripción "VALE #<id> - ESTEBAN…";
  `financiero.movimiento_personas` ANTICIPO. El anulado: contra-asiento AJUSTE + MovimientoPersonas
  desactivado.

### ⬜ T10 — Préstamos
- **Objetivo:** crear préstamo (desembolso EGRESO + plan de cuotas) y cobrar una cuota (INGRESO).
- **Datos previos:** Caja Mayor fondeada ✅.
- **Pasos UI:** `R.R.H.H.` → `Préstamos` → `Nuevo` → funcionario ESTEBAN, monto 1.200.000,
  3 cuotas, inicio 2026-07-16 → confirmar con Caja Mayor. Ver el plan de cuotas. Cobrar la cuota #1.
- **Verificación (DB):** `rrhh.prestamo` ACTIVO; 3 cuotas de 400.000 con vencimientos mensuales;
  Caja EGRESO 1.200.000 (desembolso) + INGRESO 400.000 (cobro); cuota #1 PAGADA.

---

## FASE 5 — Beneficios (sujeto: ESTEBAN #8)

### ⬜ T11 — Vacaciones
- **Objetivo:** devengar, programar, aprobar y marcar GOZADA (genera novedades por día); vender días.
- **Datos previos:** ESTEBAN con antigüedad ✅.
- **Pasos UI:** `R.R.H.H.` → `Vacaciones` → devengar para ESTEBAN → programar un período
  (ej. 2026-08-03 a 2026-08-07) → aprobar → marcar GOZADA. Probar "vender días".
- **Verificación (DB):** `rrhh.vacacion` (diasGenerados/diasGozados), `vacacion_periodo` GOZADA,
  una `jornada_novedad` tipo VACACION por cada día del período, `vacacion_venta` si se vendió.

### ⬜ T12 — Aguinaldo
- **Objetivo:** calcular aguinaldos del año y aprobar.
- **Datos previos:** funcionarios con fecha_ingreso ✅.
- **Pasos UI:** `R.R.H.H.` → `Aguinaldos` → calcular año 2026 → localizar el de ESTEBAN → aprobar.
- **Verificación (DB):** `rrhh.aguinaldo` con el registro de ESTEBAN, monto proporcional coherente
  (sueldo/12 × meses trabajados), estado APROBADO.

### ⬜ T13 — Bonos
- **Objetivo:** crear un bono.
- **Datos previos:** ESTEBAN existe.
- **Pasos UI:** `R.R.H.H.` → `Bonos` → `Nuevo` → funcionario ESTEBAN, tipo DESEMPENIO,
  monto 200.000, fecha 2026-07-18 → guardar.
- **Verificación (DB):** `rrhh.bono` con el bono de ESTEBAN, sin liquidar.

---

## FASE 6 — Liquidación

### ⬜ T14 — Liquidación de sueldo (el test integrador)
- **Objetivo:** generar borrador que arrastre TODOS los ítems de ESTEBAN del período (salario,
  IPS, HE, penalización, bono, vale, cuota de préstamo, aguinaldo), aprobar, pagar (efectos
  cruzados + egreso de Caja) e imprimir el recibo PDF.
- **Datos previos:** los eventos de T7–T13 dentro de julio 2026 ✅.
- **Pasos UI:** `R.R.H.H.` → `Liquidaciones` → generar borrador de ESTEBAN período 2026-07.
  Revisar los ítems (haberes vs descuentos, neto). Aprobar → Pagar (elegir Caja Mayor) →
  Imprimir recibo.
- **Verificación (DB):** ítems correctos; al pagar: Caja EGRESO por el neto, vale → DESCONTADO,
  cuota → PAGADA/incrementada, bono/aguinaldo con `liquidacion_id`, MovimientoPersonas PAGO_SALARIO.

### ⬜ T15 — Liquidación final / finiquito (sujeto: WILIAN #100)
- **Objetivo:** generar finiquito (antigüedad, indemnización, vacaciones no gozadas, aguinaldo
  proporcional), aprobar, pagar (funcionario queda inactivo), imprimir recibo PDF.
- **Datos previos:** WILIAN activo con antigüedad ✅.
- **Pasos UI:** `R.R.H.H.` → `Liquidaciones` (o sección finiquito) → generar liquidación final de
  WILIAN, motivo DESPIDO_INJUSTIFICADO, fecha egreso hoy → revisar cálculo → aprobar → pagar →
  imprimir recibo.
- **Verificación (DB):** `rrhh.liquidacion_final` con antigüedad/indemnización/aguinaldo coherentes;
  al pagar: Caja EGRESO por el total, `personas.funcionario.activo=false` para WILIAN.

---

## FASE 7 — Consulta

### ⬜ T16 — Dashboard RRHH
- **Objetivo:** ver los KPIs del período.
- **Datos previos:** los generados en T7–T15 ✅.
- **Pasos UI:** `R.R.H.H.` → `Dashboard RRHH` → período 2026-07. Revisar funcionarios activos,
  nómina del mes, liquidaciones pendientes, vales/préstamos, cumpleaños, vacaciones por vencer.
- **Verificación (DB):** los KPIs cuadran con los datos reales.

### ⬜ T17 — Reportes RRHH
- **Objetivo:** generar los reportes PDF (nómina, IPS, vales, préstamos, aguinaldo).
- **Datos previos:** los generados en fases previas ✅.
- **Pasos UI:** `R.R.H.H.` → `Reportes RRHH` → generar cada uno (período 2026-07 / año 2026).
  Verificar que el PDF abre y muestra datos reales.
- **Verificación (DB):** los totales del PDF cuadran con la DB.

---

## Mejoras futuras detectadas durante el testeo (TODO — NO implementado)

### TODO-1 — Feriados avanzados (gestión real de feriados) — *detectado en T3*
Hoy `rrhh.feriado` es simple: `fecha, descripcion, es_nacional, recargo_porcentaje, activo`.
La operación real de RRHH necesita bastante más (relevado por Gabriel):

1. **Alcance por ciudad y por turno.** Un feriado puede afectar solo algunas ciudades
   (ej. aniversario de Salto del Guairá) y solo algunos turnos (mañana/tarde), impactando
   tanto la **remuneración** como la **apertura/cierre del local**. (Las ciudades ya están
   registradas en el sistema.)
2. **Tipo: facultativo vs obligatorio (cierre de local).** Y casos que no son feriado pero
   obligan cierre en **ventanas horarias** (ej. día de elecciones: cierre 00:00–17:00). Es decir,
   el feriado/evento necesita ventana horaria de cierre, no solo un flag.
3. **Plan de trabajo del feriado.** Para feriados importantes RRHH arma un plan: redirige
   funcionarios entre sucursales, modifica horarios de atención y planifica **remuneración extra**
   que **muchas veces NO es un %** sino un **valor fijo según el cargo** (generalmente mayor al
   establecido por ley). El `recargo_porcentaje` actual no cubre este caso (monto fijo por cargo).

Implicancia: modelar `feriado` con alcance (ciudad/turno/sucursal), tipo (facultativo/obligatorio),
ventana de cierre, y un "plan de feriado" (asignaciones de funcionarios + remuneración por cargo,
% o monto fijo). Feature de tamaño considerable, para planificar aparte.

### TODO-2 — RRHH viola el padrón de "toda tabla paginada + filtros en backend" — *detectado en T3*
**Padrón del SaaS (confirmado por Gabriel):** TODA tabla del sistema debe estar **paginada** y
tener **filtros en el backend** (simples o complejos) — no filtros/carga client-side. Es un
estándar del producto y RRHH debe respetarlo.

**Estado actual (incumple):** las 16 listas RRHH cargan **todo del lado del cliente**
(`MatTableDataSource` con la lista completa, `res || []`), con a lo sumo un buscador que filtra
en memoria. Los queries backend devuelven `[Xxx]` (lista completa), no `Page<Xxx>`, y sin
parámetros de filtro server-side. Ejemplos: `configuracionesRrhh`, `motivosVale`, `feriados`,
`valesPorFuncionario`, `prestamos...`, etc.

**Qué hay que hacer:** retrofit de paginación + filtros server-side en cada endpoint de lista
RRHH (patrón `Page<T>` + `Pageable` + specs/params de filtro, ver skill `frc-central/graphql-patterns`)
y migrar los componentes desktop al **`GenericListComponent`** estándar
(`src/app/shared/components/generic-list/`, que ya da filtros + `MatPaginator` + "CARGAR MÁS"),
en vez de las `<table mat-table>` propias actuales. El fix B2 (scroll) es solo un parche visual;
la solución correcta y alineada al padrón es la paginación server-side con el componente estándar.

> Nota: el mandato se reforzó en las skills `frc-central/mejoras-padronizacion.md` y
> `frc-desktop/ui-patterns.md` (antes figuraba como patrón opcional / "inconsistente").

### TODO-3 — Reorganización del menú lateral por dominio — *detectado en T4*
El menú tiene dos raíces "R.R.H.H." (una el módulo RRHH, otra que agrupaba Personas/Admin).
Se resolvió lo mínimo (2ª renombrada a "Administración", Funcionarios movido a RRHH). Gabriel
propone la organización completa **por dominio de negocio**:
```
RRHH          → Funcionarios, Cargos, Departamentos, Turnos, (Legajo/Asistencia/…)
Ventas        → Clientes, Presupuestos, Pedidos, Ventas
Compras       → Proveedores, Órdenes de compra, Recepciones
Administración→ Personas, Usuarios, Roles y permisos, Parámetros
```
(Ejemplo del principio, no literal.) Reordenar el `side-mini-variant` completo por estos dominios.
Toca navegación de toda la app → proyecto de UX aparte.

### TODO-4 — Legajo del funcionario como DASHBOARD — *detectado en T4*
Transformar el legajo (hoy: card de datos + botones + 3 tablas de históricos) en un **dashboard**
estilo el de devoluciones, pero con un **componente de navegación por Tabs** en vez de un gráfico:
- **Tabs de históricos/detalle:** Cargos, Salarios, Documentos, Puntuaciones, Asistencia, etc.
  (mover a sus propias tabs las tablas y algunos botones/acciones actuales).
- **Cabecera del dashboard con info valiosa del funcionario:**
  - **Foto de perfil** (si tuviera).
  - **Datos básicos:** cargo, salario, activo, antigüedad, turno, etc.
  - **Cards de métricas** (cada card, al hacer click, abre un diálogo con el detalle):
    - **Puntuación de funcionario (1–10)** → ver TODO-5.
    - **Puntuación de asistencia** → ver TODO-6.
    - **Metas y objetivos** (indicador tipo `4/11` = 4 de 11 logradas en el mes) → ver TODO-7.
- Nota: los datos de las métricas pueden arrancar como placeholder/inventados hasta que existan
  los módulos que las alimentan (TODO-5/6/7).

### TODO-5 — Módulo de valoración de funcionarios (evaluación 360°) — *futuro*
Puntuación 1–10 con **varios parámetros**. Cada funcionario es valorado por sus **compañeros** y
por su **superior**; los superiores además son evaluados por sus **inferiores**. Debe poder
**configurarse quién puede evaluar a quién**. Alimenta el card "Puntuación de funcionario" del
legajo-dashboard (TODO-4).

### TODO-6 — Métricas de valoración de asistencia — *futuro*
Hoy ya existe la **marcación de horario** (aunque no se usa aún en operación). Futuramente se usará
y hay que **crear métricas de valoración de asistencia** (puntualidad, ausencias, etc.) para
presentarlas en el card "Puntuación de asistencia" del legajo-dashboard (TODO-4).

### TODO-7 — Módulo de metas/objetivos + comisiones — *futuro, extenso (relacionado con Fase 7 diferida)*
A cada funcionario se le asignan **metas/objetivos**; el legajo muestra el progreso mensual
(ej. `4/11`). Ligado al **módulo de comisiones** (Fase 7, hoy diferida): comisiones de varios tipos
— por meta, por valor de venta, por valor de lucro (de una o varias sucursales o de determinados
productos), etc. Módulo extenso, a analizar bien. Alimenta el card "Metas y objetivos" (TODO-4).

### TODO-8 — Impacto de cambios de configuración sobre datos dependientes — *detectado en T4*
Al cambiar un parámetro de RRHH que influye en salarios/cálculos, el sistema debería avisar y
ofrecer actualizar lo que depende de él. **Distinguir dos tipos de config:**

1. **Config usada al calcular** (IPS %, RECARGO_HE_*, DIAS_VACACIONES_*, TOLERANCIA_TARDANZA,
   divisores): no se persiste en los registros; se lee al calcular. Cambiarla ya afecta todo
   cálculo futuro → **no requiere cascade**, pero sí **avisar de la retroactividad** (ej. una
   liquidación en BORRADOR que se regenere saldrá con los valores nuevos).
2. **Config que define una regla sobre datos materializados** (`SALARIO_MINIMO_LEGAL_PYG`): al
   subir el mínimo quedan funcionarios por debajo → requiere decisión.

**Propuesta (NO auto-cascade):** flujo guiado opt-in. Al guardar el parámetro, mostrar un diálogo
con los registros afectados (ej. "N funcionarios quedaron por debajo del nuevo mínimo" + lista) y
permitir **seleccionar cuáles actualizar**, generando sus `funcionario_salario_historico` con motivo
"AJUSTE POR CAMBIO DE SALARIO MÍNIMO". Los salarios son registros legales con histórico: nunca
modificarlos en silencio.

**Extra:** el cambio de configuración de RRHH debería ser **auditable** (quién, cuándo, valor
anterior → nuevo). Hoy `rrhh.configuracion_rrhh` tiene `usuario_id`/`creado_en` pero no historial
de cambios, y estos parámetros impactan la nómina.

### TODO-8 — Impacto de cambiar una configuración sobre los datos ya existentes — *detectado en T4*
Al cambiar un parámetro de Configuración RRHH que influye en datos ya guardados (ej.
`SALARIO_MINIMO_LEGAL_PYG`), hoy no pasa nada con los registros existentes. Propuesta de Gabriel:
avisar al usuario y ofrecer actualizar lo vinculado.

**Distinción clave para no hacerlo mal:**
1. **Parámetros usados en tiempo de cálculo** (IPS %, `RECARGO_HE_*`, `DIAS_VACACIONES_*`,
   `TOLERANCIA_TARDANZA_MIN`, divisores): se leen al calcular, así que el cambio ya aplica solo a
   los cálculos futuros. **NO** hay que cascadear nada — reescribir histórico sería incorrecto.
2. **Parámetros materializados en registros** (el mínimo legal vs. el `sueldo` guardado en cada
   funcionario): acá sí puede quedar data inconsistente (funcionarios por debajo del nuevo mínimo).

**Comportamiento propuesto (solo para el caso 2):** al guardar el parámetro, detectar los
registros afectados y abrir un diálogo con **vista previa** (lista de funcionarios por debajo del
nuevo mínimo, con su salario actual), permitiendo **seleccionar cuáles actualizar**. Al confirmar,
generar los cambios por la vía normal (creando `funcionario_salario_historico` con motivo
"AJUSTE POR SALARIO MÍNIMO"), nunca con un UPDATE masivo silencioso.

**Restricciones:** los salarios son registros legales/financieros → nunca actualizar
automáticamente sin confirmación explícita, siempre auditable (histórico + motivo + usuario), y
**jamás** tocar liquidaciones ya pagadas.

## Registro de bugs encontrados

| # | Test | Tipo | Descripción | Estado |
|---|---|---|---|---|
| B1 | T1 | Backend | Editar configuración RRHH reventaba (`creado_en` NULL, ModelMapper pisaba el valor con el `creadoEn` null del input). Idéntico en `LiquidacionConcepto`. | ✅ Arreglado (commit bc4eaa39) |
| B2 | T1 | Desktop UI | Las tablas de listas RRHH (`.tabla-container`, las 16) no tenían scroll vertical: se cortaban sin ver todas las filas. | ✅ Arreglado (desktop d5227337, scroll + header sticky, arregla las 16 listas) |
| B3 | T4 | Desktop UI | Doble botón "No" en los confirmar: las **16** llamadas de RRHH pasaban `btn2=null` y el "No" como `btn3`; `DialogosComponent` defaultea `btn2Name`→"No", renderizando dos. | ✅ Arreglado (5bedd88a, las 16 llamadas) |
| B4 | T4 | Desktop | "Ver documento" no hacía nada: `window.open()` está bloqueado en Electron. | ✅ Arreglado (5bedd88a, `DocumentoViewerDialogComponent` con iframe) |
| B5 | T4 | Desktop | Al actualizar el salario desde el cambio de cargo, se guardaba con `moneda_id` NULL. | ✅ Arreglado (5bedd88a, selector de moneda + preselección) |
| B6 | T4 | Desktop UI | Selector de moneda cortado y hint pisando el campo de fecha en "Cambiar salario". | ✅ Arreglado (976b9f3c) |
| B7 | T5 | Backend/DB | Al renombrar una tabla, Postgres conserva el nombre viejo de la secuencia; el `AssignedIdentityGenerator` la busca como `<tabla>_id_seq` → **todo INSERT fallaba** con `no existe la relación "rrhh.justificativo_id_seq"`. Aplica a cualquier rename futuro. | ✅ Arreglado (V164.1) |
| B8 | T5 | Diseño | Los tipos de justificativo eran un enum fijo y 3 de 5 no tenían ningún efecto: la liquidación **ignoraba por completo** las novedades, por lo que `MEDIA_FALTA` no descontaba nada. | ✅ Resuelto con el catálogo + cableado a liquidación (c3a2385c) |

## Tests opcionales / diferidos

- **Salario base de diarista (jornalero):** requiere setear `valor_jornal` en ARTURO (#286) y
  sembrarle jornadas en el período; la liquidación debería calcular `valor_jornal × días`.
- **Fase 7 (Comisiones):** DIFERIDA — no implementada, no testear.
