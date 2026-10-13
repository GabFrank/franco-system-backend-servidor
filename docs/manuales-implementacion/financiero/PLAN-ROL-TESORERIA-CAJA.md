# Plan — Rol `TESORERIA CAJA` (operador de caja acotado)

- **Fecha:** 2026-10-13
- **Ramas:** `feature/tesoreria-rol-caja` en central y en desktop, las dos desde `develop`.
- **Piezas:** central + desktop. Filial, mobile y mobile-pwa: N/A (ver §8).
- **Estado:** auditado (paso 5, §12) y aprobado el 2026-10-13, con D1 = opción A.

## 1. Qué se pide

Dar a los gerentes de sucursal acceso a la tesorería **solo de las cajas de las que son
encargados**, sin que vean información financiera del resto de la empresa: saldo consolidado,
saldos y extractos bancarios, cheques, operaciones financieras ajenas, deuda con proveedores.

Sobre sus cajas el gerente tiene que poder:

1. ver la caja, sus saldos y sus movimientos;
2. registrar ingresos y egresos;
3. transferir entre sus cajas;
4. ingresar retiros del PDV;
5. pagar gastos y proveedores;
6. depositar al banco.

## 2. Por qué un rol nuevo

Hoy cualquier rol de tesorería (`TESORERIA VER`, `GESTIONAR`, `CPC COBRAR`, `CPP PAGAR`) pasa
`TesoreriaSecurityService.requireVer()`, y esa llamada es lo único que exigen los reportes, los
extractos, los cheques y las operaciones financieras. El ACL por caja
(`financiero.caja_virtual_acceso`) solo delimita el efectivo de la caja. Ocultar tarjetas en el
dashboard no alcanza: la misma información sale por otras pantallas.

El rol nuevo **no entra en `TODOS`**, así que queda rechazado por defecto en todo resolver que
hoy llama `requireVer()`, `requireGestionar()` o `requirePagarCpp()`. Se lo habilita
explícitamente, uno por uno, solo en los resolvers delimitados por caja. **Ningún método de
seguridad existente cambia de significado**: se agregan métodos nuevos.

## 3. Diseño

### 3.1 Seguridad (`TesoreriaSecurityService`)

| Elemento | Definición |
|---|---|
| `CAJA = "TESORERIA CAJA"` | Constante del rol. No se agrega a `TODOS`. |
| `requireVerCaja()` | Acepta `TODOS` + `CAJA`. Lecturas delimitadas por caja. |
| `requireOperarCaja()` | Acepta `GESTIONAR` o `CAJA`. Escrituras que pasan por `TesoreriaService`, que ya exige `requireEscrituraCaja`. |
| `requirePagarCppCaja()` | Acepta `CPP_PAGAR`, `GESTIONAR` o `CAJA`. Se usa **solo** en `pagarSolicitudesMixto` y `anularPagoCpp`. `requirePagarCpp()` no se toca. |
| `esSoloCaja()` | `true` si el usuario tiene `CAJA`, no es superusuario y no tiene ningún rol de `TODOS`. Sin principal devuelve `false`: no imita el fail-open de `esProcesoDeSistema()`. |
| `sucursalesOperablesIds()` | Sucursales no nulas de las cajas con **escritura** (responsable o `puede_escribir`). Nunca `null`: lista vacía significa «ninguna». |

Un usuario con `CAJA` y además un rol amplio se comporta como el rol amplio.

**Regla de ubicación.** Toda validación del modo acotado va en el **servicio**, en las primeras
líneas del método `@Transactional`, antes del primer lock y del primer asiento. Si fuera en el
resolver, otro camino al mismo servicio la esquivaría; si fuera después de un asiento, dejaría la
operación a medias.

**Regla de listas.** En modo acotado nunca se usa `null` como «sin filtro». Con la lista de
sucursales o de cajas vacía, el servicio devuelve `Page.empty` / lista vacía sin consultar. Las
consultas con `IN` reciben un flag booleano explícito y nunca una lista vacía.

### 3.2 Qué habilita el rol, por capacidad

**Ver y mover efectivo (capacidades 1 a 3).** Solo cambia la llamada de rol; el ACL ya existe.

