# Plan — Consulta automática de los lotes de notas electrónicas (central)

Rama: `fix/sifen-consultar-lotes-notas` (central, desde `develop` 92ff4936). Solo central.

## 1. Qué pasa hoy (verificado en bodega producción, 2026-09-30)

- Las notas de crédito y de remisión se emiten y se **envían en el momento** desde el central
  (`SifenEnvioSincronoService.generarYEnviarSincrono`: lote de uno + `enviarLote`). SIFEN responde
  `0300 Lote recibido` y el lote queda `EN_PROCESO`, el DE `EN_LOTE`.
- La respuesta final (aprobado / rechazado) solo la trae `SifenSchedulerService.consultarLotesPendientes`,
  gateado por `sifen.scheduler.enabled` (**default `false`**). En bodega está apagado: en el journal de
  `frc-bodega` corre `EventoSifenScheduler` cada 5 min y `SifenSchedulerService` no deja una línea.
- El plan original de las notas (`PLAN-NOTA-REMISION-NOTA-CREDITO.md`, D8) lo dejó dicho: el estado
  final lo trae el scheduler «si está habilitado (valor en producción NO VERIFICADO)» o un botón
  «Consultar» que el desktop nunca tuvo.
- Resultado: **todas** las notas quedan `EN_LOTE` para siempre. El 30/09 había 11 lotes trabados
  (1 NC + 10 NRE); se destrabaron a mano con la mutation `consultarLote` (10 aprobadas, 1 rechazada
  por 2208). A las 17:02 ya había 5 NRE más trabadas (lotes 69697-69705).
- **Prender `sifen.scheduler.enabled` en el central no es la solución**: la tabla `lote_de` y
  `documento_electronico` del central reciben por replicación los lotes y DE de facturas de las
  filiales (2962 lotes `PROCESADO` en 2 días, 16 DE `PENDIENTE`). El paso 1 del scheduler general
  (`crearYEnviarLotes`) mandaría a SIFEN los DE de facturas que ya envía cada filial, y el paso 0
  (`procesarLotesAtrasados`) reenviaría sus lotes.

### Consecuencias de que la nota quede EN_LOTE (agravan el bug)

- `reenviarNotaCredito` / `reenviarNotaRemision` aceptan un DE `EN_LOTE` cuyo lote está
  `EN_PROCESO`: arman **otro lote con el mismo CDC** y lo mandan de nuevo. Es lo primero que hace un
  usuario al ver la nota «trabada». D8 pedía consultar el CDC antes de reenviar; no está hecho.
- `anularNotaCredito` / `anularNotaRemision` solo cancelan en SIFEN si el DE está `APROBADO`: una
  nota trabada en `EN_LOTE` que el usuario anula queda de baja **solo en el sistema**, y en SIFEN
  sigue aprobada.

## 2. Decisiones

- **D1 — Un tick propio, solo para lotes de notas, con su propio bucle.** Método nuevo
  `consultarLotesDeNotas()` en `SifenSchedulerService`, con
  `@Scheduled(fixedDelayString = "${sifen.notas.consulta.fixed-delay:120000}", initialDelay = 60000)`.
  Toma únicamente lotes `EN_PROCESO` que tengan al menos un DE con `nota_credito_id` o
  `nota_remision_id` (query nueva en `LoteDERepository`, `EXISTS` por `loteDeId` **y** `sucursalId`:
  la PK es compuesta). Los lotes de facturas replicados **nunca** entran: no tienen esas columnas.
- **D2 — Bandera y exclusión con el scheduler general.** `sifen.notas.consulta.enabled`, default
  **`false`**: se prende solo en bodega, por `.env` (§5, decisión del usuario). Si `sifen.scheduler.enabled=true` el tick nuevo **no hace
  nada**: el general ya consulta todos los `EN_PROCESO`. Comparte el flag `procesandoLotes`.
  Dependencia a dejar escrita: `SIFEN_SCHEDULER_ENABLED=false` es lo que se usa para «apagar SIFEN»
  en un central (gotchas.md, «SIFEN no se puede desactivar vía env var»), y **no gobierna este
  tick**: lo gobierna `SIFEN_NOTAS_CONSULTA_ENABLED`.
