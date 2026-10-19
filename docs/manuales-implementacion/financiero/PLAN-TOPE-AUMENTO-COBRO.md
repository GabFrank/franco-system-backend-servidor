# Plan — tope del Aumento (F8) configurable (desktop #546)

Rama `feature/pdv-tope-aumento` en filial, central y desktop. Este plan muere en el PR final (ciclo §11).

## Qué resuelve

Hoy el aumento del cobro tiene un tope del 20% del total, clavado en el código y mal aplicado:

| Puerta | Hoy |
|---|---|
| Botón «Aumento (F8)» de `pago-touch` | deshabilitado si el aumento pasa el 20% (`pago-touch.component.html:687`) |
| Tecla F8 de `pago-touch` | solo mira `saldo < 0`: **sin tope** (`pago-touch.component.ts:394`) |
| `onAumento()` de `pago-touch` | `valor < data.valor * 0.5 && valor < 0`: la primera parte siempre se cumple, **sin tope** |
| Ícono de aumento del delivery (`edit-delivery-dialog`) | `vuelto <= total * 0.2` con vuelto negativo: siempre true, **sin tope** |

Decisión de Franco (19/10): tabla propia `financiero.configuracion_cobro`, MAIN_TO_ALL, editable por
**TESORERIA GESTIONAR** (o ADMIN). Opciones: con tope (porcentaje) o sin tope. Default: con tope, 20%.
La autorización por encima del tope queda para cuando exista el sistema de autorizaciones (fuera de alcance).

**Cambio de comportamiento visible:** con el desktop nuevo, F8 y el ícono del delivery pasan de «sin tope» a
«20%» aunque nadie toque la configuración. Es lo que pide la issue (botón y F8 iguales). Si se quiere evitar el
cambio de golpe, se guarda «sin tope» en el central y se ve replicado antes de liberar el desktop.

## Diseño

- Una sola fila global (sin override por sucursal: nadie lo pidió). En el central, un índice único sobre una
  constante impide una segunda fila; en el filial no va (una restricción del subscriber solo agrega formas de
  cortar la réplica).
- **El cobro lee la config del filial**, no del central: tiene que funcionar sin internet.
- **Sin fila, sin respuesta, todavía cargando o con columnas NULL = con tope del 20%.** El campo del componente
  nace con el default y solo una respuesta válida lo reemplaza: un F8 antes de que responda el filial usa el 20%.
- **La lectura del PDV va por Apollo directo** (`no-cache`, `errorPolicy: 'none'`, sin `GenericCrudService`) y
  en un documento gql propio con solo los dos campos, como `onGetTerminalObligatoria`. Así un filial sin la
  query (todavía sin desplegar) no muestra «Ups! Algo salió mal» en cada cobro: cae al 20% en silencio.
- **La decisión es una función pura**, `aumentoPermitido(aumentoGs, totalGs, config)`, que llaman las dos
  `onAumento()`. `aumentoGs` es **lo que se va a registrar** (el campo «valor», que el cajero puede editar,
  pasado a guaraníes con la cotización elegida) **más los aumentos que el cobro ya tiene**
  (`aumentosRegistradosGs`). No el saldo: el saldo se puede pasar por 1 Gs y registrar 500.000 de aumento.
  Compara en guaraníes enteros, `Math.round(aumento) <= Math.floor(Math.round(total × pct × 100) / 10000)`
  (el porcentaje tiene 2 decimales; sin el redondeo, 33,3% de 100.000 da 33.299). `total` es `data.valor` en
  `pago-touch` (ya incluye el delivery) y `venta.valorTotal + precio.valor` en el delivery.
- `onAumento()` de `pago-touch` limpia `isAumento` después de `addCobroDetalle()`: esa función sale sin registrar
  en varios casos (delivery en vuelo, convenio, config de tarjeta) y la marca quedaba puesta para el próximo Enter,
  que registraba un aumento sin pasar por el tope. En el delivery el Enter ya la limpia.
- El botón deja de tener el `-0.2` en su `disableExpression` (sigue deshabilitado con saldo ≥ 0): botón, F8,
  ícono del delivery y cobro de crédito pasan por el mismo chequeo y los cuatro avisan por snackbar.
- La condición que muestra el ícono del delivery (`<= total * 0.2`) no se toca: con vuelto negativo da siempre
  true y el chequeo real queda en `onAumento()`.

## Fases

### Fase 1 — filial (va primero: es el subscriber)

- `V106.1__espejo_configuracion_cobro.sql`: `CREATE TABLE IF NOT EXISTS financiero.configuracion_cobro`, mismas
  columnas y tipos que el central (`NUMERIC(6,2)` idéntico), **todas nullable, sin FK/CHECK/UNIQUE/seed** (patrón V103.1).
