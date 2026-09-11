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

**Sufijo `.5` siempre**, nunca `.0` ni el entero pelado. Verificado contra `origin/develop`
el **2026-09-10**:

| Repo | Última en `origin/develop` | Reservado para esta entrega |
|---|---|---|
| central | **`V221.1`** (`funcionario_cobra_banco`, commit `3c399753`) | **`V220.5` y `V221.5`–`V227.5`** |
| filial | **`V93.1`** (espejo de `cobra_banco`, commit `30b96d7e`) | **`V93.5`–`V100.5`** |

Los `.1` y los `.5` **no colisionan**: Flyway no normaliza `.1` a `.5`. Por eso `V220.5` sigue
libre aunque central ya vaya por `V221.1`.

> ⚠️ **Esta tabla se pone vieja sola, y ya pasó dos veces.**
>
> - **2026-09-09**: el plan decía «última: `V219.5`, verificado». La verificación se había hecho
>   contra el **checkout local**, que estaba atrás de `origin/develop` — que ya tenía `V220.1`, de
>   otro desarrollador y fuera de esta fase. No hubo colisión por suerte, no por método.
> - **2026-09-10**: corregida a `V220.1` / `V92.5` con un `fetch` real… y **al día siguiente ya
>   estaba vieja otra vez**: `V221.1` y `V93.1` se pushearon esa misma tarde.
>
> La conclusión no es «corregir la tabla mejor». Es que **el número escrito acá no es fuente de
> verdad, y el `git fetch origin develop` del §7 no es opcional**: hay que rehacerlo el día que se
> crea la rama y otra vez el día que se abre el PR. Lo único estable es la regla del sufijo: los
> `.5` reservados no los toca nadie más, porque el resto del equipo usa `.1`.

> ### ⚠️ El orden de despliegue depende de la dirección de replicación
>
> No es «central siempre primero»:
>
> | Tabla | Dirección | Quién migra primero |
> |---|---|---|
> | `venta_tarjeta` (columnas nuevas: `datos_extra`, `origen`) | `BRANCH_TO_MAIN` — filial publica, central suscribe | **central** |
> | `terminal_pos` (FK al formato) | `MAIN_TO_ALL` — central publica, filial suscribe | **filial** |
> | `formato_terminal_pos` (tabla nueva) | `MAIN_TO_ALL` | **filial** |
> | Tablas de configuración nuevas | `MAIN_TO_ALL` | **filial** |
>
> **La etapa 3 tiene las dos direcciones a la vez** y por eso es la que más fácil se hace mal:
> `venta_tarjeta.origen` exige central primero, el formato y su FK exigen filial primero. No hay un
> orden único para la entrega: son dos pasos separados.
>
> Y `formato_qr_pos` **no se renombra**. El motivo no es el que decía la primera versión de este
> plan («la publicación la referencia por nombre») — eso es **falso**, y se comprobó:
>
> ```sql
> BEGIN;
>   ALTER TABLE financiero.formato_qr_pos RENAME TO formato_terminal_pos_rename_test;
>   SELECT * FROM pg_publication_tables WHERE tablename LIKE 'formato%';  -- sigue ahí, con el nombre nuevo
> ROLLBACK;
> ```
>
> Postgres trackea la membresía de una publicación por **OID de la relación** (`pg_publication_rel`),
> no por nombre: un `RENAME` en el publicador no saca la tabla de la publicación.
>
> **Lo que sí rompe es el otro lado.** El protocolo de replicación lógica identifica la relación
> **en el suscriptor** por `schema.nombre`. Si central renombra y el filial no, el apply worker del
> filial busca `financiero.formato_qr_pos`, no la encuentra, y **se detiene** — el mismo síntoma que
> el enum de `tipo_dispositivo` del 2026-08-20, por un mecanismo distinto.
>
> Corolario práctico: el peligro no está en el `RENAME` en sí, sino en la **ventana** entre que
> renombra un lado y renombra el otro — y esa ventana, con 24 filiales que actualizan por cron cada
> 15 minutos, no se puede cerrar. Por eso: tabla nueva, copiar, `DROP` diferido. Ver §5.3.b.
>
> El precedente está escrito en el repo, en la cabecera de `V153.1` del central: *«financiero.
> terminal_pos esta replicada MAIN_TO_ALL. El ADD COLUMN de abajo exige que la columna ya exista en
> TODAS las filiales (migracion espejo V81.2 del filial), si no el apply worker se detiene con
> missing replicated column»*. Es exactamente el caso de **`terminal_pos.formato_terminal_pos_id`**
> — la FK nueva de la etapa 3, que es un `ADD COLUMN` sobre una tabla `MAIN_TO_ALL` y por lo tanto
> exige que **el filial migre primero**. (En una versión anterior de este plan esta línea decía «el
> caso de `tipo`», cuando el tipo todavía iba a ser columna de `terminal_pos`; ya no lo es, vive en
> el formato — ver §5.3.a.)
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

**Prueba: el arnés de comparación local, no un test en el repo.** Decidido el 2026-09-10.

Un test versionado necesitaría una foto de cupón, y **los repos de código son públicos** (verificado:
los siete lo son, porque Actions es gratis e ilimitado ahí y entre central, desktop, filial y
mobile-pwa suman ~1.800 corridas). Una foto de cupón lleva comercio, dirección, fecha, boleta,
autorización y monto de una transacción real, y quedaría en el historial de git para siempre.

La red que ese test iba a dar —que la etapa 4 no degrade la precisión al restringir el
reconocimiento— **ya existe**: el arnés que compara los 27 cupones contra RapidOCR en Python, en el
workspace local. Es el mismo que detectó que el resize de punto fijo no cambiaba nada. Se corre
como **puerta antes de adoptar el mapa espacial** (§5.4) y su resultado se anota en
[FASE-2-TICKET-FISICO.md](FASE-2-TICKET-FISICO.md).

> No es deuda: es la decisión de no versionar datos de transacciones en un repo público cuando la
> verificación ya está cubierta fuera.

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

### 5.3 · Etapa 3 — el sistema sabe qué aparato tiene enfrente

Rediseñada el 2026-09-10 con Gabriel, después de cerrar la etapa 2 con hardware real. Absorbe lo
que el plan original llamaba §3.6 (tipo de terminal) y la mitad de configuración de la vieja
etapa 3, así que **deja de ser un «2.5» y se numera por lo que es**.

#### Por qué

Hay **tres familias de POS**, no dos:

| Familia | De dónde salen los datos del cobro | Estado |
|---|---|---|
| **POS web** | El ticket trae QR → se escanea | Fase 1, en producción |
| **POS físico** | El ticket no trae QR → foto + OCR | Fase 2, etapa 2 cerrada |
| **Integración interna** | Nuestro sistema le pide la operación al proveedor | Futuro |

Las tres terminan en la **misma fila** de `venta_tarjeta`. De ahí salen las cuatro piezas de esta
etapa.

#### a) El formato es del modelo de aparato, no del proveedor

Hoy `formato_qr_pos` se resuelve **por proveedor**. Eso no distingue una maquinita Bancard de un
portal web Bancard, y tampoco dos firmwares distintos de la misma marca.

**El modelo que queda** (propuesto por Gabriel, y mejor que la cascada que yo proponía porque no
hay nada que resolver — es un solo salto):

```
proveedor_servicio  1 ──── N  formato_terminal_pos
                                 tipo         MAQUINA | WEB | API
                                 patron       (QR, si imprime)
                                 mapeo        campos + cuáles son obligatorios
                                 ejemplo
                                     ▲
                                     │ formato_terminal_pos_id
                                     │
                              N  terminal_pos
```

Se crea un proveedor, se le cargan **tantos formatos como modelos de aparato tenga**, y cada
terminal elige el suyo. Así conviven `Bancard firmware v5.2` y `Bancard firmware v5.5` sin
duplicar nada y sin reglas de herencia que se rompan calladas.

