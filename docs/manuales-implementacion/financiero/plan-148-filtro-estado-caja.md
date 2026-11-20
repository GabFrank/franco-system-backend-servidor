# Plan — filtro por `pdv_caja.estado` en el central (espejo de filial #148)

## Problema

`pdv_caja.estado` no lo escribe ningún componente: NULL en todas las cajas (filial #148; el propio
`PdvCajaRepository.findCajerosConCajaAbiertaBySucursalId` lo documenta). Tres consultas del central
filtran por esa columna y con un estado no nulo devuelven 0 filas sin avisar:

1. `PdvCajaRepository.findAllWithFilters` (JPQL, `c.estado = :estado`) — `cajasWithFilters`.
2. `PdvCajaService.findAllForAnalisisDiferencias`, `Specification` (`cb.equal(root.get("estado"), estado)`)
   — `cajasAnalisisDiferencias` sin filtro de diferencia.
3. `PdvCajaRepository.findAnalisisDiferencias` (nativa, `CAST(c.estado AS text) = :estado`) — el
   camino filtrado de #609.

Hoy ningún cliente lo manda: `list-caja` tiene `estadoControl` sin selector; `analisis-diferencia`
manda `estadoBackend`, que solo es no nulo si la opción elegida es un valor de `PdvCajaEstado`, y sus
opciones son `TODAS`/`SIN_DIFERENCIA`/`CON_DIFERENCIA`; `adicionar-caja-dialog` manda `null`. La PWA
no usa ninguna de las dos queries.

Premisa en el central (eje A): `bodega@5551` local, 51.196 cajas, todas NULL (hasta 2026-09-27).
`savePdvCaja`/`savePdvCajaPorSucursal` mapean el input entero y `PdvCajaInput` tiene `estado`, pero
ningún cliente le asigna un valor: el `toInput` del desktop reenvía lo que vino (NULL) y la PWA solo
lo muestra. Producción **no verificada** (el SSH de solo lectura lo rechazó el clasificador); no
cambia la decisión, porque ningún cliente manda el filtro. También llama a `cajasWithFilters`
`ultimas-cajas-dialog` (POS), con `estado` null y `servidor=false` (va al filial).

## Decisión (Franco, 2026-11-20)

La misma que en el filial (PR filial #275): **rechazar con error** un `estado` no nulo; el argumento
queda en el esquema porque el desktop lo declara.

## Fase 1 — `fix(caja)`

- `PdvCajaService.findAllWithFilters` y `findAllForAnalisisDiferencias`: si `estado != null`,
  `GraphQLException` («El filtro por estado de caja no esta soportado: use verificado.») como primera
  línea, antes de cualquier consulta.
- Quitar `estado` del JPQL de `findAllWithFilters` y de la nativa `findAnalisisDiferencias` (condición
  y parámetro), del `Specification`, de `analisisDiferenciasFiltrado` y del atajo
  `findCajasByAnteriorIdOptimized` (`estado == null` sobra).
- **Se mantienen** los argumentos de `cajasWithFilters` y `cajasAnalisisDiferencias` en
  `pdv-caja.graphqls` y las firmas de los resolvers y del service.
- Tests:
  - `PdvCajaServiceFiltroEstadoTest` (Mockito): los dos métodos rechazan con estado sin tocar el
    repositorio; `findAllWithFilters` sin estado delega con los argumentos en orden. El rechazo tiene
    que fallar con el código viejo (con el viejo da NPE en `cajaTurnos.zona()` / `page.getContent()`:
    tipo distinto, `assertThrows` igual queda en rojo). `CajaTurnos` mockeado para la delegación.
  - `AnalisisDiferenciasQueriesIT`: el caso «estado de la caja (enum)» pasa a esperar el rechazo;
    `estadoSql` sigue la firma nueva de la nativa (sin eso no compila `src/test` entero).
  - Correr los tres IT contra la bodega local (sin migraciones pendientes al 2026-11-20):
    `-Dit.analisisDiferencias=true -Dit.caja=true` (`CajasWithFiltersActivoIT`/`TurnoIT` cubren
    `findAllWithFilters` sin estado contra la base real; hallazgo del eje B).

Sin migración, sin enum nuevo, sin cambio de esquema GraphQL.

## Tabla de datos nuevos

N/A: no nace ningún dato.

## Pruebas

- `./mvnw clean verify -B -DskipFlyway=true` (copia fuera de `dev-frc`).
- Runtime: central de la rama en 8081 (perfil `dev`, `application-user-dev.properties` copiado),
  desktop `develop` estático en 4201 apuntado a él.
  - Financiero → **Lista de cajas** (`cajasWithFilters`, `servidor=true`): sin cambios.
  - Financiero → **Análisis de diferencias**, las dos pestañas: «Todas» (Specification) y
    «Con diferencia» (nativa): sin cambios.
  - Con estado: GraphQL directo a las dos queries → mensaje del rechazo. En el central
    `GraphqlExceptionHandler` no desenvuelve `graphql.GraphQLException`: el texto llega con el
    prefijo «Exception while fetching data (…) :» (eje B); verificarlo y aceptarlo, es como el resto.

## Despliegue

Merge a `develop` → release alpha; deploy manual por instancia. Sin migración: rollback = jar anterior.
Independiente del PR filial #275 (ninguno depende del otro).