| Resolver | Hoy | Queda |
|---|---|---|
| `cajaVirtual`, `cajaVirtuales`, `cajaVirtualesFilter`, `cajaVirtualesPorTipo`, `cajaVirtualesPorSucursal`, `cajaVirtualSaldos` | `requireVer` | `requireVerCaja` |
| `cajaVirtualesActivas` | tesorería o RRHH | se suma `CAJA` |
| `movimientosCajaVirtual`, `…PorFecha`, `…Filter`, `reporteMovimientosCaja*` | `requireVer` | `requireVerCaja` |
| `movimientoCajaVirtual(id)` | `requireVer` | `requireVerCaja` + `requireLecturaCaja` de la caja del movimiento |
| `entradasVarias`, `entradaVariaCategorias`, `operacionFinancieraCategorias` | `requireVer` | `requireVerCaja` |
| `registrarMovimientosCajaVirtual` | `requireGestionar` | `requireOperarCaja`; en modo acotado solo `INGRESO` y `EGRESO` (rechaza `AJUSTE`) |
| `anularMovimientoCajaVirtual`, `realizarTransferencia(s)CajaVirtual`, `registrarEntradaVaria`, `anularEntradaVaria` | `requireGestionar` | `requireOperarCaja` |

Quedan **fuera** del rol, sin cambios: `saveMovimientoCajaVirtual` (acepta cualquier tipo de
movimiento y un `usuarioId` arbitrario), crear, editar o borrar cajas, administrar accesos,
configurar la caja (`cajaVirtualConfiguracion` y `saveCajaVirtualConfiguracion`), ajuste por
conteo, categorías de entrada varia, maletines.

**Saldos bancarios: guard a nivel de campo.** `CuentaBancaria.saldo` y `saldoReservado` se
exponen como campo anidado en varios tipos (`OperacionFinanciera.cuentaBancariaOrigen/Destino`,
`CajaVirtualConfiguracion.cuentasBancariasVisibles`, respuestas de mutations). En vez de perseguir
cada camino, se agrega un `GraphQLResolver<CuentaBancaria>` que devuelve `null` en esos dos campos
cuando `esSoloCaja()`. El resultado de `esSoloCaja()` se resuelve una vez por request.

**Retiros del PDV (capacidad 4).**

- `retirosFlotantes`: `requireVerCaja`. En modo acotado la sucursal pedida tiene que estar en
  `sucursalesOperablesIds()`; sin sucursal pedida se usan todas las operables. `findFlotantes`
  gana una variante con `sucursalId IN`, filtrando **en la consulta**.
- `ingresarRetiroACajaMayor`: `requireOperarCaja`. En modo acotado la sucursal del retiro tiene
  que ser la de la caja destino. La escritura sobre la caja ya la exige el procesador
  (`RetiroTesoreriaProcesador` → `tesoreriaService.registrar`, verificado).
- Verificación de retiros y casos (`Control de retiros`): fuera del rol.

**Depósito al banco (capacidad 6).**

- `registrarOperacionFinanciera`: `requireOperarCaja`. En modo acotado solo `DEPOSITO_BANCARIO` y
  `TRANSFERENCIA_ENTRE_CAJAS`, y la cuenta destino del depósito tiene que estar en las cuentas
  visibles de la configuración de la caja origen.
- `anularOperacionFinanciera`: `requireOperarCaja`. En modo acotado solo operaciones de esos dos
  tipos cuya caja origen sea escribible por el usuario.
- **Query nueva `cuentasBancariasParaDeposito(cajaVirtualId): [CuentaBancaria]`**: las cuentas
  visibles de la caja. `requireVerCaja` + lectura de la caja. El saldo sale `null` por el guard
  de campo.
- Quedan con `requireVer`, sin cambios: `operacionFinanciera(id)`, `operacionesFinancieras`,
  `cajaVirtualResumenBancario`, `movimientosBancarios`. El gerente no los ve.
- **Cambio de contrato:** las lecturas de `CuentaBancariaGraphQL` exigen `requireVer` y sus
  escrituras `requireGestionar`, como el resto del módulo. Sus
  consumidores en el desktop son todos pantallas de tesorería (cuenta bancaria, chequera,
  configurar caja, operación financiera, pagar compras); se re-grepea sobre `origin/develop`
  antes de F3.

**Pagos (capacidad 5).**

