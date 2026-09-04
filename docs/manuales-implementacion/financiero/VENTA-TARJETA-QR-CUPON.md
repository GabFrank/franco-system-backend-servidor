# Venta con tarjeta — lectura del cupón por QR

> Cómo quedó implementado el registro de cobros con tarjeta leyendo el QR que el POS imprime
> en el cupón, en lugar de fotografiar el ticket térmico y pasarlo por OCR.
> Rama: `feat/qr-pos-formato-generico`. Toca **central**, **filial** y **desktop**.

## 1. Qué problema resuelve

Para registrar una venta con tarjeta, el cajero dejaba el PDV, sacaba una foto del cupón térmico
con el celular y esperaba que el OCR acertara. Fallaba seguido.

ValidaPix aceptó imprimir un QR en el cupón con los datos ya estructurados (formato `FRCP1`, en
producción desde el 2026-08-27), así que ese cupón se puede leer con el **lector que el PDV ya
tiene**.

El resto de los proveedores todavía no contestó, y varios **no van a poder** cambiar su formato:
habrá que adaptarse al que ya imprimen. Por eso el formato **no se escribe en el código**.

## 2. El formato es dato, no código

`financiero.formato_qr_pos` (central, migración `V216.5`; espejo en filial `V91.5`) guarda una
fila por proveedor:

- **`patron`** — regex con grupos nombrados (`(?<auth>...)`, `(?<amt>...)`, …).
- **`mapeo`** — JSON que dice qué grupo va a qué campo nuestro, con conversiones:
  `mapa` para monedas, `escalaSegunMoneda` para el importe, `formato` + `zona` para la fecha.
- **`proveedorServicioId`** — a qué proveedor pertenece. Puede ser null: eso lo vuelve un
  **comodín** que se prueba contra cualquier terminal.

Se carga desde la pantalla de administración (**Terminales POS → Formatos de QR**, rol `ADMIN`),
**sin release**.

### Lo que esto NO resuelve

El lector es keyboard-wedge con teclado es-LA. Nos podemos adaptar a cualquier **formato**, no a
cualquier **transporte**: si un proveedor imprime multilínea, o con llaves / comillas / pipe que el
wedge no tipea, ningún regex lo arregla — eso se resuelve configurando el scanner. **El charset es
la restricción no negociable que hay que pasarle a cada proveedor.**

## 3. Cuándo se escanea — en memoria, antes de que exista la venta

El diseño anterior escaneaba **después** de Finalizar: abría un diálogo por cada cobro con
tarjeta y hacía polling contra el backend. Eso dejaba al cajero esperando con la venta ya cerrada.

Ahora el escaneo pasa **al momento de agregar la línea de cobro**, y no toca el backend:

```
agregar cobro TARJETA
  └─ elegir terminal (ScanTerminalPosDialogComponent)
      └─ leer cupón      (EscanearCuponDialogComponent)
          └─ parsear contra los formatos activos  → datos EN MEMORIA en el CobroDetalle
                                                    (campos transitorios, no van en toInput())
Finalizar
  └─ saveVenta
      └─ recién acá: saveVentaTarjeta (PENDIENTE) + completarVentaTarjeta, sin diálogos
```

La línea de cobro muestra su estado en la tabla: `check_circle` verde si ya se leyó el cupón,
`schedule` naranja si quedó pendiente, más un ícono `qr_code_scanner` para reabrir el diálogo y
reemplazar lo leído. Todo antes de Finalizar.

**Se puede posponer**: si el cajero cierra el diálogo sin leer, la venta se cierra igual y la
`venta_tarjeta` queda `PENDIENTE`. El cierre de caja se la reclama después.

### Por qué no se guarda la venta antes (como hace Delivery)

Se evaluó portar el guardado temprano del modo Delivery para poder registrar cada tarjeta
incrementalmente. Se descartó: obliga a materializar una venta que el cajero todavía puede
cancelar, y deja ventas huérfanas si el PDV se cae en el medio. El escaneo en memoria da la misma
UX sin ese costo.

