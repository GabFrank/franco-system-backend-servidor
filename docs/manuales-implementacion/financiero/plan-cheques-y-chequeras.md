# Plan: cheques y chequeras (issue #376, bloque 5)

Rama: `fix/financiero-cheques-y-chequeras` (central), desde `develop`, que ya tiene el #398 (`emitir` relee la
chequera despues del lock y valida el rango). En el desktop, un aviso (PR propio, chico).

## Estado en produccion (solo lectura, 2026-10-09)

- Bodega y farmacia: 0 chequeras y 0 cheques. El modulo todavia no se usa.
- Alpha: 4 chequeras, 4 cheques, ningun numero repetido, y 3 pares de chequeras de la misma cuenta con rangos
  superpuestos (datos de prueba).
- `cheque` y `chequera` son tablas solo del central (no estan en ninguna publicacion). El unico indice de
  `cheque` es su clave primaria.

## El problema

1. **`saveChequera` pisa el correlativo.** La edicion arma la entidad entera desde el formulario y la guarda,
   sin lock. El desktop manda siempre `siguienteNumero` —el dialogo de edicion y el boton «Desactivar», que
   reenvia la fila de la lista tal como la tenia—. Si entre que se cargo la pantalla y se guardo se emitio un
   cheque, o alguien adelanto el correlativo, **vuelve atras** y el proximo cheque repite un numero. Lo mismo
   con el estado: una edicion vieja escribe `ACTIVA` sobre una chequera que se agoto o que otro anulo.
2. **`saveChequera` no valida nada:** rango invertido, `siguienteNumero` fuera del rango, cuenta inexistente,
   cambiar la cuenta de una chequera que ya emitio cheques. Ademas una edicion reemplaza al usuario que creo
   la chequera por el que la edita.
3. **Rangos superpuestos:** dos chequeras de la misma cuenta pueden cubrir los mismos numeros.
4. **Sin unicidad de (chequera, numero)** en la base: nada respalda a la aplicacion.
5. **El cheque al dia se guarda sin fecha de pago.** El dashboard de cheques filtra por `fecha_pago`, asi que
   un cheque al dia no aparece nunca.
6. **`saveCheque` y `deleteCheque` siguen abiertas.** Son un alta / edicion / borrado planos de la tabla
   `cheque`: dejan escribir cualquier numero, pisar el estado de un cheque cobrado y borrar un cheque emitido.
   Ninguna pantalla las usa (el desktop las declara en su servicio y nadie las llama; la PWA no las tiene),
   pero anulan todo lo demas.

