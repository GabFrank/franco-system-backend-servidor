# Fase 2 — ticket físico, cámara y OCR

Levantado el **2026-09-08/09**, después de validar en alpha la fase 1 (lectura del QR con lector
keyboard-wedge desde el PDV y desde la lista).

**Qué resuelve:** el cupón que **no se puede leer por QR** — porque el POS no lo imprime, porque
salió borroso, o porque el cajero no lo escaneó en el momento.

---

## 1 · Lo que se midió, y con qué

16 fotos de cupones reales, sacadas con celular en condiciones distintas y **comprimidas por
WhatsApp** (720×1280, ~45 KB). Es un piso pesimista: la página de captura manda 1000 px con calidad
0.9 (§1.5), o sea fotos mejores que estas.

Cuatro formatos de proveedor distintos en las 16, dos de ellos en portugués:

| Proveedor | Etiquetas |
|---|---|
| Dinelco | `Aut:` `Boleta N°:` `Terminal:` `Gs.` |
| Infonet | `C.AUT:` `BOLETA:` `MONTO:` `G.` `F:` `H:` |
| Stone (BR) | `AUT PAG:` `R$` `STONEID:` |
| BXX (BR) | `COD TRANS.` `DATA:` `TOTAL` `R$` |

### Resultados

| Motor | Campos extraídos | Tiempo | Dónde puede correr |
|---|---|---|---|
| **Apple Vision** | **74/80 — 92%** | ~700 ms | iPhone nativo |
| **PaddleOCR / RapidOCR (ONNX)** | **68/80 — 85%** | ~2.260 ms | **cualquier lado** |
| Tesseract 5 | 42/80 — 52% | ~600 ms | cualquier lado |

Por campo, que es donde el promedio engaña:

| Campo | Vision | Paddle |
|---|---|---|
| **Autorización** | 14/16 | **16/16** |
| **Boleta** | 13/16 | 13/16 |
| Fecha | 16/16 | 15/16 |
| Hora | 16/16 | 11/16 |
| Monto | 15/16 | 13/16 |

**En el código de autorización —la clave de conciliación— PaddleOCR le ganó a Vision.** Empatan en
boleta. Pierde en hora y monto, que son los campos menos críticos: **el monto ya se conoce** (es el
del cobro; el OCR sólo confirma) y la hora no se usa para conciliar.

### Tesseract no sólo falla más: falla peor

Corrompió en silencio valores con formato válido: `Aut: 70887` en vez de `570887` (se comió un
dígito), `62/09/2026`, `27:43`, `92/09/26`, y `CG, 18,000` por un monto. Las comparaciones publicadas
lo confirman: Tesseract **confunde punto y coma decimal** en documentos internacionales. Con
guaraníes (`18.000`) y reales (`69,67`) conviviendo, ese es exactamente el error que no se puede
permitir. Un campo faltante se nota; uno corrupto con formato correcto se guarda y nadie lo mira.

### Escalar no. Recortar, menos de lo que parecía

| | Precisión | Peso | Tiempo |
|---|---|---|---|
| Foto cruda | Vision 92% · Paddle 85% | 706 KB | 2,26 s |
| **Recortada sin escalar** | Vision 91% · **Paddle 86%** | **502 KB (71%)** | 2,40 s |
| Recortada y escalada ×3 | Vision 92% · **Paddle 82%** | — | 3,47 s |

**Escalar una foto ya tomada no agrega información**: interpola píxeles que no existen y amplifica
los artefactos del JPEG. Por eso Paddle empeora. **No escalar del lado del servidor.**

Recortar sin escalar mantiene la precisión y baja el peso al 71%. Las imágenes resultantes van de
320×195 a 639×474 px, 12 a 41 KB — una caja con 50 ventas con tarjeta son ~1,5 MB, revisables en
una grilla sin generar miniaturas aparte.

