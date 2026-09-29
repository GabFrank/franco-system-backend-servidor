# Plan — logo en recibos de RRHH y en los KuDE de nota de remisión / nota de crédito

Rama: `feature/reportes-logo-recibos-kude` (desde `develop` @ `d05a64fd`). Pieza: **central** solamente.

## Qué pide

Imprimir el logo de la empresa en los recibos de RRHH y en los KuDE de nota de remisión y de nota
de crédito, con el mismo mecanismo que `transferencia.jrxml`: parámetro `logo` (`String`, ruta al
archivo) y un `<image>` arriba a la izquierda.

Decidido con Franco (2026-09-29):

- RRHH: los tres recibos A4 **y** el acta de amonestación. Los tickets térmicos (58/80 mm y
  ESC/POS) quedan afuera.
- NC y NR: logo **siempre**. No se copia la regla del KuDE de factura que lo oculta en moneda
  extranjera.

## Estado actual (verificado en el código)

| Plantilla | `logo` declarado | `<image>` | Quién llena `logo` |
|---|---|---|---|
| `transferencia.jrxml` (modelo) | sí | 120×69 en (0,0), sin `onErrorType` | `ImpresionService:891` → `getImagePath() + File.separator + "logo.png"` |
| `nota-remision-kude.jrxml` | sí | 130×40 en (10,5), `onErrorType="Blank"` + `printWhen` no vacío | `KudeNotaRemisionService:85` → **`""` fijo** |
| `nota-credito-kude.jrxml` | sí | **no tiene** | `KudeNotaCreditoService:83` → **`""` fijo** |
| `recibo-rrhh.jrxml` | no | no | `ReporteRrhhService` (vale, préstamo, aguinaldo, bono, penalización en A4) |
| `recibo-liquidacion.jrxml` | no | no | `ReciboLiquidacionService.generarBase64` (A4) |
| `recibo-finiquito.jrxml` | no | no | `ReporteRrhhService.finiquitoBase64` (A4) |
| `acta-advertencia.jrxml` | no | no | `ReporteRrhhService.actaAdvertenciaBase64` |

El logo vive en `ImageService.getImagePath() + "logo.png"` (`~/FRC/resources/images/logo.png` en
Linux; 1701×1028 px, relación ≈1,65). Si el archivo no existe, un `<image>` sin `onErrorType`
**revienta el fill**; por eso las plantillas nuevas usan el patrón del KuDE (`onErrorType="Blank"` +
`printWhenExpression` de no vacío), y el Java manda `""` cuando el archivo no está.

## Diseño

1. **`ImageService.getLogoReporte()`** devuelve un `java.awt.Image` con el logo escalado a 400 px de
   ancho (relación intacta, `Scalr`, ya dependencia), o `null` si no hay logo usable. Lo toma de
   `getImagePath() + "logo.png"` (sin agregar `File.separator`: la ruta ya termina en barra). «Usable»
   significa que `isFile() && length() > 0` y que `ImageIO.read` lo decodifica. Si no, devuelve `null`
   y deja un `WARN` en el log, para que un host sin logo se note. Se cachea en memoria por
   `lastModified` del archivo: no se decodifican 1701×1028 px en cada recibo, y cambiar el logo en el
   disco se ve sin reiniciar. No se tocan los llamadores viejos (transferencia, etc.).
   *Por qué imagen y no ruta (decisión de Franco, 2026-09-29, hallazgo A5):* con la ruta, Jasper
   embebe el PNG original (~860 KB) en cada PDF. Escalado a 400 px (~290 dpi a 70 pt de ancho) el
   PDF lleva decenas de KB y en papel se ve igual.
2. **Servicios**:
   - `KudeNotaRemisionService` / `KudeNotaCreditoService`: hoy no tienen dependencias y los tests
     los crean con `new`. Se agrega `@Autowired private ImageService imageService` por campo y
     `logo()` devuelve `""` si es `null` (tests unitarios). Así no cambia su constructor.
   - `ReporteRrhhService` / `ReciboLiquidacionService` tienen **constructor explícito**: se le suma
     `ImageService` como último argumento y se actualizan los dos `new` de
     `RecibosRrhhNumeroObservacionTest` (el único `new` fuera de Spring). Sin riesgo de ciclo:
     `ImageService` solo depende de `Environment`.
   - `params.put("logo", …)` en: recibos de `recibo-rrhh` A4, finiquito A4, acta, liquidación A4.
     Los caminos de ticket no cambian.
