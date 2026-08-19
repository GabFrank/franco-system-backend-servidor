# Plan — Control de acceso por caja virtual (ACL de cajas mayores / chicas)

> **Feature solicitada 2026-08-19.** Hoy el acceso a las cajas es **por rol global**: quien tiene
> `TESORERIA VER` ve **todas** las cajas de la empresa, y quien tiene `TESORERIA GESTIONAR` puede
> mover plata en **cualquiera** de ellas. Se pide pasar a un modelo donde **cada caja tiene su
> propia lista de usuarios habilitados**, con permiso de lectura y/o escritura, administrada por el
> usuario que creó la caja.

## 1. Veredicto de viabilidad: ALTA

Tres cosas hacen que esto sea mucho menos costoso de lo que parece.

**a) La infraestructura de seguridad ya existe.**
`service/financiero/TesoreriaSecurityService.java` ya resuelve el usuario autenticado desde el
`SecurityContext` por nickname y lee sus roles de la DB, sin depender del marshaling JWT→Authentication
(que está roto a nivel sistema, issue #177). Ya expone `currentUsuario()`, `hasAnyRole()`,
`requireVer()`, `requireGestionar()`, y ya está **cableado en todos los resolvers de caja**
(`CajaVirtualGraphQL`, `MovimientoCajaVirtualGraphQL`). No hay que construir el andamiaje: hay que
agregarle métodos.

**b) Hay un único choke point para el dinero.** Verificado:

```
RRHH (vale, préstamo, aguinaldo, liquidación, finiquito)
  └─> MovimientoCajaVirtualService.registrarMovimiento()  ──┐
Financiero (gastos, CPP, maletín, retiro, entrada varia,     ├──> TesoreriaService.registrar()
            operación financiera, devolución)  ──────────────┘                    .transferir()
                                                                                  .anular()
```

`MovimientoCajaVirtualService.registrarMovimiento()` es un delegate de una línea a
`TesoreriaService.registrar()`. **Toda** la plata que entra o sale de cualquier caja pasa por
`TesoreriaService`. Eso significa que el permiso de **escritura** se hace cumplir en **3 métodos**
(`registrar`, `transferir`, `anular`), no en los 43 puntos de entrada que reciben un `cajaVirtualId`.

**c) El dueño ya está modelado.** `CajaVirtual` ya tiene `usuario_id` (`domain/financiero/CajaVirtual.java:57`).
Hoy **no se usa en ninguna lógica** — solo se setea desde el input en `saveCajaVirtual`
(`CajaVirtualGraphQL:157`). Se puede resignificar como *propietario* sin migración de columna, con dos
cambios: setearlo del usuario autenticado (no del input) y dejar de aceptarlo en el input.

**Lo que sí hay que construir:** la tabla de accesos, el filtrado de las consultas de lectura
(que son varias y hoy devuelven todo), y la UI de administración de la lista.

## 2. Estado actual — qué gatea qué

| Punto | Gate hoy |
|---|---|
| `cajaVirtual(id)`, `cajaVirtuales`, `cajaVirtualesFilter`, `cajaVirtualesPorTipo`, `cajaVirtualesPorSucursal` | `seg.requireVer()` — rol global, sin filtro por caja |
| `cajaVirtualesActivas()` | `hasAnyRole(TESORERIA.TODOS)` **o** `RrhhSecurityService.TODOS` — la comparte RRHH para elegir la caja destino. Corregido 2026-08-19: en una lectura anterior figuraba como "sin gate"; sí lo tiene |
| `cajaVirtualSaldos`, `cajaVirtualResumenBancario` | `seg.requireVer()` |
| `movimientosCajaVirtual*` (3 queries) | `seg.requireVer()` |
| `saveCajaVirtual`, `deleteCajaVirtual` | `seg.requireGestionar()` |
| movimientos / anulación | `seg.requireGestionar()` |
| Desktop | `mainService.tieneAlgunRol([ROLES.TESORERIA_GESTIONAR])` en `list-caja-virtual:82` y `caja-virtual-dashboard:160` |