**El tipo vive en el formato, no en la terminal.** `MAQUINA` / `WEB` / `API` describe cómo se
comporta un modelo de aparato, no una unidad física. Una terminal no se clasifica: apunta a su
formato y hereda todo.

#### b) Unificar en una sola tabla, sin renombrar

`formato_terminal_pos` **reemplaza** a `formato_qr_pos`: una sola tabla dice todo sobre cómo se lee
el ticket de ese aparato. Decidido asumiendo el costo de hoy para ahorrar el de mantener dos
caminos.

> ⚠️ **No es un `RENAME`, y esto no es negociable.** `formato_qr_pos` está replicada
> `MAIN_TO_ALL`. El motivo exacto **no** es el que decía la primera versión de este plan («la
> publicación la referencia por nombre») — eso se probó y es falso: Postgres trackea la membresía
> por **OID** y un `RENAME` deja la tabla dentro de la publicación, con el nombre nuevo.
>
> Lo que corta es **el suscriptor**: el protocolo de replicación lógica identifica la relación en el
> filial por `schema.nombre`. Si central renombra y las 24 filiales todavía no, cada apply worker
> busca `financiero.formato_qr_pos`, no la encuentra y **se detiene**. Y esa ventana no se puede
> cerrar: las filiales actualizan por cron cada 15 minutos, cada una por su cuenta. Es el mismo
> corte del 2026-08-20 que ya costó una noche, por la otra mitad del mismo mecanismo. Detalle en
> §3.3.
>
> **La secuencia es la de dos versiones que el repo exige:**
>
> 1. **Filial primero**: crear `financiero.formato_terminal_pos` en el filial (es `MAIN_TO_ALL`)
> 2. Central: crear la tabla, `INSERT ... SELECT` desde `formato_qr_pos`, `ALTER PUBLICATION ... ADD TABLE`
> 3. Apuntar el código de los dos repos a la tabla nueva
> 4. `formato_qr_pos` queda **viva y sin uso**. El `DROP` va en una entrega posterior, cuando toda
>    la flota corra el código nuevo.
>
> Hoy `formato_qr_pos` tiene **una sola fila** (`ValidaPix FRCP1`), así que la copia es trivial.

> ⚠️ **La tabla nueva NO lleva el índice único por proveedor.** `V217.5` creó
> `uq_formato_qr_pos_proveedor` (`UNIQUE (proveedor_servicio_id) WHERE proveedor_servicio_id IS NOT
> NULL`), y `FormatoQrPosService.validar()` lo refuerza con el mensaje *«El proveedor ya tiene el
> formato X. Editalo en vez de crear otro»*.
>
> **Eso prohíbe exactamente lo que esta etapa existe para permitir**: Bancard v5.2 y Bancard v5.5
> conviviendo bajo el mismo proveedor. El ABM nuevo se va a escribir copiando el viejo —es la
> convención del repo, `CrudService` + `validar()`— y si nadie lo dice en voz alta, el segundo
> formato se rechaza y **nadie lo nota hasta que alguien intenta cargar el firmware nuevo**.
>
> En `formato_terminal_pos` la unicidad es **`(proveedor_servicio_id, nombre)`**, y hay que sacar
> también el chequeo del `validar()`. Va mencionado en la descripción del PR como cambio de
> comportamiento respecto del ABM viejo.

**Nombre.** `ConfiguracionVentaTarjeta` ya existe y es otra cosa (la config del flujo de venta con
tarjeta), así que `configuracion_terminal` habría quedado a un carácter de confundirse.
`formato_terminal_pos` dice qué es y se lee al lado de `terminal_pos`.

#### c) Campos obligatorios: una declaración, tres comportamientos

El `mapeo` marca qué campos son **obligatorios**. Eso decide tres cosas de una sola vez:

1. **Qué tiene que encontrar el OCR.** Si un obligatorio no se lee, la operación **falla** — no se
   acepta a medias y en silencio.
2. **Cuándo el resultado es utilizable.** Los no obligatorios que falten van a `datos_extra` o
   quedan vacíos, sin frenar nada.
3. **Qué pide la carga a mano.** El formulario se arma con los obligatorios del formato. Una sola
   fuente de verdad en vez de tres listas que se desincronizan.

**`patron` es obligatorio para `MAQUINA` y `WEB`.** El diagrama de arriba dice «(QR, si imprime)» y
eso se puede leer mal: un formato `MAQUINA` —justo el que usa cámara, el caso central de esta
fase— **también necesita patrón**, porque el OCR devuelve texto igual que el QR y la etapa 4 lo
matchea con el mismo patrón y el mismo mapeo. Sin patrón, la etapa 4 no tiene contra qué comparar y
hay que reabrir el diseño de la tabla en medio de la etapa. **Sólo `API` puede no tener patrón**:
ahí los campos llegan estructurados del proveedor.

#### d) La carga a mano es la salida universal

**Hoy no existe**: `codigoAutorizacion` no aparece en ninguna plantilla del desktop, así que el
único modo de completar una venta es escaneando el QR. Verificado el 2026-09-10. Eso deja dos
agujeros abiertos: el mensaje del filial dice *«cargá el cupón a mano»* y no hay a mano, y un POS
físico sin QR con el OCR fallado deja la venta `PENDIENTE` **y la caja sin poder cerrar**.

**Y es la pieza que hace seguro cerrar el camino equivocado.** El tipo del formato **cierra** el
camino que no corresponde —una terminal `WEB` sólo lee QR— y eso sólo es aceptable porque la carga
a mano está disponible **para cualquier tipo, siempre**. Si esa condición se rompe, hay que
reabrir los caminos.

- **Sin rol aparte.** Cualquier cajero puede. Queda `origen = MANUAL` y el usuario registrado: el
  chequeo de duplicado corre igual sobre lo tipeado y el rastro queda para auditar.
- **La foto es opcional.** Se puede adjuntar usando la misma captura de la etapa 2, salteando el
  OCR y archivando la imagen. El cupón sigue siendo la evidencia aunque el motor no haya podido
  leerlo.

> ⚠️ **El chequeo de cupón duplicado no cubre la carga a mano, y hay que arreglarlo en ESTA etapa.**
>
> `VentaTarjetaService.motivoCuponNoUsable()` (filial) hoy mira dos cosas: `qrCrudo` —que sólo
> existe si el cupón entró por el lector— e `identificadorTransaccion`, que **sólo llenan los
> formatos con un campo distinto de `codigoAutorizacion`** (el `EndToEndId` de Pix sí; Dinelco,
> Infonet, Stone, BXX y PlugPay no).
>
> Con carga a mano el cajero no tiene `qrCrudo` y, para la mayoría de los proveedores, tampoco un
> identificador aparte: **puede retipear el cupón de la venta anterior entero y nada lo frena**. Es
> el caso 5 de `FASE-2-TICKET-FISICO.md` §2.10, y ahí quedó anotado que se resolvía «cuando la
> validación corra también sobre el OCR» — pero ni esta etapa ni la 4 lo tocaban.
>
> **Va acá, en la misma entrega que abre la carga a mano**: chequeo por
> `codigoAutorizacion` + `terminal_pos_id` (+ ventana de tiempo), que es el único identificador que
> `MANUAL` garantiza para todos los proveedores. Entregar el camino sin el freno es peor que no
> entregarlo.

> ⚠️ **La última etapa no puede apagar el último camino.** El backlog §3.4 —«campos obligatorios por POS
> **y si la carga manual está permitida por POS**»— agrega un interruptor que anula la garantía en
> la que se apoya toda esta etapa. El día que alguien lo apague en una terminal `MAQUINA` cuyo OCR
> falle (el `Cargo: 002511` → `802511` de §6, el peor error del módulo), el cajero queda **sin
> ningún camino**: sin QR porque lo cierra el tipo, sin manual porque se apagó. Venta `PENDIENTE` y
> caja sin cerrar — el agujero que esta etapa dice haber tapado, reabierto por el mismo plan.
>
> **Regla dura, decidida el 2026-09-10:** el ABM de esa etapa **sólo deja apagar la carga manual si
> el otro camino está abierto para ese tipo**. Si es el último, el interruptor se rechaza con el
> motivo. Va al checklist de §7.

