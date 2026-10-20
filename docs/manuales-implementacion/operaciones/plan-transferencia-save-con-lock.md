# Plan — saveTransferencia serializado con el avance de etapa (issue #462)

Rama: `fix/transferencia-save-con-lock`, desde `fix/transferencia-no-repetir-etapa` (#450, 3b221031),
porque usa su `TransferenciaService.lockById`. Pieza: central. El PR va hacia `develop` y muestra
los commits del #450 hasta que ese se mergee; después, `git rebase --onto origin/develop
fix/transferencia-no-repetir-etapa`.

## Qué pasa hoy

`saveTransferencia` no corre en una transacción propia: lee con `service.findById`, valida la etapa
contra esa lectura (`validarAvanceDeEtapa`), arma la entidad con `ModelMapper`, completa lo que el
input no trae desde la lectura y guarda con merge. `Transferencia` no tiene `@Version`.

Si un `avanzarEtapaTransferencia` de la misma transferencia commitea entre la lectura y el guardado
del save, el save escribe de vuelta la etapa vieja (la que traía el input, igual a la leída) y los
campos que copió de la lectura vieja, por ejemplo `usuarioPreparacion = null`. Queda una etapa
anterior con ítems copiados y movimientos de stock armados para la siguiente. El rechazo de
`validarAvanceDeEtapa` no salta porque compara contra la lectura vieja.

## Qué se hace

1. `saveTransferencia` pasa a envolver el cuerpo actual (movido a un método privado
   `guardarTransferencia`) en un `TransactionTemplate`, igual que `avanzarEtapaTransferencia` y
   `verificarParaTransporteMobile`.
2. La lectura de la transferencia existente pasa de `service.findById` a `service.lockById`, **en el
   mismo lugar** (es la primera carga de la transferencia en el request: `lockById` no refresca una
   instancia ya cargada). La validación de etapa y la copia de campos usan esa lectura bloqueada.
3. Resultado: avance y save de la misma transferencia quedan en fila.
   - Avance primero: el save espera, lee la etapa nueva y, si el input trae la vieja,
     `validarAvanceDeEtapa` lo rechaza. El usuario B pierde lo que editó y tiene que reabrir.
   - Save primero: el avance espera, lee lo que dejó el save y avanza normal.
4. El mensaje de `validarAvanceDeEtapa` suma «Volve a abrirla para ver como quedo.»: en el save el
   caso real es que otro la avanzó. Ningún test ni cliente compara ese texto (verificado con grep en
   central, desktop, PWA y frc-mobile).
5. Una transferencia nueva (`id == null`) también corre dentro de la transacción. No hay nada que
   bloquear (`transferencia == null`); se envuelve igual para no partir el método en dos caminos.

### Qué cambia al meterlo en una transacción

- OSIV (`FrancoSystemsApplication.OpenFilter`) ya hace que todo el request comparta el
  EntityManager; lo nuevo es una sola transacción y el bloqueo hasta el commit (mismo análisis que el
  plan del #450).
- `@Transactional(isolation = SERIALIZABLE)` de `TransferenciaService.save` se une a la transacción
  externa (READ COMMITTED). Ya pasa en `avanzarEtapa`. La exclusión la da el bloqueo de fila.
- `TransferenciaNotificationListener` es `@TransactionalEventListener(AFTER_COMMIT)`: el aviso de
  cambio de sucursal sale al commit del save completo, incluidos los movimientos de los ítems.
- Si falla el rearmado de movimientos al cambiar de sucursal, ahora se revierte también el header
  (antes quedaba la sucursal cambiada con los movimientos a medias).

## Llamadores (eje A)

Todos esperan la respuesta del save antes de seguir; ninguno manda save y avance en paralelo:

- desktop `edit-transferencia` (sucursales, solicitante, crear antes del primer ítem, observación),
  `list-transferencia` (asignar hoja de ruta), `list-productos-vencidos`.
- PWA `transferencia-alta`.
- frc-mobile `nueva-transferencia`, `edit-transferencia-producto`.

Schema y firma iguales. Lo único nuevo para el cliente es que el save puede esperar unos segundos
detrás de un avance y, en la carrera, recibir el rechazo de etapa (que ya existía para pantallas
viejas). Desktop y PWA lo muestran por su genérico. frc-mobile muestra «Ups!! Algo salió mal» sin el
motivo y no emite ni `next` ni `error` (`generic-crud.service.ts:276-308`): la pantalla queda sin
refrescar, sin colgarse. Preexistente y en mantenimiento; no se toca.

## Fases

| Fase | Commit | Tests |
|---|---|---|
| 0 | `docs(transferencias): plan para serializar saveTransferencia con el avance` | — |
| 1 | `fix(transferencias): guardar el encabezado con la fila bloqueada` | ver abajo |

Tests (`TransferenciaGraphQLEtapaTest`):

- `saveTransferencia` de una existente lee con `lockById`, nunca con `findById`. Revertir a
  `findById` y ver que falla.
- `saveTransferencia` abre transacción (`PlatformTransactionManager.getTransaction` llamado).
- Carrera simulada: `findById` devuelve una copia con la etapa **vieja** (lo que leía el save antes)
  y `lockById` la **avanzada** (lo que ve al soltar el lock); el input trae la etapa vieja →
  `GraphQLException`, no guarda. Con dos instancias distintas el test falla con el código viejo (con
  una sola, como el helper `persistida`, pasaría igual: hallazgo del eje B).
- Los tests existentes del save (`saveNoPisaLaEtapaHaciaAtras`, `TransferenciaGraphQLSolicitanteTest`)
  ya mockean `lockById` y el `transactionManager`; tienen que seguir pasando.

Batería: `./mvnw clean verify -B -DskipFlyway=true` en una copia fuera de `dev-frc` (jdtls pisa
`target/` de los worktrees).

## Persistencia, clientes, filial

- **Migraciones**: ninguna. **Datos nuevos**: ninguno.
- **Filial**: `N/A para filial` porque desktop y PWA llaman al `saveTransferencia` del central
  (`servidor = true`) y el avance que se cruza es el del central.
- **Réplica**: `transferencia` no cambia de forma ni de dirección.

## Riesgos

- **Espera**: un save detrás de un avance largo (muchos ítems) espera lo que tarde el avance, con la
  conexión del pool tomada. Sin `lock_timeout`, igual que los otros locks del repo. La asignación de
  hoja de ruta de `list-transferencia` guarda una por una y se demora igual.
- **Deadlock**: el save con cambio de sucursal bloquea la transferencia y después los ítems, en el
  mismo orden que el avance. Un `saveTransferenciaItem` concurrente que inserta un ítem toma
  `FOR KEY SHARE` y espera. PostgreSQL abortaría uno en un ciclo; no se ve uno leyendo el código.
- **Rama vieja local** `fix/transferencia-bloquear-cambio-sucursal` (14/10, sin push, con cambios sin
  commitear en `saveTransferencia`): va a chocar al rebasarse. No es de esta rama.

## Prueba de runtime (central local propio, perfil dev, bodega local)

1. Transferencia de prueba clonada en `PRE_TRANSFERENCIA_ORIGEN` con ítems.
2. **Avance primero (bloqueante)**: fila tomada desde psql (`BEGIN; SELECT ... FOR UPDATE`), lanzar
   `avanzarEtapaTransferencia(PREPARACION_MERCADERIA)` y después `saveTransferencia` con etapa
   `PRE_TRANSFERENCIA_ORIGEN` y otra observación; soltar. Esperado: el avance `true`, el save
   rechazado; etapa `PREPARACION_MERCADERIA` y `usuario_preparacion_id` del avance.
3. **Save primero**: lo mismo en orden inverso. Esperado: el save guarda la observación, el avance
   pasa y la etapa queda `PREPARACION_MERCADERIA` con la observación nueva.
4. **Save con cambio de sucursal** (rearma movimientos dentro del lock) con la fila tomada desde
   psql y un `saveTransferenciaItem` de un ítem nuevo en paralelo: los dos esperan y terminan, sin
   deadlock; movimientos rearmados una vez.
5. Sin carrera: confirmar desde psql que el save toma `FOR UPDATE` (`pg_locks` / el SQL en el log).
6. Desktop (`ng serve -c web` o build web-production en puerto propio): cambiar observación,
   sucursales y solicitante; crear una transferencia nueva y su primer ítem; asignar hoja de ruta
   desde la lista. Todo guarda igual que antes.
7. Desktop con pantalla vieja: dos pestañas, avanzar en una, guardar el encabezado en la otra →
   aviso con el texto nuevo.
8. PWA (alta de transferencia) contra el central local: crea igual que antes.
9. Borrar los datos de prueba.

## Auditoría del plan (paso 5)

Dos auditores en paralelo, sin verse.

| Eje | Hallazgo | Decisión |
|---|---|---|
| A | Lista de llamadores completa (backend sin llamadores internos; desktop 5 sitios, PWA 1, frc-mobile 3); ninguno manda save en paralelo con avance, verificación, ítem u hoja de ruta | Confirma el alcance |
| A | El plan omitía que frc-mobile no muestra el motivo del rechazo | Verificado y corregido arriba; preexistente, no se toca |
| A | `list-transferencia` (asignar hoja de ruta): una transferencia con avance en curso demora el loop; el rechazo cuenta como «rechazada», sin cuelgue | Aceptado: es la espera del lock; anotado en Riesgos |
| A | `edit-transferencia:2033` no maneja el rechazo de la promesa (preexistente) | Fuera de alcance |
| A | El filial tiene su propio `saveTransferencia`, ningún cliente lo usa | Sostiene el N/A |
| B | **El test de carrera pasaba con el código viejo** (mismo objeto para `findById` y `lockById`) | **Corregido**: dos instancias distintas |
| B | SERIALIZABLE interno ignorado en silencio por `JpaTransactionManager` (sin `validateExistingTransaction`) | Igual que en `avanzarEtapa`; la exclusión la da el lock |
| B | Nada carga `Transferencia` antes del `lockById`; la entidad no tiene colecciones ni cascadas: el merge copia lo mismo que hoy | Confirma el punto 2 |
| B | Ninguna excepción tragada en el camino: rollback con el mensaje original, sin `UnexpectedRollbackException` | Sin cambios |
| B | Si el commit externo falla, la notificación de cambio de sucursal no sale (antes salía igual) | Es lo correcto |
| B | Deadlock con movimientos/lotes no verificado | Prueba 4 de runtime |

## Sin verificar todavía

- frc-mobile: no se levanta (en mantenimiento); su save es el mismo endpoint y espera la respuesta.
