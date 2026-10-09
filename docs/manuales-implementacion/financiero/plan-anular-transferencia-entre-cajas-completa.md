# Plan: anular una transferencia entre cajas completa, no una pata suelta (issue #376, bloque 2)

Rama: `fix/financiero-anular-transferencia-entre-cajas-completa` (desde `origin/develop` 13f67968).
Este archivo es registro de trabajo: se borra en el PR final.

## El problema

`realizarTransferenciaCajaVirtual` (`TesoreriaService.transferir`) postea dos movimientos con origen `MANUAL`:
`TRANSFERENCIA_SALIDA` en la caja origen y `TRANSFERENCIA_ENTRADA` en la destino. Nada los vincula entre si.

`anularMovimientoCajaVirtual` (`TesoreriaService.anular`) acepta cualquier movimiento `MANUAL`, asi que cada
pata se anula por separado. Anular solo la salida devuelve la plata a la caja origen y la deja tambien en la
destino: la plata se duplica. Anular solo la entrada la hace desaparecer. El dashboard muestra cada pata en
su caja con un «Anular» comun («Se generará un contra-movimiento de ajuste»), sin avisar que es media
transferencia.

(Las transferencias hechas como **operacion financiera** no tienen este problema: sus patas llevan origen
`OPERACION_FINANCIERA`, `anular` las rechaza y se anulan juntas desde la operacion.)

## Alcance

Solo esto. `cancelarRetiro` / `cancelarGasto` (el otro item que queda del bloque 2) van en un ciclo propio:
necesitan cambio en el desktop y tocan tablas que se replican.

## Diseno

### Quien crea patas de transferencia

| Camino | `origenTipo` | Con este cambio |
|---|---|---|
| `TesoreriaService.transferir` (mutation `realizarTransferenciaCajaVirtual`) | `MANUAL` | se anulan juntas |
| `OperacionFinancieraService` | `OPERACION_FINANCIERA` | no cambia: `anular` ya las rechaza y se anulan desde la operacion |
| `saveMovimientoCajaVirtual` con el tipo elegido por el cliente | nulo | el desktop no ofrece ese tipo en ningun alta; solo por API |
| transferencias anteriores a `V177.5` (cuando nacio `origen_tipo`) | nulo | **a decidir**, ver abajo |

Ojo: `MANUAL` no es «hecho a mano». Solo `transferir` lo setea; los ingresos, egresos y ajustes manuales entran
por `saveMovimientoCajaVirtual` con el origen nulo, y `anular` los acepta porque deja pasar el nulo.

### Vincular las patas al crearlas

`transferir` guarda en cada pata el id de la otra en `referenciaId` (hoy nulo en estos movimientos), seteandolo
sobre las dos instancias ya registradas: Hibernate emite los dos `UPDATE` por clave al cerrar la transaccion.
Vinculo mutuo y no de un solo sentido porque es el unico que se resuelve por clave primaria: no hay indice
sobre `referencia_id`. Sin migracion. Ningun lector del backend ni del desktop interpreta `referenciaId` en un
movimiento `MANUAL` (el desktop solo lo mira en pagos consolidados y en operaciones financieras).

### Anular las dos juntas

`TesoreriaService.anular`, cuando el movimiento es una pata de transferencia. En este orden:

1. **Leer el movimiento por proyeccion** (id, tipo, cajas, moneda, cantidad, referencia, origen), sin cargar la
   entidad: `lockById` devuelve la instancia que ya estuviera cargada, sin refrescar.
2. **Permisos sobre las dos cajas** —`cajaOrigen` y `cajaDestino` estan en el propio movimiento—, antes de
   buscar nada y antes de tomar ningun lock. Mensaje propio: «Para anular una transferencia hace falta permiso
   de escritura en las dos cajas» (el generico haria pensar que falta el de la caja propia). De paso se
   corrige que hoy `anular` toma el lock antes de mirar el permiso.
