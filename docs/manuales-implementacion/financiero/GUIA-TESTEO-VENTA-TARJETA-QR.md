# Guía de testeo manual — venta con tarjeta por QR

Compañera de [VENTA-TARJETA-QR-CUPON.md](VENTA-TARJETA-QR-CUPON.md), que explica el **porqué** de
cada regla. Esto es el **cómo probarlo**.

Escrita contra el entorno **alpha** el 2026-09-07, con datos verificados en la base. Los cupones de
abajo no son inventados: cumplen el patrón sembrado por `V217.5` y fueron construidos con la escala
real del parser.

---

## 0 · Entorno y datos

| | |
|---|---|
| Central alpha | `100.64.0.2:8083` — `4.7.0-alpha.72` |
| Filial alpha | `100.64.0.2:8080` — `5.0.0-alpha.16`, sucursal **2** |
| PDV | **3** ("Caja 1", sucursal 2) |
| Timbrado | `18287247`, **no electrónico**, vence 2026-09-30, rango `002` en 5150/10000 |
| Rol necesario | **42 · `VENTA TARJETA COMPLETAR`** — la `V218.5` lo crea, **no lo asigna** |

> Usar la IP de **tailnet** (`100.64.0.2`), no la de ZeroTier. Y sin espacios: un espacio pegado al
> copiar rompe el WebSocket con un `DOMException` que no menciona la configuración.

### Terminales POS

| id | descripción | código | moneda | para qué sirve en esta guía |
|---|---|---|---|---|
| 1 | BANCARD | `B1` | Gs. (0 dec) | camino feliz en guaraníes |
| 2 | PYXPAY | `PYX1` | R$ (2 dec) | camino feliz en reales, y los decimales |
| 3 | BANCARD | `B2` | Gs. | segunda terminal: cupón de terminal ajena |
| 6 | POS SIN MONEDA | `SM1` | **NULL** | la moneda sale del registro, no de la terminal |

### Formato del QR — `ValidaPix FRCP1` (comodín: sirve para cualquier terminal)

```
FRCP1*{auth}*{bol}*{cur}*{amt}*{ref}*{ts}
```

| campo | regla |
|---|---|
| `auth` | `[A-Z0-9]{0,20}` — código de autorización |
| `bol` | `[A-Z0-9]{0,20}` — **puede venir vacío** (queda `**`) |
| `cur` | `PYG` \| `BRL` \| `USD` |
| `amt` | entero en la **menor unidad**: `monto = amt / 10^decimales` |
| `ref` | `[A-Z0-9]{0,40}` — EndToEndId, **case-sensitive** |
| `ts` | `yyyyMMddHHmm`, **12 dígitos**, hora local de Asunción |

**La escala es la trampa principal:** `9455` en reales son **94,55**; en guaraníes son **9.455**.

### El cupón se arma con TU total, no al revés

No fuerces la venta a un monto: armá el carrito con lo que tengas a mano, **anotá el total** y
generá el cupón para ese número. Forzar un total con descuentos mete una variable que no estás
probando.

```bash
python3 cupon.py 137500          # guaraníes
python3 cupon.py 94.55 BRL       # reales (punto decimal, sin separador de miles)
python3 cupon.py 137500 PYG -4320  # mismo monto, 3 días atrás
```

Cada llamada genera autorización, boleta y referencia nuevas: no vas a chocar con un falso
"cupón repetido". Para los casos que necesitan un cupón **igual** (5.2) o **distinto** (6.1), la
guía lo aclara en el paso.

---

## 1 · Antes de empezar

| # | Paso | Esperado |
|---|---|---|
| 1.1 | Config del sistema: filial `100.64.0.2:8080`, central `100.64.0.2:8083`, `isLocal` ON, PDV `3` | Sin error de WebSocket en consola |
| 1.2 | Asignar rol **42** al usuario de prueba | Aparece el menú de ventas con tarjeta |
| 1.3 | Abrir caja | El PDV deja operar |

