# El botón «Probar» del ABM de formatos

**2026-09-17.** Cierra la deuda que la jornada del 2026-09-16 dejó como prioridad: *«Hoy cada
patrón frágil costó una venta interrumpida»*.

---

## 1 · El problema

Un formato de terminal POS es un patrón (regex con grupos nombrados) más un mapeo (qué grupo es qué
campo). Hasta acá, **la única forma de saber si ese patrón aguantaba un cupón de verdad era
cobrar**: se guardaba el formato y el siguiente cliente que pagaba con tarjeta era el ensayo. Si el
patrón fallaba, la venta quedaba interrumpida con el cliente delante y el cajero buscando la salida
manual.

La vista previa que ya existía no cubre esto: corre contra la **cadena de ejemplo** guardada, que es
un texto del pasado. Y está medido que el OCR devuelve un texto distinto en cada lectura del mismo
papel, así que pasar la vista previa no dice nada sobre el próximo cupón.

## 2 · Qué se hizo

Una solapa **«Probar»** en el ABM del formato, con el mismo gesto del PDV: se abre un QR, se
fotografía o se escanea un cupón de papel, y la pantalla dice **PASA** o **NO PASA**.

| dónde | qué |
|---|---|
| `central` | Query `probarFormato(formatoTerminalPosId, token, texto)` → `ResultadoPruebaFormato` |
| `central` | `CapturaMuestraGraphQL.probarFormato()` — corre `ExtractorCupon.extraer()` con el patrón y el mapeo del formato guardado |
| `desktop` | `probar-formato-panel` — QR + foto para los formatos MAQUINA, input de lector para los demás |
| `desktop` | `edit-formato-terminal-pos` — solapa nueva, y `asegurarGuardado()` extraído de `onGuardar()` |

**Es una query, no una mutation**: no escribe nada. Por eso el mismo cupón se puede probar las veces
que haga falta mientras se ajusta el patrón, y el token de la captura no se consume.

### Qué significa «pasa»

Pasa si el patrón reconoció el cupón **y** ningún campo que el mapeo marca `"obligatorio": true`
quedó vacío. La lista se arma recorriendo el **mapeo**, no lo extraído: un campo declarado que el
cupón no trajo tiene que aparecer diciendo que falta. Recorrer sólo lo extraído lo haría invisible,
que es justamente el caso que la prueba existe para encontrar.

Se muestran además los campos que el patrón captura y el mapeo no nombra —van a `datos_extra`— y el
texto sobre el que corrió el patrón, que es lo primero que hay que mirar cuando no matchea: el
patrón se aplica sobre lo que leyó el OCR, con sus rarezas, no sobre el papel.

### Corre contra el formato guardado, y por eso guarda primero

La prueba corre contra lo **persistido**: es lo que las 24 filiales van a recibir. Probar un
borrador diría algo que no es cierto de nada desplegado.

Para que eso no obligue a un Guardar manual en el medio de cada iteración —que lo único que consigue
es que alguien lo saltee— **«Probar» guarda antes si hay cambios**. Si el formulario está
incompleto, la validación que ya existía avisa y lleva a la solapa del hueco, y la prueba no corre.

⚠️ Eso hizo falta refactorizar `onGuardar()`: se partió en `validarAntesDeGuardar()` +
`persistir(): Observable<FormatoTerminalPos>`, y el panel recibe `asegurarGuardado` como `@Input`.
El guardado también marca el formulario como `pristine`, que es lo que evita que dos «Probar»
seguidos vuelvan a guardar lo mismo.

## 3 · Lo que este cambio NO hace

- **No propone el patrón.** Sigue siendo trabajo de quien configura; esto sólo dice si el que
  escribió funciona.
- **No guarda la muestra como caso de prueba.** Cada prueba deja su captura en `captura_muestra`
  como cualquier otra, pero no hay un banco de regresión: probar contra texto guardado es probar
  contra el pasado.
- **No toca la vista previa.** Siguen conviviendo: una prueba el patrón contra el ejemplo escrito,
  la otra contra un cupón real recién escaneado.

## 4 · Cómo probarlo a mano

1. Abrir un formato MAQUINA en el ABM → solapa **Probar** → «Probar con el teléfono».
2. Escanear el QR con el celular y fotografiar un cupón de ese modelo de aparato.
3. Verificar: sale **PASA** y la lista muestra cada campo con su valor ya mapeado (el monto
   escalado, no el grupo crudo).
4. Romper el patrón a propósito (cambiar un `\*` por un `-`), **Probar** otra vez sin guardar a
   mano: tiene que guardar solo y devolver **NO PASA** con el motivo.
5. Marcar un campo como `"obligatorio": true` en el mapeo y probar con un cupón que no lo trae:
   **NO PASA**, y ese campo en rojo diciendo que el formato lo exige.
6. En un formato de tipo QR/WEB: pegar o escanear la cadena del cupón en el input y probar.

> **Antes de llegar acá, sin la app:** `RunnerFormatoCuponTest` (`src/test/.../ocr/`) corre el mismo
> motor OCR y el mismo `ExtractorCupon` sobre una carpeta de fotos (o `.txt` con cadenas de QR, una
> por línea, para un formato `WEB`) y un JSON del formato, e imprime
> el texto que ve el OCR, campo por campo con su confianza, y «N de M fotos completas». Se saltea
> sin `-Dcupon.dir`, así que el CI no lo corre. El procedimiento entero (fotos → patrón → mapeo →
> ABM → Probar) está en la skill `frc-pos-expert`, `formato-desde-fotos.md`.
