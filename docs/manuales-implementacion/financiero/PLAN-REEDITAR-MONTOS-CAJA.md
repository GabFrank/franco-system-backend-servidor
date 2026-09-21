# Plan — Re-editar los montos de una caja

Rama: `fix/financiero-reeditar-montos-caja` en **central** y **desktop** (filial no se toca).

## Síntoma

Un ADMIN edita los montos de un conteo (apertura o cierre), la edición sale bien, y al intentar
editarlo otra vez en la misma pantalla (p. ej. para volver a los montos originales) el botón
**«Guardar edición» no hace nada**: ni confirmación, ni error.

## Causa

- `PdvCajaGraphQL.editarConteoCajaDesdeServidor` recibe de `FilialCajaProxyService.editarConteoEnFilial`
  el id de la nueva versión del conteo, pero lo descarta: devuelve `CajaFilialOperacionResult.ok(cajaId)`
  = `{ exito, cajaId }`.
- El desktop (`adicionar-conteo-dialog.component.ts:editarConteo`) refleja la edición en memoria con
  un `new Conteo()` **sin `id`** (el conteo nuevo todavía no llegó al central por replicación).
- En la segunda edición, `conteoAnteriorId = this.selectedConteo?.id` es `undefined` y el método
  hace `return` en silencio.

## Fases

### Fase 1 — central: devolver el id del conteo nuevo
- `CajaFilialOperacionResult`: campo nuevo `conteoId: Long` + `ok(cajaId, conteoId)`; `ok(cajaId)`
  se conserva para `abrirCajaDesdeServidor`.
- `pdv-caja.graphqls`: `type CajaFilialOperacionResult { ... conteoId: ID }` (nullable, aditivo).
- `PdvCajaGraphQL.editarConteoCajaDesdeServidor`: `return CajaFilialOperacionResult.ok(cajaId, nuevoConteoId)`.
- Mismo método, rama `ResourceAccessException`: el mensaje «No se modifico ningun monto» es falso
  si la filial confirmó pero la respuesta no llegó en 30 s (read timeout del `RestTemplate`).
  Cambiar a «No se pudo confirmar la edicion con la sucursal. Vuelva a abrir la caja para ver los
  montos vigentes.» (hallazgo B-R1).
- Test: `PdvCajaGraphQLEditarConteoTest` (Mockito, sin contexto) — con `FilialCajaProxyService`
  mockeado devolviendo `99L`, el resultado trae `exito=true`, `cajaId` y `conteoId=99`.
  Estilo `@InjectMocks` + `MockitoAnnotations.openMocks` como `CancelarFacturaLegalPlazoTest`.
  Revertir **solo** la línea del `return` (dejando el DTO) y ver que falla por aserción: revertir
  todo da error de compilación, no un fallo del test.
- Gate: `./mvnw clean verify -B -DskipFlyway=true`, leído del log.
- Commit: `fix(financiero): devolver el id del conteo nuevo al editar los montos de una caja`.

### Fase 2 — desktop: usar el id devuelto
- `graphql-query.ts` (`editarConteoCajaDesdeServidor`): pedir `conteoId`.
- `editarConteoCaja.ts`: `conteoId?: number` en la interfaz `CajaFilialOperacionResult`.
- `adicionar-conteo-dialog.component.ts:editarConteo`: al éxito, `conteo.id = +res.conteoId`
  (`ID` llega como string; hallazgo A-R2).
- Mismo archivo, `guardarConteo`: `conteo.id = res.id` antes de asignar `selectedConteo`. Hoy el
  conteo recién cargado también queda sin id y «Editar montos» sobre él es el mismo botón muerto
  (hallazgo A-R1; `res` es el conteo que devuelve el `saveConteo` de la filial).
- `onButtonClick` / `editarConteo`: flag `guardandoEdicion` que bloquea un segundo envío mientras
  hay uno en curso — un doble clic hoy abre dos confirmaciones y la filial no tiene lock, así que
  dos ediciones con el mismo `conteoAnteriorId` duplicarían el efectivo (hallazgo B-R2).
- Mismo método: reemplazar el `return` silencioso cuando `selectedConteo.id == null` por un aviso
  («No se pudo identificar el conteo vigente. Cerrá y volvé a abrir la caja.») — para que un
  estado inconsistente nunca vuelva a ser un botón muerto.
- Gate: `NODE_OPTIONS=--max_old_space_size=8192 npx ng build --configuration production --no-progress`, leído del log.
- Commit: `fix(caja): permitir editar de nuevo los montos de un conteo ya editado`.

## Tabla de datos nuevos

| Dato | Escritor | Lector |
|---|---|---|
| `CajaFilialOperacionResult.conteoId` | `PdvCajaGraphQL.editarConteoCajaDesdeServidor` (id que devuelve la filial en `editarConteoEnFilial`) | `AdicionarConteoDialogComponent.editarConteo` → `selectedConteo.id` → `conteoAnteriorId` de la próxima edición |

Sin migraciones. Sin cambios de persistencia ni de replicación. Filial: N/A, ya devuelve `{ id conteoAnteriorId }`.

## Orden de despliegue (§3.2)

**Central antes que desktop.** El desktop nuevo pide `conteoId`; contra un central viejo la
mutación falla por campo inexistente y la edición se rompe entera. El central nuevo con un desktop
viejo no cambia nada (campo nullable que nadie pide).

- Mergear el central a `develop` **no despliega** (`deploy-auto.yml` nunca se dispara): hace falta
  `Deploy instance=alpha` y ver la versión nueva en mauro.