- **D3 — El bucle NO reusa el del scheduler general** (cambiado por la auditoría, hallazgos B2/B4):
  el general cuenta intentos y con `max-retries` manda a `ERROR_RED`/`ERROR_PERMANENTE` un lote que
  tiene protocolo y SIFEN sigue procesando; y corre dentro de un `@Transactional` que se marca
  rollback-only con cualquier excepción de un lote y pierde lo escrito en los demás. El bucle nuevo:
  - **no** es `@Transactional`: cada `sifenService.consultarLote(lote)` es su propia transacción (otro
    bean, pasa por el proxy). Queda comentado para que nadie lo «arregle»;
  - **relee** cada lote con `findByIdAndSucursalId` y lo saltea si ya no está `EN_PROCESO` (la mutation
    manual o un reenvío pudieron cerrarlo entretanto);
  - ante una excepción **solo loguea**: no toca `intentos` ni el estado. El lote sigue `EN_PROCESO` y
    se reintenta en la vuelta siguiente, hasta que lo cierre SIFEN o el corte de D4.
  El scheduler general queda **sin cambios**.
- **D4 — Código inesperado de SIFEN no mata el lote** (hallazgo B1/A-extra). Hoy el `default` del
  switch de `SifenService.consultarLote` pasa el lote a `ERROR_PERMANENTE` sin tocar los DE, y un
  código `null` revienta el `switch` con NPE. Cambio: `null` y códigos desconocidos dejan el lote como
  está (`EN_PROCESO`) y loguean WARN con la respuesta; `ERROR_PERMANENTE` queda solo para `0360`
  (lote inexistente). Aplica también al general y a la mutation manual, que hoy tienen el mismo
  agujero.
- **D5 — Lote de más de 47 h: se consulta cada DE por CDC.** SIFEN acepta la consulta de lote solo
  dentro de las 48 h de recibido (código de respuesta pasado ese plazo: **no verificado**, §6; con D4
  ya no mata el lote aunque sea desconocido). Edad medida por `lote.creadoEn` (un lote reenviado más
  tarde entra antes al camino por CDC, que sirve a cualquier edad). Para cada DE `EN_LOTE`:
  `sifenService.consultarDE(cdc)` (existente, `REQUIRES_NEW`). El estado del lote se **deriva de sus
  DE**, como en `procesarRespuestaLoteConcluido`: todos `APROBADO`/`CANCELADO` → `PROCESADO`; todos
  `RECHAZADO` → `RECHAZADO`; mezcla → `PROCESADO_CON_ERRORES`; alguno sigue `EN_LOTE` o la consulta
  tiró excepción → el lote **no cambia**. Ojo, conocido y aceptado: `consultarDE` con `0420` («no
  existe») marca el DE `RECHAZADO` (`SifenService.actualizarEstadoDENoEncontrado`).
- **D6 — Fuera de alcance, anotado** (§6): el reenvío de lotes de notas que fallaron al enviarse
  (`PENDIENTE_ENVIO`/`ERROR_ENVIO`/`ERROR_RED`). Hoy no hay ninguno en bodega; el botón «Reenviar»
  existe para ese caso.
- **D7 — Replicación** (hallazgo A2). El lote y el DE de una nota llevan la `sucursal_id` de la nota;
  `lote_de` y `documento_electronico` se publican central→filial filtradas por sucursal, así que lo
  que escribe el tick **baja a la filial de esa sucursal**. El filial no lo procesa: su scheduler
  exige DE con factura propia (`esPropioDeEstaFilial` / `todosPropiosDeEstaFilial`,
  filial `service/sifen/service/SifenSchedulerService.java:334-343`, guarda D12 del plan de notas).
  Es el mismo camino que ya recorren hoy la emisión y la mutation manual; el fix no agrega uno nuevo.