3. **Ubicar la otra pata:**
   - con vinculo: el movimiento `referenciaId`, que tiene que ser del tipo opuesto, con las mismas caja
     origen, caja destino, moneda y cantidad, y **apuntar de vuelta** a este;
   - sin vinculo (transferencias anteriores): entre las patas del tipo opuesto con las mismas cajas, moneda y
     cantidad y sin referencia —**activas o no**—, la **contigua por id**: para la pata que se registro primero,
     la de menor id mayor que el suyo; para la que se registro segunda, la de mayor id menor. Cual se registro
     primero lo dice el orden de las cajas (`transferir` registra primero la de la caja de menor id). Y se
     exige **simetria**: desde la candidata, la misma busqueda tiene que devolver esta pata.
     Por que el orden de id y no «la unica candidata dentro de 5 segundos»: `transferir` retiene el saldo de la
     primera caja hasta el commit, asi que dos transferencias identicas no pueden intercalar sus patas; en
     cambio la segunda pata se inserta **despues** de esperar el saldo de la segunda caja, que con contencion
     puede tardar mas que cualquier ventana. Y «unica candidata sin referencia» empareja mal cuando conviven una
     transferencia vieja y una nueva identicas.
4. **Lock de los dos movimientos por id ascendente** (no «el pedido y despues el otro»: dos anulaciones que
   entran cada una por una pata se cruzarian). Despues, relectura de `activo` de los dos por proyeccion, y
   revalidar que la contraparte sigue siendo la elegida.
5. **Segun el estado:**
   - el pedido ya anulado → «El movimiento #N ya está anulado.», como hoy;
   - contraparte **activa** → se anulan las dos, en la misma transaccion, revirtiendo por caja ascendente (el
     orden en que `transferir` toma los saldos);
   - contraparte **ya anulada y con su contra-movimiento** (alguien anulo antes esa pata suelta) → se anula
     solo la pedida: deja la transferencia consistente en los dos sentidos;
   - contraparte inactiva **sin** contra-movimiento (dato roto), o no se pudo identificar → rechazo: «No se
     pudo identificar la otra pata de esta transferencia; no se anula a medias». No se adivina.
6. **Limite de antiguedad (CN4):** sobre la pata mas vieja de las dos.
7. **Si no alcanza el saldo** de la caja destino para devolver lo transferido (el caso mas comun: esa plata ya
   se gasto), el rechazo dice cual caja y que es por la transferencia, en vez del «Saldo insuficiente en la
   caja virtual» pelado. Todo se deshace: no hay `REQUIRES_NEW` ni capturas en la cadena.
8. El contra-movimiento de la pata **no pedida** lleva en su descripcion que viene de anular la transferencia
   junto con el movimiento #N, para que quien abra esa caja entienda por que aparece.

`anular` de cualquier otro movimiento no cambia.

No se hace un backfill del vinculo por migracion: las migraciones del central no se validan en CI y un
emparejamiento equivocado quedaria escrito.

### Desktop (PR propio, despues del central)

El dashboard de la caja confirma «¿Anular este movimiento? Se generará un contra-movimiento de ajuste». Para
una pata de transferencia (`origenTipo === 'MANUAL'` y tipo `TRANSFERENCIA_*`, datos que la fila ya trae) pasa a
«¿Anular la transferencia completa? Se revierte también el movimiento en la otra caja». Va despues del
despliegue del central: antes, ese texto prometeria algo que el central todavia no hace.

## Tabla de datos nuevos

| Dato | Quien lo escribe | Quien lo lee |
|---|---|---|
| `movimiento_caja_virtual.referencia_id` en las patas de una transferencia manual (columna existente) | `TesoreriaService.transferir` | `TesoreriaService.anular` |

## Fases

Central (un PR):

1. **Vinculo + anulacion conjunta.** Tests en `TesoreriaServiceTest`:
   - `transferir` deja cada pata apuntando a la otra y no cambia sus saldos anterior / posterior;
   - anular una pata vinculada con la otra activa → dos contra-movimientos, las dos inactivas, los dos saldos
     de vuelta; igual entrando por la entrada o por la salida;
   - contraparte ya anulada con su contra → solo la pedida; inactiva sin contra → rechazo;
   - sin vinculo: dos transferencias identicas consecutivas → cada pata empareja con la suya, tambien en el
     sentido B→A; una vieja y una nueva identicas → no se cruzan; sin candidata → rechazo;
   - vinculo que no vuelve, o que apunta a algo que no es la contraparte → rechazo;
   - sin permiso sobre la otra caja → rechazo con el mensaje propio, sin tomar locks;
   - un ingreso manual comun (origen **nulo**, que es como entran) se anula como hoy.
   Los de «contraparte ya anulada» y «ingreso comun» son de regresion: pasan tambien con el codigo viejo.
