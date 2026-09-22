# PLAN — La cotización de mercado deja de ser una dependencia

> Documento de trabajo del ciclo de 12 pasos (`frc-cicd/ciclo-implementacion-frc-comercial.md`).
> **Se borra en el PR final.** Lo que sobreviva se muda a `gotchas.md` de `frc-cicd`.

Rama: `claude/frc-cicd-cotizacion-hotfix-y4o248` (sale de `develop`) · Repo: **central, único**

## 1 · Síntoma y causa raíz

**Síntoma reportado:** cuando nortecambios.com.py no carga, el PDV deja de funcionar.

La integración de cotización de mercado entró por el PR #59 (`feat/cotizacion-mercado-scraping`):
`NorteCambiosScraper` + `CotizacionMercadoScheduler` + la mutation `actualizarCotizacionesMercado`.
Nació como lectura informativa: si no carga, no debería pasar nada. Hoy no es así, por dos cosas
que se combinan.

**(a) Todas las tareas programadas comparten un hilo.** El central tiene **19 métodos `@Scheduled`
en 17 clases** y **ningún `spring.task.scheduling.pool.size`** en ningún `application*.properties`
ni en ningún `SchedulingConfigurer` — el default de Spring Boot es **1**.
`[ev: comando — grep -rn "@Scheduled" src/main/java → 19 en 17 archivos; grep -rn "spring.task.scheduling\|ThreadPoolTaskScheduler\|SchedulingConfigurer" src/main → 0 resultados, sobre origin/develop]`

**(b) El scrape se cuelga más de lo que declara.** `NorteCambiosScraper` usa
`HttpsURLConnection` con `connectTimeout=5000` / `readTimeout=10000`, y **ninguno de los dos cubre
la resolución DNS**: `getResponseCode()` sobre un DNS caído o en blackhole bloquea mucho más que
la suma de ambos. Es la única de las 19 tareas que sale a un sitio de terceros en internet.
`[ev: central:src/main/java/com/franco/dev/service/financiero/NorteCambiosScraper.java:openConnection — setConnectTimeout(5000), setReadTimeout(10000)]`

**La cadena hasta el PDV:** mientras ese hilo está tomado, **no corre ninguna otra tarea
programada**. Entre ellas `RetiroTesoreriaScheduler` (`fixedDelay` 60 s), que su propio javadoc
describe como *«Puente Retiro (caja PDV) → Caja Mayor»* — el poller que integra los retiros que
llegan de la filial por replicación — y `NotificationDispatchService` (`fixedDelay` 5 s).
`[ev: central:src/main/java/com/franco/dev/service/financiero/RetiroTesoreriaScheduler.java:15,41]`

**(c) Falla secundaria, independiente de la anterior.** La mutation `actualizarCotizacionesMercado`
lanzaba `RuntimeException` cuando el scrape venía vacío. Un error GraphQL aborta la operación
entera del cliente que la pidió, no solo el refresco.

> ⚠️ **Lo verificado es la cadena en el código, no un thread dump de producción.** No se capturó
> `jstack` del central mientras el PDV estaba caído. La confirmación en caliente sería
> `jstack <pid> | grep -A20 '"scheduling-1"'` mostrando el hilo dentro de `NorteCambiosScraper` /
> `InetAddress`, y `journalctl -u frc-<instancia> | grep RetiroTesoreria` en silencio. Queda como
> **hipótesis fuerte con mecanismo demostrado**, no como causa confirmada en runtime.

## 2 · Alcance

| Repo | ¿Toca? | Por qué |
|---|---|---|
| **central** | **Sí** | Es el único lugar donde existe nortecambios |
| filial | No | `grep -ril nortecambios filial/src` → 0 resultados. Nunca tuvo el scraper |
| desktop | No | Consume `actualizarCotizacionesMercado` desde dos lugares y **los dos ya manejan `false` y `error`** — ver §5 |
| mobile / mobile-pwa | No | **Verificado clonando los dos repos** (`frc-mobile` `c5b8f4c`, `frc-mobile-pwa` `caffe35`): `grep -ril "actualizarCotizacionesMercado\|nortecambios\|valorEnGsVentaMercado\|valorEnGsCompraMercado" <repo>/src` → **0 resultados en ambos** |

## 3 · Fases

### Fase 1 — El scrape deja de poder colgar a nadie

1. `NorteCambiosScraper`: el cuerpo del scrape pasa a `doFetchRates()`, y `fetchRates()` lo
   ejecuta en un **pool propio de hilos daemon** con **presupuesto total de tiempo**
   (`cotizacion.mercado.timeout-ms`, default 20000) vía `Future.get(timeout)`.
   - Pool **cached**, no single-thread: un hilo clavado en DNS **no es interrumpible**, así que
     `cancel(true)` no lo libera. Con pool single-thread el intento siguiente se encolaría detrás
     del colgado para siempre.
   - `AtomicBoolean enVuelo`: un solo scrape en vuelo a la vez, así un sitio caído no acumula
     hilos. **El flag lo libera únicamente el `finally` de `doFetchRates`**, nunca `fetchRates`:
     tras un timeout `future.cancel(true)` deja `isDone()==true` con el hilo todavía clavado, y
     liberarlo ahí volvería a abrir la puerta.
   - Cerrar la conexión del GET también en el camino de error (hoy solo se cierra en el éxito).
