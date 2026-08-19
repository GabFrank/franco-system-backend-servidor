# Plan — Lote B: features de RRHH

> Rama: **`feat/rrhh-recibos-y-penalizaciones`** (central + desktop), sale de `develop`
> **con el lote A ya mergeado**. Ver §0.3.
> Origen: `docs/manuales-implementacion/BUGS-TESTEO-2026-08-18.md`.
> Investigación item por item completada el 2026-08-19; cada sección cita `archivo:línea` verificado.

## 0. Composición

| # | Título | Veredicto de la investigación |
|---|---|---|
| **B12** | Select de operación en el ítem manual | La tabla catálogo **ya existe y está huérfana**. Ver §1 |
| **B4** | CRUD de cargos en desktop | Backend completo. Hay un delete que miente, ver §2 |
| **B7+B8** | Recibo: 2 vías, bruto en la firma, header de empresa | El `.jrxml` a tocar es el único sin test |
| **B10** | Penalización automática por rango de fechas | Ya es idempotente. Más barato de lo estimado |
| **B11** | Amonestaciones sin monto + contador + acta firmable | Reusar `Penalizacion`. `.jrxml` nuevo |
| **B16** | Consolidar cuotas de venta a crédito | Solo impresión. Una línea si el lote A hizo R1 |

**Salió del lote: F3** (recepción parcial de retiro de PDV). Tres razones, en §7.

### 0.1 Migraciones

Última aplicada: `V200.5`. Todas aditivas, sufijo `.5`.

| Versión | Contenido | Item |
|---|---|---|
| `V201.5` | `configuracion_rrhh`: claves `EMPRESA_DIRECCION`, `EMPRESA_TELEFONO` | B7/B8 |
| `V202.5` | `penalizacion`: `numero_advertencia`, `firmada`, `fecha_hecho` | B11 |
| `V203.5` | `liquidacion_concepto`: completar el seed | B12 |
| `V204.5` | `configuracion_rrhh`: flag de consolidación | B16 |

**Las cuatro son central-only.** Verificado: de las 63 tablas en `central_pub`, **ninguna** es del schema `rrhh`. `V155.0` lo dice en su header: *"Ninguna tabla se replica a filiales (gestion central-only)"*.

> ⚠️ **La primera versión de este plan ponía `direccion`/`telefono` en `empresarial.configuracion_general` con un `ALTER TABLE`. Eso rompía las filiales.** Esa tabla **sí** está publicada:
>
> ```
> V0__initial_schema.sql:14081
> ALTER PUBLICATION central_pub ADD TABLE ONLY empresarial.configuracion_general;
> ```
>
> Sin lista de columnas (sintaxis pre-PG15), así que replica todas las que existan en central. Agregar columnas ahí sin agregarlas también en filial hace que el apply worker de la suscripción falle en cuanto central escriba esa fila — y el plan preveía justamente que un operador cargue los datos poco después del deploy. Es el mismo accidente que `V154.1`/`V84.1` documentan, y el repo ya tuvo que alinear esta tabla una vez: `filial/V88.3` existe para emparejar las columnas que central agregó en `V178.5` y `V184.5`.
>
> **Por qué mover a `configuracion_rrhh` y no escribir la migración compañera en filial:** además de eliminar el riesgo y la coordinación de deploy entre repos, la pantalla de configuración de RRHH **ya sabe editar claves de esa tabla**. Con `configuracion_general` habría que construir UI nueva para dos campos; con `configuracion_rrhh` el operador los carga desde donde ya carga los otros ~20 parámetros. Sale más barato y más seguro.

### 0.2 Supuestos tomados

Ninguno de estos bloquea el trabajo, pero todos son revertibles y conviene confirmarlos en el review:

| # | Supuesto | Por qué |
|---|---|---|
| S1 | El header del recibo lleva razón social, RUC, dirección y teléfono. **Sin logo** | El pedido dice "los datos de la empresa"; el logo no se mencionó |
| S2 | El header va **una sola vez** arriba; la frase "Recibí de…" va en **ambas** vías | Cada vía tiene que sostenerse sola como comprobante firmado |
| S3 | Las advertencias no tienen niveles de gravedad | No se pidieron; agregar un nivel después es aditivo |
| S4 | El contador de advertencias es acumulado histórico, filtrable por año en la UI | Es el dato crudo; el reset es política que se puede aplicar en la vista |
| S5 | N advertencias no disparan nada automático | Automatizar consecuencias disciplinarias sin pedido explícito es de más |
| S6 | El acta de advertencia es **solo PDF A4** | Dos firmas no entran legibles en 32/48 columnas de térmica |
| S7 | El rango de penalizaciones se topa en 62 días | Evita recorrer meses de jornadas por un error de tipeo |
| S8 | B16 aplica a la liquidación **mensual**, no al finiquito | El pedido habla de la hoja de liquidación mensual. **Ver la nota de abajo** |
| S9 | B16 consolida en el recibo, **no** en la grilla de edición del borrador | Si la grilla agrupara, no se podría editar ni borrar una cuota puntual |

> **S8 deja un hueco conocido.** `LiquidacionFinalService` (líneas ~352-380) **también** arma ítems `CREDITO_CONVENIO_CUOTA`, y por diseño toma **todas** las cuotas impagas — no solo las vencidas, como sí hace la mensual (`LiquidacionSueldoService:306`). O sea el finiquito puede tener *más* filas de crédito que la liquidación mensual, y el motivo que justifica B16 ("la hoja es muy extensa") aplica igual o peor ahí.
>
> No es un defecto funcional: las filas sin consolidar siguen siendo correctas. Pero el pedido habla de la liquidación mensual y extender el flag al finiquito sin pedirlo sería decidir por el usuario. Queda anotado para confirmarlo, no asumido.

### 0.3 Por qué B va después de A, no en paralelo

Tres archivos se tocan en los dos lotes:

- `LiquidacionSueldoService` — B13 (A) y B12 (B) tocan la construcción de ítems
- `ReciboLiquidacionService` — R1 y B13 (A), B12 y B16 (B)
- `recibo-liquidacion.jrxml` — B7/B8 (B) sobre lo que R1 (A) reorganizó

Con A mergeado el rebase es trivial. En paralelo es conflicto garantizado en el archivo más delicado del módulo.

## 1. B12 — select de operación

**El hallazgo:** `rrhh.liquidacion_concepto` existe desde la migración fundacional **`V154.0:61-92`**, con backend completo — entidad `LiquidacionConcepto`, repositorio, servicio con `findByCodigo`, resolver GraphQL con save/delete/paginado, y schema en `configuracion-rrhh.graphqls:30-95`.

**Y está completamente huérfana.** No la referencia ningún servicio (`LiquidacionSueldoService`, `ReciboLiquidacionService`, `LiquidacionFinalService`) ni un solo archivo del desktop. Es scaffolding de Fase 0 que nunca se cableó.

```sql
CREATE TABLE rrhh.liquidacion_concepto (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    es_haber BOOLEAN NOT NULL DEFAULT TRUE,
    es_calculado_auto BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE, ...);
```

`es_haber` es literalmente lo que pide el usuario: que la operación elegida determine sola si es haber o descuento. **B12 no es crear un catálogo — es terminar de conectar el que ya está.**

**De dónde sale realmente "AJUSTE".** No es un `codigo` que exista en ningún lado. Nace en `ReciboLiquidacionService.operacion():180-196`, un `switch` hardcodeado sobre `codigo`. Los ítems manuales llevan `HABER_MANUAL`/`DESCUENTO_MANUAL`, caen al `default`, y con `manual == true` devuelven `"AJUSTE"`.

Ese switch es una **segunda copia del catálogo**, ya desincronizada: no tiene case para `AGUINALDO` ni `CREDITO_CONVENIO_CUOTA`, así que esos dos ya imprimen "HABER"/"DESCUENTO" genérico. Reemplazarlo por un lookup a la tabla arregla B12 y ese defecto de paso.

**El `.jrxml` imprime las dos columnas** — `operacion` (la categoría) y `observacion` (`it.getDescripcion()`, el texto libre). O sea el texto del usuario ya se imprime bien; lo que falla es la categoría. El select tiene que llenar el `codigo`; la descripción puede seguir siendo libre, o autocompletarse con el nombre del catálogo si el usuario no escribe nada.

**El backend debe forzar el signo.** Hoy `agregarItemManual:373-381` confía ciegamente en el `tipo` que manda el frontend. Con catálogo, `tipo` se deriva de `esHaber` y se ignora lo que llegue del cliente — así una llamada GraphQL directa o un bug de UI no pueden crear un ítem con el signo invertido.