#### e) De dónde vinieron los datos

`venta_tarjeta` tiene los campos y **ninguna columna que diga cómo se obtuvieron**. No todos los
orígenes merecen la misma confianza: un código leído por OCR puede tener un carácter mal, uno que
viene de la API del proveedor no puede.

`venta_tarjeta.origen` = `QR` | `OCR` | `MANUAL` | `API`. **Una columna por fila, con el origen
dominante** — suficiente para responder «¿esto necesita revisión humana?» sin meterse dentro del
jsonb.

**Sin backfill.** La columna nace nullable y las filas viejas quedan en `NULL` = histórico
desconocido. Lo que haya que completar se hace aparte, por SQL, con el guard de dry-run que exige
el repo. Convertir una suposición en dato es peor que dejar el hueco visible.

#### f) Una terminal sin formato no vende — y por eso el SQL va ANTES que el desktop

**Hoy no hay resolución, hay cascada.** `qr-pos-parser.ts` prueba **todos** los formatos activos en
orden hasta que uno matchea, sin filtrar por terminal. Por eso el comodín `ValidaPix FRCP1` funciona
en cualquier lado, y por eso hoy ninguna terminal necesita estar configurada.

El modelo de a) es **un salto directo, sin cascada de respaldo**: la terminal apunta a su formato.
Eso deja una pregunta que el diseño no puede esquivar: qué pasa con una terminal cuyo
`formato_terminal_pos_id` es `NULL` — que el día del corte son **todas**, en las 24 sucursales,
porque no hay backfill.

**Decidido el 2026-09-10: bloquea la venta.** Sin formato no se registra la venta con tarjeta, ni
por QR ni por foto ni a mano. Es la opción estricta y hay que asumir lo que implica:

- El mensaje tiene que decir **qué falta y quién lo arregla**: *«La terminal X no tiene formato
  configurado. Un administrador tiene que asignárselo en Financiero → Terminales POS»*. Un bloqueo
  mudo en una caja con gente esperando es peor que el problema que evita.
- **No aplica a ventas ya `PENDIENTE`.** Una venta creada antes del corte se completa por el camino
  que ya tenía; si no, el bloqueo alcanza plata que ya se cobró y deja la caja sin cerrar por algo
  que el cajero no puede resolver.

**Y ordena el despliegue.** El desktop es el que bloquea, y el desktop se actualiza **por
consentimiento del cajero**: no hay forma de sincronizarlo con nada. Entonces la asignación por SQL
no es una tarea posterior, es un **prerrequisito**:

```
1. filial migra   (formato_terminal_pos + FK, MAIN_TO_ALL)
2. central migra  (tabla + copia desde formato_qr_pos + publicación)
3. SQL: asignar formato a TODA terminal_pos existente     ← acá, antes de tocar el desktop
4. verificar 0 filas con formato_terminal_pos_id IS NULL
5. recién entonces sale la versión del desktop que bloquea
```

El SQL se completa a mano —no hay backfill automático, decidido el 2026-09-10— con el guard de
dry-run + `RAISE EXCEPTION` que exige el repo, y mirando la PK, que en estas tablas suele ser
`(id, sucursal_id)`.

**Y `activo = false` cambia de significado.** Hoy `activo` sólo filtra `findActivos()`, la lista que
se prueba en cascada: desactivar un formato mal cargado es inocuo. Con la FK directa, desactivar un
formato que tiene terminales asignadas las dejaría a todas sin vender. **Regla:** `activo = false`
significa **«no elegible para asignar a terminales nuevas»**, no «deja de funcionar»; las terminales
que ya lo tienen siguen operando. Y el ABM **impide desactivar** un formato con terminales
asignadas, diciendo cuántas son.

#### g) La configuración general del módulo hoy es una sola bandera

`financiero.configuracion_venta_tarjeta` (`V146.1` central / `V78.1` filial) tiene **una fila y un
campo útil**: `habilitado`, que se lee en `pago-touch.component.ts:227` y decide si el flujo de
tarjeta aparece en el PDV. El resto —`usuario_id`, `creado_en`, `modificado_en`— es el rastro del
último cambio. Es el mismo patrón de bandera única de `ConfiguracionFacturaConVenta` y
`ConfiguracionTransferencia`.

**Todo lo demás que decide comportamiento está clavado en el código:**

| Constante | Dónde | Hoy |
|---|---|---|
| Vencimiento del QR de captura | `CapturaCuponService:45` | 10 min |
| Countdown del diálogo de registro | `ventas-tarjeta-caja-dialog:184`, `list-venta-tarjeta:254` | 120 s — y `add-venta-credito-dialog:275` usa 60 |
| Sondeo del desktop | `captura-cupon.service.ts:18` | 3000 ms |
| Tamaño máximo de la foto | `CapturaCuponController:47` | 8 MB |
| Lado máximo / nitidez mínima / calidad JPEG | `captura.html:49,58` | 1000 px, 50 |
| Ruta de imágenes, base-url del QR | `@Value`, `CapturaCuponService:75-76` | properties **por host** |
| Retención y purga de imágenes | — | **no existe** |

**Lo que se agrega a la tabla en esta etapa** (decidido el 2026-09-10):

| Campo | Qué decide | Default |
|---|---|---|
| `registro_obligatorio` | `LIBRE` / `AVISA_AL_CERRAR` / `BLOQUEA_EL_CIERRE` | `LIBRE` (lo de hoy) |
| `tolerancia_diferencia_monto_pct` | Por debajo de ese %, la diferencia no pide confirmación | `0` = confirmar siempre |
| `minutos_validez_captura` | Vida del token del QR | `10` |
| `segundos_dialogo_registro` | Countdown del diálogo | `120` |
| `horas_ventana_duplicado` | Cuánto atrás mira el chequeo de §5.3.d | `24` |
| `dias_retencion_imagenes` | Purga de fotos de cupón | `NULL` = no purgar |
| `mb_libres_minimos` | Umbral de espacio libre que alerta | `NULL` = sin alerta |

**Por qué `registro_obligatorio` es el que importa.** Hoy «Registrar más tarde» deja la venta
`PENDIENTE` y **nada la persigue**: verificado, el cierre de caja no mira ventas con tarjeta
pendientes. La razón de ser del módulo depende de que el cajero se acuerde.

**Por qué la tolerancia es un porcentaje y no un monto.** `monto` y `monto_escaneado` se guardan
**sin unidad** — el comentario de `VentaTarjetaService:118-126` lo dice: un cupón de 8.000 R$ contra
un cobro de 8.000 Gs da diferencia cero, y son ~5900x. Un umbral absoluto heredaría el mismo
problema; un porcentaje es agnóstico de moneda.

**Las dos columnas de retención entran ahora, el job que las lee sigue en la última etapa.** No es
configuración muerta por descuido: es más barato agregar las columnas en la misma migración que ya
toca esta tabla replicada, que coordinar un segundo `ALTER TABLE` filial-primero sobre 24 filiales
más adelante. La lógica de purga no se adelanta.

**Queda global, sin `sucursal_id`.** Decidido el 2026-09-10 sabiendo el costo: si más adelante una
farmacia y una bodega necesitan retenciones distintas, abrirlo por sucursal va a exigir backfill
sobre valores ya cargados. Se aceptó a cambio de no arrastrar una dimensión que hoy nadie pide.