## 4. Cupón de otra terminal

Un cajero puede escanear el cupón de la terminal de al lado. El parser detecta el cruce
(`formatoCruzado`): si el formato que matcheó pertenece a **otro** proveedor que el de la terminal
elegida, se abre una confirmación — **Reintentar** (limpia y vuelve a esperar la lectura) o
**Confirmar igual**. Un formato comodín nunca cuenta como cruce.

No es una validación de servidor: el backend **no** verifica que el cupón pertenezca a la terminal.
Queda como hueco conocido.

## 5. Completar un PENDIENTE

Dos caminos, los dos contra el **filial** de la sucursal de la fila
(`VentaTarjetaService.completar()`, que ya valida `findByIdAndSucursalId` y exige estado
`PENDIENTE`):

1. **Desde el celular** — la pantalla muestra un QR que el mobile escanea
   (`RegistroVentaTarjetaComponent`). El QR se arma con `codificarQr()`, formato
   `frc-<sucursal>-VT-<idOrigen>-<idCentral>-<componente>-<data>-<ts>`, donde `data` es posicional:
   `cajaId|monto|ventaTarjetaId`. El mobile rechaza cualquier cadena que no empiece con `frc-`.
2. **Desde el PDV** — botón `qr_code_scanner` en **Ventas con tarjeta**, solo en filas
   `PENDIENTE` **de la sucursal donde está parado el usuario**. Completar la de otra sucursal no
   puede funcionar (el filial local no tiene esa fila), así que ni se ofrece.

> El filtro por sucursal compara con `Number()` en los dos lados a propósito: `Sucursal.id` es un
> `ID` de GraphQL y llega **string**, mientras `VentaTarjeta.sucursalId` es un `Int` y llega
> **número**. Con `===` estricto el botón no aparecía en ninguna fila y la pantalla quedaba
> inservible, sin ningún error visible. Vale para todo el repo: en este schema `id` es `ID` y los
> `xxxId` son `Int`.

Antes de escribir nada, el diálogo resuelve **a qué cobro** pertenece el cupón — ver §7.

## 6. Rol nuevo — `VENTA TARJETA COMPLETAR`

Migración `V217.5` (central; **sin espejo en filial**, igual que el seed de roles de tesorería
`V176.5` — `personas.role` se replica desde el central).

El aviso del cierre de caja le dice al cajero *"Registralas escaneando el QR del cupón desde el
PDV"*, pero la pantalla que lo permite colgaba de un menú gateado con `ADMIN`: **el cajero al que
el mensaje le hablaba no tenía cómo llegar**. Este rol abre ese camino, y nada más:

| Acción | Rol necesario |
|---|---|
| Ver la lista de ventas con tarjeta y completar un PENDIENTE propio | `VENTA TARJETA COMPLETAR` o `ADMIN` |
| Crear terminales, editar proveedores de servicio, configurar el módulo | solo `ADMIN` |
| Cargar / editar formatos de QR | solo `ADMIN` |

La migración **crea el rol, no lo asigna**: otorgarlo a cada cajero es un paso aparte, por la
pantalla de usuarios.

## 7. A qué cobro pertenece el cupón

Una venta puede tener varios cobros con tarjeta. `venta_tarjeta` guarda el cupón, pero la
conciliación real vive en `cobro_detalle.identificador_transaccion`: es ahí donde se ve qué
cobro pagó qué cupón. Vincularlos bien tiene tres caminos, en este orden.

### 7.1 · Desde el PDV — exacto, sin inferencia

Al leer el cupón, el desktop escribe el identificador **en la línea de cobro sobre la que se
escaneó** (`CobroDetalle.identificadorTransaccion`, que `toInput()` ya mandaba). Viaja en el
`saveVenta` junto con el resto del cobro.

