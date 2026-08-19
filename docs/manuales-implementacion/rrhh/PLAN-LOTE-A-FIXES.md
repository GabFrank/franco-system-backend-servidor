# Plan — Lote A: fixes de RRHH y Financiero

> Rama: **`fix/rrhh-calculos-y-trazabilidad`** (central + desktop), sale de `develop`.
> Origen: `docs/manuales-implementacion/BUGS-TESTEO-2026-08-18.md`.
> Investigación item por item completada el 2026-08-19; cada sección cita `archivo:línea` verificado.

## 0. Composición del lote y qué cambió tras investigar

| # | Título | Veredicto de la investigación |
|---|---|---|
| **B1** | `Integer cannot be cast to Long` al ajustar salarios | Confirmado. Mismatch schema/Java. 3 líneas |
| **B3** | Dice 15 seleccionados, lista 10 filas | **No es cosmético.** Ver §2 |
| **B5** | Toggle IPS en `false` igual descuenta | Confirmado. 1 línea + verificación de datos pre-deploy |
| **B9** | Liquidación toma el salario anterior | **Dos causas**, una de ellas independiente de B1 |
| **B13** | Penalizaciones consolidadas sin detalle | Confirmado. Sin migración, sin tocar el `.jrxml` |
| **B6** | No se puede eliminar un ítem automático | El backend **ya está hecho**. Es un `*ngIf` |
| **F8** | Movimientos históricos con `origen_tipo` mal | Script manual, no migración. Filial no se toca |
| **R1** | *(nuevo)* Deduplicar el armado de filas del recibo | Habilitador de B16; sale del hallazgo de B13/B16 |

**Salieron del lote:**

- **B2** (sueldo `1`/`2`, cargo vacío) — **no hay código que arreglar**. La cadena schema → repository → query → template está correcta de punta a punta. Es data sucia: 75 funcionarios activos con `sueldo` entre 1 y 30 y `cargo_id` NULL en la base de dev. Queda como verificación en §9, no como fix.
- **B15** (IPS del finiquito proporcional) — no es un fix, es un feature. "Vacaciones proporcionales" **no existe en el código**: `VacacionService.devengar()` solo crea filas al completar un año de servicio. Va al PR de cálculo de salario junto con B14.

## 1. B1 — `Integer cannot be cast to Long`

**Causa raíz.** El schema declara `[Int]`, el resolver recibe `List<Long>`:

```
configuracion-rrhh.graphqls:94   ajustarSalariosAlMinimo(funcionarioIds: [Int], ...)
ConfiguracionRrhhGraphQL.java:90 public ... (List<Long> funcionarioIds, ...)
AjusteSalarioMinimoService.java:57  for (Long id : funcionarioIds)   ← ClassCastException
```

`graphql-java-kickstart` puebla la lista con `Integer` sin importar el genérico Java — el borrado de tipos hace que el `List<Long>` sea una promesa vacía. El unboxing implícito del for-each explota en el primer elemento. Como `ajustarAlMinimo` es `@Transactional`, no se persiste **ningún** ajuste.

**El fix ya está escrito en este repo.** `LiquidacionSueldoGraphQL.java:109-114` tiene el mismo schema `[Int]` y convierte a mano, con un comentario que explica exactamente esta trampa. Replicar ese patrón.

**Alcance verificado:** dentro de `graphql/rrhh/` solo estos dos métodos reciben listas de ids, y solo `ajustarSalariosAlMinimo` está sin convertir.

**Fuera de RRHH el patrón existe y no está cubierto por este lote.** La auditoría encontró dos casos estructuralmente idénticos que hoy no se manifiestan:

