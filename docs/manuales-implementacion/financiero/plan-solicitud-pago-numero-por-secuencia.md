# Plan — número de solicitud de pago por secuencia

Pendiente que quedó anotado al cerrar la issue #376. Rama `fix/solicitud-pago-numero-por-secuencia`
(solo central). Este archivo se borra en el commit final, antes del PR.

## Qué pasa hoy

`SolicitudPagoService.generateNumeroSolicitud` arma el número con `repository.count() + 1`
(`SP-000123`), y `operaciones.solicitud_pago.numero_solicitud` tiene índice único
(`uk_solicitud_pago_numero`). Dos problemas:

1. **Dos altas simultáneas cuentan lo mismo** y una falla contra el índice. Desde #403 el alta de gasto y la
   de vale se ponen en fila con un lock por nombre, pero las solicitudes de compra y las que crean los pagos
   en lote de RRHH y de vales siguen numerando sin lock: contra ellas el choque sigue siendo posible.
2. **Borrar una solicitud que no es la última traba todas las altas siguientes.** `eliminarSolicitud`
   (compras, solicitud `PENDIENTE`) hace `deleteById`. Con 10 solicitudes, si se borra la 4, `count() + 1`
   vuelve a dar 10, que ya existe: cada alta nueva —compra, gasto, vale, RRHH— falla contra el índice hasta
   que alguien lo corrija a mano. Al 2026-10-10 no ocurrió: en bodega (1364), farmacia (33) y alpha (20) la
   cantidad coincide con el número más alto.

## Propuesta

Una secuencia de PostgreSQL da el número.

- **Migración `V242.1__solicitud_pago_numero_secuencia.sql`** (otra rama abierta trae una `V242.3`: conviven,
  son versiones distintas):

  ```sql
  CREATE SEQUENCE IF NOT EXISTS operaciones.solicitud_pago_numero_seq;
  SELECT setval('operaciones.solicitud_pago_numero_seq',
         COALESCE((SELECT MAX(substring(numero_solicitud from 4)::bigint)
                     FROM operaciones.solicitud_pago
                    WHERE numero_solicitud ~ '^SP-[0-9]{1,15}$'), 0) + 1, false);
  ```

  El próximo número es el más alto con forma `SP-<dígitos>` más uno; con la tabla vacía, 1. El regex acota a
  15 dígitos: un valor raro no puede romper el cast ni tumbar el arranque. Hay precedentes de las dos
  sentencias en migraciones anteriores.
- **`SolicitudPagoRepository.siguienteNumero()`**: consulta nativa `select nextval(...)`, como los
  `siguienteId()` que ya existen. Lleva `@Transactional` propio de **escritura**: los métodos de consulta de
  un repositorio corren en solo lectura si nadie abrió una transacción, y PostgreSQL rechaza `nextval` ahí
  (`crearSolicitudVale` no tiene `@Transactional` propio).
- `generateNumeroSolicitud` usa ese valor con el mismo formato (`SP-` + 6 dígitos, más si hiciera falta).
- **Realineo al arrancar** (`SolicitudPagoNumeroVerificador`, mismo patrón que `ChequeUnicoVerificador`):
  crea la secuencia si falta y la adelanta si quedó detrás del número más alto. Solo adelanta, nunca
  retrocede. Cubre tres casos que la migración sola no cubre, porque Flyway no repite una versión ya
  aplicada: (a) un entorno donde la migración no corrió; (b) volver a este JAR después de un rollback en el
  que el anterior siguió numerando por conteo; (c) los segundos entre la migración y el reemplazo del JAR,
  en los que el JAR viejo todavía puede emitir un número. Si falla, loguea `ERROR` y no frena el arranque.
- **Se quita el lock `SOLICITUD_PAGO_NUMERO`** de `AltaIdempotenteService` (constante, `enFilaPorNumero` y
  la dependencia `bloqueo`). No protegía nada más: el alta de gasto solo crea la solicitud (la fila de
  `financiero.gasto` nace al pagar) y el vale usa id por identidad.
- El índice único queda como respaldo.

`nextval` no espera a nadie, no participa de ningún orden de locks y no se deshace con el rollback: un alta
rechazada **deja un hueco** en la numeración. Nadie depende de que sea corrida: desktop, PWA y mobile solo
muestran o filtran el número como texto, y ningún reporte lo usa.

## Lo que no cambia

- El formato y los números ya emitidos.
- Quién crea solicitudes: solo el central, y siempre por `SolicitudPagoService.save` (tres caminos:
  compra, gasto, vale/RRHH). El filial no tiene la tabla y no se replica.

## Rollback del JAR

- **Rollback automático del deploy** (falla el health check): sin riesgo. El JAR nuevo no llegó a atender
  pedidos, así que no hay huecos; la secuencia queda creada y el JAR anterior la ignora.
- **Rollback manual después de haber operado**: es de una sola vía mientras haya huecos. El JAR anterior
  vuelve a `count() + 1`, que con un hueco cae sobre un número ya usado: todas las altas de solicitudes
  fallan (sin dejar nada a medias) hasta volver a este JAR. No hay forma de «cerrar» los huecos sin
  renumerar solicitudes ya emitidas. Al volver a avanzar, el realineo del arranque deja la secuencia bien.

## Pruebas

- Unitarias: el número sale de `siguienteNumero()` con el formato y `count()` ya no se consulta;
  `AltaIdempotenteServiceTest` sin el lock; el verificador crea, adelanta y no retrocede.
- IT (`-Dit.financiero=true`): (a) con una solicitud borrada del medio, el alta siguiente entra (falla con
  el código anterior); (b) altas simultáneas por el camino de gasto y por el de vale salen con números
  distintos; (c) `crearSolicitudVale` sin transacción exterior no falla por solo lectura; (d) con la
  secuencia atrasada a mano, el verificador la adelanta.
- Migración a mano en la base local: con solicitudes, con la tabla vacía (en una copia), corrida dos veces,
  y con números raros (`SP-12X`, `sp-12`, uno de 20 dígitos, `IT-CPP-…`).
- Runtime en el 8091: alta de gasto y de vale por GraphQL; los números siguen al último.
- Documentación: se reescribe el párrafo «Número de solicitud de pago» de
  `ARQUITECTURA-MODULO-FINANCIERO.md` §7.

## Decisiones a confirmar

1. **Huecos en la numeración**: un alta rechazada consume su número y no se reutiliza. *Recomendado:
   aceptarlo.*
2. **Rollback manual de una sola vía** mientras haya huecos (ver arriba). *Recomendado: aceptarlo.* La
   alternativa es un trigger en la tabla que numere también para el JAR anterior: más invasivo, y solo sirve
   para ese caso.
3. **Quitar el lock por nombre** de las altas de gasto y de vale. *Recomendado: sí.*
4. **Realineo automático al arrancar** (crea la secuencia si falta y la adelanta). *Recomendado: sí.*
