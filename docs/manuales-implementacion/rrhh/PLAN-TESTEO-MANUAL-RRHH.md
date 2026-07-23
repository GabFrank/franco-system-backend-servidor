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

### ✅ T5 — Justificativos (ex "Novedades") + catálogo de tipos
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
  1. `R.R.H.H.` → `Configuración` → `Tipos de justificativo`: revisar el catálogo, crear un tipo
     nuevo, editar el descuento de uno, verificar que los del sistema (VACACION/FERIADO) no se
     puedan eliminar ni tampoco los que están en uso.
  2. `R.R.H.H.` → `Asistencia` → `Justificativos` → buscar el funcionario → `Nueva` / editar.
- **Verificación (DB):** `rrhh.justificativo` (con `tipo_justificativo_id`) y `rrhh.tipo_justificativo`.
- **Resultado:** alta, edición, borrado y guard de tipos en uso verificados en DB. Bugs B7–B11
  detectados y arreglados durante la prueba.
- **Pendiente (va con el retrofit):** `requiereDocumento` es hoy una bandera sin efecto — la
  entidad `Justificativo` no tiene documento adjunto. Cablearlo a `rrhh.funcionario_documento`
  (FK nueva + validación en el save cuando el tipo lo exige).

### ✅ T6 — Historial de marcaciones
- **Objetivo:** consultar el historial de marcaciones de un funcionario.
- **Datos previos:** requiere jornadas/marcaciones. Claude sembrará 2–3 jornadas de ESTEBAN en
  julio 2026 (una con tardanza) antes de la prueba.
- **Pasos UI:** `R.R.H.H.` → `Historial de marcaciones` → seleccionar ESTEBAN, rango julio 2026.
  Revisar las jornadas (entrada/salida, minutos trabajados, tardanza).
- **Verificación (DB):** las jornadas mostradas coinciden con `administrativo.jornada`.
- **Resultado:** OK. Sembradas 4 jornadas de ESTEBAN (usuario 12) en julio 2026: normal,
  con 27 min de tardanza, incompleta (sin salida) y ausente. La UI las muestra correctamente
  y los totales del resumen cuadran (15:40 trabajadas = 482+458 min).
- **Nota de siembra:** `jornada.entrada`/`salida` son relaciones **compuestas**
  (`entrada_id` + `entrada_sucursal_id`), porque `Marcacion` tiene PK compuesta por la
  replicación. Cargar solo el `_id` deja el join sin resolver y las columnas salen vacías
  sin error. Aplica a cualquier siembra futura de jornadas.
- **Hallazgo abierto (H1, módulo `administrativo`, fuera del alcance de RRHH):** la tabla no
  tiene columna **Fecha** ni **Estado**. La fecha solo se ve incrustada en la columna Entrada,
  asi que una jornada **AUSENTE** (sin marcación) no muestra fecha en ninguna parte y se ve
  casi igual a una jornada normal sin atraso. Una **INCOMPLETA** solo se distingue porque le
  falta la salida.

### ✅ T7 — Horas extra
- **Objetivo:** cargar HE manual y ver la valorización (monto calculado).
- **Datos previos:** ESTEBAN existe.
- **Pasos UI:** `R.R.H.H.` → `Horas extra` → `Nuevo` → funcionario ESTEBAN, fecha 2026-07-12,
  minutos 120, tipo DIURNA, recargo 50% → guardar. Verificar el monto calculado.
- **Verificación (DB):** `rrhh.hora_extra` con la HE de ESTEBAN, `monto_calculado` coherente.
- **Resultado:** OK. 120 min DIURNA sobre sueldo 3.500.000 → **43.750,00**
  (3.500.000 ÷ 30 días ÷ 8 h = 14.583,33 × 1,5 de recargo × 2 h). El recargo (50) se tomó solo
  de `RECARGO_HE_DIURNA` sin cargarlo a mano, confirmando que la valorización lee la config.

### ✅ T8 — Penalizaciones
- **Objetivo:** cargar una penalización manual.
- **Datos previos:** ESTEBAN existe.
- **Pasos UI:** `R.R.H.H.` → `Penalizaciones` → `Nuevo` → funcionario ESTEBAN, fecha 2026-07-14,
  tipo (ej. QUEJA_CLIENTE), monto 80.000, descripción → guardar.
