# Plan #609: el filtro por estado del análisis de diferencias filtra antes de paginar

Issue: GabFrank/franco-system-backend-servidor#609. Rama: `fix/analisis-diferencias-filtro-estado`, **desde la rama de
#607** (`fix/caja-diferencia-consecutiva-por-moneda`, PR #610, sin mergear): usa su regla por moneda. PR hacia `develop`;
muestra también los commits de #607 hasta que #610 se mergee (después: `git rebase --onto origin/develop
fix/caja-diferencia-consecutiva-por-moneda` y `push --force-with-lease`). Pieza: **central** solamente.

## Problema

Desktop, **Análisis de diferencias → Diferencia en maletines**, filtro «Con diferencia» / «Sin diferencia»
(`cajasAnalisisDiferencias(difEstado)`). `PdvCajaService.findAllForAnalisisDiferencias`:

1. Pide **una página** a la base (10 cajas), calcula el estado de esas 10, las filtra y vuelve a cortar con el mismo
   offset: la página 2 toma `subList(10, 20)` de una lista que ya era de a lo sumo 10 → **vacía**. La página 1 trae solo
   las cajas con ese estado que cayeron entre las 10 primeras sin filtrar; `totalElements` es lo que quedó de esas 10.
   Lo mismo con el filtro `cajaAnteriorId` combinado con fechas, sucursal o maletín.
2. Calcula el estado **dos veces** por caja (`enriquecerConCajaAnterior` y otra vez en el filtro).
3. Buscando por **caja anterior sin fechas** (lo que manda el desktop al escribir una caja), entra en
   `findCajasByAnteriorIdOptimized`, que **ignora `difEstado`** (visto al probar #607).

## Enfoque

**El filtro y el total se resuelven en SQL** (elegido por Franco, 11/11/2026: un maletín son ~800 cajas, hasta 1.844, y
calcular el estado en Java cuesta ~3 consultas por caja). Una consulta nativa calcula el estado de cada caja con la
**misma regla que `estadoPorMoneda` (#607)**, filtra, ordena y pagina; otra cuenta. Después se cargan y enriquecen en
Java **solo las cajas de la página** (la etiqueta sigue saliendo de `enriquecerConCajaAnterior`, como hoy).

Regla en SQL, igual a la de Java:
- caja anterior: solo si esta tiene conteo de apertura, maletín y fecha de apertura (= `enriquecerConCajaAnterior`);
  misma `maletin_id`, `fecha_cierre < fecha_apertura` de esta, con `conteo_cierre_id`, la de `fecha_cierre` más
  reciente (= `findPreviousCajaByMaletinAndFecha`). **Desempate nuevo `id desc, sucursal_id desc` en los dos lados**
  (hoy el JPQL no tiene: con dos cierres en el mismo instante la anterior era indefinida);
- `SIN_DATOS` sin conteo de apertura, sin maletín, sin fecha de apertura o sin caja anterior;
- `CON_DIFERENCIA` si alguna denominación difiere en más de 0,005 entre el cierre de la anterior y la apertura de esta
  (`conteo_moneda` por `conteo_id` + `sucursal_id` de cada caja; se ignoran billetes con cantidad, valor o denominación
  nulos); si no, `SIN_DIFERENCIA`.

Una sola consulta: CTE que calcula el estado una vez, `count(*) over()` para el total, `LIMIT`/`OFFSET` explícitos
(sin `Pageable` en la nativa). Parámetros nulos con `CAST(:x AS bigint|boolean|timestamp|text)` en cada aparición;
el estado de la caja es el enum `pdv_caja_estado`: se compara `CAST(c.estado AS text) = :estado` con el `name()`
(la nativa vieja hacía `enum = text`, que PG rechaza: nunca corrió). Las fechas aplican solo si vienen las dos, como
hoy. La descripción del maletín llega en minúsculas con `%…%` armado en Java.

Filtros, los mismos de hoy: sucursal, caja, estado de la caja, maletín (id o descripción con `lower like`), cajero,
verificado, `creado_en` entre fechas, caja anterior y estado de diferencia. Orden, el de hoy: `maletin_id desc,
creado_en desc` (+ `id`, `sucursal_id` para que la paginación sea estable).

**Índice nuevo `V273.1`:** `pdv_caja (maletin_id, fecha_cierre)`. Sin él, buscar la caja anterior de cada fila recorre
la tabla: medido en la bodega local (51.196 cajas), el maletín más grande (1.844 cajas) tarda **20,4 s** sin índice y
**0,12 s** con él. También acelera la consulta JPQL de la caja anterior que ya corre por cada caja enriquecida. Solo
central: `pdv_caja` llega del filial por replicación y un índice en el suscriptor no le afecta.

**Caminos:**
- Sin `difEstado` ni `cajaAnteriorId`: **sin cambio** (página de la base + enriquecer).
- Con `difEstado` o `cajaAnteriorId` (y otros filtros o fechas): la consulta nueva.
- Solo `cajaAnteriorId` (camino «optimizado»): se queda, pero aplica también `difEstado` sobre su resultado (son pocas
  cajas: una por cada caja con ese id en las sucursales).

**Descartado:** filtrar en memoria todo el universo (más simple, pero ~2.400 consultas por maletín y ~18.000 en 120
días); usar `findAllForAnalisisDiferenciasNative` (sin llamador y con la suma mezclada: se reemplaza por la nueva).

## Fase única

- `db/migration/V273.1__idx_pdv_caja_maletin_fecha_cierre.sql`: `CREATE INDEX IF NOT EXISTS`.
- `PdvCajaRepository`: consulta nativa de la página (id, sucursal) y de conteo con el mismo `WHERE`; se borra
  `findAllForAnalisisDiferenciasNative` (sin llamador).
- `PdvCajaRepository.findPreviousCajaByMaletinAndFecha`: desempate `c.id desc, c.sucursalId desc`.
- `PdvCajaService.findAllForAnalisisDiferencias`: el camino con filtro usa la consulta nueva, carga las cajas de la
  página por `(id, sucursal)` en ese orden y las enriquece (como hoy: el desktop usa `cajaAnteriorId` y
  `conteoApertura` de ese enriquecimiento), sin segundo cálculo; `findCajasByAnteriorIdOptimized` aplica `difEstado`.
- **Tests:**
  - IT `AnalisisDiferenciasQueriesIT` (patrón `ProductoStockPorSignoQueriesIT`, transaccional, bodega local, réplica
    apagada, `-Dit.analisisDiferencias=true`), siembra con ids propios un maletín con cajas encadenadas: iguales,
    monedas cruzadas, 3 × 0,1 contra 0,3, sin caja anterior, billete con valor nulo, sin conteo de apertura, dos
    cajas anteriores con la misma `fecha_cierre`, dos cajas con el mismo `creado_en`, y una caja sin maletín. Verifica:
    estado de cada una; total; páginas de 3 sobre un total que no es múltiplo (la última parcial), sin repetir ni
    saltear; filtros `cajaAnteriorId` + fechas, `estado` (enum), verificado y sin fechas; paridad SQL == Java
    (`enriquecerConCajaAnterior` sobre la misma caja) en todas;
  - unitario del camino optimizado con `difEstado`;
  - el camino sin filtro (lo que usa `adicionar-caja-dialog` con `cajaId` + `sucId`) sin cambio.

## Datos nuevos

Un índice. Sin columnas ni cambio de GraphQL.

## Contrato y canales (eje A)

Mismo query y mismos campos; cambian el contenido y el total de las páginas filtradas (pasan a ser correctos). Cliente:
desktop (Análisis de diferencias). La PWA no lo usa. El filial no tiene esta consulta. El índice va solo en el central;
no hay orden de despliegue con otras piezas (sí: mergear #610 antes, o con, este PR).

## Pruebas de runtime

Jar de `develop`+#607 (la rama de #607) y jar de esta rama, uno después del otro, contra la bodega local (perfil `dev`,
Flyway **apagado** —el índice se crea a mano con la misma sentencia y se borra al final—, réplica y jobs apagados):
1. `cajasAnalisisDiferencias` con `difEstado` CON y SIN por maletín (el más grande) y por día: todas las páginas; con
   la rama, la suma de las páginas == `totalElements`, ninguna caja repetida ni faltante, y el estado de cada una ==
   el de la consulta sin filtro; con la rama de #607, mostrar el defecto (páginas vacías).
2. Búsqueda por caja anterior con `difEstado`: la rama filtra; antes no.
3. **Por pantalla:** desktop, Análisis de diferencias, maletín + «Con diferencia», recorrer páginas con el paginador;
   caja anterior + «Sin diferencia».
4. Tiempos con `EXPLAIN (ANALYZE, BUFFERS)`: maletín más grande, un día, 120 días.
5. **Paridad sobre datos reales:** las 1.844 cajas del maletín más grande, estado de la consulta SQL == etiqueta de
   Java (`cajasAnalisisDiferencias` sin `difEstado`).
6. Pestaña «Diferencia en cajas» del desktop (manda `cajaAnteriorId` + fechas sin `difEstado`): pasa al camino nuevo;
   total y páginas correctos.
7. La migración V273.1 aplica: su archivo por `psql` en una transacción sobre la bodega local (y rollback).

## Resultado de las pruebas (11/11/2026)

**IT** `AnalisisDiferenciasQueriesIT` (bodega local): 7/7 en verde (más `PdvCajaEstadoConsecutivoTest`,
`PdvCajaAccesoTest`, `ControlRolMatrizTest`). Con el servicio de #607 fallan 2: la última página parcial sale vacía y el
atajo por caja anterior no filtra por estado. El empate de cierres pasaba también con el viejo (el orden indefinido cayó
del mismo lado); ahora está fijado. La caja sin maletín aparece en el camino sin filtro: el `Sort` por `maletin.id` no
hace join interno.

**Migración V273.1** por `psql` sobre la bodega local en una transacción: crea el índice en **69 ms** (51.196 cajas);
rollback.

**Runtime** (bodega local, índice creado a mano con la misma sentencia y borrado al final; jar de #607 y jar de la rama,
uno por vez en el 8081, Flyway, réplica y jobs apagados). Maletín 0020220103 (1.844 cajas):

| | jar de #607 | jar de la rama |
|---|---|---|
| CON, páginas 1/2/3 | 10 / 0 / 0 cajas, totales 10 / 7 / 2 | todas las páginas de 100: total 1.130, 1.130 recorridas, sin repetidas |
| SIN, páginas 1/2/3 | 0 / 0 / 0 cajas, totales 0 / 3 / 8 | total 713, 713 recorridas, sin repetidas |
| Etiqueta distinta del filtro | — | 0 |

- **Paridad sobre datos reales:** las 1.844 cajas del maletín, estado de la consulta SQL == etiqueta de Java (consulta
  sin filtro): **0 diferencias** (1 SIN_DATOS).
- **Tiempos** (página de 10): maletín 0,5 s (página 1 y 51); un día 0,1 s.
- **Caja anterior 6328** (atajo): SIN → 0, CON → la 6333; antes devolvía la 6333 con cualquier filtro.
- **Por pantalla, desktop** (Análisis de diferencias → Diferencia en maletines, maletín 0020220103 sin fecha):
  sin filtro «1 – 10 of 1844»; «Con diferencia» «1 – 10 of 1130», siguiente página «11 – 20 of 1130», última
  «1121 – 1130 of 1130», todas CON DIFERENCIA.
- **Pestaña Diferencia en cajas:** con caja 6328 manda `cajaAnteriorId` sin fechas (aunque la fecha esté puesta) → atajo:
  6333; con sucursal SUC. CENTRAL pasa por la consulta nueva: 6333, «1 – 1 of 1».

## Auditoría del diff (paso 8)

Fijo 1 (permisos): `cajasAnalisisDiferencias` sin cambios de control de acceso. Fijo 2 (migraciones): V273.1 solo
`CREATE INDEX IF NOT EXISTS`, central, permitido; probada en transacción. Fijo 3 (operaciones/contrato): mismo query,
mismos campos; con filtro cambia contenido y total (correctos); sin filtro, igual; el desempate del JPQL de la caja
anterior solo decide empates exactos. Condicionales: `pdv_caja` es BRANCH_TO_MAIN; un índice en el suscriptor no
toca la réplica.

## Auditoría (paso 5, 11/11/2026)

Medido además (bodega local, índice temporal en transacción con rollback, una sola consulta con `count(*) over()`):
120 días **0,05 s**; las 51.196 cajas **2,6 s** (el desktop nunca lo pide: siempre manda fecha o maletín).

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | Llamadores: desktop pestaña maletín (con `difEstado`), pestaña caja (`cajaAnteriorId` + fechas, sin `difEstado`), `adicionar-caja-dialog` (`cajaId` + `sucId`, camino sin cambio) | Runtime 6 y test de no regresión |
| A2/B1 | `enum = text` no existe en PG; parámetros nulos sin tipo | `CAST(c.estado AS text)`; `CAST` en cada parámetro |
| A2 | La caja anterior solo se busca con conteo de apertura y maletín | Igual en SQL |
| A2/B1 | Empate en `fecha_cierre` indefinido en JPQL | Desempate `id, sucursal_id` en los dos |
| A2 | Conteo con FK colgante: Java da SIN_DATOS, SQL mira solo el id | Caso raro: aceptado y anotado |
| A2 | Diferencia exactamente 0,005: double vs numeric | Los billetes van de a 0,05: no ocurre; anotado |
| B3 | Estado calculado dos veces (página y conteo) sobre todo el universo | Una consulta con `count(*) over()`; medido |
| B2 | `findAllForAnalisisDiferencias` sin `@Transactional`; LAZY por open-in-view | Sin cambio (igual que hoy); el IT es transaccional |
| B4 | Índice: `CREATE INDEX IF NOT EXISTS` en la transacción de Flyway, sin CONCURRENTLY (un fallido quedaría INVALID); `pdv_caja` es BRANCH_TO_MAIN, el central suscriptor | Así; ninguna rama trae V273 |
| A4 | Borrar la nativa vieja es seguro (solo su definición) | Se borra |
| A5/B5 | Faltaban casos (empates, sin maletín, última página parcial, enum, fechas nulas) y paridad sobre datos reales | Agregados |

## Sin verificar

- Volumen de producción (la bodega local es una copia: 51.196 cajas, 27 sucursales).
- Conteo de apertura/cierre con id que apunta a un conteo inexistente: Java da SIN_DATOS, SQL lo compara.
- Si el `Sort` por `maletin.id` de JPA hace join interno (excluiría cajas sin maletín del camino sin filtro): lo dice
  el IT.
