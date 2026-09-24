# Plan — fix: teléfono del emisor (dTelEmi) vacío en los DE del central

Rama: `fix/sifen-telefono-emisor-vacio` (desde `origin/develop` 7ba22364). Pieza: **central**.

## Qué pasó

El 2026-09-23 se emitieron notas de remisión desde el depósito 13 (timbrado 18270044, serie 001-002).
SIFEN de producción rechazó la primera con **0160 «XML malformado: [El valor del elemento: dTelEmi es
invalido]»**. El `timbrado_detalle` 118 del depósito no tenía teléfono, y los builders copian
`timbradoDetalle.getTelefono()` a `gEmis.dTelEmi` sin mirar si está vacío. El número de la nota ya
estaba asignado (T1) y el lote se gastó. Se destrabó cargando el teléfono a mano en la base.

La cabecera `Timbrado` tiene su propio `telefono` (el 18270044 tiene `0982700027`): con ese respaldo
la nota habría salido.

## Dónde está

| Archivo | Línea | Documento |
|---|---|---|
| `service/sifen/SifenNotaRemisionBuilder.java` | 119 | NRE (iTiDE 7) |
| `service/sifen/SifenNotaCreditoBuilder.java` | 119 | NCE (iTiDE 5) |
| `service/sifen/SifenService.java` | 1586 | FE generada en el central (`generarDEDesdeFacturaDatosReales`, usada al crear el DE de una factura y al reconstruirlo) |

## Cambio — una sola fase

1. `SifenTimbradoHelper.telefonoEmisor(TimbradoDetalle)`: devuelve el teléfono del detalle
   recortado; si está vacío, el de la cabecera `Timbrado`; si los dos están vacíos, `null`.
   Nunca devuelve cadena vacía.
2. Los tres builders usan el helper en lugar de `timbradoDetalle.getTelefono()`.
3. `SifenNotasValidator.validarNRE` y `validarNCE`: si `gEmis.dTelEmi` queda vacío, cortan con
   `GraphQLException` «El emisor no tiene teléfono: cargalo en el timbrado de la sucursal
   (Financiero → Maestros → Timbrados)». Así el error aparece **antes** de gastar el lote y con
   un mensaje que dice qué hacer, en vez del 0160 de SIFEN.
   - La factura **no** recibe esa validación nueva: su flujo no pasa por `SifenNotasValidator`, y
     agregar un corte ahí cambiaría el comportamiento de un camino que hoy factura. Recibe solo el
     respaldo (punto 2), que es estrictamente mejor que el vacío actual.

## Tests (fase única)

- `SifenTimbradoHelperTest`: detalle con teléfono → el del detalle (recortado); detalle vacío o en
  blanco → el de la cabecera; los dos vacíos → `null`; timbrado nulo → `null`.
- `SifenNotaRemisionBuilderTest` y `SifenNotaCreditoBuilderTest`: con detalle sin teléfono y
  cabecera con teléfono, `dTelEmi` sale con el de la cabecera.
- `SifenNotasValidatorTest`: NRE y NCE con `dTelEmi` vacío → excepción con el mensaje.
- Paso 7: revertir el fix y comprobar que los tests nuevos fallan.

## Datos nuevos

Ninguno: sin columnas, sin migración, sin `.graphqls`, sin enums. N/A la tabla de escritor/lector.

## Fuera de alcance

- **filial**: emite facturas con su propio builder. N/A para filial en este PR porque el caso
  reportado es del central; queda anotado para revisar aparte si su builder repite el patrón.
- Validar formato/longitud del teléfono: el manual técnico de SIFEN no se consultó en esta sesión,
  así que no se agrega una regla que no está verificada.

## Prueba de runtime (paso 9)

2026-09-24, contra la copia local de `bodega`, con el jar de esta rama en `:8084` y **SIFEN,
replicación, Flyway y cotización apagados**. Se vació el teléfono del `timbrado_detalle` 118
(depósito 13; la cabecera 18270044 tiene `0982700027`) y se pidió `imprimirNotaRemision` de la
nota 001-002-0000001: el KuDE salió con «Teléfono: 0982700027». Después se restauró el `0986128000`
del detalle, que es el que viajó en el XML de esas notas.

No se probó en runtime el XML con respaldo ni el corte del validador: los dos pasan por SIFEN de
producción (el central local emite ahí) o consumen un número de la serie. Los cubren los tests
unitarios, que fallan con el código viejo.

## Qué queda sin verificar

- Que SIFEN acepte el teléfono de la cabecera: el formato es el mismo que el `0986128000` que
  aprobó el 2026-09-23 (15 NRE aprobadas), pero ese número concreto no viajó todavía.