> ⚠️ **Los tres campos nuevos con valores cerrados NO son enums de PostgreSQL.** Corregido el
> 2026-09-10 al implementar el filial: `financiero.venta_tarjeta.estado` **ya es `VARCHAR(20)`
> mapeado a `String` en Java**, y el módulo entero sigue esa convención — no hay un solo enum de PG
> en venta con tarjeta. Un enum habría exigido un `ALTER TYPE ... ADD VALUE` coordinado en las 24
> filiales cada vez que aparezca un tipo nuevo, que es exactamente la falla que §5.6 marca como la
> más silenciosa del repo. Con `VARCHAR` esa clase de problema no existe.
>
> **Y de ahí sale una regla que no estaba escrita en ningún lado: `CHECK` sólo donde el repo es el
> que escribe.**
>
> **Y las reglas se INVIERTEN entre los dos repos**, porque la dirección de cada tabla es distinta:
>
> | Campo | Dirección | El filial es | ¿CHECK/NOT NULL en filial? | Central es | ¿En central? |
> |---|---|---|---|---|---|
> | `venta_tarjeta.origen` | `BRANCH_TO_MAIN` | **publisher** | **Sí** | subscriber | **No** |
> | `formato_terminal_pos.tipo` | `MAIN_TO_ALL` | subscriber | No | **publisher** | **Sí** |
> | `configuracion_venta_tarjeta.registro_obligatorio` | `MAIN_TO_ALL` | subscriber | No | **publisher** | **Sí** |
>
> Leerlo al revés es el error fácil: copiar el `CREATE TABLE` del filial a central deja la tabla sin
> las restricciones que **sí** corresponden allá —y donde el ABM las necesita, porque central es
> quien escribe— y copiar el de central al filial mete restricciones que cortan la replicación.
>
> En un subscriber, un `CHECK` que el publisher no comparte convierte un valor legítimo en un corte
> de replicación en cuanto el otro lado agrega un valor antes de que ese repo actualice — y el orden
> en que actualizan las 24 filiales no lo controla nadie. Es el mismo criterio por el que `V91.5` ya
> usaba un índice y no un `UNIQUE`, escrito ahí en 2026-08 y nunca generalizado.

**Lo que NO se hace configurable, a propósito:**

- **`NITIDEZ_MIN = 50` y `LADO_MAX = 1000`** están calibrados con medición sobre 27 cupones que el
  OCR leyó bien. Un knob acá es una invitación a bajarlo «para que acepte más fotos» y llenar la
  base de imágenes ilegibles. Si cambian, cambian con una medición nueva.
- **`MAX_BYTES`, `BYTES_TOKEN`** son límites de seguridad de un endpoint sin autenticación.
- **Ruta de imágenes, base-url, `intra_op_num_threads` del OCR** dependen del hardware de cada
  filial —un Pentium Gold y un i3 no quieren el mismo número de hilos—. Son `@Value` por host y ahí
  están bien: meterlos en una tabla replicada los empeoraría.
- **`MS_SONDEO`** es detalle de implementación, no política.

**Hueco que queda abierto:** la tabla guarda `usuario_id` del **último** cambio, no un historial. Si
alguien apaga `habilitado`, el PDV entero pierde la venta con tarjeta y no queda quién ni cuándo más
allá de esa última escritura. Es el CN10 del módulo financiero, ya anotado como pendiente ahí.

#### g bis) Lo que la mitad de central NO tiene que repetir

Escrito el 2026-09-10 **después** de que la auditoría del filial encontrara cinco cosas mal en el
primer intento. Todo esto ya costó una vez; leerlo antes de escribir la migración de central sale
gratis.

**1. Central es publisher de dos de las tres tablas, así que las restricciones van al revés.** Ver
la tabla de §3.3. Concretamente: `formato_terminal_pos` en central **sí** lleva `NOT NULL` en
`nombre` y `mapeo`, `CHECK` en `tipo`, y las FK a `proveedor_servicio` y `usuario`. El
`CREATE TABLE` del espejo del filial **no se copia**: allá está deliberadamente permisivo.

**2. La unicidad es `(proveedor_servicio_id, nombre)`, y hay que sacar el chequeo del `validar()`.**
`V217.5` creó `uq_formato_qr_pos_proveedor` —único por proveedor— y `FormatoQrPosService.validar()`
lo refuerza con *«El proveedor ya tiene el formato X»*. Eso prohíbe exactamente lo que esta etapa
existe para permitir. El ABM nuevo se va a escribir copiando el viejo: **decirlo en la descripción
del PR como cambio de comportamiento.**

**3. La entrega de central es UN paso, no dos** — al revés que la del filial:

- `venta_tarjeta.origen`: central es **subscriber**. Un subscriber con una columna que el publisher
  todavía no manda no rompe nada, así que se puede agregar cuando sea.
- `formato_terminal_pos`: central es **publisher**. Acá está el peligro — en cuanto la tabla entra a
  la publicación, **toda filial que no la tenga se corta**. Por eso la entrega A del filial tiene
  que estar desplegada en **toda la flota del canal**, no sólo mergeada, antes de que esto corra.

> ⚠️ **Y la migración NO agrega la tabla a la publicación: eso lo hace un job, hasta una hora
> después.** Verificado el 2026-09-10 por auditoría: después de aplicar `V221.5`,
> `select pubname from pg_publication_tables where tablename='formato_terminal_pos'` devuelve
> **cero filas**. La migración sólo inserta en `configuraciones.replication_table`; quien lee esa
> tabla y ejecuta el `ALTER PUBLICATION` es `ReplicationPublicationSyncScheduler`, que corre **2
> minutos después del arranque y luego cada hora**, y está gateado por `replication.sync.enabled`
> — el mismo flag que este plan marca como **NO VERIFICADO** en mauro desde §3.3.
>
> **Consecuencia para el despliegue:** la ventana de peligro **no empieza cuando arranca el JAR de
> central**, empieza cuando corre ese job. Así que el prerrequisito no se cumple mirando el deploy:
> hay que **confirmar que la tabla aparezca en `central_pub` recién después** de que las 24 filiales
> tengan la tabla. Y el paso que refresca cada suscripción remota atrapa el error por filial con un
> `log.warn` sin reintentar: una filial caída en ese momento queda atrás hasta el ciclo siguiente.

**4. `ADD CONSTRAINT ... CHECK` va con `NOT VALID` + `VALIDATE CONSTRAINT`.** A secas toma
`AccessExclusiveLock` y valida contra todas las filas bajo ese lock. En central `venta_tarjeta`
acumula lo de **las 24 sucursales**, así que es peor que en el filial, no mejor.

**5. Si central implementa el chequeo de cupón duplicado, necesita su propio índice.** Los índices
**no** se replican. Sin él es un `Seq Scan` sobre la tabla consolidada de toda la red. La forma que
funciona está en `V96.7` del filial: parcial por `estado='COMPLETADO'`, de expresión sobre
`upper(btrim(...))`. Verificado que PostgreSQL lo usa con las tres escrituras posibles de `trim`,
porque compara el árbol parseado.

**6. Arrancar la app NO valida el mapeo JPA.** `spring.jpa.hibernate.ddl-auto=none` en los dos
backends: un nombre de columna mal escrito en una `@Entity` **no falla al arrancar**, falla la
primera vez que alguien consulta. El arranque limpio sólo prueba que el esquema GraphQL carga. **Hay
que correr una consulta real** contra cada campo nuevo — y contra un `@ManyToOne` nuevo, además, la
consulta que lo navega.

**7. Los tests de servicio mockean el repositorio**, así que ninguna `@Query` se verifica sola. La
traducción JPQL→SQL sólo se ejercita corriendo la consulta contra una base.

**8. Renombrar una migración deja la copia vieja en `target/classes/db/migration`** y el arranque
muere con `Found more than one migration with version X`, sin ninguna pista de la causa. El paso
`cleanup-duplicate-migrations` del pom no cubre el rename. Sincronizar `target` con `src`, o
`mvn clean`.