**Conclusión:** el rol es hoy un interruptor de todo-o-nada. Exactamente lo que la feature quiere
cambiar.

## 3. Modelo de datos propuesto

### 3.1 Tabla nueva `financiero.caja_virtual_acceso`

```sql
-- V199.5__financiero_caja_virtual_acceso.sql   (sufijo .5 por convención del repo)
CREATE TABLE IF NOT EXISTS financiero.caja_virtual_acceso (
    id                bigint PRIMARY KEY,
    caja_virtual_id   bigint NOT NULL REFERENCES financiero.caja_virtual(id),
    usuario_id        bigint NOT NULL REFERENCES personas.usuario(id),
    puede_leer        boolean NOT NULL DEFAULT true,
    puede_escribir    boolean NOT NULL DEFAULT false,
    otorgado_por_id   bigint REFERENCES personas.usuario(id),
    creado_en         timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_caja_virtual_acceso UNIQUE (caja_virtual_id, usuario_id)
);
CREATE INDEX IF NOT EXISTS ix_cva_usuario ON financiero.caja_virtual_acceso(usuario_id);
CREATE INDEX IF NOT EXISTS ix_cva_caja    ON financiero.caja_virtual_acceso(caja_virtual_id);
```

Aditiva pura, sin `DROP`/`RENAME` — cumple la regla Flyway del proyecto. El `id` va con
`AssignedIdentityGenerator` como el resto de las entidades del central.

**Dos booleanos y no un enum de nivel** porque el pedido es explícito ("lectura y/o escritura") y
permite `leer=true, escribir=false` (el caso más común: el contador mira, no toca).

### 3.2 Propietario

`caja_virtual.usuario_id` pasa a significar **propietario**: quien creó la caja, único que
administra su lista de accesos. Cambios:
- `saveCajaVirtual` lo setea desde `seg.currentUsuario()` **solo al crear** (nunca en update).
- `usuarioId` sale de `CajaVirtualInput` (o se ignora — decidir, ver §7).
- El propietario tiene lectura + escritura implícitas; **no** necesita fila en `caja_virtual_acceso`.

## 4. Punto de enforcement

### 4.1 Escritura — 3 métodos, riesgo bajo

En `TesoreriaService`, al principio de `registrar()`, `transferir()` (ambas cajas) y `anular()`:

```java
seg.requireEscrituraCaja(mov.getCajaVirtual().getId());
```

Con eso quedan cubiertos **todos** los flujos: RRHH (vale, préstamo, aguinaldo, liquidación,
finiquito), CPP, gastos, maletín, retiro de PDV, entrada varia, operación financiera, devolución.

> ⚠️ **Ojo con los procesos automáticos.** Hay flujos que postean sin un usuario interactivo
> (replicación de retiros vía `RetiroTesoreriaProcesador`, schedulers). Si `currentUsuario()` es
> `null` hay que **permitir** (proceso de sistema), no rechazar — si no, se corta la replicación.
> Esto tiene que quedar explícito y testeado.

### 4.2 Lectura — dos formas según la consulta

| Tipo | Comportamiento | Queries |
|---|---|---|
| **Caja puntual** | lanzar excepción si no tiene acceso | `cajaVirtual(id)`, `cajaVirtualSaldos`, `cajaVirtualResumenBancario`, `cajaVirtualConfiguracion`, `movimientosCajaVirtual*`, `entradasVarias` |
| **Listado** | **filtrar**, no lanzar — devolver solo las cajas accesibles | `cajaVirtuales`, `cajaVirtualesFilter`, `cajaVirtualesPorTipo`, `cajaVirtualesPorSucursal`, `cajaVirtualesActivas` |

Los listados son el trabajo real: hay que empujar el filtro **al repositorio** (un `IN (SELECT
caja_virtual_id FROM caja_virtual_acceso WHERE usuario_id = :u) OR usuario_id = :u`), no filtrar en
memoria — si no, la paginación miente (`getTotalElements` cuenta cajas que el usuario no puede ver).

