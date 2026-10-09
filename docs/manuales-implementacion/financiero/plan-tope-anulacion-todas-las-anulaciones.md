# Plan — el tope de antigüedad vale para toda anulación (issue #370)

Rama: `fix/financiero-tope-anulacion-todas-las-anulaciones` (desde `origin/develop` `0ab2de3b`).
Pieza: solo **central**. Sin migración, sin cambio de schema GraphQL, sin cambio en clientes.

## Problema

`ConfiguracionGeneral.diasLimiteAnulacion` (CN4) solo se consulta en `TesoreriaService.anular`
(movimiento MANUAL / MALETIN) y en `TesoreriaService.anularTransferencia`. Toda otra anulación
llega a `TesoreriaService.revertir` o a `BancoLedgerService.revertir`, que no miran la fecha.

Anulaciones que hoy saltean el tope (verificado con `grep` de los llamadores de los dos `revertir`):

| Entrada | Llega por |
|---|---|
| `PagoProveedorService.anularPagoCpp` (compras, gastos, RRHH del hub) | caja, banco y `ChequeGestionService.anularPorPago` |
| `AnulacionPagoRrhhService.anularLiquidacion` / `anularFiniquito` | `anularPagoCpp` |
| `OperacionFinancieraService.anular` | caja y banco |
| `EntradaVariaService.anular` | caja |
| `RetiroVerificacionService.anular` (y `RetiroCasoService`, que la llama al resolver un caso) | caja |
| `LiquidacionSueldoService.anular`, `LiquidacionFinalService.anular`, `ValeService` (pago por el atajo viejo, sin solicitud) | `MovimientoCajaVirtualService.revertirMovimiento` → caja |

## Estado en producción

`dias_limite_anulacion` es `NULL` en bodega y farmacia (consultado el 2026-10-09) y no hay input
GraphQL ni pantalla que lo cargue: solo SQL. **Este cambio no altera nada en producción mientras
siga en `NULL`.** La pantalla de configuración queda fuera de alcance (issue aparte).

## Decisiones (Franco, 2026-10-09; a confirmar por Mauro/Gabriel en la issue)

1. El tope vale para **toda** anulación que postea un contra-movimiento.
2. RRHH queda **adentro**, sin excepción.
3. **Sin flujo de autorización**: rechazo liso. El mensaje pierde «Requiere autorización».

## Diseño

Dos capas, las dos contra un único dueño de la regla.

**Dueño de la regla — `LimiteAnulacionService`** (nuevo, `service/financiero`).
`requireDentroDelLimite(LocalDateTime fecha, String queCosa)`. Lee `ConfiguracionGeneralRepository`
como hoy: `null`, `0` o sin fila = sin límite; `fecha` nula = no se puede medir, pasa. Si la lectura
de la configuración falla, sigue pasando (igual que hoy) pero deja un `log.warn`: hoy se apaga en
silencio. Mensaje: `«<queCosa> supera el límite de N días para anular.»`, donde `queCosa` nombra el
documento y su fecha (`El pago #12 del 01/09/2026`).

**Capa 1 — el documento, en la entrada, antes de tocar nada.** Mide la fecha del **documento**:

| Entrada | Fecha | `queCosa` |
|---|---|---|
| `PagoProveedorService.anularPagoCpp` | `Pago.creadoEn` | `El pago #N del …` |
| `OperacionFinancieraService.anular` | `OperacionFinanciera.creadoEn` | `La operación financiera #N del …` |
| `RetiroVerificacionService.anular` | `RetiroVerificacion.creadoEn` | `La verificación #N del …` |
| `EntradaVariaService.anular` | `EntradaVaria.creadoEn` | `La entrada/salida #N del …` |

Va después del lock y del «ya está anulado» de cada servicio. Las cuatro ya tienen
`TesoreriaService` inyectado: llaman a `tesoreriaService.requireDentroDelLimiteDeAnulacion(...)`,
que delega en el servicio nuevo (no cambian sus constructores).