> ⚠️ **Corregido el 2026-09-09.** Mirando la tabla de arriba con honestidad, el recorte movió la
> precisión **+1% en Paddle y −1% en Vision**: eso es ruido, no una mejora. La segunda medición
> (§1.5) lo confirmó con fotos de cámara nativa.
>
> **El recorte quedó fuera del diseño.** Lo que buscaba —bajar el peso— lo consigue mejor
> **escalar a 1000 px**: 60 KB contra los ~500 KB de una recortada sin escalar, sin pedirle al
> cajero un paso más ni una pantalla más. Lo que sigue vigente de esta tabla es la otra mitad del
> título: **escalar hacia arriba nunca**, porque interpola píxeles que no existen.

### Dato que no se buscaba

En estas fotos **el ticket ocupaba apenas un tercio del cuadro**. Y la detección automática del
documento **falló en las 16**: ticket blanco sobre mostrador claro, con sombras encima, es el caso
difícil clásico.

En su momento se leyó esto como el argumento a favor del overlay. **Ya no lo es** (ver §1.5): con el
ticket ocupando un tercio del cuadro, PP-OCR igual sacó todos los campos. Lo que este dato sigue
sosteniendo es lo contrario de lo que parecía: **no intentar recorte automático en el servidor**,
porque el caso es justamente el que la detección de contornos no resuelve.

### 1.5 · Segunda medición (2026-09-09): cámara nativa, dentro del flujo real

Las 16 primeras venían comprimidas por WhatsApp. Estas se sacaron **desde la página de captura**,
con la cámara nativa del teléfono: sensor completo (iPhone 4032×3024, Android 4096×2304), reducidas
en el propio teléfono antes de subir. Dos cupones Infonet, iPhone y Android.

**Recorte manual contra foto completa — la misma foto, el mismo cupón:**

| | Líneas | Confianza media | Tiempo |
|---|---|---|---|
| Completa 900×1600 | 16 | 0.935 | **2.648 ms** |
| Recorte 1600×1422 | 17 | 0.939 | 3.775 ms |

Todos los campos correctos en las dos: `BOLETA`, `C.AUT`, `MONTO`, `Caja`, `Lote`, `Cargo`, fecha y
hora. **El recorte no compró precisión**, y como pesa más (2,3 MP contra 1,4) tardó **43% más**.

El motivo es del motor, no de la foto: **la etapa de detección de PP-OCR redimensiona a un tamaño
fijo**. Darle al cupón 1600 px en vez de 777 no le agrega información que pueda usar.

**Entonces la pregunta se da vuelta: ¿cuánto se puede bajar?** (foto completa, escalada)

| Lado máximo | Peso | Tiempo | Campos |
|---|---|---|---|
| 1600 | 148 KB | 2.601 ms | todos |
| 1200 | 84 KB | 2.054 ms | todos |
| **1000** | **60 KB** | **1.862 ms** | **todos** |
| 800 | 40 KB | 1.875 ms | todos |
| 640 | 26 KB | 1.843 ms | todos |
| 500 | 17 KB | 1.817 ms | ✗ pierde `Caja` |

Tres conclusiones que van al diseño:

1. **El recorte táctil no va al producto.** No compra precisión y cuesta latencia, una pantalla más
   y un paso más al cajero. Se construyó, se midió y se descartó.
2. **El punto de trabajo es 1000 px de lado máximo**, calidad 0.9. Ahorra 28% de tiempo y 60% de
   bytes contra 1600 sin perder un campo, y deja margen cómodo sobre los 640 donde empieza a romperse.
3. **El piso de ~1.800 ms es el modelo, no la imagen.** De 1000 a 640 el tiempo no se mueve. Para
   bajar de ahí hace falta otro modelo u otro hardware — achicar más la foto no sirve.

> ⚠️ Medido en un iMac x86. **El número que importa es el del hardware real de una filial**, y ese
> todavía no se tomó. Ver §4.

> **Tamaño de muestra:** 2 cupones acá más los 16 anteriores, todos planos y bien iluminados.
> Alcanza para decidir la arquitectura. **No alcanza para prometer una tasa de acierto.**
---

## 2 · Decisiones tomadas

### 2.1 · El OCR corre en el **servidor local**, no en el teléfono

