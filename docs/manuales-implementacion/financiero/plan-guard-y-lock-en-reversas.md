# Plan: guard y lock en `revertir` y en las anulaciones de entrada varia y operacion financiera (issue #376, punto 2)

Rama: `fix/financiero-guard-y-lock-en-reversas` (desde `origin/develop` 760022c2).
Este archivo es registro de trabajo: se borra en el PR final.

## El problema

Un contra-movimiento se postea cada vez que alguien llama a `revertir`. Nada en `revertir` mira si el
original ya fue revertido:

- `TesoreriaService.revertir(orig, ...)` no verifica `activo` ni toma lock. Lo usan los modulos duenos
  (vale, liquidacion, finiquito, entrada varia, operacion financiera, pago CPP, verificacion de retiro).
  #373 puso el guard en `anular` (movimientos manuales) y dejo `revertir` afuera a proposito.
- `BancoLedgerService.revertir(orig, ...)` si rechaza un movimiento ya anulado, pero mira la instancia que
  le pasan —leida sin lock, posiblemente antes de que otra transaccion commitee— asi que dos anulaciones
  simultaneas pasan las dos.
- `EntradaVariaService.anular` y `OperacionFinancieraService.anular` rechazan la segunda anulacion **en
  secuencia**, pero leen el documento con `findById`, sin lock: dos simultaneas leen `anulado = false` las
  dos y cada una revierte.

Dos anulaciones simultaneas no hacen falta que sean maliciosas: alcanza con un doble clic, dos usuarios
sobre la misma pantalla, o un reintento tras una respuesta perdida mientras el original sigue corriendo.

## Alcance

Lo del «orden sugerido» punto 2: guard + lock en `revertir` (caja y banco) y en las dos anulaciones.

Entra ademas `RetiroVerificacionService.anular` (lo encontro la auditoria): chequea `anulada` sobre una
lectura **anterior** al lock del retiro. Si entre dos anulaciones simultaneas alguien vuelve a verificar el
retiro, la segunda encuentra los movimientos **nuevos** —activos, asi que el guard de `revertir` no la
frena— y los revierte, dejando la verificacion nueva vigente y la caja descuadrada.

Fuera, con ciclo propio: la pata suelta de una transferencia entre cajas, y `cancelarRetiro` /
`cancelarGasto` como interruptores (los otros dos items del bloque 2 de la issue).

## Diseno

El guard va en el **punto de estrangulamiento**, no en cada llamador: asi cubre a los que existen y a los
que vengan.

### `TesoreriaService.revertir`

0. Movimiento sin id → rechazo: no se puede bloquear ni referenciar lo que no esta persistido.
1. `movimientoRepository.lockById(orig.getId())` — `SELECT ... FOR UPDATE` sobre el original. El resultado
   no se usa: lo que importa es haber esperado el lock.
2. **Despues** del lock, releer `activo` de la base con una proyeccion JPQL nueva
   (`select coalesce(m.activo, true) ...`, `Optional<Boolean> findActivoById`), no de la entidad: si el
   llamador ya la tenia cargada —y con open-in-view el contexto vive toda la request— `lockById` espera el
   lock pero devuelve esa misma instancia sin refrescar (el gotcha que ya documenta
   `PagoRepository.findEstadoById`). El vacio cae al valor de la entidad (`.orElse(...)`), como en
   `anularPagoCpp`. No se usa `entityManager.refresh`: pisa cambios sin flushear y obliga a cambiar el
   constructor del servicio.
3. `activo = false` → `GraphQLException("El movimiento #N ya está anulado.")`, el mismo texto de `anular`.
   `activo` nulo cuenta como activo, igual que en `anular` y en `registrar`.
4. Lo demas, como hoy.

La JPQL de la proyeccion dispara el flush de lo pendiente: dos `revertir` del mismo movimiento dentro de una
misma transaccion tambien se cortan en el segundo.

`TesoreriaService.anular` ya lockea y chequea antes de llamar a `revertir`: el segundo lock es de la misma
transaccion (no espera) y el chequeo se repite sin efecto.

### `BancoLedgerService.revertir`

