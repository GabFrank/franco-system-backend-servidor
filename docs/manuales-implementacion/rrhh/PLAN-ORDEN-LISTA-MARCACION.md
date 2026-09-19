# Plan — Lista de marcaciones ordenada por hora de llegada

Rama: `fix/rrhh-orden-lista-marcacion` (desde `develop`) · Pieza: **central** · Fecha: 2026-09-19

## Pedido

La lista de marcaciones del desktop (`administrativo/marcacion/pages/list-marcacion`) tiene que
mostrar de la **llegada más reciente a la más vieja**. Hoy sale por `id DESC`.

## Análisis

La pantalla lista **jornadas**, no marcaciones. Siempre manda `fechaInicio`/`fechaFin` (si faltan,
`filtrar()` los completa), así que las dos rutas reales son:

| Pantalla | Query GraphQL | Repositorio | Orden hoy |
|---|---|---|---|
| sin funcionario | `jornadas(fechaInicio, fechaFin, …)` | `JornadaRepository.findByFechaRange` | `j.id DESC` |
| con funcionario | `jornadasPorUsuario(usuarioId, fechaInicio, fechaFin)` | `JornadaRepository.findByUsuarioIdAndFechaRange` | `j.id DESC` |

Por qué `id DESC` no es cronológico: la clave de `jornada` es `(id, sucursal_id)` y cada sucursal
tiene su propia secuencia (`findMaxId(sucursalId)`). Dos jornadas del mismo día creadas en
sucursales distintas quedan ordenadas por el contador de cada sucursal, no por la hora.

La jornada no tiene hora propia: la hora de llegada es `marcacionEntrada.fechaEntrada`
(`LocalDateTime`). Puede ser `NULL` (existe `findAbiertasSinEntradaByUsuarioIdAndFecha`).

El desktop no reordena en cliente: la tabla no tiene `MatSort` y `aplicarFiltroTurno` solo filtra,
conserva el orden del servidor. No hace falta tocar el desktop.

### Otros consumidores de las dos consultas

| Consumidor | ¿Depende del orden? |
|---|---|
| `EstadoMarcacionService.obtenerEstado` → `JornadaMarcacionRules.seleccionarJornadaRelevante` | Casi no: elige con `max(Comparator…getId)`. Solo ante **empate de `id`** (mismo id en dos sucursales, mismo usuario, ayer–hoy) gana la primera de la lista, y el orden nuevo decide cuál. Improbable y ya incorrecto hoy (`id` por sucursal no es cronológico); fuera de alcance, anotado (auditoría eje A, R2) |
| `PenalizacionService` (tardanza del día) | No: recorre todas |
| `LiquidacionSueldoService:210` | No: usa `.size()` |
| `MarcacionGraphQL.imprimirReporteMarcaciones` → `ImpresionService.imprimirMarcaciones` | **Sí**: imprime en el orden recibido. Pasa de `id DESC` a llegada `DESC`; sigue descendente, queda coherente con la pantalla |
| mobile Android (`getJornadasPorUsuario`) | Declarada en el service, ninguna página la llama |

## Criterio de orden

```
ORDER BY j.fecha DESC, COALESCE(me.fechaEntrada, me.fechaSalida) DESC NULLS LAST, j.id DESC
```

con `LEFT JOIN j.marcacionEntrada me`.

- `j.fecha DESC` primero: agrupa por día de jornada, así una jornada sin entrada queda en su día
  en vez de hundirse al fondo del rango. Para las jornadas con entrada coincide con ordenar por
  llegada: `fecha` se fija al crear la jornada con `fechaEntrada.toLocalDate()`
  (`MarcacionService.obtenerFechaReferencia`, `JornadaFactory:16`). En NOCHE/MADRUGADA lo que cruza
  la medianoche es la **salida**, que se engancha a la jornada del día anterior; la entrada siempre
  cae en `fecha`. Única excepción: una `fechaEntrada` corregida a mano a otro día
  (`MarcacionGraphQL:110-111`) no recalcula `fecha`. Es un dato inconsistente previo, fuera de
  alcance.
- `COALESCE(me.fechaEntrada, me.fechaSalida) DESC NULLS LAST`: dentro del día, la llegada más
  reciente primero. El `COALESCE` es el mismo criterio que ya usa el reporte impreso
  (`ImpresionService.java:1210`) para la hora de entrada; así la lista y el reporte coinciden
  (auditoría eje A, R1).
- `j.id DESC`: desempate estable (misma hora exacta).

`LEFT JOIN` y no `JOIN`: un `JOIN` interno descartaría las jornadas sin entrada, que hoy sí se
listan. `NULLS LAST` lo soporta el HQL de Hibernate 5.6.15 (el del `pom`).

## Fases

### Fase 1 — Orden en las dos consultas de rango (único commit)

- `JornadaRepository.findByFechaRange` y `findByUsuarioIdAndFechaRange`: `LEFT JOIN
  j.marcacionEntrada me` + el `ORDER BY` de arriba.
- `JornadaGraphQL.jornadas`, rama sin fechas (`findAll(pageable)`): `PageRequest.of(page, size,
  Sort.by(DESC, "fecha").and(Sort.by(DESC, "id")))`. El desktop no la usa, se alinea para que las
  dos ramas de la misma query no ordenen distinto.