- `CambioGraphQL.java:77` — `saveCambio(..., List<Long> sucursalesIdList)` con schema `[Int]`. No revienta porque **el parámetro nunca se usa**: es código muerto.
- `CajaVirtualConfiguracionGraphQL` — el wrapper de input declara `List<Long> formasPagoVisiblesIds` / `cuentasBancariasVisiblesIds` (schema `[Int]`) e itera con `for (Long id : ...)`. No se pudo confirmar sin prueba en runtime si el binding de **campos de input** vía Jackson pierde los genéricos igual que el binding de **argumentos de método**. Sospechoso, sin veredicto.

No bloquea este lote. Va como issue aparte — el resumen de B1 no debe leerse como "no hay más ocurrencias en toda la app".

**Archivos:** `graphql/rrhh/ConfiguracionRrhhGraphQL.java`. Sin cambio de schema, sin cambio en desktop, sin migración.

## 2. B3 — el contador dice 15 y se ven 10

**No es paginación ni filtro.** Se descartaron las cuatro hipótesis con evidencia: no hay `MatPaginator`, el template recorre `dataSource.data` completo sin `*ngIf` por fila, la selección se inicializa sobre el mismo array que alimenta la tabla, y el backend (`FuncionarioRepository.findConSueldoMenorA`) no pagina ni trunca.

**La causa es CSS.** `ajuste-salario-minimo-dialog.component.scss:27-30`:

```scss
.tabla { max-height: 45vh; overflow: auto; }
```

Los 15 `<tr>` están todos en el DOM; ~10 entran en el viewport. El header lleva `sticky: true` (`.html:48`), así que el checkbox de "seleccionar todos" queda siempre visible e interactuable, y `onToggleTodos()` (`.ts:63-71`) hace `dataSource.data.forEach(f => this.seleccion.select(f))` sobre el array **completo**. En Electron/macOS con la scrollbar auto-oculta no hay ninguna señal de que haya más filas.

**Por qué esto importa.** `onConfirmar()` (`.ts:82-89`) manda `seleccion.selected` tal cual. Después de un click en "seleccionar todos", eso incluye los 5 funcionarios que el usuario nunca scrolleó para ver. **Se ajustan salarios de gente no revisada, con histórico legal.** Hoy no ocurre solo porque B1 hace explotar el mutation antes de persistir.

> **B1 y B3 tienen que ir en el mismo PR.** Arreglar B1 sin B3 deja el flujo armado para escribir cambios de sueldo sobre funcionarios que nadie miró.

**Fix:** subir el `max-height` a un valor que raramente recorte (o hacerlo función del alto de fila con tope ~70vh), agregar un contador fijo "Funcionarios afectados: N" fuera del botón, y un tooltip en el checkbox de seleccionar todos aclarando que abarca los que no se ven.

El código ya documenta la intención en un comentario propio (`.ts:20-21`): ajustar un salario es una decisión explícita. Esconder registros detrás de un `overflow` sin affordance la contradice.

**Archivos:** los tres del diálogo, en desktop. La lógica de `SelectionModel` no se toca — está bien.

## 3. B5 — el toggle de IPS se ignora

**Causa raíz: cálculo, no persistencia.** `LiquidacionSueldoService.java:211-214` arma el ítem incondicionalmente:

```java
BigDecimal ipsPct = configuracionRrhhService.getNumber("IPS_PORCENTAJE_FUNCIONARIO", new BigDecimal("9"));
BigDecimal ips = salarioBase.multiply(ipsPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
if (ips.signum() > 0) items.add(item(liq, "IPS_DESCUENTO", "DESCUENTO IPS", ips, ...));
```

`ipsActivo` no aparece ni una vez en todo el archivo. La persistencia del toggle se verificó completa (template → `FormControl` → input → `.graphqls` → `ModelMapper` → entidad) y **está correcta**.

**El patrón correcto ya existe en el servicio hermano**, `LiquidacionFinalService.java:212`:

```java
boolean descontarIps = in.getDescontarIps() != null
        ? in.getDescontarIps()
        : !Boolean.FALSE.equals(f.getIpsActivo());
```