3. **Plantillas** — `<parameter name="logo" class="java.awt.Image"/>`, logo arriba a la izquierda,
   `hAlign="Center"`, relación del archivo (≈70×42), `onErrorType="Blank"` y `printWhenExpression`
   `$P{logo} != null` (los tests actuales no pasan `logo`: llega `null`). En los dos KuDE el
   parámetro pasa de `String` a `java.awt.Image`; su único escritor es su propio service.
   - `recibo-rrhh`: logo (0,0) 70×42; empresa `x=80 w=475`; título `x=80 w=320` (termina en 400,
     donde empieza «Fecha»). Banda igual (108).
   - `recibo-liquidacion`: la hoja lleva **las dos vías** y el techo medido es 25 ítems. Logo (0,0)
     70×42; empresa y línea RUC/dirección/teléfono pasan a `x=80 w=395` (centradas simétricas, no
     pisan el logo); la banda título crece **+14** (110→124) y todo lo que está debajo de `y=30`
     baja 14.
     Techo nuevo: 24 ítems (802 − 434 = 368 / 15). El test `lasDosViasEntranEnUnaHoja` corre hasta
     20: sigue siendo el gate. Se corrige el comentario del techo.
   - `recibo-finiquito`: logo (0,0) 70×42; título a `x=80, y=10`; banda +20 y lo de `y≥22` baja 20.
   - `acta-advertencia`: logo (0,0) 70×42; empresa/RUC centrados en `x=80, w=395` (simétrico); lo
     de `y≥38` baja 8 (banda 130→138).
   - `nota-credito-kude`: logo (10,8) 60×36 dentro del recuadro; el bloque razón social / RUC /
     timbrado / vigencia / dirección pasa a `x=78, w=252` (pierde 68 pt, no 90). Banda igual.
   - `nota-remision-kude`: solo cambia la clase del parámetro y el `printWhen`; la posición queda.

## Fases

**Fase 1 (única, un commit `feat(reportes): …`)** — todo lo de arriba. Es chico y de una sola
responsabilidad; separarlo en dos commits no deja nada probable a medias.

Tests (JUnit, sin contexto Spring; imagen PNG chica generada en `@TempDir`):

- Por cada una de las 6 plantillas: con `logo` = imagen el print tiene un `JRPrintImage` y el
  export a PDF sale; con `null` no lo tiene y no falla.
- `ImageService`: archivo inexistente, vacío y corrupto → `null` sin excepción; PNG válido → ancho
  400; segunda llamada devuelve la misma instancia (caché); PDF con logo escalado chico (umbral en el
  test).
- **Recorte**: `getFullText()` devuelve el texto aunque Jasper lo recorte, así que los tests de texto
  no alcanzan. Test que compara `getTextHeight()` con `getHeight()` en los campos corridos, con
  razón social de 45 caracteres y dirección de 90 (NC) / 120 (liquidación, acta).
- **Una hoja**: liquidación `==1` con 24 ítems y `==2` con 25 (fija el techo nuevo; hoy el gate
  solo llega a 20); finiquito `==1` con 15 y 20 filas. Los techos se miden corriendo el test, no
  se suponen.
- **Por service** (sin Spring, `ImageService` mock): los 4 caminos — recibo-rrhh A4, finiquito A4,
  acta, liquidación A4 — y los 2 KuDE llevan `logo` en el print. Sin `ImageService` (KuDE con
  `new`) → `""`, sin NPE.

## Datos nuevos

Ninguno: sin columnas, sin migraciones, sin cambios de GraphQL. El único dato es el parámetro
`logo`: lo escriben los 4 servicios de arriba, lo leen las 6 plantillas.

## Hallazgos de la auditoría del plan (paso 5) y qué se hizo

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| A1 | A | No hay evidencia de que `logo.png` exista en los hosts del central (bodega :8081, farmacia :8082, alpha mauro :8083); `getHomePath()` prefiere `HOMEPATH` del `.env` | `WARN` en `getLogoReporte()`; verificar el archivo en cada host **antes** de pedir deploy (queda en el PR). Sin archivo el recibo sale igual, sin logo |
| A2 | A | Windows: `getImagePath()` ya termina en barra | `getLogoReporte()` no agrega separador. La rama Windows no se prueba en Linux: queda anotado |
| A3 | A | Filial no genera estos reportes (solo `marcaciones` y `ticket-58mm`) | Confirma «central solamente» |
| A4/A6 | A | Sin cambio de contrato; único consumidor de cada método es su resolver; nada persiste ni reenvía el PDF | Sin acción |
| A5 | A | El PNG 1701×1028 (~860 KB) se embebe entero: un recibo de vale pasa de pocos KB a ~1,1 MB en base64 | Franco eligió escalar: `java.awt.Image` a 400 px, cacheado (diseño §1) |
| B1 | B | recibo-rrhh: título con `w=400` pisaría «Fecha» | Fijado `x=80 w=320` |
| B2 | B | liquidación: línea RUC/dirección centrada en 555 pisa el logo si es larga | `x=80 w=395` + test de recorte |
| B3 | B | NC: bajar a `w=230` puede recortar sin que ningún test lo vea | Logo 60×36, `w=252`, test con `getTextHeight()` |
| B4 | B | Techos de una hoja bajan sin gate (liquidación 21–24, finiquito sin test) | Tests de páginas nuevos |
| B5 | B | RRHH tiene constructor explícito, no `@RequiredArgsConstructor` | Verificado y corregido en el diseño |
| B6 | B | `logo` null en tests actuales; PNG corrupto puede fallar en el export | `printWhen` con null-check; el archivo corrupto lo descarta `ImageIO.read` antes de Jasper (test) |
| B7 | B | Tests de plantilla no detectan un service que se olvida el `put` | Tests por service |

