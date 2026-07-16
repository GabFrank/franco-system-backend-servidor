# Handoff — Continuar RRHH + testing en sesión local (Claude CLI)

> Pegá este documento (o su contenido) como prompt inicial en la sesión local.
> Explica qué se implementó, en qué ramas, y el **pre-work obligatorio** para
> compartir la misma DB que usó el módulo de **devoluciones de producto**.

---

## 0) Objetivo de la sesión local

1. Traer (pull) las ramas de trabajo del módulo **RRHH** en los 3 repos.
2. **Mergear** las ramas de **devoluciones de producto** sobre las de RRHH, resolviendo
   la **colisión de migraciones Flyway** para poder usar **la misma base de datos** del
   módulo de devoluciones **sin conflicto**.
3. Verificar que todo compila/buildea.
4. Empezar el **testing runtime** del módulo RRHH (lo único que falta — ver §5).

---

## 1) Repos y ramas

| Repo | Rama RRHH (trabajás sobre esta) | Rama devoluciones (a mergear) |
|---|---|---|
| `GabFrank/franco-system-backend-servidor` | `feature/rrhh-fase-8-dashboard-reportes` | `feature/devoluciones-productos` |
| `GabFrank/frc-sistemas-integrados-angular` (desktop) | `feature/rrhh-fase-8-dashboard-reportes` | `feature/devoluciones-productos` |
| `GabFrank/frc-mobile` | `claude/hr-module-audit-plan-h40p3r` | `feature/devoluciones-productos` |

**Datos importantes:**
- La rama `feature/rrhh-fase-8-dashboard-reportes` es **acumulativa**: ya contiene TODAS las
  fases de RRHH (0→6, pendientes-motor-recibo, config-flexibilidad, integration/rrhh-caja-mayor).
  **No hace falta** mergear las ramas de fases individuales.
- **Ni RRHH ni devoluciones están mergeadas a `develop`.** Ambas salen del mismo commit base de `develop`.
- Mobile: la rama de RRHH es `claude/hr-module-audit-plan-h40p3r` (no `feature/...`).

---

## 2) Qué se implementó en RRHH (resumen)

Módulo completo de Recursos Humanos, portado de FRC Gourmet, en los 3 componentes.
Detalle exhaustivo en **`docs/manuales-implementacion/rrhh/ESTADO-IMPLEMENTACION-RRHH.md`**.

Bloques funcionales (todos **compilan** y con **tests de lógica pura en verde**):
- **Legajo** del funcionario (histórico de cargos/salarios, documentos, egreso).
- **Asistencia**: feriados, novedades, penalizaciones (job automático), horas extra (valorización).
- **Anticipos**: vales/adelantos y préstamos con cuotas (job de cuotas vencidas) — vía Caja Mayor.
- **Beneficios**: vacaciones (devengamiento/goce/venta + job prescripción), aguinaldo, bonos.
- **Liquidación de sueldo** (motor + recibo PDF) y **liquidación final/finiquito** (+ recibo PDF).
- **Dashboard** de KPIs, **reportes** Jasper (nómina, IPS, vales, préstamos, aguinaldo).
- **Configuración RRHH** (~22 parámetros), **notificaciones** (job diario), **historial de marcaciones**.
- **Mobile**: pantallas "Mis RRHH" (empleado) y "Aprobaciones" (encargado).
- **Manual de uso in-app** (desktop, menú R.R.H.H. → Manual de uso) + manuales en
  `docs/manuales-implementacion/rrhh/` (`manual-usuario-rrhh.html`, `manual-rrhh-referencia-tecnica.html`).

**Lo único pendiente = verificación runtime** (transacciones de caja, efectos cruzados del
pago, jobs, round-trips GraphQL, render real de los PDF). Eso es lo que arranca esta sesión.

**Fase 7 (Comisiones) queda DIFERIDA** — no implementar (solo existe relevamiento/plan).

---

## 3) ⚠️ Pre-work crítico: compartir la DB de devoluciones (colisión de migraciones)

### El problema
Ambos módulos numeraron sus migraciones Flyway **con los mismos números**:

| Nº | RRHH (`fase-8`) | Devoluciones (`devoluciones-productos`) |
|---|---|---|
| V141.0 | `crear_schema_rrhh_fundaciones` | `devolucion_estado_add_values` |
| V142.0 | `rrhh_fase2_feriados_penalizaciones_he` | `create_tipo_devolucion_y_resolucion` |
| V143.0 | `rrhh_fase3_vales_prestamos` | `alter_devolucion_add_columns` |
| V144.0 | `rrhh_fase4_vacaciones_aguinaldo_bonos` | `alter_devolucion_item_add_columns` |
| V145.0 | `rrhh_fase5_liquidacion_sueldo` | `create_motivo_averia` |
| V146.0 | `rrhh_fase1_funcionario_historicos_documentos` | `tipo_origen_vencimiento_add_canje` |
| V147.0 | `rrhh_fase6_liquidacion_final` | `seed_tipo_gasto_devolucion` |
| V148.0 | `rrhh_config_flexibilidad` | `create_devolucion_configuracion` |
| V149.0 | `rrhh_notificacion_tipo_solicitud` | `devolucion_colecta_interna` |
| V150.0 | `rrhh_config_avisos` | `devolucion_config_permitir_stock_negativo` |
| V151.0–V153.0 | — | `retiro_seleccion_manual`, `retiro_colecta_devolucion`, `nota_credito_devolucion` |