`!Boolean.FALSE.equals(...)` trata `null` como activo y solo `false` explícito desactiva. Portar eso.

**Alcance:** acotado a la liquidación mensual. El finiquito ya está bien; el aguinaldo no aplica IPS. Hallazgo aparte, fuera de este lote: `ReporteRrhhService.resumenIpsBase64:103-136` recalcula el aporte desde cero sin mirar el flag ni si la liquidación llevó el ítem — va a seguir sobreestimando aun después de este fix.

**⚠️ Verificación obligatoria antes de deployar.** `V154.0:23` creó la columna con `ADD COLUMN ... DEFAULT false`, así que Postgres persistió `false` — no `NULL` — en todas las filas existentes. Todo funcionario que nunca pasó por el legajo a tildar el toggle tiene `ips_activo=false` guardado. Arreglar el cálculo **deja de descontar IPS de golpe** para toda esa gente. Ver §9.

**Archivos:** `service/rrhh/LiquidacionSueldoService.java`. Sin migración.

## 4. B9 — la liquidación toma el salario anterior

Son **dos causas distintas**, no una.

**(a) Consecuencia de B1.** Para los 15 funcionarios de ese diálogo: el ajuste nunca se persistió, así que el "salario nuevo" nunca existió en DB. Se resuelve solo al arreglar B1.

**(b) Staleness del caché de primer nivel de Hibernate — independiente.** `generarLote` (`LiquidacionSueldoService.java:643-662`) es `@Transactional` y carga todos los funcionarios al inicio; después llama `generarBorrador(f.getId(), ...)` por cada uno, que también es `@Transactional` con propagación `REQUIRED` y por lo tanto **se une a la misma transacción**. Su `funcionarioService.findById()` devuelve la instancia ya gestionada del identity map de la sesión, no una fila fresca.

No hay caché de segundo nivel en el repo (se buscó `@Cacheable`, no existe). Es puro comportamiento L1.

La mutation de un solo funcionario abre transacción propia y está sana. Solo la generación masiva puede servir un sueldo viejo — lo que explica el "en algunos casos" del reporte: depende de qué botón se usó.

**⚠️ Anotar `REQUIRES_NEW` NO alcanza — self-invocation de Spring.** `generarLote:659` llama `generarBorrador(f.getId(), ...)` **sin calificar, dentro de la misma clase**. Verificado: no hay auto-inyección `@Lazy` de sí misma, no hay `AopContext.currentProxy()`, no hay AspectJ weaving en el `pom.xml`. Con proxies de Spring (JDK o CGLIB) una llamada interna entre métodos del mismo bean **no pasa por el proxy**, así que la anotación se ignora en silencio y `generarBorrador` sigue corriendo en la transacción compartida.

Ese es el peor tipo de fix posible: compila, pasa un review a simple vista, y el bug sigue exactamente igual.

**Fix real — `TransactionTemplate` con `PROPAGATION_REQUIRES_NEW`** dentro de `generarLote`, y sacarle el `@Transactional` a `generarLote` y a `generarMes` (ninguno de los dos hace trabajo de DB propio).

*Corregido durante la implementación:* el plan decía extraer un `@Component LiquidacionLoteWorker`. No es viable acá — el worker necesitaría `LiquidacionSueldoService` para llamar a `generarBorrador`, y el servicio necesitaría al worker: dependencia circular que `@AllArgsConstructor` (inyección por constructor) no resuelve. Auto-inyección `@Lazy` de la clase en sí misma tampoco: Lombok no propaga la anotación al parámetro del constructor sin configurar `lombok.copyableAnnotations`.

`TransactionTemplate` no tiene ninguno de esos problemas: solo pide `PlatformTransactionManager`, que es un bean del framework.

**Beneficio colateral:** hoy `:661` tiene un `catch (Exception ignored)` que se traga los fallos individuales. Con transacción propia por funcionario, el fallo de uno deja de poder tumbar a los ya commiteados — hoy sí puede, porque comparten transacción.