### 4.3 Métodos nuevos en `TesoreriaSecurityService`

```java
boolean esPropietario(Long cajaId);
boolean puedeLeerCaja(Long cajaId);
boolean puedeEscribirCaja(Long cajaId);
void requireLecturaCaja(Long cajaId);
void requireEscrituraCaja(Long cajaId);
void requirePropietarioCaja(Long cajaId);   // para administrar la lista
List<Long> cajasVisiblesIds();              // para los listados
```

Con el mismo bypass de superusuario que ya tiene el servicio (rol `ADMIN` o nickname `ADMIN`).

## 5. Relación rol ↔ ACL

Recomendación: **AND, no OR.** El rol dice *qué* puede hacer; el ACL dice *dónde*.

```
ver una caja      = requireVer()       Y  puedeLeerCaja(id)
mover plata       = requireGestionar() Y  puedeEscribirCaja(id)
administrar lista = esPropietario(id)  O  ADMIN
```

Así el modelo de roles existente no se toca y la feature es puramente aditiva: quien hoy tiene el
rol, mañana tiene el rol **y** una lista acotada de cajas.

## 6. Fases de implementación

| Fase | Qué | Dónde |
|---|---|---|
| **A. Datos** | Migración `V199.5`, entidad `CajaVirtualAcceso`, repository, service | central |
| **B. Backfill** | Poblar accesos de las cajas existentes (ver §7.2) | migración o script |
| **C. Seguridad** | Los 6 métodos nuevos en `TesoreriaSecurityService` + tests | central |
| **D. Escritura** | `requireEscrituraCaja` en `TesoreriaService.registrar/transferir/anular` + bypass de proceso automático | central |
| **E. Lectura puntual** | `requireLecturaCaja` en las queries de caja única (6 resolvers) | central |
| **F. Lectura listados** | Filtro en repositorio + paginación correcta (5 queries) | central |
| **G. API de gestión** | `cajaVirtualAccesos(cajaId)`, `otorgarAccesoCaja(...)`, `revocarAccesoCaja(...)`, `transferirPropiedadCaja(...)` + schema | central |
| **H. UI** | Diálogo "Gestionar accesos" en `list-caja-virtual` / dashboard: tabla de usuarios, checkboxes leer/escribir, buscador de usuario. Visible solo al propietario y a ADMIN | desktop |
| **I. UI defensiva** | Que el desktop no ofrezca cajas que el backend va a rechazar (selectores de caja en RRHH, pagar compras, gastos, retiro) | desktop |

**Orden sugerido:** A → C → D → E → F → B → G → H → I.
La **B (backfill) va después de E/F a propósito**: primero se construye el filtro, después se
carga la data que evita el apagón. Desplegar F sin backfill deja a todo el mundo sin cajas.

## 7. Decisiones abiertas — hay que responderlas antes de codear

### 7.1 ¿Qué pasa si el propietario se va de la empresa? — ✅ RESUELTO 2026-08-19
El creador queda como primer usuario con acceso (R+W implícito, sin fila en la tabla).
**Pero eso no cubre su egreso:** ADMIN siempre puede administrar la lista y **transferir la
propiedad** (`transferirPropiedadCaja`). Sin ese fallback, el primer tesorero que renuncia deja
cajas inadministrables. El bypass de ADMIN ya existe en `TesoreriaSecurityService`, es gratis.

> ⚠️ **Corregir primero:** `add-caja-virtual-dialog.component.ts:98` setea
> `cajaVirtual.usuario = mainService.usuarioActual` **también al editar** (la línea no está bajo
> `if (!isEditing)`). Hoy `usuario_id` no es *el creador* sino *el último que guardó*. Además el
> valor viene del cliente: cualquiera puede mandar cualquier `usuarioId`. Fix: setearlo en el
> backend desde `seg.currentUsuario()` y **solo cuando `input.getId() == null`**.

