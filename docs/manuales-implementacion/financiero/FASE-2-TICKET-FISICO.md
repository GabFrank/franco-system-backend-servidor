# Fase 2 — ticket físico, cámara y OCR

Levantado el **2026-09-08/09**, después de validar en alpha la fase 1 (lectura del QR con lector
keyboard-wedge desde el PDV y desde la lista).

**Qué resuelve:** el cupón que **no se puede leer por QR** — porque el POS no lo imprime, porque
salió borroso, o porque el cajero no lo escaneó en el momento.

---

## 1 · Lo que se midió, y con qué

16 fotos de cupones reales, sacadas con celular en condiciones distintas y **comprimidas por
WhatsApp** (720×1280, ~45 KB). Es un piso pesimista: la PWA reduce a 1600 px con calidad 0.75, o
sea mandaría fotos mejores que estas.

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

### Recortar sí, escalar no

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

### Dato que no se buscaba

En estas fotos **el ticket ocupaba apenas un tercio del cuadro**. Y la detección automática del
documento **falló en las 16**: ticket blanco sobre mostrador claro, con sombras encima, es el caso
difícil clásico. Eso no es un problema del código — **es el argumento a favor del overlay**: si el
sistema no puede encontrar el ticket solo, que el cajero lo alinee no es una comodidad, es el
mecanismo.

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

| Dónde | Qué hace | Por qué ahí |
|---|---|---|
| **Teléfono** | Overlay de encuadre, chequeo de nitidez, **recorte** | Nada de esto necesita modelos ni memoria, y el recorte baja 29% lo que viaja y lo que se guarda |
| **Servidor** | OCR, asignación de campos con el mapa, guardado | Un solo lugar para dimensionar y mejorar |

**El recuadro NO debe exigir llenar la pantalla.** La cámara principal de la mayoría de los teléfonos
no enfoca por debajo de ~5-10 cm: si el overlay pide llenar el sensor, el cajero acerca de más y sale
movido. **60-70% de la pantalla** deja distancia de enfoque y margen para que el recorte tenga de
dónde cortar.

**Recortar no es escalar.** Se recorta al recuadro más unos píxeles y se guarda **al tamaño
resultante**, en píxeles nativos.

### 2.4 · Almacenamiento

| | |
|---|---|
| **Dónde** | Disco del filial, **ruta configurable**. `imagen_url` guarda la ruta |
| **Replicación** | **No viaja al central** |
| **Retención** | **Configurable**, con job de purga automática |
| **Qué se guarda** | La **recortada**, no la original |

Con ~30 KB por imagen y 50 ventas con tarjeta por caja, son unos **45 MB al mes por sucursal**. Sin
purga, cinco años son ~2,7 GB por sucursal que nadie va a mirar.

> `venta_tarjeta.imagen_url` **ya existe** en la tabla y en el modelo, sin uso. En `frc-mobile` la
> imagen **nunca se guardó**: `imagenUrl` jamás viajó en el input, la foto sólo alimentaba el OCR y
> se descartaba. Esto no es "arreglar algo que dejó de andar" — es construir algo que nunca existió.

### 2.5 · Puntos de entrada

**Híbrido:** el desktop muestra un QR **y** ofrece un botón de subir imagen. El QR lleva al celular
directo a ese registro, y de ahí sigue el flujo normal de la PWA.

El overlay necesita saber el POS **antes** de la foto, así que el orden es: elegir el registro
pendiente (que ya trae su terminal) → abrir la cámara con el overlay de ese POS.

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
   el esquema se cae. En la PWA importa más que en la app nativa: se sirve por HTTPS desde Cloudflare
   y tendría que hablarle a un filial en IP privada — el mismo problema de contenido mixto que ya
   frenó a `alpha.desk`.
3. **Si el overlay mejora la precisión** además de la estructura. Requiere fotos nuevas con el ticket
   bien encuadrado. **Se decidió que no es determinante**: la calidad actual ya alcanza (92% / 85%
   con fotos de 45 KB sacadas de apuro), y el overlay se justifica por consistencia y por habilitar
   el mapa.

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
