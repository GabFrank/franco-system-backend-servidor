# Plan de implementación — fase 2: captura del cupón por cámara

Acordado con Gabriel el **2026-09-09**, después de cerrar por medición los cuatro riesgos que podían
cambiar la arquitectura. El **qué** y el **por qué** viven en
[FASE-2-TICKET-FISICO.md](FASE-2-TICKET-FISICO.md); acá está el **cómo, en qué orden y con qué
proceso**.

> Este plan se ejecuta siguiendo **`frc-cicd/ciclo-implementacion-frc-comercial.md`** —el ciclo de
> 12 pasos, obligatorio— y su complemento de git `frc-cicd/guia-desarrollo-cicd.md`. Los dos viven
> en otro repo, no enlazables desde acá.
>
> **Este archivo muere al cierre** (paso 11): las verdades que sobrevivan se mudan a
> [FASE-2-TICKET-FISICO.md](FASE-2-TICKET-FISICO.md) y **el plan se borra en el PR final de la rama
> de trabajo**. Donde este plan se aparta de la guía, lo dice explícitamente y con el motivo.

---

## 1 · Lo que se entrega

Un cajero que no puede escanear el QR del cupón —porque el POS no lo imprime, porque salió borroso,
o porque el papel está arrugado— **le saca una foto con su teléfono y los campos se completan solos
en la caja**.

Alrededor de eso, el módulo se termina: configuración por POS, evidencia guardada, el papel suelto
resuelto, y el diálogo que cobraba un peaje sin dar nada, eliminado.

### Lo que NO entra

| | Motivo |
|---|---|
| **§3.5 · POS con recargo** | Se necesita saber primero cómo cobran realmente los POS de la red. Los cupones con recargo van a seguir pidiendo confirmación de monto |
| **Promoción a farmacia o bodega** | Esta entrega llega hasta `develop` / alpha. La promoción se decide después, en su propia conversación |
| **Permiso nuevo para la foto** | No hace falta: quien puede hacer una venta con tarjeta ya tiene permiso. Ver §3.2 |

---

## 2 · Decisiones tomadas

### 2.1 · Flujo

| Decisión | Elegido |
|---|---|
| **Cuándo se saca la foto** | Durante la venta, como alternativa al QR. La venta no avanza hasta resolver el cupón |
| **Espera** | **Síncrono**, con indicador de progreso. 2-3 s en filial típica, hasta 5,5 s en la más débil |
| **Dónde se confirman los campos** | **En el desktop.** El teléfono sólo saca y sube: sigue siendo un periférico, no una pantalla de la suite |
| **Emparejamiento** | Un QR por cupón, generado en el momento |
| **Cupón que sí tiene QR** | La foto sirve igual, **mismo camino sin distinguir**. No se decodifica QR desde la imagen |
| **Campos obligatorios** | **Configurables por POS** |

### 2.2 · El token del QR es la única credencial

**Un solo uso, expira en minutos, atado a esa venta y esa caja.** Se consume con la primera subida.

El teléfono no lleva login ni rol: el QR sólo lo puede mostrar un desktop que ya pasó las puertas
—caja abierta, rol de venta, flujo habilitado—, así que la autorización ya ocurrió antes del QR.
Aunque alguien fotografiara la pantalla, el token no le sirve después ni para otra venta.

### 2.3 · Campos extra: configuración, no migración

`venta_tarjeta` gana **una columna `jsonb` de datos adicionales**, una sola vez.

El `mapeo` declara qué valor extraído va a cada campo canónico (`monto`, `codigo_autorizacion`,
`numero_boleta`, `terminal`); **todo lo demás se guarda como clave-valor**.

Esto resuelve dos cosas de una:

- **PlugPay** imprime dos montos en dos monedas (`USD113.24` / `PYG661.309`): el mapeo dice cuál es
  el de la venta y el otro queda como dato adicional.
- **Un proveedor nuevo con campos propios** —`STONEID`, `COD.TRANS.`, lo que aparezca— se resuelve
  **desde el ABM, sin código, sin migración y sin propagar a 24 filiales**.