#### El trabajo

| Repo | Trabajo | Migración |
|---|---|---|
| **filial — entrega A** | `formato_terminal_pos` (espejo, **va primero**) · `terminal_pos.formato_terminal_pos_id` · espejo de los campos de `configuracion_venta_tarjeta` · **chequeo de duplicado por `codigoAutorizacion` + terminal** (ventana de `horas_ventana_duplicado`) · índice del chequeo · `minutos_validez_captura` en vez de la constante | `V95.5`, `V96.5`, `V96.7` |
| **filial — entrega B** | `venta_tarjeta.origen` · **parámetro `origen` en `completar()` y setearlo** | `V97.5`, **rama aparte** |
| **central** | `venta_tarjeta.origen` · `formato_terminal_pos` + copia desde `formato_qr_pos` + alta en `replication_table` (**sin el índice único por proveedor**) · FK en `terminal_pos` · ABM (impide desactivar un formato con terminales) · **7 campos nuevos en `configuracion_venta_tarjeta`** (§5.3.g) | `V221.5`, `V222.5`, `V223.5` |
| **desktop** | ABM de formatos (tipo, patrón, mapeo con obligatorios, ejemplo) · elegir formato en la terminal · el diálogo respeta el tipo · **carga a mano** con foto opcional · **bloqueo con motivo si la terminal no tiene formato** · el diálogo de configuración deja de ser un solo toggle · leer `segundos_dialogo_registro` en vez de los `120` clavados | — |

> ⚠️ **`origen` se setea en el filial, no en central.** La primera versión de esta tabla lo ponía en
> la fila de central, y está mal: el **único** método que pasa una `venta_tarjeta` a `COMPLETADO` es
> `filial/VentaTarjetaService.completar()`. `central/VentaTarjetaGraphQL.updateVentaTarjeta` es un
> update genérico que se usa para otras cosas, no el camino de completar un cupón.
>
> Ejecutando el plan como estaba escrito, central terminaba su parte —columna, ABM, FK— sin haber
> tocado el único lugar donde `origen` se puede fijar con un dato verdadero, y la columna nacía
> `NULL` para el ~100% del tráfico: todo lo que se completa desde las 24 filiales. **La columna la
> crea central; el valor lo escribe el filial.**

**`API` entra al enum pero el ABM todavía no lo ofrece.** Extender un enum de PostgreSQL después
cuesta otra migración en tres lugares; agregarlo hoy cuesta una palabra. Que no se pueda elegir
evita dejar terminales en un estado que ningún código sabe atender.

#### El orden de despliegue de esta etapa son DOS pasos, no uno

> ⚠️ **Y «dos pasos» significa DOS RAMAS, no dos commits.** Descubierto por auditoría el
> 2026-09-10, después de construirlo mal la primera vez: las tres migraciones del filial en un
> mismo commit viajan en el **mismo JAR**, y Flyway aplica todas las pendientes de una. No hay
> forma de desplegar «las MAIN_TO_ALL ahora, la BRANCH_TO_MAIN después» si están juntas.
>
> Y el costo de equivocarse no es teórico. La fila de `venta_tarjeta` en `pg_publication_rel`
> **no tiene column list** (`prattrs IS NULL`, verificado), así que **PostgreSQL publica cualquier
> columna nueva automáticamente** — no hace falta ningún `ALTER PUBLICATION`. Apenas una filial
> aplique la migración de `origen` y procese **una sola venta con tarjeta**, el stream hacia central
> incluye la columna; si central no la tiene, su apply worker se detiene con *missing replicated
> column*. Y `develop`→alpha es automático cada 15 minutos, sin aprobación.
>
> **Estructura que quedó** (`filial`):
>
> ```
> develop
>  └── feature/ocr-cupon-fase2          ← entrega A: V95.5, V96.5, V96.7
>       └── feature/venta-tarjeta-origen ← entrega B: V97.5
> ```
>
> B es **hija** de A a propósito: mergear A nunca puede arrastrar a B.
>
> **Secuencia obligatoria para B:** central migra → central **despliega** y se confirma la versión →
> recién entonces se mergea B.

Es la única etapa con migraciones en las dos direcciones a la vez:

| Tabla | Dirección | Quién primero |
|---|---|---|
| `venta_tarjeta.origen` | `BRANCH_TO_MAIN` | **central** |
| `formato_terminal_pos`, `terminal_pos.formato_terminal_pos_id` | `MAIN_TO_ALL` | **filial** |

El precedente está en la cabecera de `V153.1` del central: *«financiero.terminal_pos esta
replicada MAIN_TO_ALL. El ADD COLUMN de abajo exige que la columna ya exista en TODAS las filiales
(migracion espejo V81.2 del filial), si no el apply worker se detiene con missing replicated
column»*.

### 5.4 · Etapa 4 — el texto se convierte en campos, y el mapa se dibuja

**Absorbe lo que antes era la etapa 5** (mapa espacial). Decidido con Gabriel el 2026-09-11, por dos
razones.

**La primera es de producto.** Separadas, la etapa 4 entregaba el camino difícil —configurar un
proveedor nuevo escribiendo un regex a mano— y la 5 el fácil. No tiene sentido: **el editor visual
ES la forma de configurar un formato**, y el regex queda como camino alternativo para los que ya lo
tienen (todos los `WEB`, que leen una cadena de QR y no tienen imagen sobre la cual dibujar).

**La segunda es que la condición que las separaba ya está resuelta.** §5.5 decía que el mapa
espacial «se adopta si baja la latencia sin perder campos; si empata o pierde, el editor se archiva».
Esa medición **ya está hecha** y está en `FASE-2-TICKET-FISICO.md` §2.9: reconocer 6 cajas en vez de
26 baja `rec` de 3.841 a ~900 ms, y deja a la peor máquina de la flota en ~2,3 s —mejor que la
típica de hoy. El gate era mío y contradecía el análisis previo, que ya trata al mapa como **la
palanca de rendimiento**, no como una comodidad de asignación.

| Repo | Trabajo | Migración |
|---|---|---|
| **filial** | Espejo de las regiones (**va primero**) · aplicar el mapeo al texto del OCR: campos canónicos + el resto a `datos_extra` · **restringir el reconocimiento a las regiones** cuando el formato tiene mapa | `V98.5` |
| **central** | Regiones por campo y por formato · el ABM las valida | `V224.5` |
| **desktop** | **Editor drag-and-drop sobre la imagen del cupón** · confirmación con **semáforo por campo** según confianza (§2.6): los buenos se aplican, los dudosos se preguntan |

#### El mapa y el patrón conviven; el mapa manda si existe

Decidido el 2026-09-11. Un formato puede tener regiones, patrón, o los dos:

1. Si tiene **mapa**, se usa el mapa.
2. Si no, se cae a la **búsqueda por etiqueta** sobre el texto ya reconocido — **sin volver a
   procesar la imagen**, que es la diferencia entre un fallback y una segunda inferencia.
3. Los formatos `WEB` son patrón puro y no se tocan: no hay imagen que dibujar, la cadena entra por
   el lector.

Esto es lo que ya decía `FASE-2-TICKET-FISICO.md` §2.2; queda escrito acá porque es la regla que
decide qué código corre.

#### Lo que el editor NO puede ser

Tres restricciones que vienen de §2.2 del doc de dominio y que se pierden fácil al implementar:

**Las regiones se anclan a la etiqueta que cae adentro, no a coordenadas absolutas.** El editor se
siente igual de simple —se dibuja un rectángulo— pero el mapa sobrevive al día que el proveedor
agrega una línea. Con coordenadas absolutas, ese día **se rompen todos los mapas de ese modelo a la
vez** y nadie entiende por qué.

