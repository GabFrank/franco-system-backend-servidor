# Plan — idempotencia de las mutations que quedaban (issue #376, punto 1)

Último bloque de la issue. Rama `fix/financiero-idempotencia-mutations-restantes` (central) y una rama
par en el desktop. Este archivo se borra en el commit final, antes del PR.

## Estado de la tabla del punto 1

| Mutation de la issue | Estado |
|---|---|
| `pagarSolicitudesMixto` | hecho (clave) |
| `emitirCheque` | hecho (clave) |
| `registrar` / `transferir` de caja mayor | hecho: el desktop usa `registrarMovimientosCajaVirtual` y `realizarTransferenciasCajaVirtual` (clave). La `realizarTransferenciaCajaVirtual` simple sigue en el schema y **no la usa ningún cliente** (desktop, PWA, mobile) |
| ajuste de saldo de cuenta bancaria | hecho (saldo esperado + clave) |
| `saveChequera` (alta) | cubierto sin clave: el alta repetida se rechaza por superposición de rango (#401) |
| ingreso de maletín por cierre (`ingresarMaletinCierre`) | cubierto sin clave: la marca del cierre rechaza el segundo ingreso (#398) |
| **`registrarEntradaVaria`** | **falta** |
| **`registrarOperacionFinanciera`** | **falta** |
| **`egresarMaletinCajaMayor`** / **`ingresarMaletinCajaMayor`** (a mano) | **falta** (el desktop hoy solo llama al egreso; el ingreso a mano está en el schema) |
| **`crearGastoParaPago`** (alta de gasto del hub de egresos) | **falta** |
| **`crearValeParaPago`** (alta de vale del hub de egresos) | **falta** |
| **`crearPrestamo`** (RRHH; no está en la issue) | **falta**: crea el préstamo, sus cuotas y **egresa de la caja mayor** en cada llamada. El diálogo queda abierto ante un error y el usuario repite a mano → doble desembolso |

Alcance de este bloque: las filas en negrita (siete mutations).

Revisadas y cubiertas por su propio estado, sin acción: `pagarValesMixto`, `pagarRrhhMixto`,
`ingresarRetiroACajaMayor` («ya fue ingresado», con lock), `confirmarRetiroFuncionarioPreGasto`,
`registrarDevolucionSaldoPreGasto`, `cobrarCuotaCreditoBanco`, `ajustarCajaVirtualPorConteo`. Sin cliente
que las llame (desktop, PWA, mobile): `crearValeConfirmado`, `crearAcreditacionPos`, `pagarSolicitud`,
`pagarSolicitudesLoteCajaMayor`; quedan anotadas, no se tocan.

## Qué pasa hoy

Las seis son `@Transactional` en su servicio y ninguna reconoce un pedido repetido: si la respuesta se
pierde y el usuario repite, queda otra entrada varia, otra operación financiera (con sus dos patas), otro
egreso de maletín, otro gasto pendiente u otro vale pendiente. El desktop hoy, ante un «sin respuesta»,
cierra el diálogo y pide revisar antes de repetir; no puede reintentar con seguridad.

Un caso particular: entrada varia y operación financiera con número de comprobante tipeado. Si la
original entró, la repetición se rechaza con «ya existe una entrada varia con ese comprobante», que al
usuario le parece un error cuando en realidad se había guardado.

## Central

Mismo mecanismo que las ya hechas: `IdempotenciaService.ejecutar(clave, operacion, huella, usuario,
accion, idDe, cargar)` dentro de la transacción del servicio; clave nula o vacía = cliente viejo, corre
como hoy. Tabla `financiero.operacion_idempotente` (V237.1): **sin migración nueva**.

Schema: a cada mutation se le agrega el argumento opcional `claveIdempotencia: String` (fuera del input,
como en las anteriores). Compatible con clientes viejos.

| Mutation | Operación | Huella (campos del pedido) | Lo que se guarda / se devuelve al repetir |
|---|---|---|---|
| `registrarEntradaVaria` | `ENTRADA_VARIA` | caja, moneda, monto, esIngreso, descripción, categoría, forma de pago, comprobante tipeado | id de la `EntradaVaria` |
| `registrarOperacionFinanciera` | `OPERACION_FINANCIERA` | tipo, cajas y cuentas de origen/destino, monedas, montos origen/destino, cotización, diferencia + tipo de destino + observación, descripción, comprobante tipeado y el resto de ids del input | id de la `OperacionFinanciera` |
| `egresarMaletinCajaMayor` | `MALETIN_EGRESO` | caja, maletín, moneda, monto, descripción | id del `MovimientoCajaVirtual` |
| `ingresarMaletinCajaMayor` | `MALETIN_INGRESO` | ídem | id del `MovimientoCajaVirtual` |
| `crearGastoParaPago` | `GASTO_PARA_PAGO` | tipo de gasto, descripción, moneda, monto, beneficiario proveedor/persona, día de vencimiento, sucursal | id de la `SolicitudPago` |
| `crearValeParaPago` | `VALE_PARA_PAGO` | funcionario, motivo, moneda, monto, esAdelanto, observación | id del `Vale` |
| `crearPrestamo` | `PRESTAMO_CON_DESEMBOLSO` | todos los campos de `PrestamoInput` + caja mayor | id del `Prestamo` |

Reglas comunes (las mismas de las anteriores):

- La huella se arma con **lo que mandó el cliente**, antes de cualquier derivación del servicio (número
  autogenerado, monedas derivadas de las cuentas, textos armados): el reintento manda lo mismo. Se usan
  **los ids del input**, no los de la entidad resuelta: los resolvers de entrada varia y operación
  financiera convierten un id inexistente en nulo (`orElse(null)`), y desde la entidad se perdería. En
  operación financiera entran los 16 campos del input, nombrados uno por uno (incluye `categoriaId` y
  `diferenciaObservacion`).
- Cada sobrecarga con clave lleva su propio `@Transactional` (`ejecutar` es `MANDATORY`).
- Misma clave con otra huella u otra operación: se rechaza («la clave ya se usó para otro pedido»), que
  es lo que ya hace el servicio.
- La clave se inserta dentro de la transacción: si la operación se rechaza, la clave se va con el
  rollback y el reintento corre como pedido nuevo.
- Las validaciones que rompen la huella (monto nulo, NaN) van antes de calcularla.
- **Repetición de algo que después se anuló:** se rechaza con un mensaje propio («ese pedido ya se había
  registrado y después se anuló»), ni éxito ni otro registro. Es la regla de `emitirCheque`. Campo real
  por entidad, leído de la base y no de la sesión:

  | Entidad | Anulado es | Nota |
  |---|---|---|
  | `EntradaVaria`, `OperacionFinanciera` | `anulado = true` (nulo = no) | leer con `findAnuladoById`, como hace `anular` |
  | `MovimientoCajaVirtual` (maletín) | `activo = false` (nulo = activo) | no tiene `anulado`; un movimiento MALETIN se puede anular desde la caja mayor |
  | `SolicitudPago` (gasto) | `estado = CANCELADO` | defensivo: hoy ningún flujo cancela ni devuelve una solicitud de tipo GASTO |
  | `Vale` | `estado = ANULADO` | |
  | `Prestamo` | `estado = CANCELADO` | |

  Lo demás se devuelve tal cual, aunque el estado ya no sea el de la respuesta original (gasto `PARCIAL` o
  `CONCLUIDO`, vale `CONFIRMADO` o `DESCONTADO`): el desktop no puede asumir `SOLICITADO` en la respuesta.
- El permiso de escritura sobre la caja corre dentro de la acción, así que no se reevalúa en la
  repetición (igual que en movimientos en lote): el mismo usuario recibe lo que él mismo creó.

**Numeración `SP-` de solicitudes de pago.** `SolicitudPagoService.generateNumeroSolicitud` usa
`count() + 1` sin lock y hay índice único: dos altas simultáneas de gasto o vale (claves distintas) pueden
chocar y una falla. No lo causa este bloque, pero el «Reintentar» lo vuelve más visible. Se toma el lock
por nombre `SOLICITUD_PAGO_NUMERO` dentro de ese método (decisión 6).

Dónde va cada cosa: una sobrecarga con `claveIdempotencia` en cada servicio (`EntradaVariaService`,
`OperacionFinancieraService`, `MaletinTesoreriaService`, `GastoTesoreriaService`, `ValeTesoreriaService`),
y `PrestamoService`, que valida lo mínimo, calcula la huella y delega en el método actual. Los resolvers
pasan la clave. Los cinco tests que arman esos servicios a mano (`@AllArgsConstructor`) se actualizan con
el campo nuevo.

Fuera de alcance, a propósito:

- `ingresarMaletinCierre` y el alta de chequera: ya rechazan la repetición por su propia regla.
- `realizarTransferenciaCajaVirtual` (simple): sin clientes. No se toca ni se borra del schema.
- `pagarValesMixto` y los pagos de RRHH: la issue ya dice que la repetición se rechaza (el vale o la
  liquidación ya no están pendientes).
- Limpieza de claves viejas de `operacion_idempotente`: no existe hoy para ninguna operación; no se
  agrega acá.

## Desktop

Rama par. Patrón que ya usan emitir cheque, movimiento de caja, transferencia, ajuste de saldo y pago,
copiado tal cual:

- Clave por intento del usuario (`nuevaClaveIdempotencia()`).
- Ante un «sin respuesta» el diálogo **no se cierra y no vuelve al formulario**: queda el pedido pendiente
  con el aviso `.aviso-sin-confirmar` y solo se puede «Reintentar» o cerrar. Editar y guardar de nuevo
  saldría con otra clave y duplicaría.
- El pendiente es un **snapshot del input ya armado + la clave**, no el formulario: «Reintentar» reenvía
  esas mismas variables. Importa sobre todo en operación financiera, donde `onSave` recalcula montos y
  cotización desde los controles y un segundo armado puede redondear distinto (otra huella → rechazo).

Pantallas:

1. `add-entrada-varia-dialog` — hoy cierra y pide revisar.
2. `add-operacion-financiera-dialog` — ídem.
3. `maletin-tesoreria-dialog` — solo la rama de egreso a mano; el ingreso por cierre queda como está.
4. `pagar-compras-dialog` — alta de gasto y alta de vale. Hoy `altaSinRespuesta` vuelve a la lista con el
   formulario editable; pasa a un pendiente por tipo, con su «Reintentar» visible, y mientras exista no se
   abre otra alta.
5. `edit-prestamo-dialog` (RRHH) — hoy queda abierto con el formulario listo para repetir.

La clave se agrega al final de las firmas de los servicios del desktop o dentro de `opciones`, nunca en el
medio (se llaman por posición).

**Compatibilidad.** Desktop nuevo contra central viejo: el argumento `claveIdempotencia` no existe en el
schema y la mutation falla por validación, antes de ejecutar nada. Hay cuatro centrales con despliegue
manual (alpha, beta, farmacia, bodega) y `deploy.sh` vuelve solo al JAR anterior si falla el health check,
así que «central primero» no alcanza como garantía. Por eso (decisión 5): si el central rechaza por
validación nombrando `claveIdempotencia`, el desktop reenvía **una vez sin la clave**, con el
comportamiento de hoy (cierra y pide revisar ante un sin respuesta). Es seguro: ese rechazo ocurre antes
de ejecutar. Central nuevo con desktop viejo: funciona como hoy.

## Pruebas

- Unitarias por servicio: con clave repetida no se llama dos veces a la acción y se devuelve lo original;
  con otra huella se rechaza; sin clave corre como hoy; original anulado → rechazo.
- IT (`-Dit.financiero=true`), un caso por operación contra la base dev: dos pedidos **simultáneos** con
  la misma clave dejan un solo registro y un solo movimiento de saldo; con el arreglo neutralizado
  (clave ignorada) el IT tiene que fallar.
- Por entidad, el caso «original anulado» con su campo real, y los estados que sí se devuelven (gasto
  `PARCIAL`, vale `CONFIRMADO`); misma clave para otra operación → rechazo; un campo distinto por vez
  cambia la huella; `derivarMonedasDeCuentas` no la cambia.
- IT de rollback: el pedido falla a mitad (operación financiera con la segunda pata sin saldo) → no queda
  clave, ni registro, ni movimiento, ni correlativo avanzado, y el reintento con la misma clave corre como
  nuevo.
- IT de comprobante tipeado: el original entró → el reintento lo devuelve sin «ya existe»; autonumerado →
  el reintento no avanza la serie.
- IT de dos altas simultáneas de gasto con claves distintas (numeración `SP-`).
- Runtime en el 8091: cada mutation dos veces con la misma clave, con otra huella, y sin clave.
- Desktop: build de producción, `verificar:imports` y prueba en Chrome simulando la respuesta perdida
  (parche de `XMLHttpRequest.send`, error con forma de Apollo) en las cinco pantallas, y el reenvío sin
  clave contra un central sin el cambio (el alpha de esta máquina, antes de desplegar).
- Documentación: §7.1 de `ARQUITECTURA-MODULO-FINANCIERO.md` (qué operaciones usan la clave, la regla del
  anulado por entidad, el lock de numeración).

## Rollback

Sin cambios de base; las claves que queden en `operacion_idempotente` no molestan a un JAR anterior. El
JAR anterior rechaza el argumento que no conoce: con el reenvío sin clave del desktop (decisión 5) las
pantallas siguen funcionando como hoy. Borde que queda: un diálogo con un pendiente abierto justo cuando
el central retrocede reintenta sin clave; si el original había entrado, ese reintento duplica.

## Decisiones a confirmar

1. **Alcance**: las seis mutations de la issue que faltaban; afuera el cierre de maletín, el alta de
   chequera y las mutations sin cliente. *Recomendado.*
2. **`crearPrestamo`**: no está en la issue, pero desembolsa de la caja mayor en cada repetición.
   Incluirla en este bloque (central + su diálogo). *Recomendado: sí.* Alternativa: issue aparte.
3. **`ingresarMaletinCajaMayor`** (ingreso a mano, sin pantalla hoy): incluirla, comparte el camino del
   egreso. *Recomendado: sí.*
4. **Repetición de un original anulado**: rechazar con mensaje propio; lo no anulado se devuelve aunque
   haya cambiado de estado. *Recomendado.*
5. **Desktop contra un central sin el cambio**: reenviar una vez sin la clave cuando el rechazo es por el
   argumento desconocido. *Recomendado.* Alternativa: no hacerlo y exigir el central actualizado en las
   cuatro instancias antes de publicar el desktop.
6. **Numeración `SP-`**: agregar el lock por nombre para que dos altas simultáneas no choquen.
   *Recomendado: sí* (una línea, no cambia el orden de locks).
7. **Desktop**: las cinco pantallas pasan al pendiente + «Reintentar», sin volver al formulario.
   *Recomendado.*