El punto del bloque «el filial no tiene el modulo de cheques» no pide cambio: el desktop ya manda esas
operaciones al central (#429).

## Diseno

### 1 y 2. Guardar una chequera

La logica sale del resolver a un servicio con transaccion (`ChequeraGestionService`). El resolver exige el rol
y pasa los datos del pedido; **no arma ni carga la entidad**.

**Alta:**
- cuenta obligatoria y existente;
- rango obligatorio, entero, positivo y con `desde <= hasta`;
- `siguienteNumero` por defecto `desde`; si viene, dentro de `[desde, hasta]`;
- estado por defecto `ACTIVA`;
- sin superposicion con otra chequera de la cuenta (punto 3).

**Edicion:** la chequera se toma con lock y se relee de la base (el mismo lock que `emitir`: una edicion y una
emision se serializan). Sobre lo que hay en la base:

- **nombre y firmantes:** lo que venga.
- **fecha de retiro, fecha de alta y usuario creador:** no se tocan si no vienen (el desktop no los manda). El
  usuario creador no se reemplaza por el que edita.
- **cuenta:** si no viene, se conserva; una inexistente se rechaza; no se puede cambiar si la chequera ya emitio
  cheques.
- **correlativo: solo hacia adelante.** Se aplica el `siguienteNumero` que llega **solo si es mayor que el de la
  base**; si no, se conserva el de la base, sin rechazar. Una pantalla vieja nunca trae un numero mayor que el
  de la base, asi que no puede hacerlo retroceder ni deshacer un salto que hizo otro. Deshacer un salto hecho
  de mas ya no se puede desde la pantalla.
- **rango:** si cambia, tiene que contener todos los numeros ya emitidos y no superponerse (punto 3).
- Con el rango y el correlativo finales: `desde <= siguiente <= hasta + 1`, o la edicion se rechaza.
- **estado:**
  - `ANULADA` es terminal: se puede poner siempre y no se sale de ella (una pantalla vieja no «reactiva» una
    chequera que otro anulo, y reactivarla esquivaria el control de superposicion). Si se anulo por error, se
    crea otra.
  - si el correlativo final paso el rango (`siguiente = hasta + 1`) queda `AGOTADA`, venga lo que venga;
  - `AGOTADA` vuelve a `ACTIVA` solo si se pide y hay numeros (el rango se amplio).

### 3. Rangos superpuestos

Al dar de alta, o al cambiar el rango o la cuenta: se rechaza si otra chequera **no anulada** de la misma cuenta
cubre algun numero del rango: «El rango a–b se superpone con la chequera N (c–d).»

Para que dos altas simultaneas no pasen las dos, antes de buscar se toma el lock por nombre
`CHEQUERAS_CUENTA:<cuenta>` (`BloqueoTransaccionalService`). Orden de locks del servicio: lock por nombre de la
cuenta (de las dos, por id ascendente, si la cuenta cambia) → fila de la chequera → relectura; si la cuenta
releida no es la que se habia tomado, se rechaza y se pide reintentar. `emitir` toma la fila de la chequera y
despues la de la cuenta bancaria, y no usa el lock por nombre: no hay ciclo.

Las chequeras que ya estan superpuestas (las tres de alpha) no se tocan.

### 4. Unicidad de (chequera, numero)

Indice unico `uq_cheque_chequera_numero` sobre `financiero.cheque (chequera_id, numero)`, por migracion
(`V241.1`). Incluye los cheques anulados: un cheque anulado sigue ocupando su numero. Las dos columnas son
`NOT NULL` en la base.

La aplicacion ya no deberia repetir numeros; el indice es el respaldo. Como una migracion que falla tumba el
arranque del central y el CI no las valida, **la migracion no puede fallar**: crea el indice dentro de un bloque
que, si encuentra numeros repetidos (antes o durante), deja un `WARNING` en el log y sigue. Verificado sin
repetidos en bodega, farmacia y alpha.

Como Flyway no la reintenta, al arrancar el central comprueba si el indice existe y, si falta, lo dice en el log
con nivel `ERROR` (no frena el arranque).

Si aun asi una emision choca con el indice, el rechazo es legible («El número N de la chequera X ya existe.
Revisá su correlativo.») en vez del error crudo de la base.

### 5. Cheque al dia con fecha de pago

En `emitir`: si el cheque no es diferido y no trae fecha de pago, se le pone la de emision (la fecha de entrega,
o ahora). Con eso aparece en el dashboard cuando se filtra por cobrados; las tarjetas de «pendiente» solo suman
diferidos, asi que no se cuenta de mas. Un cheque al dia que ya exista sin fecha no se corrige (en alpha no hay
ninguno).

### 6. `saveCheque` y `deleteCheque`

Se rechazan las dos: «Los cheques se emiten, se cobran y se anulan con sus operaciones: no se editan ni se
borran.» Siguen en el schema para no romper la validacion de un cliente que las declare.

### Desktop (PR propio, chico)

Cuando el central conserva el correlativo en vez de aplicar el que se mando, hoy la pantalla diria «Chequera
actualizada» y nada mas. El dialogo de edicion compara el `siguienteNumero` devuelto con el enviado y, si
difieren, avisa: «El próximo número quedó en N: no se puede llevar hacia atrás.» No depende del orden de
despliegue.

## Tabla de datos nuevos

| Dato | Donde | Nota |
|---|---|---|
| indice unico `uq_cheque_chequera_numero` | `financiero.cheque` | `V241.1`, condicional; tabla solo del central |
| `fecha_pago` en cheques al dia | `financiero.cheque` | columna existente; antes quedaba nula |

Sin columnas nuevas, sin cambios de schema GraphQL, sin cambios de replicacion.

## Fases

Central (un PR):

1. **Guardar chequera.** Tests: alta valida; cada rechazo del alta; una edicion con el correlativo viejo no lo
   hace retroceder (tras emitir, y tras un salto de otro usuario); hacia adelante se aplica; fecha de retiro y
   usuario creador se conservan; rango que deja afuera un cheque emitido o al correlativo → rechazo; cambiar
   la cuenta con cheques → rechazo; `ACTIVA` vieja sobre una agotada queda `AGOTADA`; ampliar el rango de una
   agotada y pedir `ACTIVA` la reactiva; salir de `ANULADA` → rechazo; «Desactivar» con la fila vieja anula sin
   tocar el correlativo; lock y relectura antes de decidir.
2. **Superposicion.** Tests: alta superpuesta → rechazo; contra una anulada → pasa; otra cuenta → pasa; editar
   sin tocar el rango de una ya superpuesta → pasa; lock por nombre antes de buscar.
3. **Cheque al dia, `saveCheque` / `deleteCheque` y el rechazo legible** ante el indice.
4. **Migracion** `V241.1` y el chequeo al arrancar. Probada a mano contra la base local (que hoy tiene un numero
   repetido, de una prueba): no crea el indice ni falla; sin el repetido lo crea; corrida dos veces no falla.
5. **Tests de integracion** (`-Dit.financiero=true`): emitir un cheque y guardar la chequera con la fila
   vieja → el siguiente cheque no repite numero; dos altas simultaneas con el mismo rango → una.

Desktop (otro PR): el aviso. `verificar:imports`, build de produccion y prueba en Chrome.

Cada test de bug se corre con el fix neutralizado para ver que falla.

## Prueba de runtime (central local, perfil `dev`)

- Editar el nombre de una chequera con la pantalla abierta desde antes de emitir un cheque: el correlativo no
  retrocede. «Desactivar» con la lista vieja: igual.
- Crear una chequera con un rango superpuesto: rechazo.
- Emitir un cheque al dia: aparece en `chequesDashboard`.

## Despliegue y rollback

Solo central; requiere reinicio. La migracion solo agrega un indice (o nada).

Rollback del JAR: no deja datos incompatibles, pero **no es inocuo si el indice quedo creado**. La version
anterior vuelve a pisar el correlativo al editar una chequera; antes repetia el numero en silencio, y con el
indice la emision falla con un error de base hasta que alguien corrija `siguiente_numero`. Antes de volver
atras, y despues: `select id from financiero.chequera c where siguiente_numero <= (select max(numero) from
financiero.cheque where chequera_id = c.id)`.

## Decisiones tomadas (Franco, 2026-10-09: las seis, como se recomiendan)

1. **Correlativo en la edicion: solo hacia adelante; si llega uno menor o igual se conserva el de la base, sin
   rechazar.** Recomendacion: si. Rechazar haria fallar «Desactivar» y cualquier pantalla abierta desde antes.
   Costo: un salto hecho de mas no se puede deshacer desde la pantalla.
2. **`ANULADA` es terminal.** Recomendacion: si. Alternativa: permitir reactivar revisando la superposicion,
   pero una pantalla vieja reactivaria una chequera que otro anulo.
3. **Rechazar `saveCheque` y `deleteCheque`.** Recomendacion: si: nadie las usa y dejan saltear todo.
4. **Indice unico por migracion que no puede fallar**, con aviso al arrancar si falta. Recomendacion: si.
   Alternativa: sin indice (queda solo la aplicacion).
5. **Aviso en el desktop** cuando el correlativo no se movio. Recomendacion: si, en un PR chico aparte.
6. **Las chequeras ya superpuestas no se corrigen.** Recomendacion: si; solo hay en alpha.

## Queda sin verificar

- Bases de beta: no las mire. La migracion condicional no puede romper su arranque.
- El dialogo de edicion del desktop avisa, cuando no hay respuesta, que repetir una edicion «pisa el siguiente
  numero»: deja de ser cierto.
- Un pago a proveedores con varias formas de pago puede tomar una cuenta bancaria y despues una chequera; una
  edicion que le cambia la cuenta a esa chequera las toma al reves. Solo con una chequera sin cheques; PostgreSQL
  aborta una de las dos.

## Auditoria del plan (paso 5, 2026-10-09)

| Hallazgo | Que se hizo |
|---|---|
| «Mayor que el ultimo emitido» dejaba que una pantalla vieja deshiciera el salto de otro usuario | solo hacia adelante respecto de la base |
| Una pantalla vieja reactivaria una anulada, y reactivar esquiva la superposicion | `ANULADA` terminal |
| Cambiar el rango podia dejar al correlativo afuera | se valida contra el rango final |
| `saveCheque` y `deleteCheque` saltean todo | se rechazan |
| «Lo que venga» borraria la fecha de retiro; la edicion pisaba al usuario creador | nulo conserva |
| El bloque condicional no era atomico: un repetido que entre en el medio tumbaba el arranque | captura el error; `WARNING` |
| Si el indice no se crea, nadie se entera | aviso al arrancar |
| La violacion del indice llegaba como error crudo | rechazo legible |
| El central ignoraria un correlativo hacia atras sin que la pantalla lo diga | aviso en el desktop |
| Orden de locks al cambiar la cuenta; el servicio recibia la entidad armada en el resolver | diseno |
| El rollback no es inocuo con el indice creado | seccion de rollback |
| El #398 ya esta en develop | la rama sale de develop |