## 3. Fases

### Fase 1 — Consulta automática de los lotes de notas (commit `fix(sifen): …`)

Archivos:
- `repository/financiero/LoteDERepository.java`: `findEnProcesoDeNotas()`.
- `service/financiero/LoteDEService.java`: `findEnProcesoDeNotas()`.
- `service/sifen/SifenSchedulerService.java`: `consultarLotesDeNotas()` (D1, D2, D3, D5).
- `service/sifen/SifenService.java`: el switch de `consultarLote` (D4).
- `application.properties`: las dos propiedades nuevas, documentadas, con su default.

Tests (Mockito, mismo patrón que `SifenSchedulerLotesAtrasadosTest`):
- `SifenSchedulerLotesDeNotasTest`
  1. consulta los lotes de `findEnProcesoDeNotas` y **no** llama a `findByEstado(EN_PROCESO)`;
  2. con `sifen.scheduler.enabled=true` no consulta nada;
  3. con `sifen.notas.consulta.enabled=false` no consulta nada;
  4. un lote releído que ya no está `EN_PROCESO` no se consulta;
  5. `consultarLote` tira `SifenException` cinco vueltas seguidas: el lote sigue `EN_PROCESO`, con los
     mismos `intentos`, y nunca se guarda en `ERROR_RED`/`ERROR_PERMANENTE`;
  6. lote de 50 h: no llama a `consultarLote`; llama `consultarDE(cdc)` por cada DE; con los DE
     `APROBADO` el lote queda `PROCESADO`;
  7. lote de 50 h con un DE que sigue `EN_LOTE` (o `consultarDE` tira): el lote no cambia;
  8. lote de 50 h con el DE `RECHAZADO`: el lote queda `RECHAZADO`, no `PROCESADO`.
- `SifenServiceConsultarLoteTest` (o donde encaje con `Sifen` estático): código desconocido y `null`
  dejan el lote `EN_PROCESO`; `0360` lo deja `ERROR_PERMANENTE`.
Revertido el fix, 1, 5, 6 y el de código desconocido tienen que fallar.

### Fase 2 — Reenviar y anular miran a SIFEN antes de actuar (commit `fix(sifen): …`)

Rediseñada por el hallazgo B6: bloquear sin más dejaba al usuario sin salida con lotes muertos.
En `reenviarNota*` y `anularNota*`, si el DE está `EN_LOTE`: **primero `consultarDE(cdc)`**, y según
lo que quede:
- `APROBADO` → `anular` cancela ante SIFEN (camino existente); `reenviar` responde «ya fue aprobada»
  (existente).
- `RECHAZADO` (incluye «no existe», 0420) → `anular` hace la baja local; `reenviar` sigue como hoy.
- sigue `EN_LOTE` y su lote está `EN_PROCESO` → error «SIFEN todavía está procesando la nota, probá en
  unos minutos». Nunca un segundo lote con el mismo CDC mientras el primero está vivo.
- sigue `EN_LOTE` y el lote no está vivo (`ERROR_ENVIO`, `ERROR_RED`, `ERROR_PERMANENTE`, sin
  protocolo) → `reenviar` sigue como hoy; `anular` hace la baja local (nunca llegó a SIFEN).
Tests por resolver (NC y NRE): los cuatro caminos; en el tercero, `generarYEnviarSincrono` y
`service.anular` nunca se llaman. Revertido el fix, el tercero falla.
Sin cambios de GraphQL: mismas mutations, mensajes de error nuevos. El desktop muestra el mensaje del
`GraphQLException` tal cual (verificar con grep al implementar).

## 4. Datos nuevos

| Dato | Quién lo escribe | Quién lo lee |
|---|---|---|
| `sifen.notas.consulta.enabled` | default `false` en `application.properties`; `.env` de bodega `SIFEN_NOTAS_CONSULTA_ENABLED=true` | `SifenSchedulerService.consultarLotesDeNotas` |
| `sifen.notas.consulta.fixed-delay` | ídem | `@Scheduled` de `consultarLotesDeNotas` |

