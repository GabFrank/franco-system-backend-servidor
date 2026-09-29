# Plan — La lista de funcionarios no busca por nickname

Rama: `fix/personas-funcionarios-busqueda-nickname` (central, desde `origin/develop` `d05a64fd`,
2026-09-29). Pieza: **solo central**. Un PR.

## Qué se pide

En la lista de funcionarios del desktop (`personas/funcionarios/list-funcioario`) el input
«Nombre o nickname» solo encuentra por nombre de la persona. Buscar por nickname no trae nada.

## Causa (verificada en `origin/develop`)

- Desktop: `list-funcioario.component.ts:onFiltrar` manda el texto en mayúsculas como `nombre` a
  `FuncionarioService.onGetAllWithPage` → query `funcionariosWithPage(nombre: …)`
  (`funcionarios/graphql/graphql-query.ts:45`), cliente `servidor` (central).
- Central: `FuncionarioGraphQL.funcionariosWithPage` reemplaza espacios por `%` y llama
  `FuncionarioService.findAllWithPage` → JPQL `FuncionarioRepository.findAllWithFilterAndPage`,
  cuyo filtro de texto es **solo** `upper(p.nombre) like …`.
- El nickname no es columna del funcionario: es `personas.usuario.nickname`, del usuario cuya
  `persona` es la del funcionario. Es lo mismo que muestra la columna de la lista
  (`FuncionarioResolver.nickname` → `usuarioService.findByPersonaId`). La query nunca lo mira.
- Datos locales (`bodega@5551`): 0 personas con más de un usuario (lo garantiza el UNIQUE
  `usuario_un_persona`, `V0__initial_schema.sql:9514`); **18** funcionarios cuyo
  nickname no matchea el nombre ni con el truco de `%` (ej. id 110 «GUILLERMO FRANCO AREVALOS» /
  `MEMO`, id 281 «DENIS RAUL MORENO FERREIRA» / `PICHE`, id 177 «SIMÓN PERALTA MELGAREJO» /
  `SIMON PERALTA`). Son los casos de prueba.

## N/A

- **desktop**: N/A — ya manda el texto y el label ya dice «Nombre o nickname». Sin cambios de
  contrato GraphQL. **Segundo consumidor** (auditoría A): el diálogo «Buscar Funcionario» de
  `operaciones/venta/reportes/lucro-por-funcionario` (`onBuscarFuncionario`, línea 621) usa la
  misma query con `searchFieldName: "nombre"` y ya muestra la columna Nickname: el fix también
  lo arregla (devuelve más filas, no rompe nada). Entra en la prueba manual.
- **filial / mobile / mobile-pwa**: N/A — la lista consulta siempre al central (`servidor = true`);
  la firma de `funcionariosWithPage` no cambia.
- **Migraciones / replicación**: N/A — no cambia esquema; `personas.usuario` solo se lee.
- **Tabla de datos nuevos**: N/A — no nace ningún dato.

## Fase 1 — central (commit `fix(personas): buscar funcionarios tambien por nickname del usuario`)

1. `FuncionarioRepository.findAllWithFilterAndPage`: el bloque de `:nombre` pasa a

   ```
   (cast(:nombre as string) is null
     or upper(p.nombre) like concat('%', upper(cast(:nombre as string)), '%')
     or exists (select usr.id from Usuario usr where usr.persona = p
                and upper(usr.nickname) like concat('%', upper(cast(:nombre as string)), '%')))
   ```

   - `exists` en vez de `left join Usuario`: el UNIQUE `usuario_un_persona` ya impide que una
     persona tenga dos usuarios, así que el join tampoco duplicaría; `exists` se elige por
     defensa (no depende de esa constraint) y porque no toca el `select` ni el `count` derivado.
     EXPLAIN local (auditoría B): subplan hasheado, ~1 ms con 488 funcionarios.
   - El bloque entero va entre paréntesis: sin ellos, por precedencia AND > OR, el `or` se
     comería el resto de filtros (sucursal, activo, cargo…) sin error.
   - Mismo patrón de `like` que ya usa la query (el texto llega con espacios → `%`).
   - La query no declara `countQuery`: Spring Data lo deriva. Se verifica en runtime que
     `getTotalElements` sea coherente con el filtro (ver prueba).