Es la misma decisión que permite que FRCP1 sume `terminal` sin release.

### 2.4 · Extracción de campos: se construyen las dos y se mide

| Camino | Qué da | Qué cuesta |
|---|---|---|
| **Regex sobre el texto** | Reusa `patron` + `mapeo` de `formato_qr_pos`, que ya existe con su ABM. El OCR devuelve texto igual que el QR: es la misma mecánica | No restringe qué se reconoce |
| **Mapa espacial por POS** | Regiones por campo → **se reconocen 6 cajas en vez de 26**. Baja el peor caso de 5,5 s a ~2,3 s | Editor sobre la imagen y resolver el anclaje a la etiqueta |

**No se elige por intuición.** Se construyen los dos y la etapa 4 los compara con criterio escrito
(§5.4). El mapa espacial no es sólo asignación: es **la palanca de rendimiento**, y por eso merece
medirse en serio en vez de darse por bueno.

### 2.5 · Backlog de §3

| Ítem | Alcance acordado |
|---|---|
| **3.1 · Ticket con seña** | Se imprime **y lleva un QR** que abre ese registro pendiente. El papel te devuelve a la pantalla correcta |
| **3.2 · Guardar la imagen** | Sí, **aunque el OCR falle**. La imagen es la evidencia |
| **3.3 · Adjuntos al cierre de caja** | **Genérico**: cualquier papel, con tipo, descripción y observación |
| **3.4 · Carga manual configurable** | Campos obligatorios por POS **y** si la carga manual está permitida por POS |
| **3.5 · POS con recargo** | **Fuera de esta entrega** |
| **3.6 · Terminal `MAQUINA` / `WEB`** | Tipo en la terminal, selección filtrada, **y la terminal viajando en el mapeo del QR** |
| **3.7 · Input único** | **Entra.** Un campo que prueba primero como QR de cupón y cae a código de terminal si ningún patrón matchea |
| **Retención de imágenes** | Configurable, con un valor por defecto razonable y job de purga |

---

## 3 · Proceso acordado

### 3.1 · Ramas y PRs

| | |
|---|---|
| **Ramas** | **Una por repo para toda la entrega**, creada desde `develop` |
| **PRs** | **Uno por repo, al final** |
| **Merge** | Merge commit, **nunca squash** |
| **Canal** | Sólo hasta `develop` / alpha |
| **Revisión** | Los PRs los abro yo con descripción, riesgo e impacto en DB; **los revisa y mergea Gabriel** |

```
feature/ocr-cupon-fase2   ← misma rama en central, filial y desktop
```

> ### Por qué un solo PR al final: alpha es compartido
>
> **Alpha no es el entorno de integración de esta entrega: es de todo el equipo.** Otras personas
> levantan alpha para probar **sus fixes y features terminados**. Si esta entrega le va metiendo
> piezas granulares a medida que se escriben, el próximo que necesite probar algo suyo se encuentra
> con trabajo a medio hacer encima y no puede.
>
> **El workspace de desarrollo es el local.** Todo se construye y se prueba contra
> `./mvnw spring-boot:run` (central y filial) y `npm start` (desktop). **Hasta el merge final no hay
> nada en alpha, y eso es el objetivo, no una contrapartida.**
>
> Corolario para quien ejecute este plan: **no proponer mergear algo para desbloquear el trabajo
> propio.** Si una rama necesita otra que aún no se mergeó, se ramifica de ella o se espera.
> Pushear una rama de feature es inocuo —corre CI, no genera release ni deploy—; lo que hay que
> cuidar es el merge.

> **Corregido el 2026-09-09.** Una versión previa de este plan decía que «un PR por repo» se
> apartaba de la guía, como si fuera una preferencia de tamaño. **No lo es**: coincide con el paso 7
> del ciclo de implementación **y** es lo que exige que alpha reciba sólo trabajo terminado. Lo de
> las 400 líneas viene de `guia-desarrollo-cicd.md`, que es el flujo de git, no el ciclo.