**Ítems ya existentes:** no se toca el `codigo` de nada persistido. Se agregan `AJUSTE_HABER`/`AJUSTE_DESCUENTO` al catálogo como entrada genérica, y el `default` del lookup mantiene el comportamiento actual para los códigos legacy. Los recibos de liquidaciones ya pagadas siguen imprimiendo lo mismo que hoy.

**Seed a completar (`V203.5`):** faltan en la tabla `JUSTIFICATIVO_DESCUENTO`, `CREDITO_CONVENIO_CUOTA`, `HABER_MANUAL`, `DESCUENTO_MANUAL` y las dos entradas de ajuste. `INSERT ... ON CONFLICT (codigo) DO NOTHING`. `COMISION` está seedeado y sin uso — se deja, no molesta.

**Archivos:** `LiquidacionSueldoService` (`agregarItemManual`), `ReciboLiquidacionService` (`operacion`), `liquidacion-sueldo.graphqls`, migración; en desktop, el `mat-select` del diálogo de detalle + query nueva.

## 2. B4 — CRUD de cargos

**Backend listo.** `Cargo` vive en `empresarial` (no en `rrhh`): `id`, `nombre`, `descripcion`, `supervisadoPor` (autorreferencia), `sueldoBase`, `usuario`, `subcargoList`. `CargoGraphQL` ya expone `cargo(id)`, `cargos(page,size)`, `cargosSearch(texto)`, `countCargo()`, `saveCargo`, `deleteCargo`. La tabla existe desde `v26`. **Sin migración.**

**Desktop tiene solo lectura:** `cargo.model.ts` sin `toInput()`, `cargo.service.ts` con `onGetAll`/`onSearch`, sin `saveCargo`/`deleteCargo`, sin componentes.

**Plantilla a copiar:** `rrhh/motivo-vale/` (lista + diálogo de edición + queries + servicio). Es el CRUD más reciente y ya usa las convenciones actuales.

**El delete miente, y hay que arreglarlo.** `CrudService.deleteById:62-70` (central, Java) envuelve el borrado en try/catch y **devuelve `false` ante cualquier excepción**, incluida la violación de FK de un cargo en uso. Del otro lado, `GenericCrudService.onDelete:588-591` (desktop, TypeScript — no hay una clase `CrudService` en desktop) toma como éxito cualquier respuesta sin `errors`. Resultado: borrar un cargo asignado a un funcionario no borra nada y la UI dice **"Eliminado con éxito"**.

`Cargo` no tiene campo `activo`, así que tampoco hay baja lógica — solo borrado físico.

**El fix es en el backend, no en el frontend.** Chequear el booleano de retorno no alcanza: un `Boolean` no distingue "está en uso" de "no existía" de "se cayó la DB" — solo dice "algo falló", sin poder explicárselo al usuario.

El repo ya tiene el patrón correcto, en `ProveedorServicioGraphQL.java:82-93`, con un comentario que describe este mismo bug:

```java
/**
 * CrudService.deleteById se traga la excepcion y devuelve false, asi que la violacion
 * de FK llegaria al desktop como un "eliminado con exito" mentiroso. Chequeamos antes.
 */
public Boolean deleteProveedorServicio(Long id) throws GraphQLException {
    Long terminales = terminalPosService.countByProveedorServicioId(id);
    if (terminales != null && terminales > 0) {
        throw new GraphQLException("No se puede eliminar: hay " + terminales + " terminal(es) POS vinculada(s)...");
    }
    return service.deleteById(id);
}
```

Portar eso a `CargoGraphQL.deleteCargo`, contando las tres referencias que bloquean el borrado: `Funcionario.cargo`, `FuncionarioCargoHistorico.cargo` y `Cargo.supervisadoPor` (subcargos). El mensaje llega solo al desktop por `res.errors[0].message` — **sin tocar el frontend**.

**Otros cuidados:** no hay validación de ciclos en `supervisadoPor` — al editar, excluir el propio cargo de las opciones. Y `CambioCargoDialogComponent` (legajo) usa el mismo `CargoService`: ampliar el modelo sin romperlo.

