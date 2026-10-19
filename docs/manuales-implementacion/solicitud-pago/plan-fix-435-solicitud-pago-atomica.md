# Plan — fix #435: solicitud de pago atómica, monto al agregar nota, borrado de detalle y estado editable

Rama: `fix/compras-solicitud-pago-atomica` (desde `origin/develop` `03743e38`). Pieza: **central**.
Issue: GabFrank/franco-system-backend-servidor#435 (+ un punto agregado en la revisión: validar el estado).

## Diagnóstico (verificado leyendo `develop`, sin ejecutar)

| # | Punto de la issue | Veredicto | Evidencia |
|---|---|---|---|
| 1 | `saveSolicitudPago` / `actualizarSolicitudPago` no son atómicos | **Confirmado** | `SolicitudPagoGraphQL` sin `@Transactional`; `crearSolicitudPago`/`actualizarSolicitudPago` y `sincronizarDetalles` comitean cada uno lo suyo. Reintento del alta: «La nota de recepción ya está incluida en otra solicitud de pago» (`SolicitudPagoNotaRecepcionService:70`) |
| 2 | `agregarNotaASolicitud` no recalcula `montoTotal` | **Confirmado**, y además el monto llega en la moneda equivocada | El desktop manda `montoIncluido = nota.valorTotal` (moneda de la nota, `create-edit-solicitud-pago-dialog.component.ts:410`); el alta y la edición usan `calcularMontoNotaEnMoneda(nota, monedaCabecera)`. Recalcular sin corregir esto suma montos en monedas mezcladas |
| 3a | `deleteById` con id inexistente → error «rollback-only» | **Confirmado** | Spring Data 2.7: `SimpleJpaRepository.deleteById` lanza `EmptyResultDataAccessException`; la transacción del repo marca rollback-only la de `CrudService.deleteById`, que atrapa y devuelve `false`; al commit, `UnexpectedRollbackException` |
| 3b | FK bloquea el borrado y devuelve `false` | **No aplica** | Ninguna FK apunta a `solicitud_pago_detalle` (`V0`, `V108`); `PagoSolicitudDetalle` guarda `solicitudPagoId` suelto. Y `deleteById` no hace flush: una FK saltaría al commit, fuera del `catch` |
| 4 | (agregado) las mutations sueltas del diálogo validan el estado editable, igual que la edición completa | **A hacer** | regla de negocio: `actualizarSolicitudPago` ya la exige; se lleva a `agregarNotaASolicitudPago`, `removerNotaDeSolicitudPago`, `agregarSolicitudPagoDetalle`, `eliminarSolicitudPagoDetalle` |

Reglas de estado vigentes (fuente: código):
- Notas y cabecera: editables en **PENDIENTE** o **DEVUELTO** (`SolicitudPagoService.actualizarSolicitudPago:466`, desktop `isEditable:168`).
- Formas de pago (detalles): el desktop las deja editar en **PENDIENTE, DEVUELTO o PARCIAL** (`updatePuedeEditarFormaPago:676`). Ningún pago depende de `solicitud_pago_detalle` (`PagoProveedorService` no lo lee).

## Fases

### Fase 1 — alta y edición en una sola transacción (punto 1)