2. **Tests.** Se comprueba que **los dos fallan con la query vieja** (revertir la JPQL, correr,
   restaurar).
   - `FuncionarioBusquedaNicknameIT` (patrón de `FuncionarioFiltroCobraBancoIT`, apagada por
     `-Dit.funcionario=true`, `@Transactional` con rollback): es la que **ejecuta la JPQL** y
     prueba la precedencia. Toma un funcionario con usuario, le pone al usuario un nickname
     único que no aparece en ningún nombre, `flush`, y verifica: buscarlo por ese nickname lo
     trae y `getTotalElements` = filas devueltas = 1 (count derivado coherente); el mismo texto
     con el filtro de **otra** sucursal da 0 (el `or` no se comió el resto de filtros); un
     nombre sigue encontrando. Se corre con perfil `dev` y
     `--replication.sync.enabled=false --replication.refresh.enabled=false` explícitos: una
     `@SpringBootTest` sin perfil prende los schedulers que llegan a las filiales reales.
   - `FuncionarioRepositoryBusquedaNombreTest` (reflexión sobre `@Query`, corre en CI): solo
     aserciones estructurales mínimas — el `value` contiene `exists`, `Usuario usr`,
     `usr.persona = p` y `usr.nickname`. No pretende probar precedencia (auditoría B: sería
     regex frágil); es la única red que corre en CI contra una reversión.
3. Gate: `./mvnw clean verify -B -DskipFlyway=true`, leído del log. Push de la rama.

## Fuera de alcance (anotado, no se toca)

- Acentos: «SIMON» no encuentra «SIMÓN» por nombre. Con este fix se encuentra por su nickname
  (`SIMON PERALTA`), pero la búsqueda sigue siendo sensible a tildes en general.
- `funcionariosSearch` (autocompletes, `findByPersonaNombre`) tampoco busca por nickname; no es
  lo pedido.

## Prueba (paso 9)

Central local 8081 perfil `dev` + desktop `npm run ng:serve`. En Personas → Funcionarios:
`MEMO` → sale id 110; `PICHE` → id 281; `SIMON PERALTA` → id 177; un nombre (`ALMIRON`) sigue
funcionando; `MEMO` + filtro de otra sucursal → vacío (el `or` no se comió los demás filtros);
input vacío → lista completa; el total de páginas coincide con las filas. Después, Reportes →
Lucro por funcionario → «Buscar Funcionario» con `MEMO` → aparece.

## Qué queda sin verificar

- Rendimiento sobre la base de producción: medido solo en la copia local (~1 ms, auditoría B).
  `personas.usuario` es chica (~500 filas); no hay motivo de escala.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A (media) | `lucro-por-funcionario` también consume `funcionariosWithPage` | verificado (`lucro-por-funcionario.component.ts:621`); anotado en N/A y agregado a la prueba |
| A (baja) | el test por reflexión no ejecuta la JPQL; count derivado sin probar | se agrega `FuncionarioBusquedaNicknameIT` que verifica `getTotalElements` |
| A (baja) | rendimiento sin medir | medido por B |
| B (media) | premisa falsa: «no hay constraint» — existe `usuario_un_persona` UNIQUE | verificado (`V0__initial_schema.sql:9514`); corregido, `exists` queda justificado como defensa |
| B (media) | test por reflexión frágil, no prueba precedencia | reducido a aserciones estructurales; la precedencia la prueba la IT |
| B (baja) | nulls / cast repetido | sin cambio: `nombre=null` corta en el primer término; nickname null no matchea; el cast repetido se valida al arrancar |