**Registro:** entrada en el menú (`side-mini-variant.component.ts`, grupo *R.R.H.H. → Configuración*, con el `case` en `onItemClick()`) **y** en el buscador global (`search-bar.service.ts`, array `componenteList`). Los catálogos de `empresarial` hoy solo están en el buscador y los de RRHH solo en el menú; `Cargo` es de `empresarial` pero lo consume RRHH, así que va en los dos — y es justo lo que pide la issue desktop #235.

## 3. B7 + B8 — el recibo de liquidación

### 3.1 El test va primero

`recibo-liquidacion.jrxml` es **el único de los tres recibos de RRHH sin test**. `ReciboFiniquitoJrxmlTest` y `ReciboRrhhJrxmlTest` existen; este no.

Los `.jrxml` compilan en runtime. Un UUID duplicado al copiar el bloque de la segunda vía, o un paréntesis desbalanceado en una expresión, **no revienta en el build ni en CI: revienta al generar el PDF en producción**. Crear `ReciboLiquidacionJrxmlTest` calcado del de finiquito (compile + `fillReport` con params dummy + export) **antes** de tocar la plantilla.

### 3.2 Lo que ya está resuelto

- El parámetro `totalRecibido` que se pasa al jrxml **ya es** `liq.getTotalHaberes()`. Verificado que `totalHaberes` es el bruto real: `LiquidacionCalculator:35-49` suma todos los ítems `HABER` sin restar nada.
- `recibo-finiquito.jrxml:166` **ya tiene** casi literal la frase de B8:

  ```
  "Recibi de la empresa " + $P{empresa} + " la suma de " + $P{totalEnLetras}
  + " (Gs. " + $P{total} + ") en concepto de liquidacion final de haberes laborales."
  ```

  Se adapta, no se inventa.
- `NumeroALetrasService` ya está inyectado y se usa en el helper `enLetras()`.

### 3.3 Los cambios

**B7.1 — bruto en la firma.** `ReciboLiquidacionService:116`: `enLetras(liq.getTotalNeto())` → `enLetras(liq.getTotalHaberes())`. En el `.jrxml`, la ocurrencia de `$P{totalNeto}` de la **línea 171** (el párrafo de firma) → `$P{totalRecibido}`.

> **No tocar la de la línea 166.** Esa es el cuadro "Total a cobrar" del summary y tiene que seguir siendo el neto — es lo que el funcionario efectivamente cobra. Lo que cambia es solo la frase de recepción, que debe decir el bruto.

**B7.2 — dos vías.** Duplicar el bloque dentro de la banda `<summary>` con offset en Y, **con UUIDs nuevos** (Jasper los exige únicos; copiar y pegar literal rompe). La banda pasa de `height=210` a ~440.

**B7.3 — corte punteado.** Un `<line>` con `<pen lineStyle="Dotted"/>` de ancho completo (555pt) entre las dos vías.

**B8.1 — header de empresa.** `ConfiguracionGeneral` tiene `razonSocial` y `ruc`, pero **no tiene dirección ni teléfono**. `Sucursal` tiene dirección y no teléfono. `Timbrado` los tiene todos pero es un objeto fiscal de SIFEN — traerlo obligaría a resolver el timbrado activo por sucursal para un recibo interno que no es documento tributario.

Razón social y RUC se siguen leyendo de `ConfiguracionGeneral` (es lo que ya hace `razonSocial()`). Dirección y teléfono van como claves nuevas de `rrhh.configuracion_rrhh` en `V201.5` — ver el recuadro de §0.1 para por qué no en `configuracion_general`.

```sql
INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion, creado_en)
SELECT v.clave, v.valor, v.tipo, v.descripcion, now()
FROM (VALUES
    ('EMPRESA_DIRECCION', '', 'STRING', 'Direccion de la empresa, para el encabezado de los recibos'),
    ('EMPRESA_TELEFONO',  '', 'STRING', 'Telefono de la empresa, para el encabezado de los recibos')
) AS v(clave, valor, tipo, descripcion)
WHERE NOT EXISTS (SELECT 1 FROM rrhh.configuracion_rrhh c WHERE c.clave = v.clave);
```

> **Alguien tiene que cargar esos dos valores** en cada instancia después del deploy, desde la pantalla de configuración de RRHH. Hasta entonces el header sale con razón social y RUC, y las dos líneas nuevas vacías.