- `crearGastoParaPago`: `requireOperarCaja`.
- `gastosPendientes`: `requireVerCaja`. En modo acotado solo los gastos que **creó el propio
  usuario** (`SolicitudPago.usuario`), con un método de repositorio nuevo (hoy se filtra en
  memoria).
- `pagarSolicitudesMixto`: `requirePagarCppCaja`. En modo acotado, validado en un primer recorrido
  antes de procesar la primera línea: todas las líneas de fuente `CAJA_MAYOR` o `AJUSTE`, nunca
  `CUENTA_BANCARIA` ni `CHEQUE`; y cada solicitud visible para el usuario con la regla del
  listado.
- `anularPagoCpp`: `requirePagarCppCaja`. En modo acotado, antes de revertir: escritura sobre
  **todas** las patas de caja del evento y ninguna pata de banco o cheque.
- `detalleDePago`: `requireVerCaja`. En modo acotado un pago sin ninguna pata de caja visible
  devuelve vacío.
- Quedan con `requirePagarCpp`, sin cambios: `pagarSolicitud`, `pagarSolicitudesLoteCajaMayor`,
  `devolverSolicitudPago`, `AnulacionPagoRrhhService` (sueldos, finiquitos).
  `solicitudesPagoPendientes` queda con `requireVer` hasta D1.
- Pagos de RRHH: fuera del rol.
- **Proveedores:** las solicitudes asignadas a una de sus cajas (D1, §4).

### 3.3 Desktop

| Lugar | Cambio |
|---|---|
| `roles.enum.ts` | `TESORERIA_CAJA = "TESORERIA CAJA"`. |
| `side-mini-variant.component.ts` | Se suma el rol al grupo `Financiero`, al subgrupo `Caja y Operativa` y al ítem `Tesorería` (`list-caja-virtual`), y a su `case`. No se suma a `Dashboard`, `Operaciones Financieras`, `Control de retiros`, `Cuentas y Bancos` ni `Compras`. |
| `caja-virtual-dashboard` | Flag `puedeOperar` (`GESTIONAR` o `CAJA`) para Ingreso, Egreso, Operación financiera y Anular. `puedeGestionar` sigue gateando Configurar, conteo y escaneo. Flag `soloCaja`: no pide la configuración ni el resumen bancario, no muestra las cards de cuentas ni el selector de extracto, no abre el detalle de una operación financiera. |
| `registrar-egreso-dialog` / `pagar-compras-dialog` | En `soloCaja`: solo el modo Gastos (y Compras según D1); la única fuente es la caja; no se consultan cuentas, chequeras ni pendientes de RRHH. |
| `add-operacion-financiera-dialog` | En `soloCaja`: solo Depósito bancario y Transferencia entre cajas; las cuentas salen de `cuentasBancariasParaDeposito`. |
| `transferencia-caja-virtual-dialog` | Sin cambios: `cajaVirtualesActivas` ya viene filtrada por el ACL. Una caja con solo lectura aparece como destino y el central la rechaza con su mensaje. |
| `ingresar-retiro-caja-mayor-dialog` | En `soloCaja`: el selector de sucursal se limita a las sucursales de sus cajas. |

Todos los flags se calculan en `ngOnInit` con `mainService.tieneAlgunRol`; ninguna función en el
template. Toda consulta nueva o condicionada maneja el error con `catchError` y estado «no
disponible»: un central que todavía no tiene el cambio no puede dejar la pantalla colgada.

## 4. Decisión D1 — resuelta: opción A

**D1 — Qué solicitudes de pago a proveedores ve y paga el gerente.** `SolicitudPago` no tiene
sucursal ni caja, y las de compra las crea el área de compras, no el gerente. Opciones:

| Opción | Qué ve el gerente | Costo |
|---|---|---|
| **A (recomendada)** | Las que tesorería le **asigna a una de sus cajas**. Columna nueva `solicitud_pago.caja_virtual_asignada_id` (nullable) y una acción «Asignar a caja» en la lista de solicitudes de pago. | Migración aditiva + una acción en una pantalla existente. |
| B | Las de mercadería recibida en la sucursal de sus cajas (`RecepcionMercaderia.sucursalRecepcion`). | Sin datos nuevos, pero la cadena solicitud → nota → recepción no está verificada y una solicitud puede cubrir varias sucursales. |
| C | Ninguna: paga proveedores solo cargándolos como gasto. | Cero. |

