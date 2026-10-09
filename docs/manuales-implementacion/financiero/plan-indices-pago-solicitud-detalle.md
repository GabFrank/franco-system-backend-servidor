# Plan: indices en pago_solicitud_detalle por movimiento de caja y de banco (issue #371)

Rama: `fix/financiero-indices-pago-solicitud-detalle` (desde `origin/develop` 6c59abfe).
Pieza: solo central. Este archivo se borra en el PR final (ciclo, paso 11).

## Problema

`financiero.pago_solicitud_detalle` se consulta por dos columnas sin indice, una vez por fila de
la pagina de movimientos (10 a 50 filas) cada vez que se abre o refresca la tabla de caja mayor o
la de banco:

| Columna | Metodo | Resolver de campo |
|---|---|---|
| `movimiento_caja_virtual_id` | `PagoSolicitudDetalleRepository.existsByMovimientoCajaVirtualId` | `MovimientoCajaVirtualFieldResolver.esPagoConsolidado` |
| `movimiento_bancario_id` | `PagoSolicitudDetalleRepository.findFirstByMovimientoBancarioId` | `MovimientoBancarioFieldResolver.pagoId` |

Indices que existen: PK, `solicitud_pago_id` (V182.5), `pago_id` (V190.5), `cheque_id` (V191.5).

Medido en produccion el 2026-10-09 (solo lectura):

| | bodega | farmacia |
|---|---|---|
| Filas | 1.026 | 26 |
| Con movimiento de caja / bancario | 610 / 263 | 23 / 3 |
| Tamano | 200 kB | 8 kB |
| `seq_scan` / `seq_tup_read` | 22.053 / 10.187.577 | 12.010 / 231.932 |
| En alguna publicacion | no | no |
| Ultima migracion aplicada | V235.1 | V236.3 |

Bodega crece ~130 filas por semana desde el 2026-08-15. Hoy el costo no se nota; sube con la tabla.

## Fase 1 (unica): migracion

Archivo nuevo `src/main/resources/db/migration/V239.1__financiero_pago_detalle_indices_movimiento.sql`:

```sql
CREATE INDEX IF NOT EXISTS ix_pago_solicitud_detalle_mov_caja
    ON financiero.pago_solicitud_detalle (movimiento_caja_virtual_id)
    WHERE movimiento_caja_virtual_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_pago_solicitud_detalle_mov_banco
    ON financiero.pago_solicitud_detalle (movimiento_bancario_id)
    WHERE movimiento_bancario_id IS NOT NULL;
```

- **Numero:** el mayor de `origin/develop` es `V238.1`; ningun PR abierto trae migracion. Sufijo
  `.1` (Franco). Se re-verifica contra `develop` antes de cada push.
- **Parciales:** cada linea lleva a lo sumo una de las dos columnas. Las dos columnas se mapean
  como `Long` plano (`PagoSolicitudDetalle.java:62-66`, sin `@ManyToOne`), asi que las consultas
  derivadas filtran por igualdad contra un `bigint`, sin join ni cast. Que el planner use el indice
  parcial con plan generico de sentencia preparada esta **medido**, no supuesto: ver «Tests».
  No hay otra consulta sobre esas dos columnas en `src/main` (ni `@Query`, ni nativa, ni reportes),
  asi que no queda ningun acceso que necesite las filas con NULL indexadas.
- **Sin `CONCURRENTLY`:** Flyway corre la migracion en una transaccion. El `CREATE INDEX` comun
  toma `SHARE` sobre la tabla (bloquea escrituras, no lecturas) mientras construye. Corre en el
  arranque (`FrancoSystemsApplication` hace `flyway.migrate()` antes de atender), con la instancia
  anterior ya detenida por el deploy, y con 200 kB dura milisegundos.
- **Sin codigo Java, sin `.graphqls`, sin espejo en filial:** el filial no tiene la tabla ni la
  entidad. La tabla no esta publicada: ninguna migracion la agrega a una publicacion ni a
  `configuraciones.replication_table` (el unico camino de runtime, via
  `LogicalReplicationService.syncPublicationsWithReplicationTable`), y `pg_publication_tables` no
  la lista en alpha, bodega ni farmacia (consultado el 2026-10-09). Aunque se publicara mas
  adelante, un indice no viaja por replicacion ni la afecta.

### Datos nuevos