Lo mismo: `lockById` nuevo en `MovimientoBancarioRepository` + proyeccion `findAnuladoById`
(`coalesce(m.anulado, false)`: la columna admite nulo), y el rechazo que ya existe pasa a mirar el valor
releido. Su texto no cambia («El movimiento bancario #N ya está anulado», sin punto): no se unifican los
mensajes en este PR.

### `EntradaVariaService.anular` y `OperacionFinancieraService.anular`

`lockById` nuevo en cada repositorio en lugar de `findById`, y `anulado` releido con proyeccion despues del
lock. Con el guard de `revertir` la plata ya no se mueve dos veces; el lock del documento hace que la
segunda anulacion reciba el mensaje del documento («ya está anulada») en vez del de un movimiento, y que
no corra en paralelo con la primera.

Las patas de una operacion financiera se revierten hoy en el orden que devuelva una consulta sin
`ORDER BY`. Pasan a un orden fijo (caja ascendente, despues cuenta ascendente), que es el de
`OperacionFinancieraService.registrar`.

### `RetiroVerificacionService.anular`

Despues del lock del retiro, releer `anulada` con una proyeccion (`findAnuladaById`) y rechazar con el
mensaje que ya existe («La verificación ya está anulada»).

### Orden de locks

Documento → movimiento → saldo (caja) / cuenta (banco). Hoy nadie hace `SELECT ... FOR UPDATE` sobre un
movimiento salvo `TesoreriaService.anular` (#373), asi que el lock nuevo solo compite con otra reversa del
mismo movimiento. Ademas ordena lo que hoy `revertir` hace al reves (saldo y despues `UPDATE` del original).

**Inversiones que ya existen y este PR no toca** (PostgreSQL aborta una de las dos transacciones; no
corrompen datos):

- `anularPagoCpp` toma los saldos y **despues** las solicitudes; `procesarEvento` al reves.
- `anularPagoCpp` revierte los detalles en el orden en que el cliente mando las lineas, no caja → cuenta.
- `anularPagoCpp` directo va pago → documento de RRHH; `AnulacionPagoRrhhService` va documento → pago.
- Cada `registrar` en Gs/Rs/Ds actualiza ademas la fila `caja_virtual` (el shim): dos monedas de una misma
  caja tomadas en orden inverso por dos transacciones se traban.

### Que cambia para quien llama

Un segundo `revertir` del mismo movimiento ahora **lanza** en vez de postear otro contra-movimiento. Para
todos los llamadores eso es un rollback de su anulacion, que es lo correcto: su documento no cambia.
Consecuencia a tener presente: un documento vivo cuyo movimiento ya esta inactivo (dato inconsistente) deja
de poder anularse por el camino normal — antes se «anulaba» revirtiendo la caja por segunda vez.

Sin migracion, sin cambio de schema GraphQL, sin datos nuevos. Las tablas son solo del central.

## Tabla de datos nuevos

No hay columnas, campos ni claves nuevas. Solo consultas: `lockById` en tres repositorios, cinco
proyecciones (`findActivoById`, `findAnuladoById` ×3, `findAnuladaById`) y dos finders ordenados.

## Fases (un PR)

1. **`revertir` de caja y de banco.**
   - `TesoreriaServiceTest`: revertir dos veces → la segunda lanza y no postea; entidad `activo = true` con
     proyeccion `false` → lanza (es el que prueba la relectura); proyeccion vacia → usa la entidad; sin id →
     rechazo; `anular` sigue pasando.
   - `BancoLedgerServiceTest` nuevo: entidad `anulado = false` con proyeccion `true` → lanza. («Revertir
     dos veces» no sirve aca: con el codigo viejo ya pasaba, por el flag en memoria.)
   - Siguen en verde sin tocarlos, por el fallback a la entidad: `ContraAsientoRrhhTest` y
     `LiquidacionSueldoNetoNegativoTest`, que usan el `TesoreriaService` real con repos mock.
2. **Anulacion de entrada varia, de operacion financiera y de verificacion de retiro.**
   - `EntradaVariaServiceTest` nuevo y `OperacionFinancieraServiceTest`: documento cuya proyeccion dice
     anulado → rechazo **con su mensaje** y `verify(..., never()).revertir`; camino feliz por `lockById`;
     patas revertidas en orden (`InOrder`). Los dos tests de `anular` que ya existen stubean `findById`: se
     reescriben (el de «ya anulada» quedaria en verde por «no encontrada»).
   - Verificacion de retiro: proyeccion dice anulada despues del lock → rechazo sin revertir.
3. **Tests de integracion** (Postgres real, `-Dit.financiero=true`; no corren en CI):
   - en `FinancieroFixesIT`, determinista: cargar el movimiento, marcarlo inactivo con un `UPDATE` nativo
     (otra transaccion ya commiteada) y `revertir` → lanza. Falla con el codigo viejo y con una version que
     lea la entidad;
   - `ReversasConcurrentesIT` nuevo, con el molde de `IdempotenciaIT`: dos hilos anulan la misma entrada
     varia → un exito, un solo contra-movimiento, el saldo igual al inicial; y dos hilos sobre
     `revertirMovimiento` sin lock de documento → lo mismo, por el guard solo.

Cada test de bug se corre con el fix neutralizado para ver que falla.

## Prueba de runtime (central local, perfil `dev`, replicacion apagada)

- Entrada varia: N anulaciones **simultaneas** de la misma → una pasa, las demas rechazo; un solo
  contra-movimiento; el saldo vuelve exactamente al de antes.
- Operacion financiera (deposito bancario: una pata de caja y una de banco): idem; un contra por pata.
- Vale pagado por el atajo de caja y liquidacion: anulaciones simultaneas → un solo contra-movimiento. La
  segunda recibe el mensaje **del movimiento** («El movimiento #N ya está anulado.»), no el del documento:
  esos `anular` no lockean el documento. Es lo esperado.
- Pago CPP: `anularPagoCpp` simultaneo sigue dando una sola reversa (ya lockeaba el pago).
- Anulacion normal, de a una, de cada documento: sin cambios.
- Log sin deadlocks.

## Despliegue y rollback

Solo central. Sin migracion: rollback del JAR inocuo para el esquema (y reabre la doble reversa). Ningun
cliente cambia: el desktop muestra el rechazo y no compara su texto; la PWA y el filial no tienen estas
operaciones. Los dialogos de vale, liquidacion y finiquito del desktop avisan el rechazo pero no releen la
fila, igual que hoy. Requiere reinicio del central (workflow Deploy).

**Puerta antes de desplegar a cada instancia** (solo lectura): documentos vivos cuyo movimiento ya esta
inactivo. Con el guard dejan de poder anularse —antes se «anulaban» devolviendo la plata otra vez— y hay que
corregir el dato antes, no despues:

- vales `CONFIRMADO`, liquidaciones y finiquitos `PAGADA` con `movimiento_caja_virtual_id` → movimiento
  `activo = false`;
- entradas varias no anuladas con su movimiento inactivo;
- `pago_solicitud_detalle` no anulado de un pago `CONCLUIDO` cuyo movimiento de caja esta inactivo o cuyo
  movimiento bancario esta anulado (un pago asi no tendria forma de anularse);
- cheques `COBRADO` cuyo movimiento bancario esta anulado.

Se eligio **rechazar** y no tratar «ya revertido» como no-op: un documento trabado se arregla con SQL; una
anulacion que da exito sin devolver la plata no se ve.

## Queda sin verificar

- La puerta de arriba **no se corrio en produccion**. En la copia local: 0 vales, 0 finiquitos, 0 entradas
  varias, 0 detalles de pago y 1 liquidacion, de pruebas.
- Sin `lock_timeout`: una reversa que espera detras de una transaccion colgada retiene su conexion, igual
  que los `lockById` que ya existen. Se acepta.
- `RetiroVerificacionGraphQL` (resolver un caso anulando la verificacion) guarda el caso y **despues** anula:
  si la anulacion se rechaza, el caso queda resuelto. Ya pasaba; es el punto 3 de la issue, no de este PR.
- No corrige reversas dobles ya ocurridas. Se detectan como dos contra-movimientos con el mismo
  `origen_id` (`origen_tipo = 'ANULACION'`). En la copia local hay 1, de pruebas; produccion no se miro.
- El bloqueo real del `SELECT ... FOR UPDATE` solo se comprueba en runtime.

## Auditoria del plan (paso 5, 2026-10-08)

Dos auditores. Una sola diferencia entre ellos, resuelta leyendo el codigo: el de contrato dio por cubierta
la verificacion de retiro; el de estado mostro la carrera con una re-verificacion en el medio. Entra.

| Hallazgo | Que se hizo |
|---|---|
| `RetiroVerificacionService.anular` chequea `anulada` antes del lock del retiro | entra al alcance |
| La proyeccion devuelve vacio tanto para «sin fila» como para un nulo; tests con repos mock | `coalesce` en la JPQL y fallback a la entidad |
| «Revertir dos veces» en banco pasa con el codigo viejo; el test de «ya anulada» quedaria verde por «no encontrada» | tests de divergencia entidad/proyeccion y mensaje asertado |
| Nada con mocks ejercita el lock | dos tests de integracion (uno determinista, uno de dos hilos) |
| Patas de una operacion revertidas sin orden fijo | finders ordenados |
| `revertir` de un movimiento sin id postearia un contra sin referencia | rechazo |
| Documentos vivos con movimiento inactivo: solo se midieron vales y liquidaciones | puerta de despliegue con todos los tipos |
| Rechazar o no-op | se rechaza; motivo arriba |
| Inversiones de orden de locks preexistentes | anotadas, fuera de alcance |
| Faltaban llamadores en la lista | cheque al dia dentro de un pago (`anularPorPago`) y `AnulacionPagoRrhhService`: los dos hacen rollback completo si la reversa se rechaza |