Elegida **A** (2026-10-13). Va como fase aparte (F5), para que el resto no dependa de ella.

## 5. Migraciones

`V243.1` y `V244.3` ya están ocupadas en `develop`. Flyway corre `out-of-order`, pero se numera
por encima del máximo para no depender de eso.

| Archivo | Contenido | Espejo en filial |
|---|---|---|
| `V245.1__seed_rol_tesoreria_caja.sql` | `INSERT … WHERE NOT EXISTS` por `upper(trim(nombre))` en `personas.role`, patrón de `V237.3`. No se lo otorga a nadie. | No: `personas.role` está publicada y llega por replicación. |
| `V246.1__solicitud_pago_caja_asignada.sql` (solo D1-A) | `ADD COLUMN IF NOT EXISTS caja_virtual_asignada_id bigint NULL`, FK en bloque `DO` idempotente y **sin** `ON DELETE CASCADE`, `CREATE INDEX IF NOT EXISTS`. | Solo si `solicitud_pago` está publicada. No aparece en ninguna publicación de las migraciones; se confirma con `pg_publication_tables` **antes** de empezar F5. |

Los números se reverifican contra `origin/develop` y las ramas abiertas antes de cada push y
después de cada rebase. Las dos son aditivas; `solicitud_pago` tiene PK simple (`id`) y una
columna nullable no afecta al backend anterior, así que el rollback del JAR sigue funcionando.

## 6. Datos nuevos: quién escribe y quién lee

| Dato | Lo escribe | Lo lee |
|---|---|---|
| Fila `TESORERIA CAJA` en `personas.role` | `V245.1` | `TesoreriaSecurityService` (central); `mainService.tieneAlgunRol` y el menú (desktop). Se asigna por la pantalla de usuarios existente. |
| Query `cuentasBancariasParaDeposito` | `CajaVirtualGraphQL` (central) | `add-operacion-financiera-dialog` (desktop). |
| `solicitud_pago.caja_virtual_asignada_id` (D1-A) | Mutation `asignarSolicitudPagoACaja` desde la lista de solicitudes de pago (desktop, `GESTIONAR`). | `PagoProveedorService.listarPendientes` en modo acotado; `pagar-compras-dialog`. |

## 7. Fases

Una fase = un commit + un push de la rama. El central va primero: el desktop depende de la query
nueva y de que el central acepte el rol.

### Central

| Fase | Contenido | Tests |
|---|---|---|
| **F1** | `V245.1`; métodos nuevos de `TesoreriaSecurityService`; lecturas y efectivo de caja (§3.2, primer bloque). | `TesoreriaSecurityServiceTest`: `esSoloCaja` (solo `CAJA`; `CAJA`+`VER` = falso; superusuario = falso; sin principal = falso), `requireVerCaja`, `requireOperarCaja`. Matriz negativa con solo `CAJA`: `saldoConsolidadoTesoreria`, `agingCppTesoreria`, `proximosVencimientosTesoreria`, `operacionesFinancieras`, `operacionFinanciera`, `movimientosBancarios`, `cheques`, `chequesDashboard`, `cajaVirtualResumenBancario`, `cajaVirtualConfiguracion`, `saveCajaVirtualConfiguracion`, `saveMovimientoCajaVirtual`, `saveCajaVirtual` lanzan «No autorizado». `registrarMovimientosCajaVirtual` con `AJUSTE` en modo acotado: rechazado. Usuario `CAJA` sin ninguna caja: los listados devuelven vacío, no todo. **Lista cerrada:** un test recorre el código de `graphql/` y falla si `requireVerCaja`, `requireOperarCaja` o `requirePagarCppCaja` aparecen en un resolver que no está en la lista explícita del test. |
| **F2** | Retiros acotados por sucursal. | Modo acotado: no devuelve retiros de otra sucursal y el total de la página es coherente; sin sucursales operables devuelve página vacía; `ingresarRetiroACajaMayor` rechaza un retiro de otra sucursal. `GESTIONAR` sin cambios. |
| **F3** | Guard de campo de saldo; depósito acotado; `cuentasBancariasParaDeposito`; guards de `CuentaBancariaGraphQL`. | Solo `CAJA`: `saldo` y `saldoReservado` salen `null` por cada camino anidado (query nueva, respuesta de `registrarOperacionFinanciera`). Depósito a cuenta no visible de la caja: rechazado **sin** asiento en la caja. Tipos no permitidos: rechazados. Anular operación ajena: rechazado. Usuario sin rol de tesorería: lecturas de cuenta bancaria rechazadas. |
| **F4** | Gastos: crear, listar los propios, pagar solo en efectivo, anular, detalle. | Gasto de otro usuario no aparece ni se puede pagar; línea `CUENTA_BANCARIA` o `CHEQUE` rechazada sin procesar ninguna línea; `anularPagoCpp` de un pago con pata bancaria o de caja ajena: rechazado sin revertir nada. Negativos con solo `CAJA` para `pagarSolicitud`, `pagarSolicitudesLoteCajaMayor`, `devolverSolicitudPago` y la anulación de pagos de RRHH. `GESTIONAR` sigue viendo y pagando todo. |
| **F5** (D1-A) | `V246.1`; `asignarSolicitudPagoACaja`; `listarPendientes` acotado. | Solicitud asignada a otra caja no aparece ni se paga; sin asignar no aparece en modo acotado. |