### 3.2 · Commits

Formato `tipo(scope): descripcion en minusculas`, sin mayúscula inicial y sin punto final.
Scope sugerido: `venta-tarjeta`, `ocr`, `caja`.

Sólo `feat` y `fix` generan versión. Como el PR se mergea entero al final, **la versión la decide el
conjunto de commits de la rama**: habrá `feat`, así que sube MINOR alpha.

### 3.3 · Migraciones Flyway

**Sufijo `.5` siempre**, nunca `.0` ni el entero pelado. Verificado el 2026-09-09:

| Repo | Última en `origin/develop` | Reservado para esta entrega |
|---|---|---|
| central | **`V220.1`** (`rrhh_bono_recurrente`, commit `7efce9a9`) | **`V220.5` en adelante** |
| filial | `V92.5` | **`V93.5` en adelante** |

`V220.1` y `V220.5` **no colisionan**: Flyway no normaliza `.1` a `.5`.

> ⚠️ **Corregido el 2026-09-09 por la auditoría del paso 5.** La primera versión de este plan decía
> «última: `V219.5`, verificado». **La verificación se hizo contra el checkout local, que estaba
> atrás de `origin/develop`** — que ya tenía `V220.1`, de otro desarrollador y fuera de esta fase.
> No hubo colisión por suerte, no por método.
>
> **Antes de crear la rama hay que hacer `git fetch origin develop` real en los tres repos y
> re-confirmar que `V220.5` sigue libre.** Y el PR #282 de central sigue sin traer migraciones.

> ### ⚠️ El orden de despliegue depende de la dirección de replicación
>
> No es «central siempre primero»:
>
> | Tabla | Dirección | Quién migra primero |
> |---|---|---|
> | `venta_tarjeta` (columna nueva) | `BRANCH_TO_MAIN` — filial publica, central suscribe | **central** |
> | Tablas de configuración nuevas | `MAIN_TO_ALL` — central publica, filial suscribe | **filial** |
>
> **NO VERIFICADO.** La primera versión de este plan afirmaba que «en alpha no muerde porque corre
> con `replication.sync.enabled=false`». **No se pudo confirmar**: el default de
> `application.properties` es `true`, sólo los perfiles `dev`/`ci` lo apagan, y el `.env` real de
> `/opt/frc-backend-central/alpha/` en mauro **no es legible sin sudo con contraseña** (intentado el
> 2026-09-09). El flag apagado está confirmado para otros hosts, no para mauro.
>
> **Cómo se verifica:** `grep -i replication.sync /opt/frc-backend-central/alpha/.env` en mauro, con
> sudo. Hasta entonces, tratar el orden de publicación como si importara también en alpha.
>
> Las publicaciones **no son `FOR ALL TABLES`**: cada tabla nueva necesita su
> `ALTER PUBLICATION ... ADD TABLE` explícito.

### 3.4 · Validación

**Guía paso a paso, una prueba por vez** — el formato que funcionó en las 8 pruebas del 2026-09-08:
los pasos, qué ítems cargar, qué terminal usar y los datos exactos; Gabriel la ejecuta y yo confirmo
contra la base antes de pasar a la siguiente.

Cada etapa cierra con su guía. Nada se da por bueno sin evidencia en DB.

---

## 4 · Etapa 0 — cerrar la fase 1

Decidido: **mergear ahora, probar después.**

| Repo | Qué |
|---|---|
| desktop | Mergear **PR #275** — acceso desde el PDV, confirmación de diferencias, fixes de escaneo. CI verde |
| central | Mergear **PR #275** — guía de testeo, hallazgos y todo lo medido de fase 2 |

**Deuda que se arrastra a esta entrega** (no bloquea el arranque):

- El `.trim()` al guardar la configuración — hallazgo 2, fix pendiente
- Seis casos sin probar: pago mixto, con factura legal, venta sin timbrado, aviso en cierre de caja,
  boleta vacía (`**`), y los gates del acceso nuevo