---

## 2 · Camino feliz en guaraníes

Carrito con lo que sea, cobro **íntegro con tarjeta** en la terminal **`B1`**. Anotá el total y
generá el cupón:

```bash
python3 cupon.py <TOTAL>
```

**Guardá esta cadena**: se reusa tal cual en 5.2.

| # | Paso | Esperado |
|---|---|---|
| 2.1 | Venta por cualquier monto, pago con tarjeta, terminal `B1` | Pide el cupón |
| 2.2 | Pasar el cupón | Autorización y boleta las del cupón, monto **igual al total**, sin diferencia |
| 2.3 | Confirmar | Queda **`COMPLETADO`**, no `NO_COMPLETADO` |
| 2.4 | Ver en la lista | El total **sin decimales**, con `Gs.` |

---

## 3 · Camino feliz en reales — y los decimales

Venta cobrada en reales, terminal **`PYX1`**. Generá con `BRL`:

```bash
python3 cupon.py <TOTAL> BRL
```

Para el caso de **boleta vacía** (el split pelado), usá este, que la trae en blanco:

```
FRCP1*CXF1**BRL*9455*E2609071505DY5BCKNPMBQ*202609071505
```
(94,55 R$ — la venta tiene que ser por ese monto)

| # | Paso | Esperado |
|---|---|---|
| 3.1 | Venta en reales, tarjeta, terminal `PYX1` | — |
| 3.2 | Pasar el cupón | El monto con **dos decimales**, no dividido ni multiplicado por 100 |
| 3.3 | Boleta | **Vacía**, y la moneda se leyó bien igual |
| 3.4 | En la lista | El total con **dos decimales** y `R$` |

> 3.3 es el test del split pelado: si el parser colapsa los vacíos, corre las posiciones y lee la
> moneda como número de boleta.

---

## 4 · La moneda vive en el registro, no en la terminal

Terminal **`SM1`**, que **no tiene moneda configurada**.

```bash
python3 cupon.py <TOTAL> BRL
```

| # | Paso | Esperado |
|---|---|---|
| 4.1 | Cobro en R$ con terminal `SM1` | — |
| 4.2 | Pasar el cupón (8.000,00 R$) | Se registra en **R$** |
| 4.3 | En la lista | **`8.000,00 R$`** |

**El código viejo mostraba `8.000 Gs.`** — leía la moneda de la terminal y esta no tiene. Si ves
"Gs." acá, la corrección no está activa.

---

## 5 · Las tres validaciones que BLOQUEAN

### 5.1 · Moneda distinta

Cobro en **guaraníes** (terminal `B1`), cupón generado en **reales por el mismo número**:

```bash
python3 cupon.py <TOTAL> BRL
```

**Esperado: BLOQUEA.** Sin `moneda_id` esto daba **diferencia cero** — 8.000 contra 8.000 — y
quedaba "conciliado" difiriendo ~5900x. Es el caso más peligroso de todos.

### 5.2 · Cupón ya usado

Repetir el cupón de **2.2**, exacto, en otra venta.

**Esperado: BLOQUEA.** Un mismo `qr_crudo` no puede quedar en dos registros.

### 5.3 · Cupón de otra terminal

Cupón tomado en `B1`, pasado en un cobro de la terminal **`B2`**.

**NO APLICABLE HOY.** El cruce se detecta por **proveedor**, no por terminal, y un formato comodín
nunca cuenta como cruce (§4 del manual). El único formato cargado en alpha es comodín, así que este
caso no se puede disparar. Requiere cargar un segundo formato **con proveedor asignado**.

> Las tres tienen que dar un mensaje que **diga por qué**. Un bloqueo mudo se reintenta a ciegas y
> es casi tan malo como no bloquear.

---

## 6 · Los que sólo CONFIRMAN

### 6.1 · Monto distinto

Generá el cupón por **1.000 menos** que el total de la venta:

```bash
python3 cupon.py <TOTAL_MENOS_1000>
```