**El mapa es un intérprete, no una tijera.** Una sola pasada de OCR sobre la imagen produce cajas con
texto, coordenadas y confianza; el mapa **asigna** cada caja a un campo por posición relativa. No se
recorta campo por campo: eso serían N inferencias en vez de una, y el tiempo escala con la cantidad
de líneas, no con el tamaño de la imagen.

**Las regiones cuelgan del formato, no de la terminal.** El doc de dominio dice «por POS» porque se
escribió antes de la etapa 3. Ahora que el formato es del **modelo de aparato**, dos terminales del
mismo modelo comparten el mapa en vez de dibujarlo dos veces — que era el problema real que «por
POS» venía a resolver.

**Un campo declarado numérico rechaza `0i64`.** Eso captura gratis el tipo de error que ni Java ni
Python evitan, sin perseguir paridad binaria entre motores.

#### El asistente que propone el formato

Idea de Gabriel, 2026-09-11. Al dar de alta un formato se juntan ~10-20 cupones de ese aparato y **un
modelo propone el patrón, el mapeo y las regiones**; después el humano edita. El regex sigue
existiendo y se puede escribir a mano: el asistente es un atajo, no un reemplazo.

La pieza ya existe: `service/ia/OpenAiVisionService` en la rama `feat/factura-import-ia` de central
—gpt-4o, JSON mode, `temperature 0`, varias imágenes en un request— con la API key, el modelo y un
prompt extra saliendo de `ConfiguracionSistema`.

**Al modelo se le manda la salida del OCR, no las imágenes.** Decidido el 2026-09-11. El OCR ya
devuelve cajas con **texto, coordenadas y confianza**, que es exactamente lo que hace falta para las
dos cosas: el patrón y el mapeo salen del texto, las regiones de las coordenadas. Es más barato, más
rápido, y **las fotos de cupones reales no salen del local**. La contra aceptada: un modelo de visión
entendería mejor un layout raro si nuestro OCR leyó mal una línea.

**La sugerencia se puntúa contra la muestra ANTES de mostrarla.** Es la mitad que vale más, y no
necesita al modelo:

```
Patrón propuesto → compila ✓ · anclado ✓ · matchea 18/20
                   falla en el cupón 7 y en el 13  ← con la línea que no matcheó
Mapeo propuesto  → los 6 grupos existen en el patrón ✓
Regiones         → cada una captura una caja en 20/20 ✓
```

`FormatoTerminalPosService.validar()` ya compila el patrón, verifica el anclaje, que matchee el
ejemplo y que los grupos existan. Esto es **extenderlo de un ejemplo a N**, no construirlo.

**La muestra queda como corpus de regresión.** Al editar el patrón o el mapa más adelante —o el día
que el proveedor cambia el ticket— se vuelve a correr contra los N y dice qué se rompió. Es lo que
hace que un formato se pueda tocar sin miedo, y probablemente valga más que la generación inicial.

**Sólo en el ABM, nunca en el camino de una venta.** Si el modelo no responde, no se puede configurar
un formato nuevo: molesto y recuperable. En el camino del cobro sería un cajero que no puede cobrar.

#### Las imágenes se suben a central, y central gana su propio motor OCR

Decidido el 2026-09-11, y corrige un agujero del diseño anterior: **antes de registrar el formato no
hay ni puede haber capturas de ese ticket.** La etapa 3 bloquea la venta con tarjeta si la terminal
no tiene formato, así que el cajero no llega ni a la pantalla que fotografía. Un diseño que dependiera
de las capturas del filial sólo servía para formatos que ya operan — justo el caso que menos necesita
el asistente.

Entonces: **las N imágenes se cargan en el ABM de central, a mano, al dar de alta el formato.**

**Y el puntaje tiene que correr contra NUESTRO OCR, no contra la lectura del modelo.** Es la razón de
fondo por la que central necesita el motor. El patrón afinado sobre la transcripción de GPT puede
fallar sistemáticamente sobre la salida real de nuestro motor: el `Cargo: 002511` leído `802511` es
el error más grande del módulo y lo fallan los dos motores, mientras que un modelo de visión
probablemente lo lea bien. Un `matchea 18/20` medido sobre GPT **predice el comportamiento de un
motor que no es el que corre en la caja**: es un número falso, y peor que no tenerlo.

La contrapartida buena de esta decisión: **el diseño de formatos queda autocontenido en central.** Sin
puente por el desktop, sin depender de que haya un filial arriba, y el corpus se puede volver a
puntuar cuando sea.

> ### ⚠️ Lo que central hereda al sumar el motor
>
> **1. Las dos versiones tienen que ser la misma, y eso es una regla, no una coincidencia.** El
> filial usa `io.github.gabfrank:onnxruntime-slim:1.23.2` y `io.github.gabfrank:ppocr-models:1.0`.
> Si central corriera otra versión, el puntaje volvería a mentir — de forma más sutil, porque los
> dos motores serían «el nuestro». **Las dos versiones se mueven juntas o no se mueven.**
>
> **2. El `catch (Throwable)`, y acá importa más que en el filial.** La carga del motor tiene que ser
> fail-open y no puede impedir el arranque. En el filial esa lección costó que no arrancara **una**
> sucursal; en central, si no arranca, **no hay HQ para nadie**. Ver `CuponOcrService` del filial:
> `UnsatisfiedLinkError` es un `Error`, no una `Exception`, y un `catch (Exception)` lo deja pasar.
>
> **3. `onnxruntime-slim` sólo trae linux-x64 y win-x64.** Producción es Linux, así que va. Pero el
> desarrollo local es esta Mac: hay que portar el perfil `ocr-mac` del `pom.xml` del filial —activado
> por `<os><family>mac</family></os>`, agrega `com.microsoft.onnxruntime:onnxruntime:1.23.2`— o el
> motor no carga acá y no se puede probar nada.
>
> **4. El OCR toma todos los núcleos por defecto.** En el filial compite con el PDV de una sucursal;
> en central competiría con las operaciones de las 24. Y el uso es raro pero a ráfagas: 20 cupones de
> golpe al registrar un formato. **Acá sí conviene limitar `intra_op_num_threads`**, que en el filial
> todavía es un riesgo abierto de §6.

> ### El corpus es sólo lo que se carga a mano, y eso tiene un límite conocido
>
> Decidido el 2026-09-11: las capturas reales del filial **no** se suman al corpus. Más simple y
> predecible, y evita guardar en central imágenes de cupones que llegan por otro camino.
>
> **La consecuencia, para que no sorprenda:** el corpus protege contra **nuestras** ediciones —tocar
> el patrón y ver qué se rompe— pero **no** contra que el proveedor cambie el ticket. El día que
> agreguen una línea, el corpus sigue diciendo 20/20 mientras la caja falla. La detección de ese caso
> queda del lado del semáforo por campo y de `origen`, no del corpus. Y el arreglo es barato cuando
> pase: volver a cargar muestras nuevas.

### 5.5 · Etapa 5 — terminar el módulo

| Ítem | Repos | Migración |
|---|---|---|
| **§3.7 · Input único** | desktop | — |
| **§3.6 · La terminal viajando dentro del propio QR** | central, desktop | — |
| **§3.4 · Configuración por POS** | central, filial, desktop | `V225.5` / `V99.5` |
| **§3.1 · Ticket con seña con QR** | filial (impresión), desktop | — |
| **§3.3 · Adjuntos al cierre de caja** | central, filial, desktop | `V226.5` / `V100.5` |
| **Purga de imágenes** (el job) | filial | — |

> ⚠️ **Los números de acá son tentativos** y ya se corrieron dos veces: una el 2026-09-10 por una
> colisión real, y otra el 2026-09-11 al absorber la vieja etapa 5 dentro de la 4. Dos migraciones
> con la misma versión no conviven en el mismo repo —Flyway falla al arrancar— así que se
> re-confirman con `git fetch` el día que se abre el PR (§3.3), no antes.