- Sin cambios de esquema, `.graphqls` ni desktop.

Commit: `fix(rrhh): ordenar la lista de marcaciones por hora de llegada`.

**Tests**

- IT nuevo `JornadaOrdenQueriesIT` (patrón de `CantidadSugeridaQueriesIT`: `@SpringBootTest`
  sin web, Flyway apagado, `@Transactional`, `@EnabledIfSystemProperty(it.jornadaOrden)`), contra
  la base local. Reglas de siembra (auditoría eje B):
  - **rango aislado** (2099-…), con un `count` en `@BeforeEach` que confirme que está vacío:
    `findByFechaRange` no filtra por usuario y los datos reales romperían las posiciones;
  - usuario existente (FK `fk_jornada_usuario`) y marcación de entrada real con el mismo
    `(id, sucursal_id)` (FK `fk_jornada_entrada`); ids altos (9xxxxxxxx) para no chocar con PK reales;
  - **cada caso siembra ids en contra del orden esperado**, para que `id DESC` lo haga fallar.

  Casos:
  - tres jornadas del mismo día, sucursales distintas, `id` inverso a la hora de entrada → salen
    por hora de entrada `DESC`;
  - jornada sin `marcacionEntrada` con el **id más alto** del día → aparece (el join no la
    descarta) y queda última de su día;
  - marcación de entrada con `fechaEntrada` NULL y `fechaSalida` cargada → ordena por `fechaSalida`;
  - dos días, el día viejo con **ids más altos** → el día más reciente primero;
  - `findByUsuarioIdAndFechaRange` con una jornada de otro usuario en el mismo rango → no aparece
    (el `LEFT JOIN` no rompe el filtro).
- **Paso rojo obligatorio**: correr el IT contra el `ORDER BY j.id DESC` actual y verlo fallar
  antes de cambiar el JPQL. Comando:
  `./mvnw test -Dit.jornadaOrden=true -Dtest=JornadaOrdenQueriesIT -Dspring.datasource.url=…`
- `./mvnw test` completo verde (unitarios de `JornadaMarcacionRules` y `MarcacionServiceHorarioSyncTest`
  no deberían moverse: no dependen del orden).

**Prueba manual (Franco)**: central local con perfil `dev` + desktop `ng serve -c web`, abrir
Marcaciones con rango de ayer–hoy, con y sin funcionario; verificar el orden y que el reporte
impreso salga en el mismo orden.

## Tabla de datos nuevos

N/A: no nace ningún campo, columna ni clave. Solo cambia el orden de una consulta existente.

## N/A del ciclo

- **filial**: N/A — el desktop consulta con `servidor = true` (default de
  `marcacion.service.ts:113,117`); el cambio es solo JPQL, no hay esquema ni migración que
  espejar. **Deuda consciente**: la filial tiene su propio `JornadaRepository` con el mismo
  `ORDER BY j.id DESC`, así que la misma query GraphQL ordena distinto en la filial. Ningún cliente
  la consulta ahí para esta pantalla.
- **desktop, mobile, mobile-pwa**: N/A — no reordenan en cliente; el contrato GraphQL no cambia.
- **Migraciones Flyway / replicación**: N/A — no hay cambio de esquema.
- **Multi-repo (§3)**: N/A — un solo repo.

## Riesgos

- **Rendimiento**: el `LEFT JOIN` a `marcacion` suma un join por fila en un rango que en general es
  de 1–7 días (el desktop no pagina esta ruta; trae todo el rango). Aceptable; se mira el tiempo en
  la prueba manual con un rango de un mes.
- **Reporte impreso** cambia de orden (ver tabla). Es deseable, pero es un cambio visible.

## Sin verificar

- La corrida del IT contra la base local (paso rojo y verde): se hace en la fase 1.
- Tiempo de respuesta con un rango de un mes: se mira en la prueba manual.
- Que Spring Data no reescriba el `NULLS LAST` de un `@Query` sin `Pageable`: por documentación
  no lo hace; lo confirma el IT.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A — R1 | El reporte impreso usa `fechaEntrada ?: fechaSalida` (`ImpresionService:1210`); ordenar solo por `fechaEntrada` mandaría esas jornadas al fondo de su día | Criterio con `COALESCE(me.fechaEntrada, me.fechaSalida)` + caso en el IT |
| A — R2 | `seleccionarJornadaRelevante` decide por el orden de la lista si dos jornadas empatan en `id` (sucursales distintas) | Anotado en la tabla de consumidores; fuera de alcance |
| A | La filial tiene el mismo `ORDER BY j.id DESC` | Anotado como deuda consciente en N/A filial |
| B | La premisa «entrada del día anterior a `fecha`» en NOCHE/MADRUGADA es falsa: cruza la salida, no la entrada | Corregida en «Criterio de orden»; quitada de «Sin verificar» |
| B — R1/R2/R3/R4 | El IT podía pasar con `id DESC`, chocar con datos reales o con las FK | Reglas de siembra, caso de otro usuario y paso rojo obligatorio |
| B | Reversibilidad: sin esquema, datos ni contrato; rollback = JAR anterior | Sin acción |
