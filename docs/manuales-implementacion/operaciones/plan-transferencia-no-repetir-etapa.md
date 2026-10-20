# Plan — una transferencia no repite la etapa en la que ya está (issue #450)

Rama: `fix/transferencia-no-repetir-etapa` (desde `develop` 5338d451). Pieza: central.

## Qué pasa hoy

`avanzarEtapaTransferencia` acepta como destino la etapa actual (`puedeAvanzarA` usa `>=` y
`validarAvanceDeEtapa` solo frena retrocesos) y vuelve a correr el `case` entero. Tres etapas pisan
lo ya verificado:

| Etapa reenviada | Qué pisa |
|---|---|
| `PREPARACION_MERCADERIA` | cantidad, presentación y vencimiento de preparación ← pre-transferencia |
| `TRANSPORTE_VERIFICACION` (`iniciarVerificacionTransporte`) | cantidad, presentación, vencimiento y motivos de transporte ← preparación |
| `RECEPCION_EN_VERIFICACION` | cantidad, presentación, vencimiento y motivos de recepción ← transporte |

Las demás pisan el responsable (`PRE_TRANSFERENCIA_ORIGEN`) o no hacen nada nuevo. En los tres casos
el movimiento de stock se rearma con la cantidad pisada (`createMovimientoFromTransferenciaItem`
reutiliza el existente).

Dos caminos llevan ahí:

1. **Pantalla sin refrescar**: el segundo usuario aprieta el botón después de que el primero avanzó.
2. **Clics casi simultáneos**: los dos requests leen la etapa anterior antes de que el otro guarde.
   El método no corre en una transacción y `Transferencia` no tiene `@Version` ni bloqueo, así que
   un chequeo `destino == actual` solo no alcanza: los dos lo pasan.

`verificarParaTransporteMobile` ya exige `PREPARACION_MERCADERIA_CONCLUIDA` (cubre el caso 1), pero
tiene la misma carrera del caso 2 y además crea una hoja de ruta por llamada: dos clics simultáneos
dejan dos hojas.

## Qué se hace

1. `TransferenciaRepository.lockById(id)`: `@Lock(PESSIMISTIC_WRITE)`, mismo patrón que
   `RetiroRepository.lockByIdAndSucursalId`. `TransferenciaService.lockById` lo expone.
2. `avanzarEtapaTransferencia` corre entero dentro de un `TransactionTemplate` (como
   `verificarParaTransporteMobile`) y carga la transferencia con `lockById`. El segundo request
   espera al primero, lee la etapa ya avanzada y se rechaza.
3. Nuevo chequeo en `avanzarEtapaTransferencia`, y solo ahí: si `destino == actual`,
   `GraphQLException` «La transferencia N ya esta en etapa X: la avanzo otro usuario o un intento
   anterior. Volve a abrirla para ver como quedo.». Vale para **todas** las etapas, no solo las
   cinco que lista la issue: ningún cliente manda la etapa actual (desktop
   `edit-transferencia.component.html`, PWA `etapas.ts`/`asignar-chofer-dialog`, frc-mobile
   `info-transferencia.component.html`: cada botón manda la siguiente), y así tampoco se pisa el
   responsable de `PRE_TRANSFERENCIA_ORIGEN`.
4. `verificarParaTransporte` (mobile) carga con `lockById` en vez de `findById`.
5. **No se toca** `puedeAvanzarA` ni `saveTransferencia`: el save reenvía la etapa actual en cada
   guardado y tiene que seguir aceptándola.

### Por qué la transacción no cambia lo que se guarda

El central registra `OpenEntityManagerInViewFilter` (`FrancoSystemsApplication.OpenFilter`): todo el
request ya comparte un EntityManager. Hoy la transferencia del resolver y `ti.getTransferencia()`
son la misma instancia, y lo que `PREPARACION_MERCADERIA_CONCLUIDA` copia sin `save` se escribe al
commitear el `service.save` final. Con el `TransactionTemplate` el EntityManager es el mismo; cambia
que todo es una sola transacción (si falla un ítem no queda la etapa a medias) y que la fila queda
bloqueada hasta el commit.

Los `@Transactional(isolation = SERIALIZABLE)` de `TransferenciaService.save` y
`TransferenciaItemService.save` se unen a la transacción externa (READ COMMITTED). Es lo mismo que
ya pasa en `verificarParaTransporteMobile`. La exclusión la da el bloqueo de fila, no el
aislamiento.

## Fases

Una sola fase de código (chica) más este plan.

| Fase | Commit | Tests |
|---|---|---|
| 0 | `docs(transferencias): plan para no repetir la etapa actual` | — |
| 1 | `fix(transferencias): rechazar reenviar la etapa actual y serializar el avance` | ver abajo |

Tests (`TransferenciaGraphQLEtapaTest`, `TransferenciaGraphQLChoferTransporteTest`,
`TransferenciaGraphQLSolicitanteTest`):

- `avanzarEtapaTransferencia` con destino == actual, **parametrizado por las 9 etapas**: lanza
  `GraphQLException` y no guarda ni la transferencia ni ítems ni movimientos. Revertir el chequeo y
  ver que falla.
- `avanzarEtapaTransferencia` carga con `lockById` (no `findById`) y abre transacción.
- `verificarParaTransporteMobile` carga con `lockById`.
- Los tests existentes que mockean `findById` para avanzar se pasan a `lockById` y reciben un
  `PlatformTransactionManager` mock (si no, `TransactionTemplate` da NPE).