**B8.2 — la frase.** Reemplazar el bloque en mayúsculas actual por la redacción de §3.2, usando `$P{montoRecibido}` y `$P{montoEnLetras}` ya corregidos al bruto.

### 3.4 El espacio

A4 deja 802pt útiles (`pageHeight=842` menos márgenes 20+20). Lo fijo es header ampliado (~126) + `columnHeader` (18) + summary duplicado (~440) = **584pt**. A 15pt por fila de detalle, el techo real es **~14 ítems por página**.

**Ese número es más ajustado de lo que parece**, porque B13 del lote A convierte el ítem único de "PENALIZACIONES" en uno por penalización. Un funcionario con vale + cuota de préstamo + bono + horas extra + tres penalizaciones desglosadas ya está en el límite. Jasper no parte la banda: si no entra, manda las dos vías enteras a una hoja nueva, que es exactamente lo contrario de lo pedido.

Hay que **probarlo con una liquidación real cargada**, no con el datasource dummy del test, y con el peor caso que exista en la base. Si desborda: reducir la altura de fila del detalle, o compactar el bloque de firma.

**Fuentes:** el jrxml usa solo `SansSerif`. Todo `<font>` nuevo lo mantiene explícito. No introducir ninguna fuente.

## 4. B10 — penalizaciones por rango

**Mejor de lo estimado: ya es idempotente**, y la guarda es más fina que "esta fecha ya corrió". `PenalizacionService.generarPenalizacionesAuto:92-155` chequea **por jornada individual** (`findByJornadaIdAndSucursalIdAndAutoGeneradaTrueAndAnuladaFalse`). Correr 1–5 y después 3–7 no duplica los días 3–5: cada jornada del solape ya tiene su penalización y se saltea.

**Limitación:** la guarda es SELECT-then-INSERT a nivel aplicación, sin `UNIQUE` en DB. Dos corridas concurrentes sobre la misma jornada podrían colar un duplicado. Con un botón manual y un cron diario el riesgo es marginal; se anota, no se blinda en este PR.

**El cambio:** un método de rango que **itere día por día llamando al existente**. Reusa toda la lógica (feriados, justificativos con `evitaPenalizacion`, tolerancia) y la idempotencia intacta, y cada día lleva su propia transacción — un rango largo no queda en una transacción única, y si un día falla los otros ya commitearon. El método de un solo día se conserva: lo usa `PenalizacionScheduler`.

Mutation nueva `generarPenalizacionesAutoRango(desde, hasta)`, con el mismo gate `seg.requireAnyRole(seg.GESTIONAR)`. Tope de 62 días (S7).

**Desktop:** el diálogo pasa de un `matDatepicker` a `mat-date-range-input`, validando `desde <= hasta` y el tope.

## 5. B11 — amonestaciones

**Reusar `Penalizacion`** con `tipo = ADVERTENCIA` (valor nuevo del enum) y `monto = 0`. `PenalizacionTipo` es un enum Java, así que agregar el valor requiere deploy — aceptable para un valor sin lógica condicional, y no justifica migrar a tabla solo por esto.

Reusar evita duplicar repositorio, paginado con filtro por tipo, resolver, gating y generación de recibos, y mantiene la vista unificada del legajo.

**Que no ensucie la liquidación.** Con `monto = 0` es inocuo en los dos builders de ítems: suman montos y emiten el ítem solo `if (pen.signum() > 0)`. Pero eso es una invariante implícita que depende de que nadie cargue un monto por accidente — nada en la entidad ni en la DB lo impide. Va el filtro explícito por tipo en `LiquidacionSueldoService:225` y `LiquidacionFinalService:356`.

**Hay un tercer lugar, y es el que sí se rompe.** `DashboardRrhhService:144-149` también suma penalizaciones, **sin filtro de tipo y sin guarda de cero**:

```java
for (Penalizacion p : penalizacionRepository.findByFechaBetweenAndAnuladaFalse(desde, hasta)) {
    penCant++;
    if (p.getMonto() != null) penMonto = penMonto.add(p.getMonto());
}
```

Alimenta los KPIs `penalizacionesMesCantidad` y `penalizacionesMesMonto` (`dashboard-rrhh.graphqls:10-11`). A diferencia de los otros dos, acá el monto 0 **no salva nada**: `penCant++` corre igual. Toda advertencia inflaría el contador de penalizaciones del mes. El filtro por tipo va también acá — es el único de los tres donde es obligatorio, no defensivo.

