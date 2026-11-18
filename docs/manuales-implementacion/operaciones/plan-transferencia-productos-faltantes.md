# Plan — Productos faltantes en transferencias

Rama: `feature/transferencia-productos-faltantes` en central y desktop, las dos desde `develop`.

Pedido: una ventana que filtre los productos rechazados por faltante dentro de una transferencia.
Filtros por rango de fechas, producto y sucursal. La lista muestra el id de la transferencia, el
producto, la fecha y otros datos útiles. Se abre con un botón nuevo, **«Productos faltantes»**, en el
dashboard de transferencias.

## Decisión: sin migración

El dato ya existe. `operaciones.transferencia_item` guarda un motivo de rechazo por cada etapa:

- `motivo_rechazo_pre_transferencia`
- `motivo_rechazo_preparacion`
- `motivo_rechazo_transporte`
- `motivo_rechazo_recepcion`

Los cuatro usan el enum `transferencia_item_motivo_rechazo`, con los valores `FALTA_PRODUCTO`,
`PRODUCTO_AVERIADO`, `PRODUCTO_VENCIDO` y `PRODUCTO_EQUIVOCADO`. En la base local hay ~15.900 ítems
con `FALTA_PRODUCTO`, sobre 808 mil.

### Cómo se determina el rechazo

Al avanzar de etapa, el motivo se copia hacia adelante, pero solo cuando no es nulo
(`TransferenciaGraphQL` ~438 a 486). Una etapa posterior puede cambiarlo; por ejemplo, el destino
pasa un faltante a averiado en recepción.

- **Filtro de motivo.** Un ítem entra si **cualquiera** de sus 4 motivos es igual al filtrado. No se
  usa `COALESCE`, porque puede quedarse con un motivo viejo (auditoría A, hallazgo 1).
- **Etapa del rechazo.** Es la primera etapa, en el orden pre → preparación → transporte →
  recepción, cuyo motivo es igual al filtrado. Si es preparación, rechazó el origen; si es recepción,
  rechazó el destino. La calcula el desktop.
- Si el motivo de recepción difiere del filtrado, la fila también muestra el motivo final.

### Limitación aceptada: no hay fecha del rechazo

No se guarda la fecha del rechazo, así que el rango de fechas filtra por `transferencia.creado_en`.
Para tener la fecha real haría falta una columna nueva, que además solo cubriría los rechazos desde
el deploy. Queda fuera de este plan.

### Decisiones por defecto

Franco solo eligió la ubicación del botón. El resto se tomó por defecto y se puede cambiar:

- **Sucursal:** dos filtros opcionales e independientes, origen y destino.
- **Motivo:** un select con los 4 motivos, con `FALTA_PRODUCTO` por defecto. No hay opción «todos».
- **Fecha:** el rango por defecto son los últimos 30 días, y el máximo es de 366 días.
- **Ítems inactivos:** se excluyen los ítems con `activo = false`, porque son ítems anulados.

## Tabla de datos nuevos

**No nace ningún dato**: ni columnas, ni claves, ni configuración. Solo se agrega una query de
lectura sobre datos que ya escriben `saveTransferenciaItem` y `avanzarEtapaTransferencia`.

## Fase 1 — central: query `transferenciaItemsRechazados`

### Schema

En `graphql/operaciones/transferencia-item.graphqls` se agrega:

```graphql
transferenciaItemsRechazados(motivo: TransferenciaItemMotivoRechazo, sucursalOrigenId: ID,
  sucursalDestinoId: ID, productoId: ID, creadoDesde: String, creadoHasta: String,
  page: Int = 0, size: Int = 20): TransferenciaItemPage!
```

La query reutiliza el tipo `TransferenciaItemPage`, que ya existe. **No hay tipos ni DTO nuevos.**

### Repositorio

`TransferenciaItemRepository` suma una `@Query` JPQL, con `@Param` explícito en cada parámetro:

- **Origen:** `TransferenciaItem ti`, con `join fetch ti.transferencia t`,
  `join fetch ti.presentacionPreTransferencia pp` y `join fetch pp.producto`. Así se evita el N+1 de
  las relaciones EAGER (auditoría A, hallazgo 2).
- **Motivo:**
  `(ti.motivoRechazoPreTransferencia = :motivo OR ti.motivoRechazoPreparacion = :motivo OR ti.motivoRechazoTransporte = :motivo OR ti.motivoRechazoRecepcion = :motivo)`.
- **Activo:** `(ti.activo IS NULL OR ti.activo = true)`.
- **Origen, destino y producto:** filtros opcionales con `(:x IS NULL OR …)`, como
  `TransferenciaRepository` ~99. El producto sale de `pp.producto.id`, o sea el producto que se pidió.