Es el camino que importa: **el cliente sabe con certeza sobre qué línea escaneó**, porque el
diálogo se abrió parado en ella. Nadie tiene que deducir nada después.

### 7.2 · Desde la lista — el usuario elige

Completar un PENDIENTE de una venta ya cerrada no tiene esa certeza. El diálogo pide al filial
los cobros con tarjeta **libres** (sin identificador) de esa venta y decide:

| Cobros libres | Qué pasa |
|---|---|
| 1 | se vincula solo, sin preguntar |
| 2 o más | **frena y obliga a elegir**: countdown detenido, QR del celular oculto, cierre por backdrop/ESC bloqueado |
| 0, o falla la consulta | se completa igual sin vincular — perder el vínculo es malo, perder el registro del cupón es peor |

La elección viaja en `CompletarVentaTarjetaInput.cobroDetalleId`. Cuando ese campo viene, **manda**:
el backend no infiere nada. Valida que el cobro sea de esa venta y que no esté tomado por otro
cupón, y **esos errores sí fallan hacia afuera** — un cajero que cree que vinculó y no vinculó es
peor que un error visible.

Hay una salida explícita, **Descartar cupón**, que avisa que el registro queda PENDIENTE. Existe
para no encerrar al usuario si el lector leyó un cupón de otra venta; lo que no hay es forma de
descartarlo **por accidente**.

### 7.3 · Inferencia por monto — solo para lo viejo

Si no vino `cobroDetalleId` y la línea no está ya vinculada, el filial intenta deducirlo por monto
exacto. Con dos cobros del mismo monto no puede desempatarlos y **no escribe nada**: la referencia
igual queda en `venta_tarjeta.qr_crudo`, y eso es preferible a colgársela al cobro equivocado.

> ⚠️ El corte de "ya vinculado" (§7.1) **no es opcional**. Sin él, con dos cobros del mismo monto
> la inferencia veía dos candidatos, descartaba el que ya tenía identificador y le colgaba el cupón
> **al otro**: un vínculo *incorrecto*, peor que no tener ninguno.

### Orden de despliegue

`cobroDetalleId` es aditivo, así que un desktop viejo contra un filial nuevo sigue funcionando.
Al revés no: un desktop nuevo mandando ese campo a un filial viejo recibe error de campo
desconocido. **El filial va primero**, que es el orden que ya manda la guía de CI/CD.

## 8. Qué se valida al leer un cupón

Tres reglas, con distinta dureza a propósito.

### 8.1 · Moneda distinta → BLOQUEA, sin excepción

La moneda del cupón tiene que ser la del **cobro** que paga. No hay "registrar igual".

El motivo no es purismo: `monto` y `monto_escaneado` se guardan **sin unidad**. Un cupón de
8.000 R$ contra un cobro de 8.000 Gs da **diferencia cero** en cualquier reporte de conciliación
— el error queda invisible, y son ~5900x. Verificado en la prueba manual del 2026-09-04: se
registró sin un solo aviso.

Comparar montos de monedas distintas no significa nada, así que no hay decisión que el cajero
pueda tomar. Se valida en el cliente (contra la moneda del cobro) y en el **backend**
(`monedaId` del input, contra el cobro elegido o, si todavía no se sabe cuál es, contra la
moneda de la terminal). Un cupón cuyo formato no declara moneda no bloquea: hay proveedores que
no la imprimen.

> La terminal configurada en una moneda distinta a la del cobro **se avisa antes de escanear**,
> en el propio diálogo. No bloquea por sí sola: lo que decide es la moneda del cupón.

### 8.2 · Cupón ya registrado → BLOQUEA

Un cupón corresponde a UN cobro. Registrarlo dos veces imputa la misma plata a dos ventas, que es
un descuadre real. Se chequea por `qr_crudo` (otro `venta_tarjeta` con la misma cadena) y por
`identificadorTransaccion` (un `cobro_detalle` de **otra** venta que ya lo tenga).

