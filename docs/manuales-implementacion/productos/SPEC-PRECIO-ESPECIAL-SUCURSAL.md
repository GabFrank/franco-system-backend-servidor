# Spec — Precio especial por sucursal (promos con vigencia)

Fecha: 2026-09-27 · Rama: `feature/productos-precio-especial-sucursal` (central, filial, desktop)
Estado: **diseño aprobado en conversación; spec pendiente de revisión.**

## 1. Problema

Hoy una promo de una sola sucursal se hace **editando a mano la base de esa filial**:

- Precio especial (ej. Heineken 250 a 5000 en la sucursal 1, global 6000): `UPDATE` en
  `productos.precio_por_sucursal` de la filial.
- 2x1: se crean desde el sistema una presentación "2x1" (cantidad 2) y su precio, ambos
  `activo=false`; después, en la base de la filial, se ponen `activo=true`.

Por qué no alcanza, verificado en código:

| Hecho | Evidencia |
|---|---|
| `precio_por_sucursal.sucursal_id` es decorativo: el central lo pisa con su property (`0`) | `graphql/productos/PrecioPorSucursalGraphQL.java` (`input.setSucursalId(env.getProperty("sucursalId"))`), `application.properties:93` |
| La UNIQUE `(presentacion_id, tipo_precio_id)` impide dos precios del mismo tipo por sucursal | `V0__initial_schema.sql:9638` |
| La tabla es `MAIN_TO_ALL`: el cambio manual en la filial no sube, y un cambio posterior en el central lo pisa o queda descartado en silencio (divergencia permanente) | `V112__sync_replication_table_with_publications.sql:40-41` |
| No existe ningún módulo de promociones (`Combo` y `ProgramarPrecio` están muertos) | relevamiento, sin usos en desktop |

## 2. Objetivo y alcance

Poder cargar desde el desktop, con permiso de precios:

1. **Un precio especial para uno o varios `precio_por_sucursal` en una o varias sucursales**, con
   vigencia opcional `desde`/`hasta` (días, inclusivos). Al vencer o al cortarlo, la sucursal
   vuelve sola al precio global.
2. **Promos por presentación (2x1, 3x2…)** usando lo mismo: la presentación y su precio existen
   globalmente inactivos; un precio especial los habilita **solo** en las sucursales elegidas.
3. **Una pantalla general** para ver y cortar los precios especiales de todas las sucursales.

**Fuera de alcance** (anotado, no se hace):

- Reglas genéricas de promoción (NxM entre productos distintos, % por monto, combos).
- Vigencia por hora ("happy hour").
- Etiqueta "PROMO" en el ítem del POS.
- La PWA consulta al central, así que ve precios globales. Correcto para su uso (consulta, no cobro).
- Bug preexistente: al marcar un precio como principal, el desktop desmarca los otros **solo en la
  filial local** (`adicionar-precio-dialog.component.ts:240`).
- Bug preexistente: el P.T. del ticket de venta a crédito no resta el descuento y no cuadra con su
  P.U. (`VentaCreditoGraphQL` filial L283).
- Etiquetas de góndola, barra de compras y garantía muestran el precio global.
- Promos ya cargadas a mano en las bases de las filiales. No se migran solas: el plan pide un
  inventario previo y que Franco decida cuáles pasan a precio especial y cuáles se revierten.

## 3. Cómo cobra el POS hoy (lo que el diseño aprovecha)

- El POS pide los productos **a la filial** (`buscador.component.ts:206`, `servidor=false`) con
  `fetchPolicy: 'no-cache'`: cada escaneo trae precios frescos.
- La filial devuelve **todas** las presentaciones y **todos** sus precios, sin filtrar
  (`ProductoResolver.presentaciones`, `PresentacionResolver.precios`).
- El desktop elige el precio en `venta-touch.component.ts` (`crearItem` 913-967): `principal &&
  activo`, o el primero `activo` cuyo tipo esté en la config local (modos MIXTO/ONLY).
- **Corte automático 2x1** (`addItem` 700-790): si la cantidad alcanza una presentación mayor con
  precio activo, parte el ítem. No mira `presentacion.activo`, solo el `activo` del precio.
- El POS guarda `item.precio = precioVenta.precio` (L701, L773). El total de la venta es
  `sum(vi.cantidad * vi.precio)` (`VentaItemRepository.totalByVentaIdAndSucId`).

**Conclusión:** si la filial devuelve el precio con el valor especial, el POS cobra, guarda y
totaliza bien **sin cambiar su lógica de selección**. Hay dos excepciones, encontradas en la
auditoría del plan:

