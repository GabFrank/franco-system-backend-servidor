# Sorteos para clientes — diseño

Fecha: 2026-10-28 · Repos: central (`franco-system-backend-servidor`), filial (`franco-system-backend-filial`), desktop (`frc-sistemas-integrados-angular`) · Rama: `feature/sorteos-clientes` en los tres.

## Intención

Hoy el sorteo es manual: el cliente tira su ticket de venta en una urna. Se quiere que el sistema lo maneje:

- Desde el central se configuran los sorteos: monto mínimo de compra, fechas de vigencia (desde/hasta, como las promos), sucursales donde aplica y premios que se sortean.
- La configuración se replica a las filiales.
- Cuando una venta llega al mínimo, el POS le avisa al cajero y se imprime un **ticket de participación** con los datos del cliente para que lo firme y lo deposite en la urna.

La urna física se mantiene: el ganador se sigue sacando a mano.

## Objetivos

1. ABM de sorteos en el central, replicado a todas las filiales (MAIN_TO_ALL).
2. Un sorteo aplica a todas las sucursales o a una lista elegida.
3. Cantidad de tickets por venta configurable (ver "Modos").
4. En el POS: si la venta califica, se identifica al cliente y se imprimen los tickets de participación con sus datos y un espacio para la firma.
5. Cada participación queda registrada con un código único y sube al central (BRANCH_TO_MAIN). Así se puede verificar un ticket ganador y ver cuántas participaciones hubo por sucursal. *(Decisión de Claude: el usuario no se pronunció sobre la mejora opcional; se incluye solo el registro y su listado.)*

## No-objetivos (esta versión)

- Sorteo automático por sistema (botón "elegir ganador"). El registro del punto 5 lo deja preparado.
- Avisos al cliente por WhatsApp o correo.
- Ventas **delivery**: no participan, porque el cliente no está para firmar. *(Decisión de Claude.)*

## Anti-objetivos

- **Que el sorteo rompa o frene una venta.** Si falla algo del sorteo (lectura de config, registro, impresión), la venta se guarda igual. Mismo criterio que hoy con la impresión y con `PrecioEspecialLector`.
- **Que la regla viva en el frontend.** La decisión de si la venta participa y cuántos tickets le tocan la toma el backend de la filial. El desktop solo pregunta y muestra.
- **Cambiar pantallas conocidas del POS más de lo necesario.** El flujo de cobro queda igual. Lo único nuevo es un diálogo que aparece solo si la venta califica.

## Configuración de un sorteo (central)

Campos:

| Campo | Notas |
|---|---|
| nombre | obligatorio, sale en el ticket |
| descripcion | opcional, texto corto para el ticket (bases, condiciones) |
| monto_minimo | NUMERIC > 0, en guaraníes, contra el **monto computable** de la venta (ver abajo) |
| incluye_tarjeta | boolean, default true. Si es false, lo pagado con tarjeta no cuenta para el sorteo (pedido del usuario) |
| modo_participacion | `UNA_POR_VENTA` · `POR_MULTIPLO` · `CANTIDAD_FIJA` |
| cantidad_fija | solo `CANTIDAD_FIJA`: tickets por venta que califica (≥ 1) |
| tope_por_venta | opcional, solo `POR_MULTIPLO`: máximo de tickets por venta |
| fecha_desde / fecha_hasta | DATE, días inclusivos en zona -03. `desde` vacío = hoy, `hasta` vacío = sin fin, como en precio especial. CHECK hasta ≥ desde |
| fecha_sorteo | DATE opcional, día del sorteo, se imprime en el ticket |
| todas_sucursales | boolean. Si es true aplica también a sucursales nuevas |
| sucursales | si `todas_sucursales` es false, lista de sucursales (tabla aparte, nunca la 0) |
| premios | lista ordenada: descripción libre (ej. "Moto 110cc") y/o producto del sistema, más cantidad |
| activo | false = cortado antes de tiempo (acción "Cortar", como en precio especial) |
| usuario_id, creado_en | auditoría, como en el resto |

### Monto computable (decidido por el usuario)