| | |
|---|---|
| **Motor** | PaddleOCR PP-OCR sobre **ONNX Runtime** (empaquetado RapidOCR) |
| **Dónde** | Filial, idealmente en su propia JVM |

**Por qué el servidor:**

- Una máquina que se dimensiona, en vez de N teléfonos que no se controlan.
- **Cero variabilidad de dispositivo.** El cajero con el Android viejo obtiene lo mismo que el del
  iPhone nuevo.
- Sin descarga de modelos por dispositivo, sin riesgo de que **Safari mate la pestaña** por presión
  de memoria, y sin el enredo de **COOP/COEP** que el WASM multihilo exige (y que, mal puesto,
  reduce el rendimiento a la mitad en silencio).
- **La imagen viaja ahí igual** para guardarse, así que procesarla no cuesta transferencia extra.
- Mejorar el motor es **un deploy**, no esperar que 24 sucursales actualicen sus teléfonos.

**Por qué PaddleOCR y no otro:** es el recomendado para entornos estrictamente CPU — *"la huella más
pequeña de los seis motores, sin dependencia de framework, la inferencia CPU más rápida"*. Los
descartados y su motivo: **Surya** recomienda GPU y resuelve layout complejo que un cupón térmico no
tiene; **EasyOCR** pesa 500 MB y es 3× más lento, y su fuerte es manuscrito; **docTR** queda como
alternativa si PaddleOCR se quedara corto; los **VLM** (GOT-OCR, olmOCR) son más precisos pero
necesitan GPU — impensable en 24 filiales.

**Por qué no ML Kit:** es un SDK **exclusivamente móvil**, Android e iOS. No existe versión de
escritorio, Java ni backend. El equivalente de Google del lado servidor es Cloud Vision API, que
sale a internet.

**Por qué no OCR de nube, aunque sería más preciso:** el motivo no es confidencialidad sino
**disponibilidad**. Cuando la conexión está inestable o caída, el mostrador tiene que seguir
funcionando.

> ⚠️ **Pendiente de verificar:** el binding Java de ONNX Runtime existe y es oficial
> (`com.microsoft.onnxruntime:onnxruntime` en Maven Central), y hay precedentes de correr PP-OCR
> así (`pponnxcr`, `OnnxOCR`). Falta **medir** que el rendimiento en la JVM sea comparable al de
> Python. Si lo es, el filial no necesita un servicio Python al lado — en 24 sucursales, esa
> diferencia es enorme.
>
> Si hiciera falta más velocidad, **PP-OCRv6 con OpenVINO** rinde 1,4× a 2,7× más que PyTorch en CPU.

### 2.2 · El mapa de diseño por POS es **híbrido**

Un editor drag-and-drop sobre la imagen del ticket, **por POS y no por proveedor**: dos POS del
mismo proveedor pueden imprimir distinto, y atar el patrón al proveedor es matarse.

**Las regiones se dibujan a mano pero se anclan a la etiqueta que cae adentro.** El editor se siente
tan simple como coordenadas absolutas, y el mapa sobrevive a que el ticket cambie de largo — que es
lo que pasa el día que el proveedor agrega una línea. Con coordenadas absolutas, ese día se rompen
todos los mapas de ese POS a la vez y nadie entiende por qué.

**El mapa es un intérprete, no una tijera.** Una sola pasada de OCR sobre la imagen produce cajas con
texto, coordenadas y confianza; el mapa **asigna** cada caja a un campo por posición relativa. No se
recorta campo por campo: eso serían N inferencias en vez de una. Y si el mapa falta para ese POS, se
cae a la búsqueda por etiqueta sin volver a procesar la imagen.

Es la misma idea que sostiene `formato_qr_pos`: **el formato es dato, no código.** Un proveedor nuevo
es una fila, no un release.

### 2.3 · Reparto de responsabilidades

> **Reescrito el 2026-09-09.** La versión anterior le daba al teléfono el overlay y el recorte.
> Los dos se cayeron: el overlay es imposible sobre HTTP (§2.8) y el recorte no compra precisión
> (§1.5).