- `ConfiguracionCobro` (entidad read-only, getters efectivos NULL → true / 20), `ConfiguracionCobroRepository`,
  `ConfiguracionCobroService.findOrDefault()` (sin fila → default sin persistir), `ConfiguracionCobroGraphQL`
  (query `configuracionCobro`, sin rol: `SecurityGraphQLAspect` solo exige sesión), `configuracion-cobro.graphqls`.
- Tests: `ConfiguracionCobroServiceTest` — sin filas devuelve con tope 20 y no guarda; con fila la devuelve;
  getters efectivos con NULL.
- Gate: `./mvnw clean verify -B`.

### Fase 2 — central (publisher)

- `V251.1__configuracion_cobro.sql`: tabla con `aumento_con_tope BOOLEAN NOT NULL DEFAULT TRUE`,
  `aumento_tope_porcentaje NUMERIC(6,2) NOT NULL DEFAULT 20` + `CHECK (> 0 AND <= 100)`, `usuario_id` FK,
  `creado_en`, `modificado_en`, índice único sobre `((true))`; alta en `configuraciones.replication_table` como
  MAIN_TO_ALL. **Sin seed y sin ALTER PUBLICATION** (mismas razones que V231.1).
- Entidad, repo, `ConfiguracionCobroService`: `findOrDefault()` sin persistir y `findOrNew()` solo para el save.
  `usuario` EAGER y el tipo GraphQL expone solo `usuarioNickname` (un LAZY se resuelve fuera de la sesión de
  Hibernate y reventaba al reabrir el diálogo; además no abre el grafo de `Usuario`).
- `ConfiguracionCobroGraphQL`: query `configuracionCobro` **sin rol** que llama a **`findOrDefault()`** (abrir el
  diálogo no escribe nada). Sin rol porque el desktop web no tiene filial local y la pide al central con la
  sesión del cajero; si pidiera rol, a ese cajero le regiría siempre el 20%. Mutation
  `saveConfiguracionCobro(input)` con `seg.requireGestionar()` como primera línea, semántica PATCH, porcentaje
  llevado a 2 decimales y validado en (0, 100], usuario = `seg.currentUsuario()`.
- Tests: `ConfiguracionCobroGraphQLTest` — sin rol no guarda; porcentaje 0, negativo y > 100 rechazados; PATCH no
  pisa lo ausente; guarda el usuario de la sesión; la query no persiste.
- Gate: `./mvnw clean verify -B -DskipFlyway=true`.

### Fase 3 — desktop

- `financiero/configuracion-cobro/`: modelo + `aumentoPermitido()` con su spec (casos: −21% con config null
  bloquea — falla con la lógica vieja —; exactamente el tope permite; sin tope permite; tope con decimales y cobro
  en USD; saldo ≥ 0 no aplica), queries (la del PDV aparte), `ConfiguracionCobroService` (PDV por Apollo directo
  al filial; diálogo y save contra el central), `ConfiguracionCobroDialogComponent` (toggle + porcentaje +
  «modificado por»; si la query falla avisa «el servidor todavía no tiene esta configuración» y no deja guardar).
- Menú: Financiero › Configuración › «Cobro en PDV», `visibilityRoles: [TESORERIA_GESTIONAR, ADMIN]`; el handler
  vuelve a validar con `hasAnyRole([TESORERIA_GESTIONAR])`.
- `pago-touch`: carga la config del filial en `ngOnInit`; `onAumento()` usa `aumentoPermitido`; HTML sin el `-0.2`.
  El constructor cambia: se ajusta `pago-touch.component.spec.ts` si compila, o se anota entre los specs rotos.
- `edit-delivery-dialog`: misma carga y mismo chequeo en `onAumento()`.
- Gate: `npm run check` + el spec del helper con Karma acotado.

## Datos nuevos: quién escribe y quién lee

| Dato | Escribe | Lee |
|---|---|---|
| `aumento_con_tope` | central `saveConfiguracionCobro` ← `ConfiguracionCobroDialogComponent` | filial `configuracionCobro` → `pago-touch.onAumento` (venta y crédito), `edit-delivery-dialog.onAumento`; central `configuracionCobro` → el diálogo |
| `aumento_tope_porcentaje` | ídem | ídem |
| `usuario_id`, `modificado_en` | `saveConfiguracionCobro` (usuario de la sesión) | el diálogo («Modificado por … el …») |
| `creado_en` | `findOrCreate` del central | nadie lo muestra; por consistencia con las otras configuraciones |

## Puertas que respetan la configuración