Build por fase: `./mvnw clean verify -B -DskipFlyway=true`, leído del log. Las migraciones se
prueban aparte contra una copia de la base (el CI no las valida).

### Desktop

| Fase | Contenido | Verificación |
|---|---|---|
| **F6** | Rol, menú, `caja-virtual-dashboard` en modo acotado. | Usuario de prueba con solo `TESORERIA CAJA` y acceso a una caja: ve una sola entrada de menú, una sola caja, sin cards bancarias. Mismo usuario contra un central sin el cambio: mensaje de «no autorizado», sin pantalla colgada. |
| **F7** | Diálogos de egreso y pago, operación financiera, retiros (y asignación a caja si D1-A). | Recorrido manual de las 6 capacidades con ese usuario; y con un usuario `GESTIONAR` para confirmar que nada cambió. |

`npm run check` (AOT) al final de F7, antes del push; `verificar:imports` y `verificar:arranque`
según el CLAUDE.md del desktop.

## 8. Alcance por pieza

- **Filial:** N/A. La tesorería es solo de central (DA1) y `personas.role` llega por replicación.
  Excepción a confirmar antes de F5: el espejo de `V246.1` si `solicitud_pago` está publicada.
- **mobile-pwa y mobile (Android):** N/A. Ninguna consume estas queries (grep sobre `src/` sin
  resultados).
- **Enums:** no se agrega ningún valor a un enum de Java. La lista de roles vive solo en
  `personas.role` y en `roles.enum.ts`.
- **Entorno:** sin variables ni carpetas nuevas.
- **Compatibilidad:** no se elimina ni renombra ningún campo o query. Un desktop viejo contra el
  central nuevo funciona igual para los roles actuales; un usuario `TESORERIA CAJA` con desktop
  viejo no ve el menú. Un desktop nuevo contra un central viejo solo afecta a usuarios con el rol
  nuevo, que reciben «No autorizado».
- **Cambio de comportamiento para roles existentes:** uno solo, los guards de
  `CuentaBancariaGraphQL` se unifican con los del módulo (§3.2).

## 9. Operación

- El gerente **no tiene que ser el responsable** (`caja_virtual.usuario_id`) de la caja: el
  responsable administra la lista de accesos. La caja la crea tesorería y al gerente se le otorga
  acceso desde «Gestionar accesos».
- El acceso se otorga **siempre con lectura y escritura**. Con solo escritura la caja no aparece
  en los listados (`cajasVisiblesIds` mira `puede_leer`).
- Para que el gerente pueda depositar, tesorería marca en «Configurar» de la caja las cuentas
  bancarias destino.
- Para que vea retiros, la caja tiene que tener sucursal.

## 10. Sin verificar

