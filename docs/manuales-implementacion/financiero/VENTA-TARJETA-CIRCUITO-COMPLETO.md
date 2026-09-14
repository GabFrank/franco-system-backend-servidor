# Venta con tarjeta — el circuito completo, de la instalación a la venta

**Qué es este documento.** El recorrido entero del módulo, de punta a punta: qué se configura
primero, qué depende de qué, quién hace cada cosa y en qué servidor. No es el plan de construcción
—ese cuenta *cómo se llegó* y muere al cerrar la entrega— sino la descripción de **lo que queda
funcionando**, verificada contra el código el 2026-09-14.

Existe porque la pregunta *«¿esto está completo?»* no se puede contestar leyendo seis etapas de
implementación. Se contesta siguiendo el circuito desde cero.

---

## 0 · La regla que ordena todo: el ABM vive en central

**Todo el alta y la configuración se hace en el servidor central. La filial no administra nada:
solo recibe por replicación y cobra.**

| | Central | Filial |
|---|---|---|
| Configuración general del módulo | ✅ | — |
| Proveedores de servicio | ✅ | — |
| Formatos de terminal | ✅ | — |
| Derivación del mapa | ✅ | — |
| Alta y configuración de terminales | ✅ | — |
| **Vender con tarjeta** | — | ✅ |
| **Leer el cupón por foto** | ✅ (para derivar) | ✅ (para vender) |

Dos motivos, y el segundo es el que decide:

1. Es un ABM de catálogo: un formato describe un **modelo de aparato**, no una sucursal.
2. **Central tiene HTTPS y la filial no.** Sin contexto seguro no hay `getUserMedia`, así que en la
   filial la captura va por `<input type="file" capture="environment">` —el teléfono abre su app de
   cámara— mientras que en central se puede usar la cámara dentro de la página. Derivar un mapa
   necesita encuadre en vivo; cobrar, no.

---

## 1 · Encender el módulo

**Dónde:** Financiero → **Venta con tarjeta** → **Terminales POS** → botón *Configurar ventas con
tarjeta*. (El mismo diálogo se abre desde el listado de ventas con tarjeta.)

**El módulo viene apagado de fábrica**, y es a propósito: mientras `habilitado = false`, las 24
filiales **ni siquiera cargan el motor de OCR**. Medido el 2026-09-14: el filial arranca en **22,1 s**
apagado contra **25,6 s** encendido. Al prenderlo **no hace falta reiniciar nada**: la configuración
baja por replicación y el motor se carga en la primera foto que llegue.

Lo que se configura acá, y lo que significa cada cosa:

| Campo | Para qué |
|---|---|
| `habilitado` | La perilla maestra |
| `registroObligatorio` | `LIBRE` · `AVISA_AL_CERRAR` · `BLOQUEA_EL_CIERRE` — qué pasa al cerrar la caja con ventas en PENDIENTE |
| `toleranciaDiferenciaMontoPct` | Debajo de ese %, la diferencia entre cupón y cobro no pide confirmación. `0` = confirmar siempre. Es porcentaje y no monto porque los montos no llevan unidad |
| `minutosValidezCaptura` | Vida del token del QR de captura |
| `segundosDialogoRegistro` | Countdown del diálogo de registro |
| `horasVentanaDuplicado` | Cuánto atrás mira el chequeo de cupón duplicado por código de autorización |
| `diasRetencionImagenes` | Retención de las fotos. **Sin lector todavía** — ver §9 |
| `mbLibresMinimos` | Alerta de espacio. **Sin lector todavía** — ver §9 |

---

## 2 · Dar de alta el proveedor de servicio

**Dónde:** Financiero → Venta con tarjeta → Terminales POS → botón **Proveedores de servicios**.

No está en el menú lateral: se llega por el dashboard de terminales. Es deliberado — se lo necesita
una vez, al principio, y colgarlo del menú principal agregaba ruido a una pantalla que ya tiene
mucho.

Es quien procesa la transacción: Bancard, Dinelco, upay, Pyxpay. Sirve para dos cosas distintas:

- **Agrupa formatos.** Un proveedor suele imprimir el mismo cupón en todos sus modelos.
- **Desempata series repetidas.** Dos proveedores distintos pueden usar la misma serie de fábrica.
  Por eso el índice único es sobre `(proveedor_servicio_id, serie)`, no sobre `serie` sola.

> ⚠️ **Y por eso hay un segundo índice.** Postgres no considera iguales dos `NULL`, así que un único
> índice parcial sobre `(proveedor, serie)` **no protege nada cuando el proveedor es NULL** — y las
> terminales reales hoy lo tienen NULL. De ahí `uq_terminal_pos_serie_comodin`, que exige serie
> única entre las terminales sin proveedor. Cargar el proveedor es lo que habilita que dos aparatos
> compartan serie; no cargarlo obliga a que la serie sea única.

---

## 3 · Registrar el formato del modelo de aparato

**Dónde:** Financiero → Venta con tarjeta → **Formatos de terminal POS**. Solo `ADMIN`.

Un formato describe **un modelo de máquina**, no un aparato. Se registra una vez por modelo y lo
comparten todas las terminales de ese modelo en toda la red.

| Campo | Qué es |
|---|---|
| `nombre` | Cómo lo reconoce un humano |
| `proveedorServicio` | El del §2 |
| `tipo` | **`MAQUINA`** · **`WEB`** · **`API`**. Decide qué camino se le ofrece al cajero y, sobre todo, **cuál se le cierra** |
| `patron` | Regex con **grupos nombrados**, anclada con `^` y `$`. Obligatoria para `MAQUINA` y `WEB`; solo `API` puede no tenerla |
| `mapeo` | JSON `campo destino -> {de, obligatorio, …}`. Los `obligatorio` **también definen el formulario de carga a mano** |
| `ejemplo` | Un cupón de muestra en texto, para probar el patrón sin el aparato |
| `activo` | `false` = no se puede asignar a terminales nuevas. **Las que ya lo tienen siguen operando** |

El anclaje con `^…$` no es cosmético: es lo que permite probar un escaneo **primero como cupón y
después como código de terminal**. Un código de terminal nunca matchea un patrón anclado, pero al
revés sí, porque la búsqueda por código usa `LIKE`.

---

## 4 · Derivar el mapa desde una foto

**Dónde:** en la pantalla del formato, diálogo **Derivar mapa**.

Esto es lo que convierte un formato en algo que el OCR puede leer rápido. Sin mapa el cupón igual se
lee, pero se reconoce **el cupón entero**; con mapa se reconocen solo las zonas que importan. Medido:
de 26 cajas a 6 baja el reconocimiento de **3.841 ms a ~900 ms**.

El recorrido:

1. En central se abre *Derivar mapa* y aparece un **QR con un token**.
2. El teléfono lo escanea y cae en `/public/captura-muestra/{token}` — **HTTPS, cámara en la
   página**. El token es la única credencial y vive `minutosValidezCaptura`.
3. Se saca la foto de un cupón real de ese modelo.
4. El servidor la lee, propone las regiones y **las muestra para revisar antes de guardar**.

Cada región propuesta queda así:

| Campo | Qué significa |
|---|---|
| `campo` | La clave del mapeo, en camelCase: `codigoAutorizacion`, `numeroBoleta`, `monto`, `terminal`… |
| `etiqueta` | La leyenda impresa que ancla la región. `null` = se resuelve solo por geometría, **y esa es la frágil** |
| `posicion` | `DERECHA` · `ABAJO` · `DENTRO` — dónde está el valor respecto de su etiqueta |
| `tipo` | `TEXTO` · `NUMERO` · `FECHA`. Declarar `NUMERO` **rechaza gratis** un `0i64` del OCR |
| `x1 y1 x2 y2` | Pista geométrica normalizada 0..1. **Las cuatro o ninguna** |
| `origen` | `DERIVADA` · `MANUAL` |

> **`MANUAL` gana siempre.** Una región corregida a mano no la pisa una corrida de derivación
> posterior. Es la regla que hace seguro volver a derivar cuando cambia el papel: lo que alguien
> ajustó mirando un cupón real sobrevive.