1. Botón «Aumento (F8)» de `pago-touch` → `onAumento()`.
2. Tecla F8 de `pago-touch` → `onAumento()`.
3. Ícono de aumento del delivery → `edit-delivery-dialog.onAumento()`.
4. Cobro de ventas a crédito (Clientes › V. crédito › Cobrar): abre el mismo `PagoTouchComponent`
   (`list-venta-credito.component.ts:529`), así que pasa por 1 y 2 con `data.valor` = total de las cuotas. Hoy
   tiene el mismo 20% en el botón; el tope le aplica igual que al PDV.

Fuera de la configuración, a propósito: las líneas ya guardadas que se reconstruyen al reabrir un delivery
(no pasan por `onAumento`; bloquearlas haría desaparecer un cobro hecho), y el pago a proveedores
(`PagoProveedorService`), que tiene su propio `aumento` y no es un cobro. La PWA y el mobile solo leen
`aumento`, no lo crean.

**El backend no valida el tope**: el filial guarda lo que manda el desktop. Un desktop sin actualizar sigue con
el comportamiento de hoy (botón al 20%, F8 sin tope) hasta que actualice.

## Orden de despliegue (§3.2, MAIN_TO_ALL), en cada canal

| Canal | Filial (sale solo en ≤15 min al mergear/promover) | Central | Desktop |
|---|---|---|---|
| alpha (`develop`) | filiales alpha | mauro :8083 (deploy manual) | canal alpha |
| beta (`release/beta`) | **6 de farmacia** | :8082 (1 revisor) | canal beta |
| stable (`master`) | **18 de bodega** | :8081 (1 revisor) | canal latest |

En cada canal:

1. **Filial** con `106.1` en `flyway_schema_history` de cada filial del canal. Listar antes las filiales
   **apagadas o con una versión anterior** a la del canal: mientras haya una suscripta a `central_pub` sin la
   tabla, la primera escritura en el central le corta **toda** la bajada de `central_pub` (el apply worker queda
   en bucle con `target relation "financiero.configuracion_cobro" does not exist`), no solo esta tabla.
2. **Central**: registra la tabla en `replication_table`. En alpha la agrega el sync (con `REPLICA IDENTITY FULL`
   y `copy_data = false`). En farmacia y bodega los schedulers están apagados: el alta es manual y la hace
   **Gabriel** al desplegar ese central, siguiendo `frc-cicd/runbook-publicar-formatos-pos-farmacia.md`:
   - en el central: `ALTER TABLE financiero.configuracion_cobro REPLICA IDENTITY FULL;` (igual que lo deja el
     sync en alpha) y `ALTER PUBLICATION central_pub ADD TABLE financiero.configuracion_cobro;` (autocommit);
   - en cada filial, la suscripción **que lee `central_pub`** (por `subpublications`, `subenabled = t`; los
     nombres están invertidos entre bodega y farmacia): `ALTER SUBSCRIPTION … REFRESH PUBLICATION WITH
     (copy_data = true);`. Ojo: `copy_data` copia **todas** las tablas de `central_pub` que esa filial todavía no
     tiene en `pg_subscription_rel`. Antes, comparar `pg_publication_tables` del central contra
     `pg_subscription_rel` de la filial: si aparece otra tabla pendiente con filas en la filial, parar.
   - `REFRESH` no tiene ensayo ni rollback: si sale mal, la salida es el backfill del punto 6.
3. **Ninguna escritura** en `configuracion_cobro` hasta ver `srsubstate = 'r'` en cada suscripción y que no quede
   ninguna filial del punto 1 sin `106.1`.
4. **Desktop**. Regla: el desktop no se promueve a un canal hasta que el filial y el central de ese canal tengan
   lo suyo. Contra un filial viejo cae al 20% sin aviso; contra un central viejo el diálogo avisa y no guarda.
   Un desktop sin actualizar ignora la configuración (botón al 20%, F8 sin tope) hasta que actualice.
5. Recién ahí, el primer guardado desde el diálogo. Después, **verificar por conteo** en cada filial:
   `SELECT count(*) FROM financiero.configuracion_cobro` tiene que dar 1.
6. **Filial nueva, recreada o que vuelve después del primer guardado**: la tabla tiene una sola fila que se
   inserta una vez; con `copy_data = false` esa filial nunca la recibe y cada UPDATE posterior se descarta sin
   error (queda en el 20% para siempre). Arreglo: `REFRESH … WITH (copy_data = true)` cuando ya tiene `106.1`, o
   backfill de la fila (`pg_dump --data-only --column-inserts -t financiero.configuracion_cobro` del central).

Rollback: todo aditivo. Un JAR anterior ignora la tabla (`ddl-auto=none`; el filial tiene
`validate-on-migrate=false` y el central `ignore-missing-migrations=true`). Un desktop anterior ignora la config.

## Pruebas de runtime (local)