### 7.2 ¿Qué pasa con las cajas que ya existen? — parcialmente resuelto
El creador cubre las cajas **nuevas**. Para las existentes hay que mirar el dato antes de confiar
en él (ver el bug de §7.1 — puede ser el último editor):

```sql
select id, nombre, tipo, usuario_id, responsable_id from financiero.caja_virtual order by id;
```

En la DB local hay **1 sola caja** — no sirve como muestra. Correr esto en prod (farmacia y bodega)
antes de decidir el backfill.

Al activar el filtro, toda caja sin filas de acceso queda invisible para todos salvo ADMIN.
Opciones de backfill:
- (a) dar acceso R+W a todos los que hoy tienen `TESORERIA GESTIONAR`, y R a los de `TESORERIA VER`
  → nadie pierde acceso, pero no cambia nada hasta que alguien depure las listas;
- (b) dar acceso solo al `responsable_id` / `usuario_id` de cada caja + ADMIN
  → arranca restrictivo, probablemente rompa operación el día 1;
- (c) **recomendado:** (a) como red de seguridad, y que el propietario depure caja por caja.
  Requiere una lista revisada con vos antes de correrla.

### 7.3 ¿El rol `TESORERIA VER` sigue haciendo falta? — ✅ RESUELTO 2026-08-19
**Sí. Modelo AND confirmado por el usuario:** el rol habilita la capacidad, el ACL delimita el
alcance. El sistema de roles existente no se toca; la feature es puramente aditiva.

### 7.4 Procesos automáticos sin usuario
Replicación de retiros, schedulers, y los pagos disparados por RRHH. Definir la regla:
`currentUsuario() == null` → permitir (sistema). Y verificar que ningún flujo interactivo caiga
en esa rama por accidente (sería un bypass silencioso).

### 7.5 Interacción con F2 (pagar liquidación desde el hub de caja) — ✅ RESUELTO 2026-08-19
Se adopta la premisa **"pagos desde caja mayor solamente estando en caja mayor"** (ver F5 en
`../BUGS-TESTEO-2026-08-18.md`). Consecuencia directa para este plan: **RRHH no necesita permiso
de escritura sobre ninguna caja.** El liquidador deja la liquidación aprobada; el tesorero la paga
desde su caja, con su propio acceso. Sin esta premisa habría que dar escritura a todo liquidador y
el ACL perdería sentido.

### 7.6 ¿Aplica también al filial?
El módulo de tesorería es del central. Confirmar que ninguna caja se administra desde el filial
antes de asumir alcance central-only.

