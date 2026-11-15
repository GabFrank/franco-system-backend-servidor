# Cancelar solicitudes de pago de tipo GASTO (issue central #599)

Rama `feature/financiero-cancelar-solicitud-gasto` en central y desktop, desde `develop`.

## Problema

Una `SolicitudPago` de tipo GASTO que ya no corresponde no se puede cancelar: el `cancelar` de
compras la rechaza (`SolicitudPagoService.exigirSolicitudDeCompra`) y el módulo de gastos no tiene la
acción. Caso real del 10/11 en bodega: un gasto pagado, con el pago anulado, quedó SOLICITADO
duplicado y hubo que cancelarlo por SQL.

## Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Dónde vive | `PagoProveedorService.cancelarGasto` + mutation `cancelarGastoParaPago` en `PagoProveedorGraphQL` (`pago-proveedor.graphqls`), junto a `crearGastoParaPago` y `gastosPendientes` | Es el módulo que crea y lista los gastos pagables; ya tiene el lock de la solicitud (`asignarACaja`), `preGastoService`, `gastoTesoreriaService`, `detalleRepository` y la seguridad: no cambia ningún constructor |
| Estados de origen | PENDIENTE, SOLICITADO, DEVUELTO en el servidor. **En la práctica solo SOLICITADO llega al botón**: un GASTO nace SOLICITADO, «devolver» es solo de compras y el hub lista SOLICITADO/PARCIAL | Lo que pide la issue, sin pruebas por pantalla de estados inalcanzables |
| Sin pagos | `exigirSinPagos` (PARCIAL o `montoPagado > 0` rechaza) **y además** ningún `PagoSolicitudDetalle` con `anulado != true` (`findBySolicitudPagoIdOrderByCreadoEnAsc`) | El monto puede quedar desfasado de los detalles (hay datos arreglados por SQL); con un detalle vivo, anular ese pago después reabriría la solicitud cancelada |
| Concurrencia | `solicitudPagoRepository.lockById` antes de leer estado y pagos. Un solo lock, sin caja ni banco | El pago toma el mismo lock primero; la anulación lo toma al final. Cancelar contra pagar o anular no puede pasar por los dos lados ni genera ciclo de locks |
| Motivo | Obligatorio; `CANCELADA POR <nick> (estaba <ESTADO>): <MOTIVO>` en `observaciones` | Igual que compras, más el estado de origen para poder deshacerla |
| `montoTotal` | **No** se pone en 0 | En compras se pone en 0 porque libera las notas; un gasto no tiene notas y el monto es el rastro. Nada lo cuenta como deuda: `DEUDA_ABIERTA` y `PAGABLES` son SOLICITADO/PARCIAL (aging, vencimientos, hub), el reporte de solicitudes es solo COMPRA y no suma canceladas, el gráfico por categoría lee `financiero.gasto` |
| Gasto espejo (`financiero.gasto`, sucursal 0) | Se llama `GastoTesoreriaService.sincronizarDesdeSolicitudPago(sp)` **como defensa**: con la regla de sin pagos el espejo, si existe, ya quedó `cancelado = true` al anular el pago, así que hoy es no-op y no emite un segundo `gasto.cancelado` | Reusar el camino de la anulación sin duplicar lógica |
| PreGasto vinculado | `PreGastoService.cancelarDesdeSolicitudPago(sp, motivo, usuario)`: solo desde ENVIADO_A_TESORERIA (otro estado: no-op con log). Pasa a **RECHAZADO**, `motivoRechazo` = solo el texto de esta cancelación (no todo `observaciones`), `rechazadoEn` = ahora, `rechazadoPor` = persona del usuario. **No** toca `autorizadoPor`/`autorizadoEn` ni `solicitudPagoId` (queda como rastro). Guarda con `repository.save`, no con `save()` del servicio (que revalida el monto contra la cuota del activo y podría trabar la cancelación). No escribe `administrativo.autorizacion`: no es un rechazo del autorizador | RECHAZADO ya existe en Java, `.graphqls` y PG: sin migración ni enum nuevo (lo que rompió la PWA con PAGADO). `sincronizarDesdeSolicitudPago` lo devolvería a ENVIADO_A_TESORERIA, por eso va aparte |
| RECHAZADO por cancelación es terminal | Guarda en `PreGastoService.autorizar`: rechaza `RECHAZADO && solicitudPagoId != null` («Esta solicitud se canceló en tesorería: cargá un gasto nuevo») | Hoy `autorizar` no mira el estado: por API se podría reautorizar, retirar con QR o reenviar y crear otra solicitud. Un rechazo normal ocurre antes de enviar a tesorería, sin `solicitudPagoId`, así que no lo afecta |
| Anular un pago de una solicitud cancelada | Guarda en `anularPagoCpp`: si alguna solicitud del evento está CANCELADO, rechaza sin revertir nada | Con el código actual la reabriría a SOLICITADO en silencio (y su PreGasto a ENVIADO_A_TESORERIA). Solo puede pasar con datos tocados por SQL; mejor que lo revise una persona |
| Seguridad | Resolver: `seg.requireGestionar()` primera línea. Servicio, con el lock tomado: si `cajaVirtualAsignadaId != null`, `seguridad.requireEscrituraCaja(id)` | Lo que pide la issue. El rol acotado TESORERIA CAJA no cancela: qué se debe lo decide tesorería |
| Tipo | Rechaza si `tipo != GASTO` | Compras se cancela desde compras; RRHH desde su documento |
| Errores | El servicio lanza `IllegalStateException`/`IllegalArgumentException` (reusa `exigirSinPagos`/`exigirMotivo`); el resolver las atrapa y las relanza como `GraphqlErrorException` con el mensaje, igual que `cancelarSolicitudPago` | `PagoProveedorGraphQL` no tiene ese `catch` hoy: sin él, el desktop mostraría el error genérico |
| Idempotencia | Reintento: el segundo pedido ve CANCELADO y rechaza («ya está cancelada»). El desktop deshabilita el botón de la fila mientras la mutation está en curso. `AltaIdempotenteService.gastoParaPago` ya rechaza reusar la clave de un gasto cancelado: se actualiza su comentario («hoy ningún flujo cancela…») | Sin doble efecto; el comentario pasa a ser un caso real |

