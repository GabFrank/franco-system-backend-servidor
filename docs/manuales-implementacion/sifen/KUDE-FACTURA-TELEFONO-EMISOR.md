# KuDE de factura: el teléfono del emisor usa el mismo respaldo que el XML (issue #412)

Estado: **plan a aprobar**. Rama `fix/sifen-kude-factura-telefono-emisor`, desde `develop`
(`769f10d3`). Solo central.

## El problema

`SifenTimbradoHelper.telefonoEmisor(detalle)` (#330) resuelve el teléfono del emisor: el del
`timbrado_detalle` y, si es `null`, vacío o solo espacios, el de la cabecera `timbrado`; recorta
espacios. Lo usan el XML de la factura (`SifenService:1598`), los XML de las notas y los KuDE de
las notas (`KudeNotaCreditoService:105`, `KudeNotaRemisionService:108`).

El KuDE de factura quedó con un respaldo propio que solo mira `null`
(`FacturaLegalGraphQL.generarPdfFacturaElectronica`, `:1759-1765`). Con un detalle cuyo teléfono
es `""` o espacios, el XML lleva el teléfono de la cabecera y el impreso dice «Telefono: » vacío.
Con un teléfono con espacios alrededor, el impreso los conserva y el XML no.

Es cosmético: no afecta la aprobación en SIFEN. Ese mismo PDF es el que sale por correo
(`FacturaCorreoService:239`) y por el router de impresión (`FacturaPrintRouterGraphQL:43`).

### Medición

Copia local de `bodega` (11/10/2026): de 118 `timbrado_detalle`, 92 tienen teléfono `null`, **0**
lo tienen vacío o en blanco y 0 con espacios alrededor. Hoy no hay ningún caso: el cambio es
preventivo. Farmacia y la base de producción no se midieron.

El caso se puede crear desde el desktop: `add-timbrado-detalle-dialog.component.ts:265` asigna
`telefonoControl.value` sin normalizar, y un campo de texto que el usuario borra queda en `""`
(inferido del comportamiento de Angular, no probado). Ningún código del central escribe ese campo.

## Decisión

Un solo cambio: el parámetro `telefonoEmisor` del KuDE de factura sale de
`SifenTimbradoHelper.telefonoEmisor(factura.getTimbradoDetalle())`, con `""` si devuelve `null`
(la plantilla ya tolera `null`, pero el resto de los parámetros de ese bloque usa `""`).

Para poder testearlo sin levantar el resolver entero (17 colaboradores, compila el `.jrxml`), la
expresión va a un método estático package-private de `FacturaLegalGraphQL`,
`telefonoEmisorKude(TimbradoDetalle)`. No se extrae el armado de parámetros a un service: sería
otro trabajo.

## Qué cambia y qué no

| Detalle | Cabecera | Hoy | Después |
|---|---|---|---|
| `0981…` | cualquiera | `0981…` | igual |
| `null` | `0982…` | `0982…` | igual |
| `""` / `"  "` | `0982…` | vacío | `0982…` |
| `" 0981… "` | cualquiera | con espacios | recortado |
| `null` | `" 0982… "` | con espacios | recortado |
| `null` o en blanco | `""` / `"  "` / `null` | vacío o espacios | `""` |

Todo esto es dentro de la rama «hay detalle y hay cabecera» (`:1707`). La rama `else` (`:1779`,
detalle sin cabecera) **no se toca**: sigue imprimiendo `""`.

- `factura-legal.jrxml` (réplica sin SIFEN) no usa `telefonoEmisor`: no cambia.
- Sin migración ni cambio de contrato GraphQL. El cambio llega al PDF que consumen el desktop
  (`descargarPdfFacturaElectronica`), el correo y el router de impresión. El PDF se genera en cada
  pedido, no se guarda: reimprimir o reenviar una factura vieja la muestra con el teléfono nuevo.
- El filial no genera KuDE ni imprime el teléfono del emisor: nada que portar.

## Fases

### Fase 1 — extracción sin cambio de comportamiento

- `FacturaLegalGraphQL`: el `if/else` pasa tal cual a `telefonoEmisorKude(TimbradoDetalle)` y el
  `put` lo llama.
- Test `FacturaLegalKudeTelefonoEmisorTest` (`graphql/financiero`): detalle con teléfono; detalle
  en blanco con cabecera; detalle con espacios; cabecera con espacios; sin teléfono en ningún lado
  → `""`. Los casos de blancos y espacios **se ven en rojo** sobre este commit.

### Fase 2 — el fix

- `telefonoEmisorKude` delega en `SifenTimbradoHelper.telefonoEmisor`, con `""` si es `null`.
- `./mvnw clean verify -B -DskipFlyway=true`, leído del log.

El test cubre el método, no que el mapa de parámetros lo use: ese cableado es una línea y lo
cubre solo la verificación de runtime. No se agrega un test que lea el fuente (frágil) ni se arma
el resolver entero con mocks.

## Verificación de runtime

Central del worktree con perfil `dev`, bajar el KuDE de una factura electrónica de un detalle al
que se le pone el teléfono en blanco en la base local, y comprobar el texto del PDF; restaurar el
dato. Necesita copiar `application-user-dev.properties` al worktree y que el `:8081` esté libre.

## Despliegue y rollback

Deploy manual por instancia (alpha → beta/farmacia → bodega). Código puro: volver al JAR anterior
no deja estado.

## Fuera de alcance

- Que el desktop guarde `null` cuando el teléfono del timbrado queda vacío (detalle y cabecera).
  Con este fix y el del XML el dato en blanco ya no molesta; sería prolijidad.

## Auditoría del plan (paso 5, 11/10/2026)

Dos auditores, sin bloqueantes. Aplicado: filas que faltaban en la tabla (cabecera con espacios o
en blanco), la rama `else` que no se toca, la extracción previa para ver el test en rojo, el
cableado que el test no cubre, quién consume el PDF, y el origen posible del dato en blanco.
Confirmado: un método estático package-private no entra al schema ni al aspecto de seguridad (hay
precedentes en el mismo resolver); el teléfono cabe en el campo de la plantilla (235 de ancho); no
hay otro lugar del central ni del filial que imprima el teléfono del emisor con otra lógica.

## Sin verificar

- Si en farmacia o en producción hay detalles con teléfono en blanco.
