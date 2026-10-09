# Plan — quitar `password` del tipo `Usuario` (central y filial)

Issue #344. Rama `fix/personas-quitar-password-del-tipo-usuario`, con el mismo nombre en los dos
backends, desde `develop`. Este archivo cubre a los dos: el filial no lleva copia.

## Qué cambia

El relevamiento de la #344 dio que, de los 9 campos del tipo `Usuario`, el único que se puede
retirar es `password`. Los otros ocho los consume algún cliente.

| Repo | Archivo | Cambio |
|---|---|---|
| central | `src/main/resources/graphql/personas/usuario.graphqls` | quitar `password: String` del `type Usuario` |
| central | `src/main/resources/graphql/financiero/configuracion-facturacion.graphqls` | el comentario de `usuarioNickname` dice «el tipo Usuario expone password»: corregirlo |
| central | `src/test/java/com/franco/dev/graphql/TipoUsuarioSinPasswordTest.java` | test nuevo |
| filial | `src/main/resources/graphql/personas/usuario.graphqls` | quitar `password: String` del `type Usuario` |
| filial | `src/test/java/com/franco/dev/graphql/TipoUsuarioSinPasswordTest.java` | test nuevo |

No se toca: `UsuarioInput.password` (camino de escritura: alta, reseteo y cambio de contraseña),
la entidad `Usuario`, `UsuarioService`, ni nada de `security/`. Ningún resolver ni test existente
lee el campo del tipo: `UsuarioResolver` solo resuelve `roles` e `inicioSesion` en los dos repos.

## Quién pide el campo hoy

| Cliente | Habla con | ¿Pide `Usuario.password`? |
|---|---|---|
| desktop | central y filial | no — ni en `develop`, `release/beta`, `master`, ni en los tags 2.1.10, 3.0.8-stable, 3.5.0 y 4.5.0 |
| mobile-pwa | central | sí en `develop`, `release/beta` y `master`; quitado en GabFrank/frc-mobile-pwa#73, todavía sin mergear |
| APK `frc-mobile` | central | sí, en seis operaciones, incluida la query de login |

## Fases

**Fase 1 — filial.** Un commit, un push.
1. Quitar el campo del `type Usuario`.
2. `TipoUsuarioSinPasswordTest`: recorre todos los `.graphqls`, ubica cada bloque
   `type Usuario { … }` o `extend type Usuario { … }` y falla si alguno declara `password`.
   También falla si no encuentra ningún bloque, para que un renombre no lo deje en verde sin mirar
   nada. Se comprueba que falla con el schema viejo.
3. `./mvnw clean verify -B`, leído del log.

**Fase 2 — central.** Un commit, un push.
1. Quitar el campo y corregir el comentario de `configuracion-facturacion.graphqls`.
2. El mismo test.
3. `./mvnw clean verify -B -DskipFlyway=true`, leído del log.

**Prueba de runtime (antes de cualquier PR).** Levantar cada backend en local con perfil `dev` y
verificar por GraphQL: una consulta de usuario sin `password` responde; la misma pidiendo
`password` es rechazada por validación; `saveUsuario` con `password` en el input sigue aceptándose.
Con el desktop en `ng serve -c web`: lista de usuarios, alta y reseteo de contraseña.

## Migraciones y datos nuevos

N/A para central y filial porque no hay migración, columna, enum ni dato nuevo: solo se retira un
campo del schema GraphQL. La columna `personas.usuario.password` no se toca.

## Compatibilidad y orden de despliegue

Es la segunda mitad de la estrategia de 2 versiones de `CLAUDE.md` («Cambios en la API GraphQL»):
primero los clientes dejan de pedir el campo, después se quita.

- **Filial: sin condiciones.** Solo el desktop le habla, y no pide el campo. Un merge a `develop`
  sale solo a las filiales alpha en ≤15 min y reinicia el servicio; `release/beta` → 6 de
  farmacia; `master` → 18 de bodega.
- **Central: no se mergea hasta que** (a) el PR #73 de la mobile-pwa esté en el canal que
  corresponde a esa instancia —`develop` para alpha, `release/beta` para farmacia, `master` para
  bodega— y (b) la APK `frc-mobile` esté fuera de uso. Contra un central sin el campo, la APK no
  puede iniciar sesión.
- **La postergación de la PWA no tiene tope.** Una PWA sin actualizar pierde la búsqueda de
  usuarios (asignar chofer y solicitante en transferencias) contra un central sin el campo. El
  login no se ve afectado.
- Central y filial no dependen entre sí para este cambio: no hay tabla replicada ni llamada entre
  servidores que seleccione el campo.

## Rollback

Volver a la versión anterior restituye el campo. No hay estado que deshacer. Pero **ningún
rollback automático lo detecta**: el schema sin el campo levanta sano, y lo que falla es un cliente
que todavía lo pide.

- Central: `deploy.sh` solo mira `/actuator/health`. Hay que volver a mano, desplegando la versión
  anterior con el workflow Deploy.
- Filial: `check-update.sh` vuelve a subir la última release del canal en ≤15 min, así que volver
  atrás exige retirar la release o publicar una que restituya el campo.

## Tipo de commit

`fix(personas)`, sin `!`: el campo se retira cuando ningún cliente vigente lo pide, que es el caso
que `CLAUDE.md` contempla sin MAJOR. Si se decidiera mergear el central con la APK todavía en uso,
sería `fix!` y habría que coordinarlo como breaking change.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| La tabla de clientes omitía `develop` de la PWA: el central alpha tampoco puede recibirlo antes del #73 | A | Fila corregida |
| El filial no tiene condiciones: ni PWA ni APK le hablan, y las llamadas servidor a servidor no seleccionan el campo | A | Confirmado, sin cambios |
| Quitar un campo del schema con el getter todavía en la clase no rompe el arranque; no hay `extend type Usuario` | A | Confirmado, sin cambios |
| `ClienteResolver.password(Cliente)` es un resolver huérfano: `cliente.graphqls` no declara el campo | A | Fuera de alcance; anotado para otro PR |
| El health check no detecta este fallo; el rollback del filial se pisa solo en ≤15 min | B | Sección «Rollback» reescrita |
| El test de un solo archivo no vería un `extend type Usuario` futuro | B | El test recorre todos los `.graphqls` |
| Ningún `saveUsuario` de un cliente toma el `password` de lo leído: quitar el campo no cambia lo que se guarda | B | Confirmado, sin cambios |
| `fix` sin `!` es defendible solo si las dos condiciones del central se cumplen antes de su merge | B | Lo arbitra el usuario; ver «Tipo de commit» |

## Qué queda sin verificar

- Que no quede ninguna APK instalada en uso: es un dato operativo, no del código.
- Versiones del desktop anteriores a 2.1.10, si quedara alguna instalada.
- Flyway no corre en el CI del central; acá no aplica porque no hay migración.