Git **no** marca conflicto (los nombres de archivo difieren), pero **Flyway aborta al arrancar**:
`Found more than one migration with version 141.0`.

Además RRHH agrega, fuera de orden, `V112.1__create_caja_virtual` y `V114.1__retiro_caja_virtual`
(integración Caja Mayor). Esas **no colisionan** (números únicos; `out-of-order=true` está activo).

### La solución
La DB a reutilizar **ya tiene aplicadas las migraciones de devoluciones (V141–V153)**, así que
**devoluciones conserva su numeración** y **se renumeran las de RRHH** para que corran **después
de V153.0**. Mapeo (offset +13):

```
V141.0__crear_schema_rrhh_fundaciones.sql              -> V154.0__...
V142.0__rrhh_fase2_feriados_penalizaciones_he.sql      -> V155.0__...
V143.0__rrhh_fase3_vales_prestamos.sql                 -> V156.0__...
V144.0__rrhh_fase4_vacaciones_aguinaldo_bonos.sql      -> V157.0__...
V145.0__rrhh_fase5_liquidacion_sueldo.sql              -> V158.0__...
V146.0__rrhh_fase1_funcionario_historicos_documentos.sql -> V159.0__...
V147.0__rrhh_fase6_liquidacion_final.sql               -> V160.0__...
V148.0__rrhh_config_flexibilidad.sql                   -> V161.0__...
V149.0__rrhh_notificacion_tipo_solicitud.sql           -> V162.0__...
V150.0__rrhh_config_avisos.sql                         -> V163.0__...
```

Reglas al renumerar:
- **Solo cambia el prefijo `Vxxx.0` del nombre del archivo.** El contenido SQL no se toca.
- **Se preserva el orden relativo** (V141→V154, …, V150→V163). El orden importa: `V154`
  (ex-141) crea el schema `rrhh` y debe correr antes que las demás.
- `V112.1`/`V114.1` (caja_virtual) se dejan como están (únicas; corren out-of-order).
- Si preferís que TODO RRHH quede contiguo al final, también podés mover `V112.1`/`V114.1`
  a `V164.0`/`V165.0`, pero **no es necesario**.

> Nota: como estas migraciones RRHH **nunca se aplicaron en la DB de devoluciones**, renumerarlas
> es seguro (Flyway aún no guardó sus checksums). **Nunca** renumeres una migración ya aplicada
> en una DB real de producción — acá es válido porque RRHH todavía no tocó esa DB.

---

## 4) Pasos concretos (por repo)

### 4.1 Backend (`franco-system-backend-servidor`)
```bash
git fetch origin
git checkout feature/rrhh-fase-8-dashboard-reportes
git pull origin feature/rrhh-fase-8-dashboard-reportes

# Traer devoluciones y mergear sobre RRHH
git fetch origin feature/devoluciones-productos
git merge origin/feature/devoluciones-productos
#   -> Git NO va a conflictuar en las migraciones (archivos distintos).
#   -> Pueden aparecer conflictos de código en archivos compartidos
#      (schema.graphqls root, registro de resolvers/entities, application.properties).
#      Resolvelos conservando AMBOS módulos.

# Renumerar migraciones RRHH V141.0..V150.0 -> V154.0..V163.0 (ver tabla §3)
git -C src/main/resources/db/migration mv V141.0__crear_schema_rrhh_fundaciones.sql              V154.0__crear_schema_rrhh_fundaciones.sql
git -C src/main/resources/db/migration mv V142.0__rrhh_fase2_feriados_penalizaciones_he.sql      V155.0__rrhh_fase2_feriados_penalizaciones_he.sql
git -C src/main/resources/db/migration mv V143.0__rrhh_fase3_vales_prestamos.sql                 V156.0__rrhh_fase3_vales_prestamos.sql
git -C src/main/resources/db/migration mv V144.0__rrhh_fase4_vacaciones_aguinaldo_bonos.sql      V157.0__rrhh_fase4_vacaciones_aguinaldo_bonos.sql
git -C src/main/resources/db/migration mv V145.0__rrhh_fase5_liquidacion_sueldo.sql              V158.0__rrhh_fase5_liquidacion_sueldo.sql
git -C src/main/resources/db/migration mv V146.0__rrhh_fase1_funcionario_historicos_documentos.sql V159.0__rrhh_fase1_funcionario_historicos_documentos.sql
git -C src/main/resources/db/migration mv V147.0__rrhh_fase6_liquidacion_final.sql               V160.0__rrhh_fase6_liquidacion_final.sql
git -C src/main/resources/db/migration mv V148.0__rrhh_config_flexibilidad.sql                   V161.0__rrhh_config_flexibilidad.sql
git -C src/main/resources/db/migration mv V149.0__rrhh_notificacion_tipo_solicitud.sql           V162.0__rrhh_notificacion_tipo_solicitud.sql
git -C src/main/resources/db/migration mv V150.0__rrhh_config_avisos.sql                         V163.0__rrhh_config_avisos.sql

# Verificar que NO quedan dos migraciones con el mismo Vnnn
ls src/main/resources/db/migration | grep -oE '^V[0-9]+(\.[0-9]+)?' | sort | uniq -d
#   -> salida vacía = OK

# Compilar (SIFEN falla por dependencia del entorno, es ajeno a RRHH/devoluciones)
./mvnw -q -o compile -DskipFlyway=true

git add -A && git commit -m "chore(rrhh): renumerar migraciones a V154-V163 y merge devoluciones-productos"
```