| Dónde | Qué hace | Por qué ahí |
|---|---|---|
| **Teléfono** | Orientar por EXIF, **escalar a 1000 px**, comprimir a 0.9, chequear nitidez | No necesita modelos ni memoria, y baja 60% lo que viaja y lo que se guarda |
| **Servidor** | OCR, asignación de campos con el mapa, guardado | Un solo lugar para dimensionar y mejorar |

**La guía de encuadre pasa a ser texto, no un recuadro.** La cámara de la mayoría de los teléfonos no
enfoca por debajo de ~5-10 cm, así que la instrucción sigue siendo la misma —*no acercarse de más*—
pero la da una frase en pantalla, no un overlay. Que el ticket ocupe un tercio del cuadro **no
degrada el resultado** (§1.5), así que no hay nada que forzar.

**El chequeo de nitidez sigue en pie, pero después de la foto**, no en vivo: se calcula sobre el
`canvas` de la imagen ya tomada, que sí funciona en contexto inseguro. Si sale movida, *"sacá otra"*
**sin subir nada** — el mismo escalón de §2.6.

### 2.4 · Almacenamiento

| | |
|---|---|
| **Dónde** | Disco del filial, **ruta configurable**. `imagen_url` guarda la ruta |
| **Replicación** | **No viaja al central** |
| **Retención** | **Configurable**, con job de purga automática |
| **Qué se guarda** | La **escalada a 1000 px** que se usó para el OCR, no el original del sensor |

Con ~60 KB por imagen (medido, §1.5) y 50 ventas con tarjeta por caja, son unos **90 MB al mes por
sucursal**. Sin purga, cinco años son ~5,4 GB por sucursal que nadie va a mirar — razón de más para
que el job de purga no quede para después.

> Se guarda **la misma imagen que vio el OCR**. Si un campo salió mal, lo que se revisa es
> exactamente lo que el motor tuvo delante, no una versión distinta.

> `venta_tarjeta.imagen_url` **ya existe** en la tabla y en el modelo, sin uso. En `frc-mobile` la
> imagen **nunca se guardó**: `imagenUrl` jamás viajó en el input, la foto sólo alimentaba el OCR y
> se descartaba. Esto no es "arreglar algo que dejó de andar" — es construir algo que nunca existió.

### 2.5 · Puntos de entrada

**Híbrido:** el desktop muestra un QR **y** ofrece un botón de subir imagen. El QR lleva al celular
directo a ese registro — a la página de captura que sirve el filial (§2.7, §2.8), **no a la PWA**.

El orden sigue siendo: elegir el registro pendiente (que ya trae su terminal) → sacar la foto. No
porque haga falta para encuadrar, sino porque **el servidor necesita saber el POS para aplicar el
mapa de campos** cuando le llegue la imagen. Ese dato viaja en el QR.

### 2.6 · El fallback manual es el último de tres escalones, no el plan B

| Situación | Qué pasa |
|---|---|
| Nitidez insuficiente | *"Sacá otra"* — **sin subir nada** |
| OCR con campos dudosos | Se llenan los buenos, se pide confirmar sólo los dudosos |
| OCR falla, o el POS no tiene mapa | Carga manual completa |

**La respuesta del servidor no debería ser binaria.** Con el mapa, puede contestar **por campo**:
*"autorización, boleta y fecha con confianza alta; el monto dudoso"*. El fallback deja de ser
"cargá todo de nuevo" y pasa a ser "confirmá este campo" — una fricción completamente distinta.

Eso se apoya en algo comprobado: los dos motores dan **confianza por caja detectada**. Con el mapa se
sabe qué caja corresponde a qué campo, así que la confianza deja de ser un promedio inútil (medido:
el promedio por foto no distingue líneas buenas de malas) y pasa a ser **un semáforo por dato**.

**Los campos obligatorios de la carga manual se configuran por POS**, igual que el mapa. Un cupón
Stone no tiene número de boleta; exigirlo sería inventar un requisito.

### 2.7 · El teléfono es un **periférico de cámara del desktop**, no una pantalla de la suite

