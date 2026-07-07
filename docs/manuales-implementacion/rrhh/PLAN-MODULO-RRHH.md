# Plan/Manual: Módulo RRHH — de FRC Gourmet a FRC Comercial

> **Fecha de relevamiento:** 2026-07-07
> **Repos analizados:**
> - `GabFrank/frc-gourmet` (origen del módulo RRHH completo)
> - `GabFrank/franco-system-backend-servidor` (backend central de FRC Comercial)
> - `GabFrank/frc-sistemas-integrados-angular` (desktop de FRC Comercial)
> - `GabFrank/frc-mobile` (mobile de FRC Comercial)
>
> **Objetivo:** documentar TODO el conocimiento del módulo RRHH de FRC Gourmet, relevar lo que ya existe en FRC Comercial, y definir el plan de implementación completo (backend + desktop + mobile) para llevar las mismas funcionalidades (o mejores) a FRC Comercial.
>
> **Rev. 2 (2026-07-07):** incorpora el relevamiento de la branch `fd-93` (Caja Mayor / `CajaVirtual`, backend + desktop), el ecosistema Solicitud de pago (`TipoSolicitudPago.RRHH` ya previsto) y la convención interna de numeración de migraciones `V{max+1}.{dev}` (`.0` Gabriel, `.1` Mauro, `.2` Diego). Resuelve la decisión abierta #1 (vendedor en ventas: `venta.usuario_id` / `venta_item.usuario_id` + puente `funcionario.usuario_id`).
>
> **Rev. 3 (2026-07-07) — decisiones confirmadas por Gabriel:**
> 1. **Origen del dinero**: SolicitudPago tipo `RRHH` → `MovimientoCajaVirtual` EGRESO contra Caja Mayor (§18.3 Opción A). ✅
> 2. **Permisos**: 8–10 roles RRHH por nombre, sin granularidad de aspecto (§18.4). ✅
> 3. **Nómina**: liquidación global en el central con filtro/agrupación por sucursal (riesgo #5). ✅
> 4. **Arranque**: Fases 0–2 comienzan ya (no dependen de caja); Fases 3 y 5 esperan el merge de `fd-93`. ✅

---

## Índice

- [Parte I — El módulo RRHH de FRC Gourmet (conocimiento completo)](#parte-i--el-módulo-rrhh-de-frc-gourmet)
  - [1. Arquitectura y alcance](#1-arquitectura-y-alcance)
  - [2. Modelo de datos](#2-modelo-de-datos)
  - [3. Enums (catálogo completo)](#3-enums-catálogo-completo)
  - [4. Lógica de negocio y flujos](#4-lógica-de-negocio-y-flujos)
  - [5. Configuración RRHH (parámetros)](#5-configuración-rrhh-parámetros)
  - [6. Permisos](#6-permisos)
  - [7. UI (pantallas)](#7-ui-pantallas)
  - [8. Dashboard y reportes](#8-dashboard-y-reportes)
  - [9. Notificaciones RRHH](#9-notificaciones-rrhh)
  - [10. Integración financiera](#10-integración-financiera)
- [Parte II — Estado actual de FRC Comercial](#parte-ii--estado-actual-de-frc-comercial)
  - [11. Backend central](#11-backend-central-franco-system-backend-servidor)
  - [12. Desktop](#12-desktop-frc-sistemas-integrados-angular)
  - [13. Mobile](#13-mobile-frc-mobile)
  - [14. Matriz comparativa Gourmet vs Comercial](#14-matriz-comparativa-gourmet-vs-comercial)
- [Parte III — Plan de implementación](#parte-iii--plan-de-implementación)
  - [15. Principios de diseño y mapeo de conceptos](#15-principios-de-diseño-y-mapeo-de-conceptos)
  - [16. Modelo de datos propuesto (backend)](#16-modelo-de-datos-propuesto-backend)
  - [17. Fases de implementación](#17-fases-de-implementación)
  - [18. Consideraciones transversales](#18-consideraciones-transversales)
  - [19. Verificación end-to-end](#19-verificación-end-to-end)
  - [20. Riesgos y decisiones abiertas](#20-riesgos-y-decisiones-abiertas)

---

# PARTE I — El módulo RRHH de FRC Gourmet

## 1. Arquitectura y alcance

FRC Gourmet es Angular 15 + Electron + TypeORM (SQLite/Postgres). El dato fluye en 4 capas: **Entity → Handler IPC (`electron/handlers/*.handler.ts`) → Preload → RepositoryService (Angular)**. Todas las entidades extienden `BaseModel` (`id`, `created_at`, `updated_at`, `created_by`, `updated_by`).

El módulo RRHH fue implementado en **8 fases** (todas en producción) y cubre:

| Sub-dominio | Contenido |
|---|---|
| Núcleo funcionario | Funcionario, Cargo, históricos de cargo/salario, documentos (legajo digital) |
| Asistencia | Turnos, asignación funcionario↔turno, asistencia diaria, feriados, tardanzas |
| Penalizaciones | Manuales + auto-generadas por tardanza (parametrizable) |
| Horas extra | Diurna/nocturna/feriado con recargos configurables |
| Vales/adelantos | Ciclo SOLICITADO→CONFIRMADO→DESCONTADO/ANULADO con impacto en caja |
| Préstamos | Vía CPP tipo `PRESTAMO_FUNCIONARIO` con cuotas |
| Vacaciones | Devengamiento por año de servicio, períodos de goce, venta de días, prescripción |
| Aguinaldo/bonos | Cálculo 1/12 anual; bonos manuales/recurrentes |
| Liquidación de sueldo | Motor de haberes/descuentos, estados BORRADOR→APROBADA→PAGADA→ANULADA |
| Liquidación final | Finiquito al egreso: indemnización + vacaciones no gozadas + aguinaldo proporcional |
| Comisiones | Motor de reglas sobre ventas, requisitos, equipos con reparto porcentual |
| Notificaciones | 8 tipos auto-generados con dedupe, badge en sidenav |
| Configuración | Key/value tipado con ~18 parámetros seed |
| Dashboard + reportes | 10 KPIs, 8 reportes con export Excel/PDF, recibo de sueldo PDF |

Total: **~30 entidades, 15 handlers IPC, ~28 permisos, ~30 pantallas**.

Rutas raíz en el repo gourmet:
- Entidades: `src/app/database/entities/rrhh/`
- Handlers: `electron/handlers/` (rrhh-funcionarios, asistencias, vales, vacaciones, liquidacion-sueldo, liquidacion-final, comisiones, equipos-comision, horas-extra, feriados, funcionario-documentos, configuracion-rrhh, dashboard-rrhh, notificaciones-rrhh, reportes-rrhh)
- UI: `src/app/pages/rrhh/` y `src/app/pages/comisiones/`
- Plan original: `docs/plan-rrhh-comisiones.md`

## 2. Modelo de datos

Convenciones: montos `decimal(18,2)`; enums persistidos como **string literal** (facilita portar a Postgres); FKs "planas" (`liquidacion_id`, `movimiento_id`) como `int` sin relación ORM para evitar ciclos; strings en **UPPERCASE**.

### 2.1 Núcleo funcionario

**`funcionarios`**

| Columna | Tipo | Notas |
|---|---|---|
| persona_id | FK → Persona | requerido (datos personales: nombre, fechaNacimiento, sexo, estadoCivil, email) |
| codigo_interno | varchar UNIQUE nullable | código interno opcional |
| cargo_id | FK → Cargo | cargo actual |
| fecha_ingreso | date | |
| fecha_egreso | date nullable | |
| motivo_egreso | enum MotivoEgreso nullable | |
| salario_base | decimal(18,2) default 0 | |
| moneda_salario_id | FK → Moneda | |
| es_jornalero | boolean default false | |
| valor_jornal | decimal(18,2) nullable | si jornalero |
| usuario_id | FK → Usuario nullable | **vínculo clave**: permisos + comisiones (vendedor) |
| ips_activo / numero_ips | boolean / varchar | aporte IPS Paraguay |
| cuenta_bancaria_propia | varchar nullable | datos cuenta de cobro |
| observacion | varchar nullable | |
| activo | boolean default true | soft delete (lo apaga la liquidación final al pagarse) |

**`cargos`**: `nombre`, `descripcion?`, `salario_referencia?`, `activo`. ⚠️ Cargo es **laboral**, NO es Role de permisos.

**`historico_cargos`**: funcionario, cargo, `fecha_desde`, `fecha_hasta?`, `motivo?`. Se crea uno con motivo `INGRESO` al alta; cada cambio de cargo cierra el anterior (`fecha_hasta`) y abre uno nuevo.

**`historico_salarios`**: funcionario, `salario_anterior?`, `salario_nuevo`, moneda, `fecha_vigencia`, `motivo?`, `autorizado_por?`. Mismo patrón que histórico de cargos.

**`funcionario_documentos`**: funcionario, `tipo` (CEDULA/CONTRATO/CERTIFICADO/CV/ANTECEDENTES/CARNET_SALUD/TITULO_ACADEMICO/OTRO), `nombre_archivo`, `ruta_relativa`, `mime_type?`, `tamano_bytes?`, `fecha_subida`, `vencimiento?`, `observacion?`. **El binario vive en filesystem** (`userData/funcionario-documentos/{id}/`), nunca en la BD. El vencimiento alimenta la notificación `DOCUMENTO_VENCE`.

### 2.2 Turnos, asistencia, penalización

**`turnos`**: `nombre` ("MAÑANA"), `hora_entrada` ('HH:mm'), `hora_salida`, `tolerancia_tardanza_minutos` default 5, `descripcion?`, `activo`.

**`funcionario_turnos`** (asignación con vigencia → historial): funcionario, turno, `fecha_desde`, `fecha_hasta?`.

**`asistencias`** (`@Index(funcionario, fecha)`): funcionario, turno?, `fecha`, `hora_entrada?`, `hora_salida?`, `estado` (PRESENTE/AUSENTE/TARDANZA/MEDIA_FALTA/JUSTIFICADO/FERIADO/VACACION), `minutos_tardanza` default 0, `horas_trabajadas decimal(5,2)?`, `justificada` default false, `observacion?`, `registrado_por?`.

**`penalizaciones`**: funcionario, asistencia? (si auto-generada), `tipo` (TARDANZA/AUSENCIA/QUEJA_CLIENTE/AMBIENTE_LABORAL/DANIO_MATERIAL/COMISION_DESCUENTO/OTRO), `descripcion?`, `monto` default 0, `fecha`, `registrado_por?`, `anulada` default false, `auto_generada` default false.

**`feriados`**: `fecha` UNIQUE, `descripcion`, `es_nacional` default true, `recargo_porcentaje decimal(5,2)` default 100 (% recargo de HE en feriado), `activo`.

**`horas_extra`**: funcionario, `fecha`, `horas decimal(5,2)`, `tipo` (DIURNA/NOCTURNA/FERIADO), `recargo_porcentaje` default 50, `monto_calculado` default 0 (⚠️ no se autocalcula al crear), asistencia?, `autorizado_por?`, `observacion?`, `anulada` default false.

### 2.3 Vales

**`vales`**: funcionario, motivo? (FK a catálogo `motivos_vale`), `monto`, `fecha`, `descripcion?`, caja_mayor?, moneda, forma_pago?, `estado` (SOLICITADO/CONFIRMADO/DESCONTADO/ANULADO), `es_adelanto` default false (adelanto de sueldo vs vale corriente), `liquidacion_id?` (FK plana, set al descontarse), `movimiento_id?` (FK plana a movimiento de caja), `cuenta_bancaria_id?` + `monto_cuenta_bancaria?` + `cotizacion?` (soporte pago desde banco y multi-moneda), `autorizado_por?`, `comprobante_url?`.

**`motivos_vale`**: catálogo plano (`nombre`, `descripcion?`, `activo`). Ej: "ANTICIPO SUELDO", "EMERGENCIA MEDICA".

### 2.4 Vacaciones

**`vacaciones`** (`@Index(funcionario, anioServicio)`): funcionario, `anio_servicio` (1, 2, 3…), `dias_generados`, `dias_gozados` default 0, `fecha_corte` (cuándo cumplió el año), `prescrita` default false, `observacion?`.

**`vacacion_periodos`**: vacacion, `fecha_desde`, `fecha_hasta`, `dias_usados`, `estado` (PROGRAMADA/EN_CURSO/GOZADA/CANCELADA), `autorizado_por?`, `asistencias_generadas` default false (flag de idempotencia), `observacion?`.

**`vacacion_ventas`** (venta de días no gozados — el funcionario "vende" días a la empresa): vacacion, `dias`, `monto` default 0, `fecha`, `estado` (PENDIENTE/PAGADO/ANULADO), `liquidacion_id?`, `observacion?`.

### 2.5 Aguinaldo y bonos

**`aguinaldos`** (`@Index(funcionario, anio)`): funcionario, `anio`, `monto_calculado` default 0, `meses_trabajados` default 0, `fecha_pago?`, `liquidacion_id?`, `estado` (CALCULADO/APROBADO/PAGADO).

**`bonos`**: funcionario, `tipo` (CUMPLEANIOS/NAVIDAD/DESEMPENIO/PRODUCTIVIDAD/OTRO), `monto`, `fecha`, `motivo?`, `autorizado_por?`, `liquidacion_id?`, `es_recurrente` default false, `frecuencia?` (SEMANAL/MENSUAL/TRIMESTRAL…), `anulado` default false.

### 2.6 Liquidación de sueldo

**`liquidaciones_sueldo`** (`@Index(funcionario, periodo)`): funcionario, `periodo` 'YYYY-MM', `fecha_inicio`, `fecha_fin`, `salario_base`, `total_haberes`, `total_descuentos`, `total_neto`, moneda_pago, `estado` (BORRADOR/APROBADA/PAGADA/ANULADA), `aprobado_por?`, `fecha_aprobacion?`, `fecha_pago?`, `movimiento_id?`, `cuenta_bancaria_id?` (pago desde banco), `observacion?`, `comprobante_url?`. `@OneToMany items`.

**`liquidacion_items`** (CASCADE): liquidacion, concepto? (FK a `liquidacion_conceptos`), `descripcion`, `monto`, `tipo` (HABER/DESCUENTO), `referencia_id?` + `referencia_tipo?` (VALE/CPP_CUOTA/AGUINALDO/LIQUIDACION_COMISION/…), `manual` default false (los manuales sobreviven a la regeneración), `observacion?`.

**`liquidacion_conceptos`**: `codigo` UNIQUE UPPERCASE, `descripcion`, `es_haber`, `es_calculado_auto`, `activo`. Seed: HABER → `SALARIO_BASE`, `HORA_EXTRA`, `BONO_MANUAL`, `AGUINALDO`, `COMISION`; DESCUENTO → `IPS_DESCUENTO`, `ADELANTO_DESCUENTO`, `VALE_DESCUENTO`, `PENALIZACION`, `PRESTAMO_CUOTA`.

### 2.7 Liquidación final

**`liquidaciones_final`**: funcionario, `fecha_egreso`, `motivo_egreso`, `antiguedad_dias/meses/anios`, `salario_promedio_ultimos_6_meses`, `indemnizacion_monto`, `indemnizacion_aplica`, `vacaciones_no_gozadas`, `monto_vacaciones_no_gozadas`, `aguinaldo_proporcional`, `total_liquidado`, moneda, `estado` (BORRADOR/APROBADA/PAGADA/ANULADA), `aprobado_por?`, fechas, `movimiento_id?`, `observacion?`. `@OneToMany items`.

**`liquidacion_final_items`** (CASCADE): `concepto` ('INDEMNIZACION', 'VACACIONES_NO_GOZADAS', 'AGUINALDO_PROPORCIONAL'), `monto`, `descripcion?`.

### 2.8 Comisiones

**`reglas_comision`**: `nombre`, `descripcion?`, `tipo` (META_UNIDADES/PORCENTAJE_VENTA/META_VENTA_LOCAL/EXTRA_MANUAL/PENALIZACION_MANUAL/EQUIPO_PORCENTAJE), `monto_base` default 0, `porcentaje?`, `meta_unidades?`, `meta_monto_local?`, `modo_validacion` (TODO_O_NADA/PROPORCIONAL), `recurrencia` (UNICA/DEFINIDA/INDEFINIDA), `fecha_inicio?`, `fecha_fin?`, `es_equipo` default false, `activo`.

**`regla_comision_productos`** (CASCADE): regla, producto. **Vacío = aplica a todos los productos.**

**`regla_comision_requisitos`** (CASCADE): regla, `tipo` (TARDANZA_MAX/QUEJA_MAX/ASISTENCIA_MIN/CUSTOM), `umbral`, `peso` default 1, `descripcion?`.

**`funcionario_regla_comision`**: funcionario, regla, `fecha_desde`, `fecha_hasta?`, `activo`.

**`equipos_comision`**: `nombre`, `descripcion?`, `activo`.
**`equipo_comision_miembros`** (CASCADE): equipo, funcionario, `porcentaje_reparto` (la suma debería ser 100).
**`equipo_comision_reglas`** (CASCADE): equipo, regla, `fecha_desde`, `fecha_hasta?`, `activo`.

**`liquidaciones_comision`**: funcionario, `periodo` 'YYYY-MM', `fecha_inicio/fin`, `total_calculado`, `estado` (BORRADOR/APROBADA/INTEGRADA/ANULADA), `aprobado_por?`, `fecha_aprobacion?`, `observacion?`.
**`liquidacion_comision_items`** (CASCADE): liquidacion, regla?, `concepto` (texto descriptivo), `monto`, `es_manual`, `observacion?` (**snapshot de parámetros aplicados** — auditoría: "META_UNIDADES: 50u de PARRILLA, resultado 60u").

### 2.9 Configuración y notificaciones

**`configuraciones_rrhh`** (key/value tipado): `clave` UNIQUE UPPERCASE, `valor?`, `tipo` (NUMBER/STRING/BOOLEAN/DATE), `descripcion?`, `activo`.

**`notificaciones_rrhh`**: `tipo` (8 valores, ver §9), `prioridad` (ALTA/MEDIA/BAJA), `titulo`, `mensaje`, funcionario?, usuario_destino?, `fecha_generada`, `fecha_leida?`, `accion_url?`, `clave_dedupe` UNIQUE nullable (idempotencia: ej `"CUMPLEANIOS-2026-05-05-3"`).

### 2.10 Préstamos (fuera de `rrhh/`, en financiero)

**`cuentas_por_pagar`** con `tipo = PRESTAMO_FUNCIONARIO` y `funcionario_id` FK: `descripcion`, `monto_total`, `monto_pagado`, moneda, `fecha_inicio`, `cantidad_cuotas`, `estado` (ACTIVO/PAGADO/CANCELADO). Genera N **`cuentas_por_pagar_cuotas`**: `numero`, `fecha_vencimiento`, `monto`, `monto_pagado`, `estado` (PENDIENTE/PARCIAL/PAGADA/VENCIDA/CANCELADA), `fecha_pago?`.

⚠️ **Dirección invertida**: el negocio le prestó al empleado, por lo tanto es un ingreso esperado. Movimientos: `EGRESO_DESEMBOLSO_PRESTAMO_FUNCIONARIO` al crear, `INGRESO_COBRO_CUOTA_PRESTAMO_FUNCIONARIO` al cobrar cuota directa, o descuento implícito dentro de la liquidación de sueldo (sin movimiento aparte).

## 3. Enums (catálogo completo)

| Enum | Valores |
|---|---|
| AsistenciaEstado | PRESENTE, AUSENTE, TARDANZA, MEDIA_FALTA, JUSTIFICADO, FERIADO, VACACION |
| ValeEstado | SOLICITADO, CONFIRMADO, DESCONTADO, ANULADO |
| PenalizacionTipo | TARDANZA, AUSENCIA, QUEJA_CLIENTE, AMBIENTE_LABORAL, DANIO_MATERIAL, COMISION_DESCUENTO, OTRO |
| HoraExtraTipo | DIURNA, NOCTURNA, FERIADO |
| MotivoEgreso | RENUNCIA, DESPIDO_JUSTIFICADO, DESPIDO_INJUSTIFICADO, MUTUO_ACUERDO, JUBILACION, FALLECIMIENTO, OTRO |
| LiquidacionSueldoEstado / LiquidacionFinalEstado | BORRADOR, APROBADA, PAGADA, ANULADA |
| LiquidacionItemTipo | HABER, DESCUENTO |
| LiquidacionComisionEstado | BORRADOR, APROBADA, INTEGRADA, ANULADA |
| TipoReglaComision | META_UNIDADES, PORCENTAJE_VENTA, META_VENTA_LOCAL, EXTRA_MANUAL, PENALIZACION_MANUAL, EQUIPO_PORCENTAJE |
| ModoValidacionComision | TODO_O_NADA, PROPORCIONAL |
| RecurrenciaComision | UNICA, DEFINIDA, INDEFINIDA |
| TipoRequisitoComision | TARDANZA_MAX, QUEJA_MAX, ASISTENCIA_MIN, CUSTOM |
| BonoTipo | CUMPLEANIOS, NAVIDAD, DESEMPENIO, PRODUCTIVIDAD, OTRO |
| AguinaldoEstado | CALCULADO, APROBADO, PAGADO |
| VacacionPeriodoEstado | PROGRAMADA, EN_CURSO, GOZADA, CANCELADA |
| VacacionVentaEstado | PENDIENTE, PAGADO, ANULADO |
| TipoNotificacionRrhh | PRESTAMO_VENCIDO, CUOTA_VENCIDA, CUMPLEANIOS, VACACION_PROXIMA, CONTRATO_VENCE, LIQUIDACION_PENDIENTE, COMISION_PENDIENTE, DOCUMENTO_VENCE |
| PrioridadNotificacion | ALTA, MEDIA, BAJA |
| FuncionarioDocumentoTipo | CEDULA, CONTRATO, CERTIFICADO, CV, ANTECEDENTES, CARNET_SALUD, TITULO_ACADEMICO, OTRO |
| ConfiguracionRrhhTipo | NUMBER, STRING, BOOLEAN, DATE |
| CuentaPorPagarTipo (financiero) | COMPRA, PRESTAMO, **PRESTAMO_FUNCIONARIO**, OTRO |
| CuotaEstado (financiero) | PENDIENTE, PARCIAL, PAGADA, VENCIDA, CANCELADA |
| TipoMovimiento caja (los que usa RRHH) | EGRESO_VALE, EGRESO_SALARIO, EGRESO_CUOTA_PRESTAMO, EGRESO_DESEMBOLSO_PRESTAMO_FUNCIONARIO, INGRESO_COBRO_CUOTA_PRESTAMO_FUNCIONARIO, AJUSTE_POSITIVO |

## 4. Lógica de negocio y flujos

### 4.1 Alta de funcionario (transacción)

1. Validar que Persona, Cargo y Moneda existen.
2. Crear `Funcionario`.
3. Crear `HistoricoCargo` (fecha_desde = fecha_ingreso, motivo = "INGRESO").
4. Crear `HistoricoSalario` (salario_nuevo = salario_base, fecha_vigencia = fecha_ingreso, motivo = "INGRESO").
5. Commit. Los cambios de cargo/salario posteriores cierran el histórico anterior (`fecha_hasta`) y crean uno nuevo.

El **egreso** (`egresar-funcionario`) setea `fecha_egreso` + `motivo_egreso` pero NO apaga `activo` — eso lo hace el pago de la liquidación final.

### 4.2 Registro de asistencia y penalización automática

```
1. Validar funcionario + turno asignado vigente
2. Si hay turno y hora_entrada:
     diff = diffMinutos(hora_entrada, turno.hora_entrada)
     si diff > turno.tolerancia_tardanza_minutos:
        estado = TARDANZA; minutos_tardanza = diff
3. Crear Asistencia
4. Si TARDANZA y NO justificada y config PENALIZACION_AUTO_TARDANZA = true:
     monto = PENALIZACION_MONTO_TARDANZA
           + PENALIZACION_MONTO_POR_MINUTO_TARDANZA × minutos_tardanza
     Crear Penalizacion(tipo=TARDANZA, auto_generada=true, asistencia_id, monto)
```

**Justificar asistencia**: estado → JUSTIFICADO + anula las penalizaciones `auto_generada` asociadas a esa asistencia. Las penalizaciones auto se muestran con chip "AUTO", no se editan (trazabilidad), solo se anulan.

Existe también **marcación masiva** (`marcar-asistencia-masiva`) para registrar la asistencia de todos los funcionarios de un día en un paso.

### 4.3 Vales (ciclo de vida)

```
SOLICITADO ──confirmar──▶ CONFIRMADO ──descuento en liq. sueldo──▶ DESCONTADO
     │                        │
     └───────anular───────────┴──▶ ANULADO (contra-movimiento si estaba CONFIRMADO)
```

- **Confirmar (transacción)**: crea movimiento de caja `EGRESO_VALE` + resta saldo + vale → CONFIRMADO + `movimiento_id`.
- **Crear vale ya confirmado** (atajo desde Caja Mayor): una sola transacción crea el Vale CONFIRMADO + movimiento + resta saldo. Requiere doble permiso (`RRHH_VALE_CREAR` + `RRHH_VALE_CONFIRMAR`).
- **Anular**: si estaba CONFIRMADO crea contra-movimiento `AJUSTE_POSITIVO` (nunca se borra el original) y revierte el saldo.
- `es_adelanto=true` distingue adelantos de sueldo (concepto `ADELANTO_DESCUENTO` en liquidación) de vales corrientes (`VALE_DESCUENTO`).

### 4.4 Préstamos a funcionarios

1. Crear CPP tipo `PRESTAMO_FUNCIONARIO` con N cuotas mensuales → desembolso `EGRESO_DESEMBOLSO_PRESTAMO_FUNCIONARIO` (resta caja).
2. Cobro por dos vías:
   - **Directa** desde caja: `INGRESO_COBRO_CUOTA_PRESTAMO_FUNCIONARIO` (suma caja).
   - **Por liquidación de sueldo**: la cuota vencida del período entra como item DESCUENTO `PRESTAMO_CUOTA` (`referencia_tipo=CPP_CUOTA`); al pagar la liquidación la cuota se marca PAGADA **sin** movimiento aparte (ya va incluida en el `EGRESO_SALARIO` neto).
3. Terminología de UI invertida: "Cobrar cuota", "Saldo a cobrar" (es plata a favor del negocio).

### 4.5 Vacaciones

1. **Devengamiento**: por año de servicio se genera `Vacacion` con días según antigüedad — <5 años: 12; 5–10: 18; >10: 30 (configurables, ley paraguaya).
2. **Programar período** (PROGRAMADA): valida `dias_gozados + dias_nuevos ≤ dias_generados`.
3. **Marcar GOZADA**: si `asistencias_generadas=false`, genera una `Asistencia` estado VACACION por cada día del rango (idempotente por el flag) y suma `dias_gozados`.
4. **Venta de días**: `vender-dias-vacacion` crea `VacacionVenta` PENDIENTE valorizada a `salarioDiario × dias`; se cobra como HABER en la liquidación de sueldo.
5. **Prescripción**: tras `PRESCRIPCION_VACACIONES_MESES` (24) desde `fecha_corte` → `prescrita=true`, ya no se pueden gozar ni cobrar en liquidación final.

### 4.6 Aguinaldo

`calcular-aguinaldos-anio`: **aguinaldo = Σ(haberes del año) / 12** por funcionario, con `meses_trabajados`. Estados CALCULADO → APROBADO → PAGADO. Se paga como HABER `AGUINALDO` dentro de la liquidación del mes `MES_AGUINALDO` (default diciembre) o aparte.

### 4.7 Liquidación de sueldo (el motor central)

**Generar borrador** (`generar-liquidacion-borrador(funcionario, periodo, moneda)`):

```
1. Si existe BORRADOR (funcionario, periodo): borrar items NO manuales
   (preserva los manuales del usuario). Si APROBADA/PAGADA: error.
2. Items automáticos:
   - SALARIO_BASE (HABER)       = funcionario.salario_base
   - IPS_DESCUENTO (DESC)       = salario_base × IPS_PORCENTAJE_FUNCIONARIO/100
   - HORA_EXTRA (HABER)         = Σ HoraExtra no anuladas del período
   - PENALIZACION (DESC)        = Σ Penalizacion no anuladas del período
   - BONO_MANUAL (HABER)        = Σ Bono no anulados del período
   - VALE_DESCUENTO (DESC)      = Σ Vale CONFIRMADO pendientes (referencia VALE)
   - ADELANTO_DESCUENTO (DESC)  = vales con es_adelanto = true
   - PRESTAMO_CUOTA (DESC)      = cuotas CPP PRESTAMO_FUNCIONARIO vencidas en período
   - COMISION (HABER)           = LiquidacionComision APROBADA del período
   - AGUINALDO (HABER)          = si mes == MES_AGUINALDO y Aguinaldo APROBADO
3. totalHaberes = Σ HABER; totalDescuentos = Σ DESCUENTO; totalNeto = diferencia
```

**Estados**: BORRADOR (editable, regenerable) → APROBADA (read-only) → PAGADA. `volver-borrador` permite retroceder de APROBADA.

**Pagar (transacción atómica)**:
1. Validar APROBADA.
2. Crear movimiento `EGRESO_SALARIO` por `total_neto` (o débito de cuenta bancaria si `cuenta_bancaria_id`).
3. Efectos cruzados por `referencia_tipo` de cada item: VALE → DESCONTADO + `liquidacion_id`; CPP_CUOTA → PAGADA (sin movimiento aparte); LIQUIDACION_COMISION → INTEGRADA; AGUINALDO → PAGADO.
4. Estado → PAGADA + `fecha_pago` + `movimiento_id`.

**Anular liquidación pagada (reversión total)**:
- VALE: DESCONTADO → CONFIRMADO, `liquidacion_id = null`.
- CPP_CUOTA: resta `monto_pagado` de cuota y CPP, vuelve PENDIENTE/PARCIAL; CPP PAGADO → ACTIVO.
- AGUINALDO: PAGADO → APROBADO. LIQUIDACION_COMISION: INTEGRADA → APROBADA.
- Contra-movimiento `AJUSTE_POSITIVO` en caja (vinculado por `referencia_anulacion_id`). Estado → ANULADA.

### 4.8 Liquidación final (finiquito)

```
1. antiguedad = diff(fecha_ingreso, fecha_egreso) → días, meses (÷30), años (÷365)
2. salarioPromedio = avg(total_haberes) de últimas 6 liquidaciones APROBADA/PAGADA
3. Indemnización — solo DESPIDO_INJUSTIFICADO y antigüedad ≥ 90 días:
     (salarioPromedio/30) × INDEMNIZACION_DIAS_POR_ANIO(15) × max(1, años)
4. Vacaciones no gozadas = Σ(dias_generados − dias_gozados) de Vacacion NO prescritas
     monto = díasNoGozados × (salarioPromedio/30)
5. Aguinaldo proporcional = Σ(haberes del año en curso) / 12
6. totalLiquidado = 3 + 4 + 5
```

Pagar → `EGRESO_SALARIO` (obs "LIQUIDACION FINAL") + **`funcionario.activo = false`**.

### 4.9 Motor de comisiones

`evaluarReglaParaFuncionario(regla, funcionario, periodo)`:

```
1. Query VentaItem ⋈ Venta:
   venta.estado = CONCLUIDA, item.estado != CANCELADO,
   producto ∈ regla.productos (vacío = todos),
   COALESCE(item.vendedor_id, venta.vendedor_id) = funcionario.usuario_id,
   fecha ∈ [inicio, fin]
2. Métricas: totalUnidades, totalMontoProductos, totalMontoVentaLocal
3. Requisitos (cada ReglaComisionRequisito):
   TARDANZA_MAX:  Σ minutos_tardanza del período ≤ umbral
   ASISTENCIA_MIN: COUNT asistencias PRESENTE|TARDANZA ≥ umbral
   QUEJA_MAX:     COUNT penalizaciones QUEJA_CLIENTE ≤ umbral
   Si no cumple: TODO_O_NADA ⇒ monto 0; PROPORCIONAL ⇒ descuento ponderado por peso
4. Monto según tipo:
   META_UNIDADES:       unidades ≥ meta ? montoBase : 0
   PORCENTAJE_VENTA:    montoProductos × porcentaje/100
   META_VENTA_LOCAL:    montoLocal ≥ meta ? montoBase : 0
   EXTRA_MANUAL:        +montoBase   |   PENALIZACION_MANUAL: −montoBase
5. monto −= Σ Penalizacion tipo COMISION_DESCUENTO del período
6. EQUIPO_PORCENTAJE: reparte monto × porcentaje_reparto/100 por miembro
7. Snapshot de parámetros en item.observacion (auditoría)
```

`LiquidacionComision` por funcionario+período: BORRADOR (regenerable) → APROBADA → INTEGRADA (cuando la liquidación de sueldo que la incluye se paga) → ANULADA. Existe generación masiva mensual (`generar-liquidaciones-comision-mes`).

## 5. Configuración RRHH (parámetros)

Seed key/value (`configuraciones_rrhh`):

| Clave | Default | Tipo | Uso |
|---|---|---|---|
| IPS_PORCENTAJE_FUNCIONARIO | 9 | NUMBER | Aporte IPS del funcionario (descuento) |
| IPS_PORCENTAJE_PATRONAL | 16.5 | NUMBER | Aporte patronal (reporte IPS) |
| SALARIO_MINIMO_LEGAL_PYG | (varía) | NUMBER | Salario mínimo PY |
| DIAS_VACACIONES_HASTA_5A | 12 | NUMBER | Ley PY |
| DIAS_VACACIONES_5_10A | 18 | NUMBER | |
| DIAS_VACACIONES_MAS_10A | 30 | NUMBER | |
| INDEMNIZACION_DIAS_POR_ANIO | 15 | NUMBER | Despido injustificado |
| INDEMNIZACION_ANTIGUEDAD_MIN_DIAS | 90 | NUMBER | Antigüedad mínima |
| RECARGO_HE_DIURNA | 50 | NUMBER | % recargo hora extra |
| RECARGO_HE_NOCTURNA | 100 | NUMBER | |
| RECARGO_HE_FERIADO | 100 | NUMBER | |
| TOLERANCIA_TARDANZA_MIN | 5 | NUMBER | Default para turnos |
| PRESCRIPCION_VACACIONES_MESES | 24 | NUMBER | |
| DIA_CIERRE_MES | 30 | NUMBER | Cierre mensual de liquidación |
| MES_AGUINALDO | 12 | NUMBER | Diciembre |
| PENALIZACION_AUTO_TARDANZA | true | BOOLEAN | Generar penalización automática |
| PENALIZACION_MONTO_TARDANZA | 0 | NUMBER | Monto fijo |
| PENALIZACION_MONTO_POR_MINUTO_TARDANZA | 0 | NUMBER | × minutos |

## 6. Permisos

**RRHH**: `RRHH_FUNCIONARIO_VER`, `RRHH_FUNCIONARIO_EDITAR`, `RRHH_FUNCIONARIO_EGRESAR`, `RRHH_ASISTENCIA_REGISTRAR`, `RRHH_ASISTENCIA_JUSTIFICAR`, `RRHH_VALE_CREAR`, `RRHH_VALE_CONFIRMAR`, `RRHH_VALE_ANULAR`, `RRHH_PRESTAMO_OTORGAR`, `RRHH_LIQUIDACION_GENERAR`, `RRHH_LIQUIDACION_APROBAR`, `RRHH_LIQUIDACION_PAGAR`, `RRHH_LIQUIDACION_ANULAR`, `RRHH_VACACION_GESTIONAR`, `RRHH_LIQUIDACION_FINAL_GENERAR`, `RRHH_PENALIZACION_REGISTRAR`, `RRHH_BONO_OTORGAR`, `RRHH_CONFIG_EDITAR`, `RRHH_DASHBOARD_VER`, `RRHH_REPORTE_GENERAR`, `RRHH_NOTIFICACIONES_VER`.

**COMISIONES**: `COMISION_REGLA_VER`, `COMISION_REGLA_GESTIONAR`, `COMISION_REGLA_EDITAR`, `COMISION_LIQUIDACION_GENERAR`, `COMISION_LIQUIDACION_APROBAR`, `COMISION_LIQUIDACION_ANULAR`, `COMISION_EQUIPO_GESTIONAR`.

Nota: la separación entre "quien genera", "quien aprueba" y "quien paga" es deliberada (segregación de funciones).

## 7. UI (pantallas)

Sidenav grupo **"Recursos Humanos"**: Dashboard RRHH, Notificaciones (con badge), Reportes RRHH, Cargos, Funcionarios, Turnos, Asistencias, Penalizaciones, Horas extra, Vales, Motivos de vale, Préstamos, Liquidaciones, Bonos, Aguinaldos, Feriados, Config RRHH. Grupo **"Comisiones"**: Reglas, Equipos, Liquidaciones.

| Pantalla | Tipo | Contenido |
|---|---|---|
| Dashboard RRHH | tab | 10 KPIs + gráficos |
| Lista funcionarios | tab | filtros + acciones |
| Funcionario detalle | tab | pestañas: Datos, Cargos, Salarios, Documentos, Asistencia, Vales, Préstamos, Vacaciones, Liquidaciones |
| Alta/edición funcionario, cambio de cargo, cambio de salario, egreso, asignar turno, subir documento | dialogs | |
| Asistencias | tab + dialogs | marcar individual y masiva |
| Penalizaciones / Horas extra / Turnos / Feriados / Bonos / Aguinaldos / Motivos vale | tabs | listas CRUD |
| Vales | tab + dialogs | crear/editar + confirmar |
| Préstamos funcionarios | tab + dialog | lista + crear préstamo |
| Vacaciones | tab + dialog | lista + detalle (períodos, venta de días) |
| Liquidaciones sueldo | tab lista + tab detalle + dialogs | generar, agregar item, pagar |
| Notificaciones RRHH | tab | lista + marcar leído |
| Reportes RRHH | tab | selector + filtros + export |
| Configuración RRHH | tab | CRUD key/value |
| Comisiones: reglas, equipos, liquidaciones | tabs + dialogs | incluye asignar funcionarios a regla, generación masiva mensual |

## 8. Dashboard y reportes

**KPIs del dashboard** (`get-dashboard-rrhh-kpis(periodo)`): totalNominaMes (Σ neto pagado), totalFuncionariosActivos, porcentajeAsistenciaMes, valesPendientes (CONFIRMADO no adelanto), prestamosActivos, proximosCumpleanios (≤30 días), vacacionesProximas (≤30 días), top5Vendedores (Σ ventas CONCLUIDA por vendedor), liquidacionesPendientesAprobacion (BORRADOR), liquidacionesPendientesPago (APROBADA).

**Reportes** (export Excel con exceljs, PDF con pdfmake):

| Reporte | Excel | PDF |
|---|---|---|
| Liquidaciones del mes (nómina) | ✓ | ✓ |
| Asistencia del mes | ✓ | |
| Vales del mes | ✓ | |
| Préstamos activos | ✓ | |
| Comisiones del mes | ✓ | |
| Aguinaldo anual | ✓ | ✓ |
| Resumen IPS (funcionario + patronal) | ✓ | |
| **Recibo de liquidación individual** | | ✓ |

## 9. Notificaciones RRHH

Generación automática al startup + cada 24 h, **idempotente por `clave_dedupe` UNIQUE**:

1. `CUMPLEANIOS` — funcionarios cuyo cumpleaños es hoy.
2. `CUOTA_VENCIDA` — cuota vencida de préstamo funcionario.
3. `VACACION_PROXIMA` — períodos EN_CURSO/PROGRAMADA próximos (±3 días).
4. `LIQUIDACION_PENDIENTE` — liquidación APROBADA sin pagar > 5 días.
5. `COMISION_PENDIENTE` — liquidación de comisión APROBADA no integrada.
6. `DOCUMENTO_VENCE` — documento con vencimiento ≤ hoy+30 días.
7. `PRESTAMO_VENCIDO`, `CONTRATO_VENCE` — variantes adicionales.

UI: badge en sidenav con count de no leídas (refresh 5 min) + tab de lista filtrable.

## 10. Integración financiera

Toda operación con impacto financiero es **transaccional** y ajusta el saldo de caja; las anulaciones crean **contra-movimientos** (`AJUSTE_POSITIVO`) en lugar de borrar — trazabilidad total.

| Evento RRHH | Movimiento de caja | Dirección |
|---|---|---|
| Confirmar vale / crear vale confirmado | EGRESO_VALE | − |
| Pagar liquidación sueldo / final | EGRESO_SALARIO (por el neto) | − |
| Desembolsar préstamo funcionario | EGRESO_DESEMBOLSO_PRESTAMO_FUNCIONARIO | − |
| Cobrar cuota de préstamo (directa) | INGRESO_COBRO_CUOTA_PRESTAMO_FUNCIONARIO | + |
| Cuota descontada en liquidación | (ninguno — incluida en EGRESO_SALARIO) | |
| Anular vale/liquidación pagada | AJUSTE_POSITIVO (contra-movimiento) | + |

Además soporta **pago desde cuenta bancaria** (campos `cuenta_bancaria_id`, `monto_cuenta_bancaria`, `cotizacion` en Vale y LiquidacionSueldo) como alternativa a caja.

**Vínculo con ventas**: el vendedor de una venta es un `Usuario`; el puente para comisiones es `funcionario.usuario_id`.

---

# PARTE II — Estado actual de FRC Comercial

## 11. Backend central (franco-system-backend-servidor)

Stack: Spring Boot 2.1 / Java 8 / PostgreSQL / GraphQL (graphql-java-kickstart) / Flyway / multi-tenant por schema / replicación lógica hacia filiales.

### 11.1 Lo que YA EXISTE (base parcial de RRHH)

**Módulo `personas`** (`domain/personas/`):
- **`Funcionario`** (`personas.funcionario`): persona FK, **cargo FK**, `credito:Float`, `fechaIngreso`, **`sueldo:Float`**, `activo`, sucursal FK, `fasePrueba`, `diarista` (≈ `es_jornalero` de Gourmet), `supervisadoPor` (self-FK), usuario FK, **horario FK** (→ `administrativo.Horario`), `creadoEn`.
- `Persona`: nombre, apodo, nacimiento, documento, sexo, dirección, teléfono, email, ciudad, **`embedding`** (reconocimiento facial), imágenes.
- `Usuario`, `Role`, `UsuarioRole`, `GrupoRole`/`UsuarioGrupo` (roles por nombre, no permisos granulares).
- `PreRegistroFuncionario` (alta previa/aspirantes) con service, repo y GraphQL.
- GraphQL ya expone `funcionariosWithPage`, `funcionariosSearch`, `saveFuncionario`, `deleteFuncionario`.

**`Cargo`** (`domain/empresarial/Cargo.java`, `empresarial.cargo`): nombre, descripción, `supervisadoPor` (jerarquía), **`sueldoBase:Float`**, subcargos.

**Módulo `administrativo` — asistencia (lo más maduro)**:
- **`Horario`** (`administrativo.horario`): descripción, horaEntrada, horaSalida, **toleranciaMinutos**, inicioDescanso, finDescanso, `turno` (enum DIA/NOCHE/MADRUGADA), `dias` (Set<Dia>). ≈ `Turno` de Gourmet pero más rico (descanso + días de semana).
- **`Jornada`** (`administrativo.jornada`, PK compuesta id+sucursalId): registro diario con 4 marcaciones (entrada, salida almuerzo, entrada almuerzo, salida) y **cálculos ya hechos**: `minutosTrabajados`, **`minutosExtras`**, **`minutosLlegadaTardia`**, `minutosLlegadaTardiaAlmuerzo`, snapshot de parámetros del horario, `estado` (NORMAL/INCOMPLETO/AUSENTE), `observacion`. ≈ `Asistencia` de Gourmet pero superior (captura real vs registro manual).
- **`Marcacion`** (`administrativo.marcacion`, PK compuesta): usuario, tipo ENTRADA/SALIDA, **geolocalización** (lat/long/precisión/distancia a sucursal), deviceId/deviceInfo, sucursal entrada/salida, `presencial`, `autorizacion`.
- Servicios/GraphQL completos: `HorarioService`, `JornadaService`, `MarcacionService` + resolvers + schemas (`horario.graphqls`, `jornada.graphqls`, `marcacion.graphqls`), incl. `ajustarJornadaA8Horas`, `guardarObservacionJornada`, `imprimirReporteMarcaciones`.

**Módulo `financiero`**:
- **`MovimientoPersonas`** (`financiero.movimiento_personas`): persona FK, **`tipo` enum `TipoMovimientoPersonas` = ANTICIPO, AGUINALDO, BONO, VENTA_CREDITO, MULTA, PRESTAMO, VACACIONES, NO_DEVOLVIDOS, COBRO, SALARIO, PAGO_SALARIO**, `referenciaId`, `valorTotal:Double`, `vencimiento`, `activo`. ⚠️ Es un libro mayor por persona **sin entidades de detalle ni integración con caja** — el enum anticipa el dominio RRHH monetario pero no hay lógica detrás.
- Infra de dinero reusable: `MovimientoCaja`, `Retiro`/`RetiroDetalle` (con responsable Funcionario y estados), `Gasto`/`GastoDetalle`/`TipoGasto`/`PreGasto` (workflow pre-gasto → autorización → retiro → rendición), `Moneda`, `Cambio`, `FormaPago`, `PdvCaja`, `Conteo`.

**Comisiones**: solo `VentaPorFuncionario` (DTO de reporte "lucro por funcionario", migración `V130.3__add_indexes_lucro_por_funcionario.sql`). No hay motor.

**Notificaciones**: módulo completo (`Notificacion`, `NotificacionDestinatario`, `NotificacionTipoRole`, `NotificacionPreferenciaUsuario`, `NotificacionEnvioLog`) + push FCM (`fmc/service/NotificationDispatchService`).

### 11.2 Infraestructura a reusar

| Pieza | Detalle |
|---|---|
| Patrón entidad | `Identifiable<Long>` + Lombok `@Data` + `@GenericGenerator("assigned-identity")` + campos manuales `usuario` (creador) y `creadoEn`. No hay auditoría automática. |
| Entidades por sucursal | PK compuesta `EmbebedPrimaryKey` (id + sucursalId) + `@IdClass`; services extienden `EmbeddedEntity`. Jornada/Marcacion ya lo usan. |
| CRUD base | `CrudService` (base de los services) |
| Multi-tenancy | `config/multitenant/` — schema por tenant; entidades declaran `@Table(schema="...")`; `@MultiTenantTransactional` |
| Replicación central↔filial | tabla `configuraciones.replication_table` (direcciones MAIN_TO_ALL / MAIN_TO_SPECIFIC / BRANCH_TO_MAIN). Marcacion ya replica BRANCH_TO_MAIN y Jornada MAIN_TO_ALL (`V117__add_jornada_to_replication_table.sql`). Cada tabla RRHH nueva debe registrarse acá. |
| Seguridad | `SecurityGraphQLAspect`: todo resolver exige autenticación; `@Unsecured` exime; `@AdminSecured` exige ROLE_ADMIN (hoy sin uso). **No hay permisos granulares** — roles por nombre en `personas.role`. |
| Schedulers | `@Scheduled` distribuidos por service package (`NotificationDispatchService`, `CotizacionMercadoScheduler`, etc.). Convención: crear `XxxScheduler` en el package del dominio. |
| GraphQL | Por dominio: `*GraphQL.java` (query+mutation resolver) + `input/*Input.java` + `resolver/*Resolver.java` (field resolvers) + `.graphqls` en `resources/graphql/<modulo>/`. |
| Reportes | JasperReports disponible (`service/reports/`, `service/print/`) — el desktop recibe PDF base64. |
| Migraciones | Flyway, **continuar desde `V131.0`** (la más alta actual en `develop` es `V130.3`). **Convención interna de numeración**: `V{max+1}.{dev}__descripcion.sql`, donde el sufijo identifica al desarrollador autor — `.0` = Gabriel, `.1` = Mauro, `.2` = Diego. Si dos devs toman el mismo número en paralelo, el sufijo evita la colisión de versión (`out-of-order=true`). **Todas las migraciones de esta implementación RRHH llevan sufijo `.0`.** Solo aditivas (no DROP/RENAME — estrategia 2 versiones). |

### 11.3 Lo que NO existe en el backend

1. Paquete/dominio `rrhh` dedicado (todo disperso en personas/administrativo/financiero).
2. Históricos de cargo y salario.
3. Documentos de funcionario (legajo digital con vencimientos).
4. Penalizaciones (los `minutosLlegadaTardia` se calculan pero no se penalizan).
5. Valorización/pago de horas extra (los `minutosExtras` se calculan pero no se liquidan).
6. Vales/adelantos como entidad con ciclo de vida (solo enum `ANTICIPO`).
7. Préstamos a funcionarios con cuotas (solo enum `PRESTAMO`).
8. Vacaciones (devengamiento, períodos, prescripción, venta de días) — solo enum.
9. Aguinaldo (cálculo 1/12) — solo enum.
10. Bonos como entidad configurable — solo enum.
11. **Liquidación de sueldo completa** (cabecera/detalle/conceptos/estados/recibo) — inexistente.
12. Liquidación final (finiquito) — inexistente. `Funcionario` ni siquiera tiene `fechaEgreso`/`motivoEgreso`.
13. Motor de comisiones (reglas, requisitos, equipos, liquidaciones).
14. Integración pago RRHH → caja (`MovimientoPersonas` no genera `MovimientoCaja`/`Retiro`).
15. Configuración RRHH parametrizable.
16. Feriados como entidad.
17. Permisos granulares por acción.
18. Reportes RRHH (nómina, IPS, recibo de sueldo).

### 11.4 En desarrollo — branch `fd-93`: Caja Mayor ("Caja Virtual")

Existe trabajo en curso (Mauro) en la branch **`fd-93`** (backend y desktop, mismo nombre en ambos repos) que implementa la **Caja Mayor** de FRC Comercial bajo el nombre `CajaVirtual`. Es la pieza que faltaba para el destino final del dinero de RRHH:

- **`financiero.caja_virtual`**: `nombre`, `tipo` enum (`CAJA_MAYOR`/`CAJA_CHICA`), sucursal?, **responsable (Funcionario)**, usuario?, **saldos multi-moneda como columnas separadas** (`saldo_gs`, `saldo_rs`, `saldo_ds` en Double), `limite_gs` (alerta por límite), `descripcion`, `activo`.
- **`financiero.movimiento_caja_virtual`**: caja FK, `tipo_movimiento` enum (`INGRESO`, `EGRESO`, `TRANSFERENCIA_ENTRADA`, `TRANSFERENCIA_SALIDA`, `PAGO_PROVEEDOR`, `AJUSTE`), `cantidad`, **snapshot `saldo_anterior`/`saldo_posterior`**, moneda?, **`referencia_id` (FK plana genérica — el gancho natural para vincular vale/liquidación RRHH)**, `caja_origen`/`caja_destino` (transferencias entre cajas), usuario, `activo`.
- **`MovimientoCajaVirtualService.registrarMovimiento`**: transaccional, valida saldo suficiente en egresos, actualiza saldo por moneda según denominación. Patrón compatible con el de Gourmet (movimiento + ajuste de saldo atómico).
- **`V114.1__retiro_caja_virtual.sql`**: agrega `retiro.caja_virtual_id` — conecta el flujo `Retiro` existente con la caja virtual.
- Extra en la misma branch: `empresarial.tipo_local` enum en Sucursal (`VENTA`/`DEPOSITO`/`ADMINISTRATIVO`/`VIRTUAL`) + `manejo_stock`.
- **Desktop (`fd-93`)**: módulo `financiero/caja-virtual/` completo — dashboard, lista, alta de caja, registrar movimiento, transferencia entre cajas, historial de movimientos, alerta por límite; entrada en el dashboard financiero y el sidenav.

**Ecosistema Solicitud de Pago (ya en `develop`)**: a la par del módulo de compras se creó `operaciones.solicitud_pago` (+detalle, +recepciones) con estados `PENDIENTE/PARCIAL/CONCLUIDO/CANCELADO` y **`TipoSolicitudPago` = `COMPRA`, `GASTO`, `RRHH`** — el tipo `RRHH` ya está previsto en el enum. El ciclo objetivo del negocio es:

```
Compras ─▶ Solicitud de pago ─▶ Caja Mayor (egreso)
Gastos  ─▶ Solicitud de pago ─▶ Caja Mayor (egreso)
RRHH    ─▶ Solicitud de pago ─▶ Caja Mayor (egreso)   ← lo aporta este plan
```

Ese ciclo **todavía no está cerrado** para ningún origen (la pata Solicitud de pago → Caja Mayor es justamente lo que `fd-93` habilita). La integración financiera de RRHH (§18.3) debe montarse sobre este riel y no inventar uno propio.

⚠️ **Observación sobre `fd-93`**: sus migraciones son `V112.1` y `V114.1` — el sufijo `.1` es correcto (Mauro), pero los números base son **menores** al máximo actual de `develop` (`V130.3`). Con `out-of-order=true` Flyway las aplica igual, pero si aún no corrieron en ningún ambiente conviene renumerarlas (`V131.1`, `V132.1`) antes del merge para respetar la convención `V{max+1}.{dev}`.

## 12. Desktop (frc-sistemas-integrados-angular)

Stack: Angular 15 + Electron + Apollo GraphQL, navegación por tabs, dark mode.

### 12.1 Lo que YA EXISTE

- **Módulo funcionarios** (`src/app/modules/personas/funcionarios/`): modelo con `sueldo`, `cargo`, `horario`, `supervisadoPor`, `diarista`, `fasePrueba`; CRUD completo (list, wizard de alta, dialogs); pre-registro/onboarding con verificación.
- **Cargos** (`modules/empresarial/cargo/`): jerarquía + `sueldoBase`.
- **Horarios** (`modules/administrativo/horarios/`): CRUD + `asignar-horario-dialog`.
- **Marcación/asistencia** (`modules/administrativo/marcacion/`): módulo robusto — reconocimiento facial (`face-recognition.service.ts` local con embeddings por usuario), GPS, pantallas `marcar-horario` y `list-marcacion`, jornadas con `minutosExtras` y `minutosLlegadaTardia` visibles, ajuste de jornada a 8 h, observaciones, impresión de reporte de marcaciones.
- **Roles RRHH definidos**: `VER_FUNCIONARIOS`, `CREAR_FUNCIONARIOS`, `EDITAR_FUNCIONARIOS`, `VER_PRE_REGISTRO_FUNCIONARIOS` (en `modules/personas/roles/roles.enum.ts`).
- **Menú "R.R.H.H." ya existe** en `shared/components/side-mini-variant/side-mini-variant.component.ts` (Clientes, Usuarios, Funcionarios, Proveedores) + sección "Horarios" (Marcar horario, Lista de horarios).
- **Reporte "Lucro por funcionario"** (`modules/operaciones/venta/reportes/lucro-por-funcionario/`).
- **Plomería financiera reusable**: workflow gastos (pre-gasto → autorización → retiro → rendición), retiros, pagos con cuotas (`operaciones/pago/`), multi-moneda con cotizaciones.
- **Infra**: `generics/generic-crud.service.ts`, `layouts/tab/tab.service.ts`, `dialogos.service.ts`, `notificacion-snackbar.service.ts`, `modules/reportes/reporte.service.ts` (PDF base64 del backend), impresión térmica (`modules/print/` + `electron-printer/`).

Nota: `src/app/services/face-ai.service.ts` referido en documentación vieja **ya no existe**; la biometría vive en `administrativo/marcacion/service/`.

### 12.2 Lo que NO existe en el desktop

Liquidaciones de sueldo, vales/adelantos RRHH (el `printVale` del módulo print es un voucher de fiestas, NO reutilizable como dominio), préstamos RRHH, vacaciones, aguinaldo, bonos, penalizaciones, pago de horas extra, comisiones (config/liquidación), liquidación final, dashboard RRHH con KPIs, reportes de nómina/IPS, configuración RRHH, notificaciones RRHH específicas.

## 13. Mobile (frc-mobile)

Stack: Angular 15 + Ionic 6 + Capacitor 5 + Apollo.

### 13.1 Lo que YA EXISTE

- **Marcación de asistencia — el activo más fuerte**: `src/app/pages/marcacion/` con reconocimiento facial en vivo (`@vladmandic/human` local, galería de embeddings que se enriquece con cada marcación), validación GPS contra la sucursal (Haversine, `distanciaSucursalMetros`), estados de jornada (entrada/salida/almuerzo), modo ADMIN para marcar por terceros, hora del servidor (`hora-servidor.service.ts`). Queries ya existentes: `saveMarcacion`, `getEstadoMarcacionUsuario`, `getMarcacionesPorUsuario`, `getJornadasPorUsuario`.
- **Pre-registro de funcionario** (`pages/funcionario/`): legajo básico de ingreso (sin datos salariales). Guarda por REST `/config/pre-registro` (no GraphQL).
- **"Mis finanzas"** (`pages/mis-finanzas/`): dashboard con Convenios + QR; **tarjetas comentadas "Vales y anticipos" y "Estado financiero"** — la intención de diseño ya existe, falta implementación.
- **Perfil** (`pages/informaciones-personales/`): edición de datos + captura de galería facial (3 fotos) + configuración de huella (`@capgo/capacitor-native-biometric`, solo login).
- **Roles definidos**: `VER_FUNCIONARIOS`, `CREAR_FUNCIONARIOS`, `EDITAR_FUNCIONARIOS`, `VER_PRE_REGISTROS_FUNCIONARIOS`, `DIRECTIVO` (poco chequeados aún).
- **Notificaciones push completas**: FCM con topic **`funcionarios`** ya suscripto, deep-link por `notification.data.path`, módulo de notificaciones con preferencias por tipo.
- Infra: `generic/generic-crud.service.ts` (inyecta usuarioId, loading, errores), patrón apollo por operación, `pdf-viewer.service.ts` (útil para recibos), menú lateral por acordeones en `app.component.html`, quick-actions por rol en `home.component.ts`.

### 13.2 Lo que NO existe en mobile

Recibos/liquidaciones de sueldo, vales/anticipos (solo tarjeta comentada), vacaciones, aguinaldo, comisiones, préstamos, datos laborales del funcionario (cargo/salario), historial propio de marcaciones para el empleado (el servicio existe, falta la pantalla).

## 14. Matriz comparativa Gourmet vs Comercial

| Funcionalidad | Gourmet | Comercial backend | Comercial desktop | Comercial mobile |
|---|---|---|---|---|
| Funcionario (datos laborales) | ✅ completo | 🟡 parcial (sin egreso, sin IPS, sueldo Float) | 🟡 CRUD básico + wizard | 🟡 solo pre-registro |
| Cargo | ✅ | ✅ (+jerarquía, mejor que Gourmet) | ✅ | ❌ |
| Histórico cargo/salario | ✅ | ❌ | ❌ | ❌ |
| Documentos funcionario (legajo) | ✅ | ❌ | ❌ | ❌ |
| Turnos/horarios | ✅ | ✅ (Horario, más rico: descanso+días) | ✅ | 🟡 (informativo) |
| Asistencia | ✅ manual | ✅ **superior** (Marcacion+Jornada biométrica GPS) | ✅ | ✅ **superior** |
| Tardanzas calculadas | ✅ | ✅ (`minutosLlegadaTardia`) | ✅ (visible) | ✅ |
| Penalización automática por tardanza | ✅ | ❌ | ❌ | ❌ |
| Horas extra valorizadas/pagas | ✅ | 🟡 (minutos calculados, sin pago) | 🟡 | 🟡 |
| Feriados | ✅ | ❌ | ❌ | ❌ |
| Vales/adelantos | ✅ | ❌ (solo enum) | ❌ | ❌ (tarjeta comentada) |
| Préstamos con cuotas | ✅ (CPP) | ❌ (solo enum) | ❌ | ❌ |
| Vacaciones | ✅ (+venta de días) | ❌ (solo enum) | ❌ | ❌ |
| Aguinaldo | ✅ | ❌ (solo enum) | ❌ | ❌ |
| Bonos | ✅ | ❌ (solo enum) | ❌ | ❌ |
| Liquidación de sueldo | ✅ motor completo | ❌ | ❌ | ❌ |
| Liquidación final | ✅ | ❌ | ❌ | ❌ |
| Comisiones (reglas/equipos/motor) | ✅ | ❌ (solo reporte lucro) | ❌ (solo reporte) | ❌ |
| Notificaciones RRHH | ✅ (in-app) | 🟡 (infra genérica + FCM listos) | 🟡 (módulo genérico) | 🟡 (FCM listo, topic `funcionarios`) |
| Configuración RRHH | ✅ | ❌ | ❌ | ❌ |
| Dashboard RRHH | ✅ 10 KPIs | ❌ | ❌ | ❌ |
| Reportes RRHH / recibo sueldo | ✅ 8 reportes | ❌ (Jasper disponible) | ❌ (visor PDF listo) | ❌ (pdf-viewer listo) |
| Permisos granulares | ✅ ~28 permisos | ❌ (roles por nombre) | 🟡 (4 roles funcionarios) | 🟡 |
| Multi-sucursal | ❌ (mono-local) | ✅ nativo | ✅ | ✅ |
| Marcación biométrica | ❌ | ✅ | ✅ | ✅ |
| Caja Mayor | ✅ (caja central + movimientos) | 🟡 en branch `fd-93` (`CajaVirtual`) | 🟡 en branch `fd-93` (dashboard + movimientos + transferencias) | ❌ |
| Solicitud de pago (workflow) | ❌ | 🟡 (`SolicitudPago` tipo COMPRA/GASTO/**RRHH**; falta la pata → Caja Mayor) | 🟡 | ❌ |

**Lectura clave**: Comercial ya supera a Gourmet en captura de asistencia (biométrica, GPS, multi-sucursal) y estructura organizacional (jerarquías, sucursales). Lo que falta es **toda la capa monetaria de RRHH**: penalizaciones, valorización de HE, vales, préstamos, vacaciones, aguinaldo, bonos, liquidaciones, comisiones, y su integración con caja.

---

# PARTE III — Plan de implementación

## 15. Principios de diseño y mapeo de conceptos

### 15.1 Decisiones rectoras

1. **No duplicar lo que Comercial ya hace mejor.** La asistencia NO se reimplementa: `Jornada`/`Marcacion` son la fuente de verdad. El módulo RRHH **consume** `minutosLlegadaTardia` y `minutosExtras` de Jornada para penalizar y valorizar.
2. **Nuevo paquete de dominio `rrhh`** en el backend (`domain/rrhh/`, `service/rrhh/`, `repository/rrhh/`, `graphql/rrhh/`, `resources/graphql/rrhh/`) con schema PostgreSQL propio **`rrhh`**. Mantiene el módulo cohesivo y separa la numeración de replicación.
3. **RRHH vive en el central (HQ), no en filiales.** Las tablas RRHH de gestión (liquidaciones, vales, vacaciones…) se administran en el central. Se replican MAIN_TO_ALL solo las de consulta que las filiales/mobile necesiten (ver §18.2). La marcación ya replica BRANCH_TO_MAIN — no se toca.
4. **Retrocompatibilidad estricta**: no se modifica ningún campo/método existente usado por desktop/mobile. Todo cambio sobre `Funcionario` es **aditivo** (columnas nullable). Regla del sufijo `Mobile` para endpoints que el mobile necesite adaptar.
5. **Montos en `BigDecimal`** en las entidades nuevas (no `Float`/`Double` como el legado). El `Funcionario.sueldo:Float` existente NO se migra de tipo (prohibido por reglas Flyway); las liquidaciones guardan su propio snapshot `salarioBase` en decimal.
6. **Enums como string** (`@Enumerated(EnumType.STRING)`) — igual que Gourmet, portable y legible.
7. **Estados y contra-movimientos**: se copia el patrón de Gourmet — nada se borra, las anulaciones generan reversas; los pagos son transaccionales.
8. **Migraciones Flyway solo aditivas** desde **`V131.0`**, retrocompatibles, un módulo por migración. Convención `V{max+1}.{dev}`: sufijo `.0` = Gabriel, `.1` = Mauro, `.2` = Diego — esta implementación usa `.0` en todas sus migraciones.
9. **PRs chicos** (<400 líneas netas), target `develop`, conventional commits `feat(rrhh): ...`.

### 15.2 Mapeo de conceptos Gourmet → Comercial

| Concepto Gourmet | Equivalente Comercial | Acción |
|---|---|---|
| `Persona` | `personas.persona` | Reusar tal cual |
| `Funcionario` | `personas.funcionario` | **Extender** (columnas aditivas: fecha_egreso, motivo_egreso, ips_activo, numero_ips, codigo_interno, moneda_salario, valor_jornal, cuenta_bancaria) |
| `Cargo` + `salario_referencia` | `empresarial.cargo` + `sueldoBase` | Reusar (ya es superior: jerarquía) |
| `Turno` | `administrativo.horario` | Reusar (superior: descanso + días) |
| `FuncionarioTurno` | `funcionario.horario` FK directa | 🟡 Gap: Comercial no tiene historial de asignación. Opcional: tabla `rrhh.funcionario_horario_historico` |
| `Asistencia` | `administrativo.jornada` | Reusar. Estados extra (VACACION, JUSTIFICADO, FERIADO) se modelan en `rrhh.jornada_novedad` (ver §16) para no tocar Jornada |
| `minutos_tardanza` | `jornada.minutosLlegadaTardia` (+ almuerzo) | Consumir |
| `HoraExtra` | `jornada.minutosExtras` + nueva `rrhh.hora_extra` (valorización/autorización) | Nueva entidad que referencia jornada |
| `Penalizacion` | — | Nueva `rrhh.penalizacion` (manual + auto desde jornada) |
| `Feriado` | — | Nueva `rrhh.feriado` |
| `Vale`/`MotivoVale` | enum `ANTICIPO` en MovimientoPersonas | Nueva `rrhh.vale` + `rrhh.motivo_vale`; al confirmar genera `MovimientoPersonas` (libro por persona) + egreso de caja |
| CPP `PRESTAMO_FUNCIONARIO` + cuotas | enum `PRESTAMO` | Nueva `rrhh.prestamo` + `rrhh.prestamo_cuota` (no existe un CPP genérico equivalente en Comercial) |
| `Vacacion`/`VacacionPeriodo`/`VacacionVenta` | enum `VACACIONES` | Nuevas entidades `rrhh.*` |
| `Aguinaldo`, `Bono` | enums | Nuevas entidades `rrhh.*` |
| `LiquidacionSueldo/Item/Concepto` | — | Nuevas entidades `rrhh.*` |
| `LiquidacionFinal/Item` | — | Nuevas entidades `rrhh.*` |
| Comisiones (7 entidades) | reporte `VentaPorFuncionario` | Nuevas entidades `rrhh.*`; motor sobre `operaciones.venta`/`venta_item` |
| `ConfiguracionRrhh` | — | Nueva `rrhh.configuracion_rrhh` |
| `NotificacionRrhh` | módulo `configuracion.Notificacion` + FCM | **Reusar** el módulo de notificaciones existente agregando tipos RRHH (mejor que Gourmet: llega push al celular) |
| Caja Mayor (`EGRESO_SALARIO`…) | **`CajaVirtual` tipo `CAJA_MAYOR` (branch `fd-93`)** + `SolicitudPago` tipo `RRHH` + `MovimientoPersonas` | Doble asiento: `MovimientoPersonas` (cuenta corriente del empleado) + egreso real vía Solicitud de pago → `MovimientoCajaVirtual` EGRESO (ver §18.3) |
| Permisos granulares (`checkPermission`) | roles por nombre | Ampliar `roles.enum.ts` con roles RRHH + (opcional) extender `SecurityGraphQLAspect` |
| Documentos en filesystem local | — | `rrhh.funcionario_documento` con storage en disco del server (patrón Google Drive/carpeta local ya existente para imágenes) |

### 15.3 Dónde Comercial quedará MEJOR que Gourmet

- **Asistencia biométrica** con GPS y multi-sucursal alimentando penalizaciones y horas extra automáticamente (Gourmet es carga manual).
- **Notificaciones push reales** al celular del empleado (FCM ya integrado) además de in-app.
- **Self-service del empleado en mobile**: ver recibo de sueldo, solicitar vale, solicitar vacaciones, ver historial de marcaciones — Gourmet no tiene nada de esto.
- **Multi-sucursal nativo** en toda entidad RRHH (Gourmet es mono-local).
- **Jerarquía organizacional** (cargo.supervisadoPor, funcionario.supervisadoPor) utilizable para flujos de aprobación.
- **Workflow de autorización** ya existente (pre-gasto → autorizar → ejecutar) aplicable a vales y vacaciones (solicitud del empleado → aprobación del supervisor).

## 16. Modelo de datos propuesto (backend)

Schema PostgreSQL **`rrhh`** (nuevo). Todas las tablas con `id bigserial`, `usuario_id` (creador), `creado_en timestamp`. Montos `numeric(18,2)`. Enums `varchar` + check implícito por código.

### 16.1 Extensión de `personas.funcionario` (aditiva, V131.0)

```sql
ALTER TABLE personas.funcionario ADD COLUMN fecha_egreso date;
ALTER TABLE personas.funcionario ADD COLUMN motivo_egreso varchar(30);
ALTER TABLE personas.funcionario ADD COLUMN codigo_interno varchar(50);
ALTER TABLE personas.funcionario ADD COLUMN ips_activo boolean DEFAULT false;
ALTER TABLE personas.funcionario ADD COLUMN numero_ips varchar(30);
ALTER TABLE personas.funcionario ADD COLUMN valor_jornal numeric(18,2);
ALTER TABLE personas.funcionario ADD COLUMN moneda_id bigint;          -- moneda del sueldo
ALTER TABLE personas.funcionario ADD COLUMN cuenta_bancaria varchar(100);
```

(`diarista` ya cumple el rol de `es_jornalero`; `sueldo` existente queda como está.)

### 16.2 Tablas nuevas (schema `rrhh`)

| Tabla | Campos clave (resumen — detalle completo en §2 de la Parte I, es el espejo) |
|---|---|
| `historico_cargo` | funcionario_id, cargo_id, fecha_desde, fecha_hasta, motivo |
| `historico_salario` | funcionario_id, salario_anterior, salario_nuevo, moneda_id, fecha_vigencia, motivo, autorizado_por_id |
| `funcionario_documento` | funcionario_id, tipo, nombre_archivo, ruta_relativa, mime_type, tamano_bytes, vencimiento, observacion |
| `feriado` | fecha UNIQUE, descripcion, es_nacional, recargo_porcentaje, activo |
| `jornada_novedad` | funcionario_id, fecha, tipo (VACACION/JUSTIFICADO/FERIADO/AUSENCIA_JUSTIFICADA/MEDIA_FALTA), jornada_id?, sucursal_id?, observacion, registrado_por_id — *cubre los estados de asistencia que Jornada no modela, sin tocarla* |
| `penalizacion` | funcionario_id, jornada_id? + sucursal_id? (si auto), tipo, monto, fecha, auto_generada, anulada, descripcion, registrado_por_id |
| `hora_extra` | funcionario_id, fecha, jornada_id? + sucursal_id?, minutos, tipo (DIURNA/NOCTURNA/FERIADO), recargo_porcentaje, monto_calculado, origen (JORNADA/MANUAL), autorizado_por_id, anulada |
| `motivo_vale` | nombre, descripcion, activo |
| `vale` | funcionario_id, motivo_id?, monto, moneda_id, fecha, estado (SOLICITADO/CONFIRMADO/DESCONTADO/ANULADO), es_adelanto, liquidacion_id?, movimiento_persona_id?, retiro_id?/gasto_id? (egreso real), autorizado_por_id, observacion |
| `prestamo` | funcionario_id, descripcion, monto_total, monto_pagado, moneda_id, fecha_inicio, cantidad_cuotas, estado (ACTIVO/PAGADO/CANCELADO), observacion |
| `prestamo_cuota` | prestamo_id, numero, fecha_vencimiento, monto, monto_pagado, estado (PENDIENTE/PARCIAL/PAGADA/VENCIDA/CANCELADA), fecha_pago |
| `vacacion` | funcionario_id, anio_servicio, dias_generados, dias_gozados, fecha_corte, prescrita |
| `vacacion_periodo` | vacacion_id, fecha_desde, fecha_hasta, dias_usados, estado (SOLICITADA*/PROGRAMADA/EN_CURSO/GOZADA/CANCELADA), autorizado_por_id, novedades_generadas |
| `vacacion_venta` | vacacion_id, dias, monto, fecha, estado (PENDIENTE/PAGADO/ANULADO), liquidacion_id? |
| `aguinaldo` | funcionario_id, anio, monto_calculado, meses_trabajados, estado (CALCULADO/APROBADO/PAGADO), fecha_pago, liquidacion_id? |
| `bono` | funcionario_id, tipo, monto, fecha, motivo, es_recurrente, frecuencia, anulado, liquidacion_id?, autorizado_por_id |
| `liquidacion_concepto` | codigo UNIQUE, descripcion, es_haber, es_calculado_auto, activo |
| `liquidacion_sueldo` | funcionario_id, periodo 'YYYY-MM' (+UNIQUE con funcionario), fecha_inicio/fin, salario_base, total_haberes, total_descuentos, total_neto, moneda_id, estado, aprobado_por_id, fecha_aprobacion, fecha_pago, retiro_id?/gasto_id?, observacion |
| `liquidacion_item` | liquidacion_id CASCADE, concepto_id?, descripcion, monto, tipo (HABER/DESCUENTO), referencia_id?, referencia_tipo?, manual |
| `liquidacion_final` | funcionario_id, fecha_egreso, motivo_egreso, antiguedad_*, salario_promedio_6m, indemnizacion_*, vacaciones_no_gozadas, monto_vac_no_gozadas, aguinaldo_proporcional, total_liquidado, moneda_id, estado, aprobado_por_id, fechas |
| `liquidacion_final_item` | liquidacion_final_id CASCADE, concepto, monto, descripcion |
| `regla_comision` | nombre, descripcion, tipo, monto_base, porcentaje, meta_unidades, meta_monto_local, modo_validacion, recurrencia, fecha_inicio/fin, es_equipo, activo |
| `regla_comision_producto` | regla_id CASCADE, producto_id (vacío = todos) |
| `regla_comision_requisito` | regla_id CASCADE, tipo, umbral, peso, descripcion |
| `funcionario_regla_comision` | funcionario_id, regla_id, fecha_desde, fecha_hasta, activo |
| `equipo_comision` / `equipo_comision_miembro` / `equipo_comision_regla` | (espejo de Gourmet) |
| `liquidacion_comision` | funcionario_id, periodo, fecha_inicio/fin, total_calculado, estado (BORRADOR/APROBADA/INTEGRADA/ANULADA), aprobado_por_id |
| `liquidacion_comision_item` | liquidacion_id CASCADE, regla_id?, concepto, monto, es_manual, observacion (snapshot) |
| `configuracion_rrhh` | clave UNIQUE, valor, tipo (NUMBER/STRING/BOOLEAN/DATE), descripcion, activo |

\* `SOLICITADA` es un estado nuevo (mejora vs Gourmet): permite que el empleado pida vacaciones desde el mobile y un supervisor las apruebe.

Los **valores exactos de enums, seeds de conceptos y claves de configuración** se copian 1:1 de Gourmet (§3, §5 de la Parte I) — ya están validados en producción y ajustados a la ley laboral paraguaya.

### 16.3 Notas de diseño

- `vale`, `liquidacion_sueldo`, `prestamo` referencian el egreso real de dinero mediante FK planas a `retiro_id`/`gasto_id`/`movimiento_persona_id` (según la decisión de §18.3), igual que las FK planas `movimiento_id` de Gourmet.
- `jornada_id` en penalización/hora_extra es FK plana doble (`jornada_id` + `sucursal_id`) porque Jornada tiene PK compuesta.
- Las tablas RRHH **no** usan PK compuesta por sucursal (se gestionan en el central); la sucursal del funcionario ya está en `personas.funcionario.sucursal_id`.
- Índices mínimos: `(funcionario_id, fecha)` en penalizacion/hora_extra/jornada_novedad; `(funcionario_id, periodo)` UNIQUE en liquidaciones; `(funcionario_id, anio_servicio)` en vacacion; `clave` UNIQUE en configuracion.

## 17. Fases de implementación

Cada fase = 1–3 PRs a `develop` (backend primero, luego desktop, luego mobile si aplica). Convención de branch: `feature/rrhh-fase-N-descripcion`. Cada fase deja el sistema deployable y útil por sí misma.

### Fase 0 — Fundaciones (backend + desktop)
**Backend**: migración V131.0 (schema `rrhh` + extensión `personas.funcionario` + `configuracion_rrhh` + `liquidacion_concepto` + seeds); paquete `domain/rrhh/` + `service/rrhh/` base; roles nuevos (INSERT en `personas.role`): `RRHH_VER`, `RRHH_GESTIONAR`, `RRHH_LIQUIDAR`, `RRHH_APROBAR`, `RRHH_PAGAR`, `RRHH_CONFIG` (granularidad simplificada vs Gourmet, mapeable a los ~28 si después se quiere afinar); GraphQL `configuracionRrhh` CRUD.
**Desktop**: sub-menú "R.R.H.H." reorganizado (sección propia con visibilityRoles nuevos); pantalla Configuración RRHH (CRUD key/value tipado).
**Criterio de aceptación**: config RRHH editable desde desktop; roles asignables.

### Fase 1 — Núcleo funcionario ampliado
**Backend**: históricos de cargo/salario (se generan al guardar Funcionario si cambia cargo/sueldo — hook en `FuncionarioService.save` **nuevo método paralelo** para no romper desktop: `saveFuncionarioRrhh` o lógica interna transparente y aditiva); mutación `egresarFuncionario(funcionarioId, fecha, motivo)`; entidad + upload de `funcionario_documento` (multipart o base64, storage en carpeta del server creada con `Files.createDirectories()`); queries `historicoCargos`, `historicoSalarios`, `funcionarioDocumentos`.
**Desktop**: pestañas nuevas en la ficha del funcionario (Históricos, Documentos con visor PDF, botón Egresar con dialog motivo); dialogs cambio de cargo / cambio de salario (crean histórico con autorización).
**Mobile**: —
**Criterio**: alta/cambio/egreso dejan rastro histórico; legajo digital con vencimientos.

### Fase 2 — Feriados, novedades, penalizaciones y horas extra
**Backend**: `feriado` CRUD + seed feriados PY; `jornada_novedad` CRUD; `penalizacion` CRUD + **job `PenalizacionScheduler`** (diario: recorre Jornadas del día anterior con `minutosLlegadaTardia > 0` sin novedad justificada y genera penalización auto según config — espejo de la fórmula Gourmet `monto = fijo + porMinuto × minutos`); `anularPenalizacion`, `justificarJornada` (crea novedad JUSTIFICADO + anula penalizaciones auto de esa jornada); `hora_extra`: generación desde `jornada.minutosExtras` (job o acción manual "consolidar HE del período") con valorización `monto = (sueldo/30/horasJornada) × horas × (1 + recargo/100)` usando recargos de config y feriados.
**Desktop**: pantallas Feriados, Penalizaciones (chip AUTO, anular, justificar), Horas Extra (consolidar/autorizar/anular), Novedades.
**Mobile**: —
**Criterio**: una llegada tarde real (marcación biométrica) genera penalización automática visible y anulable; HE del mes consolidadas y valorizadas.

### Fase 3 — Vales y préstamos
**Backend**: `motivo_vale` + `vale` con ciclo SOLICITADO→CONFIRMADO→DESCONTADO/ANULADO; al confirmar: crea `MovimientoPersonas(tipo=ANTICIPO)` + egreso real (§18.3) en transacción; anular crea reversa. `prestamo` + `prestamo_cuota`: crear con N cuotas, desembolso (egreso), cobro directo de cuota (ingreso) y estado VENCIDA por job diario.
**Desktop**: pantallas Vales (lista + crear + confirmar + anular), Motivos de vale, Préstamos (lista + crear + cobrar cuota). Integración con el flujo de gastos/retiros existente para el egreso.
**Mobile**: **"Mis vales"** en Mis Finanzas (des-comentar la tarjeta): el empleado ve sus vales y **solicita** un vale (queda SOLICITADO; push al aprobador). Endpoints con sufijo `Mobile` (`misValesMobile`, `solicitarValeMobile`).
**Criterio**: vale solicitado desde el celular, confirmado en desktop, plata sale de caja, empleado recibe push.

### Fase 4 — Vacaciones, aguinaldo y bonos
**Backend**: `vacacion` (job anual/al ingreso genera devengamiento por antigüedad según config), `vacacion_periodo` (con estado extra SOLICITADA para self-service), al marcar GOZADA genera `jornada_novedad` VACACION por cada día (idempotente); `vacacion_venta`; prescripción por job. `aguinaldo`: `calcularAguinaldosAnio` = Σ haberes año/12 (usa liquidaciones si existen; fallback sueldo × meses/12). `bono` CRUD (+recurrentes).
**Desktop**: pantallas Vacaciones (saldos por año, programar período, aprobar solicitudes, vender días), Aguinaldos (calcular año, aprobar), Bonos.
**Mobile**: **"Mis vacaciones"**: saldo de días + solicitar período (SOLICITADA, push al supervisor) + estado de solicitudes.
**Criterio**: ciclo completo de vacaciones con solicitud mobile; aguinaldo diciembre calculado.

### Fase 5 — Liquidación de sueldo (el corazón)
**Backend**: `liquidacion_sueldo` + `liquidacion_item` + motor `generarLiquidacionBorrador(funcionario, periodo)` — espejo exacto del algoritmo Gourmet (§4.7) con dos mejoras: HORA_EXTRA sale de las HE consolidadas de Fase 2 (que a su vez salen de Jornada) y PENALIZACION de Fase 2; item DESCUENTO `PRESTAMO_CUOTA` de cuotas vencidas; HABER `VACACION_VENTA` (mejora: Gourmet lo tiene); estados + `aprobarLiquidacion`/`volverBorrador`/`pagarLiquidacion` (transaccional: egreso real + efectos cruzados VALE→DESCONTADO, CUOTA→PAGADA, COMISION→INTEGRADA, AGUINALDO→PAGADO, VACACION_VENTA→PAGADO + `MovimientoPersonas(PAGO_SALARIO)`)/`anularLiquidacion` (reversión total + contra-asiento); generación masiva mensual (`generarLiquidacionesMes`); **recibo de sueldo PDF** vía Jasper (patrón `imprimirReporteMarcaciones` devuelve base64).
**Desktop**: Lista de liquidaciones (filtros periodo/estado), detalle con items (agregar item manual, eliminar), dialogs generar (individual y masivo), aprobar, pagar (elige origen del dinero), anular con confirmación fuerte; imprimir recibo.
**Mobile**: **"Mis recibos"**: lista de liquidaciones PAGADAS del empleado + ver recibo PDF (`pdf-viewer.service`) + push "Tu salario fue liquidado". Sufijo `Mobile`.
**Criterio**: el flujo E2E de §19 pasa completo.

### Fase 6 — Liquidación final
**Backend**: `liquidacion_final` + items + cálculos espejo (§4.8: antigüedad, promedio 6 meses, indemnización, vacaciones no gozadas, aguinaldo proporcional); pagar → egreso + `funcionario.activo=false`.
**Desktop**: se dispara desde el dialog de egreso del funcionario (Fase 1) — genera borrador, muestra desglose, aprobar/pagar.
**Criterio**: egreso por despido injustificado calcula indemnización correcta según config.

### Fase 7 — Comisiones
**Backend**: las 7 entidades de reglas/equipos + `liquidacion_comision`; **motor** espejo de §4.9 adaptado al modelo de ventas de Comercial: query sobre `operaciones.venta`/`venta_item` (verificar campo vendedor/cajero real del modelo Comercial — decisión abierta §20); requisitos TARDANZA_MAX/ASISTENCIA_MIN leen de Jornada, QUEJA_MAX de penalizaciones; snapshot en observación del item; generación masiva mensual; integración como HABER COMISION en liquidación de sueldo (Fase 5 ya deja el hook).
**Desktop**: pantallas Reglas (CRUD + productos + requisitos + asignar funcionarios), Equipos (miembros con % reparto + reglas), Liquidaciones de comisión (generar, aprobar, item manual).
**Mobile**: (opcional) "Mis comisiones" — total del período y detalle por regla.
**Criterio**: la secuencia 9–12 de §19 reproduce el resultado esperado.

### Fase 8 — Notificaciones, dashboard y reportes
**Backend**: `RrhhNotificacionScheduler` diario que genera notificaciones (reusando el módulo `configuracion.Notificacion` + FCM) con dedupe: CUMPLEANIOS, CUOTA_VENCIDA, VACACION_PROXIMA, LIQUIDACION_PENDIENTE, COMISION_PENDIENTE, DOCUMENTO_VENCE, CONTRATO_VENCE (vencimiento de `fasePrueba` — mejora), AUSENCIA_SIN_MARCACION (mejora: funcionario con horario que no marcó); query `dashboardRrhhKpis(periodo)` con los 10 KPIs de §8; reportes Jasper: nómina del mes, asistencia (ya existe base), vales, préstamos activos, comisiones, aguinaldo anual, **resumen IPS** (funcionario 9% + patronal 16.5%), recibo individual.
**Desktop**: Dashboard RRHH (cards KPI + gráficos), pantalla Reportes RRHH (selector + filtros + PDF vía `reporte.service`), badge de notificaciones.
**Mobile**: los push ya llegan por FCM (topic `funcionarios` o token individual con deep-link).
**Criterio**: dashboard con datos reales; los 8 reportes exportan.

### Fase 9 — Pulido mobile self-service (consolidación)
Historial de marcaciones propio (pantalla sobre `getJornadasPorUsuario` ya existente), "Mi resumen RRHH" (saldo vacaciones + vales pendientes + último recibo), quick-actions en home por rol, y pantallas admin livianas para DIRECTIVO (aprobar vacaciones/vales desde el celular).

### Resumen de esfuerzo relativo

| Fase | Backend | Desktop | Mobile | Riesgo |
|---|---|---|---|---|
| 0 Fundaciones | M | S | — | bajo |
| 1 Funcionario+ | M | M | — | bajo |
| 2 Penalizaciones/HE | L | M | — | medio (jobs sobre Jornada) |
| 3 Vales/Préstamos | L | M | M | medio (integración caja) |
| 4 Vacaciones/Aguinaldo/Bonos | L | M | M | bajo |
| 5 Liquidación sueldo | XL | L | M | **alto** (motor + transacciones) |
| 6 Liquidación final | M | S | — | bajo |
| 7 Comisiones | XL | L | S | **alto** (modelo de ventas) |
| 8 Notif/Dash/Reportes | L | M | S | bajo |
| 9 Mobile self-service | S | — | M | bajo |

**Orden confirmado (rev. 3):** arrancan **ya** las Fases **0 → 1 → 2** (no dependen de caja: fundaciones, funcionario ampliado, penalizaciones/HE sobre Jornada). Las Fases **3 (vales) y 5 (liquidación)** entran **tras el merge de `fd-93`** (Caja Mayor), porque su pago real usa `SolicitudPago RRHH → MovimientoCajaVirtual` (§18.3). Secuencia completa: 0 → 1 → 2 → **[merge fd-93]** → 3 → 5 → 4 → 6 → 8 → 7 → 9. La 4 (vacaciones/aguinaldo/bonos) puede solaparse con la 5 porque el motor de liquidación degrada bien si aún no hay vacaciones/aguinaldo. Las fases 3 y 5 son las que más valor entregan al negocio.

## 18. Consideraciones transversales

### 18.1 Retrocompatibilidad y regla Mobile

- Ningún campo GraphQL existente se elimina/renombra. `Funcionario` solo gana campos.
- `FuncionarioGraphQL.saveFuncionario` es usado por desktop: si la lógica de históricos requiere firma nueva, crear `saveFuncionarioConHistorico` paralelo o hacer la generación de histórico transparente (comparar estado previo dentro del service, sin cambiar firma). Documentar en JavaDoc `Usado en: Desktop: Sí / Mobile: No`.
- Endpoints pensados para mobile llevan sufijo `Mobile` y devuelven DTOs mínimos (`misRecibosMobile`, `solicitarValeMobile`, `misVacacionesMobile`).
- Cambios de schema GraphQL: solo aditivos; verificar con grep en `desktop/src/app/modules/*/graphql/` antes de tocar cualquier tipo existente.

### 18.2 Replicación y multi-tenancy

- Tablas RRHH de gestión (liquidaciones, vales, préstamos, vacaciones, config): **solo central**, no replicar (las filiales no las necesitan y son datos sensibles).
- Candidatas a replicar MAIN_TO_ALL: `rrhh.feriado` (si filiales validan feriados), `rrhh.configuracion_rrhh` (si algún cálculo corre en filial). Decidir por necesidad real; registrar en `configuraciones.replication_table` vía migración (patrón V117).
- La marcación ya replica BRANCH_TO_MAIN: los jobs de penalización/HE corren **en el central** sobre datos consolidados. Considerar el lag de replicación: el job de penalizaciones debe correr sobre el día N−1, no el día corriente.
- Datos sensibles (sueldos): evaluar restricción de acceso por rol antes de exponer queries; nunca replicar sueldos a filiales.

### 18.3 Integración con caja (decisión de diseño)

Gourmet usa una Caja Mayor central. Comercial tiene `PdvCaja` por sucursal + workflow `PreGasto→Gasto→Retiro` + ecosistema **Solicitud de pago** (`operaciones.solicitud_pago`, tipos `COMPRA`/`GASTO`/`RRHH`) + **Caja Mayor en desarrollo** (`CajaVirtual` tipo `CAJA_MAYOR`, branch `fd-93`, ver §11.4). El ciclo objetivo del negocio — `Compras/Gastos/RRHH → Solicitud de pago → Caja Mayor` — aún no está cerrado para ningún origen; RRHH debe **cerrarlo por el mismo riel**, no crear uno paralelo:

- **Opción A (✅ DECIDIDA — rev. 3)**: cada pago RRHH (liquidación, vale confirmado, desembolso de préstamo, liquidación final) genera una **`SolicitudPago` tipo `RRHH`** que se salda con un **`MovimientoCajaVirtual` EGRESO** contra la `CajaVirtual` `CAJA_MAYOR`, usando `referencia_id` para vincular al vale/liquidación (FK plana, mismo patrón `movimiento_id` de Gourmet). Reusa aprobación (estados PENDIENTE→CONCLUIDO) y deja a Compras/Gastos el mismo camino ya pavimentado.
- **Opción B**: `Gasto` con `TipoGasto` seed ("PAGO SALARIO", "VALE FUNCIONARIO", "DESEMBOLSO PRESTAMO") reusando el workflow pre-gasto → autorización → rendición. Válida si el negocio prefiere que RRHH pase por rendición de gastos; más pasos por pago.
- En ambas: **siempre** se crea además el `MovimientoPersonas` correspondiente (ANTICIPO/PAGO_SALARIO/PRESTAMO/AGUINALDO/VACACIONES/MULTA/BONO) — así la cuenta corriente del empleado queda completa y el enum existente por fin se usa.
- Anulaciones: contra-asiento (nunca borrar) — con `CajaVirtual` el contra-asiento es un `MovimientoCajaVirtual` de signo inverso (tipo `AJUSTE`) referenciando el original, espejo del patrón `AJUSTE_POSITIVO` de Gourmet.
- **Dependencia**: la Fase 3 (vales) y la Fase 5 (liquidación) requieren que `fd-93` esté mergeada en `develop`. Si se retrasa, las FK planas (`referencia_id` inverso) permiten empezar con la Opción B y re-apuntar después sin cambio de modelo.

### 18.4 Seguridad

- **✅ DECIDIDO (rev. 3)**: 8–10 roles por nombre (patrón actual), **sin** granularidad de aspecto por ahora — `RRHH_VER`, `RRHH_GESTIONAR`, `RRHH_LIQUIDAR`, `RRHH_APROBAR`, `RRHH_PAGAR`, `RRHH_CONFIG`, `COMISION_GESTIONAR`, `COMISION_APROBAR` + los 4 de funcionarios ya existentes. Chequeo en front (desktop `visibilityRoles`/`openTabIfAuthorized`, mobile `role.service`) y en resolvers críticos (aprobar/pagar/anular) validando el rol server-side (no confiar solo en el front).
- Mantener la **segregación de funciones** de Gourmet: quien genera ≠ quien aprueba ≠ quien paga.
- No tocar `security/TokenController.java` ni `JwtGenerator.java` (vulnerabilidades conocidas, fuera de alcance).

### 18.5 CI/CD y proceso

- Backend: `./mvnw clean verify -B -DskipFlyway=true` antes de cada PR; migraciones desde **`V131.0`** con la convención `V{max+1}.{dev}` (`.0` Gabriel / `.1` Mauro / `.2` Diego — esta implementación siempre `.0`), numeración única, nunca modificar aplicadas; checklist DB del CLAUDE.md en cada PR con migración. Antes de numerar, verificar el máximo real en `develop` **y en branches en vuelo** (ej. `fd-93`) para no colisionar.
- Desktop: `npm run check` (AOT) obligatorio antes de push.
- Mobile: features RRHH son solo código Angular → se propagan por OTA (CapacitorUpdater), sin release a Play Store (no tocan plugins nativos).
- PRs < 400 líneas netas, draft, target `develop`, merge commit (no squash), sin push a branches protegidas, sin deploy los viernes.
- Commits: `feat(rrhh): ...` (minor release). La secuencia de fases genera releases alpha incrementales — coordinar con el equipo qué fases se promueven a beta/stable juntas (mínimo: no promover Fase 5 sin Fase 3, por los descuentos de vales).

## 19. Verificación end-to-end (adaptada de Gourmet)

Secuencia de aceptación integral en ambiente alpha:

1. Seed de roles RRHH y asignación al usuario de prueba.
2. Alta Funcionario A (cargo CAJERO, sueldo 2.500.000 PYG, horario con tolerancia 5 min) → verifica histórico de cargo y salario INGRESO.
3. Subir cédula PDF al legajo con vencimiento próximo → notificación DOCUMENTO_VENCE.
4. Día 1: marcación biométrica normal (mobile). Día 2: marcación con 15 min de tardanza → job genera Penalización AUTO con monto según config.
5. Justificar la jornada del día 2 → penalización auto anulada.
6. Empleado solicita vale de 200.000 desde el mobile → push al aprobador.
7. Confirmar vale en desktop → egreso real en caja/gasto + `MovimientoPersonas ANTICIPO`.
8. Crear préstamo 600.000 / 6 cuotas → desembolso; cobrar cuota 1 directa → ingreso.
9. Crear regla comisión META_UNIDADES (50 u de producto X = 150.000) y asignar a Funcionario A.
10. Registrar 60 ventas CONCLUIDAS con vendedor A del producto X.
11. Generar liquidación de comisión → item 150.000 con snapshot; aprobar.
12. Generar liquidación de sueldo del período → items: SALARIO 2.5M (HABER), COMISION 150k (HABER), HE consolidadas (HABER), IPS −225k, VALE −200k, PRESTAMO_CUOTA −100k, PENALIZACION −X.
13. Agregar bono manual 50.000 → sobrevive a regeneración del borrador.
14. Aprobar → read-only. Pagar → egreso por el neto; vale DESCONTADO; cuota PAGADA; comisión INTEGRADA; `MovimientoPersonas PAGO_SALARIO`; push "salario liquidado" al empleado.
15. Empleado abre "Mis recibos" en el mobile y ve el PDF.
16. Anular la liquidación pagada → reversión total (vale CONFIRMADO, cuota PENDIENTE, comisión APROBADA) + contra-asiento.
17. Solicitar vacaciones desde mobile → aprobar en desktop → al gozarse genera novedades VACACION.
18. Diciembre: calcular aguinaldo (Σ haberes/12), aprobar, pagar en la liquidación del mes.
19. Egresar Funcionario A por DESPIDO_INJUSTIFICADO → liquidación final con indemnización + vacaciones no gozadas + aguinaldo proporcional; pagar → funcionario inactivo.
20. Dashboard RRHH refleja todo; export de reportes nómina + IPS.

## 20. Riesgos y decisiones abiertas

| # | Tema | Detalle | Recomendación |
|---|---|---|---|
| 1 | **Vendedor en ventas de Comercial** — ✅ RESUELTO (rev. 2) | Verificado: `operaciones.venta.usuario_id` y `operaciones.venta_item.usuario_id` (ambos FK a `personas.usuario`, nullable) registran quién vendió; `personas.funcionario.usuario_id` ya existe como puente — **idéntico patrón que Gourmet**. `VentaEstado.CONCLUIDA` disponible para filtrar. | El motor de Fase 7 cruza `COALESCE(venta_item.usuario_id, venta.usuario_id) = funcionario.usuario_id` con `venta.estado = CONCLUIDA`, espejo exacto de §4.9. |
| 2 | **Origen del dinero** | Opción A (SolicitudPago tipo RRHH → CajaVirtual CAJA_MAYOR de `fd-93`) vs B (workflow Gasto) — §18.3. | Opción A: cierra el ciclo Solicitud de pago → Caja Mayor que el negocio ya definió para Compras/Gastos. Depende del merge de `fd-93`. |
| 3 | **`Funcionario.sueldo` es Float** | Precisión monetaria pobre; no se puede cambiar el tipo (regla Flyway). | Las liquidaciones snapshottean en `numeric(18,2)`; opcionalmente estrategia 2 versiones futura para `sueldo_decimal`. |
| 4 | **Permisos granulares** | Gourmet tiene 28 permisos; Comercial roles por nombre. ¿Se replica la granularidad? | Empezar con 8–10 roles RRHH; si se necesita granularidad real, extender `SecurityGraphQLAspect` con anotación `@RoleSecured("RRHH_PAGAR")` (proyecto aparte). |
| 5 | **Multi-sucursal en liquidación** — ✅ RESUELTO (rev. 3) | ¿La nómina se liquida global o por sucursal? Funcionario tiene sucursal. | **Global en el central** con filtro/agrupación por sucursal en pantallas y reportes; una sola Caja Mayor paga la nómina. |
| 6 | **Jornalero/diarista** | Gourmet tiene `valor_jornal`; Comercial `diarista` sin valor. En liquidación, el jornalero cobra `valor_jornal × días trabajados` (de Jornadas) en vez de salario fijo. | Incluir la rama jornalero en el motor de Fase 5 desde el día 1 (mejora sobre Gourmet, que lo tiene a medias). |
| 7 | **IPS multi-empleador / reportes legales** | Gourmet solo calcula %; PY exige planillas específicas (REI). | Fase 8: resumen IPS como Gourmet; planilla legal como mejora posterior. |
| 8 | **Volumen de notificaciones push** | Push masivos a topic `funcionarios` llegan a todos; las RRHH personales deben ir por token individual. | Usar envío por usuario destino (el módulo `NotificacionDestinatario` ya lo soporta). |
| 9 | **Doble fuente de asistencia** | Marcación biométrica (real) vs carga manual (Gourmet-style). ¿Se permite asistencia manual cuando no hay dispositivo? | Sí: `jornada_novedad` + permitir crear Jornada manual con flag (ya existe `presencial`/`autorizacion` en Marcacion como precedente). |
| 10 | **Prescripción de vacaciones** | Ley PY: prescriben; el job debe correr y notificar antes (ej. 60 días antes). | Agregar notificación `VACACION_POR_PRESCRIBIR` (mejora vs Gourmet). |
| 11 | **Dependencia de `fd-93` (Caja Mayor)** | La integración financiera (§18.3 Opción A) requiere `CajaVirtual` mergeada. Además sus migraciones (`V112.1`, `V114.1`, sufijo `.1` de Mauro correcto) están numeradas por debajo del máximo actual (`V130.3`). | Coordinar merge de `fd-93` antes de Fase 3; renumerar sus migraciones a `V131.1`/`V132.1` si aún no corrieron en ningún ambiente. Mientras tanto, las fases 0–2 no dependen de caja. |

---

## Apéndice — Fuentes

- Gourmet: `src/app/database/entities/rrhh/`, `electron/handlers/{rrhh-*,asistencias,vales,vacaciones,liquidacion-*,comisiones,equipos-comision,horas-extra,feriados,funcionario-documentos,configuracion-rrhh,dashboard-rrhh,notificaciones-rrhh,reportes-rrhh}.handler.ts`, `src/app/pages/rrhh/`, `src/app/pages/comisiones/`, `docs/plan-rrhh-comisiones.md`, skill `frc-gourmet-expert` (domains/rrhh.md, rrhh-liquidaciones.md, financiero-cpp-cpc.md).
- Backend: `com.franco.dev.domain.{personas,administrativo,empresarial,financiero}`, `resources/graphql/`, `db/migration/` (V100.3–V130.3), `config/multitenant/`, `security/`.
- Desktop: `src/app/modules/{personas/funcionarios,empresarial/cargo,administrativo/{horarios,marcacion},financiero,operaciones}`, `shared/components/side-mini-variant/`.
- Mobile: `src/app/pages/{marcacion,funcionario,mis-finanzas,informaciones-personales,notificaciones}`, `src/app/services/{face-recognition,geo-location,push-notifications}.service.ts`.