**La foto de muestra se descarta después de leerla.** No se guarda: la captura vive en memoria, con
TTL de 20 minutos y tope de 50 capturas vivas.

---

## 5 · Dar de alta la terminal

**Dónde:** Financiero → Venta con tarjeta → **Terminales POS** → *Nueva terminal*. `ADMIN` o
`VENTA_TARJETA_COMPLETAR`.

Acá se registra **el aparato físico**, uno por caja.

| Campo | Qué es |
|---|---|
| `descripcion` | Para humanos |
| `codigo` | **Etiqueta interna que el cajero escanea** para elegir la terminal. **No** es el identificador de la máquina |
| `serie` | **El identificador propio del aparato**, el que viene de fábrica y el que **el cupón imprime**. Con esto cargado, un cupón dice solo de qué máquina salió |
| `sucursal` | Dónde está físicamente. `null` en las terminales viejas: no se puede adivinar, se carga a mano |
| `moneda` | — |
| `proveedorServicio` | §2 |
| `formatoTerminalPos` | §3. **`null` = sin configurar, y el desktop bloquea la venta con tarjeta en esa terminal** |

**La distinción `codigo` / `serie` es la que hace funcionar todo lo demás.** El `codigo` lo elige la
empresa y lo escanea el cajero; la `serie` la trae la máquina y la imprime el cupón. Por eso, cuando
el cajero escanea el QR de un cupón, el sistema puede resolver **solo** de qué aparato salió, sin
pedirle que además escanee la terminal. Antes de esto, el diálogo cobraba ese peaje sin dar nada.

### Configuración por terminal

Botón **Configurar** en la fila. Dos cosas:

| Campo | Qué hace |
|---|---|
| `cargaManualPermitida` | Si en esta terminal se puede tipear el cupón a mano. `null` = hereda la general |
| `camposObligatorios` | Qué no se puede dejar vacío acá. `null` = hereda los `obligatorio` del mapeo del formato |

`camposObligatoriosEfectivos` es lo que se le exige de verdad al cajero: la lista del aparato si la
hay, y si no, la del formato. **Con eso se arma el formulario de carga a mano.**

> 🔒 **Un candado que conviene conocer.** No se puede apagar `cargaManualPermitida` **ni** quitar el
> formato si eso deja a la caja sin ningún camino para cobrar con tarjeta. El sistema lo rechaza con
> el motivo escrito. Es la protección contra apagar dos perillas en días distintos y descubrirlo un
> sábado a la tarde.

---

## 6 · Que llegue a la filial

Nada de lo anterior sirve hasta que baja. Las direcciones, **verificadas contra una base migrada**
el 2026-09-14:

| Tabla | Dirección | Quién escribe |
|---|---|---|
| `configuracion_venta_tarjeta` | `MAIN_TO_ALL` | central → filiales |
| `terminal_pos` | `MAIN_TO_ALL` | central → filiales |
| `formato_terminal_pos` | `MAIN_TO_ALL` | central → filiales |
| `formato_terminal_pos_region` | `MAIN_TO_ALL` | central → filiales |
| **`venta_tarjeta`** | **`BRANCH_TO_MAIN`** | **filial → central** |

**Las migraciones no ejecutan `ALTER PUBLICATION`, y está bien.** Solo registran la tabla en
`configuraciones.replication_table`; `ReplicationPublicationSyncScheduler` lee esa tabla y hace el
`ALTER PUBLICATION` solo, en central y en cada filial alcanzable por JDBC remoto.

⏱️ **Pero eso no es instantáneo:** el scheduler corre **a los 120 s del arranque y después cada
hora**, y solo alcanza a las filiales **levantadas en ese momento**. Una sucursal apagada se
incorpora en la siguiente vuelta.

---

## 7 · La venta, desde la caja

Tres caminos, en orden de preferencia:

**1 · El QR del cupón.** El cajero escanea y listo. El escáner acepta las dos cosas —el código de la
terminal o el QR del cupón— y **prueba primero como cupón**, que es el orden seguro.

**2 · La foto.** Si no hay QR, o salió borroso, o el papel está arrugado: aparece un QR en pantalla,
el teléfono lo escanea, cae en `/public/captura/{token}` del **filial** —HTTP, así que el teléfono
abre su app de cámara— saca la foto, y los campos se completan solos.