Decidido el 2026-09-09. La página de captura **no es la PWA oficial** ni un módulo suyo: es una
página de un solo propósito que se abre, saca una foto y se cierra. Sin login, sin sesión, sin
offline, sin nada de la suite.

**El desktop muestra un QR y el teléfono lo escanea.** Ese QR es el canal de configuración: lleva la
URL del filial, el token de la operación, la caja y la venta en curso. **El teléfono no configura
nada** — ni servidor, ni credenciales, ni app que instalar.

Lo que el QR **no** puede llevar es alcance de red: dice a dónde hablar, no hace que ese destino sea
alcanzable ni que el navegador lo acepte. Eso lo resuelve §2.8.

### 2.8 · La página la sirve el **filial, por HTTP plano** — y por eso no hace falta certificado

Medido el 2026-09-09 contra Chrome 152/Android y Safari/iPhone, con un servidor de prueba en la LAN.

**El camino que no funciona:** una página servida por HTTPS desde Cloudflare (la PWA) hablándole a
un filial en IP privada.

| | Chrome / Android | Safari / iOS |
|---|---|---|
| Página HTTPS pública → `fetch` a `http://IP-privada` | ✅ 200 · permiso de red local en `prompt` | ❌ **contenido mixto, bloqueo duro** |
| `ws://` desde página HTTPS | ❌ | ❌ |

En Chrome anduvo: la primera llamada tardó **3.155 ms** (paga el permiso y el preflight de red
privada) y con `targetAddressSpace: "local"` respondió en **16 ms**. **WebKit no tiene equivalente**:
no implementa *Local Network Access* ni la relajación de contenido mixto para destinos locales. Y
**Chrome en iOS también es WebKit**, así que fallan los dos — es un solo fallo, no dos.

**El camino que sí funciona: que la página la sirva el propio filial, por HTTP.**

| | |
|---|---|
| Contenido mixto | no existe — página HTTP, `fetch` HTTP |
| Red local (Chrome) | no aplica — origen privado → privado, no cruza espacios de direcciones |
| Safari / WebKit | nada que bloquear |
| Certificado, DNS, *rebinding* | **desaparecen del plan** |

Se aprovecha que **el puerto 8082 del filial ya está abierto y ya recibe conexiones de la LAN** — es
por donde le habla el desktop hoy. Sin puerto nuevo, sin tocar el firewall de ninguna caja, y la
imagen llega directo al proceso que hace el OCR: un solo salto.

**Qué cuesta HTTP** (verificado en ambos teléfonos):

- ❌ `getUserMedia` — **no existe** en contexto inseguro. Sin vista previa en vivo, sin overlay.
  No es un permiso que el usuario pueda conceder ni una cabecera que el servidor pueda mandar:
  lo decide el navegador mirando el esquema del origen.
- ❌ Service worker, instalación como PWA, `wss://`.
- ✅ `<input type="file" capture="environment">` — abre la cámara nativa y devuelve la foto.
- ✅ `canvas` y `toBlob` — se puede orientar, recortar y comprimir en el teléfono.

**Y el camino sin overlay le da mejor materia prima al OCR, no peor.** `input capture` usa la cámara
nativa: enfoque por toque, HDR, pipeline de foto fija y sensor completo. `getUserMedia` en Safari
entrega **cuadros de video** — menor resolución, sin `ImageCapture` (Safari no la implementa) y sin
control de enfoque. Para leer letra chica de un cupón térmico, la foto fija gana.

**El certificado no está muerto: está costeado.** Si alguna vez se quiere overlay en vivo o
suscripciones contra el filial, el único camino es TLS en la filial, con el nombre colgando de
`frcsuite.com` (DNS-01 por Cloudflare, un wildcard para toda la flota, distribuido por el
`check-update.sh` que ya corre cada 15 min). **No sirve el tailnet:** `base_domain` es `hs.farmacia`,
que no existe públicamente, y headscale no emite certificados. Y enrolar teléfonos en la VPN se
descartó aparte — sin MDM no hay forma automática de hacerlo, y son teléfonos personales de cajeros.