- En producción no se pudo consultar cuántos `timbrado_detalle` electrónicos activos tienen el
  teléfono vacío (la lectura de la base de producción está bloqueada en esta máquina). En la copia
  local, ninguno tras la corrección manual del 118.

## Auditoría del plan (paso 5)

Dos auditores (eje A contrato/propagación, eje B reversibilidad/estado), sin verse. Ninguno
encontró riesgo alto. Todo lo citado se verificó contra el código.

| # | Eje | Sev. | Hallazgo | Qué se hace |
|---|---|---|---|---|
| 1 | B | — | El validador corre **antes** del único `save` del DE (`SifenService.java:213` y `:262`, `save` en `:234` y `:284`): si corta, no queda DE a medias, ni CDC, ni lote. El número de la nota ya se asignó en otra transacción; el reintento tras cargar el teléfono cae otra vez en `orElseGet(crearDocumento)` y **no quema otro número**. | Confirma el diseño. Sin cambios. |
| 2 | A | media | La factura también se regenera por `reconstruirDEDesdeFactura` cuando falta o no parsea el `xmlOriginal` de un DE ya aprobado: con el respaldo, `dTelEmi` podría salir distinto del que viajó. | Aceptado: el teléfono no entra en el CDC, y ese camino ya hoy toma el teléfono vigente del detalle, no el de la emisión. Se deja constancia acá. |
| 3 | A | media | El filial repite el patrón sin respaldo (`franco-system-backend-filial/.../service/sifen/service/SifenService.java:1648`) y la nota «revisar aparte» solo vive en este plan. | Sigue fuera de alcance (otro repo, otro PR). Se propone abrir un issue en el repo del filial para que no se pierda — se pide al usuario, porque es una acción hacia GitHub. |
| 4 | B | baja | El mensaje llega con prefijo redundante («No se pudo generar el documento electrónico: El emisor no tiene teléfono…»): `NotaRemisionGraphQL.crearDocumento` lo concatena. | Aceptado: el texto útil llega completo. |
| 5 | B | baja | El test «timbrado nulo → null» del helper no protege end-to-end: `construirEmisor` ya revienta antes con `getTimbrado().getRuc()`. | Se mantiene como test del helper y se aclara en el PR que no cubre ese caso en producción. |
| 6 | B | baja | Al revertir el fix para el paso 7, los tests del helper no compilan (el método no existe): el rojo es de compilación, no de assert. | Para el paso 7 se revierte solo el **uso** del helper en los builders y la regla del validador, dejando el método: así el rojo es de assert. |
| — | A | — | Sin cambio de GraphQL, enums, variables de entorno ni tablas: `timbrado` y `timbrado_detalle` ya se replican (`V0:14739,14746`, `V112:18-19`) y solo se leen. Ningún cliente parsea el texto del error. | — |
| — | B | — | `TimbradoDetalle.timbrado` es `FetchType.EAGER` (`TimbradoDetalle.java:40`): leer la cabecera desde el builder no arriesga `LazyInitializationException`. | — |

## Auditoría del diff (paso 8)

Tres ejes fijos sobre `origin/develop...HEAD`. Los condicionales no se disparan: el diff no toca la
maquinaria de release ni nada que viaje entre nodos.

| Eje | Resultado |
|---|---|
| Fijo 1 — autorización | Sin hallazgos: ningún resolver nuevo ni modificado, ningún control de rol ni filtro por sucursal tocado. El respaldo lee `detalle.getTimbrado()`, la cabecera de esa misma fila: no puede traer el teléfono de otro timbrado. El mensaje es texto fijo. |
| Fijo 2 — esquema | Sin hallazgos: sin migración. `financiero.timbrado.telefono` existe desde `V0:3283`, está mapeado 1:1 (`Timbrado.java:77`) y se publica sin filtro de columnas. |
| Fijo 3 — contrato | **Hallazgo (media), aplicado.** Los KuDE de nota de remisión y de crédito leían `timbradoDetalle.getTelefono()` crudo (`KudeNotaRemisionService:97`, `KudeNotaCreditoService:94`): con el respaldo, el XML aprobado habría llevado el teléfono de la cabecera y el PDF impreso, vacío. Ahora los dos usan `SifenTimbradoHelper.telefonoEmisor`, con un test cada uno que falla con el código viejo. El KuDE de factura ya tenía su propio respaldo (`FacturaLegalGraphQL.java:1702-1708`). El desktop muestra el mensaje del backend completo (`generic-crud.service.ts:227-239`) y deja reintentar desde la lista sin duplicar la nota. |

## Despliegue

Solo código del central: reinicio del servicio por el workflow Deploy. Sin impacto en DB ni en
rollback (revertir el commit vuelve al comportamiento anterior).
