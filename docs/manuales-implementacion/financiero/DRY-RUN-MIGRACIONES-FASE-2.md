# Dry-run de las migraciones de la fase 2

**Corrido el 2026-09-14.** Paso 10 del ciclo de implementación: las migraciones Flyway **no las
valida ningún CI** —central corre con `-DskipFlyway=true` y el filial no tiene un solo test que
levante contexto—, así que esta es la única verificación que existe antes de que toquen una base
real. Son 17 migraciones sobre 24 filiales.

## Resultado

| | Central | Filial |
|---|---|---|
| Base fuente | `bodega_producto_devoluciones` (7,9 GB) | `general` (177 MB) |
| Copia | `dryrun_central` en 5551 | `dryrun_filial` en 5552 |
| Migraciones aplicadas | **18** (V216.5 → V226.5) | **59** (V57 → V101.5) |
| De las cuales, de la fase 2 | **7** | **10** |
| Fallidas | **0** | **0** |
| Arranque de la app | no probado (ver abajo) | ✅ `Started FrancoSystemsApplication in 35,7 s` |

**Las 17 migraciones de la fase 2 aplican limpio sobre una base real.**

## Cómo se hizo la copia

`pg_dump --no-publications --no-subscriptions | psql`, **no `CREATE DATABASE ... TEMPLATE`**. Un
copiado por template se lleva las 21 publicaciones y las suscripciones, y un worker de réplica
arrancando contra el mismo slot que el original es un problema real, no teórico.

Después se recrearon las **21 publicaciones** de la fuente con su lista real de tablas (714 pares
pub-tabla), porque seis migraciones del rango tocan publicaciones y sin ellas el fallo sería
artefacto del método y no defecto del código.

## Efectos verificados, no solo "no explotó"

**Central**

- Los tres índices únicos parciales de `financiero.terminal_pos` se crearon:
  `uq_terminal_pos_proveedor_serie`, `uq_terminal_pos_serie_comodin`, `uq_terminal_pos_codigo`.
- `configuraciones.replication_table` quedó con las direcciones correctas, y esto **confirma contra
  una base migrada** lo que el plan afirmaba:

  | Tabla | Dirección |
  |---|---|
  | `financiero.venta_tarjeta` | `BRANCH_TO_MAIN` |
  | `financiero.terminal_pos` | `MAIN_TO_ALL` |
  | `financiero.configuracion_venta_tarjeta` | `MAIN_TO_ALL` |
  | `financiero.formato_terminal_pos` | `MAIN_TO_ALL` |
  | `financiero.formato_terminal_pos_region` | `MAIN_TO_ALL` ← la inserta V225.5 |

**Filial**

- Las seis tablas existen: `captura_cupon`, `configuracion_venta_tarjeta`, `formato_terminal_pos`,
  `formato_terminal_pos_region`, `terminal_pos`, `venta_tarjeta`.
- `terminal_pos` tiene `serie`, `sucursal_id`, `carga_manual_permitida`, `campos_obligatorios`.
- `venta_tarjeta` tiene `datos_extra`.

## Una pregunta que el dry-run abrió y cerró

Ninguna de nuestras migraciones ejecuta `ALTER PUBLICATION`: solo registran la tabla en
`configuraciones.replication_table`. Eso **es lo correcto**, y conviene que quede escrito porque no
es obvio: `ReplicationPublicationSyncScheduler` lee esa tabla y hace el `ALTER PUBLICATION` solo, en
central y en cada filial alcanzable por JDBC remoto
(`LogicalReplicationService:1830, 1866, 1903` — *«no manual ALTER PUBLICATION is required»*).

**Implicancia para el despliegue:** el scheduler corre con **120 s de retraso inicial y después cada
hora**, y solo alcanza a las filiales que estén **levantadas y accesibles** en ese momento. O sea que
`formato_terminal_pos_region` no entra a la publicación en el instante del deploy. Está encendido en
producción (`application.properties:169 replication.sync.enabled=true`); los `false` que aparecen en
los runbooks son de sandbox, `dev` y `ci`, a propósito.

## Lo que este dry-run NO cubre

1. **La colisión de datos en los índices únicos de `terminal_pos`.** La base fuente tiene **cero
   terminales**, así que `uq_terminal_pos_codigo` y `uq_terminal_pos_serie_comodin` se crearon sobre
   una tabla vacía. Si en bodega o farmacia hay dos terminales con el mismo `codigo` o la misma
   `serie`, la migración **falla ahí y no acá**. Es el riesgo que queda vivo, y solo lo cierra un
   dump de la instancia destino.
2. **El arranque del central** contra la copia. Se corrió `flyway:migrate` con el plugin de Maven,
   no el `spring-boot:run`. El filial sí se arrancó entero.
3. **La copia del central no refleja las publicaciones de producción**: en ella ninguna tabla de
   venta con tarjeta figura en publicación alguna, ni siquiera `terminal_pos`, que en producción
   replica. Es una base de desarrollo; sus publicaciones son viejas.

## Ruido del entorno, que no es defecto del código

La primera corrida del central falló en `validate` y después en `V203.5__add_lote_to_inventario_producto_item`
con `coluna "lote_id" ... já existe`. **No es nuestra migración.** Esa base de desarrollo tiene la
columna aplicada sin su fila de historial y le faltan seis migraciones por debajo de su máximo
(162.3, 203.5, 205.5–208.5): historial con huecos, típico de una base que se fue tocando a mano.

Se resolvió preservando el historial viejo (`flyway_schema_history_orig`) y haciendo `baseline` en
**215.5**, de modo que todo lo de 216 para arriba —las de `develop` y las nuestras— se ejecutara de
verdad. Ninguna migración de la fase 2 fue salteada.

**Lo que esto deja como tarea:** no tenemos una copia local fiel de un central productivo. Mientras
no la haya, el dry-run del central valida estructura pero no colisiones de datos.