> **La configuración *general* del módulo se adelantó a la etapa 3** (§5.3.g), con las columnas de
> retención incluidas. Lo que queda acá es la configuración **por POS** —campos obligatorios y si la
> carga manual está permitida en esa terminal— y el **job de purga**, que lee `dias_retencion_imagenes`
> y `mb_libres_minimos` de columnas que ya van a existir.

> ⚠️ **§3.4 lleva la restricción de §5.3.d**: el interruptor de «carga manual permitida por POS»
> **no puede apagar el último camino disponible**. Si el tipo del formato ya cierra QR o cámara,
> apagar manual deja al cajero sin salida. El ABM lo rechaza con el motivo.

**§3.6 quedó partido.** El tipo de terminal se absorbió en la etapa 3, porque es el router del
flujo. Lo que queda acá es la otra mitad: que la terminal viaje dentro del propio QR (`FRCP1` ya
mapea campos por nombre, así que es configuración y no release) con fallback a lista filtrada por
formato. Eso no necesita migración.

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

Aplica a **todo** el ABM que agrega esta entrega: formato de terminal POS, regiones por POS,
configuración por POS. Si no se inyecta a mano, **quedan abiertos**.

### Un enum nuevo va a TRES lugares en el mismo commit

**`formato_terminal_pos.tipo`** (`MAQUINA` / `WEB` / `API`) es un enum. Va en **Java + `.graphqls` +
migración**, todo en el mismo commit. (Una versión anterior de este plan lo llamaba
`terminal_pos.tipo`: el tipo dejó de ser columna de la terminal y pasó al formato — §5.3.a. Si se
lee esa versión y se implementa literal, se construye el diseño viejo.) Y en PostgreSQL se extiende con `ALTER TYPE ... ADD VALUE` idempotente, **sin
usar el valor nuevo en la misma transacción**.

**La etapa 3 sola trae tres**: `formato_terminal_pos.tipo`, `venta_tarjeta.origen` y
`configuracion_venta_tarjeta.registro_obligatorio`. Los tres, misma regla.

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
| **desktop** (cada caja) | `electron-updater` chequea cada 5 min y **pide consentimiento**. Se puede posponer **indefinidamente** |

**Y no son dos mitades, son tres.** Esta sección hablaba sólo de central↔filial, pero desde la
etapa 3 el trabajo que **cierra el camino equivocado y abre la carga a mano vive en el desktop** — y
el desktop es la pieza que nadie puede sincronizar con nada.

Entre esos dos momentos el filial corre código nuevo **contra un central que todavía no tiene las
tablas**. Las tablas de configuración de las etapas **3 a 6** son `MAIN_TO_ALL`, así que sí muerde — y la
etapa 3 además tiene una columna en la dirección contraria, así que su merge son **dos pasos**, no
uno.

**Procedimiento obligatorio al mergear:**

1. Mergear **primero el PR de central**
2. **Disparar el Deploy de central a `alpha` de inmediato**, sin esperar
3. Recién entonces mergear el PR de filial
4. Confirmar que mauro tomó la versión (`.current-version` y `/api/version`)
5. **Correr el SQL de asignación de formato** y verificar 0 terminales sin formato (§5.3.f)
6. **Recién entonces** liberar el desktop, y **confirmar que las cajas tomaron la versión antes de
   reconfigurar tipos en el ABM**

Los pasos 5 y 6 son de la etapa 3 en adelante. Sin el 5, el desktop nuevo bloquea ventas en toda la
flota. Sin el 6, un administrador marca una terminal como `MAQUINA` en el ABM —que ya está en
central— y el cajero con desktop viejo sigue viendo el lector de QR como si nada: no rompe nada,
pero **la funcionalidad que el administrador cree haber habilitado no existe en esa caja, y nadie
avisa**.

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
| **Disco lleno en un filial** | Las imágenes, `releases/` y la base PostgreSQL **comparten disco**. Un disco lleno no sólo rompe el guardado de fotos: **impide que Postgres escriba WAL y tumba todas las ventas de esa sucursal**. La purga necesita un umbral de espacio libre que alerte, no sólo retención por antigüedad. **Las dos columnas (`dias_retencion_imagenes`, `mb_libres_minimos`) se adelantaron a la etapa 3** (§5.3.g); el job que las lee sigue en la última etapa. Y hay que confirmar en una filial real en qué partición viven las tres cosas |
| **Un formato reasignado deja ventas viejas ilegibles** | `venta_tarjeta.datos_extra` guarda claves según el `mapeo` vigente al momento, y **ninguna columna dice qué formato las produjo**. Si el formato se corrige o la terminal se reapunta, las ventas archivadas quedan con claves que ya no corresponden a ningún mapeo vivo. Candidato: guardar `formato_terminal_pos_id` en cada `venta_tarjeta`, no sólo el `origen`. **Abierto, no bloquea la etapa 3** |
| **La pantalla de venta con tarjeta de `mobile` no valida nada** | **Bajo — camino heredado, fuera del circuito de esta fase.** Llama `updateVentaTarjeta` del **central** (`mobile/.../venta-tarjeta.service.ts:82`), un setter genérico sin validación de estado, ni de cupón duplicado, ni de moneda. **No es el camino de la fase 2**: la captura por cámara va por una página web que sirve el filial y que abre el navegador del teléfono, así que los dos caminos vivos —lector del PDV y foto— sí pasan por `completar()`. Y `mobile` está en mantenimiento, reemplazada por `mobile-pwa`. Queda anotado por si esa pantalla se reactiva |
| **Un `tipo` desconocido llegado por SQL** | `API` entra al enum pero el ABM no lo ofrece — la mitigación vale sólo si el único camino es la pantalla, y este repo tolera (y para arreglos puntuales recomienda) tocar la base a mano. **El desktop cae a carga manual ante un `tipo` que no conoce**, nunca a una pantalla en blanco |
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
- [ ] **`git fetch origin develop` recién hecho** y los números de `.5` re-confirmados libres — la
      tabla de §3.3 ya se puso vieja dos veces en dos días
- [ ] **Ningún camino queda cerrado sin alternativa**: si el tipo del formato cierra QR o cámara, la
      carga a mano tiene que estar abierta para esa terminal (§5.3.d)
- [ ] **Ninguna terminal sin formato** antes de liberar el desktop de la etapa 3 (§5.3.f)
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

### 8.3 · Segunda auditoría — sobre el plan ya rediseñado (2026-09-10)

