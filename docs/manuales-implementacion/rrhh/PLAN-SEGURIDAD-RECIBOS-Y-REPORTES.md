# PLAN — Control de acceso en recibos por id y reportes de RRHH (issue #346)

> Plan de trabajo. Se borra en el PR final; lo que sobrevive se muda a
> `ESTADO-IMPLEMENTACION-RRHH.md`.

- Rama: `fix/rrhh-recibos-y-reportes-control-de-acceso`, desde `origin/develop` (`7e8586f2`).
- Pieza: **solo central**. Filial no expone estos resolvers; desktop, PWA y Android no cambian.
- Sin migración, sin cambio de schema GraphQL, sin variables de entorno.

## Problema

`SecurityGraphQLAspect` solo exige sesión (#177). Doce queries de RRHH no llaman a
`RrhhSecurityService`: cualquier usuario autenticado baja el recibo de otro funcionario
recorriendo ids, o la nómina entera.

## Regla que se implementa

| Query | Regla |
|---|---|
| `reporteNominaMes`, `reporteResumenIps`, `reporteValesPendientes`, `reportePrestamosActivos`, `reporteAguinaldoAnual` | `seg.requireVer()` |
| `imprimirReciboVale`, `imprimirReciboPenalizacion`, `imprimirReciboAguinaldo`, `imprimirReciboPrestamo`, `imprimirReciboBono`, `imprimirReciboFinal` | `seg.requireVer()` |
| `imprimirReciboLiquidacion` | rol RRHH (`seg.hasAnyRole(seg.TODOS)`, con bypass ADMIN) **o** dueño de una liquidación `PAGADA` |

Decisión de Franco (2026-10-09): la regla del dueño va **solo** en `imprimirReciboLiquidacion`,
que es el único recibo que pide un cliente sin rol (PWA, `mi-trabajo/mi-trabajo.page.ts:319`). La
issue proponía «rol o dueño» en los siete.

Detalles de la regla del dueño:

- Dueño = la persona del usuario es la persona del funcionario de la liquidación. Nunca
  `funcionario.usuario_id` (es auditoría). Se resuelve en **una sola consulta** del repositorio
  (`existsByIdAndEstadoAndFuncionarioPersonaId`): sin navegar relaciones LAZY, sin comparar
  `Long` en Java y con el mismo costo para «no existe», «ajena» y «no pagada».
- Es equivalente al autoservicio: `misRecibosMobile` resuelve el funcionario por persona y filtra
  `PAGADA`, y `persona_id` es UNIQUE en `funcionario` y en `usuario`
  (`V0__initial_schema.sql:9382,9518`). Comentario cruzado en ambos métodos para que no diverjan.
- Solo estado `PAGADA`: es lo que lista `misRecibosMobile`. Sin esto el funcionario bajaría su
  propio borrador probando ids.
- El rechazo usa **el mismo mensaje** para «no existe», «no es tuya» y «no está pagada», para
  no dar un oráculo de ids.
- Usuario sin persona, o liquidación sin funcionario/persona → rechaza.
- **Decisión anotada**: `currentUsuario()` no mira `activo`. Un funcionario dado de baja sigue
  bajando sus recibos pagados mientras su token valga (`activo` solo se chequea en login y
  refresh). Se deja así: son sus propios recibos y es el comportamiento de todo `seg.*`.

## Fase 1 — código y tests (un commit `fix(rrhh)`)

Archivos:

1. `service/rrhh/RrhhSecurityService.java` — `currentPersonaId()`: id de la persona del usuario
   autenticado, o `null`.
2. `repository/rrhh/LiquidacionSueldoRepository.java` —
   `existsByIdAndEstadoAndFuncionarioPersonaId(Long id, LiquidacionSueldoEstado estado, Long personaId)`.
3. `service/rrhh/LiquidacionSueldoService.java` — `esReciboPagadoDe(Long liquidacionId, Long personaId)`:
   `false` si alguno es `null` (ojo: `CrudService.findById(null)` devuelve `null`, no
   `Optional.empty()`), si no delega en el repositorio con `PAGADA`.
4. `graphql/rrhh/LiquidacionSueldoGraphQL.java` — `imprimirReciboLiquidacion`: si no tiene rol y
   `!service.esReciboPagadoDe(id, seg.currentPersonaId())` → `GraphQLException`. El chequeo va
   **antes** de `generarBase64`, que tira «Liquidacion no encontrada».
5. `graphql/rrhh/ReporteRrhhGraphQL.java` — `seg.requireVer()` como primera línea en los 11
   métodos; se actualiza el comentario de `imprimirActaAdvertencia` («el resto no gatea»).
6. `service/rrhh/RrhhMobileService.java` — solo el comentario cruzado en `misRecibos`.

Tests (Mockito puro, patrón `ChequeYChequeraGraphQLSeguridadTest`):

- `ReporteRrhhGraphQLSeguridadTest`: sin rol, los 11 rechazan sin tocar `ReporteRrhhService`;
  con rol, delegan.
- `LiquidacionSueldoGraphQLReciboSeguridadTest`: con rol pasa y **no** consulta propiedad
  (`verify(service, never()).esReciboPagadoDe`); dueño sin rol pasa; ajeno sin rol rechaza y
  **no** genera el recibo (`verify(reciboLiquidacionService, never())`); usuario sin persona
  rechaza. Ids mayores a 127.
- `LiquidacionSueldoServiceReciboPropioTest`: nulls → `false` sin tocar el repositorio; si no,
  delega con `PAGADA`.
- `RrhhSecurityServiceTest`: `currentPersonaId()` con usuario con persona, sin persona y sin sesión.
- **Rojo con el código viejo** (ciclo, paso 7): se revierten **solo los dos resolvers** (revertir
  todo daría error de compilación, no rojo). Tienen que fallar los rechazos de los 11 y el
  «ajeno» y «sin persona» de la liquidación. Los casos «con rol pasa» y «dueño pasa» son de no
  regresión: quedan verdes con el código viejo, y se anota así.
- La consulta derivada del repositorio no la cubre ningún test Mockito ni el CI (no levanta
  contexto): la valida el arranque local y la prueba de runtime.

Gate: `./mvnw clean verify -B -DskipFlyway=true`, salida leída del log.

## Fase 2 — documentación (un commit `docs(rrhh)`)

- `ESTADO-IMPLEMENTACION-RRHH.md`: quitar la nota «Pendiente (seguridad, deuda previa)» (hoy
  línea 594) y corregir «los recibos por-id … no se gatean» (línea 667) con la regla nueva.
- `PLAN-TESTEO-MANUAL-RRHH.md`: caso de prueba del autoservicio sin rol.
- Borrar este archivo en el commit final.

## Datos nuevos

Ninguno: no nace campo, columna, clave de config ni bandera.

## Prueba de runtime (local, antes de pedir el PR)

Central con perfil `dev` en :8081 contra `bodega@5551`, por GraphQL directo:

1. Usuario con rol RRHH: los 12 responden.
2. Usuario **sin** rol y con persona de funcionario: `imprimirReciboLiquidacion` de su
   liquidación pagada responde; la de otro, la suya no pagada y un id inexistente rechazan con el
   mismo mensaje; los otros 11 rechazan.
3. PWA local (`npm start`, :4300) contra :8081 con ese usuario: «Mi trabajo → recibos» abre el
   PDF. Con la lista ya cargada, anular esa liquidación y tocar el recibo: tiene que salir el
   aviso de error (`verRecibo` no tiene try/catch, `mi-trabajo.page.ts:319`).
4. Desktop con un usuario solo de tesorería (caja virtual → «Ir a Vales (RRHH)») y otro solo con
   `VER_FUNCIONARIOS` (legajo): no aparece ningún botón de impresión que quede roto.

## Clientes revisados (auditoría eje A)

- **Desktop**: las 12 queries se llaman solo desde `src/app/modules/rrhh/`, igual en `develop`,
  `release/beta` y `master`. Dos entradas llegan sin rol RRHH y ninguna se rompe de nuevo:
  `caja-virtual-dashboard.component.ts:701` abre el listado de vales, que ya exige `requireVer()`
  (`ValeGraphQL.java:67`) y por eso no muestra filas; el legajo (`VER_FUNCIONARIOS`) no imprime
  recibos.
- **PWA**: un solo llamador, `mi-trabajo.page.ts:319`, con ids de `misRecibosMobile`.
- **Android y filial**: no usan ninguna.
- **Consumidores internos del central**: ninguno llama a estos métodos de resolver.

## Sin verificar / riesgos conocidos

- **Datos de producción**: usuarios de la PWA sin `persona_id`. Quedarían rechazados, pero hoy
  tampoco ven su lista (`funcionarioDe` ya falla sin persona).
- **Consumidores fuera de estos repos** (scripts, integraciones): no se encontró ninguno.
- La skill `rrhh-expert/seguridad-roles.md` (repo `frc-cicd`, solo lectura en esta máquina) queda
  desactualizada en su línea 51: avisar a Gabriel.

## Fuera de alcance (ticket aparte)

- Las 22 lecturas sin control de `FeriadoGraphQL` (6), `MotivoValeGraphQL` (4),
  `LiquidacionConceptoGraphQL` (4), `TipoJustificativoGraphQL` (4) y `JustificativoGraphQL` (4,
  con datos personales).
- `FuncionarioGraphQL` (módulo personas): devuelve `sueldo` sin ningún control de rol.
- `MarcacionGraphQL.imprimirReporteMarcaciones`: recibe `usuarioId` del cliente y no lo valida.
- El arreglo sistémico de #177.

## Auditoría del plan (paso 5, 2026-10-09)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Comparar `Long` con `==` pasa tests con ids chicos y falla en producción | B | Consulta única en el repositorio; tests con ids > 127 |
| Solo parte de los tests da rojo con el código viejo | B | Revertir solo los resolvers; `verify(never())`; casos de no regresión anotados |
| `findById(null)` devuelve `null` | B | Guarda de `null` explícita |
| `currentUsuario()` no mira `activo` | B | Decisión anotada, sin cambio |
| Lecturas salariales abiertas no nombradas; eran 22 y no 19 | B | Sumadas a «Fuera de alcance» |
| La regla del dueño y el listado usan caminos distintos | A | Comentario cruzado; equivalencia apoyada en los UNIQUE de `persona_id` |
| Dos «sin verificar» ya eran verificables | A | Reemplazados por evidencia; prueba 4 de runtime |
| `verRecibo` de la PWA sin try/catch | A | Caso agregado a la prueba 3; la PWA no se toca |
| Lookups duplicados de usuario por recibo | B | Sin cambio: se imprime de a uno |

Los dos auditores no se contradijeron.

## Despliegue y rollback

Requiere reinicio del central (lo hace el workflow `Deploy`). Rollback de código puro: la versión
anterior vuelve a dejar todo abierto, sin estado que revertir. Clientes viejos siguen funcionando:
no cambia ninguna firma.