Lo leído vuelve con **semáforo de confianza por campo**, umbral **0,9**:

- **Verde** — el OCR está seguro.
- **Amarillo/rojo** — hay que mirarlo. Corregir cuenta como confirmar.

El semáforo existe porque el OCR se equivoca de maneras plausibles: en una imagen sintética limpia
leyó `COMERCIO` como `COMERCI0`. Un campo que se ve bien y está mal es peor que uno que se ve mal.

**3 · La carga a mano.** El respaldo. El formulario se arma con `camposObligatoriosEfectivos`, y
está disponible **en las dos puertas**: al registrar la venta y al completar una pendiente.

Si el motor de OCR no está disponible —arquitectura sin binario nativo, glibc vieja— **el filial
arranca igual** y la foto cae directo a la carga a mano. El lector de cupones es opcional; la
sucursal vende.

---

## 8 · Poner esto en un servidor

**El orden lo fija la dirección de replicación, no la jerarquía.** Como el mismo par de JARs lleva
las dos direcciones, los dos órdenes se contradicen. No es un empate: las violaciones no cuestan lo
mismo.

- `BRANCH_TO_MAIN` con el filial adelante la dispara **cualquier venta con tarjeta**. Tráfico
  normal, incontrolable.
- `MAIN_TO_ALL` con central adelante la dispara **una escritura administrativa**. Posponible.

Por eso:

1. **Central primero.**
2. **Ninguna escritura sobre `terminal_pos` ni `configuracion_venta_tarjeta`** hasta que la flota del
   canal actualice (≤15 min en el filial).
3. Esperar a las filiales.
4. **Recién ahí**, el SQL de asignación de formato.

> ⚠️ **El SQL de configuración va último.** El plan original lo tenía como paso 5, **antes** de que
> las filiales actualizaran. Ese `UPDATE` sobre una tabla `MAIN_TO_ALL` es exactamente la escritura
> que corta la réplica de las 24 sucursales.

Y el módulo **queda apagado** hasta que las dos mitades estén arriba. Encenderlo es el último paso, y
no requiere reiniciar nada.

---

## 9 · Lo que NO está, y hay que saberlo antes de decir que sí

| | Estado |
|---|---|
| **Venta completa en el PDV con productos** | **Sin ejercitar.** Se probó la captura y la extracción, no el carrito hasta el cobro. Son las pruebas 5 a 8 del guion manual |
| **Colisión de datos en los índices únicos de `terminal_pos`** | **Sin probar.** La base del dry-run tenía cero terminales. Si en bodega o farmacia hay dos con el mismo `codigo` o la misma `serie`, **la migración falla allá y no acá** |
| `diasRetencionImagenes` y `mbLibresMinimos` | **Configuración sin lector.** Se guardan y nadie los lee: la purga llega en la etapa 6 |
| **POS con recargo** | Fuera de alcance. Los cupones con recargo siguen pidiendo confirmación de monto |
| `capturaCupon(token)` en el filial | No verifica que el token sea del cajero que pregunta. Riesgo **bajo**: `SecureRandom`, y ya no se difunde |
| **Promoción a farmacia o bodega** | Fuera de esta entrega. Esto llega hasta `develop` / alpha |

---

## 10 · Los cinco lugares donde mirar si algo no anda

1. **El cajero no ve la opción de tarjeta** → `configuracion_venta_tarjeta.habilitado`, y que haya
   bajado por replicación a esa filial.
2. **Una terminal no se puede elegir** → le falta `formatoTerminalPos`. Sin formato, el desktop
   bloquea.
3. **La foto no completa nada** → el formato no tiene mapa (§4), o su `patron` no matchea. El campo
   `ejemplo` del formato sirve para probarlo sin el aparato.
4. **El cupón no resuelve la terminal** → falta la `serie` en el aparato, o está cargada distinta de
   como la imprime el cupón.
5. **Se configuró en central y la filial no se entera** → el scheduler de publicaciones corre cada
   hora y solo alcanza filiales levantadas (§6).