- **Verificación (DB):** `rrhh.penalizacion` con la penalización de ESTEBAN, `auto_generada=false`.
- **Resultado:** OK, se probó la cadena completa:
  1. Alta manual (QUEJA_CLIENTE 80.000, `auto_generada=false`).
  2. `PENALIZACION_MONTO_POR_MINUTO_TARDANZA` 0 → 5000 desde la UI; el cambio quedó en
     `configuracion_rrhh_historico` (primera validación de la auditoría de TODO-8 en uso real).
  3. Generación automática sobre 07/07: TARDANZA **135.000** = 27 min × 5.000, ligada a la
     jornada 102. La tolerancia decide *si* se penaliza, no descuenta minutos.
  4. Idempotencia: regenerar la misma fecha no duplica.
  5. **Guard de `evitaPenalizacion`:** con la penalización anulada y un justificativo
     `JUSTIFICADO` (evitaPenalizacion=true) ligado a la jornada 102, regenerar **no crea nada**.
     El comportamiento lo gobierna el catálogo, no un enum compilado.
- **Bugs corregidos durante la prueba:** B15 (campo "fecha a procesar" suelto entre los filtros,
  con el label cortado: parecía un filtro y se generaba sobre la fecha equivocada) y B16
  (Configuración RRHH mostraba la clave técnica en vez de un nombre legible).

---

## FASE 4 — Anticipos vía Caja Mayor (sujeto: ESTEBAN #8)

### ⬜ T9 — Vales
- **Objetivo:** solicitar y confirmar un vale (egreso real de Caja Mayor); anular otro
  (contra-asiento).
- **Datos previos:** Caja Mayor fondeada ✅, motivos de vale ✅.
- **Pasos UI:** `R.R.H.H.` → `Vales` → `Nuevo` → funcionario ESTEBAN, motivo, monto 400.000,
  fecha 2026-07-15 → confirmar eligiendo la Caja Mayor. Luego crear un segundo vale y anularlo.
- **Verificación (DB):** vale CONFIRMADO; `financiero.movimiento_caja_virtual` EGRESO 400.000 con
  `saldo_anterior/posterior` correctos y descripción "VALE #<id> - ESTEBAN…". El anulado:
  contra-asiento AJUSTE que devuelve el saldo.