- Central con perfil `dev` (apaga `replication.sync` y `replication.refresh`; la `bodega` local tiene las IP
  reales de las filiales). Filial con perfil `dev` y `sifen.enabled=false`. Desktop con `ng serve -c web`.
- La base local del filial (`general` en el 5551) tiene sus 4 suscripciones desactivadas (verificado 19/10). La
  fila de prueba se inserta con **`id = -1`** y se **borra al terminar** (`DELETE … WHERE id = -1`, verificado).
  El alpha (`general` en el 5552, con suscripción activa) no se toca.
- Casos por pantalla: PDV con 20% (botón, F8, en guaraníes y en USD, justo en el tope y 1 Gs arriba); sin tope;
  tope 50%; delivery; cobro de crédito; filial sin la fila (default); diálogo con TESORERIA GESTIONAR, sin el
  rol (no aparece en el menú) y validación del porcentaje; desktop contra un filial sin V106.1 (sin cartel).

## Qué queda sin verificar y cómo

- **La replicación real de la tabla**: en local central y filial no replican. Se verifica en alpha después del
  merge, con `pg_subscription_rel`.
- **Migraciones contra una copia real** (paso 10): se aplican en `bodega` y `general` locales, copias de producción.

## Hallazgos de la auditoría del plan (paso 5) y qué se hizo

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Lectura por `GenericCrudService` muestra «Ups» en cada cobro contra un filial viejo | A y B | Apollo directo + query propia del PDV |
| Falta la puerta del cobro de crédito | A | Agregada como puerta 4 |
| Tope «sin tope» mientras carga la config | B | Campo nace con el default 20% |
| Sin test que falle con el código viejo | B | `aumentoPermitido()` puro, usado por las dos `onAumento`, con spec |
| Redondeo al comparar en el borde del tope | B | Comparación en guaraníes enteros |
| La query del central podía insertar la fila | B | La query usa `findOrDefault()` |
| Fila de prueba en `general` podía cortar la réplica | B | Es la del alpha (5552); la local no tiene suscripciones activas. Igual: `id = -1` y borrado |
| Paso 10 sin perfil toca filiales reales | B | Perfil `dev` explícito |
| Orden sin canales ni dueño del alta manual | A | Tabla por canal; Gabriel hace el alta en farmacia/bodega |
| Diálogo contra un central viejo | A | Avisa y no guarda |
| Spec de `pago-touch` con constructor nuevo | A | Se ajusta o se anota |
| Dos filas por guardados simultáneos | B | Índice único en el central |
| «No cambia nada» era falso para F8 y el delivery | B | Anotado como cambio de comportamiento visible |

## Hallazgos de la auditoría del diff (paso 8) y qué se hizo

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| El tope miraba el saldo y se registra «valor» (editable): 1 Gs de más dejaba registrar cualquier aumento | Fijo 3 | Se mide la línea a registrar en Gs más los aumentos ya cargados |
| Dos aumentos seguidos (borrando la línea de pago) pasaban el tope cada uno | Fijo 3 | `aumentosRegistradosGs` los acumula |
| `isAumento` quedaba puesto si `addCobroDetalle` salía antes: el Enter siguiente lo registraba sin tope | Fijo 3 | `onAumento` lo limpia después de la llamada |
| `Math.floor` perdía 1 Gs con porcentajes decimales (33,3%) | Fijo 3 | Redondeo de `total × pct × 100` antes de cortar, con spec |
| La lectura del PDV esperaba 60 s y avisaba timeout | Fijo 3 | Corte de mostrador sin aviso (`CONTEXTO_MOSTRADOR`) |
| Desktop web sin filial: la query del central pedía rol y al cajero le regía siempre el 20% | Fijo 3 | Query del central sin rol |
| `usuario` LAZY resuelto fuera de sesión rompía el diálogo al reabrir | Fijo 2 | EAGER + `usuarioNickname` |
| `usuario: Usuario` abría todo el grafo del usuario a quien leía la config | Fijo 1 y 2 | Solo `usuarioNickname` |
| `0.004` pasaba la validación y violaba el CHECK como error opaco | Fijo 2 | `setScale(2)` antes de validar, con test |
| Filiales apagadas/viejas, alta manual sin precisión, filial que pierde el INSERT | Cond. B | Orden de despliegue, puntos 1, 2, 3, 5 y 6 |
| El diálogo culpaba al central de cualquier error | Fijo 3 | Texto neutro |
| El tope solo se controla en el cliente (el filial guarda lo que llega) | Fijo 1 | Sin cambio: igual que antes, fuera del alcance de la issue |
| En el delivery un aumento se guarda también con `vuelto = true` | Fijo 3 | Sin cambio: viene de antes y no lo toca este trabajo; anotado para Gabriel |
