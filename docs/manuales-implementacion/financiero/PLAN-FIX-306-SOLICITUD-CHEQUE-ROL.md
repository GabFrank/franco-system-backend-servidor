# Plan — fix #306: solicitudes de pago y cheques sin control de rol

Issue: GabFrank/franco-system-backend-servidor#306. Rama `fix/tesoreria-lectura-solicitud-cheque` desde `origin/develop`
`6b083cdc` (ya trae #307).

## Decisión (usuario, 2026-09-16)

- **No se crea un rol de compras**: las solicitudes `COMPRA` siguen sin rol.
- **Obligaciones `RRHH`** por `SolicitudPagoGraphQL`: **se leen con rol** (RRHH o tesorería, o ADMIN) y **no se escriben nunca**
  (se gestionan desde su módulo).
- **Solicitudes `GASTO`** por ese resolver: lectura e impresión como hoy; **las mutations de compras las rechazan**.
- **`ChequeGraphQL` y `ChequeraGraphQL`**: queries exigen `requireVer` (cualquier rol de tesorería) y mutations
  `requireGestionar`.

## Análisis (paso 3)

| Hecho | Evidencia |
|---|---|
| `SolicitudPagoGraphQL` no exige rol salvo `devolverSolicitudPago` (`requirePagarCpp`) | `SolicitudPagoGraphQL.java:82-420` |
| `solicitudesPagoPaginated` ya filtra `tipo = COMPRA` | `SolicitudPagoGraphQL.java:109-111` |
| Por id y sin filtro de tipo: `solicitudPago`, `notasAsociadasASolicitud`, `imprimirSolicitudPagoPDF`, `imprimirSolicitudPagoTicket` | idem |
| Mutations por id sin validar tipo: `actualizarSolicitudPago`, `deleteSolicitudPago`, `actualizarEstadoSolicitudPago` (a `SOLICITADO` no pasa por `exigirSolicitudDeCompra`), `agregarNotaASolicitudPago`, `removerNotaDeSolicitudPago`, `agregarSolicitudPagoDetalle`, `eliminarSolicitudPagoDetalle` (por id de detalle) | `SolicitudPagoGraphQL.java:247-400`; `SolicitudPagoService.java:556-560,634-640` |
| `cancelarSolicitudPago` y `devolverSolicitudPago` ya rechazan no-`COMPRA` en el servicio (`exigirSolicitudDeCompra`) | `SolicitudPagoService.java:556-560` |
| `saveSolicitudPago` siempre **crea** una solicitud `COMPRA` (no usa `input.id`) | `SolicitudPagoGraphQL.java:196-240` |
| **La guarda va en el resolver, no en el servicio**: el motor concluye obligaciones RRHH con `solicitudPagoService.actualizarEstado` | `PagoProveedorService.java:571` |
| Ningún cliente pide solicitudes `RRHH` por este resolver. `GASTO`: el dashboard de gastos **imprime** por `imprimirSolicitudPagoPDF`; ninguna mutation de compras se usa desde gastos | relevamiento desktop/mobile/mobile-pwa; `financiero/gastos/graphql/imprimirSolicitudPago.ts` |
| El módulo de gastos no controla rol en el backend (usuarios `ANALISIS_DE_CAJA`): cerrar `GASTO` con rol de tesorería lo rompería | `graphql/financiero/*Gasto*GraphQL.java` sin `require*` |
| Consumidores de `solicitudPago(id)`: diálogo de compras (desktop), mobile, mobile-pwa (`/operaciones/solicitud-pago/:id`, pide `pago { … autorizadoPor { persona { nombre } } }`) — todos sobre `COMPRA` | relevamiento |
| `ChequeGraphQL` sin rol: 6 queries + `saveCheque`/`deleteCheque` (CRUD plano: no mueve saldo, reserva ni chequera). **Sin consumidores** en desktop, mobile, mobile-pwa ni filial | `ChequeGraphQL.java`, `ChequeService.java` |
| `ChequeraGraphQL` sin rol y **vivo**: `gestionar-chequeras-dialog` (abierto desde el dashboard de cheques, gateado `TESORERIA VER/GESTIONAR`), `emitir-cheque-dialog` (`GESTIONAR`) y `pagar-compras-dialog` (`chequerasPorCuenta`, usuarios `CPP PAGAR`) | desktop `cheques-dashboard.component.ts:370`, menú `cheques-dashboard` |
| `requireVer` acepta cualquier rol de tesorería (`TODOS`, incluye `CPP PAGAR`); `requireGestionar` solo `GESTIONAR` (+ ADMIN) | `TesoreriaSecurityService.java:100-109` |
| `ChequeraResolver.cheques(Chequera)` solo se alcanza por las queries de chequera (quedan gateadas) | relevamiento |
| `SolicitudPagoRepository.findTipoById` (proyección, #302) disponible | `SolicitudPagoRepository.java:34` |
| Filial no expone `SolicitudPago` ni `Cheque` | grep filial |
| `PagoResolver.solicitudesPago(Pago)` devuelve todas las solicitudes del evento sin rol. Se alcanza por `pago(id)` (ya `requireVer`, #304) y por `solicitudPago(id) { pago { solicitudesPago } }` de una `COMPRA` **sin rol**. Antes de #302 un pago genérico podía mezclar `COMPRA` y `RRHH`: en datos históricos filtra | `PagoResolver.java:18-20` |
| `solicitudesPagoPorPedido` solo trae solicitudes con notas de un pedido (compras) | `SolicitudPagoRepository.findByPedidoId` |
| `SolicitudPagoRecepcionGraphQL` apunta a `operaciones.solicitud_pago_recepcion`, **dropeada** en `V95.5`: cualquier llamada falla por SQL | `V95.5__drop_obsolete_solicitud_pago_recepcion.sql` |
| Un usuario con solo `ANALISIS DE CAJA` ya no puede usar el diálogo de pagos: la caja (`cajaVirtualSaldos`), `solicitudesPagoPendientes`/`gastosPendientes` exigen `requireVer` y pagar `requirePagarCpp` | `CajaVirtualGraphQL.java:70-72`; `PagoProveedorGraphQL.java:27-59` |

## Cambios

### Fase 1 — central

**`SolicitudPagoGraphQL`** (inyecta además `RrhhSecurityService`; guardas **antes** de los `try` que envuelven errores):

1. `exigirLectura(Long solicitudId)`: `findTipoById`; si es `RRHH` y el usuario no tiene rol de tesorería
   (`TesoreriaSecurityService.TODOS`) ni de RRHH (`RrhhSecurityService.TODOS`) ni es ADMIN → «No autorizado: la solicitud #n
   es una obligación de pago de RRHH.» Id inexistente o sin tipo: sigue el flujo de siempre.
   Se aplica a `solicitudPago`, `notasAsociadasASolicitud`, `imprimirSolicitudPagoPDF`, `imprimirSolicitudPagoTicket`.
2. `exigirSolicitudDeCompra(Long solicitudId)`: `findTipoById`; si no es `null` ni `COMPRA` → error para mostrar «La solicitud
   #n es de TIPO: se gestiona desde su propio módulo, no desde compras.» Se aplica a `actualizarSolicitudPago`,
   `deleteSolicitudPago`, `actualizarEstadoSolicitudPago`, `agregarNotaASolicitudPago`, `removerNotaDeSolicitudPago`,
   `agregarSolicitudPagoDetalle` y `eliminarSolicitudPagoDetalle`. Para el detalle, proyección nueva
   `SolicitudPagoDetalleRepository.findSolicitudIdById` (`select d.solicitudPago.id …`, sin cargar entidades); detalle
   inexistente → la guarda deja pasar y el borrado se comporta como hoy.
3. Rol «tesorería o RRHH» con el mismo idioma que `CajaVirtualGraphQL.cajaVirtualesActivas`
   (`seg.hasAnyRole(TesoreriaSecurityService.TODOS) || rrhhSeg.hasAnyRole(RrhhSecurityService.TODOS)`; ambos con bypass ADMIN).

**`PagoResolver.solicitudesPago`** (auditoría A-4): omite las solicitudes `RRHH` si el usuario no tiene rol de tesorería ni
de RRHH; el resto igual.

**`ChequeGraphQL` y `ChequeraGraphQL`** (inyectan `TesoreriaSecurityService`): `requireVer` en las queries
(`cheque`, `cheques`, `chequesPorChequeraId`, `chequePorPagoDetalleCuotaId`, `chequesSearch`, `countCheque`, `chequera`,
`chequeras`, `chequerasSearch`, `chequerasPorCuenta`, `countChequera`) y `requireGestionar` en las mutations (`saveCheque`,
`deleteCheque`, `saveChequera`, `deleteChequera`).

Sin cambio de schema GraphQL ni migración.

### Tests (Mockito, `@InjectMocks` + `MockitoAnnotations.openMocks` como en #304)

`SolicitudPagoGraphQLTipoTest`:

| Caso | Esperado | Con el código viejo |
|---|---|---|
| `solicitudPago` de una `RRHH` sin roles | rechaza sin cargar la solicitud | la devuelve → **falla** |
| `solicitudPago` de una `RRHH` con rol RRHH / con rol de tesorería | la devuelve | — |
| `solicitudPago` de una `COMPRA` sin roles | la devuelve (sin tocar seguridad) | — |
| `imprimirSolicitudPagoPDF`/`Ticket` y `notasAsociadasASolicitud` de una `RRHH` sin roles | rechaza | **falla** |
| Cada una de las 7 mutations sobre `RRHH` y sobre `GASTO` | rechaza sin llamar al servicio | **falla** |
| Mutations sobre `COMPRA` | delegan como hoy | — |
| `eliminarSolicitudPagoDetalle` de un detalle de una `RRHH` | rechaza | **falla** |
| `eliminarSolicitudPagoDetalle` de un detalle inexistente | delega como hoy | — |
| `solicitudPago` de una `RRHH` como ADMIN sin roles propios (bypass en los mocks de seguridad) | la devuelve | — |
| Id inexistente (`findTipoById` vacío, stub explícito) | sigue el flujo de siempre | — |

`PagoResolverTest`: pago con una `COMPRA` y una `RRHH`; sin roles devuelve solo la `COMPRA` (**falla** con el código viejo);
con rol RRHH o tesorería, las dos.

`ChequeYChequeraGraphQLSeguridadTest`: con `TesoreriaSecurityService` que lanza, las 11 queries y las 4 mutations rechazan
sin llamar al servicio. **Falla** con el código viejo.

Build `./mvnw -o clean verify -B -DskipFlyway=true` leído del log. Revert check: neutralizar las guardas.

### Desktop / mobile / mobile-pwa

**N/A**: ninguna pantalla usa estas operaciones sobre `RRHH`, ni mutations de compras sobre `GASTO`, ni `ChequeGraphQL`. Las
pantallas de chequeras ya se abren con rol de tesorería.

## Prueba de runtime (paso 9) — gate antes del PR

Central local contra `bodega`, sesión del usuario (ADMIN o tesorería). Solicitud `RRHH` #3 (liquidación #488, `CONCLUIDO`):
1. `solicitudPago(3)` con la sesión → la devuelve (rol presente).
2. `actualizarEstadoSolicitudPago(3, CANCELADO)`, `deleteSolicitudPago(3)`, `agregarSolicitudPagoDetalle(3, …)` → rechazan con
   «se gestiona desde su propio módulo»; la #3 sin cambios (psql).
3. Idem con una `GASTO` si existe en la base.
4. `solicitudesPagoPaginated` y `solicitudPago` de una `COMPRA` siguen respondiendo.
5. `chequerasPorCuenta` y `chequesDashboard` responden con la sesión.
6. Sin rol: no hay usuario sin roles con sesión; cubierto por los tests del resolver.

## Datos nuevos

Ninguno.

## Persistencia, replicación, filial

Sin migración. `N/A para filial`: no expone `SolicitudPago` ni `Cheque`.

## Orden de despliegue

Un PR (central). `deploy-auto.yml` no se dispara: deploy manual del workflow «Deploy». Compatible con cualquier desktop.

## Riesgos conocidos

- Un usuario con solo `TESORERIA VER` deja de poder crear/borrar chequeras (queda para `GESTIONAR`), coherente con el módulo.
- Lectura de solicitudes `COMPRA` y `GASTO` sigue sin rol (decisión: sin rol de compras; gastos sin rol en todo su módulo).
- `SolicitudPagoRecepcionGraphQL` (sin rol) queda fuera: su tabla no existe desde `V95.5` (código muerto, borrar en otro PR).
- Un rechazo nuevo en el desktop se ve solo si la pantalla muestra `graphQLErrors`; ningún flujo legítimo lo dispara
  (verificar en runtime cómo aparece).
- Menú desktop: `compras-dashboard` y `list-solicitud-pago` abren el tab sin `openTabIfAuthorized` (defensa en profundidad del
  cliente; la seguridad real es el backend). Fuera de alcance.

## Auditoría del plan (paso 5)

| # | Eje | Hallazgo | Verificación | Qué se hizo |
|---|---|---|---|---|
| A-1 | A · alta | Un usuario con solo `ANALISIS DE CAJA` llega al diálogo de pagos y `requireVer` en `chequerasPorCuenta` le rompería el flujo | **Descartado**: ese usuario ya recibe «No autorizado» en `cajaVirtualSaldos`, `solicitudesPagoPendientes`, `gastosPendientes` y no puede pagar (`requirePagarCpp`) | Anotado en el análisis |
| A-2 | A | `ChequeGraphQL`: wrappers en `cheque.service.ts` sin ningún llamador | Confirmado | Sin cambio |
| A-3 | A | Gastos no usa mutations de `SolicitudPago` (dominio `Gasto` aparte); RRHH y mobile/pwa no leen `SolicitudPago` RRHH ni cheques | Confirmado | Sin cambio |
| A-4 | A · alta | `PagoResolver.solicitudesPago` sin rol; alcanzable desde una `COMPRA` sin rol | **Confirmado** (datos previos a #302 pueden mezclar tipos) | **Aplicado**: omite `RRHH` sin rol + test |
| A-5 | A | `SolicitudPagoRecepcionGraphQL`: tabla dropeada en `V95.5` | Confirmado | Anotado (fuera de alcance) |
| A-6 | A · baja | `solicitudesPagoPorPedido` no listada | Solo compras (por pedido) | Anotado en el análisis |
| B-1 | B | Guardas antes de los `try` y con `GraphQLException`/`errorParaMostrar` para que el mensaje llegue limpio | `GraphqlExceptionHandler.java:18-42` | Confirma el plan |
| B-2 | B | `findTipoById` es proyección; sin transacción en el resolver | Confirmado | Sin cambio |
| B-3 | B | Doble `hasAnyRole` ya tiene precedente (`cajaVirtualesActivas`) | Confirmado | **Aplicado**: mismo idioma |
| B-4 | B · media | `eliminarSolicitudPagoDetalle`: sin mecanismo definido ni caso de id inexistente | Correcto | **Aplicado**: proyección `findSolicitudIdById` + test |
| B-5 | B | Ningún flujo de RRHH/gastos usa estas mutations por el resolver (van por el servicio) | Confirmado | Sin cambio |
| B-6 | B · baja | «Mockito devuelve `null` para `Optional`» | **Incorrecto**: Mockito 4 devuelve `Optional.empty()`; igual se stubbea explícito | Stub explícito en los tests |
| B-7 | B | Faltaban casos: ADMIN, detalle inexistente | Correcto | **Aplicado** |
| B-8 | B · media | El desktop puede mostrar el rechazo solo en consola | Ningún flujo legítimo lo dispara | Verificar en runtime |