2. **Tests de integracion** en `ReversasIT` (`-Dit.financiero=true`; no corren en CI), con dos cajas propias
   reutilizables: tres anulaciones a la vez repartidas entre las dos patas, en A→B y en B→A → un exito, dos
   contra-movimientos, los dos saldos de vuelta; gastar el destino y anular → rechazo con el mensaje nuevo, las
   dos patas siguen activas; entrada ya anulada suelta → se anula la salida sola; y que el estado se lee de la
   base aunque las entidades ya esten cargadas.

Desktop (otro PR, despues del despliegue del central):

3. **Texto de confirmacion** para las patas de transferencia. `npm run check` y prueba en Chrome.

Cada test de bug se corre con el fix neutralizado para ver que falla.

## Prueba de runtime (central local, perfil `dev`)

- Transferir entre dos cajas y anular la salida: las dos patas quedan anuladas, los dos saldos vuelven.
- Idem anulando la entrada.
- Seis anulaciones simultaneas, tres por pata: una pasa, cinco «ya está anulado»; sin deadlock.
- Usuario con acceso a una sola de las dos cajas: rechazo.
- Una transferencia sin vinculo (creada a mano, como las anteriores al cambio): se anula completa.
- Ingreso manual: se anula como siempre.

## Despliegue y rollback

Solo central. Sin migracion ni cambio de schema; rollback del JAR inocuo (las patas ya vinculadas quedan con un
`referencia_id` que la version anterior ignora). Requiere reinicio del central (workflow Deploy; mergear a
`develop` no despliega).

## Decisiones abiertas

1. **Transferencias con el origen nulo** (anteriores a `V177.5`, o creadas por API). Los dos auditores no
   coinciden: uno propone dejarlas fuera y declararlo como limite; el otro, tratarlas igual que las `MANUAL`,
   porque si no el defecto sigue vivo justo en las transferencias viejas. **Arbitra Franco.** Recomendacion:
   tratarlas igual; la busqueda por firma y simetria ya filtra lo que no es una transferencia.
2. **Cuanto pesa el camino sin vinculo.** Depende de cuantas transferencias manuales hay en produccion, que no
   se miro. Si hay muy pocas o ninguna, se puede reemplazar toda la busqueda sin vinculo por un rechazo y
   resolver esas a mano.

## Queda sin verificar

- En produccion: cuantas transferencias manuales hay, cuantas tienen el origen nulo, cuantas quedaron con una
  sola pata anulada (plata duplicada o perdida, que este cambio no corrige pero si permite completar), y
  cuantas son emparejables o ambiguas con la regla de arriba. Es una consulta de solo lectura.
- `transferir` no valida que el monto sea positivo. Fuera de este cambio.
- `recalcularSaldos` suma los movimientos activos, y un original anulado queda inactivo con su contra activo:
  reconstruye mal. Es anterior a este cambio.
- Una pestana ya abierta de la otra caja queda vieja hasta que se recargue.

## Auditoria del plan (paso 5, 2026-10-09)

Dos auditores. Una contradiccion (origen nulo), que queda para arbitrar.

| Hallazgo | Que se hizo |
|---|---|
| La ventana de 5 s mide mal: la segunda pata se inserta despues de esperar el saldo de la segunda caja | fuera; se empareja por contiguidad de id con simetria |
| «Unica candidata sin referencia» empareja mal una transferencia vieja con una nueva identicas | idem |
| Leer el movimiento como entidad antes del lock deja a `lockById` devolviendo estado viejo | lectura previa por proyeccion, relectura por proyeccion tras el lock |
| `MANUAL` no es «hecho a mano»: los ingresos manuales entran con el origen nulo | corregido; los tests de regresion se arman con el origen nulo |
| Contraparte inactiva sin contra-movimiento | rechazo |
| Mensaje de permiso ambiguo; mensaje de saldo insuficiente sin contexto | mensajes propios |
| El contra-movimiento de la otra pata no explica por que aparece | lo dice su descripcion |
| El texto de confirmacion del desktop | PR de desktop, despues del central |
| Permisos despues del lock | permisos primero |
| CN4 sobre una sola pata | sobre la mas vieja |
| Backfill del vinculo por migracion | no se hace |