**Capa 2 — el movimiento, en las dos reversas (red).** `TesoreriaService.revertir` y
`BancoLedgerService.revertir` miden `orig.getCreadoEn()` después de permiso + lock + «ya está
anulado» y antes de postear. Cubre lo que no tiene capa 1 —las tres anulaciones de RRHH por el
atajo viejo— y cualquier entrada futura. `queCosa`: `El movimiento de caja #N del …` /
`El movimiento bancario #N del …`.

- `TesoreriaService`: cambia `ConfiguracionGeneralRepository` por `LimiteAnulacionService` en el
  constructor. `anular` pierde su copia del chequeo (la cubre `revertir`; el rechazo pasa a salir
  después del lock y del «ya anulado»). `anularTransferencia` conserva su chequeo previo sobre la
  pata más vieja, ahora vía el servicio.
- `BancoLedgerService`: recibe `LimiteAnulacionService` en el constructor.

Por qué las dos capas (hallazgo de los dos auditores): con solo la capa 2, un pago viejo hecho con
un cheque **cobrado hace poco** se anulaba, porque el débito bancario nace al cobrar; y el mensaje
nombraba un movimiento que el usuario de RRHH o de compras no conoce. Con solo la capa 1 se vuelve
a depender de enumerar entradas, que ya falló (la issue listó dos y son nueve).

### Consecuencias que hay que saber

- **Todas las entradas son `@Transactional`** y ningún resolver ni `catch` se traga el rechazo
  (verificado): si salta a mitad de camino, se deshace todo.
- **Cheque diferido sin cobrar**: anular su pago solo libera la reserva; lo frena la capa 1 por la
  fecha del pago. `ChequeGestionService.anular` (cheque suelto, sin pago) no postea
  contra-movimiento y queda fuera del tope.
- **Caso de retiro**: resolverlo con «error de conteo» **anulando** una verificación más vieja que
  el límite se rechaza, y el caso sigue abierto. Se puede resolver con el mismo veredicto **sin**
  anular la verificación; el mensaje nombra la verificación.
- **Liquidación, finiquito o vale** con pago más viejo que el límite: no se pueden anular. Por el
  hub el mensaje nombra el pago; por el atajo viejo, el movimiento de caja.
- No hay ningún scheduler ni proceso sin sesión que llame a `revertir` (grep, confirmado por el
  auditor A): el tope no corta procesos automáticos.

## Fases

### Fase 1 — la regla y la caja mayor
- `LimiteAnulacionService` + `LimiteAnulacionServiceTest` (sin fila, `null`, `0`, dentro, fuera,
  fecha nula, lectura que falla; el mensaje no dice «Requiere autorización»).
- `TesoreriaService`: constructor, `requireDentroDelLimiteDeAnulacion`, `revertir`, `anular`,
  `anularTransferencia`.
- Ajustar los 3 tests que construyen `TesoreriaService` a mano: `TesoreriaServiceTest`,
  `LiquidacionSueldoNetoNegativoTest`, `ContraAsientoRrhhTest`.
- Tests nuevos en `TesoreriaServiceTest` (seteando `creadoEn` a mano): `revertir` rechaza un
  movimiento de origen `PAGO_CPP` viejo y no postea nada; lo deja pasar dentro del límite.
- Commit: `fix(financiero): aplicar el tope de antiguedad a toda reversa de caja mayor`

### Fase 2 — banco
- `BancoLedgerService`: constructor + `revertir`. Ajustar `BancoLedgerServiceTest`.
- Tests: rechaza un movimiento bancario viejo sin postear ni marcarlo anulado; pasa dentro del límite.
- Commit: `fix(financiero): aplicar el tope de antiguedad a toda reversa bancaria`

### Fase 3 — el documento, en las cuatro entradas
- Capa 1 en `anularPagoCpp`, `OperacionFinancieraService.anular`, `RetiroVerificacionService.anular`
  y `EntradaVariaService.anular`.