**Contador: sale gratis.** `penalizacionesPage(page, size, funcionarioId, desde, hasta, tipo)` ya filtra por `tipo`, así que `totalElements` con `tipo: ADVERTENCIA` da el número sin tocar backend. Se muestra como un cuarto `dash-stat-chip` en el legajo, junto a los tres que ya están.

**Campos nuevos (`V202.5`):** `numero_advertencia` (int), `firmada` (boolean), `fecha_hecho` (date, cuando el hecho no es el día del registro). Los tres nullable.

**El acta.** `recibo-rrhh.jrxml` es la plantilla más cercana, pero tiene **una sola firma** y el acta necesita dos (funcionario y empresa). Va `.jrxml` nuevo — `reports/acta-advertencia.jrxml` — copiando su estructura de bandas y `SansSerif`, con parámetros `numeroAdvertencia`, `motivo`, `fechaHecho` y el bloque de firma duplicado en dos columnas. Más `ActaAdvertenciaJrxmlTest`, calcado de `ReciboRrhhJrxmlTest`.

Método `actaAdvertenciaBase64(Long, Integer, boolean)` en `ReporteRrhhService`, con la misma firma que los otros cinco. Solo PDF A4 (S6).

**Nota de orden:** el acta usa `razonSocialEmpresa():193-201`, que hoy devuelve solo el nombre. Si B7/B8 se hace antes en el mismo PR, el acta nace ya con RUC y dirección — vale la pena hacer B7/B8 primero para no tocar el jrxml nuevo dos veces.

## 6. B16 — consolidar cuotas de venta a crédito

**Se pueden distinguir de las de préstamo, sin ambigüedad.** Las de venta a crédito llevan `referenciaTipo = "CREDITO_CONVENIO_CUOTA"` (`LiquidacionSueldoService:317-318`), las de préstamo `"CPP_CUOTA"` (`:283-284`). Constantes distintas asignadas explícitamente, y ya usadas como discriminador en `aplicarEfectosCruzados`.

**Consolidar solo en la impresión.** Este es el punto que decide el diseño: si se consolidara en el modelo, el ítem único perdería el `referenciaId` por cuota y `aplicarEfectosCruzados:617-624` no podría llamar `reconciliarPorCuota`. **Las cuotas nunca quedarían saldadas: plata descontada del sueldo y la deuda viva.** Eso descarta tanto el ítem consolidado con tabla puente como el de ids serializados — no por costo, por corrección.

Y el motivo del pedido es explícito: *"la hoja de liquidación es muy extensa"*. Es un problema de presentación.

**Con R1 hecho en el lote A**, el cambio es agrupar los ítems `CREDITO_CONVENIO_CUOTA` en una fila sumada dentro del armado común, cuando `getBoolean("LIQUIDACION_CONSOLIDAR_CUOTAS_CREDITO", false)` sea true.

**Contingencia si R1 no está.** El lote A marca R1 como la fase con más chance de desbordarse (tres modelos de fila distintos, no una extracción mecánica). Antes de arrancar la fase 7 hay que **verificar la forma real con la que R1 quedó**, no asumir la planeada. Si R1 se sacó del lote A o salió con otra firma, B16 no queda bloqueado: se implementa replicando la agrupación en los tres generadores. Cuesta más y hay que verificar los tres formatos a mano, pero es viable. Lo que no es aceptable es tocar uno solo y dejar el ticket térmico desalineado del PDF.

**La config es key/value** (`rrhh.configuracion_rrhh`), así que `V204.5` es un `INSERT` idempotente, no un `ALTER TABLE`. En desktop, un `toggle` en `configuracion-rrhh-catalogo.ts` — el widget ya existe (`PENALIZACION_AUTO_TARDANZA` lo usa).

**El flag es global**, no por sucursal ni por funcionario. Si mañana se quiere consolidar en farmacia y no en bodega, no hay dónde colgar ese scope sin migrar la tabla.

## 7. F3 — por qué sale del lote