**Riesgo a vigilar:** una transacción por funcionario en un lote grande cambia el perfil de locking. Medir con un lote real antes de dar por cerrado.

**Verificación de que el fix funciona:** no alcanza con leer el código. Hay que probar en runtime — cambiar el sueldo de un funcionario y generar el lote en la misma sesión, confirmando que toma el valor nuevo. Si se hubiera aplicado solo la anotación, esta prueba habría fallado y el review no.

**Archivos:** `service/rrhh/LiquidacionSueldoService.java`. Sin migración.

## 5. B13 — una penalización, un ítem

**Hoy** (`LiquidacionSueldoService.java:223-228`):

```java
BigDecimal pen = BigDecimal.ZERO;
for (Penalizacion p : penalizacionRepository.findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(fid, inicio, fin)) {
    if (p.getMonto() != null) pen = pen.add(p.getMonto());
}
if (pen.signum() > 0) items.add(item(liq, "PENALIZACION", "PENALIZACIONES", pen, DESCUENTO, null, null));
```

`referenciaId` y `referenciaTipo` van en `null`, que es exactamente por qué no hay detalle **y** por qué el ítem no se puede borrar con reversión segura.

**Cambio:** un `items.add(...)` por penalización, con `referenciaId = p.getId()`, `referenciaTipo = "PENALIZACION"` y descripción `tipo + ": " + descripcion`.

**Efectos colaterales — verificados, ninguno bloqueante:**

- **`aplicarEfectosCruzados` (`:573-629`)**: filtra con `if (tipo == null || refId == null) continue;`. Al setear el par, `PENALIZACION` cae en el `default: break;`. **No hace falta un `case` nuevo** — `Penalizacion` no tiene estado que revertir al pagar o anular (solo `anulada`, que este flujo no toca).
- **El recibo**: la banda `<detail>` del `.jrxml` **ya es iterable** y `ReciboLiquidacionService:88-96` ya itera `findItems()` uno a uno usando `getDescripcion()` como observación. Pasar de 1 a N ítems lista N filas solo. **No se toca el `.jrxml`.**
- **Totales**: `LiquidacionCalculator.calcular():38-52` suma por `tipo`. Un ítem de 50.000 y tres que suman 50.000 dan el mismo total.
- **Liquidaciones ya generadas**: quedan como están. `generarBorrador` borra y reconstruye los automáticos en cada llamada, y la query filtra por rango de fecha, así que no hay duplicación ni al regenerar ni entre períodos. **No hay que migrar datos.**

**Mejora que se habilita de paso — con trabajo real, no gratis.** `ReciboLiquidacionService.fechaItem():203-233` corta antes del switch por el guard `if (ref != null && tipo != null)`, y además no tiene case para `"PENALIZACION"`. Con `referenciaId` poblado, cada fila puede mostrar la fecha del hecho en vez del fallback.

Pero `ReciboLiquidacionService` **no tiene `PenalizacionRepository` inyectado** — el constructor solo trae `ValeRepository`, `BonoRepository`, `VacacionVentaRepository` y `PrestamoCuotaRepository`. Hay que agregar la dependencia además del `case`.

**Archivos:** `service/rrhh/LiquidacionSueldoService.java`, `service/rrhh/ReciboLiquidacionService.java` (`fechaItem`). Sin migración, sin desktop.

## 6. B6 — eliminar un ítem automático

**El backend ya está completo.** `LiquidacionSueldoService.eliminarItem(itemId):385-399` existe, valida `estado == BORRADOR`, borra y recalcula totales, y **no filtra por `manual`**. Está expuesto (`liquidacion-sueldo.graphqls:74`, resolver con `seg.requireAnyRole(seg.LIQUIDAR)`), y desktop tiene el `EliminarItemGQL`, el método del servicio y el handler del componente ya conectados.