`total` = total de los productos en Gs (`venta.total_gs`) **menos los descuentos**, **sin sumar recargos** (por ejemplo el `aumento` de tarjeta), para que pagar con tarjeta no le dé a nadie un ticket de más.

Si el sorteo tiene `incluye_tarjeta = false`, además se resta lo pagado con tarjeta. Así una venta pagada toda con tarjeta no participa y, en un pago mixto, cuenta solo la parte que no es tarjeta. *(Decisión de Claude sobre cómo tratar el pago mixto.)* Se considera tarjeta con el mismo criterio que usa la filial para registrar `venta_tarjeta` (forma de pago tarjeta/POS/QR); el builder confirma cuál es.

La fórmula vive en **un solo servicio de la filial**, que usan tanto la pre-consulta como `saveVenta`. El desktop no calcula nada: le manda a la pre-consulta los datos crudos del cobro (total de productos, descuentos, lista de pagos con su forma de pago) y la filial aplica la fórmula. Así el cobro normal y el cobro rápido dan lo mismo.

### Modos de participación (pedido del usuario: configurable)

Con `total` = monto computable y `minimo` = monto mínimo:

- `UNA_POR_VENTA`: 1 ticket si total ≥ minimo.
- `POR_MULTIPLO`: `floor(total / minimo)` tickets, limitado por `tope_por_venta` si está cargado. Ejemplo: mínimo 100.000 y compra de 250.000 → 2 tickets.
- `CANTIDAD_FIJA`: `cantidad_fija` tickets si total ≥ minimo.

Si hay varios sorteos vigentes para la sucursal, cada uno se evalúa por separado y la venta puede participar en más de uno.

### Reglas de edición *(decisión de Claude)*

- Los cambios de nombre, descripción, premios, fecha_sorteo y fecha_hasta se pueden hacer siempre.
- Monto mínimo, modo y sucursales se pueden editar solo mientras el sorteo no tenga participaciones. Después hay que cortarlo y crear uno nuevo, para no cambiar las reglas a mitad de camino.
- No se permiten dos sorteos con el mismo nombre vigentes al mismo tiempo.

## Tablas

Tablas nuevas en el central, con espejo en la filial. Schema sugerido: `operaciones`; queda a criterio del builder si encaja mejor otro.

1. `sorteo`: MAIN_TO_ALL. Campos de la tabla de arriba.
2. `sorteo_sucursal` (sorteo_id, sucursal_id): MAIN_TO_ALL.
3. `sorteo_premio` (sorteo_id, orden, descripcion, producto_id nullable, cantidad): MAIN_TO_ALL.
4. `sorteo_participacion`: BRANCH_TO_MAIN, la crea la filial.
   - PK compuesta (id, sucursal_id), con el mismo patrón que `financiero.venta_tarjeta` (`@IdClass(EmbebedPrimaryKey)` + secuencia).
   - Una fila por venta y por sorteo.
   - Campos: sorteo_id, venta_id, sucursal_id, cantidad (tickets), cliente_id nullable, documento, nombre, codigo (texto único, ej. `S{sorteo}-{sucursal}-{venta}`; cada ticket impreso agrega `-1/3`, `-2/3`…), creado_en, usuario_id. **Sin columna de estado** (ver "Anulación").
   - `replicate_central_to_branch_with_filter`: false; no hace falta que vuelva a bajar.

Migraciones:

- Central: `V260.3__sorteos_clientes.sql`. Crea las 4 tablas con CHECKs y FKs y las registra en `configuraciones.replication_table`, como en V232.1 y V150.1.
- Filial: `V109.3__espejo_sorteos_clientes.sql`. Espejo de las tablas MAIN_TO_ALL: mismas columnas, todo nullable, sin FK ni CHECK. Además crea la tabla propia `sorteo_participacion`, como las otras tablas BRANCH_TO_MAIN de la filial.
- Los números se verificaron hoy contra todas las ramas remotas: el central llega hasta 259.3 y la filial hasta 108.5. Se vuelven a verificar antes del PR.

**Orden de despliegue (obligatorio):**

