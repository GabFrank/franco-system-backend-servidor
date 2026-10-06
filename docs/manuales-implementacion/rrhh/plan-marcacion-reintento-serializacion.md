# Plan — Reintentar el guardado de marcación ante un fallo de serialización

Rama: `fix/marcacion-reintento-serializacion` (desde `develop`). Solo central.
Sin migración, sin cambio de esquema GraphQL.

## Qué pasó

Bodega, 06/10/2026, 08:00:48. Un `saveMarcacion` desde la PWA (sucursal 13)
falló con:

```
ERROR: could not serialize access due to read/write dependencies among transactions
  Detail: Reason code: Canceled on identification as a pivot, during write.
  Hint: The transaction might succeed if retried.
SQLState: 40001  →  CannotAcquireLockException  →  al cliente: «could not execute statement; SQL [n/a]; …»
```

91 ms antes se había guardado otra marcación, desde el desktop y en **otra
sucursal** (la 0). El usuario vio el mensaje crudo, repitió todo —rostro
incluido— y al intento siguiente entró.

Frecuencia: una ocurrencia en el journal disponible (01/10 21:59 al 06/10).
No es urgente; por eso `fix/` desde `develop` y no `hotfix/`.

## Causa

`MarcacionService.save` (`MarcacionService.java:71`) corre en `SERIALIZABLE`
y, dentro de la transacción, lee `max(id)` de la sucursal
(`asignarNuevoId`, `:118`), valida contra las jornadas del día y guarda la
jornada —que también lee su `max(id)` (`JornadaService.java:116`)—. Postgres
cancela una de dos transacciones concurrentes cuando no puede garantizar un
orden serial; es el comportamiento documentado de ese nivel de aislamiento, y
**la aplicación tiene que reintentar**. Hoy nadie reintenta: el único
`@Retryable` del repo cubre obtener la conexión (`RetryableDataSource`).

El aislamiento no se toca: es lo que impide que dos marcaciones simultáneas
de la misma sucursal reciban el mismo id.

## Fase única — Reintento en el resolver, reconstruyendo la entidad

`MarcacionGraphQL.saveMarcacion`:

- El cuerpo actual pasa a un método privado que arma la entidad desde el
  input y llama a `service.save`. `saveMarcacion` lo invoca hasta **3 veces**.
- **Se reintenta desde el input, no con la misma entidad.** El intento fallido
  deja la entidad con el id que calculó; `prepararMarcacion` solo asigna id si
  viene nulo, así que reusar el objeto mandaría un id viejo que otra
  transacción pudo haber ocupado, y `repository.save` con id asignado es un
  *merge*: **pisaría la marcación de otra persona**. Cada intento arma una
  entidad nueva.
- **Va en el resolver y no con `@Retryable` sobre el servicio**: el reintento
  tiene que envolver a la transacción, y el orden entre el advice de retry y
  el de `@Transactional` sobre un mismo método no está fijado en este repo. El
  resolver no es transaccional, así que el bucle queda afuera sin depender de
  eso.
- **Qué se reintenta:** solo si en la cadena de causas hay una
  `SQLException` con SQLState `40001` (serialización) o `40P01` (deadlock).
  Se mira el SQLState y no la clase de Spring: `ConcurrencyFailureException`
  también cubre el lock timeout y el bloqueo optimista, que no son este caso.
  Se recorre la cadena porque `procesarJornada` envuelve lo que atrapa en un
  `RuntimeException` (`MarcacionService.java:171`), y porque el fallo puede
  saltar al hacer commit, envuelto en `TransactionSystemException` /
  `RollbackException`.
- **Qué no:** las validaciones («Ya registró entrada…», `IllegalStateException`)
  salen al primer intento, igual que hoy.
- Entre intentos, una espera corta y creciente (del orden de 50–150 ms). Si
  el hilo se interrumpe durante la espera, se restaura la interrupción y se
  corta sin reintentar.
- Agotados los intentos: una excepción propia que **implementa
  `GraphQLError`**, como `UnauthenticatedAccessException`. Un
  `GraphQLException` común llega al cliente con el prefijo «Exception while
  fetching data (/data) :» (`GraphqlExceptionHandler.java:34-41` solo
  desanida las que son `GraphQLError`). El mensaje: «No se pudo registrar la
  marcación porque se estaba guardando otra al mismo tiempo. Volvé a
  intentar.» Conserva la causa original.
- Un `WARN` por cada reintento, con usuario, sucursal e intento, para poder
  contarlos en el journal.
- **Un input que trae id** (edición desde el desktop) se reintenta igual:
  cada intento vuelve a cargar la marcación existente con `findById`.