**El bug es una condición de más**, en `liquidacion-detalle-dialog.component.html:66`:

```html
*ngIf="row.manual && liq.estado === 'BORRADOR'"
```

El botón de **editar** (línea 63) no tiene esa restricción — ya funciona hoy para automáticos.

**Sobre los efectos cruzados:** `aplicarEfectosCruzados` se invoca **solo** desde `pagar()`, `anular()` y `sincronizarDesdeSolicitudPago()`. En `BORRADOR` ningún ítem tiene todavía efecto aplicado sobre su entidad de origen — `Vale.estado`, `PrestamoCuota.montoPagado` y los demás se tocan recién al pagar. **Borrar un automático en BORRADOR no deja nada colgado.**

**Decisión: opción mínima.** Sacar `row.manual &&` y agregar un `dialogos.confirm` (hoy `onEliminarItem` no confirma).

**Limitación aceptada y documentada:** `generarBorrador:129-140` preserva solo `manual=true`, así que un automático borrado **reaparece al Regenerar**. No introduzco una inconsistencia nueva: la **edición** de automáticos ya se comporta igual hoy (`editado=true` tampoco sobrevive al regenerado). La alternativa durable — campo `excluido` + preservarlo en el regenerado + saltear la recreación en los 7 tipos de ítem automático — toca la lógica central de reconstrucción y merece su propio PR con tests por tipo.

**Archivos:** los dos del diálogo, en desktop. Backend intacto. Sin migración.

## 7. R1 — deduplicar el armado de filas del recibo

No estaba en la lista de bugs; sale de cruzar B13 con B16.

`ReciboLiquidacionService` arma las filas del recibo **tres veces**, cada una recorriendo `findItems()` por su cuenta. Cualquier cambio de presentación aplicado a uno solo deja los otros dos desalineados.

B13 (desglosar penalizaciones) y B16 (consolidar cuotas de crédito) son la misma operación con signo opuesto y viven ahí.

**⚠️ No es una extracción mecánica. Los tres modelos de fila son distintos:**

| Generador | DTO | Columnas | Cómo codifica el signo |
|---|---|---|---|
| `generarBase64` (PDF A4) | `ReciboLiquidacionItemDto` | 5: operación, dirección, descripción, fecha, monto | Columna explícita `ENTRADA`/`SALIDA` |
| `generarTicketEscPos` (térmica) | `Row` | 2: concepto, monto | Paréntesis: `(monto)` |
| `generarTicket` (58/80mm) | `FiniquitoRow` | 2: concepto, monto | Paréntesis: `(monto)` |

El A4 tiene fecha y dirección y **no usa paréntesis**; los tickets no tienen ni fecha ni dirección y codifican el signo **solo** con paréntesis. Unificar exige diseñar un modelo superset y parametrizar el formato de monto por destino — no mover código. Los dos errores a evitar: filtrar los paréntesis al A4, o perder la columna de fecha en los tickets.

Presupuestar esto como diseño, no como refactor de cinco minutos. Es la fase con más chance de desbordarse del lote.

**De paso:** `operacion(it):181-197` no tiene case para `"CREDITO_CONVENIO_CUOTA"` ni para `"AGUINALDO"`, así que hoy imprimen "HABER"/"DESCUENTO" genérico en la columna Operación en vez de su nombre. Se corrige acá.

**Archivos:** `service/rrhh/ReciboLiquidacionService.java`.

**Criterio de aceptación:** las tres salidas idénticas antes y después, salvo los dos labels corregidos. Comparar el PDF A4, el ticket 58 y el ticket 80 generados con la misma liquidación. `ReciboRrhhJrxmlTest` cubre `recibo-ticket-58.jrxml` y `recibo-ticket-80.jrxml` — correrlo, aunque no ejercite este servicio (usa su propia clase `Row` dummy).

## 8. F8 — backfill de `origen_tipo`

**Cadena de derivación, validada contra datos reales.** El movimiento no tiene FK al pago; el link va por la tabla puente:

```
movimiento_caja_virtual (origen_tipo='PAGO_CPP')
  → pago_solicitud_detalle.movimiento_caja_virtual_id = m.id
  → solicitud_pago.tipo   (COMPRA | GASTO | RRHH)
      si RRHH → desambiguar por bridge 1:1 (índice único por tabla):
        rrhh.vale.solicitud_pago_id              → RRHH_VALE
        rrhh.liquidacion_sueldo.solicitud_pago_id → RRHH_LIQUIDACION_SUELDO
        rrhh.liquidacion_final.solicitud_pago_id  → RRHH_LIQUIDACION_FINAL
        rrhh.aguinaldo.solicitud_pago_id          → RRHH_AGUINALDO
```

Replica la clasificación que ya hace el código en vivo (`PagoProveedorService.clasificar():176-191`).

**Alcance real: menor de lo que parecía.** De los 17 valores del enum, solo `PAGO_CPP`, `GASTO` y los 4 `RRHH_*` están afectados — son los únicos que pasan por el motor de pago compartido. Los otros (`RETIRO_CAJA`, `VENTA_CREDITO_COBRO`, `DEVOLUCION`, `ENTRADA_VARIA`, `OPERACION_FINANCIERA`, `MALETIN`, `MANUAL`, `ANULACION`) los setea siempre su propio servicio dueño y nunca pasaron por el bug. `CHEQUE` y `ACREDITACION_POS` están declarados pero no tienen productor: 0 filas.

**Medición en la base de dev:** de 16 movimientos `PAGO_CPP`, 8 quedan igual, 5 pasan a `GASTO` y 3 a `RRHH_VALE`. La mitad mal etiquetada. No es muestra representativa de prod.

**Script manual, no migración Flyway.** Hay precedente de las dos formas y la diferencia es la ambigüedad: `V180.5` sí hizo un backfill de esta misma columna dentro de la migración, pero era determinístico — FK 1:1, nada que decidir. Acá hay tres clases de fila que no se pueden derivar:

- eventos **MIXTO** (una línea de caja paga solicitudes de tipos distintos) — `PAGO_CPP` es la clasificación *correcta*, no un bug
- **RRHH huérfano** (solicitud RRHH sin fila en ninguna bridge) — el código en vivo asume `VALE` por default, pero eso es una heurística para el flujo en tiempo real, no evidencia para reescribir historia
- pagos **anteriores a `V182.5`**, sin fila en `pago_solicitud_detalle`

Una migración corre ciega al bootear: no hay punto donde un humano vea los conteos antes de aplicar. Son 3 instancias (alpha/farmacia/bodega) con historiales distintos. Y si la clasificación tuviera un error, en un script se reedita y se re-corre; en una migración ya aplicada hay que escribir otra migración correctiva por algo puramente cosmético. Mismo razonamiento que sacó `backfill-acl-cajas.sql` de Flyway.

La query solo actualiza cuando la clasificación es unánime y deja lo ambiguo intacto. El label de fallback de `PAGO_CPP` es "Compra", honesto para lo que efectivamente son compras.

**Filial no se toca.** `financiero.movimiento_caja_virtual` no existe en su schema ni está en ninguna publicación de replicación — caja mayor es central-only. Cero riesgo de propagación.

**Entregable:** `docs/manuales-implementacion/financiero/backfill-origen-tipo.sql`, con la misma estructura que el del ACL: PASO 0 de conteo, `BEGIN`, `UPDATE`, verificación, `COMMIT`/`ROLLBACK`. Idempotente (`WHERE origen_tipo = 'PAGO_CPP'` deja de matchear una vez corregido).

## 9. Verificaciones de datos previas al deploy

Ninguna es opcional. Las tres se corren contra la instancia destino, no contra dev.

**1. B5 — cuántos funcionarios dejarían de aportar IPS**