---

## 5 · Etapas de construcción

### 5.1 · Etapa 1 — el motor OCR dentro del filial

Sin nada visible todavía. Termina cuando el filial puede leer un cupón desde un test.

| Repo | Trabajo |
|---|---|
| **filial** | Dependencia `com.microsoft.onnxruntime:onnxruntime` **podada a linux-x64 + win-x64** en el build. Los 3 modelos y `ppocr_keys.txt` como **recursos del JAR**. El motor del spike bajado a **Java 8** (hoy usa `instanceof` con patrón y `List.of`, que son Java 9+/16+). `CuponOcrService` como **singleton** — la sesión de ORT cuesta ~100 ms de crear y es segura de reusar |

**Verificación al arrancar:** el largo del diccionario contra la dimensión de salida del modelo.
Aborta el arranque en vez de producir texto sutilmente equivocado — la trampa de Modified UTF-8 de
§2.9 costó dos intentos y se manifestaba como «faltan los espacios».

**Prueba:** un test que corre el motor sobre cupones de referencia y verifica los campos.

### 5.2 · Etapa 2 — la foto llega del teléfono a la caja

Termina cuando el cajero saca una foto y ve el texto crudo en el desktop.

| Repo | Trabajo | Migración |
|---|---|---|
| **central** | Columna `jsonb` de datos adicionales en `venta_tarjeta` | `V220.5` |
| **filial** | Misma columna. Tabla de tokens de captura (local, **no replica**). Página de captura estática. `GET`/`POST /captura/{token}`. Guardado de la imagen | `V93.5`, `V94.5` |
| **desktop** | Botón de foto en el diálogo del cupón, QR con el token, espera con indicador, resultado en pantalla | — |

**La página de captura la sirve el filial por HTTP en el puerto que ya está abierto.** Origen privado
→ privado: sin contenido mixto, sin permiso de red local, sin certificado. Ver §2.8 de FASE-2.

Del lado del teléfono, lo verificado el 2026-09-09 y que hay que conservar: **orientar por EXIF
detectando qué hizo el navegador** (comparando contra el marcador `SOF` del JPEG, no asumiendo),
**escalar a 1000 px** y comprimir a 0.9.

### 5.3 · Etapa 3 — los campos se completan solos

| Repo | Trabajo | Migración |
|---|---|---|
| **central** | Formato de cupón OCR: `patron` + `mapeo`, con la misma mecánica que `formato_qr_pos`. ABM GraphQL | `V221.5` |
| **filial** | Aplicar el mapeo al texto del OCR: campos canónicos + el resto a datos adicionales | `V95.5` |
| **desktop** | ABM del formato. Confirmación con **semáforo por campo** según confianza (§2.6): los buenos se aplican, los dudosos se preguntan |

**Un campo declarado numérico rechaza `0i64`.** Eso captura gratis el tipo de error que ni Java ni
Python evitan, sin perseguir paridad binaria entre motores.

### 5.4 · Etapa 4 — mapa espacial, y la medición que decide

| Repo | Trabajo | Migración |
|---|---|---|
| **central** | Regiones por campo y por POS | `V222.5` |
| **filial** | Restringir el reconocimiento a las regiones del mapa | `V96.5` |
| **desktop** | Editor de regiones sobre la imagen del cupón |

**Criterio de la comparación, escrito antes de medir:**

1. Sobre el mismo lote de cupones, con los dos caminos activos
2. Se comparan **latencia total** y **campos correctos contra el papel** — no líneas ni confianza
   media, que ya demostraron no distinguir nada
3. El mapa espacial se adopta si **baja la latencia sin perder campos**. Si empata o pierde, se
   queda el regex y el editor se archiva documentado

### 5.5 · Etapa 5 — terminar el módulo