Batería: `./mvnw clean verify -B -DskipFlyway=true` (en copia fuera de `dev-frc` si jdtls pisa
`target/`).

## Persistencia, clientes, filial

- **Migraciones**: ninguna. **Datos nuevos**: ninguno.
- **Contrato GraphQL**: sin cambios de schema. Lo único nuevo es un rechazo más. El desktop lo
  muestra por el genérico (`erroresDeRechazo`); la PWA por `datos.mutar`; frc-mobile por su
  manejador de error. Ninguno recarga solo tras un rechazo: el usuario tiene que reabrir (el mensaje
  lo dice).
- **Filial**: `N/A para filial` porque su `avanzarEtapaTransferencia` es una versión vieja que no
  copia cantidades, y desktop/PWA llaman al del central (`servidor = true`).
- **Réplica**: `transferencia`/`transferencia_item` no cambian de forma ni de dirección.

## Riesgos

- **Bloqueo más largo**: la fila queda `FOR UPDATE` mientras se procesan los ítems. Un
  `saveTransferenciaItem` que **inserte** un ítem de esa transferencia (toma `FOR KEY SHARE` por la
  FK) espera a que termine. Espera, no falla. Un `UPDATE` de ítem que no cambia la FK no se bloquea.
- **Deadlock posible pero raro**: avanzar bloquea la transferencia y después los ítems; un
  `saveTransferenciaItem` concurrente bloquea un ítem. PostgreSQL lo detecta y aborta uno con
  error, que el cliente muestra.
- **Reintento tras corte de red**: si el primer avance se aplicó y el cliente reintenta, ahora
  recibe «ya esta en etapa X» en vez de volver a pisar. Es lo buscado; el mensaje lo explica.

## Prueba de runtime (central local, perfil dev, 8081)

1. Transferencia de prueba en la base local, llevada a `PREPARACION_MERCADERIA` con ítems; cambiar
   a mano una `cantidad_preparacion` (como si la hubieran verificado).
2. **Pantalla sin refrescar** (desktop `ng serve -c web`): abrir la transferencia en dos pestañas
   en `PRE_TRANSFERENCIA_ORIGEN`, «Iniciar preparación» en una, verificar un ítem, apretar el mismo
   botón en la otra → aviso de rechazo, la cantidad verificada no cambia.
3. **Simultáneos (bloqueante)**: dos `avanzarEtapaTransferencia` en paralelo por curl con el mismo
   destino → uno `true`, el otro rechazo; cantidades y movimientos una sola vez.
4. Igual para `RECEPCION_EN_VERIFICACION` y para `TRANSPORTE_VERIFICACION` (desktop «Continuar sin
   chofer» y PWA con chofer: dos `verificarParaTransporteMobile` en paralelo → una sola hoja de ruta).
5. Avance normal de punta a punta (creación → recepción concluida) sigue andando y mueve stock igual
   que antes.
6. Restaurar/borrar los datos de prueba.

## Auditoría del plan (paso 5)

Dos auditores en paralelo, sin verse. Qué se hizo con cada hallazgo:

| Eje | Hallazgo | Decisión |
|---|---|---|
| A | Ningún cliente (desktop, PWA, frc-mobile) manda la etapa actual; ningún otro llamador en el backend | Confirma el chequeo para todas las etapas |
| A | Sin tope de espera del lock: el segundo request espera sin límite | **No se agrega `lock_timeout`.** Los locks existentes (`RetiroRepository`, `EntradaVariaRepository`) tampoco lo tienen, y la espera la acota lo que tarda el primero en terminar: después pasa y se rechaza enseguida |
| A | Tras el rechazo ningún cliente recarga; frc-mobile muestra «algo salió mal» sin el texto | Fuera de este PR (es de los clientes). Anotado como pendiente |
| A + B | `lockById` devuelve la instancia ya cargada sin refrescar si algo la cargó antes en el request | Hoy no pasa (ningún aspecto carga `Transferencia`). `lockById` reemplaza a `transferencia(id)`, no se suma; comentario en el repositorio |
| B | La explicación OSIV / transacción se sostiene; listeners son `@Async @TransactionalEventListener(AFTER_COMMIT)`: las notificaciones salen después del commit externo | Sin cambios |
| B | Deadlock entre dos transferencias distintas por el desglose de lotes (FEFO) | **No se reproduce leyendo el código**: `limpiarDesglose`/`reemplazarDesglose` borran e insertan solo las filas del movimiento propio; no se actualiza ninguna fila compartida. Queda el riesgo de abajo |
| B | FEFO de dos transferencias en paralelo puede asignar el mismo lote | Existe hoy (READ COMMITTED). La transacción más larga agranda un poco la ventana. Residual, anotado |
| B | Los tests unitarios no prueban la carrera | La prueba 3 de runtime (dos avances simultáneos) es **bloqueante** para dar el fix por terminado |
| B | `saveTransferencia` no toma el lock y puede pisar la etapa con un input viejo del mismo valor | Residual, fuera de alcance (la issue pide no tocar el save) |
| B | Datos ya pisados en producción | No se pueden distinguir de ítems sin modificar (cantidad igual a la de la etapa anterior es lo normal). No se corrige nada |

## Residuales (fuera de este PR)

- Clientes: recargar la transferencia tras el rechazo; frc-mobile no muestra el texto del error.
- `saveTransferencia` sin lock (read-modify-write con `ModelMapper`).
- Asignación FEFO concurrente del mismo lote entre transferencias distintas.

## Sin verificar todavía

- Deadlock bajo carga real: solo por lectura.