### 4.2 Desktop (`frc-sistemas-integrados-angular`)
```bash
git fetch origin
git checkout feature/rrhh-fase-8-dashboard-reportes
git pull origin feature/rrhh-fase-8-dashboard-reportes
git fetch origin feature/devoluciones-productos
git merge origin/feature/devoluciones-productos
#   -> Resolver conflictos de código si los hay (menú side-mini-variant, app.module, routing).
npm run check    # AOT build — obligatorio antes de pushear
git add -A && git commit -m "chore(rrhh): merge devoluciones-productos"
```
(No hay migraciones en el desktop.)

### 4.3 Mobile (`frc-mobile`)
```bash
git fetch origin
git checkout claude/hr-module-audit-plan-h40p3r
git pull origin claude/hr-module-audit-plan-h40p3r
git fetch origin feature/devoluciones-productos
git merge origin/feature/devoluciones-productos
npm install --legacy-peer-deps   # la flag es obligatoria en este repo
npm run build
git add -A && git commit -m "chore(rrhh): merge devoluciones-productos"
```

> **No pushear** estas ramas sin confirmación explícita (dispararían release por semantic-release).
> El merge local alcanza para levantar el entorno y testear.

---

## 5) Verificación runtime a testear (lo que falta de RRHH)

Con backend levantado (`./mvnw spring-boot:run`, profile dev, apuntando a la DB de devoluciones
ya migrada + las RRHH V154+) y desktop (`npm start`):

- [ ] **Arranque limpio**: Flyway aplica V154–V163 sin duplicados; app levanta.
- [ ] **Caja Mayor**: confirmar un **vale** descuenta de Caja Mayor y registra `MovimientoPersonas`; anular = contra-asiento.
- [ ] **Préstamo**: desembolso (EGRESO), cobro de cuota (INGRESO), plan de cuotas correcto.
- [ ] **Liquidación de sueldo**: generar borrador arma ítems (salario, HE, bonos, vales, cuotas, penalizaciones, aguinaldo); aprobar → pagar descuenta vales/cuotas y mueve Caja Mayor.
- [ ] **Liquidación final**: cálculo antigüedad/indemnización/vacaciones/aguinaldo; pagar deja al funcionario inactivo.
- [ ] **Recibos PDF** (sueldo y finiquito) y **reportes** (nómina, IPS, vales, préstamos, aguinaldo): render real con iText en dev.
- [ ] **Jobs**: penalización (5:00), cuotas (6:00), prescripción vacaciones (4:00), notificaciones — disparar manualmente o ajustar cron.
- [ ] **Vacaciones**: marcar período GOZADO genera novedades por día.
- [ ] **Mobile**: login, "Mis RRHH" (recibos/vales/vacaciones/marcaciones), solicitar vale/vacación, "Aprobaciones".
- [ ] Regresión rápida de **devoluciones** para confirmar que el merge no lo rompió.

---

## 6) Guardrails (no romper)

- **Nunca** push directo a `master`, `release/beta`, `develop` — siempre PR.
- **Nunca** modificar una migración Flyway ya aplicada en una DB real (checksum). El renumerado de §3
  es válido solo porque esas migraciones RRHH aún no se aplicaron en la DB de devoluciones.
- Backend: strings a la DB en MAYÚSCULAS; endpoints nuevos en GraphQL, no REST; `frc-central-server.jar` no se renombra.
- Desktop: **dark mode siempre**; sin funciones/getters en templates; `npm run check` antes de pushear.
- Mobile: `--legacy-peer-deps` obligatorio; no tocar métodos backend que usa el desktop (regla del sufijo `Mobile`).
- Reportes Jasper: solo fuentes `SansSerif`/`Verdana`; validar plantilla con compile+fill antes de pushear.

---

## 7) Referencias

- `docs/manuales-implementacion/rrhh/ESTADO-IMPLEMENTACION-RRHH.md` — estado detallado por fase.
- `docs/manuales-implementacion/rrhh/PLAN-COMISIONES-FASE7.md` — relevamiento de la fase diferida.
- `docs/manuales-implementacion/rrhh/manual-usuario-rrhh.html` — manual de usuario (imprimible).
- `docs/manuales-implementacion/rrhh/manual-rrhh-referencia-tecnica.html` — referencia con links al código.
- CLAUDE.md de cada repo — convenciones institucionales.
