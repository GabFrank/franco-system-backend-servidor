# Plan — Modificaciones del sistema: consulta unida de varios módulos (issue desktop #583)

## Problema

La pantalla «Modificaciones del sistema» del desktop, con el módulo `productos`, muestra registros de
`productos` y `operaciones`. El central solo pagina un esquema por vez (`modificacionesPorSchema`),
así que el desktop tiene que unir dos consultas. El arreglo del desktop (rama
`fix/modificaciones-sistema-paginacion`, fase 1) pide a cada esquema todas las filas hasta la
página N y corta en el cliente. Le quedan dos límites:

1. **Costo:** la página N trae `2 × (N+1) × pageSize` filas.
2. **Empates:** las consultas ordenan solo por `modificadoEn`. Con fechas iguales, Postgres puede
   devolver otro orden entre dos consultas, y una fila puede repetirse o faltar en el borde de una
   página. Esto también afecta a la paginación de un solo módulo y a la del filtro por tipo de
   entidad.

## Cambios

- `ModificacionRegistroRepository`:
  - consulta nueva `findBySchemaNombreInAndFechaBetween` (`schemaNombre IN :schemaNombres`);
  - `ORDER BY m.modificadoEn DESC, m.id DESC` en la nueva y en las tres paginadas que ya existen.
    Solo agrega un desempate: los clientes viejos reciben las mismas filas, en un orden fijo.
- `ModificacionGraphQL.modificacionesPorSchemas(schemaNombres, inicio, fin, page, size)`. Con la
  lista vacía devuelve una página vacía sin consultar, porque `IN ()` rompe en Postgres.
- `modificacion.graphqls`: query `modificacionesPorSchemas(schemaNombres: [String!]!, …)`.
  `modificacionesPorSchema` queda igual: la usan los desktops que todavía no se actualizaron.

Sin migración: el plan de Postgres usa `idx_modificacion_fecha` con orden incremental (1,2 ms en
la base local de 10 mil filas, con offset 300).

## Datos nuevos

N/A: no nace ningún campo ni columna. Lo único nuevo es una operación GraphQL. La escribe el
central y la lee el desktop (`ModificacionesService.onModificacionesPorSchemas`, fase 2 de la rama
del desktop).

## Tests

`ModificacionGraphQLPorSchemasTest` (unitario, repositorio simulado):

- pasa la lista y un `PageRequest(page, size)` al repositorio, y mapea la página;
- con la lista vacía no consulta y devuelve una página vacía.

El orden con desempate se prueba por pantalla contra el central local.

## Orden de despliegue

Primero el central. El PR del desktop que usa `modificacionesPorSchemas` se mergea recién cuando el
central de ese canal ya la tiene desplegada (el deploy del central es manual). Un desktop viejo
contra el central nuevo no cambia: sigue usando `modificacionesPorSchema`.

## Auditoría del plan (paso 5)

- **Eje A (contrato):** las tres queries y `modificacionesPorSchema` solo las usa la pantalla
  Modificaciones del desktop. No aparecen en mobile-pwa, mobile ni filial. `[String!]!` viaja bien
  por `onCustomQuery`. `IN :lista` con `Pageable` deriva bien la consulta de conteo. Riesgo: un
  desktop nuevo contra un central sin la query (deploy manual por instancia). → **Aplicado en el
  desktop:** si la query nueva falla, usa la unión en el cliente.
- **Eje B (estado):** sin migración ni datos; el rollback del JAR no deja nada colgado. Riesgo: el
  mismo, por el rollback automático de `deploy.sh`. → mismo respaldo. Además: el test con el
  repositorio simulado no valida el JPQL. → Spring Data valida los `@Query` al arrancar (un JPQL
  roto no levanta el central y `deploy.sh` vuelve atrás), y la prueba por pantalla lo ejercita.
  `page` negativa o `size` 0 ya rompían en las queries existentes: no se toca.

## Resultado de las pruebas

- `ModificacionGraphQLPorSchemasTest`: 3 tests verdes. Sin `m.id DESC`, el de orden falla.
- `./mvnw clean verify -B -DskipFlyway=true`: 2317 tests, 0 fallas.
- Por pantalla (desktop de la rama contra este central, local), PRODUCTOS del 1/9 al 23/10: un
  pedido `modificacionesPorSchemas` por página con `page` y `size` reales; 8 páginas, 120 de 120
  ids, en el mismo orden que `ORDER BY modificado_en DESC, id DESC` en la base.
- Desktop de la rama contra el alpha (sin esta query): la query nueva vuelve con `FieldUndefined`,
  el desktop cae a la unión en el cliente sin aviso de error; 7 páginas, 103 de 103.

## Fuera de alcance

- Control de rol en estas consultas: hoy solo piden sesión, igual que las que ya existen.

## Sin verificar

- Plan de ejecución contra la base de producción (la local es una copia de bodega).
