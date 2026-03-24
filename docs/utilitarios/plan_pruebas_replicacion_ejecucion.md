# Ejecución del plan de pruebas de replicación lógica (UI + psql)

Referencia: [plan_pruebas_replicacion_ui_cccc81d0.plan.md](/.cursor/plans/plan_pruebas_replicacion_ui_cccc81d0.plan.md)

## Estado ejecutado (automático)

- **Paso 1 – Registro inicial:** Ejecutadas consultas de la sección 1 en master y slave. Resultados: master con 21 publicaciones (incl. `central_filial24_pub`), 19 suscripciones; `configuraciones.replication_table`: MAIN_TO_ALL 59, BRANCH_TO_MAIN 28 (5 con flag). Slave: `central_filial24_sub`, `filial24_central_sub` (ambas enabled), `filial24_pub`.
- **Paso 2 – Limpieza:** Ejecutados DROP en master (`filial24_sub`, `central_filial24_pub`) y en slave (`central_filial24_sub`, `filial24_central_sub`, `filial24_pub`) vía psql (un DROP por invocación).
- **Post-limpieza:** Master: 20 publicaciones, 19 suscripciones (sin filial24). Slave: 0 suscripciones, 0 publicaciones.

---

## Comandos psql (master = 5551, slave = 5552)

Prefijo master: `PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "..."`  
Prefijo slave: `PGPASSWORD=franco psql -h localhost -p 5552 -U franco -d general_fact_test_2 -c "..."`

### Después de 3.1–3.3 (Actualizar servicio / toggle tabla / agregar tabla)

```bash
# Master: configuración replication_table
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT direction, count(*) AS cnt, count(*) FILTER (WHERE replicate_central_to_branch_with_filter) AS con_bidi FROM configuraciones.replication_table WHERE enabled = true GROUP BY direction ORDER BY direction;"
```

### Después de 3.4 (Setup replicación sucursal 24)

```bash
# Master: publicación y tablas
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT pubname, schemaname, tablename FROM pg_publication_tables WHERE pubname = 'central_filial24_pub' ORDER BY tablename LIMIT 30;"
# Master: suscripción
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT subname, subenabled FROM pg_subscription WHERE subname = 'filial24_sub';"
```

### Después de 3.5 Remove (eliminar replicación 24 en UI)

```bash
# Master: no debe existir central_filial24_pub ni filial24_sub
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT pubname FROM pg_publication WHERE pubname = 'central_filial24_pub';"
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT subname FROM pg_subscription WHERE subname = 'filial24_sub';"
```

### Después de 3.5 Setup de nuevo

Mismos comandos que “Después de 3.4”.

### Después de 3.6 (Toggle suscripción)

```bash
# Master: subenabled false o true
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT subname, subenabled FROM pg_subscription WHERE subname = 'filial24_sub';"
```

### Después de 3.7 (Toggle publicación)

```bash
# Master: central_filial24_pub no debe existir (desactivar) o sí (activar)
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT pubname FROM pg_publication WHERE pubname = 'central_filial24_pub';"
```

### Después de 3.8 (Setup en slave / filial)

```bash
# Slave: filial24_pub y suscripciones
PGPASSWORD=franco psql -h localhost -p 5552 -U franco -d general_fact_test_2 -c "SELECT pubname FROM pg_publication ORDER BY pubname;"
PGPASSWORD=franco psql -h localhost -p 5552 -U franco -d general_fact_test_2 -c "SELECT subname, subenabled FROM pg_subscription ORDER BY subname;"
```

### 3.9 Sucursal inactiva (preparación y verificación)

```bash
# Marcar inactiva (ejecutar antes de intentar setup desde UI)
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "UPDATE empresarial.sucursal SET activo = false WHERE id = 24;"
# Verificar que no se creó nada para 24
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT pubname FROM pg_publication WHERE pubname = 'central_filial24_pub';"
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT subname FROM pg_subscription WHERE subname = 'filial24_sub';"
# Reactivar para siguientes pruebas
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "UPDATE empresarial.sucursal SET activo = true WHERE id = 24;"
```

### Después de 3.10 (Refrescar suscripciones)

```bash
# Master: estado de suscripciones
PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_fact_test_2 -c "SELECT subname, subenabled FROM pg_subscription ORDER BY subname;"
```

---

## Orden sugerido (pasos 3–10 desde UI)

| # | Acción en UI | Verificación psql |
|---|----------------|-------------------|
| 3 | Configuración > Replicación Lógica > Tablas de replicación → **Actualizar servicio** | Comando “Después de 3.1–3.3” |
| 4 | **Setup de replicación** para sucursal 24 | Comandos “Después de 3.4” |
| 5 | **Eliminar** replicación 24 → luego **Setup** de nuevo para 24 | Comandos “Después de 3.5 Remove” y “Después de 3.4” |
| 6 | **Desactivar** suscripción filial 24 → luego **Activar** | Comando “Después de 3.6” |
| 7 | **Desactivar** publicación filial 24 → luego **Activar** (si la UI lo expone) | Comando “Después de 3.7” |
| 8 | En app filial (o psql/remotas): **Setup replicación filial** para 24 | Comandos “Después de 3.8” en slave |
| 9 | Poner sucursal 24 inactiva (psql arriba), intentar **Setup** desde UI, verificar rechazo | Comandos “3.9 Sucursal inactiva” |
| 10 | **Refrescar todas las suscripciones** | Comando “Después de 3.10” |