> ### ⚠️ Trampa verificada: la orientación EXIF
>
> La cámara de iOS casi siempre guarda el sensor **acostado** y marca la rotación en la etiqueta
> EXIF; Android suele entregar los píxeles ya rotados. **Los navegadores no coinciden** en si
> `createImageBitmap()` aplica esa etiqueta, y equivocarse rota el cupón 90°: PP-OCR corrige
> inclinaciones chicas, **no una imagen entera acostada**. Sería una caída de precisión silenciosa
> y sólo en algunos teléfonos.
>
> **No se resuelve suponiendo qué hace cada navegador** — costó dos intentos fallidos, uno por
> defecto y otro por rotar de más. Se resuelve **midiendo**: leer las dimensiones crudas del JPEG
> del marcador `SOF` y compararlas con las que devolvió `createImageBitmap`. Si vienen con los ejes
> intercambiados, el navegador ya aplicó la orientación y no hay que tocar nada.
>
> En la medición del 2026-09-09 **los dos** navegadores la aplicaban (`crudo=4032x3024`,
> `bmp=3024x4032`). Igual la detección se queda: es lo que hace que la próxima versión de Safari no
> rompa esto en silencio.
---

## 3 · Backlog

### 3.1 · Ticket con seña al posponer

Al elegir **"Registrar más tarde"**, imprimir un ticket que permita retomar el registro: **terminal,
hora, id de venta, monto y moneda**.

Hoy posponer no deja rastro físico: el cajero termina con un cupón del POS en la mano y ninguna forma
de saber a qué venta pertenece.

### 3.2 · Guardar la imagen del ticket

Ver §2.4. Se guarda **aunque el reconocimiento no logre extraer los datos** (previa confirmación del
usuario): la imagen es la evidencia, y permite completar a mano después y auditar más tarde.

### 3.3 · Adjuntar imágenes al cierre de caja

Con **tipo, descripción y observación**. El cierre es donde aparecen los papeles sueltos —cupones,
comprobantes, notas— y hoy no hay dónde ponerlos.

### 3.4 · Carga manual configurable

Ver §2.6. `configuracion_venta_tarjeta` hoy tiene un solo campo (`habilitado`); este ítem la
convierte en configuración de verdad.

### 3.5 · POS con recargo

Algunos POS aplican recargo: el valor del cupón es **mayor** que el que se pasó en el sistema.

Sin esto, ese cupón da diferencia de monto **en cada venta** y pide confirmación siempre — y un aviso
que sale siempre deja de leerse. Ya pasó exactamente eso con el bug de comparación de tipos,
arreglado el 2026-09-08.

**A definir:** si el recargo es por terminal o por proveedor; porcentaje o monto fijo; y si la
conciliación compara contra el monto **con** recargo o guarda los dos.

### 3.6 · Terminal de tipo `MAQUINA` o `WEB`

Algunos cupones no salen de un POS: se imprimen desde una PC. No hay aparato con código que
escanear, y el primer diálogo deja al cajero trabado.

**`terminal_pos` lleva un tipo.** Es mejor que registrar sin terminal o crear una terminal genérica:
la conciliación por terminal, el recargo (§3.5) y la acreditación futura siguen colgando de una
terminal real.

**La terminal puede viajar en el propio QR.** FRCP1 es un formato nuestro —a ValidaPix le pedimos el
nuestro y aceptó— y `formato_qr_pos.mapeo` ya mapea campos por nombre, así que sumar `terminal` es la
misma mecánica y **se configura desde el ABM, sin release**. Fallback: elegir de una lista filtrada
por proveedor y tipo `WEB`.

**Y los cupones ya traen el número de terminal impreso** — `Terminal:52287864` en Dinelco, `STONEID:`
en Stone. O sea el OCR también puede resolverlo.

### 3.7 · Un solo input que distingue código de QR