1. La migración espejo de la filial, desplegada en todas las filiales.
2. Después, el central. Si el central publica la tabla antes de que la filial la tenga, se corta TODA la replicación entrante de esa filial.
3. Por último, el desktop.

Recordar `copy_data=false`: los sorteos creados antes del REFRESH de las suscripciones no bajan. Hay que crear los sorteos reales después de verificar `srsubstate='r'`.

## Filial: evaluar y registrar

- **`SorteoLector`**: copia el patrón de `PrecioEspecialFuente`/`PrecioEspecialLector`. Lee los sorteos activos y vigentes hoy (-03) para `env sucursalId`, ya sea porque tienen `todas_sucursales` o una fila en `sorteo_sucursal`. Lee por JDBC con `@Transactional(NOT_SUPPORTED, readOnly)` para no dejar `saveVenta` en rollback-only. Ante cualquier excepción devuelve "sin sorteos" y loguea.
- **Kill switch**: property `sorteo.habilitado` (default true).
- **Query `sorteosAplicables(input: SorteoEvaluacionInput!): [SorteoAplicable]`**: recibe los datos crudos del cobro (ver "Monto computable") y devuelve, por cada sorteo que califica, `{sorteoId, nombre, cantidadTickets, premios}`. El desktop la usa antes de guardar para saber si tiene que pedir el cliente.
- **En `saveVenta`** (el modo FRONTEND usa la misma mutation con `imprimirEnCliente`): un argumento nuevo y opcional, `sorteoCliente: SorteoClienteInput {clienteId, documento, nombre}`. Si viene null no se crea ninguna participación: es el caso de "No participa", y también el de un desktop viejo que no conoce los sorteos.
  - Después de guardar la venta, la filial **vuelve a evaluar** con el total real, que es el que vale.
  - Por cada sorteo que califica, **primero** crea la `sorteo_participacion` y **después** agrega los N tickets al final del stream de impresión, después del comprobante normal, por los dos caminos (impresora del filial y `ticketEscpos` para modo FRONTEND).
  - En las rutas que no imprimen comprobante (`FACTURA_SILENCIOSA`, `SIN_FACTURA` con factura manual), los tickets del sorteo salen solos. *(Decisión de Claude.)*
  - El registro va en una transacción propia (REQUIRES_NEW), así un error de SQL no aborta la transacción de la venta.
  - Todo va en try/catch y nunca tumba la venta.
- **Respuesta**: la `Venta` devuelta trae un campo transient nuevo, `sorteoTickets: [SorteoTicketInfo]` (`{nombreSorteo, cantidad}`), para que el desktop muestre el aviso.
- **Anulación** *(decisión de Claude, corregida tras la revisión)*: la filial no cancela ventas (`VentaService.cancelarVenta` de la filial siempre tira error; se cancela en el central por `cancelarVenta`, `cancelarFacturaLegal(cancelarVenta=true)` y `cancelarVentaCredito`, y `cancelarVenta` además reactiva). Por eso el estado **no se guarda**: el central lo calcula al listar o validar un código. Si la venta (`venta_id`, `sucursal_id`) está cancelada, la participación es ANULADA; si no, ACTIVA. Cubre los tres caminos y la reactivación sin tocar ningún flujo de cancelación.

## Reimpresión (pedido del usuario: entra en esta versión)

- Valor nuevo `SORTEO` en el enum `TicketEscposTipo` de la filial, con su case en `TicketEscposGraphQL` (id = venta). Reimprime todos los tickets de las participaciones de esa venta con la leyenda "REIMPRESIÓN" y el mismo código, así que no crea participaciones nuevas.
- Funciona por los dos caminos: impresora del filial y `ticketEscpos` en modo FRONTEND.
- En el desktop: botón "Reimprimir ticket de sorteo" en el mismo lugar del POS donde hoy se reimprime el ticket de una venta, visible solo si la venta tiene participaciones. Ese lugar es el diálogo "Últimas ventas" (`ultimas-ventas-dialog`), al lado del botón "Reimprimir venta".
- Si se agrega el valor al enum de Java, va también al `.graphqls`.

## Ticket de participación (58 mm, ESC/POS)

Basado en `ImpresionService.printSenaCupon`. Un ticket por participación individual, con corte entre tickets. Contenido:

