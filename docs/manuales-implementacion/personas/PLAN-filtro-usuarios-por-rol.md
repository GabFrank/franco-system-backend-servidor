# Plan — Filtro por rol (multi selección) en la lista de usuarios

Rama en los dos repos: `feature/usuarios-filtro-por-rol` (desde `origin/develop`, 2026-09-28).
Piezas: **central** y **desktop**. Un PR por repo; el del central se mergea primero.

## Qué se pide

En la lista de usuarios del desktop (`personas/usuarios/list-usuario`), un filtro por rol con
selección múltiple. Semántica decidida con Franco (2026-09-28): **se muestran los usuarios que
tienen al menos uno de los roles elegidos** (OR). Sin roles elegidos, la lista se comporta como
hoy.

## Estado actual (verificado en `origin/develop`)

- Desktop: `list-usuario.component.ts:onFiltrar` → `UsuarioService.onSearchConFiltros(texto, page, size)`
  → query `usuarioSearchPaginated(texto, page, size)` (`usuarios/graphql/graphql-query.ts`), cliente
  `servidor` (central).
- Central: `UsuarioGraphQL.usuarioSearchPaginated` → `UsuarioService.findbyIdOrPersonaPaginated` →
  JPQL `UsuarioRepository.findbyIdOrPersonaPaginated`. Si el texto es numérico y hay usuario con ese
  `persona_id`, devuelve solo ese (atajo).
- Roles del usuario: `personas.usuario_role` (`user_id` = dueño del rol, el que usa `findByUserId`;
  `role_id`). `usuario_id` es otra columna: hoy `UsuarioRoleGraphQL:77-78` le pone el mismo usuario.
  Entidad `UsuarioRole` (`user`, `role`, `usuario`).
- Opciones del filtro: `RoleService.onGetRoles()` del desktop (`configuracion/roles`) sin argumentos
  → `roles(page: null)` → `CrudService.findAll(null)` = todos los roles ordenados por id. Ya lo usa
  `adicionar-usuario-dialog`. No hace falta nada nuevo en el central para las opciones.

## N/A

- **filial**: N/A — la lista consulta siempre al central (`servidor = true` en `onSearchConFiltros`).
- **mobile / mobile-pwa**: N/A — ninguno usa `usuarioSearchPaginated` (grep en `frc-mobile-pwa/src`: 0).
- **Migraciones / replicación**: N/A — no cambia esquema. `personas.usuario_role` **sí** está en
  `central_pub` (`V0__initial_schema.sql:15152`), pero el plan solo la lee: no hace falta espejo.

## Fase 1 — central

1. `usuario.graphqls`: query **nueva**
   `usuarioSearchPaginatedPorRoles(texto: String, page: Int, size: Int, roleIds: [ID]): UsuarioPage`.
   Se crea aparte en vez de sumar un argumento a `usuarioSearchPaginated`: un desktop nuevo que
   mandara un argumento desconocido a un central viejo haría fallar la validación de toda la query.
   Con una query aparte, el desktop solo la usa cuando hay roles elegidos.
2. `UsuarioRepository`: `findbyIdOrPersonaAndRolesPaginated(texto, roleIds, pageable)` = la misma
   JPQL de hoy, con **todo el `where` de texto entre paréntesis**, más
   `and exists (select ur.id from UsuarioRole ur where ur.user = u and ur.role.id in :roleIds)`
   (idem en `countQuery`). `exists` evita filas duplicadas cuando el usuario tiene varios de los roles.
   **Solo parámetros nombrados** (`:texto`, `:roleIds` con `@Param`): el repo no tiene ningún caso
   que mezcle `?1` con `:nombre` (auditoría B). Los paréntesis son el punto de falla silenciosa:
   sin ellos, por precedencia AND > OR, el `exists` solo filtra la última condición y el filtro
   deja pasar casi todo sin error.
3. `UsuarioService.findbyIdOrPersonaPaginated(texto, page, size, roleIds)`: si `roleIds` es null o
   vacío → camino actual sin cambios. Si no → no toma el atajo de `persona_id` (devolvería el
   usuario sin mirar el rol) y usa la query nueva. La sobrecarga de 3 argumentos queda igual.
4. `UsuarioGraphQL.usuarioSearchPaginatedPorRoles(...)`.
5. **Tests** (`UsuarioServiceFiltroRolTest`, Mockito como `FuncionarioServiceCascadaEstadoTest`):
   - sin roles → llama `findbyIdOrPersonaPaginated` del repo (camino viejo);
   - con roles → llama la query nueva con los ids y el texto en mayúsculas;
   - con roles y texto numérico que matchea un `persona_id` → **no** usa el atajo;
   - lista vacía de roles = sin filtro.
   - **test de la anotación** (reflexión sobre `@Query`, patrón de
     `FuncionarioServiceCascadaEstadoTest:160`): el `value` y el `countQuery` de la query nueva
     tienen el bloque de `or` entre paréntesis antes del `and exists`. Es la única red automática
     contra el hallazgo A; se comprueba que falla quitando los paréntesis.
   La JPQL en sí no la cubre ningún test unitario (no hay contexto Spring en CI): se valida con el
   arranque local (Hibernate valida las `@Query` al levantar) y la prueba manual.
6. Gate: `./mvnw clean verify -B -DskipFlyway=true`, leído del log.

## Fase 2 — desktop

1. `usuarios/graphql/graphql-query.ts`: `usuariosSearchPaginatedPorRoles` (mismos campos que
   `usuariosSearchPaginated`) + clase `graphql/usuarioSearchPaginatedPorRoles.ts`.
