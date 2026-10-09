# Plan: validaciones que hoy solo hace el desktop (issue #376, bloque 4, resto)

Rama: `fix/financiero-validaciones-en-el-central` (central). Sin cambios en el desktop.

Cubre lo que quedaba del bloque 4 despues de los ajustes con saldo esperado (#396): el ingreso del cierre de un
maletin, el numero de comprobante y la emision de cheques. El limite de caja chica queda como decision.

## Estado en produccion (solo lectura, 2026-10-09)

Ninguna de estas funciones esta en uso todavia, en bodega ni en farmacia:

- movimientos de maletin en caja mayor: 0;
- cheques: 0;
- cajas chicas: 0 (todas las cajas son `CAJA_MAYOR`);
- series de comprobante (`comprobante_serie`): 0 filas, o sea que hoy **no se autonumera nada**;
- entradas varias: 5 en bodega y 1 en farmacia, todas con el comprobante vacio (`''`); operaciones financieras:
  20 y 3, 7 con el comprobante vacio; ningun numero repetido.

No hay datos que corregir. Lo que sigue cierra los huecos antes de que se usen.

## 1. Maletin: el cierre se ingresa una sola vez

### El problema

`ingresarMaletinCierre(cajaVirtualId, maletinId, monedaIds, descripcion)` toma el valor del ultimo cierre de la
caja de PDV que uso el maletin y postea un `INGRESO` por moneda. No queda ninguna marca de que ese cierre ya se
ingreso: repetir el pedido (doble clic, reintento, otra persona) vuelve a ingresar la misma plata. Tampoco hay
lock.

### Diseno

La marca va en el propio movimiento, sin columna nueva: hoy los movimientos de maletin llevan
`origen_tipo = MALETIN`, `origen_id = maletin` y `referencia_id = maletin` (repetido). Los del **cierre** pasan a
llevar `referencia_id = id de la caja de PDV` y `origen_sucursal_id = su sucursal` (la caja tiene clave compuesta:
el id solo no la identifica). Los ingresos a mano siguen con `origen_sucursal_id` nulo: no se confunden.

- **Lock:** `pg_advisory_xact_lock` sobre `MALETIN_CIERRE:<maletin>`, lo primero de la transaccion. No se toma
  la fila de `maletin`: es una tabla que llega por replicacion desde la filial, y un `FOR UPDATE` frenaria al
  apply worker de esa filial mientras la transaccion espera saldos.
- **La caja se resuelve una sola vez, despues del lock**, con un metodo compartido con `valorMaletin` que
  devuelve la caja y sus valores. Si la ultima caja del maletin esta abierta (sin cierre), el mensaje lo dice
  («El maletín está en una caja abierta: todavía no tiene un cierre para ingresar») en vez de «no tiene un
  cierre con valores».
- **Marca:** por cada moneda con valor mayor que cero, buscar un movimiento **activo** con (`MALETIN`,
  `INGRESO`, maletin, caja de PDV, sucursal, moneda). La busqueda es escalar, no carga entidades.
  - Monedas pedidas explicitamente y alguna ya ingresada → rechazo, nombrandolas: «El cierre de este maletín
    ya se ingresó en GUARANI (movimiento #N).» No se ingresa ninguna.
  - Sin lista de monedas («todas») → se ingresan las que falten; se rechaza solo si no queda ninguna.
- Se postea por **moneda ascendente** (hoy va en el orden del conteo, y dos maletines hacia la misma caja mayor
  podian pedir los saldos en orden distinto).
- Anular ese movimiento desde la caja mayor (ya se puede: origen `MALETIN`) lo deja inactivo y habilita
  ingresar de nuevo. Un cierre nuevo es otra caja de PDV: otra marca.
- `valorMaletin` suma un campo `ingresado: Boolean` por moneda (aditivo en el schema; el desktop actual lo
  ignora y puede usarlo mas adelante para no ofrecer lo ya ingresado).
- `ingresarMaletinCajaMayor` y `egresarMaletinCajaMayor` (monto a mano) no cambian ni cuentan para la marca.
  El desktop no usa el ingreso a mano.

Limites que quedan, anotados:

- La marca es por **caja**, no por conteo: si un administrador corrige el conteo de cierre despues del ingreso
  (el conteo es versionado: la caja pasa a apuntar a uno nuevo), la marca sigue bloqueando. Se resuelve
  anulando el ingreso y volviendo a ingresar. Marcar por conteo seria peor: dejaria ingresar el valor entero
  otra vez.
- Solo se puede ingresar el cierre de la **ultima** caja del maletin. Si el maletin vuelve a salir antes de
  que tesoreria ingrese el cierre anterior, ese cierre ya no se puede ingresar por aca. Ya es asi.

## 2. Numero de comprobante

### El problema

- `EntradaVariaService.registrar` autonumera solo si el comprobante llega **nulo**; el desktop manda `''`, asi
  que nunca autonumera y guarda la cadena vacia.
- `OperacionFinancieraService.registrar` no autonumera nunca.
- Nada impide dos documentos con el mismo comprobante.

### Diseno

- **Vacio = nulo:** en los dos servicios el comprobante se normaliza (`trim`, mayusculas; vacio → `null`). Mas
  de 60 caracteres (el largo de la columna) se rechaza con mensaje.
- **Unicidad de lo tipeado:** si llega con valor y ya lo tiene otro documento **no anulado** de la misma
  tabla, se rechaza: «Ya existe una entrada varia con el comprobante N.» La busqueda compara
  `upper(trim(numero_comprobante))` y trata `anulado` nulo como no anulado; ignora nulos y vacios.
- **Autonumeracion:** si queda nulo, `siguienteNumero("ENTRADA_VARIA")` (ya esta) y
  `siguienteNumero("OPERACION_FINANCIERA")` (nuevo). Sin serie configurada devuelve `null`, como hoy.
  Si el numero generado ya esta usado (alguien lo tipeo a mano antes), **no se rechaza: se pide el siguiente**,
  hasta encontrar uno libre (con tope). Rechazar ahi trabaria la serie: el rollback deshace el avance del
  correlativo y cada alta volveria a generar el mismo numero.
- **Carrera:** antes de buscar un numero —tipeado o generado— se toma `pg_advisory_xact_lock` sobre
  (tabla, numero normalizado). Dura la transaccion. Todo esto va antes del primer `save`.
- **Sin indice unico por migracion.** Seria la forma natural, pero una migracion que falla tumba el arranque
  del central y el CI no las valida; con el lock la regla queda igual de cerrada.
- El numero de un documento anulado queda libre (no existe «des-anular»).

## 3. Emitir cheque

### El problema

`ChequeGestionService.emitir` (lo usan `emitirCheque` y el pago a proveedores) no valida casi nada:

- un total nulo se emite por 0, y uno negativo se registra por su valor absoluto;
- un diferido sin fecha de pago se acepta;
- si `siguiente_numero` quedo fuera del rango de la chequera, emite igual;
- sin cuenta bancaria (ni en el cheque ni en la chequera) revienta con un `NullPointerException`;
- acepta una cuenta distinta de la de la chequera, y una moneda distinta de la de la cuenta.

### Diseno

**Primero, un defecto que ya existe y es mas grave que las validaciones:** `emitir` toma la chequera con
`lockById`, pero tanto `emitirCheque` como el pago a proveedores la cargan antes; el lock devuelve esa instancia
sin refrescar, asi que el numero siguiente y el estado se leen como estaban **antes** de esperar. Dos emisiones
simultaneas de la misma chequera salen con el **mismo numero**. Se corrige leyendo la chequera de la base
despues del lock (regla de §7).

Despues, las validaciones, antes de mover nada:

- total finito, mayor que cero y dentro de `numeric(18,4)` (se valida antes de calcular la huella del pedido);
- la cuenta es la de la chequera: si el cheque trae otra, se rechaza; si ninguna tiene, se rechaza con mensaje;
- si el cheque trae moneda y no es la de la cuenta, se rechaza; un id de moneda o de cuenta que no existe se
  rechaza en vez de ignorarse;
- diferido → fecha de pago obligatoria y **no anterior al dia de emision** (la fecha de entrega del cheque, o
  hoy si no trae). No «anterior a hoy»: el pago a proveedores permite registrar con fecha retroactiva un cheque
  ya entregado;
- el numero a emitir —el efectivo: `siguiente_numero`, o `rango_desde` si es nulo— tiene que estar dentro del
  rango: por encima, «La chequera N no tiene más números (rango a–b).»; por debajo, «El próximo número de la
  chequera N (x) está fuera de su rango (a–b).»

Queda para el bloque 5: la unicidad de (chequera, numero), el cheque al dia sin fecha de pago y que
`saveChequera` deja escribir cualquier `siguiente_numero` (es lo que puede dejar una chequera fuera de rango).

## 4. Limite de caja chica

El desktop **no lo valida**: si un ingreso deja la caja chica por encima de `limite_gs` muestra un aviso
(«¡Atención! La caja ha superado el límite…») y lo registra igual. El central no mira el limite. No hay cajas
chicas en produccion.

Decision abierta: dejarlo como aviso (no tocar) o que el central rechace el ingreso.

## Tabla de datos nuevos

| Dato | Donde | Nota |
|---|---|---|
| `referencia_id` = caja de PDV y `origen_sucursal_id` en los `INGRESO` del cierre de un maletin | `movimiento_caja_virtual` | columnas existentes; antes `referencia_id` repetia el maletin |
| serie `OPERACION_FINANCIERA` | `comprobante_serie` | se consulta; no se crea |
| campo `ingresado: Boolean` | tipo `ValorMaletinItem` del schema | aditivo |

Sin columnas ni migracion. En el schema, solo el campo aditivo de arriba.

## Fases

Un PR, tres commits:

1. **Maletin.** Tests: primer ingreso postea y deja la marca; el segundo se rechaza sin postear; «todas» con
   una ya ingresada ingresa el resto; moneda en cero no cuenta; anulado el movimiento se puede ingresar de
   nuevo; el lock va antes de resolver la caja; maletin en una caja abierta → su mensaje; por moneda
   ascendente; los ingresos a mano no llevan ni miran la marca.
2. **Comprobante.** Tests: `''` y espacios → nulo; sin serie queda nulo; con serie autonumera y **salta** un
   numero ya usado; tipeado repetido → rechazo; repetido de un documento anulado → pasa; el lock se toma antes
   de buscar y antes del `save`; mas de 60 caracteres → rechazo; operacion financiera igual.
3. **Cheque.** Tests: el numero sale de la base aunque la chequera cargada diga otro; cada rechazo; un cheque
   valido al dia y uno diferido siguen emitiendose igual; el pago a proveedores con cheque sigue pasando,
   tambien con fecha de emision retroactiva.
4. **Tests de integracion** (`-Dit.financiero=true`): dos ingresos simultaneos del mismo cierre → uno; dos
   entradas varias simultaneas con el mismo comprobante → una; dos emisiones simultaneas de la misma chequera
   → numeros distintos.

Cada test de bug se corre con el fix neutralizado para ver que falla.

## Prueba de runtime (central local, perfil `dev`)

- Ingresar el cierre de un maletin dos veces: la segunda se rechaza. Anular y volver a ingresar: pasa.
- Entrada varia con comprobante vacio: queda sin numero (sin serie) o numerada (con una serie de prueba).
  Dos con el mismo numero: la segunda se rechaza.
- Emitir cheque con total 0, diferido sin fecha, y con la chequera fuera de rango: rechazos.

## Despliegue y rollback

Solo central, sin migracion; requiere reinicio. Rollback del JAR inocuo: la version anterior ignora la marca
(vuelve a dejar ingresar dos veces) y los comprobantes nulos son validos para ella.

## Decisiones tomadas (Franco, 2026-10-09: las cuatro, como se recomiendan)

1. **Limite de caja chica.** Recomendacion: no tocarlo ahora. Es un aviso, no una validacion; no hay cajas
   chicas en uso; y un rechazo en el central cortaria tambien las reposiciones que entren por otra via.
2. **Unicidad del comprobante sin indice unico** (lock de transaccion). Recomendacion: si, por el riesgo de una
   migracion que falle al arrancar.
3. **Fecha de pago de un diferido no anterior a su emision.** Recomendacion: si; hoy se acepta cualquier fecha.
4. **Los tres temas en un solo PR.** Recomendacion: si: son chicos, solo del central y ninguno esta en uso.

## Queda sin verificar

- Si el cierre de una caja llega por replicacion a medias (la caja ya apunta a su conteo y los renglones del
  conteo todavia no), se ingresaria un valor incompleto y la marca bloquearia el correcto: anular y reingresar.
- Ida y vuelta de version: un ingreso hecho con el JAR anterior no lleva la marca.
- El comentario del desktop que dice que «el central no impide ingresar dos veces el mismo cierre» queda
  viejo; el comportamiento del dialogo sigue siendo correcto.

## Auditoria del plan (paso 5, 2026-10-09)

| Hallazgo | Que se hizo |
|---|---|
| `emitir` lee la chequera cargada antes del lock: dos emisiones simultaneas repiten el numero (defecto previo) | se lee de la base tras el lock; test de integracion |
| Un numero autogenerado que ya estaba tipeado trabaria la serie | la autonumeracion salta al siguiente libre |
| Lock de la fila `maletin` frena la replicacion de la filial | advisory lock |
| «Todas las monedas» sobre un cierre ingresado a medias se rechazaba entero | ingresa las que faltan |
| La caja del cierre se resolvia fuera del lock; mensaje engañoso con el maletin en una caja abierta | metodo compartido, dentro del lock |
| El conteo de cierre es versionado: marcar por conteo dejaria ingresar dos veces | marca por caja, limite anotado |
| Dos maletines a la misma caja mayor podian cruzarse | por moneda ascendente |
| `pg_advisory_xact_lock` devuelve `void` y Hibernate no lo mapea | `cast(... as varchar)` |
| «No anterior a hoy» rechazaria cheques diferidos registrados con fecha retroactiva | contra el dia de emision |
| El rango tiene que mirar el numero efectivo; mensajes distintos por encima y por debajo | diseno |
| Comprobante de mas de 60 caracteres; busqueda sobre datos sin normalizar | validacion y `upper(trim())` |
| El desktop no sabe que monedas ya estan ingresadas | campo `ingresado` aditivo |
| Quien lee `referencia_id` de un movimiento de maletin; diferido sin fecha desde el desktop | verificado: nadie; no ocurre |