- `@Transactional` en `SolicitudPagoGraphQL.saveSolicitudPago` y `.actualizarSolicitudPago`. Precedente: `GuardadoPedidoItemGraphQL` (#444). El `catch` re-lanza `RuntimeException`, así que todo vuelve atrás.
- **`solicitudPagoService.getRepository().flush()` como última línea dentro del `try`** (auditoría B): con la transacción en el resolver el commit pasa después del `return`, fuera del `catch`; un CHECK / NOT NULL / duplicado saldría crudo, sin el «Error al crear solicitud de pago: …» que muestra el desktop. El flush lo adelanta adentro del `try`.
- `crearSolicitudPago` ya comparte transacción con `procesoEtapaService.actualizarEtapaAEnProceso`, que tiene `noRollbackFor = IllegalStateException` (`ProcesoEtapaService:109`): ampliar la transacción no cambia ese caso.
- Clientes: desktop, mobile y PWA llaman `saveSolicitudPago`; mismo contrato, solo cambia que un fallo no deja restos.
- **Test en CI**: guarda por reflexión de que los métodos del resolver de este plan llevan `@Transactional` (no prueba atomicidad, evita que alguien la saque).
- **Test**: `SolicitudPagoAtomicidadIT` (patrón de `SolicitudPagoNumeroIT`: `-Dit.financiero=true`, base local, sin CI) — `saveSolicitudPago` con un detalle de moneda inexistente → falla, no queda la solicitud ni la relación con la nota, y el reintento corregido funciona. Ídem `actualizarSolicitudPago`: detalles viejos intactos.

### Fase 2 — agregar nota: monto en moneda de la cabecera y recálculo (punto 2)

- Nuevo `SolicitudPagoService.agregarNota(solicitudId, notaId)` `@Transactional`: exige estado PENDIENTE/DEVUELTO con `exigirEditable` (nace en esta fase, descrito en la fase 3), calcula `calcularMontoNotaEnMoneda(nota, solicitud.getMoneda())`, llama `agregarNotaASolicitud` y `recalcularMontoTotalSolicitud`.
- El resolver `agregarNotaASolicitudPago` llama a este método. El argumento `montoIncluido` **se mantiene en el esquema** (desktop lo manda) pero se ignora: el servidor es la fuente del monto.
- `agregarNotaASolicitud` del servicio de notas no cambia (lo usan `crearSolicitudPago` y `actualizarSolicitudPago`, que ya convierten y recalculan).
- **Tests** (Mockito, estilo `SolicitudPagoServiceMontoRedondeoTest`): monto convertido a la moneda cabecera con nota en USD y solicitud en Gs; `recalcularMontoTotalSolicitud` invocado; rechazo en SOLICITADO.
- **Criterio de aceptación bloqueante** (auditorías A y B): en la prueba por pantalla, el total que muestra el diálogo tras agregar una nota en otra moneda coincide con el `montoTotal` guardado (reabrir la solicitud). Si no coincide, la fase no cierra: se decide con Franco si el diálogo pasa a tomar el total del servidor (cambio de desktop).
- Filas viejas: relaciones creadas por la mutation actual con `montoIncluido` en la moneda de la nota ya contaminan hoy el recálculo de `removerNota` y de Guardar. Antes de la fase, contar cuántas hay en la base local (copia de bodega): nota con moneda ≠ cabecera y `monto_incluido` = valor de la nota sin convertir. Si hay, se informa; corregirlas es un trabajo aparte (UPDATE sobre producción).
  - **Resultado (17/10):** la base local tiene solo 3 relaciones y ninguna con monedas distintas. No es muestra de producción: queda **sin verificar en bodega/farmacia** (consulta de solo lectura para correr allá cuando Franco lo pida).

### Fase 3 — estado editable en las mutations sueltas y borrado de detalle (puntos 3 y 4)

- `SolicitudPagoService.exigirEditable(solicitudId, estadosPermitidos)`: toma la solicitud con el `lockById` que ya existe (`SolicitudPagoRepository:27`, lock pesimista de los pagos parciales) y valida el estado. Así la validación y el cambio quedan en la misma transacción con la fila bloqueada: se cierra la carrera con un cambio de estado concurrente (auditoría B). `actualizarSolicitudPago` pasa a usarlo con su mensaje actual.
- Las 4 mutations sueltas llevan `@Transactional` + `flush()` dentro del `try`, igual que la fase 1, y llaman primero a `exigirEditable` (tras `exigirSolicitudDeCompra`), con `errorParaMostrar` para que el desktop muestre el motivo:
  - `removerNotaDeSolicitudPago` → PENDIENTE, DEVUELTO (agregar ya quedó en fase 2).
  - `agregarSolicitudPagoDetalle`, `eliminarSolicitudPagoDetalle` → PENDIENTE, DEVUELTO, **PARCIAL** (igual que el desktop; ver decisión abierta). **Cambiado el 17/10: PARCIAL cerrado, ver Decisiones.**
- `eliminarSolicitudPagoDetalle` deja de usar `CrudService.deleteById`: nuevo `SolicitudPagoDetalleService.eliminarDetalle(id)` `@Transactional` = `findById(id).ifPresent(delete)`, devuelve `true`, con `log.warn` si el id no existía. Id inexistente = ya borrado (idempotente: el desktop quita la fila en `next`, y el flujo editar = borrar + agregar sigue). Cualquier otro error sube con su mensaje.
- `CrudService.deleteById` **no se toca**: lo heredan ~190 servicios; cambiarlo es otro trabajo.
- **Tests** (Mockito): rechazo por estado en las 3 mutations; PARCIAL acepta detalle y rechaza nota; `eliminarDetalle` con id inexistente → `true` sin llamar `delete`.

## Datos nuevos

Ninguno: sin columnas, migraciones ni campos GraphQL. `N/A Fijo 2 / espejo filial porque no hay DDL`.

## Otras piezas

- desktop: `N/A` porque no cambia el contrato (el `montoIncluido` sigue aceptado; los rechazos nuevos los maneja el diálogo con `erroresDeRechazo` / `recargarSolicitud`). Si la decisión abierta cierra PARCIAL, sí hay cambio de desktop.
- filial: `N/A` porque la solicitud de pago vive solo en el central: `solicitud_pago*` no está en `central_pub` ni en `central_bodega_filial24_pub` (consulta a `pg_publication_tables` en la base local, copia de bodega, 17/10).
- PR: aclarar que un desktop viejo sigue mandando `montoIncluido` (ignorado) y calcula su total en local hasta recargar.
- mobile / mobile-pwa: `N/A` porque solo usan `saveSolicitudPago`, sin cambio de contrato.

## Pruebas de runtime (paso 9)

Central propio desde el worktree (copiar `application-user-dev.properties`; mirar antes quién ocupa el 8081) + desktop `ng serve -c web` en un puerto propio, usuario `MR`.

| Fase | Por pantalla | Por API (solo lo que la pantalla no puede provocar) |
|---|---|---|
| 1 | Crear y editar solicitud con notas y 2 formas de pago | `saveSolicitudPago` / `actualizarSolicitudPago` con moneda inexistente en un detalle → error y base sin restos (SQL) |
| 2 | Editar solicitud en Gs, agregar nota en USD, cerrar sin Guardar → reabrir: monto correcto | — |
| 3 | Quitar nota y editar/borrar forma de pago en PENDIENTE y PARCIAL; doble clic en borrar | Agregar/quitar nota y detalle en SOLICITADO y CONCLUIDO → rechazo con motivo |

Batería: `./mvnw clean verify -B -DskipFlyway=true` + el IT con `-Dit.financiero=true`.

### Resultados (17/10, central del worktree en :8084 + desktop develop `87070f4d` en :4201, usuario MR)

Datos preparados por SQL y restaurados al final (diff contra copia CSV: igual). Batería: 2059 tests, 0 fallos. IT: pasa con el fix y falla sin él (2/2).

| Fase | Caso | Resultado |
|---|---|---|
| 1 | Alta por pantalla con 2 notas → el diálogo guarda solo | OK, `SP-000055` |
| 1 | Editar: 2 formas de pago + Guardar | OK, detalles 100.000.000 + 71.030.000 |
| 1 | API: `actualizarSolicitudPago` con moneda inexistente en el 2.º detalle | Error «Error al actualizar…: Moneda no encontrada: 999999»; observaciones y detalles intactos |
| 1 | API: `saveSolicitudPago` con moneda inexistente | Error con su mensaje; ni solicitud ni nota 9 tomada |
| 2 | Agregar nota USD 30.000 (cot. 5700) a solicitud en Gs y cerrar sin Guardar | Total guardado 171.030.000 = el del diálogo; `monto_incluido` 171.000.000 (antes hubiera sido 30.000). **Criterio bloqueante cumplido** |
| 2 | API en DEVUELTO: agregar nota con `montoIncluido: 1` | Monto del servidor (166.080), total recalculado; quitarla vuelve al total anterior |
| 3 | PENDIENTE: quitar nota, editar forma de pago (borrar+agregar), borrar con doble clic | OK; el doble clic manda un solo borrado |
| 3 | API: borrar forma de pago ya borrada (id 17) | `true`, sin error de rollback-only |
| 3 | API en PARCIAL | agregar/borrar forma de pago: OK; quitar/agregar nota: rechazo con motivo |
| 3 | API en SOLICITADO y CONCLUIDO, las 4 mutations | Rechazo con motivo; base sin cambios |

**Corrección al análisis:** el desktop **no** deja editar formas de pago en PARCIAL. El menú de la forma de pago está bajo `*ngIf="isEditable"` (`create-edit-solicitud-pago-dialog.component.html:169`, solo PENDIENTE o DEVUELTO); la rama PARCIAL de `updatePuedeEditarFormaPago` es inalcanzable. La decisión de mantener PARCIAL se tomó sobre la premisa contraria: se vuelve a consultar a Franco.

Hallazgos de la prueba:
- El IT dejaba la etapa SOLICITUD_PAGO del pedido en EN_PROCESO: ahora la guarda y la restaura.
- El rechazo de `agregarNotaASolicitudPago` llega con el prefijo «Error al agregar nota a solicitud de pago:»; los otros tres, limpios (`errorParaMostrar`). El motivo se lee igual; se deja así.

## Auditoría del diff (paso 8, 17/10)

Condicionales A y B: `N/A` porque el diff no toca workflows, release, migraciones, replicación ni scheduler.

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| Fijo 1 (autorización) | Sin hallazgos altos ni medios | — |
| Fijo 1 | «`agregarNota` no valida el proveedor» | Falso: `agregarNotaASolicitud` lo valida (`SolicitudPagoNotaRecepcionService:79-83`) |
| Fijo 2 (datos) | **MAYOR**: el resolver de edición hacía `findById` antes del lock; `lockById` devuelve la instancia cacheada sin refrescar (documentado en `SolicitudPagoRepository:29-31`) y validaba un estado viejo | Verificado. El resolver carga la solicitud con `exigirEditable`; test `laEdicionNoCargaLaSolicitudAntesDelLock` (falla con el código viejo: «findById antes del lock») |
| Fijo 2 | Rollback-only silencioso, deadlock, `eliminarDetalle` con Hibernate, auto-flush antes de recalcular | Sin hallazgo (verificado) |
| Fijo 3 (clientes) | Sin cambio de esquema; ningún cliente llama las 4 mutations fuera de PENDIENTE/DEVUELTO; nadie trata `false` del borrado | — |
| Fijo 3 | Medio-bajo: el desktop agrega varias notas en paralelo y ahora se serializan por el lock | Probado por API: 4 notas a la vez, todas OK, total = suma |

Pruebas por API tras cerrar PARCIAL y el fix del lock (central :8084, token de MR, datos restaurados y comparados contra la copia: igual):
- 4 `agregarNotaASolicitudPago` en paralelo sobre SP-000013: las 4 OK; `monto_total` = suma de relaciones.
- PARCIAL: agregar/borrar forma de pago y agregar/quitar nota → las 4 rechazadas con motivo; nada cambia.
- Edición: PENDIENTE edita (detalle pasa a TRANSFERENCIA 20); SOLICITADO rechaza «No se puede editar la solicitud» sin tocar observaciones.
- Encontrado de paso: SP-000013 tenía en la base local `monto_total` 332.161 contra 447.501 de sus notas, dato viejo del mismo bug. Se restauró tal cual estaba.

## Decisiones

- ~~**Formas de pago en PARCIAL** (Franco, 17/10): se mantiene.~~ Tomada sobre una premisa falsa (ver «Corrección al análisis»).
- **Formas de pago en PARCIAL, revisada** (Franco, 17/10): **se cierra**. Notas y formas de pago, solo en PENDIENTE o DEVUELTO (`ESTADOS_EDITABLES`, se borra `ESTADOS_DETALLE_EDITABLES`). Sin cambio de desktop: su diálogo ya era de solo lectura en PARCIAL, así que no hay orden de despliegue que cuidar.

## Sin verificar

- Que el proxy transaccional aplique al resolver (CGLIB, Boot 2.7): se confirma con el IT de la fase 1.
- `ultimoCambioEnGs` (`SolicitudPagoService:321-331`) cae a cotización 1.0 si la nota no trae cotización y no hay `Cambio`: una nota en USD sumaría como Gs. Comportamiento existente del alta y la edición; fuera de alcance, se anota en el PR.
- `saveSolicitudPago` en la fase 1 hereda el mismo bloque tolerado de `actualizarEtapaAEnProceso`: solo `IllegalStateException` tiene `noRollbackFor`. Ya compartía transacción con `crearSolicitudPago`, no empeora.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| B (mayor) | Con la transacción en el resolver, errores de flush/commit salen fuera del `catch` | `flush()` dentro del `try` (fases 1 y 3) |
| B | Validación de estado y cambio en transacciones distintas → carrera | `exigirEditable` con `lockById` dentro de la misma transacción |
| B | Test Mockito no prueba atomicidad; el IT no corre en CI | Guarda por reflexión en CI + IT a mano |
| B | Filas viejas con `montoIncluido` en moneda de la nota; fallback de cotización 1.0 | Conteo antes de la fase 2; fallback anotado fuera de alcance |
| B | Borrado idempotente oculta ids mal tipeados | `log.warn` |
| A + B | Total del diálogo puede diferir del guardado con monedas distintas | Pasa de «sin verificar» a criterio bloqueante de la fase 2 |
| A | Desktop viejo calcula su total en local | Nota en el PR |
| A | Cerrar PARCIAL en detalles obliga a desplegar el desktop primero | Falso: el desktop ya no editaba en PARCIAL. Cerrado sin cambio de desktop |
| A | Réplica de `solicitud_pago` sin confirmar | Verificado: no se publica |
