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
| Central corriendo | `cd frc-comercial/central && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| Filial corriendo | `cd frc-comercial/filial && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| Desktop | `cd frc-comercial/desktop && npx ng serve -c web --port 4201` |
| Un teléfono | En la misma wifi que la máquina. Para las pruebas 4 y 12 |
| Un cupón de papel | De cualquier POS. Si no hay, sirve el sintético: `java CuponDemo.java cupon.jpg` |

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