Hoy el primer diálogo pide escanear el código del POS antes de leer el cupón. **Cobra un peaje sin
dar nada a cambio**, verificado el 2026-09-08: sus dos aportes —priorizar formatos y detectar cupón
cruzado— están inactivos, porque el único formato cargado es comodín y un comodín nunca cuenta como
cruce.

Un solo input que **prueba primero como QR**: los patrones están anclados con `^...$`, así que un
código de terminal (`B1`, `TPOS-VPX-01`) no puede matchear un patrón de cupón. Si ninguno matchea, se
trata como código. **Ese orden es el seguro; al revés no lo es**, porque la búsqueda de código usa
`LIKE`.

---

## 4 · Lo que queda por verificar

1. **Rendimiento del binding Java de ONNX Runtime** (§2.1). Decide si el filial necesita un servicio
   Python aparte.
2. **Que los teléfonos estén efectivamente en la LAN de la sucursal.** Si algún cajero usa datos
   móviles, o si la WiFi de clientes está aislada de la de servidores, el filial no es alcanzable y
   el esquema se cae.
   **La mitad de navegador de este riesgo quedó resuelta el 2026-09-09** (§2.8): sirviendo la página
   desde el filial por HTTP, Chrome/Android y Safari/iOS llegan sin cert ni permisos. Lo que sigue
   abierto es **la topología de red de cada sucursal**, que no se probó: la medición se hizo en una
   LAN doméstica, no en un local. Falta confirmar que la WiFi que usan los cajeros alcanza al filial.
3. **El rendimiento en el hardware real de una filial.** Los ~1.800 ms de piso (§1.5) se midieron en
   un iMac. Es el número que define si el flujo se siente instantáneo o si el cajero espera.
4. **La tasa de acierto sobre cupones difíciles.** Todo lo medido hasta ahora son cupones planos y
   bien iluminados. Faltan térmicos gastados, con brillo, en ángulo, y los formatos brasileños con
   fotos de cámara nativa.

**Cerrados el 2026-09-09:**

- ~~Si el overlay mejora la precisión~~ — **la pregunta quedó sin objeto**. El overlay en vivo es
  imposible sobre HTTP (§2.8) y resultó innecesario: con el ticket ocupando un tercio del cuadro,
  PP-OCR sacó todos los campos igual (§1.5).
- ~~Si conviene recortar~~ — **no**. Se construyó el recorte táctil, se midió y se descartó: no
  compra precisión y cuesta latencia (§1.5).

---

## 5 · Sobre el flujo que ya existe en `frc-mobile`

**No vale la pena probarlo antes de construir**, y conviene saber por qué.

`frc-mobile` tiene cámara y OCR con **ML Kit** (`@pantrist/capacitor-plugin-ml-kit-text-recognition`
7.0.0). Contra los objetivos de esta fase falla tres de cinco: es Android, **nunca guardó la imagen**
y no tiene fallback manual. Y probarlo cuesta un release de Play Store, porque **no hay OTA**.

**Pero el motor no era el problema, y eso sí importa.** Quien lo escribió acertó en lo difícil: hay un
`reconstruirLineas()` que agrupa los bloques por `boundingBox` porque *"ML Kit separa el texto en
bloques por proximidad espacial: en tickets con columnas la etiqueta y el valor pueden caer en
bloques distintos"*. Es exactamente el problema que apareció midiendo Vision (`MONTO:` / `G.` /
`. 31.000` en tres líneas).

**Donde falla es en la extracción:** `extraerCampos()` son regexes hardcodeadas que ramifican por
**moneda**, no por proveedor. Dinelco e Infonet son los dos en guaraníes con etiquetas distintas
(`Aut:` contra `C.AUT:`) y caen en la misma rama. Además el fallback `(?:nsu|aut|auth)` es
peligrosamente laxo y no hay validación posterior, así que un valor equivocado se guarda como bueno.

**La misma lección apareció desde el otro lado en la medición de hoy:** Vision leyó los 16 cupones
casi perfecto y los "fallos" eran de los patrones, no del OCR. El motor no es el cuello de botella
—ni allá ni acá—; **la asignación de campos sí.** Eso es lo que el mapa por POS viene a resolver.