Tests (`MarcacionGraphQLReintentoTest`, unitario con mocks, corre en el CI):

- falla una vez con `CannotAcquireLockException` y después guarda → devuelve
  la marcación; `service.save` recibió **dos entidades distintas**, las dos
  con id nulo al entrar. **Falla con el código viejo** (propaga la excepción).
- el 40001 llega envuelto en `RuntimeException` → también se reintenta.
- falla las 3 veces → `GraphQLException` con el mensaje legible; `save`
  llamado 3 veces, no más.
- `IllegalStateException` de validación → sale al primer intento, `save`
  llamado una vez, mensaje intacto.
- una excepción cualquiera (`NullPointerException`) → no se reintenta.
- el 40001 llega como en un commit fallido
  (`TransactionSystemException` → `RollbackException` → `SQLException`) → se
  reintenta.
- un 23505 (clave duplicada) → **no** se reintenta.
- un input con id → se reintenta y cada intento vuelve a buscar la existente.
- al agotarse, la excepción final es un `GraphQLError`, con el mensaje
  legible y la causa original.
- interrupción durante la espera → no hay otro intento y el hilo queda
  marcado como interrumpido.

## Datos nuevos

Ninguno.

## A quién le llega

- `saveMarcacion` lo usan la PWA, el desktop y `frc-mobile`. La firma, el
  input y el tipo devuelto no cambian. Lo único observable es que un fallo
  que antes salía ahora se resuelve solo, y que el mensaje final es otro.
- El filial tiene su propio `MarcacionService` con el mismo aislamiento. **No
  se toca en este plan**: no hay ninguna ocurrencia registrada ahí y sería
  otro repo, otro PR y otro alcance de despliegue (18 sucursales).
- Los otros guardados en `SERIALIZABLE` del central (transferencias,
  movimientos de stock) tienen el mismo hueco. Fuera de alcance.

## La sesión compartida entre intentos

`FrancoSystemsApplication.java:93-100` registra `OpenEntityManagerInViewFilter`:
los intentos de una misma request comparten `EntityManager`. Lo que hace que
el segundo intento arranque limpio es que `JpaTransactionManager` vacía ese
`EntityManager` al hacer rollback de una transacción que no lo creó. **Eso es
comportamiento de Spring, no algo que se vea en este repo, y los mocks no lo
prueban**: es el punto central de la prueba local de abajo.

## Qué queda sin verificar

- **El choque real no se reproduce con mocks.** Para verlo de verdad: central
  local con perfil `dev` y dos `saveMarcacion` lanzados a la vez en un bucle
  contra la base local, contando los `WARN` de reintento y que no quede
  ningún id repetido ni marcación pisada.
- Que el fallo en commit llegue con una de las formas que el detector
  reconoce, y que el segundo intento funcione con la sesión compartida: lo
  cubre esa misma prueba local, no el test unitario.
- **Una colisión de `max(id)+1` que salga como 23505** en vez de 40001 no se
  reintenta. Postgres da 40001 cuando la transacción leyó antes esa clave, y
  `repository.save` con id asignado hace un *merge* —lee antes de insertar—,
  así que debería salir siempre como 40001. Si en la prueba local aparece un
  23505, se documenta y se decide aparte; no se agrega a ciegas.

## Hallazgos de la auditoría del plan

| Hallazgo | Qué se hizo |
|---|---|
| Sesión compartida por request (OSIV) entre intentos | Verificado el filtro. Sección propia y punto central de la prueba local |
| `GraphQLException` llega con prefijo al cliente | Excepción que implementa `GraphQLError`, patrón ya usado en el repo |
| El detector por `ConcurrencyFailureException` reintenta de más | Solo SQLState 40001 / 40P01 |
| El 40001 en commit llega con otra envoltura | Test con `TransactionSystemException` anidada |
| Colisión de id que salga como 23505 | No se reintenta; anotado en «sin verificar» |
| El input puede traer id | Aclarado y con test |
| `Thread.sleep` e interrupción | Se restaura la interrupción y se corta |
| Efectos que no se revierten (push, eventos, listeners) | Revisado: no hay. Un reintento no duplica nada externo |
| Otros llamadores de `MarcacionService.save` | Revisado: solo el resolver |
| `reprocesarJornadaDeMarcacion` también es `SERIALIZABLE` | Fuera de alcance: acción manual de administración |
| El filial sigue mostrando el error crudo | Fuera de alcance; se anota en el PR |
| Reintento desde el cliente que duplique | PWA y `frc-mobile` no reintentan; el desktop no se pudo descartar. Un duplicado lo frena `validarEntrada`/`validarSalida` |