- **Resultado:** OK. Cadena de caja sin huecos: 50.142.000 → 49.742.000 (vale #4) → 49.592.000
  (vale #5) → 49.742.000 (anulación). Cada `saldo_anterior` empalma con el `saldo_posterior`
  anterior.
- **Nota:** `financiero.movimiento_personas` ya **no** se verifica acá — se desvinculó de RRHH,
  ver TODO-10.

### ⬜ T10 — Préstamos
- **Objetivo:** crear préstamo (desembolso EGRESO + plan de cuotas) y cobrar una cuota (INGRESO).
- **Datos previos:** Caja Mayor fondeada ✅.
- **Pasos UI:** `R.R.H.H.` → `Préstamos` → `Nuevo` → funcionario ESTEBAN, monto 1.200.000,
  3 cuotas, inicio 2026-07-16 → confirmar con Caja Mayor. Ver el plan de cuotas. Cobrar la cuota #1.
- **Verificación (DB):** `rrhh.prestamo` ACTIVO; 3 cuotas de 400.000 con vencimientos mensuales;
  Caja EGRESO 1.200.000 (desembolso) + INGRESO 400.000 (cobro); cuota #1 PAGADA.

---

## FASE 5 — Beneficios (sujeto: ESTEBAN #8)

### ✅ T11 — Vacaciones
- **Objetivo:** devengar, programar, aprobar y marcar GOZADA (genera novedades por día); vender días.
- **Datos previos:** ESTEBAN con antigüedad ✅.
- **Pasos UI:** `R.R.H.H.` → `Vacaciones` → devengar para ESTEBAN → programar un período
  (ej. 2026-08-03 a 2026-08-07) → aprobar → marcar GOZADA. Probar "vender días".
- **Verificación (DB):** `rrhh.vacacion` (diasGenerados/diasGozados), `vacacion_periodo` GOZADA,
  un `rrhh.justificativo` de tipo VACACION por cada día del período (la tabla se llamaba
  `jornada_novedad` antes del rediseño de T5), `vacacion_venta` si se vendió.
- **Resultado:** OK. Devengado año 5 con 12 días (`DIAS_VACACIONES_HASTA_5A`; con 4,38 años
  de antigüedad no alcanza el tramo 5-10). Período 10-14/08 recorrió
  `SOLICITADA → PROGRAMADA → GOZADA`, dejó `autorizado_por = GABRIEL`, sumó 5 días gozados
  (quedan 7) y generó 5 justificativos VACACION, uno por día. Convive con la vacación
  prescrita del año 1.
- **Bugs corregidos durante la prueba:** B18 y B19 (ver registro).

### ✅ T12 — Aguinaldo
- **Objetivo:** calcular aguinaldos del año y aprobar.
- **Datos previos:** funcionarios con fecha_ingreso ✅.
- **Pasos UI:** `R.R.H.H.` → `Aguinaldos` → calcular año 2026 → localizar el de ESTEBAN → aprobar.
- **Verificación (DB):** `rrhh.aguinaldo` con el registro de ESTEBAN. Ojo: **no se puede aprobar
  antes de `MES_AGUINALDO` (12)** — aprobar congela el monto y dejaría fijado un devengado parcial.
- **Nota:** la lista muestra dos cifras distintas: `montoCalculado` = devengado a la fecha,
  `montoProyectado` = lo que se deberá al 31/12. En un año cerrado coinciden.
- **Pendiente (issue #161):** la base de cálculo usa el sueldo base actual; por ley debería ser
  1/12 de la remuneración total percibida (incluye HE, bonos, comisiones y aumentos del año).

### ✅ T13 — Bonos
- **Objetivo:** crear un bono.
- **Datos previos:** ESTEBAN existe.
- **Pasos UI:** `R.R.H.H.` → `Bonos` → `Nuevo` → funcionario ESTEBAN, tipo DESEMPENIO,
  monto 200.000, fecha 2026-07-18 → guardar.
- **Verificación (DB):** `rrhh.bono` con el bono de ESTEBAN, sin liquidar.

---

## FASE 6 — Liquidación

### ✅ T14 — Liquidación de sueldo (el test integrador)
- **Objetivo:** generar borrador que arrastre TODOS los ítems de ESTEBAN del período (salario,
  IPS, HE, penalización, bono, vale, cuota de préstamo, aguinaldo), aprobar, pagar (efectos
  cruzados + egreso de Caja) e imprimir el recibo PDF.
- **Datos previos:** los eventos de T7–T13 dentro de julio 2026 ✅.
- **Pasos UI:** `R.R.H.H.` → `Liquidaciones` → generar borrador de ESTEBAN período 2026-07.
  Revisar los ítems (haberes vs descuentos, neto). Aprobar → Pagar (elegir Caja Mayor) →
  Imprimir recibo.
- **Verificación (DB):** ítems correctos; al pagar: Caja EGRESO por el neto, vale → DESCONTADO,
  cuota → PAGADA/incrementada, bono/aguinaldo con `liquidacion_id`. (Ya no se escribe
  `MovimientoPersonas`, ver TODO-10.)
- **Resultado:** OK, test integrador completo. Liquidación #11 de ESTEBAN, período 2026-07:
  9 ítems (5 haberes: salario 3.500.000, HE 43.750, bono 200.000, 2 ventas de vacaciones
  233.333,34 + 350.000,01; 4 descuentos: IPS 315.000, penalización 80.000, justificativos
  175.000,01, adelanto 400.000). **Neto 3.357.083,34**, calculado a mano de antemano y verificado
  al centavo. Al pagar se dispararon los 5 efectos cruzados en una transacción: liquidación →
  PAGADA, Caja Mayor 48.942.000 → 45.584.916,66 (EGRESO encadenado), vale #4 → DESCONTADO,
  ventas de vacaciones #1/#6 → PAGADO, bono #2 ligado. Ninguna cuota de préstamo (venc. sep/oct,
  fuera del período) ni aguinaldo (solo en diciembre), como se esperaba.
- **Extras probados:** generación por lote (3 funcionarios de una, cada uno con su neto correcto;
  IGOR con vale + cuota vencida), alta/eliminación de ítem manual con recálculo de totales.

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

### ✅ TODO-2 — RRHH viola el padrón de "toda tabla paginada + filtros en backend" — *RESUELTO 2026-07-22*
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
>
> **RESUELTO (2026-07-22).** Las 13 entidades de lista de RRHH tienen ahora query paginada
> con filtros server-side (`valesPage`, `prestamosPage`, `motivosValePage`, `feriadosPage`,
> `penalizacionesPage`, `horasExtraPage`, `justificativosPage`, `tiposJustificativoPage`,
> `vacacionesPage`, `aguinaldosPage`, `bonosPage`, `liquidacionesPage`,
> `configuracionesRrhhPage`), todas devolviendo `Page<T>` con el shape estándar del producto,
> y sus listas desktop migradas a `GenericListComponent` + `MatPaginator`.
> Verificado contra la API: los 13 endpoints responden, los filtros acotan de verdad y las
> páginas no se solapan (`liquidacionesPage` tiene 392 registros — el caso que justificaba todo).

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

### ✅ TODO-4 — Legajo del funcionario como DASHBOARD — *RESUELTO (rediseñado durante T4)*
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

### ✅ TODO-10 — Desvincular RRHH de `movimiento_personas` (tabla candidata a eliminarse) — *detectado en T10*
Al cobrar una cuota de préstamo la caja se movía pero la "cuenta corriente" del empleado seguía
mostrando la deuda completa, y el enum tenía un valor `COBRO` que no se usaba en ningún lado.
Antes de implementarlo se auditó el uso real de la tabla. Resultado:

**`financiero.movimiento_personas` es write-only.** Se escribe desde 6 puntos (Vale, Préstamo,
LiquidacionSueldo, LiquidacionFinal, VentaCredito) y **nadie la lee**:
- El único cálculo agregado (`getSaldoPorPersona` → `getTotalCredito`) tiene un solo llamador y
  está **comentado** (`ClienteResolver.java:54`). El saldo del cliente se calcula desde
  `VentaCredito` en estado `ABIERTO`.
- Ningún reporte Jasper la usa. Desktop y mobile: cero referencias en todo `src/`.
- **La liquidación de sueldo no la lee**: descuenta vales y cuotas leyendo `rrhh.vale` y
  `rrhh.prestamo_cuota` directamente.
- 7 de los 11 valores del enum nunca se emiten (`COBRO`, `AGUINALDO`, `BONO`, `MULTA`,
  `VACACIONES`, `NO_DEVOLVIDOS`, `SALARIO`).

**Además, signos inconsistentes en la misma columna:** `VENTA_CREDITO` guarda negativo (30.650
filas), los tipos de RRHH guardan positivo. Dos personas reales (FRANCO AREVALOS #1, GILBERTO
FRANCO #8) ya tienen ambos mezclados: si se descomentara el cálculo de saldo, un adelanto de
sueldo les reduciría la deuda como clientes.

**Decisión (Gabriel, 2026-07-22):** nadie la usa; se desvincula RRHH de la tabla y se la considera
legacy. La deuda viva del préstamo sale de `prestamo_cuota` (`monto_total - monto_pagado`), que es
la misma fuente que gobierna el cobro y que lee la liquidación — no puede desincronizarse.
La eliminación completa de la tabla queda como issue de GitHub aparte (afecta también a
VentaCredito y a la replicación filial→central).

**Ojo al eliminarla del todo:** la tabla se replica `BRANCH_TO_MAIN`
(`V112__sync_replication_table_with_publications.sql:69`) y tiene `REPLICA IDENTITY FULL`, así que
sacarla toca la configuración de replicación de toda la red.

### TODO-9 — Catálogo configurable de tipos de penalización con monto estipulado — *detectado en T8*
Hoy `PenalizacionTipo` es un **enum fijo en código** (`TARDANZA`, `AUSENCIA`, `QUEJA_CLIENTE`,
`AMBIENTE_LABORAL`, `DANIO_MATERIAL`, `COMISION_DESCUENTO`, `OTRO`): no se pueden agregar tipos sin
desplegar, y **el monto se carga a mano en cada penalización**, sin valor de referencia por tipo.

**Pedido de Gabriel (2026-07-22):** cada tipo de penalización debe tener un **monto estipulado**
por defecto, y debe poder **crearse tipos nuevos** desde la UI.

Es exactamente el mismo problema que tenía "Novedades" antes de T5, y la solución ya está probada:
convertirlo en catálogo `rrhh.tipo_penalizacion` con ABM propio, con al menos:
- `nombre`, `descripcion`, `activo`
- `montoDefecto` (se precarga al elegir el tipo, editable por caso)
- `generadoPorSistema` (para proteger `TARDANZA`, que emite el job automático)

Ojo con la migración: `rrhh.penalizacion.tipo` es hoy un `varchar` con el nombre del enum. Mismo
camino que V164.0: crear el catálogo, sembrar los 7 tipos actuales, agregar `tipo_penalizacion_id`
y backfillear por nombre. Y **recordar el `DEFAULT nextval` en el id** (ver B13) y que un rename de
tabla conserva el nombre viejo de la secuencia (ver B7).

Relacionado: los montos de la penalización automática por tardanza hoy viven en config
(`PENALIZACION_MONTO_TARDANZA`, `PENALIZACION_MONTO_POR_MINUTO_TARDANZA`). Al hacer el catálogo hay
que decidir si esos parámetros se mueven al tipo TARDANZA o si conviven.

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

### TODO-11 — Foto de perfil del funcionario: tipo de documento dedicado — *detectado al mover el alta al legajo*
La foto se guarda como `FuncionarioDocumento` tipo `OTRO` con observación `FOTO_PERFIL` (no hay
un tipo dedicado en `FuncionarioDocumentoTipo`). Para que se reconozca al reabrir, el operador
tiene que tipear `FOTO_PERFIL` en la observación al subirla. Pendiente: agregar un valor
`FOTO_PERFIL` al enum + un botón directo "Cambiar foto" que lo setee solo, y conectar el avatar
de la cabecera del legajo a esa foto.

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
| B9 | T5 | Backend | `deleteTipoJustificativo` bloqueaba los tipos de sistema pero no los que están **en uso**: eliminar un tipo con justificativos reventaba con un FK violation crudo en vez de un mensaje. | ✅ Arreglado (guard con `countByTipoId`) |
| B10 | T5 | Desktop (global) | **Off-by-one de zona horaria al editar fechas.** `new Date('2026-07-21')` parsea como medianoche UTC; en Paraguay (UTC-3) eso es el día 20, y al guardar con `dateToString` se pierde un día **en cada edición**. Detectado editando un justificativo (21/07 → 20/07). Estaba también en `edit-feriado-dialog` y en el cálculo de antigüedad del legajo. | ✅ Arreglado con `stringToLocalDate()` en `dateUtils` |
| B11 | T5 | Desktop | La lista de justificativos no exponía editar, aunque el backend ya hacía update cuando el input traía `id`. | ✅ Agregada la acción de editar (81e95dee) |
| B12 | TODO-2 | Backend | `motivosValePage` filtraba por `descripcion`, pero la lista **muestra** `nombre`: buscar "QUINCENA" (que vive en `nombre`) no devolvía nada. Filtrar por un campo distinto del que se ve en pantalla es indistinguible de "no hay resultados". | ✅ Arreglado: busca en `nombre` Y `descripcion` |
| B13 | TODO-8 | Backend/DB | La tabla nueva `configuracion_rrhh_historico` se creó sin `DEFAULT nextval(...)` en `id`; todo INSERT fallaba con *"valor nulo na coluna id"*. El generador del proyecto espera la convención `<tabla>_id_seq` **con el DEFAULT puesto** (igual que `funcionario_documento`). Emparentado con B7. | ✅ Arreglado en la propia V166.0 |
| B15 | T8 | Desktop UX | El campo "fecha a procesar" de la generación automática vivía suelto entre los filtros con el label cortado ("Fecha a pro…"): parecía un filtro más y no el parámetro de la acción, así que se generaba sobre una fecha sin jornadas y el mensaje decía "generadas: 0" sin explicar nada. | ✅ Movido a un diálogo propio en la acción + mensaje explícito cuando no genera |
| B16 | T8 | Desktop UX | La lista de Configuración RRHH mostraba la clave técnica (`PENALIZACION_MONTO_POR_MINUTO_TARDANZA`), exponiendo un detalle interno a quien solo quiere editar un parámetro. | ✅ Se muestra legible ("Penalizacion monto por minuto tardanza"); la clave queda en el tooltip para soporte |
| B24 | T14 | Reporte | El recibo era mínimo; se rediseñó según un modelo real: encabezado con dirección/sueldo base/fecha en palabras/razón social, tabla con Operación (categoría) + ENTRADA/SALIDA + fecha del movimiento origen, tres totales (recibido/descontado/a cobrar), monto en letras y firma con nombre/documento. | ✅ `recibo-liquidacion.jrxml` rediseñado |
| B25 | T14 | Backend | El conversor de número a letras (copiado de filial) tenía dos errores: "tres millon" (singular incorrecto para >1M) y "trecientos" (ortografía). | ✅ Corregidos en la copia de central; el original en filial sigue con el bug |
| B22 | T14 | Desktop | El botón "Recibo" no hacía nada: `window.open()` está bloqueado en Electron (mismo caso que B4). El backend generaba el PDF bien, pero no se mostraba. | ✅ Se muestra en `DocumentoViewerDialogComponent` (iframe) |
| B23 | T14 | Backend/Reporte | El recibo era una tabla mínima sin datos para firmarse: faltaba el documento del funcionario y las líneas de firma. Detectado por Gabriel: "una liquidación debe generar un documento firmable detallado". | ✅ `recibo-liquidacion.jrxml` con documento + cláusula de conformidad + firmas empleador/empleado |
| B20 | T12 | Backend | El aguinaldo contaba **12 meses siempre** que el funcionario hubiera ingresado antes del año en curso: `mesesTrabajados` nunca miraba la fecha actual. Calculado en julio mostraba el aguinaldo completo (3.500.000) como si estuviera devengado, cuando lo ganado eran 7/12 (2.041.666,67). Detectado por Gabriel: "estamos en julio, ¿de qué sirve generar aguinaldo?". | ✅ Se separan devengado y proyectado (V168.0) |
| B21 | T12 | Backend | Aprobar congela el monto (el recálculo no toca los `APROBADO`), así que aprobar a mitad de año dejaba fijado un devengado parcial y la liquidación de diciembre pagaba **de menos**. | ✅ Se bloquea aprobar antes de `MES_AGUINALDO` |
| B18 | T11 | Backend | `aprobarPeriodo` **nunca registraba quién aprobaba**: hacía `funcionarioService.findById(autorizadoPorId)` y descartaba el resultado (`// no-op guard`), sin llamar a `setAutorizadoPor`. Encima `autorizadoPor` es un `Usuario` y buscaba un `Funcionario` con ese id. Una autorización de permiso laboral quedaba sin constancia. | ✅ Guarda el usuario real |
| B19 | T11 | Desktop | El botón "Programar" mandaba `'PROGRAMADA'` hardcodeado, salteando `SOLICITADA`: el botón de aprobar (visible solo en ese estado) quedaba **inalcanzable**, aunque mobile sí implementa la aprobación por supervisor (`aprobaciones-rrhh`, `VacacionesPendientesAprobacionMobile`). Detectado por Gabriel: "el botón se llama Programar y hace lo que se llama, en todo caso se debería llamar Solicitar". | ✅ Botón `Solicitar`, crea en SOLICITADA |
| B17 | T8 | Desktop | Las 13 listas migradas no mostraban tabla ni paginador: el host sin altura definida hacía colapsar a cero el `<div fxFlex>` de `app-generic-list`. | ✅ `:host { display: block; height: 100% }` en las 13 |
| B14 | TODO-2 | Desktop | El `.scss` de las listas migradas anidaba todo bajo `.xxx-container`, clase que desapareció al pasar la raíz del template a `<app-generic-list>`: los chips de estado quedaban sin color y las tablas sin alinear. Afectaba a las 13 listas. | ✅ Aplanado + `:host` en las 13 |

## Tests opcionales / diferidos

- **Salario base de diarista (jornalero):** requiere setear `valor_jornal` en ARTURO (#286) y
  sembrarle jornadas en el período; la liquidación debería calcular `valor_jornal × días`.
- **Fase 7 (Comisiones):** DIFERIDA — no implementada, no testear.