2. `CotizacionMercadoScheduler`: el tick delega en **su propio hilo daemon** y vuelve de
   inmediato, así el hilo compartido de `@Scheduled` nunca queda retenido ni siquiera los 20 s
   del presupuesto. `actualizarCotizaciones()` devuelve `0` en vez de lanzar.
3. `CambioGraphQL.actualizarCotizacionesMercado`: devuelve `false` en vez de lanzar.
4. `application.properties`: `cotizacion.mercado.timeout-ms=20000` + comentario del interruptor
   operativo.

**Tests de la fase** (`CotizacionMercadoNoBloqueaTest`, nuevo):
- `fetchRatesNoSeCuelga` — presupuesto de 200 ms; `fetchRates` devuelve mapa vacío sin lanzar y
  sin retener al llamador.
- `sinCotizacionesNoLanza` — scraper mockeado que devuelve vacío; `actualizarCotizaciones()` → `0`.
- `scheduledUpdateNoRetieneElHilo` — scraper mockeado que tarda 3 s; `scheduledUpdate()` vuelve
  en < 1 s. **Es el test que falla con el código viejo** (ver §7).

### Fase 2 — El interruptor vale en las dos puertas

`cotizacion.mercado.enabled` hoy **apaga el scheduler pero no la mutation manual**: el
`@ConditionalOnProperty` está solo en `CotizacionMercadoScheduler`, mientras `CambioGraphQL`
inyecta el scraper directo. Con el flag en `false`, el botón «Actualizar cotización» del desktop
sigue saliendo a internet.

Es exactamente el defecto que el ciclo llama *«una perilla que vale en un camino y no en el otro
es peor que no tenerla»*: el operador la apaga, comprueba que el scheduler se calló, y el segundo
camino sigue abierto sin que nada avise.

- `CambioGraphQL` lee `cotizacion.mercado.enabled` con `@Value` y, si está en `false`, devuelve
  `false` sin tocar el scraper.

**Test de la fase:** `mutationRespetaElInterruptor` — con el flag en `false`, la mutation devuelve
`false` y **el scraper no se invoca** (`verify(scraper, never()).fetchRates()`).

> Orden real, anotado como corresponde: la Fase 1 se implementó en una sesión anterior como
> `hotfix/*` sobre `master` y se rebasó sobre `develop` para este PR (los 4 archivos que toca son
> **idénticos entre `master` y `origin/develop`**, así que el cherry-pick entró limpio). El plan
> se escribe ahora. **El paso 4 quedó fuera de orden para la Fase 1**; la Fase 2 sí nace del plan.

## 4 · Tabla de datos nuevos

| Dato | Tipo | Quién lo **escribe** | Quién lo **lee** |
|---|---|---|---|
| `cotizacion.mercado.timeout-ms` | property | `src/main/resources/application.properties` (default 20000); override por `.env` / drop-in systemd por instancia | `NorteCambiosScraper` constructor (`@Value`, fallback 20000 si ≤ 0) |
| `cotizacion.mercado.enabled` | property, **ya existía** | idem | `CotizacionMercadoScheduler` (`@ConditionalOnProperty`) **y, a partir de la Fase 2, `CambioGraphQL`** |

**Ninguna columna, ningún campo de entidad, ningún valor de enum, ningún cambio de `.graphqls`.**
La mutation `actualizarCotizacionesMercado: Boolean!` conserva firma y tipo.

## 5 · Contrato con los clientes

`actualizarCotizacionesMercado` pasa de *«lanza o devuelve `true`/`false`»* a *«siempre devuelve
`true`/`false`»*. Los dos consumidores son del desktop y **ya tratan `false` y `error` por
separado**, así que el cambio los deja igual o mejor:

- `desktop:src/app/modules/financiero/cambio/cambio.component.ts:83` — `next: (ok) => ok ? openSucess(...) : openWarn('No se encontraron cambios para actualizar')`, con rama `error` que igual refresca.
- `desktop:src/app/modules/operaciones/compra/gestion-compras/gestion-compras.component.ts:2213` — rama `error` con `openAlgoSalioMal("No se pudo actualizar la cotización del mercado")`.

Efecto: el caso «nortecambios no responde» deja de caer en la rama `error` y cae en `false`. En
`cambio.component.ts` el usuario pasa de un snackbar de error a *«No se encontraron cambios para
actualizar»*. En `gestion-compras` deja de ver «Algo salió mal» y el `cotizacionRefreshing` se
apaga por la rama `next`. **Nada que actualizar del lado del cliente.**

**mobile y mobile-pwa: 0 usos, verificado sobre los repos clonados** (`frc-mobile` `c5b8f4c`, `frc-mobile-pwa` `caffe35`), no citado. Ninguno de los dos conoce la mutation ni los campos de cotización de mercado, así que el cambio de semántica no los alcanza.

## 6 · Migraciones