## Fases

### Fase 1 — central

- `SolicitudPagoService.cancelarGasto(SolicitudPago sp, String motivo, Usuario usuario)`: motivo,
  tipo GASTO, estado de origen, `exigirSinPagos`, observación, CANCELADO, `save`. No pasa por
  `cambiarEstado` (que exige compra y libera notas).
- `PagoProveedorService.cancelarGasto(Long solicitudId, String motivo, Usuario usuario)`
  (`@Transactional`): lock, ACL de la caja asignada, detalles vivos, `solicitudPagoService.cancelarGasto`,
  `gastoTesoreriaService.sincronizarDesdeSolicitudPago`, `preGastoService.cancelarDesdeSolicitudPago`.
- `PagoProveedorService.anularPagoCpp`: guarda de solicitud CANCELADA antes de revertir.
- `PreGastoService.cancelarDesdeSolicitudPago` + guarda en `autorizar`.
- `PagoProveedorGraphQL.cancelarGastoParaPago(solicitudId, motivo)` + `pago-proveedor.graphqls`:
  `cancelarGastoParaPago(solicitudId: ID!, motivo: String!): SolicitudPago!`.
- Comentario de `AltaIdempotenteService.gastoParaPago`.
- Tests (Mockito):
  - `PagoProveedorServiceCancelarGastoTest`:
    1. SOLICITADO sin pagos → CANCELADO, observación con firma, estado de origen y motivo,
       `montoTotal` intacto, se llama a las dos sincronizaciones.
    2. PARCIAL / `montoPagado > 0` → rechaza con «anular el pago» y no guarda.
    3. `montoPagado = 0` pero con un detalle no anulado → rechaza y no guarda.
    4. Tipo COMPRA y tipo RRHH → rechaza.
    5. Motivo vacío → rechaza.
    6. Con caja asignada → llama `requireEscrituraCaja(caja)`; si lanza, no guarda.
    7. CONCLUIDO / CANCELADO → rechaza.
    8. `anularPagoCpp` con una solicitud CANCELADA en el evento → lanza, no revierte ni reabre.
  - `PreGastoCancelarDesdeSolicitudTest`: ENVIADO_A_TESORERIA → RECHAZADO con el motivo y
    `rechazadoPor`, conserva `autorizadoPor`/`autorizadoEn` y `solicitudPagoId`, guarda por el
    repositorio; sin PreGasto o en otro estado → no-op; `autorizar` sobre RECHAZADO con
    `solicitudPagoId` → rechaza, y sobre RECHAZADO sin `solicitudPagoId` → autoriza como siempre.
  - Las guardas nuevas (`anularPagoCpp`, `autorizar`) se prueban también revirtiendo el cambio: el test
    tiene que fallar.
- Resolver: prueba por API (paso 9) de que el rechazo llega con el texto del servicio.
- Build: tests de las clases nuevas + `mvn -DskipTests package`, log leído entero.

### Fase 2 — desktop

- `pagar-compras-dialog/graphql/graphql-query.ts`: `cancelarGastoParaPagoMutation`; GQL nuevo
  `cancelarGastoParaPago.ts`; `PagarComprasService.onCancelarGasto(id, motivo)` por `mutar`.
- `pagar-compras-dialog.component`: en modo GASTOS, columna `acciones` y botón «Cancelar gasto»
  (`MotivoDialogComponent`, igual que «Devolver a compras») en las filas `_cancelable`:
  `esGasto && puedeGestionar && estado !== 'PARCIAL' && !(montoPagado > 0)`. `puedeGestionar` se fija
  en `ngOnInit` con `tieneAlgunRol([ROLES.TESORERIA_GESTIONAR])`. El botón se deshabilita con la fila
  tildada y mientras la mutation está en curso. Al cancelar, la fila sale de la lista; ante un
  rechazo, el mensaje del central y se relee.
- Build: `npm run check` (o `ng build --configuration production`), log leído entero.

## Datos nuevos

No hay columnas ni claves nuevas. Lo único nuevo es la mutation:

| Dato | Escribe | Lee |
|---|---|---|
| `cancelarGastoParaPago` | desktop `pagar-compras-dialog` (`onCancelarGasto`) | central `PagoProveedorGraphQL` → `PagoProveedorService.cancelarGasto` |
| `observaciones` con `CANCELADA POR …` | `SolicitudPagoService.cancelarGasto` | detalle de la solicitud (ya existente) |
| `pre_gasto.motivo_rechazo` / `rechazado_en` / `rechazado_por` | `PreGastoService.cancelarDesdeSolicitudPago` | lista de RECHAZADOS del POS (`adicionar-gasto-dialog`, columna «Motivo rechazo») y lista de PreGasto del desktop y la PWA (ya existentes) |

## Contrato y despliegue

- Solo agrega una mutation: un desktop viejo sigue andando contra el central nuevo. Un desktop nuevo
  contra un central viejo falla solo al tocar «Cancelar gasto» (campo inexistente). Orden: **central
  antes que desktop**, en las dos instancias (bodega y farmacia) antes de que el desktop salga por beta.
- Sin migración y sin env vars. Sin tablas replicadas:
  - `solicitud_pago` y `pre_gasto` no aparecen en ninguna migración de publicación del central;
  - `financiero.gasto` se publica por filial con `WHERE sucursal_id = N` (`V0__initial_schema.sql`,
    `V112`): la sucursal 0 del espejo no viaja;
  - el filial tiene `PreGasto`, pero el POS consulta los pre-gastos al central (#390).
  N/A para el filial.
- La PWA y `frc-mobile` no usan la mutation. Con RECHAZADO + `solicitudPagoId` ninguna pantalla se
  rompe: la PWA no lee `solicitudPagoId` en gastos y retiro y rendición exigen otros estados; el desktop
  no muestra acciones para RECHAZADO; `frc-mobile` lo usa solo como color.

## Pruebas por pantalla (paso 9)

Central y desktop locales, usuario MR:

1. Nuevo gasto en el hub → Cancelar con motivo → sale de la lista; la solicitud queda CANCELADO con la
   observación (estado de origen incluido) y `monto_total` intacto.
2. Gasto pagado y anulado (caso del 10/11): mirar `financiero.gasto.cancelado` del espejo **antes**
   (ya `true`) → Cancelar → sigue igual y no hay un segundo evento `gasto.cancelado`.
3. Gasto de un PreGasto enviado a tesorería → Cancelar → el PreGasto queda RECHAZADO con el motivo,
   conserva la autorización y aparece con el motivo en la lista de rechazados del POS. Autorizarlo por
   API → rechazado.
4. Gasto con pago parcial → el botón no aparece; por API, el central lo rechaza con el mensaje.
5. Sin motivo → el diálogo no deja confirmar; por API, rechaza con el mensaje.
6. Usuario sin TESORERIA GESTIONAR → no ve el botón; por API, «No autorizado».
7. Gasto asignado a una caja sin acceso de escritura → rechaza.
8. Doble pedido por API → el segundo rechaza («ya está cancelada»), sin otro efecto.

## Deshacer una cancelación hecha por error

No hay «reactivar». Gasto creado en el hub: se carga de nuevo. Gasto de un PreGasto, por SQL en el
central (con el estado de origen que dice la observación):

```sql
UPDATE operaciones.solicitud_pago SET estado = 'SOLICITADO' WHERE id = :id AND estado = 'CANCELADO';
UPDATE financiero.pre_gasto SET estado = 'ENVIADO_A_TESORERIA', motivo_rechazo = NULL,
       rechazado_en = NULL, rechazado_por_id = NULL
 WHERE solicitud_pago_id = :id AND estado = 'RECHAZADO';
```

(Nombres de columna a confirmar contra el esquema en el paso 9 antes de dejarlo como runbook.)

## Sin verificar

- `pg_publication_tables` de bodega y farmacia en vivo (hay antecedentes de tablas publicadas a mano):
  confirmar que ni `pre_gasto` ni `solicitud_pago` están publicadas. Necesita acceso a producción
  (lo corre Franco con `!`).

## Registro

- Paso 1: worktrees en central y desktop desde `origin/develop` (15/11).
- Pasos 2–3: skills `frc-financiero-expert`, `frc-central`; análisis sobre el código de `develop`.
- Paso 5 (15/11): eje A y eje B, los dos «aceptar con reservas», sin hallazgos críticos. Incorporado:
  mapeo de errores en el resolver (A1), RECHAZADO terminal con guarda en `autorizar` (A2, B3),
  gasto espejo como defensa y prueba 2 reescrita (A3, B5), evidencia de replicación (A4), lista de
  lectores de deuda (A5), comentario de idempotencia (A6), `motivoRechazo` solo con la cancelación
  (A7), sin auditoría en `administrativo.autorizacion` (A8), estados alcanzables (A9), detalles vivos y
  guarda en `anularPagoCpp` (B1), PreGasto por `repository.save` sin borrar la autorización (B2),
  estado de origen en la observación y SQL de reversión (B3), botón deshabilitado en curso (B4).