Después de reescribir §5.3 con el modelo de `formato_terminal_pos`, se corrió una segunda auditoría
de dos ejes: **A, hechos** (cada afirmación verificable contra código y base) y **B, diseño**
(coherencia interna y modos de falla). **Tres hallazgos desmienten afirmaciones de este plan.**

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | **La tabla de migraciones ya estaba vieja otra vez** — `V221.1` / `V93.1`, pusheadas al día siguiente de «corregirla» | **§3.3** reescrita: el número de acá no es fuente de verdad. Ítem nuevo en §7 |
| A2 | **Falso el mecanismo del `RENAME`.** La publicación **no** referencia por nombre: trackea por OID. Probado con un `RENAME` + `ROLLBACK` | **§3.3 y §5.3.b** corregidas. Lo que corta es el **suscriptor**, que identifica por `schema.nombre`. La conclusión operativa no cambia |
| A3 | **Contradicción interna:** §5.3.a dice que el tipo vive en el formato, pero §5.6 y §3.3 seguían diciendo `terminal_pos.tipo` — y §5.6 se presenta como regla dura | Las dos corregidas, con la nota de por qué |
| A4 | `V224.5` y `V99.5` repetidos entre etapa 5 y 6; una fila decía «filial» citando un número del rango de central | **§5.6** renumerada |
| A5 | PR #282 mal citado (es de transferencias, y está mergeado) | Referencia sacada de §3.3 |
| B1 | **Alto.** `uq_formato_qr_pos_proveedor` + `validar()` **prohíben exactamente** lo que la etapa existe para permitir. El ABM nuevo se copia del viejo por convención | **§5.3.b**: unicidad `(proveedor, nombre)`, y va en la descripción del PR |
| B2 | **Alto.** Nadie asigna formato a las terminales existentes, y el modelo nuevo **no tiene cascada de respaldo** | **§5.3.f** nueva: bloquea la venta, y el SQL es prerrequisito del desktop |
| B3 | **Alto.** «Setear `origen` al completar» estaba en la fila de **central**, pero el único `completar()` es del **filial**. La columna nacía `NULL` para el ~100% del tráfico | Movido a la fila del filial, con la explicación |
| B4 | **Alto.** El interruptor de la etapa 6 («carga manual permitida por POS») **anula la garantía** en la que se apoya toda la etapa 3 | **§5.3.d** y **§5.6**: no se puede apagar el último camino. Ítem en §7 |
| B5 | **Alto.** El chequeo de cupón duplicado no cubre OCR ni MANUAL — con carga a mano se puede retipear el cupón anterior entero | Adelantado **a la etapa 3**, en la fila del filial |
| B6 | `activo = false` con terminales apuntando por FK: sin definir | **§5.3.f**: significa «no elegible para asignar», y el ABM impide desactivar |
| B7 | `patron` «si imprime» sugiere que `MAQUINA` puede no tenerlo — la etapa 4 lo necesita | **§5.3.c**: obligatorio para `MAQUINA` y `WEB` |
| B8 | §5.7 cubría central↔filial, pero el cierre de camino vive en **desktop** | **§5.7**: son tres mitades, con dos pasos nuevos |
| B9 | Nada registra qué formato produjo un `datos_extra` | **§6**, riesgo abierto |
| B10 | Un `tipo` desconocido llegado por SQL | **§6**: el desktop cae a carga manual, no a pantalla en blanco |

### 8.4 · Tercera auditoría — el código del filial (2026-09-10)

Dos ejes sobre el primer commit de la etapa 3: **replicación/despliegue** y **código Java**. La
sospecha con la que se lanzó —que `vt.terminalPos.id` generaba un inner join implícito y excluía las
filas sin terminal— **quedó refutada** con evidencia dura: un `SessionFactory` de Hibernate 5.4.17
bootstrapeado aparte muestra que la navegación `.id` se traduce a la columna FK, sin JOIN.

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | **Alto.** `venta_tarjeta.origen` no puede viajar en el mismo JAR que las MAIN_TO_ALL. La publicación no tiene column list, así que publica columnas nuevas sola | Migración renumerada a `V97.5` y movida a **rama propia**, hija de la de A |
| A2 | `V95.5` ponía `NOT NULL` en `nombre`/`mapeo`/`tipo` sobre una tabla **subscriber**, violando la regla que el propio commit predica | Sólo la PK lo lleva. La obligatoriedad es regla de negocio y vive en el ABM de central |
| A3 | `ADD CONSTRAINT` sin `NOT VALID` toma `AccessExclusiveLock` sobre la tabla de cobros en horario de atención | `NOT VALID` + `VALIDATE CONSTRAINT` |
| B1 | `mobile` no pasa por `completar()`. **El auditor lo reportó como alto y no lo es**: la captura de la fase 2 va por una página web servida por el filial, no por la app, así que los dos caminos vivos sí pasan por ahí. La pantalla de `mobile` es un camino heredado | Reformulado en el javadoc y bajado a riesgo bajo en §6 |
| B2 | El chequeo de duplicado hacía **Seq Scan** sobre toda `venta_tarjeta`, en cada `completar()` y en cada pre-chequeo | `V96.7`, índice parcial de expresión. Verificado con `EXPLAIN` que se usa con las tres escrituras posibles de `trim` |
| B3 | `origenEfectivo` no validaba contra el whitelist: el valor llegaba al `INSERT` y el `CHECK` explotaba como error opaco | Whitelist antes de `save()`, con mensaje legible |
| B4 | `catch (Exception)` en `minutosValidez()`, el patrón que ya costó un arranque caído | `catch (Throwable)`, como `CuponOcrService` |
| B5 | Los 27 tests mockean el repositorio: **ningún test toca la traducción JPQL→SQL** de ninguna `@Query` del repo | Reconocido, no resuelto: el repo no tiene tests de integración. Lo cubre la corrida manual |

### 8.5 · Cuarta auditoría — el código de central (2026-09-10)

Dos ejes sobre el commit de central: **migraciones/replicación** y **ABM/código**. Confirmaron que
el espejo y el original coinciden columna por columna sin ningún mismatch de tipo o nombre, que las
restricciones van en el sentido correcto en los tres casos, que las tres migraciones son
idempotentes, que los números están libres y que el rollback del JAR no rompe nada. Y descartaron
ReDoS midiendo: dentro del tope de 512 caracteres el crecimiento es cuadrático, no exponencial.

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | **La migración NO agrega la tabla a la publicación**: eso lo hace un job hasta una hora después, gateado por un flag no verificado. La ventana de peligro no empieza con el deploy | **§5.3.g bis** reescrita: el prerrequisito se confirma mirando `central_pub`, no el deploy |
| A2 | `saveTerminalPos` con un id de formato inexistente dejaba la terminal en `null` en silencio | Ahora avisa |
| A3 | El plan reservaba cuatro migraciones para central y la entrega son tres | Corregido |
| B1 | **Alto.** `saveTerminalPos` **borraba el formato asignado** cuando el input no lo traía — que es lo que hace el desktop de hoy. Cualquier edición trivial apagaba la venta con tarjeta en esa caja | Arreglado, y con la mutation `desasignarFormatoTerminalPos` para el caso legítimo |
| B2 | **Alto.** `configuracionVentaTarjeta()` no pedía rol | `requireVer()`, después de verificar que el PDV lee la copia del filial y no ésta |
| B3 | `tipo = API` aceptaba un mapeo que no es JSON | Se exige forma JSON en los dos caminos |
| B4 | El nombre se comparaba trimeado pero se guardaba crudo: dos filas visualmente idénticas | Se normaliza lo que se guarda |
| B5 | `saveFormatoTerminalPos` es full-overwrite mientras la config es PATCH — inconsistente dentro del mismo commit | Anotado, sin cambiar: es el patrón heredado del ABM viejo |

**Lo que este ciclo dejó como método, más que como arreglo:** el primer intento de arreglar B1
corrigió la capa equivocada —«no seteo el campo si no viene»— y el bug siguió igual, porque
`saveTerminalPos` arma la entidad **de cero** con ModelMapper y lo que no viene nace en `null`. El
auditor lo había dicho con todas las letras y yo lo leí como contexto en vez de como el mecanismo.
Se descubrió sólo porque se volvió a probar con una mutation real después de arreglar.

### 8.6 · Lo que sigue sin verificar

1. **`replication.sync.enabled` en mauro.** El `.env` no es legible sin sudo con contraseña. Se
   resuelve con `grep -i replication.sync /opt/frc-backend-central/alpha/.env`.
2. **En qué partición viven `releases/`, las imágenes y los datos de PostgreSQL** en una filial real.
3. **La aditividad real de las migraciones**: hoy no existen como archivos. La auditoría revisó la
   intención declarada, no código. Es lo que el dry-run de §5.8 viene a cubrir.
4. **Las mediciones de rendimiento del OCR** (Apple Vision 92% / PaddleOCR 85%, IoU 0,974, tiempos en
   Pentium Gold e i3) no son reproducibles desde una auditoría: el arnés de comparación no está en el
   repo. Se toman como reportadas, no como verificadas.
5. **`imagenUrl` en `frc-mobile`.** El plan afirma que no se usa. Se confirmó el patrón análogo en
   desktop (el resolver lo acepta, ningún template lo manda), pero el repo `mobile` no se revisó.
