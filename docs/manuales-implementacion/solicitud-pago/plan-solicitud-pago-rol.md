# Plan — Solicitudes de pago de compras con rol (central)

**Objetivo.** Que las operaciones de solicitudes de pago de **compra** de
`SolicitudPagoGraphQL` pidan en el resolver los mismos roles que el desktop y
la PWA (GabFrank/frc-mobile-pwa#88), como la lectura de las de RRHH
(`exigirLectura`, #306) y «Devolver a compras» (`requirePagarCpp`).

**Decisión (Franco, 2026-10-19).** Los mismos roles que el menú del desktop
(`side-mini-variant.component.ts:170-172`) y la PWA: `ANALISIS DE CAJA`,
`ANALISIS FINANCIERO`, `ANALISIS CONTABLE`, `VER PRECIO COSTO`,
`RECIBIR PEDIDOS`, `TESORERIA VER`, `TESORERIA GESTIONAR` (ADMIN pasa por el
bypass de `TesoreriaSecurityService.hasAnyRole`).

## Fase 1 (única) — rama `fix/solicitud-pago-rol`

En `SolicitudPagoGraphQL`:

1. Constante `ROLES_COMPRAS` (visible para el test) y `exigirRolCompras()`: si
   `tesoreriaSecurityService.hasAnyRole(ROLES_COMPRAS)` es falso, lanza
   `errorParaMostrar(new IllegalStateException(...))` con un mensaje corto.
   `GraphQLException` pelada llega al desktop como «Exception while fetching
   data…» (`GraphqlExceptionHandler` solo desanida `GraphQLError`). Va **fuera**
   de cualquier `try` que la envuelva en `RuntimeException`.
2. **Antes** de `exigirSolicitudDeCompra`: sin rol no se puede usar el id para
   averiguar si es RRHH o GASTO. Primera línea de las operaciones que solo son de compras:
   - Queries: `solicitudesPagoPorPedido`, `solicitudesPagoPaginated`,
     `notasDisponiblesParaPago`, `isNotaIncludedInSolicitud`,
     `notaRecepcionDisponibleParaPagoPorNumero`,
     `notasDisponiblesParaPagoPorProveedor` (+ `Paginated`),
     `datosInicialesSolicitudPagoPorRecepcion`.
   - Mutations: `saveSolicitudPago`, `actualizarSolicitudPago`,
     `deleteSolicitudPago`, `actualizarEstadoSolicitudPago`,
     `cancelarSolicitudPago`, `agregarNotaASolicitudPago`,
     `removerNotaDeSolicitudPago`, `eliminarSolicitudPagoDetalle`,
     `agregarSolicitudPagoDetalle`.
3. Lecturas por id (`solicitudPago`, `notasAsociadasASolicitud`,
   `imprimirSolicitudPagoPDF`, `imprimirSolicitudPagoTicket`): dentro de
   `exigirLectura`, **solo si el tipo es `COMPRA`** se exige `exigirRolCompras()`.
   RRHH sigue con su regla (#306) y GASTO sigue abierto: lo imprime caja chica /
   gastos (`gasto.service.ts:211`) y no es parte de este pedido.
4. `devolverSolicitudPago` no cambia: `requirePagarCpp()`.

Sin cambios en `.graphqls`, enums, migraciones ni datos.

**Tabla de datos nuevos:** ninguno.

**Tests** (`SolicitudPagoGraphQLTipoTest` + uno nuevo si hace falta):
- sin rol → cada query y mutation de compras rechaza con el mensaje nuevo y no
  llega al servicio;
- con rol (`hasAnyRole(ROLES_COMPRAS)` = true) → pasan;
- GASTO por id se sigue leyendo sin rol; RRHH sin cambios;
- `setUp()` stubea `hasAnyRole(ROLES_COMPRAS)=true` por defecto, así los tests
  de tipo (#306) siguen probando lo suyo; los de «sin rol» lo ponen en false;
- ajustar `compras_y_gastos_se_leen_sin_rol` (deja de ser cierto para compras);
- cubrir también `cancelarSolicitudPago` y las queries de lista;
- actualizar los javadoc de `cancelarSolicitudPago` y `exigirLectura`.
- Revertir el gate y ver que los tests nuevos fallan.

**Prueba por pantalla.** Central local + desktop web: con `MR` (todos los
roles) la lista, el alta y el PDF funcionan; con un usuario sin esos roles la
mutation y la query rechazan. PWA contra el mismo central: con rol funciona.

## Quién queda afuera / riesgo

- Llamadores verificados: desktop (menú «Compras» y «Solicitud de pago» ya
  piden estos roles; `list-solicitud-pago` no tiene otra puerta), PWA (#88),
  procesos internos (RRHH, gastos, Pagar Compras llaman a `SolicitudPagoService`,
  no al resolver). Ningún Java interno llama al resolver.
- **APK vieja (`frc-mobile`)**: un usuario sin rol que todavía la use va a ver
  error al crear solicitudes. En bodega, los 7 usuarios que crearon las 1.663
  solicitudes de los últimos 90 días tienen rol (consulta del 2026-10-19).
  Farmacia no se revisó.
- Un usuario con solo `TESORERIA CPP PAGAR` / `CPC COBRAR` no entra a la lista
  de compras (tampoco la ve en el desktop). Pagar Compras usa
  `PagoProveedorGraphQL`, que no cambia.

- Otros tests que usan `@InjectMocks` sobre el resolver y van a necesitar el
  stub: `SolicitudPagoGraphQLEstadoTest`, `RolTesoreriaCajaMatrizTest`.
- Orden de despliegue: ninguno. Desktop y PWA (#88) ya piden los mismos roles.

**Reversión.** Revertir el PR y desplegar. Sin migración.
