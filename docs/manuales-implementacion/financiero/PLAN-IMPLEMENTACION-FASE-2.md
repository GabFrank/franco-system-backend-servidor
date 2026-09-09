# Plan de implementación — fase 2: captura del cupón por cámara

Acordado con Gabriel el **2026-09-09**, después de cerrar por medición los cuatro riesgos que podían
cambiar la arquitectura. El **qué** y el **por qué** viven en
[FASE-2-TICKET-FISICO.md](FASE-2-TICKET-FISICO.md); acá está el **cómo, en qué orden y con qué
proceso**.

> Este plan se ejecuta siguiendo **`frc-cicd/guia-desarrollo-cicd.md`** al pie (repo aparte, no
> enlazable desde acá). Donde este plan se aparta de la guía, lo dice explícitamente y con el motivo.

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

> ### ⚠️ Consecuencia de un solo PR al final
>
> **Hasta el merge final no hay nada en alpha.** Las pruebas paso a paso de cada etapa corren contra
> **builds locales** (`./mvnw spring-boot:run` en central y filial, `npm start` en desktop), no
> contra mauro.
>
> Es la contrapartida aceptada de la decisión. Lo escribo acá para que no aparezca a mitad de camino.

> La guía (§3, *Buenas prácticas de PR*) pide PRs de menos de 400 líneas y una responsabilidad por
> PR. **Esta entrega se aparta de eso a pedido explícito de Gabriel.** No está entre los ítems de
> §13 «lo que NUNCA debes hacer», así que es una decisión legítima — pero queda anotada.

### 3.2 · Commits

Formato `tipo(scope): descripcion en minusculas`, sin mayúscula inicial y sin punto final.
Scope sugerido: `venta-tarjeta`, `ocr`, `caja`.

Sólo `feat` y `fix` generan versión. Como el PR se mergea entero al final, **la versión la decide el
conjunto de commits de la rama**: habrá `feat`, así que sube MINOR alpha.

### 3.3 · Migraciones Flyway

**Sufijo `.5` siempre**, nunca `.0` ni el entero pelado. Verificado el 2026-09-09:

| Repo | Última existente | Reservado para esta entrega |
|---|---|---|
| central | `V219.5` | **`V220.5` en adelante** |
| filial | `V92.5` | **`V93.5` en adelante** |

El PR #282 abierto en central **no trae migraciones**, así que no hay colisión conocida.

> ### ⚠️ El orden de despliegue depende de la dirección de replicación
>
> No es «central siempre primero»:
>
> | Tabla | Dirección | Quién migra primero |
> |---|---|---|
> | `venta_tarjeta` (columna nueva) | `BRANCH_TO_MAIN` — filial publica, central suscribe | **central** |
> | Tablas de configuración nuevas | `MAIN_TO_ALL` — central publica, filial suscribe | **filial** |
>
> **En alpha esto no muerde**, porque corre con `replication.sync.enabled=false`. Pero es la regla
> que gobierna la promoción a farmacia y bodega, y ahí sí rompe. Queda escrito para ese día.
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

---

## 6 · Riesgos vivos

| Riesgo | Estado |
|---|---|
| **`Cargo: 002511` leído `802511`** | Abierto, y **es el error más grande del módulo** — lo fallan los dos motores. Más resolución sobre esa línea, o un segundo pase, son las hipótesis a probar |
| **Rendimiento con la filial bajo carga** | Lo medido fue con la máquina ociosa. ORT toma todos los núcleos por defecto: falta decidir si limitar `intra_op_num_threads` para no ahogar la aplicación |
| **Topología de red de una sucursal real** | La captura se probó en una LAN doméstica. Falta confirmar que la WiFi que usan los cajeros alcanza al filial |
| **El JAR del filial engorda** | +8 MB de ORT podado y +15,5 MB de modelos, en cada auto-update de 24 sucursales cada 15 min. Medir el impacto real antes de promover |

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