Los cobros de la misma venta no cuentan: el PDV escribe el identificador junto con el `saveVenta`
(§7.1), así que al completar ya está puesto. Sin esa excepción el camino normal quedaría roto.

### 8.3 · Monto distinto o cupón viejo → CONFIRMA

Acá sí hay una decisión real: el cliente ya pagó y el cupón ya está impreso. Se muestra el cobro
y lo que dice el cupón, con **Escanear otro** / **Registrar igual**. Antes esto avisaba *después*
de registrar, que es enterarse tarde.

## 9. Qué se testeó

| Pieza | Batería | Resultado |
|---|---|---|
| central | `./mvnw clean verify -B -DskipFlyway=true` | 487 tests, 0 fallos |
| filial | `./mvnw clean verify -B` | 107 tests, 0 fallos (`VentaTarjetaServiceTest` 18/18) |
| desktop | `npm run check` (AOT, el gate del repo) | limpio, 0 errores |
| desktop — unit | `qr-pos-parser` (23), `venta-tarjeta-qr-payload` (8), `cobro-tarjeta` (8), `mensaje-error` (6) | 45 verdes |
| `V217.5` | dos pasadas en transacción con `ROLLBACK` | `INSERT 0 1` / `INSERT 0 0` — idempotente |

⚠️ **Karma (`npm test`) está roto de antes en el desktop y el CI no lo corre.** Los specs se
verificaron transpilando con esbuild y corriéndolos en node. Además `npm run check` **no
typechequea los `.spec.ts`** (`src/tsconfig.app.json` los excluye): eso se cubrió aparte con
`tsc -p src/tsconfig.spec.json --noEmit`.

**Probado end-to-end contra central y filial locales**, con verificación en base de los dos
caminos (escaneo inmediato y completar diferido) por los tres casos: un POS, dos POS con montos
distintos y dos POS con el mismo monto. En todos los casos el `venta_tarjeta` quedó COMPLETADO y
**ningún `cobro_detalle` quedó sin identificador**.

Queda sin probar con hardware real: el lector keyboard-wedge y un cupón impreso por ValidaPix.

## 10. Huecos conocidos

### ⚠️ CRÍTICO — la feature tiene DOS puertas y solo una está validada

Todo lo de §8 (moneda, cupón duplicado, estado) y §7 (vínculo con el cobro) vive en
**`completarVentaTarjeta` del FILIAL**, que es el camino del desktop.

La PWA no usa ese camino: usa **`updateVentaTarjeta` del CENTRAL**
(`central/.../graphql/financiero/VentaTarjetaGraphQL.java`), que es otra mutation sobre la misma
tabla y **no valida nada**:

```java
public VentaTarjeta updateVentaTarjeta(VentaTarjetaInput input) {
    VentaTarjeta entity = service.findByIdAndSucursalId(input.getId(), input.getSucursalId());
    if (input.getEstado() != null) entity.setEstado(input.getEstado());
    ...
    return service.save(entity);   // sin guardia de estado, sin duplicado, sin moneda
}
```

`mobile-pwa/.../venta-tarjeta-registro.page.ts` llama `actualizar(input)` con
`estado: COMPLETADO` fijo, y `cargar()` trae el registro sin filtrar por estado. Por ese camino:

- se puede pisar un registro ya COMPLETADO/CANCELADO,
- se puede registrar dos veces el mismo cupón,
- no se valida la moneda,
- **no se vincula ningún `cobro_detalle`** — la conciliación de §7 no ocurre.

Cerrarlo tiene dos salidas, y la elección no es obvia:

1. **Portar las validaciones al central.** Cierra el hueco sin tocar la PWA, al costo de duplicar
   la lógica en dos backends que no comparten código.
2. **Que la PWA complete contra el filial**, reusando `completarVentaTarjeta`. Elimina la segunda
   puerta en vez de blindarla, pero depende de resolver el acceso del móvil al filial: HTTPS
   contra una IP privada (mixed content), el token —que no es válido entre instancias— y los ids
   de sucursal, que pueden diferir entre central y filial.

