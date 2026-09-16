# FRCP1 v2 — que el QR diga de qué punto salió

**Decidido el 2026-09-16.** Pedirle a ValidaPix que agregue el identificador del punto de venta al
QR del cupón. Este documento es lo que hay que mandarles, y lo que nosotros hacemos mientras tanto.

---

## El problema, en una frase

El PDV puede resolver **solo** de qué aparato salió un cupón, si el cupón lo dice. Dinelco imprime
`Terminal:52287864`, Stone imprime `STONEID:`, INFONET imprime `C.N.:82829`. **FRCP1 no lleva nada
equivalente**, así que con ValidaPix el cajero tiene que elegir la terminal a mano, siempre.

Y es peor que con una maquinita: en un POS físico hay un aparato con una etiqueta pegada que se
puede escanear. **En una terminal WEB no hay aparato** — el código interno (`VP-CAJA1`) sólo existe
en nuestra base, así que la pantalla le pide al cajero escanear algo que no está en ninguna parte.

> El sistema **no adivina** la terminal, y eso no se va a cambiar: elegir mal deja la conciliación
> por terminal equivocada y nadie lo nota. Se pide el dato o se elige de la lista; no hay tercera.

## Lo que se pide

Un campo más al **final** de la cadena, con el identificador del punto de venta:

```
hoy:  FRCP1*AUTH*BOL*CUR*AMT*REF*TS
v2:   FRCP1*AUTH*BOL*CUR*AMT*REF*TS*TERM
```

| | |
|---|---|
| **Posición** | último, número 8 |
| **Contenido** | el identificador del punto de venta / establecimiento, el mismo que ValidaPix use en su propia conciliación |
| **Charset** | `A-Z0-9`, hasta 20 caracteres — dentro del alfanumérico del QR, igual que el resto |
| **Vacío** | permitido (`…*TS*`), para los puntos que todavía no lo tengan cargado |
| **Estabilidad** | tiene que ser **el mismo valor siempre** para el mismo punto. Si cambia, deja de resolver |

**Al final y no en el medio, a propósito.** Un campo intercalado rompería a todo consumidor que
cuente posiciones; agregado al final, un parser que corte en 7 sigue leyendo lo mismo.

## Por qué al final es suficiente para nosotros

Nuestro parser es **regex, no `split` posicional** — verificado el 2026-09-16, no hay ningún chequeo
de «exactamente 7 elementos» en `qr-pos-parser.ts`. Así que el patrón puede aceptar **las dos
formas** y la transición no necesita coordinarse con nadie.

Patrón, con el grupo nuevo **opcional** (ya aplicado en el entorno de prueba):

```
^FRCP1\*(?<auth>[A-Z0-9]{0,20})\*(?<bol>[A-Z0-9]{0,20})\*(?<cur>PYG|BRL|USD)\*(?<amt>[0-9]{1,15})\*(?<ref>[A-Z0-9]{0,40})\*(?<ts>[0-9]{12})(?:\*(?<term>[A-Z0-9]{0,20}))?$
```

Verificado contra cuatro cadenas:

| cadena | patrón viejo | patrón nuevo |
|---|---|---|
| cupón REAL de producción (`FRCP1*CXF1**BRL*9455*E607…*202608271401`) | OK | OK |
| cupón de prueba de 7 campos | OK | OK |
| v2 con terminal | **rechaza** | OK, `term=VPX0042` |
| v2 con terminal vacía | **rechaza** | OK |

Es la misma lección que costó tres correcciones de patrón esa misma mañana: **lo que puede no venir
va opcional**. Un grupo obligatorio de más no deja el campo vacío, tira el cupón entero.

## Plan de migración — quién hace qué

| # | Qué | Quién | Cuándo |
|---|---|---|---|
| 1 | Patrón de `ValidaPix FRCP1` acepta las dos formas | nosotros | **hecho en prueba**; en producción, antes del paso 3 |
| 2 | Cargar en `terminal_pos.serie` el identificador que ValidaPix va a emitir, por cada punto | nosotros | cuando ellos confirmen los valores |
| 3 | ValidaPix empieza a imprimir el campo 8 | ValidaPix | su despliegue |
| 4 | Agregar `"terminal":{"de":"term"}` al mapeo del formato | nosotros | **recién después** del paso 3 |

**El orden importa y el paso 4 va último.** Con el formulario del cupón saliendo del mapeo (cambio
del 2026-09-16), declarar `terminal` antes de que el campo exista dibujaría un input vacío que el
cajero **no puede llenar** porque el papel de hoy no lo trae. Es el mismo error que se corrigió ese
día con «Referencia del proveedor».

**El paso 2 es el que puede trabar todo.** El atajo busca por `terminal_pos.serie` con coincidencia
**exacta**: si lo que ValidaPix emite no es idéntico a lo que tenemos cargado, el cupón se lee pero
la terminal no resuelve y el cajero la elige a mano — o sea, se queda como hoy. Hay que pedirles la
**lista de identificadores por punto**, no deducirla.

## Lo que NO se pide, y por qué

- **No se pide bumpear a `FRCP2`.** Un prefijo nuevo obliga a soportar dos formatos en paralelo por
  tiempo indefinido. Un campo opcional al final no.
- **No se pide que sea obligatorio.** Un punto sin identificador cargado tiene que poder seguir
  emitiendo cupones; el cajero elige la terminal como hoy.
- **No se pide cambiar `REF`.** Sigue siendo el `EndToEndId` del BCB, con las tres trampas ya
  documentadas (comparar sin distinguir caja, `TS` local vs UTC del EndToEndId, `BOL` vacío en Pix).

## Alternativa que quedó descartada, y por qué conviene recordarla

Se evaluó **resolver la terminal sola cuando hay una sola de ese formato en la sucursal**. Es la
misma regla que el código ya aplica con la serie —resolver sólo cuando es único, nunca adivinar— y
no depende de terceros. Se descartó a favor de pedir el dato: el identificador en el cupón es
verdad del proveedor, y la unicidad por sucursal es una suposición que se rompe sola el día que un
local abra la segunda caja.

Si ValidaPix no responde o demora, esta alternativa sigue sobre la mesa y se implementa en el
desktop, sin backend.