| Qué | Cómo se verifica |
|---|---|
| Sitio exacto, línea por línea, de cada validación en `OperacionFinancieraService.registrar/anular` y `PagoProveedorService.pagarLoteMixto/anularPagoCpp` | Lectura completa al empezar F3 y F4; los tests «rechazado sin asiento» lo cubren. |
| Que un `GraphQLResolver<CuentaBancaria>` tome precedencia sobre el getter de la entidad en esta versión de kickstart | Primer test de F3; si no, se cambia a un tipo liviano sin saldo para la query nueva y se cierran los caminos anidados uno por uno. |
| Consumidores de las queries de cuenta bancaria sobre `origin/develop` del desktop | `git grep` antes de F3 (el barrido de la auditoría fue sobre otra rama). |
| Si `operaciones.solicitud_pago` está en una publicación creada a mano | `pg_publication_tables` en la base local y en una filial antes de F5. |
| Numeración `V245.1` / `V246.1` libre | `git ls-tree` de `origin/develop` y ramas abiertas antes de cada push. |
| Recorrido completo en runtime con central y desktop locales | Paso 9 del ciclo, con un usuario de prueba nuevo en la base local. |

## 11. Fuera de alcance

- Cambiar lo que ven hoy `TESORERIA VER` / `GESTIONAR` / `CPC COBRAR` / `CPP PAGAR`.
- Filtrar el saldo consolidado por cajas visibles.
- ACL por cuenta bancaria.
- Exigir ser responsable para `saveCajaVirtualConfiguracion`: rompería a los `GESTIONAR` que hoy
  configuran cajas ajenas, y el rol nuevo no puede llamarla.
- Cobros de CPC desde la caja.

## 12. Auditoría del plan (paso 5)

Dos auditores independientes sobre la primera versión. No se contradijeron.

| # | Eje | Hallazgo | Severidad | Qué se hizo |
|---|---|---|---|---|
| 1 | A y B | `V243.1` ya existe en `develop` y `V244.1` quedaría por debajo de `V244.3`. | Crítico | Renumerado a `V245.1` / `V246.1` (§5). |
| 2 | A | Ampliar `requirePagarCpp` abría `pagarSolicitud`, `pagarSolicitudesLoteCajaMayor`, `devolverSolicitudPago` y la anulación de pagos de RRHH. | Crítico | Método aparte `requirePagarCppCaja`, usado en dos mutations; tests negativos para las demás (§3.1, §3.2, F4). |
| 3 | B | Lista vacía o `null` podía significar «ve todo» en modo acotado; `esSoloCaja` sin principal. | Crítico | Regla de listas, `sucursalesOperablesIds` nunca `null`, `esSoloCaja` falso sin principal, test de usuario sin cajas (§3.1, F1, F2). |
| 4 | A | El saldo bancario se filtraba por campos anidados (`cuentasBancariasVisibles`, `cuentaBancariaOrigen/Destino`). | Alto | Guard a nivel de campo; `cajaVirtualConfiguracion` y `operacionFinanciera(id)` quedan fuera del rol (§3.2, F3). |
| 5 | A | Las mutations de movimiento aceptan `AJUSTE` y `saveMovimientoCajaVirtual` un `usuarioId` arbitrario. | Alto | `saveMovimientoCajaVirtual` queda fuera del rol; el lote rechaza `AJUSTE` en modo acotado (§3.2). |
| 6 | B | Las validaciones no tenían ubicación: riesgo de operación a medias o de esquivarlas por otro camino. | Alto | Regla de ubicación en el servicio, antes del primer lock (§3.1); tests «rechazado sin asiento». |
| 7 | B | Faltaban tests de regresión y no hay test del ACL de cajas. | Alto | Tests agregados por fase y test de lista cerrada (§7). |
| 8 | B | Migración de columna sin idempotencia y con riesgo de `CASCADE`. | Alto | `IF NOT EXISTS`, FK en bloque `DO`, sin cascade (§5). |
| 9 | A | «Los roles actuales no cambian» era falso en dos puntos. | Medio | Se quitó el requisito de responsable en la configuración (§11) y se declaró el cambio de los guards de cuenta bancaria (§8). |
| 10 | B | `cajasVisiblesIds` mira solo `puede_leer`: un gerente con solo escritura no ve su caja. | Medio | Invariante operativa: acceso siempre con lectura y escritura (§9). |
| 11 | B | Desktop nuevo contra central viejo podía quedar colgado. | Medio | `catchError` obligatorio y prueba explícita en F6 (§3.3, §7). |
| 12 | A | `gastosPendientes` filtra en memoria. | Medio | Método de repositorio nuevo (§3.2). |
| 13 | A | En los selectores de destino aparecen cajas con solo lectura. | Bajo | Aceptado: el central rechaza con mensaje claro (§3.3). |