- El desktop, en cambio, publica solo al mergear (`release.yml` → `alpha.yml`, auto-update cada
  5 min). Por eso el PR del desktop **se mergea recién con el central ya desplegado** en ese canal,
  y lo mismo al promover a `release/beta` / `master` (el central beta/prod requiere reviewer; el
  desktop no).
- **Web (Cloudflare Pages):** `Deploy Web` es manual y acepta cualquier `ref`/`canal`. No publicar
  en un canal web un desktop con este fix antes de que el central de ese canal lo tenga.
- **No revertir el central** mientras el desktop nuevo esté en algún canal: la edición de montos
  quedaría rota por completo (falla segura: la validación GraphQL corta antes de escribir).
- No hay forma limpia de que el desktop tolere un central viejo: el campo desconocido falla en la
  validación aunque vaya con `@include`.

Regla «método paralelo `Mobile`»: N/A — el cambio es un campo nullable aditivo en el tipo de
respuesta; clientes existentes no lo piden y no cambian de comportamiento.

## Prueba manual (local: central 8081 dev + filial 8082 dev + `ng serve -c web`)

1. Abrir una caja no verificada como ADMIN → Conteo Inicial → Editar montos → cambiar un billete → Guardar edición → confirmar.
2. Sin cerrar el diálogo: Editar montos otra vez → volver al monto original → Guardar edición.
   **Esperado:** aparece la confirmación y guarda; el historial muestra la versión anterior.
3. Repetir 1–2 sobre Conteo Final.
4. En la base de la filial: la cadena `conteo_anterior_id` tiene 3 eslabones y los `movimiento_caja`
   `CAJA_INICIAL`/`CAJA_FINAL` activos corresponden solo al último conteo.

## Fuera de alcance / sin verificar

- **Carrera en la filial sin lock** (`ConteoGraphQL.editarConteoCaja`, `findById` sin `@Version`):
  dos requests simultáneos con el mismo `conteoAnteriorId` pasan la validación y duplican
  efectivo. Preexistente; el guard del desktop (fase 2) tapa el doble clic, no dos PCs a la vez.
- El test de fase 1 mockea el proxy: no verifica que `conteoId` se resuelva por el esquema
  GraphQL. Lo cubre la prueba manual (paso 2).
- **Reabrir la caja antes de que la edición llegue al central por replicación**: el central
  devuelve el conteo viejo y la filial rechaza con «El conteo fue editado por otro usuario». Es el
  control de concurrencia funcionando con datos atrasados; se resuelve reabriendo pasados unos
  segundos. No se toca en este fix (cambiar el mensaje implica desplegar el filial a 24 sucursales).
- Tras la 2.ª edición, `conteo.conteoAnterior` en memoria apunta al conteo en memoria anterior (sin
  `creadoEn` real del backend); «Ver historial» compara contra esa versión. Aceptable: al reabrir
  la caja se ve lo que llegó por replicación.
- `PdvCajaGraphQL.java:411` toma el usuario ADMIN de `conteoInput.usuarioId` (lo manda el cliente),
  no de la sesión: cualquier sesión puede pasar el chequeo de rol y figurar como otro autor.
  Preexistente, no lo agrava este diff; ticket aparte.
- Doble clic en «Abrir/Cerrar caja» (no en la edición) puede crear un conteo huérfano en la filial
  (`ConteoGraphQL.saveConteo` no rechaza una caja que ya tiene conteo). Preexistente.
- El `saveConteo` del central devuelve `null`: abrir/cerrar caja desde el admin (no venta touch)
  por este diálogo no hace nada. Preexistente, fuera de alcance.

## Auditoría del diff (paso 8)

Condicionales A y B: N/A, ningún archivo del diff matchea sus globs.

| Hallazgo | Eje | Verificado | Qué se hizo |
|---|---|---|---|
| ADMIN se valida antes de cualquier efecto; `conteoId` no expone nada | Fijo 1 | sí | sin cambios |
| usuario ADMIN tomado del input del cliente | Fijo 1 | sí (`PdvCajaGraphQL.java:411`) | preexistente, anotado |
| `conteoId` con escritor y lector de punta a punta; sin migración | Fijo 2 | sí | sin cambios |
| `res.id` sin convertir a número en `guardarConteo` | Fijo 2 | sí | `+res.id` |
| `guardandoEdicion` trabado si se cancela dentro del segundo de espera (y se disparaba `guardarConteo`) | Fijo 3 | sí (`onButtonClick` leía `enEdicion` al vencer el timeout) | modo tomado al clic |
| Deploy Web a Cloudflare sin mención en el orden de despliegue | Fijo 3 | sí | agregado |

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Verificado | Qué se hizo |
|---|---|---|---|
| R1: `guardarConteo` deja `selectedConteo` sin id → mismo botón muerto tras cargar un conteo | A | sí (`adicionar-conteo-dialog.component.ts:418`) | Fase 2 |
| R2: `ID` llega como string | A | sí | Fase 2 (`+res.conteoId`) |
| R3: test con proxy mockeado no cubre el esquema | A | sí | prueba manual; anotado |
| Orden de despliegue: merge ≠ deploy en central; desktop publica solo | A | sí | sección Orden de despliegue |
| R1: mensaje «No se modifico ningun monto» falso tras timeout | B | sí (`PdvCajaGraphQL.java:428-430`) | Fase 1 |
| R2: doble clic → dos ediciones, filial sin lock | B | sí (`onButtonClick` sin flag; botón habilitado en edición) | guard en Fase 2; carrera entre PCs fuera de alcance |
| Test «revertir y ver fallar» da error de compilación si se revierte todo | B | sí | Fase 1: revertir solo el `return` |
| Doble edición en filial desactiva bien los movimientos y encadena historial | B | sí (`ConteoGraphQL.java:180,192,223-229`) | sin cambios |
