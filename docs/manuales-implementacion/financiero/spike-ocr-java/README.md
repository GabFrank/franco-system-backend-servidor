# Spike — PP-OCRv4 en Java puro sobre ONNX Runtime

Código que respalda [§2.9 de FASE-2-TICKET-FISICO.md](../FASE-2-TICKET-FISICO.md).
**No es código de producción**: es la verificación de que el OCR puede correr dentro del
filial sin un servicio Python al lado. Cuando se integre, va al repo del filial como
componente Spring.

## Qué hay acá

| Archivo | Qué hace |
|---|---|
| `Imagen.java` | Imagen en BGR, resize bilineal en punto fijo (replica `cv2.resize`), rotaciones, recorte de cuadrilátero por homografía |
| `DetectorCajas.java` | Post-proceso DB completo: dilatación, componentes conexas, envolvente convexa, rectángulo de área mínima por calipers rotantes, *unclip*, puntaje por polígono. **Reemplaza a OpenCV** — sin dependencias nativas |
| `MotorOcr.java` | Pipeline: detección → recorte → clasificador de ángulo → reconocimiento por lotes → decodificación CTC |
| `Principal.java` | CLI: corre sobre una o más imágenes y muestra tiempos por etapa |
| `Volcar.java` | Vuelca el texto de un directorio a `.txt`, para comparar contra Python |
| `Bench.java` | Corre el detector sobre un tensor ya preprocesado por Python. Aísla ONNX Runtime del resto |

Unas 776 líneas. Sin OpenCV, sin JNI propio, sin servicio externo.

## Qué hace falta para correrlo

**No están versionados** por tamaño:

1. **El jar de ONNX Runtime** (71,8 MB) — trae los binarios nativos de todas las plataformas:
   ```
   https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.23.2/onnxruntime-1.23.2.jar
   ```
2. **Los tres modelos** (15,5 MB) — vienen dentro del paquete `rapidocr-onnxruntime`:
   ```
   pip install rapidocr-onnxruntime
   # los .onnx quedan en site-packages/rapidocr_onnxruntime/models/
   ```
   Hacen falta `ch_PP-OCRv4_det_infer.onnx`, `ch_PP-OCRv4_rec_infer.onnx` y
   `ch_ppocr_mobile_v2.0_cls_infer.onnx`.
3. **El diccionario `ppocr_keys.txt`** — ⚠️ **no se lee de la metadata del modelo**, ver abajo.
   Se extrae así:
   ```python
   import onnxruntime as ort
   s = ort.InferenceSession("ch_PP-OCRv4_rec_infer.onnx", providers=["CPUExecutionProvider"])
   v = s.get_modelmeta().custom_metadata_map["character"]
   open("ppocr_keys.txt", "w", encoding="utf-8").write("\n".join(v.splitlines()))
   ```
   Tiene que quedar en la misma carpeta que los `.onnx`, con **6623 líneas**.

```bash
javac -d classes -cp onnxruntime-1.23.2.jar src/*.java
java -cp classes:onnxruntime-1.23.2.jar Principal ./modelos foto.jpg
```

## Las dos trampas que cuestan tiempo

**La metadata del modelo se corrompe leída desde Java.** El binding expone la metadata por JNI
`NewStringUTF`, que usa *Modified UTF-8* y no admite secuencias de 4 bytes. El único carácter
fuera del BMP del diccionario (`U+231C9`, línea 6137) vuelve como 4 caracteres sueltos, corre
todos los índices y rompe la decodificación **en silencio** — se manifiesta como "faltan los
espacios", que no apunta a la causa. Por eso el diccionario va en archivo y `MotorOcr` **verifica
su largo contra la dimensión de salida del modelo**, abortando al arrancar.

**Queda una diferencia de ~1 px por caja contra Python** (IoU medio 0,974). Ya se descartaron por
medición: la salida del detector (idéntica bit a bit), la dilatación (idéntica a `cv2.dilate`), el
resize (corregido a punto fijo) y cuantizar el *unclip* a enteros. Es el redondeo sub-píxel de
`minAreaRect` y la teselación de arcos de `pyclipper`. **No la busques en otro lado.**

## Antes de integrarlo al filial

- **Podar el jar de ORT en el build.** Pesa 71,8 MB con los nativos de cinco plataformas; el
  `frc-filial-server.jar` se descarga entero en cada auto-update, en 24 sucursales. Dejar
  linux-x64 (21,3 MB) y win-x64 (13,5 MB).
- **Medir en hardware de filial.** Los tiempos publicados son de un iMac de 4 núcleos.
- Las sesiones de ORT son caras de crear (~100 ms) y seguras para reusar: **una sola instancia
  como singleton**, no una por request.