## Fuera de alcance / sin verificar

- El logo del KuDE de factura en moneda extranjera (regla existente, no se toca).
- La rama Windows de `getImagePath()`: no se prueba en esta máquina.
- Cómo se ve en papel: se verifica generando los PDF en local y mirándolos (paso 9).

## Implementación — lo que cambió respecto del diseño (2026-09-29)

- **Línea RUC/dirección/teléfono** (liquidación y acta): con `w=395` y una dirección de 60
  caracteres el teléfono salía cortado. Pasó a alto 24 (dos renglones). Entra antes del recuadro
  (`y=46`) sin tocar la banda.
- **Dirección del emisor en la NC**: concatena dirección + ciudad + departamento (con datos reales
  ~80 caracteres). Con `w=252` en un renglón se cortaba: pasó a alto 22. Entra en el recuadro de 90.
- **Recorte en Jasper**: `getFullText()` devuelve el texto **ya recortado** y `getTextTruncateIndex()`
  vino `null`. El hallazgo B3 del auditor decía lo contrario y no se sostiene. Los tests buscan el
  texto entero esperado. Se verificó por mutación (angostar el campo → el test falla).
- **Techos de una hoja medidos**: liquidación 24 ítems (era 25); finiquito 19 conceptos con
  observación larga / 20 con corta (era 21); recibo-rrhh 26. Los fijan
  `LogoRecibosRrhhJrxmlTest.liquidacionTechoDeUnaHoja` y `finiquitoTechoDeUnaHoja`.
- **Peso**: con el `logo.png` real de esta máquina escalado a 400×242, cada PDF pesa ~17–19 KB
  (sin logo, 2–3 KB). Pasando la ruta como transferencia serían ~860 KB.
- **Verificación con mutación** de los tests por service: sin el `put("logo")` del finiquito, el
  test falla.

## Revisión con Franco (2026-09-29) — diseño final

Probado en local, Franco pidió cambiar el diseño de los recibos:

- **Notas (NR y NC):** logo arriba, más grande (~58–60 pt de alto) y centrado en su columna, no pegado
  al borde. En la NC el bloque razón social / RUC / timbrado / dirección pasa a `x=126 w=209`.
- **Recibos de RRHH y acta: marca de agua, no logo en el encabezado.** Con el logo arriba el recibo
  crecía (bandas +8/+14/+20) y a Franco le parecía grande incluso cortado. Las cuatro plantillas
  vuelven al layout de `develop` (tamaño de producción: sueldo 25 ítems, finiquito 21) y llevan de
  fondo `ImageService.getMarcaAguaReporte()`: el logo escalado y aclarado al 12 % mezclándolo con
  blanco (opaco, no depende de que la impresora maneje alfa). Parámetro `marcaAgua`.
  - La marca va **dentro de cada vía**, no centrada en la hoja, para que quede entera al cortar: el
    sueldo lleva dos (background para la vía del funcionario + primer elemento del summary para la
    vía ORIGINAL). El acta, que no se corta, la lleva centrada detrás del texto.
  - Los `<rectangle>` del sueldo y del acta pasaron a `mode="Transparent"`: por defecto Jasper los
    pinta con fondo blanco y tapaban la marca («cortada por la cabecera»). Lo cubre
    `MarcaAguaRecibosRrhhJrxmlTest.ningunElementoOpacoTapaLaMarca`.
  - Tinta: a 12 % gasta del orden del logo chico a todo color. Los PDF pesan 5–15 KB.
- Los techos de una hoja vuelven a los de producción y los fijan
  `MarcaAguaRecibosRrhhJrxmlTest.liquidacionTechoDeUnaHoja` (25/26) y `finiquitoTechoDeUnaHoja` (21/22).
  Las notas de la sección anterior sobre techos 24 y 19–20 y la línea RUC de dos renglones quedan
  **reemplazadas** por esto.