**N/A** — el diff no toca `src/main/resources/db/migration/`. Sin columnas, sin espejo en filial,
sin `ALTER PUBLICATION`. La tabla `financiero.cambio` no cambia de forma; el scheduler ya escribía
en ella y sigue escribiendo igual.

Por lo tanto **N/A también el dry-run de migración del paso 10**.

## 7 · Test que falla con el código viejo

`scheduledUpdateNoRetieneElHilo` es el test de regresión exigido por el paso 7. Con el
`CotizacionMercadoScheduler` anterior, `scheduledUpdate()` llamaba `actualizarCotizaciones()` en
línea, así que con un scraper que tarda 3 s el método tardaba 3 s y el assert de `< 1000 ms`
falla. Con el nuevo delega y vuelve en milisegundos.

`sinCotizacionesNoLanza` también falla con el código viejo: lanzaba
`RuntimeException("No se pudieron obtener cotizaciones…")`.

## 8 · Qué queda sin verificar

1. **La batería del paso 9 no se puede correr en este entorno.** `./mvnw clean verify -B
   -DskipFlyway=true` falla al resolver `io.github.gabfrank:jsifenlib:0.2.4-frc.13` desde GitHub
   Packages (`401 Unauthorized`): la sesión no tiene un `PACKAGES_PAT` con `read:packages` sobre
   `GabFrank/rshk-jsifenlib`. Se verificó sintaxis y estructura de los 4 archivos con `javac`
   aislado (solo errores de símbolos ausentes, ninguno estructural).
   **Mitigación: el CI corre el mismo comando con el PAT real. El veredicto es `gh pr checks`.**
2. **El paso 10 (build de producción) tampoco corre**, por lo mismo. El artefacto lo produce el CI.
3. **La causa raíz no tiene confirmación de runtime** (§1). No se capturó thread dump durante el
   incidente y ya no es reproducible a demanda.
4. **No se probó en el canal alpha.** El cambio no se ejercitó contra un central corriendo.

## 9 · Riesgo y rollback

**Riesgo: bajo.** Todo el diff está contenido en la integración de cotización de mercado: 3 clases
y una property. No toca esquema, ni `.graphqls`, ni autorización, ni replicación.

**Rollback:** revertir el commit. No hay estado que deshacer — sin migración, sin datos escritos
de forma nueva. La versión anterior del backend funciona contra el mismo esquema, y el cliente
anterior contra el backend nuevo (§5).

**Peor caso del cambio:** el `Future.get` con presupuesto podría cortar un scrape lento pero
sano (> 20 s) que antes hubiera terminado. Consecuencia: esa corrida no actualiza la cotización de
mercado y la siguiente lo intenta 10 min después. Se conserva la última cotización conocida.

**Lo que este PR NO arregla, a propósito:** el `spring.task.scheduling.pool.size=1` sigue en pie.
Cualquiera de las otras 18 tareas puede starvear a las demás por el mismo mecanismo; esto solo
saca de esa lista a la única que sale a internet. Subir el pool convierte 19 tareas hoy
serializadas en concurrentes — entre ellas las de replicación — y eso merece su propio PR con
tiempo en alpha. **Va como issue, no como parche colgado de este diff.**

## 10 · Pasos del ciclo — estado

| Paso | Estado |
|---|---|
| 1 · Rama | ✅ desde `origin/develop`. ⚠️ **Nombre fuera de convención**: `claude/frc-cicd-cotizacion-hotfix-y4o248` en vez de `fix/financiero-cotizacion-no-bloqueante`, impuesto por la configuración de la sesión. Además dice «hotfix» y ya no lo es |
| 2 · Skill de dominio | ✅ `frc-cicd` + `frc-central` |
| 3 · Análisis | ✅ re-verificado sobre `origin/develop` (19 `@Scheduled`, no 14 como en `master`) |
| 4 · Plan | ✅ este archivo. ⚠️ **posterior a la Fase 1** (§3) |
| 5 · Auditoría del plan | 2 agentes, ejes A y B |
| 6 · Presentar y commitear | |
| 7 · Implementación por fases | Fase 1 hecha (rebasada), Fase 2 pendiente |
| 8 · Auditoría del diff | 3 fijos + Condicional B (el diff toca un poller que integra datos replicados) |
| 9 · Batería | ⚠️ **INCUMPLIDO en local** — §8.1. Se delega al CI |
| 10 · Build de producción | ⚠️ **INCUMPLIDO en local** — §8.2. Dry-run de migración N/A |
| 11 · Documentación | gotcha del pool de `@Scheduled` → `frc-cicd/.claude/skills/frc-cicd/gotchas.md` (**PR aparte**, un PR por repo). Este plan se borra al cierre |
| 12 · Cierre | PR a `develop`, 6 secciones, aviso de reinicio, `gh pr checks` verde |

## 11 · Aviso de reinicio

Central: requiere `systemctl restart frc-<instancia>.service`; lo hace el workflow `Deploy`.
Merge a `develop` **no despliega nada solo** — el deploy del central es manual
(`workflow_dispatch`), y `alpha` es el único target sin revisor. Ninguna filial se entera de este
cambio: no hay nada que replicar.