- **Fechas:** `t.creadoEn >= :desde AND t.creadoEn <= :hasta`. En el resolver siempre llegan con
  valor, así que no hace falta el `cast(... IS NULL)`.
- **Orden:** `t.creadoEn DESC, ti.id DESC`.
- **`countQuery`:** explícito, con los mismos `WHERE` y sin `fetch` ni `ORDER BY`.
- **Timeout:** `@QueryHints(javax.persistence.query.timeout = 10000)`, como defensa ante el peor
  caso. Hoy no hay `statement_timeout` ni en el datasource ni en el servidor (auditoría B, hallazgo 1).

### Servicio

`TransferenciaItemService` solo pasa los filtros al repositorio.

### Resolver

`TransferenciaItemGraphQL.transferenciaItemsRechazados`:

- **Primera línea:** `seg.requireAnyRole(TransferenciaRoles.ROLES)`, igual que las mutations de la
  clase.
- **Motivo:** si viene nulo, se usa `FALTA_PRODUCTO`.
- **Fechas:** pasan por `DateUtils.stringToDate`. Si falta `creadoHasta`, es ahora; si falta
  `creadoDesde`, es `creadoHasta` menos 30 días. Un `creadoHasta` sin hora, o en 00:00, se lleva a
  fin de día. Si el rango supera los 366 días o desde es posterior a hasta, se rechaza con una
  `GraphQLException` con texto legible.
- **Paginado:** `page < 0` pasa a 0, y `size` se acota a 1..100.

No toca entidades, enums ni tablas publicadas: **el filial no cambia**, y no hay espejo ni orden de
replicación que cuidar.

### Tests de la fase 1

- **Gate del CI:** `./mvnw clean verify -B -DskipFlyway=true`. El arranque del contexto valida que la
  JPQL parsee y que el schema coincida con el resolver.
- **Test unitario del resolver**, con el repositorio mockeado. Verifica que:
  - sin rol, la query se rechaza;
  - `motivo` nulo pasa como `FALTA_PRODUCTO`;
  - sin fechas, el rango es de 30 días;
  - un «hasta» sin hora pasa a fin de día;
  - un rango de más de 366 días o invertido se rechaza;
  - `size` y `page` quedan saneados.
- **Runtime:** central local en el 8081, contra `bodega@5551`, con replicación sync/refresh apagada.
  - Ejecutar la query por GraphiQL con cada filtro solo y combinado, incluido un ítem cuyo motivo
    cambió entre etapas. Comparar los conteos con el mismo SQL corrido a mano en psql.
  - Con `size=100` y el log de SQL activo, contar las sentencias por página y medir el tiempo.
  - Probar el rechazo de un rango de 2 años.

## Fase 2 — desktop: ventana «Productos faltantes»

Todos los archivos van en `src/app/modules/operaciones/transferencia/`.

### Query y servicio

- **`graphql/graphql-query.ts`:** `transferenciaItemsRechazadosQuery`. **Cada campo que usa la tabla
  tiene que estar en la query** (memoria «Bug de template»). Pide:
  - del ítem: id, cantidades, los 4 motivos y las 4 observaciones;
  - `presentacionPreTransferencia { cantidad producto { id descripcion } }`;
  - de la transferencia: `id creadoEn estado etapa sucursalOrigen{id nombre} sucursalDestino{id nombre}`
    y los 4 usuarios por etapa (`id persona { nombre }`).

  **No se pide `transferenciaItemList`**: dispara una query por fila.
- **`graphql/getTransferenciaItemsRechazados.ts`:** la clase GQL.
- **`transferencia.service.ts`:** `onGetItemsRechazados(...)` con `GenericCrudService.onCustomQuery`.
  Tiene una sola salida y avisa los errores.

### Componente `list-productos-faltantes/`

Archivos ts, html y scss. Es una versión reducida de `inventario/list-productos-vencidos`.

**Filtros:**

- Rango de fechas (desde y hasta), por defecto los últimos 30 días. El «hasta» se envía con 23:59:59.
- Sucursal de origen y sucursal de destino, como selects cargados con
  `sucursalService.onGetAllSucursales`.
- Producto, con `PdvSearchProductoDialog` y un botón para limpiar.
- Motivo.
- Botones Buscar y Limpiar.
- **Buscar y Limpiar vuelven a la página 1** (memoria «Filtrar vuelve a página 1»).

**Columnas:**

- transferencia (id) y fecha;
- origen → destino;
- producto (presentación);
- cantidad pedida y cantidad preparada;
- motivo, y el motivo final si difiere;
- etapa del rechazo, con el responsable y la observación de esa etapa;
- estado de la transferencia.

**Comportamiento:**

- La etapa, el responsable y la observación se calculan **una vez por fila al recibir la página**,
  con un `map` en el `subscribe`. Nunca con una función en el HTML.
