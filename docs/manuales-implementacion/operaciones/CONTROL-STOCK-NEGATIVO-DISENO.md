# Control de stock negativo — diseño

Fecha: 2026-10-10 · Rama: `feature/inventario-control-stock-negativo` (central, desktop, mobile-pwa)

## Para qué

El equipo de inventario necesita ver qué productos salieron de una sucursal cuando su stock ya
era 0 o negativo, para ir a controlar ese stock. Hoy esas salidas no dejan rastro: una venta del
PDV nunca mira el stock, y una transferencia solo lo mira en el desktop.

## Qué se registra

Una salida se registra cuando **el stock previo del producto en la sucursal era ≤ 0**. Vender 5
con stock 2 no se registra; la venta siguiente, que encuentra -3, sí.

| Origen | Cuándo | Quién registra |
|---|---|---|
| Transferencia | al guardar un ítem **nuevo** con stock del origen ≤ 0 | central, en `saveTransferenciaItem` |
| Venta del PDV | cuando el movimiento de stock de la venta llega replicado al central | central, poller |

El stock es siempre **el del central** (suma de `operaciones.movimiento_stock` activos del
producto en la sucursal). Con una filial de réplica atrasada puede diferir del que veía el cajero.

No hay carga retroactiva: el control registra desde el despliegue.

## Decisiones tomadas con Franco

1. **Negativo en transferencias respeta la configuración.** Si `permitirStockNegativo` es falso,
   el cliente bloquea como hoy y no se llega a guardar. Si es verdadero, aviso con confirmación.
   Stock 0: siempre aviso con confirmación.
2. **Criterio: stock previo ≤ 0**, no «la salida lo deja negativo».
3. **El central registra venga del cliente que venga.** El diálogo se agrega en desktop y en
   mobile-pwa. La app Android (en mantenimiento) no avisa, pero lo que cargue queda registrado.
4. **Las ventas las detecta el central** (enfoque A). La filial no se toca: sin migración, sin
   cambios de publicación ni código nuevo en `saveVenta`.
5. Lista de **solo lectura**, sin estado «revisado». Acceso con el rol `VER INVENTARIO` o ADMIN.

## Central

### Tabla `operaciones.control_stock_negativo` (migración `V243.1`)

Solo en el central. **No se replica** y no entra en `configuraciones.replication_table`.

| Columna | Tipo | Contenido |
|---|---|---|
| `id` | bigserial PK | |
| `sucursal_id` | bigint not null | sucursal de la que salió el stock |
| `producto_id` | bigint not null | |
| `tipo` | varchar not null, `CHECK IN ('VENTA','TRANSFERENCIA')` | |
| `cantidad` | numeric not null | unidades que salieron, en positivo |
| `stock_previo` | numeric not null | stock antes de la salida (≤ 0) |
| `usuario_id` | bigint | quien vendió o cargó el ítem |
| `fecha` | timestamp not null | fecha de la operación (no la del registro) |
| `referencia_id` | bigint | id de la venta o de la transferencia |
| `item_id` | bigint not null | id del `venta_item` o del `transferencia_item` |
| `movimiento_stock_id` | bigint | movimiento que originó el registro (ventas) |
| `creado_en` | timestamp not null default now() | cuándo se registró |

Índices: único `(tipo, item_id, sucursal_id)` — es la idempotencia; `(fecha)`; `(sucursal_id, fecha)`.

Tabla auxiliar `operaciones.control_stock_negativo_cursor (sucursal_id PK, ultimo_movimiento_id)`
para el poller.

### Registro de transferencias

En `TransferenciaItemGraphQL.saveTransferenciaItem`, solo cuando el input no trae `id` (ítem
nuevo): se lee `stockByProductoIdAndSucursalId(producto, sucursalOrigen)` **antes** de
`createMovimientoFromTransferenciaItem` y, si es ≤ 0, se inserta el registro. Va en un servicio
propio (`ControlStockNegativoService.registrarTransferencia`) con `try/catch`: un fallo se loguea
y no impide guardar el ítem.

### Registro de ventas

`ControlStockNegativoScheduler`, `@Scheduled` cada 60 s (patrón de `RetiroTesoreriaScheduler`:
scheduler + procesador en bean aparte para que aplique `@Transactional`). Por cada sucursal:

1. toma los movimientos `VENTA` activos con `id` mayor al cursor, en lotes;
2. para cada uno calcula el stock previo como la suma de movimientos activos del mismo producto y
   sucursal con `creado_en` anterior al del movimiento;
3. si es ≤ 0 inserta el registro (`cantidad = -movimiento.cantidad`, `item_id = referencia`,
   `referencia_id = venta_id` del ítem);
4. avanza el cursor.

Encendido por defecto; se apaga con `inventario.control-stock-negativo.poller.enabled=false`.
Apagado en el perfil `dev`.

Al crear el cursor de una sucursal se inicializa en su `max(id)` actual: no hay retroactivo.

Una venta anulada después conserva su registro: es un control, no un saldo.