```sql
SELECT ips_activo, count(*) FROM personas.funcionario WHERE activo GROUP BY ips_activo;
```

Si la mayoría de los activos está en `false` por el default de `V154.0` y no por decisión real, hay que corregir los datos por legajo **antes** de deployar el fix. Si no, el primer mes post-deploy sale sin IPS para media plantilla.

**2. B2 — si la data sucia existe fuera de dev**

```sql
SELECT count(*) FROM personas.funcionario WHERE activo AND (sueldo < 1000 OR cargo_id IS NULL);
```

Si da 0 en prod, B2 se cierra como artefacto de la base de dev. Si no, hace falta un script de limpieza aparte — que no es parte de este PR.

**3. F8 — desglose antes de aplicar el backfill**

```sql
SELECT origen_tipo, count(*) FROM financiero.movimiento_caja_virtual GROUP BY origen_tipo ORDER BY 2 DESC;
```

Más la CTE de clasificación agrupada por `nuevo_origen`, para ver el reparto antes de tocar nada.

## 10. Fases y commits

Sin AOT entre fases; el gate real (`npm run check` en desktop, `./mvnw clean verify` en central) corre al final.

| Fase | Contenido | Commit |
|---|---|---|
| 1 | B1 + B3 | `fix(rrhh): ajuste de salario minimo no persistia y seleccionaba filas no visibles` |
| 2 | B5 | `fix(rrhh): respetar el toggle de ips del funcionario en la liquidacion mensual` |
| 3 | B9 | `fix(rrhh): leer el salario fresco en la generacion masiva de liquidaciones` |
| 4 | R1 | `refactor(rrhh): unificar el armado de filas del recibo de liquidacion` |
| 5 | B13 | `fix(rrhh): un item por penalizacion en la liquidacion` |
| 6 | B6 | `fix(rrhh): permitir eliminar items automaticos del borrador` |
| 7 | F8 + docs | `docs(financiero): script de backfill de origen_tipo historico` |

**Orden no negociable:** B1 y B3 en la misma fase (§2). R1 antes de B13, porque B13 toca `fechaItem` y R1 reorganiza ese archivo.

## 11. Cierre

1. `./mvnw clean verify` en central — incluye los tests que rompió el PR anterior por constructores nuevos.
2. `npm run check` en desktop (AOT). `tsc --noEmit` **no sirve** como gate: TS 4.8.4 no parsea los `.d.ts` modernos y los errores sintácticos suprimen el chequeo semántico.
3. Dos agentes auditores de código.
4. Tests e2e y UI con Claude in Chrome, servidor central local + desktop web.
5. Actualizar `BUGS-TESTEO-2026-08-18.md` (marcar resueltos) y la skill `rrhh-expert` si cambia algún invariante.
6. Commit, push, PR a `develop` en cada repo. Revisión humana.

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| **B5 deja media plantilla sin IPS** | La consulta de §9.1 es bloqueante del deploy, no del merge |
| **B9: el fix parece aplicado y no funciona** (self-invocation) | Worker bean aparte, no anotación. Prueba en runtime obligatoria, §4 |
| B9: transacción por funcionario cambia el locking en lotes grandes | Medir con un lote real; el `catch` que hoy traga errores queda mejor, no peor |
| **R1 se desborda: tres modelos de fila distintos** | Presupuestar como diseño. Si crece, sacarlo del lote y hacer B13 sin él |
| R1 cambia los PDF sin querer | Comparar A4 + ticket 58 + ticket 80 antes/después; solo deben diferir los labels de `AGUINALDO` y `CREDITO_CONVENIO_CUOTA` |
| B6: un automático borrado reaparece al Regenerar | Aceptado y documentado. La edición ya se comporta igual |
| F8 mal clasifica | La query solo toca lo unánime; `BEGIN`/verificar/`COMMIT` |
| B13 alarga el recibo | La banda iterable pagina sola. Cosmético |
