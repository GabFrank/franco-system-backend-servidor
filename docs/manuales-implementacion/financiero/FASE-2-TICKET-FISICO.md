# Fase 2 — ticket físico y captura por cámara

Backlog levantado el **2026-09-08**, después de validar en alpha la fase 1 (lectura del QR por
lector keyboard-wedge desde el PDV y desde la lista).

El objetivo de la fase 2 es el caso que hoy no tiene salida: **el cupón que no se puede leer por
QR** — porque el POS no lo imprime, porque el QR salió borroso, o porque el cajero no lo escaneó
en el momento.

> El flujo de escaneo con la cámara del celular **ya existe** (`RegistroVentaTarjetaComponent`,
> alcanzado por el QR que arma `codificarQr()`), pero **no está verificado**. Antes de construir
> encima hay que probar si funciona.

---

## 1 · Ticket con seña al posponer

Cuando se elige **"Registrar más tarde"**, imprimir un ticket que permita retomar el registro
después.

Debe llevar: **terminal, hora, id de venta, monto y moneda.**

**Por qué:** hoy posponer no deja rastro físico. El cajero termina con un cupón del POS en la mano
y ninguna forma de saber a qué venta pertenece — y al cierre de caja tiene que reconstruirlo de
memoria.

---

## 2 · Guardar la imagen del ticket

Guardar la foto del cupón **de forma local y accesible al momento de completar**, incluso cuando
el reconocimiento **no** logre extraer los datos (previa confirmación del usuario).

**Por qué:** la imagen es la evidencia. Si el parser falla, hoy no queda nada; con la foto, el
registro se puede completar a mano después y auditar más tarde.

**Nota:** `venta_tarjeta` ya tiene la columna `imagen_url`, hoy sin uso. Definir dónde vive el
archivo: local a la sucursal, en el filial, o replicado al central (impacta tamaño y replicación).

---

## 3 · Adjuntar imágenes al cierre de caja

Poder agregar imágenes a una caja en su cierre, con **tipo, descripción y observación**.

**Por qué:** el cierre es el momento donde aparecen los papeles sueltos (cupones, comprobantes,
notas). Hoy no hay dónde ponerlos.

---

## 4 · Carga manual como fallback, configurable

Un diálogo de carga manual de los datos del ticket, **configurable**: poder elegir **qué campos son
obligatorios**.

**Por qué:** ningún reconocimiento automático acierta siempre. Sin un camino manual, un cupón que
no se deja leer bloquea la operación. Que sea configurable evita imponer los mismos campos a
empresas con POS distintos.

**Nota:** `configuracion_venta_tarjeta` hoy sólo tiene `habilitado`. Este ítem la convierte en una
configuración de verdad.

---

## 5 · POS con recargo

Algunos POS aplican **recargo**: el valor final del cupón es **mayor** que el que se pasó en el
sistema. Hay que poder configurarlo.

**Por qué:** con la validación actual, ese cupón siempre va a dar diferencia de monto y va a pedir
confirmación en cada venta — y un aviso que sale siempre deja de leerse (ya nos pasó con el bug de
comparación de tipos, arreglado el 2026-09-08).

**A definir:** si el recargo es por terminal o por proveedor; si es porcentaje o monto fijo; y si la
conciliación debe comparar contra el monto **con** recargo o guardar los dos.

---

## 6 · Tickets sin POS fisico — terminal de tipo Maquina o Web

Algunos cupones **no salen de un POS**: se imprimen desde una PC. No hay aparato con un codigo
pegado que escanear, asi que el primer dialogo (elegir terminal escaneando su codigo) deja al
cajero trabado sin salida.

**Decision (2026-09-08): `terminal_pos` lleva un tipo — `MAQUINA` o `WEB`.**

Es mejor que las alternativas que se evaluaron (registrar sin terminal, o crear una terminal
generica "PC"): la conciliacion por terminal, el recargo por POS (item 5) y la acreditacion futura
siguen colgando de una terminal real. **No se pierde nada** y se cubre el caso nuevo.

### Lo que hay que construir

1. **`terminal_pos.tipo`** — `MAQUINA` / `WEB`. Es `MAIN_TO_ALL`: migracion en central + espejo en
   filial, y **registrar la tabla en `replication_table`** (ya esta, la columna viaja sola).
2. **El ABM** tiene que dejar elegir el tipo.
3. **Un solo input en el PDV**, que distingue lo que recibe:
   - **Se prueba primero como QR.** Los patrones estan anclados con `^...$`, asi que un codigo
     de terminal (`B1`, `TPOS-VPX-01`) no puede matchear un patron de cupon. Si ninguno matchea,
     se trata como codigo de terminal. Ese orden es el seguro; al reves no lo es.
   - Si es **codigo** -> terminal `MAQUINA`, flujo actual.
   - Si es **QR** -> el proveedor sale del formato que matcheo.

### El punto que queda abierto

**Hay varias terminales por proveedor en una sucursal** (confirmado por Gabriel). Asi que despues
de parsear un QR de ticket impreso desde PC, saber el proveedor **no alcanza** para saber la
terminal.

**Decision (2026-09-08): la terminal viaja en el propio QR cuando se puede, y si no, se elige de
una lista.**

FRCP1 es un formato nuestro — a ValidaPix le pedimos nuestro formato y acepto — asi que en muchos
casos se le puede agregar un campo con la terminal. Y no hace falta codigo nuevo para leerlo:
`formato_qr_pos.mapeo` ya mapea campos por nombre (`moneda`, `monto`, `fecha`...), asi que sumar
`terminal` es la misma mecanica y **se configura desde el ABM, sin release**.

Fallback cuando el QR no la trae: elegir de una lista, filtrada por `proveedor + tipo WEB`.

Esto ademas encaja con el principio que sostiene toda la feature: **el formato es dato, no
codigo**. Cada proveedor que acepte incluir la terminal mejora el flujo sin tocar la aplicacion.

### Por que el primer dialogo hoy cobra un peaje sin dar nada a cambio

Verificado el 2026-09-08: los dos aportes reales de conocer la terminal ANTES de leer el cupon
estan inactivos.

- **Priorizar formatos** (`ordenarPorProveedor`): con un solo formato cargado, da igual.
- **Detectar cupon cruzado** (`formatoCruzado`): el unico formato es comodin, y un comodin nunca
  cuenta como cruce.

Lo que si depende de la terminal es todo **posterior** al escaneo: `terminal_pos_id` en el
registro, los filtros y el reporte por terminal, y el recargo del item 5. Nada de eso exige
elegirla primero.

Dato: `terminal_pos_id` es **nullable** (entidad y migracion `V78.1`), asi que un registro sin
terminal ya es estructuralmente valido — pero con el tipo `WEB` no hace falta llegar a eso.

---

## 7 · (pendiente de definir)

Gabriel dejó un ítem sin completar al listar esto. **Confirmar cuál era.**

---

## Antes de empezar

**Probar el flujo de cámara que ya existe.** Es el supuesto sobre el que se apoyan los ítems 2 y 4,
y hoy nadie lo verificó. Si no funciona, el alcance de la fase cambia.