### Consulta

Query GraphQL paginada `controlStockNegativo(fechaInicio, fechaFin, sucursalId, tipo, texto, page,
size)`, ordenada por fecha descendente. `texto` busca en la descripción del producto. Primera
línea del resolver: validación de rol `VER INVENTARIO` o ADMIN contra `personas.usuario_role`
(patrón de `FacturacionSecurityService`, porque `@AdminSecured` no funciona — issue #177).

No hay mutations públicas: nadie escribe esta tabla desde un cliente.

## Desktop

- `inventario-dashboard`: botón «Control de stock negativo», visible con `VER INVENTARIO`.
- `list-control-stock-negativo` (módulo de operaciones/inventario, sin módulo Angular nuevo):
  filtros de rango de fecha, sucursal, tipo y búsqueda por descripción; tabla paginada con fecha,
  sucursal, tipo, producto, cantidad, stock previo, usuario y referencia. Tres estados: cargando,
  vacío, error. Sin funciones en el HTML.
- `edit-transferencia.onEjecutarGuardadoItem`:
  - stock `< 0` y la config no lo permite → bloqueo, como hoy;
  - stock `< 0` y la config lo permite, o stock `== 0` → `DialogosService.confirm` con «El
    producto tiene stock 0 / negativo (N). ¿Está seguro de continuar?»; al aceptar sigue a
    `procederConGuardadoItem`, al cancelar limpia el ítem;
  - se mantiene la regla de no mostrar el número si el origen es Compras y falta
    `VER STOCK COMPRAS`, y el fail-closed de #390 cuando el central no responde.

## Mobile-pwa

Al agregar un ítem a una transferencia: consulta `stockPorProducto(id, sucId)` del origen y aplica
la misma regla que el desktop. **Cambio de comportamiento:** hoy la PWA no mira el stock, así que
empieza a bloquear negativos cuando la configuración no los permite.

## Datos nuevos: quién escribe y quién lee

| Dato | Escribe | Lee |
|---|---|---|
| `control_stock_negativo` (filas TRANSFERENCIA) | `ControlStockNegativoService.registrarTransferencia` | query `controlStockNegativo` → `list-control-stock-negativo` |
| `control_stock_negativo` (filas VENTA) | `ControlStockNegativoScheduler` / procesador | ídem |
| `control_stock_negativo_cursor` | procesador del poller | procesador del poller |
| property `…poller.enabled` | `application*.properties` | `@ConditionalOnProperty` del scheduler |

## Orden de entrega

Tres PRs, uno por repo. **Central primero**: el desktop necesita la query nueva y el registro. La
PWA solo usa `stockPorProducto`, que ya existe, pero su aviso no tiene sentido sin el registro.
Filial: N/A (no se toca).

## Sin verificar — se resuelve en el plan

1. **Orden de llegada de los movimientos.** Dos ventas concurrentes de una filial pueden
   confirmarse en orden distinto al de sus ids; un cursor estricto podría saltear una. Hay que
   decidir entre un solapamiento de re-lectura (la idempotencia la da el índice único) o un margen
   de espera, y probarlo.
2. **Ids de movimientos de venta en una sucursal.** Confirmar que todos los movimientos `VENTA` de
   una sucursal nacen en su filial (una sola secuencia) y no también en el central.
3. **Otros llamadores de `saveTransferenciaItem` en el central** (devoluciones, transferencias
   automáticas): decidir si registran o quedan fuera.
4. **Costo del cálculo de stock previo** sobre `movimiento_stock` con los índices actuales
   (`(producto_id, sucursal_id)`), medido contra la copia local de bodega.
5. **Pantalla exacta de la PWA** donde se agrega el ítem (`transferencia-borrador` /
   `transferencia-detalle`) y si ya lee la configuración de transferencias.
6. **Número de migración**: `V242.1` está tomado por una rama sin mergear; re-verificar `V243.1`
   tras cada rebase.

## Cambios tras la auditoría del plan (2026-10-10)

El plan (`CONTROL-STOCK-NEGATIVO-PLAN.md`) manda donde difiera de lo de arriba:

- **Idempotencia.** Las ventas se registran por **movimiento de stock** (`(sucursal_id, movimiento_stock_id)`), no por ítem: un ítem de venta puede tener más de un movimiento activo y esos duplicados son parte de lo que hay que ver. Las transferencias siguen siendo una por ítem.
- **Cursor.** Guarda además la fecha del último movimiento. El solapamiento es de 15 minutos de `creado_en` (acotado a 5000 ids), no un número fijo de ids.
- **Poller.** Corre en hilo propio, con tope de 20 s por ciclo. Una sucursal que nunca vendió se sondea una vez por hora.
- **Registro de transferencias.** Va por `JdbcTemplate`, no por JPA, para no compartir el `EntityManager` del request.
- **Sucursal COMPRAS.** Queda fuera del diálogo y del registro de transferencias (decidido por Franco: el control apunta a las transferencias entre sucursales y COMPRAS es la excepción).