**Decisión pendiente (2026-09-04).** Anotado, no implementado.

### La moneda vive en el registro, no en la terminal — `V218.5` / `V92.5`

`venta_tarjeta.moneda_id` guarda la moneda **del cobro** que el registro respalda.

Antes, `monto` y `monto_escaneado` no tenían unidad y la lista los pintaba con
`terminalPos.moneda.simbolo` — configuración **mutable**. Verificado el 2026-09-04: al pasar
`TPOS-BCD-01` a R$, cinco ventas hechas en guaraníes pasaron a mostrarse como reales sin que
ningún dato cambiara. La lista ahora usa `item.moneda` y solo cae a la de la terminal para filas
anteriores a la columna.

La moneda se escribe al crear el PENDIENTE (viaja en `VentaTarjetaInput.monedaId`, desde
`cobroDetalle.moneda`) y, si el PENDIENTE se creó sin ella —cliente viejo—, la aporta el cupón al
completar. Para entonces ya pasó por la validación de §8.1, así que se sabe que coincide.

⚠️ **Orden de despliegue.** `venta_tarjeta` es `BRANCH_TO_MAIN` (V150.1): la filial publica y el
central se suscribe. Si el publisher manda una columna que el subscriber no tiene, **la
replicación se corta**. Por eso `V218.5` (central) va **antes** que `V92.5` (filial) — y la
filial se despliega sola en ≤15 min, mientras el central es manual.

Sin backfill: la funcionalidad no está en uso en producción, así que no hay filas históricas que
convertir.

**Los decimales van con la moneda.** La lista formateaba con un `1.0-2` fijo, que mostraba
`55,5 R$` en vez de `55,50 R$`. Ahora el componente precalcula por fila `simboloMoneda` y
`digitosMoneda` a partir de `moneda.decimales` (0 en guaraníes, 2 en reales) — precalculado
porque el template no puede llamar funciones ni getters, y sobre una copia de la fila porque los
resultados de Apollo vienen congelados.

### Otros



- **El rol `VENTA TARJETA COMPLETAR` no lo aplica ningún backend.** Cero anotaciones de seguridad
  en `VentaTarjetaGraphQL` (filial y central) y `FormatoQrPosGraphQL`. El control es solo del menú
  del desktop: cualquier usuario autenticado puede completar o cancelar por GraphQL directo, y
  también reconfigurar los formatos de QR de toda la flota. Es la manifestación puntual del
  issue #177 (backend sin control de roles), no algo que introdujera esta feature.
- **`cancelarVentaTarjetaPorVentaId` no mira el estado**: cancela todos los registros de la venta,
  incluido uno COMPLETADO con plata cobrada, y deja su `cobro_detalle` vinculado. Estados
  desincronizados.
- **`completar()` es check-then-act sin lock**: lee, valida `PENDIENTE` y guarda dentro de un
  `@Transactional` simple, sin lock pesimista ni `@Version`. Dos submits concurrentes sobre el
  mismo pendiente pueden pasar los dos la validación.
- **El backend no valida que el cupón sea de la terminal.** La defensa es solo del cliente
  (§4). Un cliente modificado o el mobile podrían mandar un cupón cruzado.
- ~~Sin unicidad sobre el cupón~~ — **cerrado**: registrar el mismo cupón en dos ventas se
  bloquea en el backend (§8.2). Se detectó justamente porque el hueco estaba anotado acá y una
  prueba manual lo ejecutó.
- Cuando una `venta_tarjeta` no tiene `caja`, el QR viaja con el primer segmento vacío y el
  mobile lo reporta como *"pertenece a otra caja"* — mensaje engañoso para lo que en realidad es
  un dato faltante. `venta_tarjeta.caja_id` es `NOT NULL` en central desde `V140.1`, así que no
  debería pasar; queda anotado por si aparece.