| Ítem | Repos | Migración |
|---|---|---|
| **§3.7 · Input único** | desktop | — |
| **§3.6 · Tipo de terminal + terminal en el mapeo** | central, filial, desktop | `V223.5` / `V97.5` |
| **§3.4 · Configuración por POS** | central, filial, desktop | `V224.5` / `V98.5` |
| **§3.1 · Ticket con seña con QR** | filial (impresión), desktop | — |
| **§3.3 · Adjuntos al cierre de caja** | central, filial, desktop | `V225.5` / `V99.5` |
| **Retención y purga de imágenes** | filial | incluida en `V224.5` |

**§3.7 va al final a propósito**: cambia el flujo que Gabriel ya probó en las 8 pruebas manuales, y
no conviene mover el piso mientras se construye encima.

## 5.6 · Reglas duras que aplican mientras se escribe el código

Salieron de la skill de dominio (paso 2) y de la auditoría (paso 5). **Ninguna se deduce leyendo el
código**, y tres de ellas ya rompieron producción antes.

### Seguridad — no existe `@PreAuthorize` en este repo

**Cero usos** de `@PreAuthorize`, `@Secured` o `@RolesAllowed` en todo `src/main/java`. Un resolver
nuevo se protege **inyectando `TesoreriaSecurityService`** y llamando `requireVer()` (query) o
`requireGestionar()` (mutation) **como primera línea del método**. `@AdminSecured` no sirve: está
roto de punta a punta (issue #177) y no hay un solo método anotado con él.

Aplica a **todo** el ABM que agrega esta entrega: formato de cupón OCR, regiones por POS,
configuración por POS, tipo de terminal. Si no se inyecta a mano, **quedan abiertos**.

### Un enum nuevo va a TRES lugares en el mismo commit

`terminal_pos.tipo` (`MAQUINA` / `WEB`) es un enum. Va en **Java + `.graphqls` + migración**, todo
en el mismo commit. Y en PostgreSQL se extiende con `ALTER TYPE ... ADD VALUE` idempotente, **sin
usar el valor nuevo en la misma transacción**.

> **Es la falla más silenciosa del repo.** No rompe el build ni el CI: graphql-java loguea un WARN y
> devuelve `null`. Ya pasó con `EstadoPreGasto.PAGADO` (`V197.5`), que **tumbó la caja chica de la
> mobile-pwa**. `SchemaEnumsSincronizadosTest` existe justamente para esto y **hay que correrlo
> antes del PR**.

### Todo campo nuevo de input es opcional

`mobile` sigue instalada, consume `VentaTarjeta` y `TerminalPos`, y **sólo se actualiza por release
manual de Play Store**: no puede seguir el ritmo de esta entrega. Hoy todos los campos de
`VentaTarjetaInput` y `TerminalPosInput` son opcionales — **mantener esa convención**.

### Los schedulers van apagados por default

El job de purga de imágenes lleva `@ConditionalOnProperty(..., matchIfMissing=false)`, como
`tesoreria.retiro-poller.enabled` y `tesoreria.acreditacion-pos.enabled`. Se enciende por propiedad,
no por existir.

### La carpeta de imágenes se crea sola

`Files.createDirectories()` en el primer guardado. **No asumir que el servidor la tiene.** Si la ruta
llega por variable de entorno, hay que provisionarla **antes** de que el código se despliegue o la
app no arranca.

---

## 5.7 · Coreografía del merge — las dos mitades de alpha no llegan juntas

**Hallazgo crítico de la auditoría.** Este plan decía «todo llega junto a `develop`/alpha». Es falso:

| Mitad | Cómo se actualiza |
|---|---|
| **filial-alpha** (mauro) | **Solo, cada 15 min, sin aprobación**, apenas `semantic-release` corta el tag |
| **central-alpha** (mauro) | **`workflow_dispatch` manual.** Sin revisor, pero alguien tiene que apretarlo |

Entre esos dos momentos el filial corre código nuevo **contra un central que todavía no tiene las
tablas**. Las tablas de configuración de las etapas 3 a 5 son `MAIN_TO_ALL`, así que sí muerde.

**Procedimiento obligatorio al mergear:**

1. Mergear **primero el PR de central**
2. **Disparar el Deploy de central a `alpha` de inmediato**, sin esperar
3. Recién entonces mergear el PR de filial
4. Confirmar que mauro tomó la versión (`.current-version` y `/api/version`)

Alternativa si hace falta más margen: **pausar el cron de `check-update.sh` en mauro** durante la
ventana de merge y reanudarlo cuando central ya esté arriba.

---

## 5.8 · Antes de abrir los PRs: ensayar las migraciones

**Hallazgo alto de la auditoría.** Con un PR por repo al final, el merge aplica de una vez ~6
migraciones en central y ~7 en filial. Y **el CI no las valida**: central corre con
`-DskipFlyway=true` y en filial no hay ningún test que levante contexto Spring. La primera vez que
tocan una base real es cuando ya están corriendo — y en el filial, **sin ningún gate humano**.

**El repo ya tiene el procedimiento:** `frc-cicd/.claude/skills/frc-cicd/runbooks/dry-run-migration.md`.
Correrlo contra una copia de la base de alpha con el JAR final de la rama, **antes de abrir el PR**.
Este plan lo adopta como paso obligatorio.

**Y en la descripción de cada PR**, columna por columna: qué hace el backend **viejo** si la lee
(ignorarla) y qué falla si la escribe (nada, porque no la escribe). El rollback re-apunta el symlink
y reinicia — **nunca toca la base**.
---

## 6 · Riesgos vivos

| Riesgo | Estado |
|---|---|
| **`Cargo: 002511` leído `802511`** | Abierto, y **es el error más grande del módulo** — lo fallan los dos motores. Más resolución sobre esa línea, o un segundo pase, son las hipótesis a probar |
| **Rendimiento con la filial bajo carga** | Lo medido fue con la máquina ociosa. ORT toma todos los núcleos por defecto: falta decidir si limitar `intra_op_num_threads` para no ahogar la aplicación |
| **Topología de red de una sucursal real** | La captura se probó en una LAN doméstica. Falta confirmar que la WiFi que usan los cajeros alcanza al filial |
| **El JAR del filial engorda, y nadie limpia** | +8 MB de ORT podado y +15,5 MB de modelos. Peor: **`check-update.sh` nunca borra `releases/<version>/`** — cada JAR descargado queda en disco para siempre, en 24 sucursales. El incremento no es un evento único: se repite en **cada release futura**. Purgar releases viejas es **prerequisito de promoción**, y va en el script, no en esta entrega |
| **Disco lleno en un filial** | Las imágenes, `releases/` y la base PostgreSQL **comparten disco**. Un disco lleno no sólo rompe el guardado de fotos: **impide que Postgres escriba WAL y tumba todas las ventas de esa sucursal**. La purga necesita un umbral de espacio libre que alerte, no sólo retención por antigüedad. Y hay que confirmar en una filial real en qué partición viven las tres cosas |
| **El cajero posterga la actualización del desktop** | `autoDownload=false` y la instalación pide consentimiento: se puede posponer **indefinidamente**. Un desktop viejo contra un central nuevo es exactamente el escenario del incidente de `EstadoPreGasto` |

---

## 7 · Checklist de la guía, antes de cada PR

- [ ] La rama salió de `develop`
- [ ] Commits en formato `tipo(scope): descripcion en minusculas`
- [ ] Compila y los tests básicos pasan (los de Jasper **se validan por CI**, revientan por memoria en local)
- [ ] Migraciones con sufijo `.5`, **aditivas**, sin `DROP` ni `RENAME`
- [ ] Compatibles con la versión anterior del backend
- [ ] La API GraphQL no rompe desktop ni mobile
- [ ] Sin secretos, sin `.env`, sin claves
- [ ] Descripción del PR: qué resuelve, cómo probarlo, riesgo, impacto en DB y en rollback
- [ ] `npm run check` corrido en desktop antes de pushear
- [ ] **Resolvers nuevos con `TesoreriaSecurityService`** — `requireVer()` / `requireGestionar()` como primera línea (§5.6)
- [ ] **Enum nuevo en los tres lugares** y `SchemaEnumsSincronizadosTest` corrido (§5.6)
- [ ] **Campos nuevos de input opcionales** — `mobile` no puede mandarlos (§5.6)
- [ ] **Dry-run de las migraciones** contra una copia de la base de alpha (§5.8)
- [ ] La descripción del PR dice, **columna por columna**, qué hace el backend viejo con el esquema nuevo (§5.8)
- [ ] `git fetch origin develop` y re-confirmar que los números de migración siguen libres (§3.3)

---

## 8 · Registro del ciclo — pasos incumplidos y auditoría

El ciclo de implementación pide que **un paso incumplido se anote, no se borre**. Esto es ese
registro.

### 8.1 · Pasos que se hicieron fuera de orden

La primera versión de este plan se escribió leyendo `guia-desarrollo-cicd.md` —el flujo de git— **en
lugar de `ciclo-implementacion-frc-comercial.md`**, que es el ciclo obligatorio. Consecuencia:

| Paso | Qué pasó | Corregido |
|---|---|---|
| **1 · Rama** | El plan se commiteó en `docs/testeo-venta-tarjeta-qr`, no en una rama de trabajo | Las ramas `feature/ocr-cupon-fase2` se crean al aprobarse |
| **2 · Skill de dominio** | No se cargó ninguna. Correspondía `frc-financiero-expert` | Cargada. Aportó las cuatro reglas de §5.6 |
| **3 · Análisis** | Se hizo sin las skills y sin `gotchas.md` | Cubierto por la auditoría |
| **5 · Auditoría** | **No se corrió antes de mostrar el plan** | Corrida. Resultados abajo |
| **6 · Commit del plan** | **Se commiteó antes de auditarlo y antes de aprobarse** | El commit queda; la corrección va encima, que es lo que el ciclo pide |

### 8.2 · Auditoría del paso 5 — dos ejes, sin verse

**Eje A — contrato y propagación:** 4 riesgos. **Eje B — reversibilidad y estado:** 7 riesgos.
Ninguno se contradijo; **dos coincidieron de forma independiente** en el enum.

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | **Crítico.** filial-alpha se actualiza solo en 15 min; central-alpha exige dispatch manual. No llegan juntos | **§5.7**, con procedimiento de merge |
| A2 | La carpeta de imágenes: nadie la crea | **§5.6** |
| A3 · B5 | `terminal_pos.tipo` es el patrón que tumbó la caja chica de la PWA | **§5.6**, con `SchemaEnumsSincronizadosTest` |
| A4 | `mobile` es consumidor ciego y no puede seguir el ritmo | **§5.6**, campos opcionales |
| B1 | **Alto.** ~13 migraciones que el CI no valida, aplicadas de una vez, y el filial sin gate humano | **§5.8**, dry-run obligatorio |
| B2 | **Alto.** El rollback no revierte la base | **§5.8**, documentar columna por columna en el PR |
| B3 | **La numeración se verificó contra un checkout viejo.** `origin/develop` ya tenía `V220.1` | **Verificado y corregido en §3.3** |
| B4 | `check-update.sh` nunca purga `releases/` | **§6**, prerequisito de promoción |
| B6 | La purga no tiene diseño de fallo, y el disco es compartido con el WAL de Postgres | **§6** |
| B7 | La afirmación sobre `replication.sync.enabled` en alpha **no estaba verificada** | **Marcada NO VERIFICADO en §3.3**, con el comando que la resolvería |

### 8.3 · Lo que sigue sin verificar

1. **`replication.sync.enabled` en mauro.** El `.env` no es legible sin sudo con contraseña. Se
   resuelve con `grep -i replication.sync /opt/frc-backend-central/alpha/.env`.
2. **En qué partición viven `releases/`, las imágenes y los datos de PostgreSQL** en una filial real.
3. **La aditividad real de las migraciones**: hoy no existen como archivos. La auditoría revisó la
   intención declarada, no código. Es lo que el dry-run de §5.8 viene a cubrir.