- **Grilla de favoritos (venta táctil por categorías).** Carga los precios una sola vez, al abrir
  el POS (`pdv-categoria.service.ts:32-57`), y su botón "Actualizar" consulta al **central**. Se
  corrige en el desktop (plan, Tarea D5). Un desktop viejo sigue cobrando por favoritos lo que
  cargó al abrir: **no usar el botón "Actualizar"** (pide al central y vuelve al precio global);
  reiniciar el POS en esas cajas, en su lugar, después de cargar o cortar un especial.
- **Desktop web, o Electron con `serverIp` apuntando al central.** Consulta al central
  (`aplicarOverrideWeb`, `configuracion.service.ts:729-741`) y cobra el precio global. El precio
  especial aplica solo en cajas conectadas al servidor de su sucursal.

## 4. Diseño

### 4.1 Datos

Tabla nueva **`productos.precio_especial_sucursal`**. Central `V232.1`, espejo filial `V104.1`.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `bigserial` PK | generado en el central |
| `precio_id` | `bigint NOT NULL` | FK `precio_por_sucursal(id) ON DELETE CASCADE` (solo central) |
| `sucursal_id` | `bigint NOT NULL` | FK `empresarial.sucursal(id)` (solo central) |
| `precio` | `numeric NOT NULL` | `> 0` (CHECK solo central) |
| `fecha_desde` | `date` NULL | NULL = desde siempre |
| `fecha_hasta` | `date` NULL | NULL = sin fin; CHECK `fecha_hasta >= fecha_desde` (solo central) |
| `activo` | `boolean NOT NULL DEFAULT true` | corte manual; no se borra, para que quede historial |
| `usuario_id` | `bigint` | quién lo cargó o modificó |
| `creado_en` | `timestamp DEFAULT now()` | igual que `V231.1` |

- Índice `(sucursal_id, precio_id)` en los dos lados.
- **Restricciones solo en el publisher (central)**, como `V231.1`. El espejo de la filial no
  lleva FK ni CHECK: el apply worker corre con `session_replication_role=replica`.
- **Superposición**: dos filas `activo` del mismo `(precio_id, sucursal_id)` no pueden tener rangos
  que se pisen. Se valida **solo en el servicio del central**, al guardar y al editar. No se usa
  constraint de exclusión porque pide la extensión `btree_gist` en cada base. El riesgo residual
  son dos altas simultáneas del mismo precio y sucursal: es bajo (lo carga una sola persona) y
  acotado (la filial toma el especial de menor `id`, así el resultado es determinístico).
- Alta en `configuraciones.replication_table` como `MAIN_TO_ALL`, sin seed.

### 4.2 Central (escritura)

- Entidad, repositorio, servicio y resolver GraphQL de `PrecioEspecialSucursal`.
- Operaciones (los nombres definitivos los fija el plan):
  - `savePreciosEspeciales(input{precioId, sucursalIds[], precio, fechaDesde, fechaHasta})`:
    crea **una fila por sucursal en una sola transacción**. Si alguna se superpone, no se guarda
    ninguna, y el error nombra la sucursal y la vigencia con la que choca.
  - `updatePrecioEspecial(id, precio, fechaDesde, fechaHasta)`: edita una fila, con la misma
    validación.
  - `cortarPrecioEspecial(id)`: `activo=false`.
  - Queries: `preciosEspecialesPorPrecio(precioId)` y
    `filterPreciosEspeciales(sucursalId, texto, soloVigentes, page, size)` para la pantalla general.