- El paginador de Material usa el paginado del backend.
- El doble clic en una fila abre la transferencia en `EditTransferenciaComponent`, como lo hace
  `list-transferencia`.
- **Estado vacío:** «Sin productos rechazados para estos filtros». **Error:** no se muestra el
  estado vacío; queda el aviso del error y la tabla anterior, porque un error tomado por «no hay
  faltantes» es peor que un error (auditoría B, hallazgo 2).

### Dashboard y módulo

- **`transferencia.component.{ts,html}`:** un botón `app-boton` «Productos faltantes», con el icono
  `remove_shopping_cart`, que abre el tab. Lo ven los mismos que ven el dashboard; el backend exige
  el rol `VER` o `CREAR TRANSFERENCIA`.
- **`operaciones.module.ts`:** declara el componente.

### Tests de la fase 2

- `npm run check` (AOT) al final de la fase.
- **Runtime por pantalla:** desktop web (`ng serve -c web`) contra el central local, entrando con MR.
  Se prueba:
  - el botón del dashboard;
  - cada filtro solo y combinado;
  - el cambio de motivo;
  - el paginado, y que filtrar vuelva a la página 1;
  - que el doble clic abra la transferencia correcta;
  - el estado vacío;
  - el error de rango (más de 366 días) y el de query inexistente (contra un central sin la fase 1),
    que tienen que mostrar un aviso, no la tabla vacía;
  - un ítem rechazado en preparación y otro rechazado recién en recepción, que muestran etapa y
    responsable distintos.

  Los datos se buscan en la base local. Si falta un caso, se crea por SQL y se restaura al terminar.

## Orden de PRs y despliegue

1. **Central (fase 1).** Mientras el central no esté desplegado, la ventana nueva muestra el error de
   query. El resto del dashboard no se ve afectado.
2. **Desktop (fase 2)**, después del deploy del central en el canal.

Los dos PR van hacia `develop`. Se abren después de la prueba y del OK de Franco.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Severidad | Qué se hizo |
|---|---|---|---|
| A | `COALESCE` devuelve un motivo viejo si una etapa posterior lo cambia | media | El filtro pasa a ser un OR de los 4 motivos; la etapa es la primera que coincide |
| A | N+1 por las relaciones EAGER de `TransferenciaItem` y `Presentacion` | media | `join fetch` de la transferencia, la presentación y el producto; `countQuery` aparte; se cuentan las sentencias en la prueba |
| A | Parámetros nulos de fecha y de enum | baja | Las fechas siempre llegan con valor desde el resolver; el motivo nunca es nulo; `@Param` explícito |
| A | Ítems con `activo=false` | baja | Se excluyen |
| A | Pedir `transferenciaItemList` dispara una query por fila | baja | Anotado: no se pide |
| A | El «hasta» sin hora pierde el último día; `size < 1` revienta | baja | Fin de día y paginado saneado en el resolver |
| A | Otros clientes (PWA, filial) | — | Sin impacto: la query es aditiva y no hay tipos nuevos |
| B | Seq Scan de los 808k ítems en cada consulta (~120 ms, sin importar el rango); sin tope en el backend; pool de 10 y sin `statement_timeout` | mayor | Rango obligatorio (30 días por defecto, 366 como máximo), `size ≤ 100` y timeout de 10 s. El índice sobre `transferencia(creado_en)` se descarta porque no ayuda |
| B | Desktop publicado antes que el central | menor | Se mantiene el orden central → desktop; el error se muestra como aviso, no como estado vacío, y se prueba |
| B | Rollback | menor | Confirmado sin estado: no hay migración ni escrituras |

## Cambios pedidos tras la primera prueba (18/11)

- Se sacan las columnas «Motivo final» y «Observación».
- Filtro nuevo por id de transferencia (`transferenciaId` en la query). Con id se ignoran las fechas:
  una transferencia de hace más de 30 días quedaría afuera, y la consulta va por el índice de
  `transferencia_id`.
- Filtros en dos filas, con más ancho para las sucursales de origen y destino.

## Qué queda sin verificar

- **Rendimiento en producción.** Se midió en la base local: cada consulta tarda ~120 ms, y la página
  más el `count` suman ~240 ms por búsqueda. No se midió la base real de producción.
  - Si resulta lenta, la salida es un **índice parcial** sobre
    `transferencia_item(transferencia_id) WHERE` alguno de los motivos no es nulo.
  - Ese índice es una migración `.1` aditiva y rompe el «sin migración». En producción conviene
    crearlo con `CONCURRENTLY` en un horario bajo, y en un PR aparte.
  - No se hace ahora.
- **Ítems de transferencias `CANCELADA`:** se muestran, con su estado visible. Si molestan, se agrega
  un filtro de estado.