**Esperado:** avisa la diferencia de 1.000 y **deja continuar** si confirmás.

### 6.2 · Cupón viejo

```bash
python3 cupon.py <TOTAL> PYG -4320   # 3 días atrás
```

**Esperado:** avisa que el cupón es de otro día y deja continuar.

---

## 7 · Elegir a qué cobro pertenece

| # | Paso | Esperado |
|---|---|---|
| 7.1 | Venta con **dos cobros con tarjeta del mismo monto** | — |
| 7.2 | Pasar un cupón | **Pide elegir** a cuál corresponde |

Con dos cobros idénticos no hay dato que permita desempatarlos: adivinar es vincular al azar. Por
eso el usuario elige (`cobroDetalleId`).

---

## 8 · Completar un `NO_COMPLETADO` desde la lista

No necesita PDV ni caja abierta.

| # | Paso | Esperado |
|---|---|---|
| 8.1 | Cerrar una venta con tarjeta **sin** pasar el cupón | Queda **`NO_COMPLETADO`** |
| 8.2 | Ir a la lista, abrir el `NO_COMPLETADO`, pasar el cupón | Pasa a **`COMPLETADO`** |
| 8.3 | Con un usuario **sin** el rol 42 | No debería poder llegar a la pantalla |

> En el filial alpha ya hay tres `NO_COMPLETADO` de pruebas previas (**ids 2, 5 y 6**, por 72.000,
> 20.000 y 4.000 Gs.). Sirven para este paso sin fabricar nuevas.
>
> Los estados reales son **`COMPLETADO`** / **`NO_COMPLETADO`**.

---

## 9 · Modalidades de venta

Repetir el camino de §2 variando la forma de pago:

| # | Modalidad | Mirar |
|---|---|---|
| 9.1 | Todo tarjeta, Gs. | Base |
| 9.2 | Todo tarjeta, R$ | Decimales |
| 9.3 | **Mixto**: parte efectivo, parte tarjeta | Sólo la parte con tarjeta pide cupón |
| 9.4 | **Dos tarjetas** de montos distintos | Un cupón por cobro, sin cruzarse |
| 9.5 | **Con factura legal** (PDV 3) | Factura emitida, numeración avanza |
| 9.6 | Sin factura | La venta cierra igual |
| 9.7 | **Crédito** con entrega en tarjeta | La entrega pide cupón |

---

## 10 · Facturación — el fix que viaja en este PR

**Caso:** PDV **sin timbrado vigente** + facturar.

| # | Paso | Esperado |
|---|---|---|
| 10.1 | Venta con factura en un PDV sin timbrado | Error visible de timbrado |
| 10.2 | Buscar la venta | **La venta EXISTE** |

Antes la excepción marcaba la transacción como rollback-only y **la venta desaparecía con el
cliente ya pagado**. Si en 10.2 la venta no está, el fix no quedó.

---

## 11 · Cierre de caja

| # | Paso | Esperado |
|---|---|---|
| 11.1 | Dejar una venta con tarjeta sin cupón y cerrar caja | Avisa cuántas quedan sin registrar |
| 11.2 | El mensaje | Manda a registrarlas escaneando desde el PDV |

---

## Planilla

| Caso | Resultado | Nota |
|---|---|---|
| 2 · feliz Gs. | | |
| 3 · feliz R$ + decimales | | |
| 4 · moneda del registro | | |
| 5.1 · moneda distinta BLOQUEA | | |
| 5.2 · cupón repetido BLOQUEA | | |
| 5.3 · terminal ajena | | |
| 6.1 · monto distinto CONFIRMA | | |
| 6.2 · cupón viejo CONFIRMA | | |
| 7 · elegir cobro | | |
| 8 · completar desde la lista | | |
| 8.3 · sin rol 42 | | |
| 9.3 · mixto | | |
| 9.5 · con factura | | |
| 10 · venta sobrevive sin timbrado | | |
| 11 · aviso en cierre de caja | | |