2. `UsuarioService.onSearchConFiltros(texto, pageIndex, pageSize, servidor = true, roleIds?)`:
   `roleIds` va **después** de `servidor`, para no correr el 4.º parámetro que comparte con
   `ClienteService`/`SucursalService` (auditoría B). Con `roleIds` no vacío usa la query nueva;
   si no, la de hoy.
3. `list-usuario`:
   - `rolesControl = new FormControl<number[]>([])` y `roleList` cargada una vez con
     `RoleService.onGetRoles()` (dentro del `setTimeout` del `ngOnInit`, porque `RoleService`
     arma su `genericCrud` en un `setTimeout`), ordenada por nombre;
   - `mat-select multiple` en la barra de filtros, con `mat-select-trigger` que muestra el primer
     rol y `(+N otros)` (patrón de `list-productos-vencidos`);
   - filtra al **cerrar** el selector (`(openedChange)` con `false`) y vuelve a la página 0 — no en
     `valueChanges` (regla del repo). También filtra con el botón Filtrar de `app-generic-list`;
   - `resetFiltro` limpia los roles.
   - si `onGetRoles` falla con error GraphQL, `onGetAll` muestra el snackbar pero no emite
     (`generic-crud.service.ts:110-119`, preexistente): el selector queda vacío y se ve
     deshabilitado con `roleList.length === 0` precalculado, sin colgar la pantalla.
   - Nada de funciones en el HTML: el texto del trigger se calcula en el componente al cerrar.
4. Gate: `npm run check` (AOT), leído del log.

## Tabla de datos nuevos

| Dato | Quién lo escribe | Quién lo lee |
|---|---|---|
| argumento `roleIds` de `usuarioSearchPaginatedPorRoles` | `ListUsuarioComponent.rolesControl` → `UsuarioService.onSearchConFiltros` | `UsuarioGraphQL` → `UsuarioService` → `UsuarioRepository.findbyIdOrPersonaAndRolesPaginated` |

No nace ninguna columna ni configuración.

## Seguridad

La lista de usuarios ya es visible para cualquier usuario logueado que abra la pantalla (control de
menú en el desktop, sin control por rol en el backend — issue #177). El filtro solo **reduce** lo
que ya devuelve `usuarioSearchPaginated`; la query nueva expone exactamente los mismos campos. No
agrega superficie.

## Despliegue

El orden de **merge** no alcanza (auditoría A): mergear el central a `develop` **no despliega**
(`deploy-auto.yml` nunca corre; el deploy es `workflow_dispatch` manual), mientras que mergear el
desktop a `develop` publica el instalador alpha solo y los clientes alpha se actualizan en ≤5 min.
Gate por canal:

1. PR central → merge → **Deploy del central a `alpha` en `success`** (health check).
2. Recién ahí merge del PR del desktop a `develop`.
3. Igual en cada promoción: el central de `beta` / `farmacia` / `bodega` desplegado **antes** de
   promover el desktop a `release/beta` / `master`.

Aun así, la URL del central la fija la config local de cada instalación, no el canal: un desktop
nuevo contra un central viejo sigue funcionando mientras no se elijan roles; con roles elegidos da
un error GraphQL visible (snackbar), no un dato mal filtrado.

## Qué queda sin verificar y cómo

- JPQL real contra la base: arranque local del central (perfil `dev`, 8081) + prueba manual en el
  desktop (`ng serve -c web`) con 2 roles que tengan usuarios distintos y uno en común.
- Rendimiento del `exists`: `usuario_role` es chica; se mira el log de `SlowQuerySessionEventListener`
  (>500 ms) en la prueba.

## Auditoría del plan (paso 5, 2026-09-28)

| Eje | Hallazgo | Severidad | Qué se hizo |
|---|---|---|---|
| A | El orden de merge no fija el orden de deploy: el central se despliega a mano, el desktop alpha sale solo | media | Gate por canal en «Despliegue» |
| A | `usuario_role` está publicada a filiales | informativo | Aclarado en N/A: solo lectura, sin espejo |
| B | Sin paréntesis en el `where`, el filtro por rol deja pasar casi todo sin error | alta | Explícito en fase 1 + test de la anotación |
| B | Mezclar `?1` con `:roleIds` no tiene precedente en el repo | media | Query nueva solo con parámetros nombrados |
| B | `roleIds` como 4.º parámetro pisaba `servidor` en `onSearchConFiltros` | media | Va después de `servidor` |
| B | `onGetAll` no emite si hay error GraphQL (preexistente) | baja | Selector deshabilitado sin roles; se prueba a mano |
| B | Binding `[ID]` → `List<Long>`, `role` nulo, `ORDER BY` duplicado | sin riesgo | Verificado con precedentes (`FacturaLegalGraphQL:258`) |

## Auditoría del diff (paso 8, 2026-09-28)

Tres fijos; ningún condicional disparado (el diff no toca release ni replicación).

| Eje | Hallazgo | Severidad | Qué se hizo |
|---|---|---|---|
| Fijo 1 | Sin riesgo nuevo: la query nueva expone los mismos campos que `usuarioSearchPaginated` | — | — |
| Fijo 1 | Preexistente: `Usuario.password` pedible por GraphQL y búsqueda de usuarios sin control por rol (#177) | fuera de alcance | Avisado a Franco |
| Fijo 1 | Invertir la búsqueda («usuarios con rol ADMIN») es más cómodo, pero el dato ya era obtenible (`Usuario.roles`, `usuarioRolePorUsuarioId`) | baja | Aceptado |
| Fijo 2 | Sin riesgo nuevo; JPQL ↔ entidades verificado | — | — |
| Fijo 3 | `ur.user` vs `ur.usuario`, nombres gemelos | baja | Comentario en la `@Query` |
| Fijo 3 | `resetFiltro` depende del `valueChanges` de `buscarControl` | baja | Rechazada la llamada explícita (duplicaría la búsqueda); comentario |