- Logo
- "SORTEO" + nombre del sorteo
- Premios (lista)
- Vigencia y fecha del sorteo, si está cargada
- Sucursal, N° de venta, fecha y hora, total de la venta
- Código único + "Ticket i de N"
- Nombre y CI/RUC del cliente (impresos)
- `Teléfono: ____________`
- `Firma: ______________`
- Pie: "Deposite este ticket firmado en la urna"

## Desktop

### Central: pantalla "Sorteos"

En el menú, al lado de "Promociones", solo en modo servidor, con el patrón de `list-precio-especial` y `GenericCrudService`.

- **Listado** con estado (Programado / Vigente / Finalizado / Cortado), filtro por texto y por "solo vigentes", y acciones Nuevo / Editar / Cortar / Ver participaciones.
- **Diálogo de alta/edición**:
  - nombre y descripción;
  - monto mínimo;
  - modo con sus campos condicionales y una línea de ayuda ("Compra de 250.000 → 2 tickets");
  - desde, hasta y fecha del sorteo, con datepicker;
  - sucursales: checkbox "Todas", si no multi-select como en precio especial;
  - premios: tabla con agregar/quitar; cada fila lleva descripción, producto opcional (buscador de productos existente) y cantidad.
- **Participaciones de un sorteo**: tabla paginada (código, sucursal, venta, cliente, documento, cantidad, estado, fecha), filtro por sucursal y búsqueda por código, que es lo que se usa para validar el ticket ganador, y totales de tickets por sucursal.
- **Permiso**: la regla es que solo entren los roles que hoy gestionan precios/promociones (`CREAR_PRECIOS`/`EDITAR_PRECIOS`), salvo que ya exista un mecanismo simple para un rol nuevo; la decisión queda para el builder.

### POS (venta-touch)

1. Al finalizar el cobro (`PagoTouch.onFinalizar` y también el cobro rápido `onTicketClick`), antes de guardar, el desktop consulta `sorteosAplicables(total)`. Si viene vacío o falla, el flujo sigue igual que hoy.
2. Si califica:
   - **Con factura legal o venta a crédito** (el cliente ya está elegido): no se pregunta nada y se manda ese cliente.
   - **Sin cliente**: se abre el diálogo "Sorteo". Muestra el texto "Esta compra participa del sorteo *X* (N tickets)", un campo RUC/CI y el botón Buscar.
     - Buscar primero busca el cliente por documento en la base y, si no está, consulta la SET (lógica existente de `ClienteGraphQL`, `consultaRuc` / cliente por documento).
     - Si no aparece en ninguna, el cajero **escribe el nombre a mano**.
     - Botones: Confirmar y "No participa" (se manda `sorteoCliente` null).
3. Se guarda la venta con `sorteoCliente`.
4. Con la respuesta, si `sorteoTickets` no está vacío, el desktop muestra un aviso: "Se imprimieron N tickets del sorteo X. El cliente debe firmarlos y depositarlos en la urna." Este aviso va en el bloque post-guardado de `venta-touch` (~línea 1900), dentro del try/catch existente.
5. Delivery no pasa por nada de esto.

## Restricciones conocidas

- La filial no compila contra el central: la query y el input viven en el schema GraphQL de la filial.
- Migraciones siempre con sufijo `.3` (regla del usuario, gana sobre los CLAUDE.md de los repos).
- `./mvnw` del central borra `target/`: hay que compilar y testear en un worktree aparte. El código fuente vive en el checkout principal, en `feature/sorteos-clientes`.
- Karma no corre en el desktop; para verificar se usa `npm run check`.
- No hay que levantar servidores (los levanta el usuario).
- No se abre PR hasta que el usuario lo pida.

## Queda a criterio del builder

- Schema exacto y nombres de clases y archivos.
- Si `sorteosAplicables` y la evaluación en `saveVenta` comparten un mismo servicio (deberían).
- Formato visual del ticket dentro de lo listado.
- Tests: unitarios del cálculo de tickets por modo y de la vigencia (como hay para `PrecioEspecialLector`) y del servicio del central (validaciones).
