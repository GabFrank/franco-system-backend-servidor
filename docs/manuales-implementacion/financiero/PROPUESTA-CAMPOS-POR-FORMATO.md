# Propuesta — que el formato declare los campos, y no una lista fija

**Escrita el 2026-09-15**, después de analizar **19 transacciones reales** de dos proveedores
(13 INFONET, 6 PYXPAY). Todo lo que sigue sale de esos tickets, no de suposiciones.

---

## 1 · El diagnóstico, más preciso que «los campos fijos fueron un error»

El mapeo **nunca fue cerrado**: `MapeoFormato.campos()` lee las claves de primer nivel que haya, sean
cuales sean. El conjunto fijo está en **dos lugares distintos**, y no cumplen la misma función:

| Dónde | Qué impone | ¿Se gana algo? |
|---|---|---|
| Columnas de `venta_tarjeta` | 4 datos del cupón | **sí, para dos de ellos** |
| `CAMPOS` del diálogo (desktop) | qué ve el cajero | **no** |

Y mirando qué hace cada columna:

| Columna | ¿Universal? | ¿Se consulta? | Veredicto |
|---|---|---|---|
| `monto_escaneado` | **sí** — todo cupón tiene importe | es el **único cruce objetivo** contra lo cobrado | **se queda** |
| `codigo_autorizacion` | el **concepto** sí (el id que da el proveedor); el nombre es INFONET-céntrico | **tiene índice**: `idx_venta_tarjeta_codigo_autorizacion`, que sostiene el chequeo de cupón duplicado | **se queda** |
| `numero_boleta` | **no** — PYXPAY no lo imprime | nadie | no necesitaba columna |
| `identificador_transaccion` | ya es genérico | nadie | — |

**Conclusión:** hay **dos conceptos universales** —el importe, y el identificador que el proveedor le
da a la transacción— y **N campos propios de cada proveedor**. El error no fue tener columnas: fue
**tratar los cuatro como si fueran lo mismo**, y que el diálogo mostrara esa lista en vez de lo que
el formato declara.

En PYXPAY, `COD TRANS.` **es** el identificador de la transacción. No es que falte
`codigoAutorizacion`: es que se llama distinto.

---

## 2 · Los tres cambios

### Cambio 1 · El diálogo lo manda el mapeo

**Qué:** `camposSegunFormato()` deja de recorrer la lista fija y arma los campos **desde el mapeo**.
Cada entrada declara además su `etiqueta` —el nombre que ve el cajero— y su `tipo`.

```json
{
  "codigoAutorizacion": { "de": "auth", "etiqueta": "Cód. autorización", "obligatorio": true, "tipo": "TEXTO" },
  "monto":              { "de": "total", "etiqueta": "Total", "obligatorio": true, "tipo": "NUMERO" },
  "lote":               { "de": "lote", "etiqueta": "Lote", "obligatorio": false, "tipo": "NUMERO" }
}
```

Lo que mapea a una columna va a la columna; **lo demás sigue yendo a `datos_extra`, que ya funciona**
— la diferencia es que ahora se muestra, se valida, se puede marcar obligatorio y entra al semáforo.

**Se conserva el fallback:** sin mapeo, los cuatro de siempre. La carga a mano es la salida de
emergencia y no puede depender de que la configuración esté impecable.

**Qué resuelve:** PYXPAY deja de mostrar un «Número de boleta» vacío en cada venta. Y `Lote`/`Cargo`
de INFONET pasan a ser de primera clase, si la conciliación los necesita.

**Alcance:** desktop. Sin migración.

### Cambio 2 · El tipo lo declara el formato, no se deduce de una foto

**Por qué:** está medido que la deducción produce falsos positivos. El código de autorización de
INFONET es alfanumérico (`D380AD`, `0HNDMK`) y a la vez numérico (`467769`, `038116`). Derivando
desde un ticket numérico, `tipoDe` deduce `NUMERO` y **toda venta con VISA crédito iría a revisión**.

**Qué:** sacar la inferencia (`tipoDe` deja de asignar) y que el `tipo` venga del mapeo, puesto por
quien registra el formato — que es quien conoce el proveedor.

**El lector del filial no cambia**: `descontarPorTipo` ya ignora el `tipo` nulo, así que quitar la
inferencia restablece el comportamiento previo sin tocar nada más.

**Alcance:** central (1 línea + los tests de la deducción), desktop (el tipo viaja desde el mapeo).

### Cambio 3 · El mapa acumula variantes en vez de reemplazarlas

**Por qué:** INFONET tiene **dos variantes estructurales**. El ticket de QR tiene dos renglones
menos, así que el monto está más arriba. **Un mapa derivado de un ticket de tarjeta no sirve para
uno de QR**, y las dos operaciones pasan por la misma terminal, el mismo día, en la misma caja.

**Qué:** `guardarDerivadas` calcula la **unión por campo** en vez de reemplazar. Una segunda foto
ensancha la caja; no la pisa.

**Por qué unión y no una lista de variantes:** el filtro se aplica **después de detectar**, así que
probar N variantes en secuencia serían N pasadas completas —detección incluida, que es la etapa que
no se ahorra—. La unión cuesta una sola pasada y reconoce unas cajas más. Y no hace falta «elegir la
variante»: el regex ya hace eso sobre el texto.

**Cuidados:** mostrar cuánto creció cada caja antes de confirmar (el diff ya existe), avisar si la
unión pasa de cierto porcentaje del cupón, y un botón de **derivar desde cero**. Lo `MANUAL` sigue
ganando.

**Alcance:** central (`guardarDerivadas`), desktop (el diff).

---

## 3 · Qué entra a alpha

| Cambio | ¿Bloquea? | Motivo |
|---|---|---|
| **2 · tipo del mapeo** | **sí** | Sin esto, con un formato INFONET derivado de un ticket numérico, **cada venta con VISA crédito va a revisión**. Es activo, no latente. Cuesta una línea |
| **3 · unión de variantes** | **sí, para INFONET** | Sin esto uno de los dos caminos —tarjeta o QR— no extrae campos |
| **1 · diálogo por mapeo** | **no** | PYXPAY muestra un campo de más. Molesto, no roto |

**Recomendación:** 2 y 3 entran al PR actual; 1 va en su propio PR.

Con 1 pendiente, el módulo **soporta INFONET completo y PYXPAY con un campo de ruido**. Es un piso
honesto para alpha, siempre que quede escrito que es eso.

---

## 4 · Lo que NO se cambia, y por qué

**No se quitan columnas.** `codigo_autorizacion` sostiene el índice del chequeo de duplicados, y
`monto_escaneado` es el único cruce objetivo que tiene la pantalla. Quitarlas costaría una estrategia
de dos versiones sobre 24 filiales, a cambio de nada.

**No se renombra `codigo_autorizacion`** aunque el nombre sea INFONET-céntrico. El renombre es
prohibido sin estrategia de dos versiones, y lo que resuelve el problema es la **etiqueta** del
mapeo: PYXPAY mapea su `COD TRANS.` a esa columna y el cajero lee «Cód. transacción».

**No se toca la nitidez.** Sigue sin lector, y ya está documentado por qué conviene medirla contra
tickets reales antes de darle uso.

**No se agrega detección de cupones múltiples.** Confirmado con Gabriel que en operación normal no
pasa; la foto con tres cupones fue un accidente de la recolección.
