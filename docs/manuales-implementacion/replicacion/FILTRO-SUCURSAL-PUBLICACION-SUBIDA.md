# Filtro por sucursal en la publicación de subida de cada filial

Desde #154, cada tabla de `<db>_filial<N>_pub` (la publicación en la base de la filial por la que
sube a central) lleva `WHERE (sucursal_id = N)`. Sin el filtro, una fila con el `sucursal_id` de
otra sucursal subía y chocaba con la PK `(id, sucursal_id)` de la fila legítima en central:
`duplicate key` y la suscripción `filial<N>_sub` cortada.

Lo pone `LogicalReplicationService` en tres lugares: al crear la publicación
(`createRemotePublication`), al agregar una tabla (`agregarTablaAPublicacionFilial`) y, en el sync
de cada hora, a las tablas que ya estaban sin filtro (`aplicarFiltroSucursal`, DROP + ADD en una
transacción con `lock_timeout`). El sync corre 2 minutos después de arrancar el central.

## Cuándo una tabla queda sin filtro

El log del sync dice el motivo de cada una (`sin filtro por sucursal: …`):

| Motivo | Por qué |
|---|---|
| `PostgreSQL < 15` | no existe el filtro por fila |
| `sin columna sucursal_id` | `financiero.movimiento_personas` |
| `la identidad de replica no cubre sucursal_id` | con el filtro, PostgreSQL rechazaría **todo UPDATE y DELETE** de la tabla en la filial («column used in the publication WHERE expression is not part of the replica identity»). Cubre: `FULL`, `DEFAULT` con `sucursal_id` en la PK, `USING INDEX` con `sucursal_id`. En el filial, `operaciones.sorteo_participacion` tiene PK solo `(id)` |
| `tiene filas de otra sucursal o sin sucursal` | esas filas hoy suben; con el filtro se quedarían en la filial sin aviso. Hay que mirar de dónde salen |
| `no se pudo contar las filas de otra sucursal` | el conteo tiene tope de 30 s; se reintenta en la corrida siguiente |

Si a una tabla **ya filtrada** le bajan la identidad (una migración del filial con
`REPLICA IDENTITY DEFAULT`, o un cambio de PK), el sync la sube a `FULL`; si no puede, le saca el
filtro. Una migración del filial que toque la identidad o la PK de una tabla que sube tiene que
dejar `sucursal_id` cubierto.

## Lo que el filtro cambia

- **Una fila con otra sucursal escrita después del filtro ya no sube, y no deja rastro.** Antes
  cortaba la suscripción. Si central «no ve» algo que la filial tiene, mirar el `sucursal_id` de la
  fila.
- **No destraba una suscripción ya cortada**: lo que ya está en el WAL se decodifica con la
  definición vigente al commit. Sigue haciendo falta `ALTER SUBSCRIPTION … SKIP (lsn = …)`.
- **`delete` se sigue publicando**: el filial borra ítems de venta, `cobro_detalle`, cajas
  huérfanas, conteos y vueltos en flujos normales, y central tiene que enterarse. Un `DELETE` a
  mano en la filial de una fila **propia** sigue borrándola en central.

## Sacar los filtros a mano

Revertir el JAR no los saca (el sync viejo los respeta; `editRemotePublication` del JAR viejo
recrea la publicación sin ellos). En la base de la filial, generar el SQL y correrlo:

```sql
SELECT format('BEGIN; ALTER PUBLICATION %I DROP TABLE %I.%I; ALTER PUBLICATION %I ADD TABLE %I.%I; COMMIT;',
              pubname, schemaname, tablename, pubname, schemaname, tablename)
FROM pg_publication_tables
WHERE pubname LIKE '%\_filial%\_pub' AND rowfilter IS NOT NULL;
```

Después de aplicar o sacar filtros, el chequeo de que ninguna tabla corta los UPDATE es, por cada
tabla de la publicación, `BEGIN; UPDATE t SET sucursal_id = sucursal_id WHERE false; ROLLBACK;`:
PostgreSQL valida la identidad aunque no haya filas.