Sin migraciones. Sin cambios de GraphQL. Sin desktop. Rollback seguro: el tick solo escribe estados
que ya existen; con la versión vieja las notas nuevas vuelven a quedar `EN_LOTE`.

## 5. Decisión del usuario (2026-09-30)

**Default de `sifen.notas.consulta.enabled` = `false`; se prende solo en bodega.** Bodega es hoy el
único central que factura con SIFEN. Los tres centrales corren con `SIFEN_ENABLED=true` (no se puede
apagar), así que con default `true` el tick correría también en farmacia y alpha.

**Paso de despliegue obligatorio** (lo hace el usuario, el `.env` es 600 de `deploy`): agregar
`SIFEN_NOTAS_CONSULTA_ENABLED=true` a `/opt/frc-backend-central/bodega/.env` antes o junto con el
deploy. Sin eso el fix llega a bodega apagado. Cuando farmacia empiece a emitir notas, se prende igual
en su `.env`.

## 6. Verificación

- Paso 9: `./mvnw clean verify -B -DskipFlyway=true`; veredicto `gh pr checks`.
- Runtime local: central con perfil `dev` **y** `--sifen.enabled=true` contra la `bodega@5551` local:
  en el log, la query de lotes de notas y que los lotes de facturas no entran. Consulta SIFEN
  productivo en modo lectura.
- Tras el deploy en bodega: los lotes `EN_PROCESO` de notas pasan a `PROCESADO`/`RECHAZADO` solos
  en ≤ 2 min, y en el journal aparece el tick.

## 7. Sin verificar / fuera de alcance

- El `.env` de bodega (600, `deploy`) no se pudo leer: que `sifen.scheduler.enabled` no esté seteado
  se infiere del log, no del archivo. Idem farmacia y alpha.
- Qué código devuelve SIFEN al consultar un lote de más de 48 h (D5 no depende de eso).
- Reenvío automático de lotes de notas que fallaron al enviarse (D6).
- La NRE 31 rechazada (2208) salió de una ciudad de entrega editada a mano sin su código: validar el
  par ciudad/código es otro fix.

## 8. Auditoría del plan (paso 5) — qué se hizo con cada hallazgo

| # | Eje | Hallazgo | Sev. | Qué se hizo |
|---|---|---|---|---|
| A1 | A | Fase 2 cambia mensajes de error de mutations existentes | baja | anotado en Fase 2: verificar con grep en desktop |
| A2 | A | Lo que escribe el tick replica a la filial de la sucursal | media | D7, verificado contra la guarda del filial |
| A3 | A | Default `true` corre en farmacia y alpha; `SIFEN_SCHEDULER_ENABLED=false` no lo apaga | baja | D2 + §5: default `false`, solo bodega |
| A4 | A | `EXISTS` sin `sucursalId` colisiona ids entre sucursales | baja | D1: `loteDeId` **y** `sucursalId` |
| A5 | A | Lote en `ERROR_PERMANENTE` por código raro antes de 47 h queda huérfano | baja | resuelto de raíz con D4 |
| B1 | B | `default`/`null` del switch → `ERROR_PERMANENTE` / NPE | alta | D4 |
| B2 | B | `max-retries` manda lotes vivos a `ERROR_RED` | alta | D3: bucle propio sin contadores; test 5 |
| B3 | B | Camino por CDC: excepción, 0420, estado del lote | media | D5: estado derivado de los DE; tests 7 y 8 |
| B4 | B | `@Transactional` envolvente se marca rollback-only | media | D3: bucle sin transacción envolvente |
| B5 | B | Carrera tick vs mutation manual (entidad vieja) | baja | D3: relectura y salteo |
| B6 | B | Fase 2 deja `anular` sin salida con lote muerto | media | Fase 2 rediseñada: `consultarDE` primero |
| B7 | B | Default `false` más prudente | baja | aceptado: default `false`, solo bodega en `true` (§5) |