Ninguno. No nace campo, columna ni clave: solo dos indices sobre columnas que ya tienen escritor
(el motor de pagos CPP) y lector (los dos resolvers de arriba).

### Tests

- `N/A test automatizado para central porque` el CI corre con `-DskipFlyway=true` y en `src/test`
  no hay ningun test que valide migraciones o indices para extender (el unico de esquema es
  `SchemaEnumsSincronizadosTest`, de enums GraphQL): un indice no es observable desde la bateria.
  La regla «revertir el fix y ver que el test falla» se cumple a mano con el `EXPLAIN` antes y
  despues.
- **Medido el 2026-10-09** en `bodega@5551` local, dentro de una transaccion revertida, con 1.000
  filas sinteticas (13 paginas, volumen parecido al de bodega) y `plan_cache_mode =
  force_generic_plan`: antes, `Seq Scan` en las dos consultas (costo 25,85); despues, `Index Only
  Scan using ix_pago_solicitud_detalle_mov_caja` (8,29) e `Index Scan using
  ix_pago_solicitud_detalle_mov_banco` (8,16). Con las 28 filas reales de la base local (1 pagina)
  el planner sigue eligiendo `Seq Scan` porque es mas barato: ahi el criterio es que el indice sea
  **elegible** (`enable_seqscan = off`), no que se elija.
- Bateria igual: `./mvnw clean verify -B -DskipFlyway=true` (no debe cambiar).
- Verificacion real (paso 10): aplicar la migracion sobre la base dev local `bodega@5551` levantando
  el central con perfil `dev`, y comprobar:
  1. `flyway_schema_history` registra `239.1` con `success = t`;
  2. `pg_indexes` muestra los dos indices con su `WHERE`;
  3. con `plan_cache_mode = force_generic_plan` y `enable_seqscan = off`, el `EXPLAIN` de las dos
     consultas usa el indice nuevo (elegible con plan generico);
  4. segunda corrida de la sentencia: idempotente (`IF NOT EXISTS`).

## Rollback

Aditiva. La version anterior del backend funciona igual contra el esquema nuevo (no conoce los
indices). Para deshacer a mano: `DROP INDEX financiero.ix_pago_solicitud_detalle_mov_caja,
financiero.ix_pago_solicitud_detalle_mov_banco;`.

## Despliegue

Requiere reinicio del central (lo hace el workflow `Deploy`, manual por instancia). Alpha primero.
Cada canal aplica antes sus pendientes: farmacia (en V236.3) arrastra V237.1, V237.3 y V238.1;
bodega (en V235.1) ademas V236.1 y V236.3. Si un arranque falla, la causa puede ser cualquiera de
esas y no solo esta; el rollback del JAR no deshace ninguna migracion ya aplicada. Tras el deploy
de cada instancia, confirmar en `pg_indexes` que los dos indices quedaron con su `WHERE`.

## Queda sin verificar

- El plan de ejecucion en produccion: con 25 paginas en bodega el planner deberia elegir el indice,
  pero se confirma recien con la migracion aplicada (`idx_scan` de los dos indices en
  `pg_stat_user_indexes` deja de ser 0).
- El N+1 sigue: son 10 a 50 consultas por pagina, ahora por indice. Resolverlo en lote es otro
  cambio y queda fuera de esta issue.

## Auditoria del plan (paso 5, 2026-10-09)

Dos auditores, sin verse. Ningun bloqueante.

| Eje | Hallazgo | Que se hizo |
|---|---|---|
| A | El despliegue nombraba mal lo que arrastra cada canal (faltaba V237.3) | Corregido en «Despliegue» |
| A | «No publicada» no citaba el camino de runtime (`replication_table`) | Agregado en «Fase 1» |
| A, B | Faltaba decir que el bloqueo ocurre en el arranque | Agregado en «Fase 1» |
| B | El uso del indice parcial con plan generico estaba afirmado, no probado; en una base chica el planner puede seguir con `Seq Scan` | Medido con volumen real y criterio cambiado a «elegible» |
| B | El N/A de test no decia que no hay test de migraciones para extender | Agregado en «Tests» |
| B | La base de la rama ya no era la punta de `develop` (#387 mergeado) | Rama llevada a 6c59abfe; V239 sigue libre |
| B | `IF NOT EXISTS` deja un indice homonimo previo sin avisar | No aplica: `pg_indexes` de alpha, bodega y farmacia no tiene esos nombres; se re-chequea tras el deploy |