- Tests (uno por servicio, en `PagoProveedorServiceTest`, `OperacionFinancieraServiceTest`,
  `RetiroVerificacionAnularTest`, `EntradaVariaServiceTest`): la entrada pide el chequeo con la
  fecha del documento y, si rechaza, no se llama a ningún `revertir` ni se marca nada anulado.
  En pagos, además: pago viejo con cheque → no se llega a `anularPorPago`.
- Commit: `fix(financiero): medir el tope de anulacion sobre la fecha del documento`

### Fase 4 — documentación (paso 11)
- `ARQUITECTURA-MODULO-FINANCIERO.md` (dónde vive CN4), `ESTADO-Y-PLAN-MODULO-FINANCIERO.md:284`,
  `PRUEBA-GUIADA-MODULO-FINANCIERO.md:21` (casos por pago, operación, banco y caso de retiro).
  Borrar este plan.
- Commit: `docs(financiero): el tope de anulacion vale para toda reversa`

Cada test de bug se verifica revirtiendo el fix y viéndolo fallar.

## Auditoría del plan (paso 5, 2026-10-09)

Dos auditores, sin verse. No se contradijeron. Qué se hizo con cada hallazgo:

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Un cheque cobrado hace poco deja anular un pago viejo | A y B | Aplicado: capa 1 sobre la fecha del documento |
| El mensaje nombra un movimiento que el usuario no conoce | A y B | Aplicado: `queCosa` nombra documento y fecha |
| Caso de retiro: no se puede resolver anulando una verificación vieja | A | Aceptado y documentado. El auditor decía que obligaba a un veredicto falso: no es así, el mismo veredicto se puede guardar sin anular (`RetiroCasoService.java:180-186`) |
| Los tests que construyen `TesoreriaService` son 3, no 4 | A | Corregido |
| La lectura de la config falla en silencio | A y B | Aplicado: `log.warn`, sigue pasando |
| Nada prueba el camino completo (los dueños mockean tesorería) | B | Parcial: tests por entrada en la fase 3. Un caso en `ReversasIT` queda sujeto a que la base local lo permita (ver «sin verificar») |
| Patas de antigüedad distinta en un mismo documento | B | Cubierto por la capa 1 (rechaza antes de tocar nada) y por la transacción |
| `findAll()` por cada reversa | A | No se cambia: es una fila y la tabla no tiene otra consulta |

## Datos nuevos

Ninguno: no nace columna, campo ni clave. `dias_limite_anulacion` ya existe (V184.5); lo lee
`LimiteAnulacionService`; **sigue sin escritor en la aplicación** (solo SQL) — deuda conocida, fuera de alcance.

## Migraciones

N/A para central porque no cambia el esquema. Paso 10 (dry-run de migración): N/A por lo mismo.

## Prueba de runtime (paso 9, local)

Central con perfil `dev` contra la base local + desktop `ng serve -c web`. Con
`UPDATE empresarial.configuracion_general SET dias_limite_anulacion = 5` en la base **local**:
anular un pago a proveedor, una operación financiera y un movimiento bancario de más de 5 días →
rechazo con el mensaje nuevo; uno de hoy → anula. Un pago viejo con cheque → rechazo. Volver la columna a `NULL` al terminar.

## Qué queda sin verificar

- Que Mauro/Gabriel confirmen las decisiones 1 y 2 (regla de negocio).
- `ReversasIT`, `FinancieroFixesIT` y `RetiroCasoIT` no corren en CI (apagados por system property):
  se corren en local si la base lo permite, o se anota que no se corrieron. Idem un caso nuevo de
  punta a punta en `ReversasIT` (pago viejo → `anularPagoCpp` rechaza y el pago sigue vivo).
- Cuántos movimientos bancarios tienen `creado_en` nulo en producción (la columna lo permite; pasan sin tope).
- El comportamiento con el límite **activo** en producción: no se va a ver hasta que exista la configuración.