- **Seguridad**: las mutations chequean `CREAR_PRECIOS` o `EDITAR_PRECIOS` a mano, con el mismo
  mecanismo que `TerminalPosGraphQL`. En este repo no hay `@PreAuthorize` y `@AdminSecured` está
  roto (issue #177). Las queries quedan abiertas, como el resto de productos.
- El central **nunca** sustituye precios: con `sucursalId=0` la ficha del producto sigue mostrando
  el global.

### 4.3 Filial (lectura y resolución)

- **Lectura por JDBC** (`PrecioEspecialFuente`), sin entidad JPA.
  - Con OSIV, una consulta JPA que falla limpia el contexto del request, y la venta revienta aunque
    el error se ataje.
  - Vigente significa: `activo AND (fecha_desde IS NULL OR fecha_desde <= hoy) AND (fecha_hasta IS
    NULL OR fecha_hasta >= hoy)`.
  - `hoy` se calcula en **zona fija -03**, no en la zona de la JVM, porque hay filiales con tzdb
    viejo.
  - `precio.especial.habilitado=false` apaga la sustitución sin deploy.
  - Una falla de lectura devuelve el precio global.
- `PresentacionResolver.precios` y `precioPrincipal`: por cada precio con especial vigente para la
  `sucursalId` propia (si hay más de uno, el de menor `id`), devuelven una **copia no administrada** del `PrecioPorSucursal` con
  `precio = especial` y `activo = true`, conservando el **mismo `id`**.
  - **Nunca se modifica la entidad JPA**: un `setPrecio` sobre la entidad administrada puede
    terminar en un flush que graba el 5000 en la tabla global de la filial.
  - Mismo `id` implica que `venta_item.precio_id` sigue apuntando al precio real, y los reportes
    por tipo de precio no cambian.
- `ProductoResolver.presentaciones`: si una presentación está `activo=false` pero alguno de sus
  precios tiene especial vigente en esta sucursal, se devuelve como **copia activa**. Así aparece
  en el buscador de esa sucursal y en ninguna otra (decisión del usuario).
- `Presentacion.precios` por otros caminos (queries que no pasan por el resolver) queda para que
  el plan los enumere.

### 4.4 Impresión y totales mostrados: precio cobrado, no precio de lista

Con precios especiales, el precio de lista y el cobrado difieren **en la misma venta**. Todo lo
que imprime o muestra desde `vi.getPrecioVenta().getPrecio()` pasa a usar
`vi.getPrecio() != null ? vi.getPrecio() : vi.getPrecioVenta().getPrecio()`, el mismo criterio que
el PR filial #144 aplicó a la factura silenciosa. La lógica de descuento de cada lugar no se toca.

Líneas en `develop`:

| Repo | Archivo:línea | Qué produce |
|---|---|---|
| filial | `graphql/operaciones/VentaGraphQL.java` 659, 661 | ticket de venta (P.U., P.T.) |
| filial | `graphql/financiero/FacturaLegalGraphQL.java` 779, 781, 790 | ticket de factura autoimpresa |
| filial | `graphql/financiero/VentaCreditoGraphQL.java` 274, 275, 283 | ticket de venta a crédito |
| filial | `graphql/operaciones/resolver/VentaItemResolver.java` 27 | `valorTotal` del ítem mostrado |
| central | `graphql/operaciones/VentaGraphQL.java` 319, 321, 330 | reimpresión de ticket desde central |
| central | `graphql/financiero/VentaCreditoGraphQL.java` 376, 378, 388 | reimpresión de venta a crédito |

No se tocan porque ya están bien o están comentadas: `VentaGraphQL.itemsFacturaSilenciosa` (filial
L364, ya prioriza `vi.getPrecio()`), `VentaItemService.saveAndSend` L104 (respaldo cuando el cliente
no manda precio; el POS siempre lo manda), `ImpresionService` 798-807 y `VentaResolver` (código
comentado).

Beneficio adicional: arregla las reimpresiones de ventas viejas cuyo precio cambió después.

### 4.5 Desktop

- **Ficha del producto**: cada precio de cada presentación suma la acción *"Precio especial por
  sucursal"*. El diálogo tiene:
  - selección múltiple de sucursales, con buscador y "todas";
  - precio, con el aviso de margen bajo contra costo que ya tiene `adicionar-precio-dialog`;
  - desde y hasta opcionales;
  - lista de los especiales de ese precio con su estado (*vigente / programado / vencido /
    cortado*, calculado en el cliente) y las acciones editar y cortar.
- **Pantalla "Precios especiales"** en el menú Productos: lista filtrable por sucursal, producto y
  "solo vigentes", con la acción cortar.
- Visibilidad: `CREAR_PRECIOS` o `EDITAR_PRECIOS`, en `side-mini-variant.component.ts` y en el
  buscador de pantallas (`search-bar.service.ts`).
- Todas las operaciones van al central (`servidor=true`).

### 4.6 Tabla de datos nuevos: quién escribe y quién lee

| Dato | Escribe | Lee |
|---|---|---|
| fila `precio_especial_sucursal` (central) | `PrecioEspecialSucursalService` (central), desde el diálogo y la pantalla del desktop | queries del central → diálogo y pantalla del desktop |
| fila replicada (filial) | apply worker de la replicación (`MAIN_TO_ALL`) | `PrecioEspecialFuente` (JDBC) → `PrecioEspecialLector` → `PresentacionResolver`, `ProductoResolver` → POS |
| `activo` | desktop (acción cortar) → central | filial (filtro vigente); desktop (estado) |
| `fecha_desde` / `fecha_hasta` | desktop → central | filial (filtro vigente); central (superposición); desktop (estado) |
| `precio` | `crear` y `editar` | `PrecioEspecialFuente`, `PrecioEspecialLector`; desktop |
| `usuario_id` | `crear`, `editar` y `cortar` (quién lo cargó o lo modificó por última vez) | `getUsuarioNickname`, en la columna Usuario de la pantalla general |
| `creado_en` | `crear` (hora -03) | metadata de auditoría que expone GraphQL; el desktop hoy no la muestra |
| `venta_item.precio` con el valor especial (dato existente, valor nuevo) | POS (`item.precio = precioVenta.precio`) | total de la venta, factura SIFEN, tickets (§4.4) |

## 5. Despliegue (§3.2 del ciclo: tabla `MAIN_TO_ALL` nueva)

> **El procedimiento vigente es el de la sección «Despliegue» del plan**, corregido por la
> auditoría. Incluye:
> - inventario previo de las promos manuales;
> - checklist por host;
> - alta manual a la publicación en farmacia y bodega, **sin** el botón "Sincronizar publicaciones";
> - merge del desktop condicionado por canal;
> - recuperación y apagado de emergencia.
>
> Un error del resumen de abajo: una filial sin la tabla no rompe solo el REFRESH. Una vez
> publicada la tabla, esa filial **corta toda su réplica entrante** al primer especial. Además, las
> filas cargadas antes de que una filial refresque su suscripción no le llegan nunca
> (`copy_data=false`).

1. **Filial**: `V104.1` espejo + resolución + arreglo de tickets. Mergear y esperar a que toda la
   flota del canal tenga `104.1` en `flyway_schema_history` (≤15 min).
2. **Central**: `V232.1` + `replication_table` + API + reimpresiones. Deploy manual (`gh workflow
   run Deploy`): `deploy-auto.yml` no se dispara nunca. En farmacia y bodega el alta a la
   publicación es **manual** (los schedulers de replicación están apagados ahí). Verificar
   `pg_subscription_rel.srsubstate='r'` en cada suscripción.
3. **Desktop**: las pantallas. Un desktop viejo cobra el especial igual, porque resuelve la filial.
4. **Recién entonces** se carga el primer precio especial. Sumar la tabla a la publicación
   mientras alguna filial no tiene `104.1` rompe el `REFRESH` de su suscripción. Por eso el
   paso 1 tiene que estar completo en **toda** la flota antes del paso 2, y ningún especial se
   carga hasta que la tabla esté en `srsubstate='r'` en todas las suscripciones.

La tabla nace vacía, así que `copy_data=false` no pierde filas.

**Reversión**: cortar (`activo=false`) todos los especiales devuelve cada sucursal al precio
global de inmediato, sin deploy. Revertir los JAR deja la tabla sin lector, y las cajas vuelven al
precio global.

## 6. Pruebas

- **Central**:
  - superposición rechazada (mismo precio y sucursal, rangos que se pisan, incluidos NULL abiertos);
  - varias sucursales todo o nada;
  - rol ausente rechazado;
  - `hasta < desde` rechazado;
  - `SchemaEnumsSincronizadosTest` y el resto de la suite en verde.
- **Filial**:
  - vigencia con bordes (el día `desde` y el día `hasta` aplican; el día siguiente no);
  - un especial de otra sucursal no aplica;
  - un especial cortado no aplica;
  - un precio inactivo con especial vigente sale activo;
  - una presentación inactiva con especial vigente sale activa, y sin especial sigue inactiva;
  - **después de resolver, la fila global en la base sigue intacta** (prueba de copia no administrada);
  - tickets y `valorTotal` usan `vi.precio`, con respaldo al de lista cuando es NULL.
- **Desktop**: build de producción de la pieza (matriz §0.2).
- **Manual local**: central :8081 y filial :8082 con perfil `dev`, desktop `ng serve -c web`:
  - Heineken a 5000 en la sucursal de la filial local: escanear da 5000 y el ticket imprime 5000;
    otra sucursal sigue en 6000;
  - 2x1 con presentación y precio inactivos: aparece en el buscador, escanear 2 parte en la
    presentación 2x1, cortar la promo y volver a escanear da el unitario;
  - vigencia futura: no aplica hasta el día `desde`.

## 7. Sin verificar todavía

- Cómo resuelve Postgres las `UPDATE` replicadas sobre filas editadas a mano en la filial (pisa o
  descarta). No cambia el diseño: este diseño deja de editar la filial a mano.
- `out-of-order` de Flyway en la filial: **verificado en `true`** (`application.properties:74`). La
  skill de migraciones está desactualizada en ese punto.
- Otras queries del POS: **verificado**. Todos los caminos de cobro (escaneo, buscador y favoritos)
  pasan por `PresentacionResolver.precios`. Favoritos, además, guarda los precios en memoria (§3).
- Que `V232.1` y `V104.1` sigan libres al momento del push (se revalida tras el rebase).
