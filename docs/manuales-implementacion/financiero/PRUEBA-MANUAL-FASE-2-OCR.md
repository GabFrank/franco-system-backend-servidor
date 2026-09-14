# Prueba manual — fase 2: el cupón se lee solo

**Para hacer juntos, lunes a la mañana.** Una prueba por vez: Gabriel ejecuta, yo confirmo contra
la base antes de pasar a la siguiente. Si algo falla, se anota y se sigue — no se corrige en el
momento, salvo que bloquee lo que viene después.

**Duración estimada:** 50-70 minutos las 14 pruebas. Los bloques A y B son los imprescindibles;
del C en adelante se puede cortar si no da el tiempo.

---

## Antes de empezar

| Qué | Cómo |
|---|---|
| Central corriendo | `cd frc-comercial/central && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → puerto **8081**, base `bodega_fact_test_2` (5551) |
| Filial corriendo | `cd frc-comercial/filial && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,user-dev -Dspring-boot.run.arguments=--sifen.scheduler.enabled=false` → puerto **8082**, base `general_fact_test_2` (5552) |
| Desktop | `cd frc-comercial/desktop && npx ng serve -c web --port 4201` |
| Un teléfono | En la misma wifi que la máquina. Para las pruebas 4 y 12 |
| Un cupón de papel | De cualquier POS. Si no hay, sirve el sintético: `java CuponDemo.java cupon.jpg` |

> ⚠️ **Los dos perfiles del filial, y por qué.** Con `dev` solo, el filial apunta a
> `localhost:5551/general`, **una base que no existe en este equipo**: no arranca. `user-dev` es el
> archivo personal, fuera de git, y es el que lo manda a `5552/general_fact_test_2`. Van los dos.
>
> ⚠️ **Y el scheduler de SIFEN se apaga a mano.** El perfil `dev` lo enciende con
> `sifen.ambiente=PROD` y `user-dev` le da un certificado que sí existe. Hoy la base de prueba no
> tiene nada pendiente de enviar —0 DEs en PENDIENTE, 0 lotes en PENDIENTE_ENVIO, verificado el
> 2026-09-14— pero **en esta prueba vamos a registrar ventas**, y una venta genera un documento que
> ese scheduler mandaría a la SIFEN **de producción** a los 30 segundos.

> ⚠️ **El módulo arranca apagado.** `financiero.configuracion_venta_tarjeta.habilitado` viene en
> `false`, y eso es a propósito. La prueba 0 lo enciende.

---

## Bloque A · Que el sistema sepa qué aparato tiene enfrente

### Prueba 0 — el módulo apagado no carga el motor

**Por qué importa:** son 24 filiales cargando ~2,5 s y memoria por una función que nadie usa hasta
que la empresa termine de configurar.

1. Con `habilitado = false`, arrancar el filial.
2. Mirar el log.

**Esperado:** `OCR de cupon: el modulo de venta con tarjeta esta deshabilitado, el motor se va a
cargar la primera vez que haga falta`. **No** debe aparecer "OCR de cupon listo en N ms".

Después: `update financiero.configuracion_venta_tarjeta set habilitado = true;` y **no reiniciar**.
El motor se carga solo en la primera foto (prueba 4).

---

### Prueba 1 — registrar el formato de un modelo de aparato

Financiero → Venta con tarjeta → **Formatos de terminal POS** → Nuevo.

| Campo | Valor |
|---|---|
| Nombre del modelo | `BANCARD FIRMWARE V5.5` |
| Cómo se lee el ticket | **Maquinita (ticket sin QR)** |
| Patrón | `^[\s\S]*TERMINAL:\s*(?<terminal>[A-Z0-9]+)[\s\S]*AUT:\s*(?<auth>[0-9]+)[\s\S]*BOLETA:\s*(?<boleta>[0-9]+)[\s\S]*MONTO:\s*(?<monto>[0-9.]+)[\s\S]*$` |
| Mapeo | `{"terminal":{"de":"terminal"},"codigoAutorizacion":{"de":"auth","obligatorio":true},"numeroBoleta":{"de":"boleta"},"monto":{"de":"monto","obligatorio":true}}` |
| Cadena de ejemplo | `TERMINAL: JF798SJJ AUT: 883921 BOLETA: 00045 MONTO: 150.000` |

**Esperado:** la **vista previa** de abajo se llena sola mientras tipeás, y guarda.

**Probá también que RECHACE**, una por una:
- Un patrón sin `^` al principio → *"El patrón debe estar anclado"*.
- Un patrón que no matchee el ejemplo → *"El patrón no reconoce la cadena de ejemplo"*.
- Un mapeo que use un grupo que el patrón no declara → dice **cuál** grupo.

> **Hallazgo conocido, no es un bug de esta prueba:** en la vista previa el monto `150.000` se
> muestra como `150`. Esa vista usa el parser de QR, donde los importes vienen sin separador de
> miles. La extracción real del cupón corre en el filial y **no** pasa por ahí — se verifica en la
> prueba 5.

---

### Prueba 2 — la terminal: dónde está y cuál es

Financiero → Venta con tarjeta → **Terminales POS** → Nueva terminal.

| Campo | Valor |
|---|---|
| Descripción | `CAJA 1 PRUEBA` |
| Código | `PRUEBA-01` |
| **Serie del aparato** | `jf798sjj` ← **en minúsculas a propósito** |
| **Sucursal** | la que corresponda |
| Formato del aparato | `BANCARD FIRMWARE V5.5` |

**Esperado:**
- La serie se guarda **en mayúsculas**: `JF798SJJ`.
- El hint de la serie —*"La que viene de fábrica y el cupón imprime. No es el código."*— **no se
  superpone** con el campo de abajo.
- En la lista aparecen las columnas **Serie**, **Sucursal** y **Formato**.
- Las terminales viejas dicen **"Sin asignar"** en ámbar y **"No vende"** en rojo.

**Probá que rechace:** crear otra terminal con la misma serie → mensaje que nombra **cuál** terminal
ya la tiene. Lo mismo con el código repetido.

---

### Prueba 3 — el filtro por sucursal

En la lista, elegir una sucursal en el filtro.

**Esperado:** quedan sólo las de esa sucursal, y **el total del paginador también baja**. Si el
total no cambia, el filtro está corriendo en memoria y es un bug.

---

## Bloque B · Que el cupón se lea solo

### Prueba 4 — la foto, y el mapa que se deriva solo

Volver a **Formatos** → en `BANCARD FIRMWARE V5.5`, el ícono de **grilla** → *Mapa del cupón*.

1. **Subir una foto** del cupón (o sacarla con el teléfono escaneando el QR).
2. Esperar a que aparezca *"Lo que se leyó"* con el texto y los milisegundos.
3. **Proponer el mapa**.

**Esperado:** una fila por campo, con la etiqueta que lo ancla:

| Campo | Etiqueta | Posición |
|---|---|---|
| terminal | `TERMINAL:` | dentro |
| codigoAutorizacion | `AUT:` | dentro |
| numeroBoleta | `BOLETA:` | dentro |
| monto | `MONTO:` | dentro |

⚠️ **Los campos tienen que ser los canónicos** (`codigoAutorizacion`), **no** los nombres de los
grupos del patrón (`auth`). Si aparece `auth`, es la regresión que se corrigió el 2026-09-12.

4. **Guardar el mapa**.

**Después, volver a entrar y proponer de nuevo:** ahora tiene que aparecer el **diff** y pedir
confirmación, con el aviso de que baja a todas las sucursales. Cancelar.

---

### Prueba 5 — el OCR deja de ser una lupa

Esta es **la prueba que justifica toda la entrega**.

Desde una caja abierta, en el cobro con tarjeta, elegir la terminal `CAJA 1 PRUEBA` y sacar la foto
del cupón con el teléfono.

**Esperado:** ya **no** aparece sólo el texto crudo para transcribir. Se abre
**"Confirmá los datos del cupón"** con los campos ya cargados.

Confirmar en la base:

```sql
select campos from financiero.captura_cupon order by id desc limit 1;
```

Tiene que traer `codigoAutorizacion`, `numeroBoleta`, `monto`, `terminal` **y** un objeto
`confianzas` con un número por campo.

---

### Prueba 6 — el semáforo por campo

En esa misma pantalla de confirmación:

**Esperado:**
- Los campos leídos con claridad: borde **verde** y *"Leído con claridad"*.
- Los dudosos: borde **ámbar**, el motivo, y un tilde **"Coincide con el ticket"**.
- **El botón Confirmar está deshabilitado** mientras quede un dudoso sin resolver, y abajo dice
  cuántos faltan.
- **Corregir un campo cuenta como confirmarlo** — no hace falta además tildarlo.

> Si el cupón sale muy limpio y no hay ningún dudoso, forzarlo: sacar la foto movida o con poca luz.
> Un cupón térmico gastado sirve mejor que uno nuevo.

---

### Prueba 7 — la foto queda atada a la venta

Después de confirmar:

```sql
select id, origen, imagen_url from financiero.venta_tarjeta order by id desc limit 1;
```

**Esperado:** `origen = 'OCR'` y `imagen_url` **no nulo**. Esa columna es lo que impide que la purga
borre la evidencia de un cobro.

---

### Prueba 8 — cargar a mano después de una foto fallida

Sacar una foto **deliberadamente mala** (tapada, movida). Cuando falle, usar **"Cargar el cupón a
mano"** y completar.

**Esperado:** `origen = 'MANUAL'` **y `imagen_url` igual no nulo** — la foto se conserva aunque el
motor no la haya podido leer. El cupón sigue siendo la evidencia.

---

## Bloque C · Los caminos que se cierran

### Prueba 9 — el tipo del formato cierra el camino que no corresponde

Con una terminal de formato **MAQUINA**: en el cobro, **no** se ofrece el lector, sí la cámara.
Con una **WEB**: al revés.

**En los dos casos tiene que estar disponible "Cargar el cupón a mano".**

---

### Prueba 10 — el interruptor no puede apagar el último camino

En la lista de terminales, menú **⋮ → Configurar** sobre una terminal **sin formato**.

**Esperado:** la opción *"No permitida en esta terminal"* aparece **deshabilitada**, con el motivo
al lado: sin formato la venta con tarjeta ya está bloqueada y la carga a mano es lo único que queda.

**Y la otra dirección** — la que se escapó en la primera versión:

1. En una terminal **con** formato, apagar la carga a mano. Deja.
2. Sobre esa misma, **⋮ → Quitar formato**.

**Esperado:** lo **rechaza**, diciendo que esa caja se quedaría sin ninguna forma de cobrar, y que
hay que volver a permitir la carga a mano primero. Verificar en la base que el formato **sigue
asignado**.

---

### Prueba 11 — los campos obligatorios sólo pueden apretar

⋮ → Configurar → **Campos obligatorios**.

**Esperado:** los que el formato ya declara obligatorios aparecen **tildados y bloqueados**, con la
leyenda *"lo exige el formato"*. Se pueden agregar otros; no se pueden sacar ésos.

---

## Bloque D · El camino rápido

### Prueba 12 — un solo input: código o cupón

En el cobro con tarjeta, en el primer diálogo —el que pide la terminal— **escanear directamente el
QR del cupón** en vez del código del aparato.

**Esperado:** lo reconoce como cupón. Si el cupón trae el identificador del aparato y coincide con
una `serie` cargada, **resuelve la terminal solo** y saltea el paso.

Si no la puede resolver, avisa en ámbar que **el cupón ya se leyó** y pide el código del aparato —
para que no lo vuelvas a escanear creyendo que no entró.

---

### Prueba 13 — la diferencia de monto se confirma, no se avisa

Escanear un cupón cuyo monto **no coincida** con lo cobrado.

**Esperado:** un **diálogo** que dice los dos importes y pregunta si es el cupón correcto, con
"Escanear otro" como alternativa. No un snackbar que se va solo.

---

### Prueba 14 — el duplicado

Intentar registrar **dos veces el mismo cupón** en la misma caja, dentro de la ventana de horas
configurada.

**Esperado:** lo rechaza nombrando la venta que ya lo usó.

---

## Lo que NO entra en esta prueba

| Qué | Por qué |
|---|---|
| La purga de imágenes | El scheduler viene **apagado** y arranca en simulación. Se prueba cuando se prenda, mirando el log de lo que borraría |
| Replicación real a 24 filiales | Local sólo hay dos clusters. El dry-run contra copia de alpha es aparte (§5.8) |
| El asistente de IA | Etapa 6, no entra en esta entrega |
| Terminales tipo `API` | El driver no existe todavía |

---

## Nota de despliegue que salió de la prueba automatizada

Las tablas **nuevas** (`formato_terminal_pos`, `formato_terminal_pos_region`) no bajan solas a un
filial que ya estaba corriendo: hay que agregarlas a la publicación y refrescar la suscripción. El
scheduler de replicación lo hace, pero **por hora**. Si al probar en un filial el formato no
aparece, es eso y no un bug — verificar con:

```sql
select * from pg_publication_tables where tablename like 'formato_terminal_pos%';
```

---

## Hallazgos de la corrida del 2026-09-14

Se anotan y se sigue, como dice el encabezado. Ninguno bloquea las pruebas siguientes.

### H1 · El prefijo crudo de GraphQL llega al usuario — **sistémico, no de esta pantalla**

**Qué se vio.** Al guardar un formato con el patrón sin anclar, el snackbar mostró:

```
Exception while fetching data (/data) : El patron debe estar anclado: empezar con ^ y terminar con $.
```

**Por qué.** No es que falte manejo de errores: `mensaje-error.ts` extrae bien `e.message`, y el
backend manda el motivo exacto. El prefijo **ya viene dentro del mensaje**.

`GraphqlExceptionHandler.getNested()` existe justamente para desenvolver esto, pero solo actúa
cuando la excepción anidada **implementa `GraphQLError`**:

```java
if (exceptionError.getException() instanceof GraphQLError) {
    return (GraphQLError) exceptionError.getException();
}
```

`graphql.GraphQLException` extiende `RuntimeException`, **no** `GraphQLError`. El `instanceof` da
false, no desenvuelve nada, y pasa el `ExceptionWhileDataFetching` entero — cuyo `getMessage()` es
`"Exception while fetching data (/ruta) : " + mensaje`.

**Alcance.** No es de venta con tarjeta: **todo `throw new GraphQLException(...)` del sistema**
llega así. Son cientos de mensajes de negocio —"ese cupón ya fue registrado", "no hay saldo
suficiente"— con basura técnica adelante.

**Fix propuesto:** en `getNested`, contemplar también el caso `GraphQLException` y devolver un
error con el mensaje pelado. Una línea en un solo archivo, y mejora todos los módulos a la vez.
`[central:src/main/java/com/franco/dev/graphql/exceptions/GraphqlExceptionHandler.java:34]`

### H2 · El listado de formatos no sigue el patrón de listados del repo

**Qué se vio.** Sin filtros, sin paginación, y las acciones como **iconos sueltos** en vez de un
`mat-menu`.

**No es que falte el patrón: existe y se ignoró.** `.cursor/rules/create-edit-list-entity.md` manda
`MatPaginator`, controles de filtro y columna `acciones`; y `shared/components/generic-list` es el
componente que ya lo resuelve —lo usa, por ejemplo, `list-caja-virtual`.

Medido sobre `formato-terminal-pos.component.html`: **0** `app-generic-list`, **0** `mat-paginator`,
**0** `mat-menu`, **3** `matTooltip` (los iconos sueltos).

**Por eso no se abre issue pidiendo un documento de patrones de diseño**: el patrón está escrito y
tiene componente. Lo que hace falta es que esta pantalla lo use.

**Alcance:** revisar también `formato-qr-pos`, que se construyó con el mismo molde.

### H3 · El monto en la vista previa

Ya estaba documentado arriba, en la prueba 1: `150.000` se muestra como `150` porque la vista previa
usa el parser de QR. **Confirmado en esta corrida.** No afecta la extracción real, que corre en el
filial y se verifica en la prueba 5.

### H4 · El error de negocio llega con DOS prefijos, los dos sistémicos

**Qué se vio**, al intentar una serie repetida:

```
Ups! Algo salió mal en operacion: Exception while fetching data (/data) : La serie "JF798SJJ" ya
esta registrada en la terminal "DEMO CAJA PRUEBA". Dos aparatos no pueden compartir identificador:
el cupon no diria de cual salio.
```

El mensaje **de la derecha es bueno**: dice qué pasó, con qué terminal chocó y por qué importa. Lo
que sobra es todo lo de adelante, y viene de dos lugares distintos, **ninguno de este módulo**:

| Capa | Origen | Alcance |
|---|---|---|
| `"Ups! Algo salió mal: "` | `desktop:src/app/generics/generic-crud.service.ts:103, 159, 228` | **Todos** los módulos: es el CRUD base |
| `"Exception while fetching data (/data) : "` | kickstart, no desenvuelto — ver **H1** | **Todo** `throw new GraphQLException` del backend |

Son dos correcciones de una línea cada una, en dos archivos, y arreglan todos los módulos a la vez.

### H5 · Diálogo de alta de terminal — ancho y disposición

Pedido en la corrida: llevarlo a **45vw** y poner dos campos por línea, que hoy van de a uno:

| Línea | Campos |
|---|---|
| 1 | descripción |
| 2 | código · serie |
| 3 | sucursal · moneda |
| 4 | proveedor · formato |

Hoy el diálogo tiene `max-width: 500px`
`[desktop:.../add-terminal-pos-dialog/add-terminal-pos-dialog.component.scss:3]`, más angosto que
la convención de diálogos del repo (65vw × 70vh). **45vw es una excepción deliberada**, no el
default: este formulario tiene ocho campos cortos y a 65vw quedaría vacío a los costados.

### H6 · El selector de proveedor puede ser un `mat-select`

La lista de proveedores de servicio **nunca va a ser larga** —son las procesadoras de la plaza— así
que no necesita buscador ni diálogo: entra en un select simple.

### H7 · Dos terminales pueden compartir serie, y el sistema lo tolera a propósito

**Lo que pasó.** Quedaron cargadas dos terminales con serie `JF798SJJ`: una **sin** proveedor y otra
**con** proveedor. Ni los índices ni `validarSerieUnica` lo impiden, porque los dos trabajan **por
ámbito**: con proveedor se compara contra las de ese proveedor, sin proveedor contra las que no
tienen ninguno.

**No es un bug, y el desktop lo maneja bien.** `resolverTerminalDelCupon` pide la serie exacta y, si
vuelve más de una, **no elige**: le dice al cajero que escanee el código de la terminal. Adivinar
sería cobrar contra la máquina equivocada y romper la conciliación sin que nadie lo note.

**Pero tiene una consecuencia práctica para esta prueba**: mientras dos terminales compartan la
serie del cupón de ejemplo, la resolución automática de la prueba 5 **no se puede ejercitar** — se
va a ver siempre el pedido de escanear el código, que es el camino degradado.

**Resuelto en la corrida**: la terminal vieja (`DEMO CAJA PRUEBA`, id 4) pasó a serie
`DEMO-VIEJA-1`, y `JF798SJJ` ahora resuelve a una sola. Verificado que replicó al filial.

### H8 · «Quitar formato» está en el menú de la fila, no en Configurar

**Qué pasó.** Durante la corrida se indicó buscar la opción en el diálogo *Configurar*, y ahí no
está. No es que falte: **está en el `mat-menu` de la fila del listado**, como *Quitar formato*, y se
deshabilita sola cuando la terminal no tiene formato asignado
`[desktop:.../list-terminal-pos/list-terminal-pos.component.html:197]`.

Y está bien que esté ahí y no en un select con opción vacía: **quitar el formato le bloquea la venta
con tarjeta a esa caja**, así que tiene que ser una acción deliberada y con confirmación, no el
efecto lateral de dejar un campo vacío.

**Para probar el candado:** en la terminal, apagar primero la carga a mano desde *Configurar*, y
recién entonces intentar *Quitar formato* desde el menú de la fila. Se tiene que rechazar.

### H2 bis · El contraste está dentro del mismo módulo

Medido sobre los dos listados vecinos:

| Listado | `mat-paginator` | `mat-menu` | filtros (`matInput`) | iconos sueltos |
|---|---|---|---|---|
| **Terminales POS** | 1 | 7 | 3 | 0 |
| **Formatos de terminal POS** | **0** | **0** | **0** | **3** |

La lista de terminales, que está al lado y es del mismo módulo, **ya implementa el patrón completo**.
No hay que inventar nada: sirve de referencia directa para arreglar la de formatos.

### H9 · El diálogo de imprimir código deja al usuario encerrado — y la causa es sistémica

**Qué pasó.** Abriendo *Imprimir código* sobre una terminal, el diálogo quedó con el spinner
girando para siempre, **«Cancelar» deshabilitado**, y la única salida fue la tecla `ESC`.

**La cadena completa, de afuera hacia adentro:**

1. `print-terminal-pos-dialog.component.html:53` —
   `<button mat-button (click)="onCancel()" [disabled]="loading">Cancelar</button>`.
   **Cancelar se apaga mientras `loading` sea true.** Cancelar un diálogo siempre es seguro:
   deshabilitar la única salida visible es lo que convierte un cuelgue en una trampa.
2. `loading` solo vuelve a `false` en los caminos previstos de `loadPrinters()` (`next` y `error`).
3. `ElectronService.getPrinters()` hace `from(ipcRenderer.invoke('get-system-printers'))`
   **sin chequear que haya Electron**. En el navegador `ipcRenderer` es `null`, así que eso lanza un
   `TypeError` **sincrónico, antes de que exista el observable**: no lo ve el `catchError` del
   `ThermalPrinterService`, no lo ve el handler `error` del `subscribe`, y `loading` se queda en
   `true` para siempre.

**Y el archivo declara la invariante que él mismo rompe**
`[desktop:src/app/commons/core/electron/electron.service.ts:14]`:

> *«Todo consumidor de `electron`/`ipcRenderer` está detrás del getter `isElectron` (false en web),
> así que en browser quedan null sin romper.»*

Medido sobre ese archivo: **de 12 métodos que tocan `ipcRenderer`, 11 no tienen guarda.** El único
que chequea es `getAppVersion`. Sin guarda: `relaunch`, `print`, `getPrinters`, `detectLocalDevices`,
`detectNetworkPrinters`, `getLocalIp`, `shareLocalPrinter`, `installLocalPrinter`, `printLocal`,
`printTestLocal`, `printWithPosPrinter`.

**Por qué importa más que antes.** El desktop **ya no es solo Electron**: la misma app se publica
como web en Cloudflare Pages (`alpha.desk`, `beta.desk`, `farmacia.desk`, `bodega.desk`). Cada uno de
esos 11 métodos revienta ahí.

**Hay un segundo camino a la misma trampa**, todavía sin disparar: el `subscribe` de la impresión
tiene solo handler `next`, sin `error`
`[print-terminal-pos-dialog.component.ts:~134]`. Si la impresión falla, `loading` tampoco se
restablece.

**Tres arreglos, de más barato a más profundo:**

| | Qué | Alcance |
|---|---|---|
| 1 | Sacar `[disabled]="loading"` de Cancelar | Este diálogo. Convierte una trampa en una molestia |
| 2 | Agregar handler `error` al `subscribe` de impresión | Este diálogo |
| 3 | Poner la guarda `isElectron` en los 11 métodos | **Todo el desktop web** |

**Detalle visual del mismo diálogo:** el texto de error del select de impresora
(*«Seleccione una impresora»*) **se superpone con la etiqueta «Cantidad\*»** del campo de abajo. Es
el mismo problema de espaciado que el alta de terminal ya tuvo que resolver con márgenes explícitos.

> **Nota de entorno:** la impresión térmica es Electron-only y el ciclo de implementación ya lo dice
> —servir el desktop en el navegador no la cubre—. Pero eso explica *por qué no imprime*, no por qué
> **encierra al usuario**. Lo segundo es un defecto real, y visible también en el desktop web.

### H10 · El diálogo del mapa crece entre pasos, y el texto leído no tiene contraste

**Contraste — es un bug, no una preferencia.** `.texto-ocr` pone
`background: rgba(0, 0, 0, 0.25)` sobre una superficie que **ya es oscura**, y **nunca define
`color`** `[desktop:.../derivar-mapa-dialog/derivar-mapa-dialog.component.scss:46]`. El `<pre>`
hereda el gris atenuado del tema y queda gris oscuro sobre gris oscuro. Es el bloque **que hay que
leer** para decidir si el OCR entendió el cupón.

Toda superficie que oscurece su fondo tiene que declarar su color de texto: heredarlo es apostar a
que el tema no cambie.

**Tamaño.** El diálogo es `max-width: 620px` **sin alto definido**, así que arranca chico en el paso
1 (dos botones) y da un salto en el paso 2 (texto leído + tabla). Pedido en la corrida: **tamaño
estable desde el inicio**, y la disposición en dos columnas —

| Izquierda | Derecha |
|---|---|
| la foto subida | lo que se leyó, y después la propuesta |

Eso además aprovecha el ancho: hoy la foto **ni se muestra**, y es justamente contra lo que uno
querría comparar el texto.

> Nota: la convención de diálogos del repo es 65vw × 70vh. Este usa 620px fijos. Con la disposición
> en dos cards, ir a la convención tiene sentido — al revés que el alta de terminal (H5), donde 45vw
> es una excepción deliberada porque son ocho campos cortos.

### H11 · El OCR volvió a leer `COMERCIO` como `COMERCI0` — y eso es una buena noticia

En la corrida, sobre el cupón sintético **limpio**, la lectura devolvió:

```
COMERCI0:00451233
```

Con **cero** en lugar de la O. No es un defecto nuevo: es el mismo caso que motivó el semáforo de
confianza, reproducido en vivo. Vale anotarlo porque es la evidencia de por qué el umbral existe:
un campo puede salir **plausible y estar mal**, y sin el semáforo el cajero no tendría cómo notarlo.

`COMERCIO` no es de los campos que el mapeo captura, así que no afecta esta prueba. Si algún formato
futuro lo necesitara, es candidato a `tipo: NUMERO`, que rechaza la confusión gratis.

**Tiempo medido:** 1505 ms para el cupón entero, **sin mapa**. Consistente con los 1552 ms de la
prueba automatizada. Es exactamente el número que el mapa viene a bajar.
