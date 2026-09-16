# Plan — fix #304: `savePago` sin rol permite cancelar un pago sin revertir la caja

Issue: GabFrank/franco-system-backend-servidor#304. Rama `fix/tesoreria-save-pago-rol` desde `origin/develop` `88201ad9`
(ya trae #305).

## Decisión (usuario, 2026-09-16)

- `savePago`: **exige `TESORERIA GESTIONAR` y limita los estados** (no se deshabilita ni se quita del schema).
- Las mutations hermanas de la pantalla vieja (`savePagoDetalle`, `deletePagoDetalle`, `updatePagoDetalleCajaySucursal`,
  `savePagoDetalleCuota`, `deletePagoDetalleCuota`) **exigen el mismo rol en este PR**.
- **Desktop sin cambios**: la pantalla vieja «Pagos» queda como está (código muerto).

## Análisis (paso 3)

| Hecho | Evidencia |
|---|---|
| `savePago` no controla rol; mapea el input entero (`estado`, `usuarioId`, `autorizadoPorId`, `creadoEn`) y guarda | `PagoGraphQL.java:32-46` |
| `PagoService.save` solo fuerza `ABIERTO` + `creadoEn` en el alta; en una edición guarda lo que venga | `PagoService.java:23-29` |
| La anulación real es `anularPagoCpp`: revierte caja/banco, anula detalles, reabre solicitudes. Rechaza un pago ya `CANCELADO` | `PagoProveedorService.java:622-625` |
| El motor deja cada `Pago` en `CONCLUIDO` (o `CANCELADO` al anular); el `ABIERTO` del alta no sale de su transacción | `PagoProveedorService.procesarEvento` |
| **La pantalla vieja es inalcanzable**: el `case "list-pagos"` del menú real está comentado y `ListPagoComponent`/`EditPagoComponent` no se abren desde ningún lado (solo declarados). El issue citaba el `side.component` huérfano | desktop `side-mini-variant.component.ts:986-988`; grep |
| La pantalla vieja solo usaba `ABIERTO` (alta, reabrir), `PENDIENTE` (finalizar) y `programado` | desktop `edit-pago.component.ts:473,816,873,907` |
| Mutations hermanas sin rol; tocan `operaciones.pago_detalle` / `pago_detalle_cuota` (tablas de la pantalla vieja), **sin mover plata** | `PagoDetalleGraphQL.java`, `PagoDetalleCuotaGraphQL.java`; `PagoDetalleService`/`PagoDetalleCuotaService` sin tesorería |
| Base local `bodega`: 5 `Pago` (2 `CONCLUIDO`, 3 `CANCELADO`, todos del motor); `pago_detalle` y `pago_detalle_cuota` vacías | psql |
| `solicitudesPago` es el lado inverso (`mappedBy`): guardar un `Pago` no toca las solicitudes | `Pago.java` |
| mobile-pwa no tiene nada de `pago`. `frc-mobile` tiene `pago.service.ts` / `pago-detalle.service.ts` con `savePago` / `savePagoDetalle`, **sin ningún importador** (código muerto) | grep |
| El motor guarda el `Pago` con `pagoService.save` directo en Java, **nunca por el resolver**: la guarda nueva no lo afecta | `PagoProveedorService.java:436,594,680` |
| `pago(id).solicitudesPago` trae las solicitudes del evento (el motor hace `sp.setPago(pago)`), **de compras y de RRHH** | `PagoProveedorService.java:563` |
| `programado` no lo lee ningún proceso ni reporte (dato inerte de la pantalla vieja) | grep |

## Cambios

### Fase 1 — central

1. **`PagoService.guardarManual(...)`** (nuevo, `@Transactional`), que usa `savePago`:
   - Estados asignables a mano: **`ABIERTO` y `PENDIENTE`**. Cualquier otro (`PARCIAL`, `CONCLUIDO`, `CANCELADO`) → «El
     estado X no se asigna a mano: los pagos se concluyen y se anulan desde la caja.»
   - **Alta** (`id == null`): igual que hoy (`save` fuerza `ABIERTO` y `creadoEn`).
   - **Edición**: carga el `Pago` de la base; si no existe → «Pago no encontrado». Si su estado **no** es `ABIERTO` ni
     `PENDIENTE` → «El pago #n está X: se gestiona desde la caja (para revertirlo, anular desde su movimiento).» Si pasa,
     aplica **sobre la entidad cargada** solo `estado` (si viene), `programado` y `autorizadoPor`; **`usuario` y
     `creadoEn` se conservan** de la base (no se reescriben desde el input).
2. **`PagoGraphQL.savePago`**: `seg.requireGestionar()` primero; arma los datos y delega en `guardarManual`.
3. **Hermanas**: `seg.requireGestionar()` al inicio de `savePagoDetalle`, `deletePagoDetalle`,
   `updatePagoDetalleCajaySucursal`, `savePagoDetalleCuota`, `deletePagoDetalleCuota`.
4. **Queries de los tres resolvers** (`pago`, `pagoDetalle`, `pagoDetallesPorPagoId`, `pagoDetalleCuota`,
   `pagoDetalleCuotas`, `pagoDetalleCuotasPorPagoDetalleId`, `pagoDetalleCuotasSearch`, `countPagoDetalleCuota`,
   `getPagoDetalleCuotasFiltrado`): `seg.requireVer()` (auditoría A-5; propuesta a confirmar por el usuario).

**Sin lock sobre el `Pago`** (auditoría B-4): los únicos editables son `ABIERTO`/`PENDIENTE`, que el motor nunca deja fuera de
su transacción; son de la pantalla vieja y no tienen movimientos, así que una carrera con `anularPagoCpp` no mueve
plata. Los del motor se rechazan por estado.

Sin cambio de schema GraphQL ni migración.

### Tests (Mockito)

`PagoServiceGuardarManualTest`:

| Caso | Esperado | Con el código viejo |
|---|---|---|
| Edición de un `Pago` `CONCLUIDO` con estado `CANCELADO` | rechaza, no guarda | lo cancela → **falla** |
| Edición de un `Pago` `CONCLUIDO` con estado `ABIERTO` (reabrir por la puerta de atrás) | rechaza | lo reabre → **falla** |
| Alta con estado `CONCLUIDO` | rechaza | guarda `ABIERTO` (el alta lo pisaba) |
| Edición de un `Pago` `ABIERTO` a `PENDIENTE` con otro `usuario`/`creadoEn` en el input | guarda `PENDIENTE`; `usuario` y `creadoEn` quedan los de la base | pisa usuario y fecha → **falla** |
| Edición de un id inexistente | «Pago no encontrado» | — |
| Alta con `ABIERTO` | sigue funcionando | — |

`PagoLegacyGraphQLSeguridadTest`: los tres resolvers usan `@Autowired` de campo, así que se instancian con `@InjectMocks`
(Mockito inyecta por campo; sin refactor de producción). Con `TesoreriaSecurityService` mockeado que lanza, `savePago`, las
cinco hermanas y las queries rechazan **sin llamar al servicio**. Con el código viejo **fallan**.

Build: `./mvnw -o clean verify -B -DskipFlyway=true` leído del log. Revert check: neutralizar guarda y validaciones y
correr los tests nuevos esperando los fallos marcados.

### Implementación (fase 1) — resultado de los tests

- `PagoService.guardarManual(id, estado, programado, usuario, autorizadoPor)`; `savePago` exige `GESTIONAR` y delega. Las 5
  mutations hermanas exigen `GESTIONAR`; las 9 queries de los tres resolvers, `VER` (usuario confirmó incluirlas).
- `PagoServiceGuardarManualTest` 7/7 y `PagoLegacyGraphQLSeguridadTest` 3/3 (`@InjectMocks` + `MockitoAnnotations.openMocks`).
- **Con guardas y validaciones neutralizadas**: fallan los 6 casos esperados (4 rechazos del servicio, mutations y queries
  sin rol). Siguen pasando alta, id inexistente y conservación de usuario/fecha (no dependen de lo neutralizado).
- `./mvnw -o clean verify -B -DskipFlyway=true` → 669/669, BUILD SUCCESS.

### Desktop

**N/A** (decisión): la pantalla vieja no es alcanzable; si se reactivara, su flujo (`ABIERTO`/`PENDIENTE`/`programado`)
sigue permitido con `TESORERIA GESTIONAR`.

## Prueba de runtime (paso 9) — gate antes del PR

Central local contra `bodega`, mutations desde la página con la sesión del usuario (tiene rol de tesorería / ADMIN):
1. `savePago` con el id de un pago `CONCLUIDO` y `estado: CANCELADO` → rechaza; el pago sigue `CONCLUIDO` (psql).
2. Idem con un pago `CANCELADO` y `estado: ABIERTO` → rechaza.
3. `savePago` alta con `ABIERTO` → crea; después `PENDIENTE` sobre ese id → pasa y conserva `usuario`/`creado_en`. Se
   borra la fila de prueba al final (base local).
4. Sin rol: no hay usuario sin rol de tesorería con sesión; queda cubierto por el test del resolver.

### Resultado (2026-09-16, `451a8cee`, central local contra `bodega`, sesión del usuario)

| Paso | Resultado |
|---|---|
| `savePago` #6 (`CONCLUIDO`) → `CANCELADO` | **rechaza**: «El estado CANCELADO no se asigna a mano: los pagos se concluyen y se anulan desde la caja.» |
| `savePago` #6 → `ABIERTO` | **rechaza**: «El pago #6 está CONCLUIDO: se gestiona desde la caja (para revertirlo, anular desde su movimiento).» |
| `savePago` #5 (`CANCELADO`) → `ABIERTO` | **rechaza** (mismo mensaje con CANCELADO) |
| Alta con `CONCLUIDO` | **rechaza** |
| Alta con `ABIERTO` | crea #7 `ABIERTO` |
| #7 → `PENDIENTE`, `programado`, con `usuarioId: 1` y `creadoEn: 2020-01-01` en el input | `PENDIENTE`, `programado = true`; **conserva** usuario 410 y la fecha real |
| `pago(6)` con rol | responde |
| Base | pagos #2–6 sin cambios; máximo de `movimiento_caja_virtual` sigue en 26; #7 sin detalles, borrado al final |
| Log del central | solo los 4 rechazos esperados (más Firebase, preexistente) |

## Datos nuevos

Ninguno.

## Persistencia, replicación, filial

Sin migración. `N/A para filial`: no tiene entidad `Pago` y `operaciones.pago` no se replica (auditoría A-4).

## Orden de despliegue

Un PR (central). `deploy-auto.yml` no se dispara: deploy manual del workflow «Deploy». Compatible con cualquier desktop.

## Riesgos conocidos

- Un usuario sin `TESORERIA GESTIONAR` que usara la pantalla vieja queda bloqueado: la pantalla es inalcanzable desde el
  menú.
- Con `requireVer` en las queries, un usuario sin rol de tesorería no ve `Pago` por id; nadie las usa (pantallas muertas).
- Un desktop con specs e2e viejos que matcheen `savePago` no se ve afectado: el CI del desktop no corre specs.

## Auditoría del plan (paso 5)

| # | Eje | Hallazgo | Verificación | Qué se hizo |
|---|---|---|---|---|
| A-1 | A | El motor guarda `Pago` por Java, no por el resolver | `PagoProveedorService.java:436,594,680` | Anotado en el análisis |
| A-2 | A | Pantalla vieja inalcanzable; el `case` comentado ni siquiera abría `ListPagoComponent` | `side-mini-variant.component.ts:986-988`; grep | Confirma |
| A-3 | A · media | `frc-mobile` **sí** tiene servicios `savePago`/`savePagoDetalle` | Correcto, pero sin importadores (código muerto) | **Corregido** el análisis |
| A-4 | A | Filial sin `Pago`; `operaciones.pago` no se replica; `SolicitudPagoGraphQL` ya bloquea estados a mano | grep | Confirma `N/A para filial` |
| A-5 | A · media | Queries sin rol exponen `solicitudesPago`; el auditor dijo «solo compras» | **Parcial**: el motor setea `sp.pago` también en solicitudes RRHH | **Propuesto**: `requireVer` en las queries de los tres resolvers |
| A-6 | A | `requireGestionar` coherente con mutations administrativas del módulo | `ChequePosGraphQL`, `MaletinTesoreriaGraphQL` | Sin cambio |
| B-1/B-2 | B | Aplicar sobre la entidad cargada no pierde campos; `CrudService.save` sin efectos ocultos | `PagoInput`, `CrudService`, `AssignedIdentityGenerator` | Confirma |
| B-3 | B | Ningún `Pago` del motor queda `ABIERTO`/`PENDIENTE` fuera de su transacción | grep `PagoEstado.ABIERTO/PENDIENTE` | Confirma |
| B-4 | B · media | `guardarManual` y `anularPagoCpp` no lockean el `Pago` | Correcto; sin solapamiento con plata en juego | **Aplicado**: decisión escrita (sin lock) |
| B-5 | B | `autorizadoPor` editable razonable; `programado` inerte | grep | Anotado |
| B-7 | B · media | Resolvers con `@Autowired` de campo: el patrón por constructor no aplica | Correcto | **Aplicado**: `@InjectMocks` |
| B-8 | B | Rollback limpio; mensajes sin datos sensibles | verificado | Sin cambio |