**1. Tiene una pregunta de negocio bloqueante.** Cuando tesorería recibe menos de lo declarado, no se puede deducir del código qué pasa con la diferencia. Tres modelos, tres diseños distintos: el retiro queda abierto con saldo pendiente; se cierra y la diferencia va como faltante de caja (ya existe el mecanismo — `OperacionFinanciera.diferencia` + `DiferenciaDestinoTipo`, que postea un `AJUSTE` rotulado sin crear Gasto/Vale real); o se le carga a `Retiro.responsable`. Elegir uno por cuenta propia es inventar política contable.

**2. Toca filial, con riesgo de cortar la replicación.** `financiero.retiro` replica **central→filial** (`V155.1`), y el repo ya documenta el accidente en `V154.1`/`V84.1`: el apply worker de la suscripción falla si el enum local no conoce la etiqueta. Un `PARCIAL` nuevo en `estado_retiro` tiene que existir en **todas** las filiales antes de que central escriba una sola fila con ese valor — y las filiales se autoactualizan cada 15 minutos, así que hay ventana real para que una se quede atrás.

**3. Es cross-repo** (central + filial + desktop) con deploy ordenado. No entra de colado en un lote de RRHH.

**Lo que sí quedó resuelto:** "seleccionar qué monedas" son **divisas** (Gs/R$/US$), no denominaciones. `RetiroDetalle` es `(retiro, moneda, cantidad)` sin FK a `MonedaBilletes`; el PDV nunca registra billetes al retirar. Y hay patrón hermano listo para copiar: `MaletinTesoreriaService.ingresarMaletinCierre` ya recibe `List<Long> monedaIds`. Esa mitad es barata; la de recepción parcial es la cara.

## 8. Fases y commits

| Fase | Contenido | Commit |
|---|---|---|
| 1 | Test del recibo (§3.1) | `test(rrhh): cubrir la compilacion del recibo de liquidacion` |
| 2 | `V201.5` + B7 + B8 | `feat(rrhh): recibo de liquidacion con datos de empresa y dos vias` |
| 3 | `V203.5` + B12 | `feat(rrhh): catalogo de conceptos en el item manual de liquidacion` |
| 4 | B4 | `feat(empresarial): abm de cargos` |
| 5 | B10 | `feat(rrhh): generar penalizaciones automaticas por rango de fechas` |
| 6 | `V202.5` + B11 | `feat(rrhh): amonestaciones con contador y acta firmable` |
| 7 | `V204.5` + B16 | `feat(rrhh): consolidar cuotas de venta a credito en el recibo` |

**Orden no negociable:** la fase 1 antes que la 2 (§3.1). B7/B8 antes que B11, para que el acta nazca con el header completo (§5).

## 9. Cierre

Igual que el lote A: `./mvnw clean verify`, `npm run check`, dos auditores de código, tests e2e/UI con Claude in Chrome, actualizar `BUGS-TESTEO-2026-08-18.md` y la skill `rrhh-expert`, commit, push, PR por repo, revisión humana.

**Verificación específica de este lote:** generar el recibo con una liquidación real de muchos ítems y confirmar que las dos vías entran en una hoja (§3.4).

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **El `.jrxml` revienta recién en producción** | El test de la fase 1 es bloqueante, no opcional |
| **Una migración corta la replicación a filiales** | Ninguna de las 4 toca una tabla publicada. Verificado, §0.1. Volver a verificarlo si se agrega una migración al lote |
| Las dos vías desbordan a una segunda hoja | Techo real ~14 ítems. Probar con el peor caso real; compactar detalle o firma si pasa |
| Nadie carga dirección/teléfono de la empresa | El header degrada a razón social + RUC. Avisar en el PR |
| **B16 depende de la forma real de R1** | Verificar cómo quedó R1 antes de la fase 7. Camino alternativo en §6 |
| B12 rompe recibos históricos | No se toca el `codigo` de nada persistido; el `default` mantiene el comportamiento |
| B4: se borra un cargo en uso y la UI dice que salió bien | Pre-check + `GraphQLException` en el backend, patrón de `ProveedorServicioGraphQL` |
| B11: una advertencia infla los KPIs del dashboard | Filtro por tipo en los **tres** lugares. En el dashboard el monto 0 no salva: cuenta filas |
| B10: duplicado por concurrencia | Anotado. Sin `UNIQUE` en DB; riesgo marginal con un botón y un cron |
| B16: el flag es global | Anotado. No hay scope por sucursal sin migrar la tabla |
| B16: el finiquito queda sin consolidar | Anotado en §0.2. A confirmar, no asumido |