### 7.7 ¿Qué ve alguien sin acceso a ninguna caja?
Definir el estado vacío del dashboard/listado: pantalla vacía con mensaje ("no tenés acceso a
ninguna caja, pedíselo al responsable") en vez de una tabla vacía sin explicación.

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Apagón operativo** al activar el filtro sin backfill | Fase B antes de F en producción; feature flag por configuración para activar el enforcement |
| **Bypass por punto de entrada olvidado** | El choke point de `TesoreriaService` cubre escritura; para lectura hay que auditar los 43 puntos con `cajaVirtualId`. Un test que enumere resolvers sin gate ayuda |
| Paginación mentirosa si se filtra en memoria | Filtro en el repositorio (§4.2) |
| Corte de replicación por rechazar procesos de sistema | §7.4, con test explícito |
| Un punto de entrada de lectura sin acotar | El choke point cubre escritura; para lectura se auditaron los 43 puntos con `cajaVirtualId` |

## 9. Tamaño estimado

- **Central:** 1 migración, 1 entidad + repo + service, ~6 métodos de seguridad, ~11 resolvers
  tocados, 4 mutations nuevas, schema. Es el grueso.
- **Desktop:** 1 diálogo nuevo (patrón `add-*-dialog` existente, 65vw × 70vh por convención),
  ajustes en 2 componentes + los selectores de caja.
- **Sin breaking change de API**: todo aditivo salvo `usuarioId` en `CajaVirtualInput` (§3.2).

El trabajo no está en la lógica de permisos —es simple— sino en **cubrir todos los caminos de
lectura sin dejar un hueco** y en **no apagarle las cajas a nadie el día del deploy**.

---

# 10. Fase previa obligatoria — completar el hub con el modelo de pago genérico

> Agregado 2026-08-19. Esta fase **va antes** de todo lo anterior: sin ella no se puede aplicar la
> premisa F5 ("pagos desde caja mayor solo estando en caja mayor"), y sin F5 el ACL obliga a dar
> escritura de caja a todo liquidador de RRHH.

## 10.1 El componente genérico ya existe — no hay que inventarlo

`PagarComprasDialogComponent` (792 líneas) **ya está parametrizado por modo** y se reutiliza para
dos conceptos distintos desde el mismo hub:

```ts
// registrar-egreso-dialog.component.ts:57-58
if (op.tipo === 'PAGO_CPP' || op.tipo === 'GASTO') {
  const d: PagarComprasDialogData = { cajaVirtual, modo: op.tipo === 'GASTO' ? 'GASTOS' : 'COMPRAS' };
```

```ts
// pagar-compras-dialog.component.ts:26
modo?: 'COMPRAS' | 'GASTOS';   // GASTOS reusa el mismo builder de pago; default COMPRAS
```

La ramificación por modo está contenida: **9 usos de `esGasto` en el `.ts` y 6 en el `.html`**.
Todo lo demás —selección, totales, formas de pago, cheques, cotización— es común.

**Recomendación: extender el modo, no crear 5 diálogos nuevos.** Renombrar el concepto a algo
como `PagarObligacionesDialogComponent` con
`modo: 'COMPRAS' | 'GASTOS' | 'LIQUIDACION' | 'FINIQUITO' | 'AGUINALDO' | 'PRESTAMO'`, y extraer las
diferencias a una **estrategia por modo** en vez de seguir agregando `if (esGasto)`.

## 10.2 Anatomía del modelo de pago genérico (lo que hay que respetar)

**Paso 1 — selección de obligaciones.** Tabla filtrable de pendientes, con `montoAPagar` editable
por fila (habilita pago parcial), agrupación forzada por ente + moneda (`_disabled` sobre las filas
incompatibles, `:379`), totales en moneda de la deuda y en moneda principal con cotización.

**Paso 2 — formas de pago (el "builder").** Un *draft* siempre visible que se confirma en líneas:
- `fuente: 'CAJA_MAYOR' | 'CUENTA_BANCARIA' | 'CHEQUE'`
- moneda + cotización + monto convertido
- el draft se prefija con el **restante** (`totalDeuda − totalPago`), así el caso común es un clic
- cheques: N cuotas encadenadas por intervalo de días, numeración desde la chequera (`:486-508`)
- líneas editables/eliminables, con recálculo de faltante

**Paso 3 — confirmación en lote.** Una sola mutation con todas las obligaciones y todas las líneas
de pago (`pagarSolicitudesLoteCajaMayor`), atómica.

**Extra del modo GASTOS:** permite **crear la obligación en el momento** (`abrirNuevoGasto()` /
`crearGasto()`, `:306-321`). Útil para conceptos que no preexisten.

**Contraste — el vale es el modelo simple.** `registrar-vale-dialog` son 133 líneas: no hay
selección de deuda porque el vale se *crea* y se paga en el acto. Ese es el otro patrón válido:
**alta directa**, no *pago de pendientes*.

## 10.3 Qué agregar al hub, y con qué patrón

| Concepto | Hub | Patrón | Estado |
|---|---|---|---|
| Compras (CPP) | egreso | pendientes | ✅ existe |
| Gastos | egreso | pendientes + crear al vuelo | ✅ existe |
| Vale | egreso | alta directa | ✅ existe |
| **Liquidación mensual** | egreso | pendientes, **selección simple** | ❌ **F2** |
| **Finiquito** | egreso | pendientes, **selección simple** | ❌ **F2** |
| **Aguinaldo** | egreso | pendientes, **selección simple** | ❌ falta |
| **Desembolso de préstamo** | egreso | **alta directa** (como el vale) | ❌ falta |
| **Cobro de cuota de préstamo** | **ingreso** | pendientes, **selección simple** | ❌ falta |

Los 4 de tipo *pendientes* son el mismo diálogo con otro `modo` y otra query de origen; los de RRHH
con **selección simple** (§10.4). El desembolso sigue el patrón vale. El cobro de cuota es el único
que va al **hub de ingreso**.

## 10.4 Backend — reutilizar, no duplicar

Las mutations de pago **ya existen** y ya tienen los efectos cruzados resueltos
(vale→DESCONTADO, cuota→PAGADA, aguinaldo→PAGADO y su regla de no-doble-pago en diciembre):

| Concepto | Mutation |
|---|---|
| Liquidación mensual | `pagarLiquidacion(id, cajaVirtualId)` — `LiquidacionSueldoService:457` |
| Finiquito | `pagarLiquidacionFinal(id, cajaVirtualId)` — `LiquidacionFinalService:561` |
| Aguinaldo | `pagarAguinaldo(id, cajaVirtualId)` — `AguinaldoService.pagar` |
| Préstamo | `crearPrestamo(input, cajaVirtualId)` / `cobrarCuota(cuotaId, cajaVirtualId, monto)` |
| Vale | `confirmarVale(id, cajaVirtualId, autorizadoPorId)` |

**Lo que falta del lado backend son las queries de pendientes** por concepto (liquidaciones
aprobadas sin pagar, aguinaldos aprobados sin pagar, cuotas vencidas), análogas a las que alimentan
el modo COMPRAS/GASTOS.

**✅ Decisión tomada 2026-08-19 (usuario): los pagos de RRHH son siempre 1 por 1.** Nunca se paga
la nómina de 30 funcionarios en una sola operación. Consecuencias, todas simplificadoras:

- **No hacen falta mutations en lote.** Se reutilizan tal cual las mutations existentes
  (`pagarLiquidacion`, `pagarLiquidacionFinal`, `pagarAguinaldo`, `cobrarCuota`), que ya pagan
  de a una y ya traen resueltos los efectos cruzados. **Cero backend nuevo de pago.**
- **No hay problema de atomicidad parcial** — desaparece el escenario "pagué 18 de 30 y falló".
- **Selección simple, no múltiple**, en los modos de RRHH: la tabla de pendientes funciona como
  *selector de a quién le pago*, no como carrito. Un radio/click por fila en vez de checkboxes.

**Implicancia para el diálogo genérico:** el modo tiene que declarar su cardinalidad.

```ts
modo: 'COMPRAS' | 'GASTOS'                                    // multi-selección (carrito)
    | 'LIQUIDACION' | 'FINIQUITO' | 'AGUINALDO' | 'COBRO_CUOTA'  // selección simple
```

Lo que **no** cambia es el paso 2: el builder de formas de pago (efectivo / cuenta bancaria /
cheque, multi-moneda con cotización) se usa igual, porque una sola liquidación puede pagarse con
más de una forma.

## 10.5 Orden de trabajo actualizado

```
FASE 0 — Hub (esta sección)
  0.1  Generalizar PagarComprasDialog a PagarObligacionesDialog (estrategia por modo)
  0.2  Queries de pendientes por concepto (central) — lo ÚNICO nuevo del backend
  0.3  Cardinalidad por modo en el diálogo (multi para COMPRAS/GASTOS, simple para RRHH)
  0.4  Modos nuevos: LIQUIDACION, FINIQUITO, AGUINALDO (egreso) + COBRO_CUOTA (ingreso)
  0.5  Desembolso de préstamo: patrón alta directa (como vale)
FASE 1 — F5: ocultar los 6 selectores de caja en las pantallas de RRHH
FASE 2 — ACL: A → C → D → E → F → B → G → H → I  (§6)
```

**§7.3 resuelta 2026-08-19: modelo AND confirmado.** El rol habilita la capacidad, el ACL
delimita el alcance.

---

# 11. Estado de implementación — 2026-08-19

Rama `feat/pagos-hub-acl-cajas` en **central** y **desktop**.

## Hecho

| Fase | Qué se hizo | Dónde |
|---|---|---|
| **0** (hub) | Modos `LIQUIDACION` / `FINIQUITO` / `AGUINALDO` en el diálogo genérico + entradas en el hub de egresos. Puente `SolicitudPago` tipo RRHH para los tres, con `sincronizarDesdeSolicitudPago` por concepto | `PagoRrhhTesoreriaService`, `PagoRrhhTesoreriaGraphQL`, `V199.5`, `pagar-compras-dialog` |
| **F4** | Etiqueta del movimiento desde `origenTipo` (el concepto real) en historial y dashboard | `caja-virtual.model.ts`, 2 componentes |
| **F6** | `origenTipo` correcto por concepto del evento + etiqueta consolidada con N documentos + `detalleDePago(pagoId)` + diálogo "Detalle del pago" | `PagoProveedorService`, `detalle-pago-dialog` |
| **A** | Migración `V200.5` (`financiero.caja_virtual_acceso`), entidad, repo, `CajaVirtualAccesoService` | central |
| **C** | `esSuperusuario` · `esPropietario` · `puedeLeerCaja` · `puedeEscribirCaja` · `requireLecturaCaja` · `requireEscrituraCaja` · `requirePropietarioCaja` · `cajasVisiblesIds` · `esProcesoDeSistema` | `TesoreriaSecurityService` |
| **D** | Escritura exigida en `registrar` / `transferir` / `anular` | `TesoreriaService` |
| **E** | Lectura puntual en caja, saldos, resumen bancario, 3 queries de movimientos, entradas varias, configuración | resolvers de `financiero` |
| **F** | Listados filtrados **en la consulta** (`findAllVisibles`, `filterVisibles`, `findByTipoAndIdIn`, `findBySucursalIdAndIdIn`, `findByActivoTrueAndIdIn`) | repo + service + resolver |
| **G** | `cajaVirtualAccesos` · `otorgarAccesoCaja` · `revocarAccesoCaja` · `transferirPropiedadCaja`; propietario desde el `SecurityContext` y solo en el alta | schema + resolver |
| **H** | Diálogo "Gestionar accesos" (alta con buscador, toggle de escritura, revocar, hacer responsable), visible solo al responsable o ADMIN | `gestionar-accesos-caja-dialog` |
| **B** | Script de backfill con verificación previa y posterior — **se corre a mano** | `backfill-acl-cajas.sql` |

## Decisiones de implementación que no estaban en el plan

- **La lectura no puede apagarse desde la UI.** Un acceso con escritura y sin lectura sería mover
  plata a ciegas; para sacar el acceso se revoca (borra la fila) en vez de dejar una inerte.
- **Al responsable no se le puede otorgar una fila de acceso.** Ya tiene permisos implícitos, y
  una fila sería revocable — dejaría la caja sin quien la administre.
- **Transferir la propiedad borra el acceso explícito del nuevo responsable**, por lo mismo.
- **`esProcesoDeSistema()` mira que no haya principal**, no que el usuario no tenga permisos. Un
  usuario autenticado sin acceso es rechazado; solo pasa lo que corre sin sesión.

## Pendiente

| Qué | Por qué |
|---|---|
| **Fase I** — UI defensiva: que los selectores de caja no ofrezcan cajas que el backend va a rechazar | Depende de F5 |
| **F5** — ocultar los 6 selectores de caja de RRHH | Necesita completar el hub |
| **Hub: desembolso de préstamo y cobro de cuota** | El desembolso es alta directa (patrón vale); el **cobro de cuota es un ingreso** y no pasa por el motor de pago — necesita su propio camino y una query de cuotas pendientes |
| **Correr el backfill** | Necesita la lista de cajas de prod revisada (§7.2) |
