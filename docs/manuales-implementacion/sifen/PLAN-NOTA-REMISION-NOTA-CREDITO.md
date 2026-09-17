# Plan — Nota de Remisión (NRE) y Nota de Crédito (NCE) electrónicas SIFEN

_Financiero / SIFEN · central (emisor) + filial (espejo DDL + guarda) + desktop (UI)._
_Referencia de implementación: `frc-efact` (NC y NR ya funcionales contra SIFEN con la misma `jsifenlib 0.2.4-frc.13`)._
_Ciclo: `frc-cicd/ciclo-implementacion-frc-comercial.md` §1 (12 pasos). Este archivo es el paso 4; el paso 5 (auditoría de dos ejes) corrió antes de presentarlo — hallazgos en §9._

Todo dato marcado `[ev: ...]` se verificó el 2026-09-17 contra el código de los repos. Lo que no se pudo
verificar dice `NO VERIFICADO` en §10.

---

## 0 · Resumen ejecutivo

**Qué se construye.** Dos tipos nuevos de Documento Electrónico (DE) SIFEN en FRC Comercial:

| Fase | Documento | `iTiDE` | Para qué sirve en el negocio |
|---|---|---|---|
| **1** | Nota de Remisión Electrónica (NRE) | 7 | Amparar el traslado de mercadería: entre sucursales (transferencias), a un cliente (venta con entrega) o a un tercero (consignación, reparación, devolución a proveedor) |
| **2** | Nota de Crédito Electrónica (NCE) | 5 | Anular total o parcialmente una factura electrónica **después** de las 48 h en que SIFEN acepta el evento de cancelación, o documentar devoluciones/descuentos/bonificaciones sobre una factura aprobada |

**Decisión de arquitectura central (D1).** Las notas se **emiten únicamente desde el servidor central**,
operadas desde el desktop (que para toda la administración de facturación ya habla con central,
`servidor=true`). El filial **no emite** notas: solo recibe una migración espejo sobre
`financiero.documento_electronico` (para que la replicación no muera) y una guarda en su scheduler
(para que no reenvíe a SIFEN documentos que emitió central). Justificación en §2.

**Por qué en este orden (NR primero, NC después).** NR no toca dinero ni IVA, no tiene documento
asociado obligatorio y no interactúa con la cancelación de ventas ni con la cuenta corriente del
cliente: es el camino más corto para dejar armada la infraestructura común (Fase 0) y validarla
contra SIFEN real. NC reutiliza esa infraestructura y suma las reglas fiscales (moneda heredada,
ítems 1:1, totales, documento asociado por CDC) y la interacción con la cancelación de facturas.

**Piezas que cambian.**

| Repo | Toca | PR |
|---|---|---|
| filial (`GabFrank/franco-system-backend-filial`) | 2 migraciones (`V91.5` espejo de `documento_electronico`, `V91.7` partición de ids a pares) + guarda en los tres métodos de `SifenSchedulerService` + test | **PR 1 — va primero** (ver §7) |
| central (`GabFrank/franco-system-backend-servidor`) | migraciones `V225.5`/`V226.5`/`V227.5`, 4 entidades + ítems, `SifenService` (2 constructores de DE nuevos), 2 resolvers + `.graphqls`, 2 KuDE `.jrxml`, servicio de seguridad por rol | **PR 2** (o dos PRs: Fase 0+1 y Fase 2) |
| desktop (`GabFrank/frc-sistemas-integrados-angular`) | módulo UI de NR y de NC bajo `modules/financiero/`, botón "Nota de crédito" en la lista de facturas, botón "Nota de remisión" en transferencias, menú + roles | **PR 3** (plan del cliente: `desktop/docs/manuales-implementacion/sifen/PLAN-NR-NC-DESKTOP.md`) |
| mobile-pwa / mobile | **N/A**: no consumen ningún campo que cambie (`documento_electronico.factura_legal_id` pasa a nullable, pero ningún cliente móvil lee DE) `[ev: grep documentoElectronico en mobile-pwa — NO VERIFICADO en esta sesión, el repo no está clonado; se asume por el mapa de módulos de la skill frc-cicd]` | — |

---

## 1 · Contexto y hallazgos

### 1.1 Lo que YA existe en FRC Comercial (reutilizable)

**Central** (`com.franco.dev`, Spring Boot 2.7 / Java 11):

- **Pipeline SIFEN completo para factura**: `SifenService.crearDocumentoElectronico(FacturaLegal)`
  arma el bean de jsifenlib en `generarDEDesdeFacturaDatosReales` (`service/sifen/SifenService.java:1374`),
  obtiene CDC (`deSifen.obtenerCDC()`), genera XML (`generarXml(ctx)`), extrae QR (`SifenXmlParser.extractUrlQr`)
  y persiste `DocumentoElectronico` en `PENDIENTE`. Lotes: `crearLote()`:189, `vincularDocumentosALote`:212,
  `enviarLote`:253 (reconstruye cada DE **desde `xmlOriginal`**, agnóstico al tipo), `consultarLote`:337,
  `consultarDE(cdc)`:417. `[ev: central:service/sifen/SifenService.java]`
- **Helpers reutilizables tal cual**: `construirDatosEmisor` (RUC/DV, `tipoContribuyenteEmisor` por
  property, dirección/tel/ciudad del `TimbradoDetalle`, actividades económicas) `:1434`;
  `construirDatosReceptor` vía `SifenReceptorHelper.determinarConfiguracionReceptor(Cliente, total)`
  `:1464`; `GCamIvaMapper.construir(iva)` (grupo E730, reglas NT13); `mapearDepartamento(String)`
  `:1667`; `CodigosGeograficos` (departamento/distrito por código o nombre); `aplicarFixTotalesIVA`.
  **Ventaja sobre frc-efact**: acá el emisor NO tiene `iTipCont` hardcodeado y el receptor SÍ usa el
  helper — dos deudas de frc-efact que no hay que portar. `[ev: SifenService.java:1441, :1464]`
- **Eventos**: `EventosSifenGraphQL` expone `cancelarDocumentoElectronico(cdc, motivo)`,
  `nominarReceptorDocumento(cdc, clienteId)` e `inutilizarNumeros(...)`; `SifenEventoService.inutilizarNumeros`
  ya recibe `TTiDE tipoDE` genérico y lo persiste en `evento_inutilizacion_de.tipo_de`. La
  cancelación es **por CDC, sin mirar el tipo** → sirve para NC y NR sin cambios.
  `[ev: central:graphql/financiero/EventosSifenGraphQL.java:131,156,186; service/sifen/SifenEventoService.java:303-363]`
- **Scheduler**: `SifenSchedulerService.procesarLotesAutomaticamente()` cada 5 min, gateado por
  `sifen.scheduler.enabled` (**default `false`** en código) → agrupa `findByEstado(PENDIENTE)` en lotes y
  consulta lotes `EN_PROCESO`. `EventoSifenScheduler` consulta eventos de cancelación pendientes.
  `[ev: SifenSchedulerService.java:43,75,134,222]`
- **Lote y DE son agnósticos al tipo**: `LoteDE` no distingue tipo; `DocumentoElectronico.tipoDocumento`
  es `String` libre, hoy siempre `"FACTURA"` (`DocumentoElectronicoService.createFromFacturaLegal:72`).
  Un lote SIFEN puede mezclar facturas, NC y NR (cada DE lleva su `iTiDE` en su XML) — confirmado en
  frc-efact (`SifenService.enviarLote` de referencia).
- **KuDE**: `FacturaLegalGraphQL.descargarPdfFacturaElectronica` compila `reports/factura-electronica-kude.jrxml`
  en runtime (72 `fontName="SansSerif"`, cero fuentes físicas) y genera el QR con
  `QRCodeImageGenerator` desde `DocumentoElectronico.urlQr`. `[ev: FacturaLegalGraphQL.java:1517-1560; grep fontName]`
- **Impresión padrón (backend)**: queries con `anchoMm: Int` / `escpos: Boolean` ya existen
  (`retiro-devolucion.graphqls:59`, `impresora.graphqls`), consumidas por `ImpresionService` del desktop.
- **Seguridad por rol**: `TesoreriaSecurityService` (`service/financiero/TesoreriaSecurityService.java`)
  resuelve el usuario por nickname del `SecurityContext` y lee `personas.usuario_role`; constantes con
  nombres con espacios (`"TESORERIA VER"`); `requireAnyRole(...)`; bypass `ADMIN`. Seed de rol:
  `V218.5__seed_rol_venta_tarjeta_completar.sql` (INSERT idempotente en `personas.role`, sin espejo
  en filial porque `personas.role` es `MAIN_TO_ALL`). **Ninguna mutation SIFEN existente tiene control
  por rol** (solo login). `[ev: TesoreriaSecurityService.java:29-90; V218.5; FacturaLegalGraphQL.java:187 @Unsecured en saveFacturaLegal]`
- **Vehículo y chofer**: `vehiculos.vehiculo` (`Vehiculo`: `chapa`, `modelo→marca`, `tipoVehiculo`,
  `propietario: Persona`) es `MAIN_TO_ALL`; `operaciones.hoja_ruta` (`HojaRuta`: `vehiculo`,
  `chofer: Persona`, `fechaSalida/Llegada`, `kmSalida/Llegada`, `acompanantes`) cuelga de
  `Transferencia.hojaRuta`. No existe entidad `Chofer`: el chofer es una `Persona`.
  `[ev: domain/activos/Vehiculo.java; domain/operaciones/HojaRuta.java:32-59; V112 replication_table 'vehiculos.vehiculo' MAIN_TO_ALL]`
- **Transferencias** (`operaciones.transferencia`, `transferencia_item`): central-only, sin
  replicación, con `sucursalOrigen`/`sucursalDestino`, estados `ABIERTA…EN_TRANSITO…CONLCUIDA`, ítems
  con cantidades por etapa y **sin precio/IVA** — exactamente lo que una NRE necesita (la NRE **no**
  lleva valores). `[ev: domain/operaciones/Transferencia.java; docs/devoluciones-replicacion.md:9-14]`
- **Geografía del emisor**: `TimbradoDetalle` tiene `departamento`, `ciudad`, `codigoCiudad`,
  `direccion`, `telefono`; `Sucursal` tiene `ciudad: Ciudad` (`general.ciudad.codigo`), `direccion`,
  `codigoEstablecimientoFactura`. `[ev: domain/financiero/TimbradoDetalle.java:48-72; domain/empresarial/Sucursal.java:34,54,59]`

**Filial** (`com.franco.dev`, Spring Boot 2.1 / Java 8, clon de solo lectura en esta sesión):

- Emite el DE de cada venta del POS **localmente** (`FacturaLegalBuilder.build` →
  `sifenService.crearDocumentoElectronico`, `:148`) y su scheduler envía/consulta los lotes.
  Publica `documento_electronico`, `lote_de`, `evento_*`, `timbrado_detalle` hacia central
  (`V50__add_facturacion_electronica_to_publication.sql`). `[ev: filial:service/financiero/builder/FacturaLegalBuilder.java:148; V50]`
- `financiero.documento_electronico.factura_legal_id` es **`NOT NULL`** en su DDL
  (`V34__add_documento_electronico_fields.sql:8`) y en la entidad (`nullable=false`). **Este es el
  bloqueo estructural que obliga a tocar el filial** (ver D1 y §7).
- Su scheduler toma `findByEstado(PENDIENTE)` **sin filtrar por sucursal ni por tipo**
  (`SifenSchedulerService.java:137`) — si central le replica un DE de NC en `PENDIENTE`, el filial
  intentaría enviarlo a SIFEN. `[ev: filial:SifenSchedulerService.java:137; repository/financiero/DocumentoElectronicoRepository.java:18]`
- `VentaService.cancelarVenta` es un stub que devuelve `true` sin hacer nada `[ev: filial:VentaService.java:148]`.
  La cancelación real de facturas la hace **central** (`FacturaLegalGraphQL.cancelarFacturaLegal:1348`
  → `sifenEventoService.cancelarDE`), invocada por el desktop con `servidor=true`.

**Desktop** (Angular 15 + Electron 22):

- Toda la administración de facturación va contra **central**: `FacturaLegalService` usa
  `servidor=true` en lista, detalle, cancelación, XML/PDF, nominación `[ev: desktop:modules/financiero/factura-legal/factura-legal.service.ts:129-331]`.
  Solo el alta desde el POS va al filial.
- Lista de facturas con menú de acciones por fila (`list-factura-legal.component.html:405-453`):
  ahí va el botón **"Nota de crédito"**. Cancelación actual: `onCancelarFactura` → `ConfirmDialogComponent`
  con opciones "Solo Factura" / "Factura + Venta" `[ev: list-factura-legal.component.ts:625-650]`.
- Módulo de transferencias en `modules/operaciones/transferencia/` (⚠️ `modules/transferencias/` no es
  un NgModule) con `HojaRuta` (vehículo + chofer) ya cargados por el usuario → ahí va el botón
  **"Nota de remisión"**.
- Padrón de impresión `ImpresionService.imprimir(nombre, generar, soloPdf)` + `ImprimirDialogComponent`
  (`docs/IMPRESION.md`); la factura legal todavía usa el flujo legacy. NC/NR nacen con el padrón.
- Roles `ROLES.CREAR_FACTURAS` / `EDITAR_FACTURAS` existen en `roles.enum.ts` y **no se usan en ningún
  lado** `[ev: desktop:modules/personas/roles/roles.enum.ts:29-30; grep sin usos]`.

### 1.2 Lo que NO existe (100 % nuevo)

- Ninguna referencia a `gCamNCDE`, `gCamNRE`, `gTransp`, `gCamDEAsoc`, `TTiDE.NOTA_DE_*` en central ni
  en filial (grep en `src/main/java` = 0). Único `iTiDE` usado: `FACTURA_ELECTRONICA` (`SifenService.java:1392`).
- Ninguna entidad, migración, `.graphqls`, componente Angular ni doc de NC/NR. La "nota de crédito"
  de `operaciones/devolucion` (`NotaCreditoDevolucion`, `Devolucion.nroNotaCredito`) es la nota **del
  proveedor hacia la empresa**, en papel: otro dominio, no se reutiliza.
- **Numeración por tipo de documento**: `TimbradoDetalle` tiene una sola secuencia (`numeroActual`) y
  no distingue tipo; SIFEN exige series independientes por `iTiDE` dentro del mismo punto de
  expedición.
- **Establecimiento**: `gTimb.setdEst("001")` está hardcodeado en `SifenService.java:1394`, ignorando
  `Sucursal.codigoEstablecimientoFactura`.
- **Validación local de la ventana de cancelación** (48 h factura / 168 h otros DE): no existe en
  central ni en filial; hoy depende de que SIFEN rechace.

### 1.3 Referencia frc-efact — qué se porta y qué NO

**Se porta (patrones probados contra SIFEN):**

| Patrón | Dónde en frc-efact | Notas |
|---|---|---|
| Construcción del DE de NC: `iTiDE=5`, `gCamNCDE.iMotEmi`, `gCamCond` **siempre CONTADO** con un `gPaConEIni` EFECTIVO, ítems con precio+IVA, `gCamDEAsoc(ELECTRONICO, dCdCDERef=CDC factura)` en el **DE raíz**, `gTotSub` con fix de IVA | `SifenService.construirDEDesdeNotaCredito:1695-1865`, `construirDatosItemsNotaCredito:1974-2110` | Sin `gCamCuotas`; sin `descripcionMotivo` en el XML (jsifenlib genera `dDesMotEmi` desde el enum) |
| Construcción del DE de NR: `iTiDE=7`, **`gOpeDE.dInfoFisc` obligatorio** (RG 41/2014), `cMoneOpe=PYG` fijo, receptor **nunca innominado** y con `dDirRec` + geografía, `gCamNRE` (motivo, `iRespEmiNR`, `dKmR`, `dFecEm`), ítems **sin `gValorItem` ni `gCamIVA`**, `gTransp` completo (salida, entrega, vehículo, transportista, chofer), **sin `gTotSub`**, `gCamDEAsoc` opcional | `construirDEDesdeNotaRemision:2128-2264`, `construirDatosItemsNotaRemision:2270-2385`, `construirTransporteNRE:2391-2706`, `validarDECompletoNRE:3007-3107` | `dMarVeh` ≤ 10 caracteres; `cCiuSal`/`cCiuEnt` ≠ 0; `dFecEm` obligatorio si motivo=1 sin factura asociada y acotado a `[dFeEmiDE, dFeEmiDE+5 días]`; motivo 7 exige RUC receptor = RUC emisor; si se manda `dFecEm` sin km, forzar `dKmR=1` (orden del XSD) |
| Fecha de firma segura: `min(fechaDocumento, now(America/Asuncion) − 10 s)` | `obtenerFechaFirmaSegura:3297` | Evita "fecha y hora de la firma adelantada" |
| NC hereda **exactamente** moneda y tipo de cambio de la factura; ítems y totales copiados 1:1 desde la factura (no recalcular, evita diferencias de redondeo) | `NotaCreditoService.crearNotaCredito` | Ver D6 para la NC parcial |
| Numeración por tipo: `MAX(numero) + 1` sobre la tabla de cada tipo, filtrado por `timbrado_detalle_id`, con `UNIQUE (timbrado_detalle_id, numero)` | `TimbradoDetalleService.incrementarNumeroNotaCredito/Remision` | Acá con lock pesimista en vez de `synchronized` (D4) |
| Envío síncrono al generar (`generarYEnviar…` = crear lote de 1 + `enviarLote`) y `vincularALoteYEnviar` para reintentos sin regenerar el DE | `DocumentoElectronicoService.generarYEnviarDesdeNota*` | Independiza NC/NR del scheduler (D8) |
| Vehículo/chofer: FK a catálogo + **snapshot** de marca/matrícula/nombre/documento/dirección en la nota | `NotaRemision` (V33/V35) | El snapshot es lo que va al XML y al KuDE; el catálogo puede cambiar después |
| KuDE de NC y NR (`nota-credito-kude.jrxml`, `nota-remision-kude.jrxml`) | `KudePdfService` | Reemplazar el único `fontName="Monospaced"` por `SansSerif` (regla del repo: solo `SansSerif`/`Verdana`) |

**NO se porta (bugs o deuda de frc-efact detectados en esta sesión):**

1. **Motivo de NR roto en la referencia**: el front persiste `"1".."14"` y `mapearMotivoEmisionNRE` hace
   `TiMotivTras.valueOf("1")` → siempre cae al default `TRASLADO_POR_VENTAS`. Acá el motivo se
   persiste como **nombre del enum** (`TRASLADO_ENTRE_LOCALES`) y se mapea con `valueOf(name)`.
   `[ev: frc-efact:SifenService.java:2736-2748]`
2. `iTipCont` del emisor hardcodeado `PERSONA_JURIDICA` (central ya lo resuelve por property).
3. `SifenReceptorHelper` duplicado a mano en 3 rutas (central ya lo usa; NC/NR lo reutilizan).
4. `iRespFlete`, `cCondNeg`, `dTiVehTras` hardcodeados: acá `iRespFlete` se deriva del tipo de
   transporte (`PROPIO` → `TRANSPORTE_PROPIO`, `TERCERO` → `TERCERO`), y `cCondNeg` se omite (es
   opcional en el XSD para operaciones internas; **NO VERIFICADO** contra SIFEN real, ver §10 — si
   rechaza, se restaura `CFR` como en la referencia).
5. Chofer informado siempre, incluso en transporte propio (decisión abierta "SIFEN-4" de frc-efact).
   Acá: se informa siempre que esté cargado, **con una property** `sifen.nre.chofer-en-propio` (default
   `true`) para apagarlo sin release si SIFEN empieza a rechazar.
6. Tablas de auditoría `created_at/…` renombradas después (V29): acá nacen con `creado_en` /
   `usuario_id`, la convención de central.
7. Endpoints REST: acá todo es GraphQL (regla del repo).

---

## 2 · Decisiones de diseño

- **D1 — Emite solo central; el filial recibe espejo + guarda.** _(✅ Confirmado por Gabriel el 2026-09-17.)_
  Razones: (a) el desktop ya administra facturación contra central; (b) central tiene replicadas
  la factura, sus ítems y el DE con CDC de todas las sucursales, y el certificado/CSC de la misma
  empresa (`sifen.enabled` debe seguir `true` — gotcha "SIFEN no se puede desactivar vía env var");
  (c) transferencias y devoluciones ya son central-only; (d) el filial es Spring Boot 2.1/Java 8,
  se propaga solo a 24 hosts sin gate humano y no está en el alcance de escritura de esta sesión.
  Costo: dos cambios chicos en filial (§4 Fase 0.F) que igual son obligatorios por la replicación.
  **Alternativa descartada**: emitir en ambos nodos — duplica `SifenService` en dos bases de código
  distintas y exige partición de ids en las tablas nuevas.
- **D2 — Cuatro tablas nuevas central-only, con PK compuesta `(id, sucursal_id)`.**
  `financiero.nota_remision`, `nota_remision_item`, `nota_credito`, `nota_credito_item`. **No** se
  registran en `configuraciones.replication_table` ni en ninguna publicación (mismo criterio que
  `transferencia*`, `devolucion*`). La PK compuesta se mantiene por coherencia con `factura_legal` y
  `documento_electronico` (las FK compuestas `(id, sucursal_id)` lo exigen) y para que
  `sucursal_id` sea el eje de aislamiento por sucursal. `sucursal_id` de una NC = el de la factura;
  de una NR = la sucursal de origen del traslado.
- **D3 — `documento_electronico` se generaliza, no se duplica.** Migración: `factura_legal_id` pasa a
  **nullable** (`DROP NOT NULL`: relaja, no rompe la versión anterior), se agregan `nota_credito_id`
  y `nota_remision_id` nullable con FK compuesta `(x_id, sucursal_id)`, y un `CHECK` de que exactamente
  una de las tres FK esté cargada. `tipoDocumento` sigue `String` (no se toca el `.graphqls`) con los
  valores `FACTURA` | `NOTA_CREDITO` | `NOTA_REMISION` centralizados en una clase de constantes
  `TipoDocumentoElectronico` (no enum PG, no enum GraphQL → no dispara `SchemaEnumsSincronizadosTest`
  ni migración de `CHECK`). **La UNIQUE `(factura_legal_id, sucursal_id)` se conserva** (NULLs no
  colisionan en PG).
- **D4 — Numeración por tipo dentro del mismo `TimbradoDetalle`.** `numero_nota_credito` /
  `numero_nota_remision` = `MAX+1` por `timbrado_detalle_id`, asignado dentro de la transacción con
  **`SELECT … FOR UPDATE` sobre la fila de `timbrado_detalle`** (lock pesimista, regla transversal
  del repo) y respaldado por `UNIQUE (timbrado_detalle_id, numero_*)`. No se agregan columnas a
  `timbrado_detalle` (es `MAIN_TO_ALL`: obligaría espejo en filial). El timbrado usado es el de la
  factura (NC) o el `TimbradoDetalle` activo y electrónico de la sucursal de origen (NR).
  ⚠️ Verificar con el contador/SET que el timbrado vigente **habilita** NC y NR (el timbrado
  electrónico habilita los tipos que se declararon al solicitarlo). Ver §10.
- **D5 — Establecimiento desde la sucursal.** Nuevo helper `SifenTimbradoHelper.codigoEstablecimiento(Sucursal)`
  = `sucursal.codigoEstablecimientoFactura` formateado `%03d`, fallback `"001"`. Lo usan NC y NR.
  **La ruta de factura no se toca** en este trabajo (deuda anotada, issue aparte).
- **D6 — NC: siempre contra una factura electrónica APROBADA con CDC; MVP = copia 1:1.** Motivo del
  catálogo `TiMotEmi` (enum Java propio `MotivoEmisionNotaCredito` espejo 1:1 por `name()`, con su
  `.graphqls`). Moneda/cambio heredados. _(✅ Confirmado el 2026-09-17: **solo NC total** en esta entrega.)_ **Fase 2.G (fuera de esta entrega): NC parcial** por
  selección de ítems y cantidades, con validación `cantidad ≤ cantidad facturada` por ítem y
  `totalNC ≤ totalFactura − Σ NC previas activas`. Sin NC standalone (SIFEN la permite contra
  documento impreso, pero no hay caso de negocio hoy).
- **D7 — NR: tres orígenes, una sola entidad.** _(✅ Confirmado el 2026-09-17: los tres entran en la Fase 1.)_ `origen` ∈ `TRANSFERENCIA` | `FACTURA` | `MANUAL`:
  - `TRANSFERENCIA` (motivo `TRASLADO_ENTRE_LOCALES`): precarga salida = sucursal origen, entrega =
    sucursal destino, receptor = **la propia empresa** (RUC del timbrado; SIFEN exige RUC receptor =
    emisor para motivo 7), vehículo/chofer desde `HojaRuta`, ítems desde `transferencia_item`
    (cantidad de la etapa actual). Guarda `transferencia_id`.
  - `FACTURA` (motivo `TRASLADO_POR_VENTAS`): precarga receptor = cliente de la factura, ítems de la
    factura, `gCamDEAsoc` con el CDC. Guarda `factura_legal_id` + `sucursal_id`.
  - `MANUAL`: todo editable (consignación, reparación, devolución a proveedor, etc.).
  Vehículo: FK opcional a `vehiculos.vehiculo` + snapshot `vehiculo_marca` (≤ 10 chars al emitir) /
  `vehiculo_matricula`. Chofer: FK opcional a `personas.persona` + snapshot nombre/documento/
  dirección. Transportista tercero: nombre/RUC/dirección manuales. Geografía de salida/entrega:
  `departamento` (nombre → `mapearDepartamento`), `codigo_ciudad`, `descripcion_ciudad`, `direccion`,
  precargados desde `TimbradoDetalle` de la sucursal (salida) y desde `Sucursal.ciudad.codigo` +
  `direccion` (entrega entre locales) o del cliente (`Persona`/`Cliente`).
- **D8 — Envío "en un paso" para el usuario, pero en TRES transacciones separadas.**
  `generarYEnviarNotaRemision(id, sucursalId)` / `generarYEnviarNotaCredito(...)` encadenan desde el
  resolver (sin `@Transactional` envolvente) tres llamadas que ya son transaccionales por separado
  en `SifenService` (`crearDocumentoElectronico`, `crearLote` + `vincularDocumentosALote`,
  `enviarLote` `[ev: SifenService.java — cada método lleva su propio @Transactional REQUIRED]`):
  1. **T1** — asignar número (D4) + persistir la nota + crear el DE con CDC/XML/QR (`PENDIENTE`).
     Commit. A partir de acá el número y el CDC existen en la base pase lo que pase con SIFEN.
  2. **T2** — crear `LoteDE` de un documento y vincularlo (`EN_LOTE`). Commit.
  3. **T3** — `enviarLote`: llamada a SIFEN; según respuesta el lote queda `EN_PROCESO` (protocolo)
     o `ERROR_ENVIO`/`ERROR_RED`, y **el DE queda `EN_LOTE`** (hoy `EstadoDE` no tiene estado de
     error `[ev: domain/financiero/enums/EstadoDE.java]`).
  **Por qué no una sola transacción** (hallazgo B1 de la auditoría): con un `@Transactional` que
  englobe todo, un timeout de *lectura* (SIFEN ya aceptó el lote, el cliente HTTP no vio la respuesta)
  haría rollback del número y del CDC; el reintento tomaría `MAX+1` = **el mismo número con otro CDC**
  → dos documentos en SIFEN bajo el mismo número comercial. Con T1 commiteada, el peor caso es un DE
  `EN_LOTE` con lote en error, recuperable.
  **Recuperación** (hallazgo B2: central **no tiene** `procesarLotesAtrasados`, solo el filial lo
  tiene `[ev: central SifenSchedulerService.java — dos métodos; filial :354]`; un lote `ERROR_ENVIO`
  queda huérfano porque `crearYEnviarLotes` busca DE `PENDIENTE` y `consultarLotesPendientes` lotes
  `EN_PROCESO`): `reenviarNota*(id, sucursalId)` se habilita cuando el DE está `PENDIENTE` **o**
  `EN_LOTE` con lote en `{ERROR_ENVIO, ERROR_RED, ERROR_PERMANENTE}` o `RECHAZADO` por error
  transitorio, y **antes de reenviar consulta `consultarDE(cdc)`** (existente, `:417`): si SIFEN ya
  lo tiene (`0422`), solo actualiza estado y no reenvía; si no (`0420`/`0421`), vincula a un lote
  nuevo y envía. Además, Fase 0.B porta a central `procesarLotesAtrasados` del filial (recupera
  lotes `PENDIENTE_ENVIO`/`ERROR_ENVIO`/`ERROR_RED` con más de 1 min) para que, con el scheduler
  habilitado, no haga falta el botón. El estado final (`APROBADO`/`RECHAZADO`) lo trae el scheduler de
  central **si está habilitado** (`sifen.scheduler.enabled`, default `false` en código; valor en
  producción **NO VERIFICADO**, §10) o el botón "Consultar" (`consultarLote` existente).
- **D9 — Seguridad por rol nueva y obligatoria.** _(✅ Confirmado el 2026-09-17: roles nuevos `FACTURACION *`.)_ `FacturacionSecurityService` (patrón
  `TesoreriaSecurityService`) con roles `FACTURACION VER`, `FACTURACION NR EMITIR`,
  `FACTURACION NC EMITIR`, `FACTURACION ANULAR` (seed idempotente, sin espejo). Primera línea de cada
  mutation nueva: `seg.requireAnyRole(...)`. Desktop: `visibilityRoles` en el menú y flags en
  `ngOnInit` vía `mainService.tieneAlgunRol`. Las mutations SIFEN preexistentes **no se tocan**
  (fuera de alcance; anotar en issue #177).
- **D10 — Impresión: KuDE PDF A4 por el padrón `ImpresionService` (`soloPdf=true`).** Query
  `imprimirNotaRemision(id, sucursalId, anchoMm, escpos)` / `imprimirNotaCredito(...)` que devuelve
  base64; en el MVP solo `escpos=false, anchoMm=null` (A4). Ticket 58/80 mm queda para después. Las
  plantillas se portan de frc-efact con `fontName="SansSerif"` en todos los `<font>` y se validan en
  local con `fillReport` de datos dummy (regla del repo: el build no compila `.jrxml`).
- **D11 — Cancelación e inutilización reutilizan lo existente.** Cancelar NC/NR =
  `cancelarDocumentoElectronico(cdc, motivo)` (ya genérico) + `activo=false` en la nota + validación
  local de ventana **168 h** (NC/NR no son factura). Inutilizar rangos = `inutilizarNumeros` pasando
  `tipoDE` (hoy la UI del desktop no deja elegir tipo: se agrega el selector). **Además**: en
  `cancelarFacturaLegal` de central se agrega la validación local de **48 h** desde
  `fechaRecepcionSifen`; si venció, el resolver devuelve `ERROR_PLAZO_NC` y el desktop ofrece "Emitir
  nota de crédito" (Fase 2.5).
- **D12 — Filial: espejo mínimo + guarda en los TRES métodos del scheduler.** Migración `V91.5`
  (solo `documento_electronico`: `DROP NOT NULL` de `factura_legal_id`, `ADD COLUMN
  nota_credito_id/nota_remision_id BIGINT NULL` **sin FK**, porque el filial no tiene las tablas de
  notas) + `@JoinColumn(nullable = true)` en su entidad. Guarda (hallazgos A2 y B5: hoy **ninguno**
  de los tres métodos filtra por sucursal ni por tipo, y el scheduler del filial está habilitado por
  default `[ev: filial SifenSchedulerService.java:50,137,235,365]`): un único predicado
  `esPropioDeEstaFilial(DE) = de.sucursalId == sucursalPropia && de.facturaLegal != null`, aplicado
  en `crearYEnviarLotes` (DE `PENDIENTE`), en `consultarLotesPendientes` (lotes `EN_PROCESO` cuyos DE
  cumplan **todos** el predicado) y en `procesarLotesAtrasados` (lotes `PENDIENTE_ENVIO`/`ERROR_ENVIO`/
  `ERROR_RED`, mismo criterio). El filtro solo por sucursal **no alcanza**: un lote de NC que central
  crea para la sucursal 3 y falla al enviarse llega a la filial 3 con `sucursal_id = 3` y estado
  `ERROR_ENVIO`, y `procesarLotesAtrasados` lo reenviaría (doble envío). Sin la guarda, un DE de NC
  replicado en `PENDIENTE` también sería reenviado por el filial.
- **D13 — Partición de ids en `documento_electronico`, `lote_de`, `evento_cancelacion_de`,
  `evento_nominacion_de` (Fase 0.D, prerrequisito de la Fase 1).** _(✅ Confirmado el 2026-09-17: entra en esta entrega.)_ Las dos auditorías confirmaron
  que R3 no es hipotético: las secuencias son `BIGSERIAL` planas sin reparto
  `[ev: V0__initial_schema.sql:2460-2472,2887-2903]`, `V223.1` no incluye estas tablas
  `[ev: V223.1:18-23,66-75]`, y central ya inserta DE con `sucursal_id` de cualquier filial vía
  `crearDocumentoElectronicoDesdeFactura(facturaId, sucursalId)` `[ev: DocumentoElectronicoGraphQL.java:79-94]`.
  Se extiende el esquema de `V223.1` (central impar, filial par, trigger `rechazar_id_de_filial`) a
  esas cuatro tablas, con su espejo en filial (`V91.7`, secuencias pares). **Orden**: primero el
  filial (pasa a pares; sus ids nuevos no chocan con nada), después central (pasa a impares). Entre
  ambos deploys no hay riesgo nuevo (los espacios viejos ya eran los de hoy). Nota: en el filial la
  PK de `documento_electronico` es **simple** (`id BIGSERIAL PRIMARY KEY`, `V34:5`, entidad sin
  `@IdClass`) mientras en central es compuesta `(id, sucursal_id)` (`V77:92`); la partición evita
  la colisión en los dos espacios sin necesidad de alinear la forma de la PK — alinearla queda como
  issue aparte (se anota en §Fuera de alcance).

---

## 3 · Tabla de datos nuevos (quién escribe, quién lee)

Regla del ciclo (paso 4): un dato con una sola punta no se implementa.

### 3.1 `financiero.documento_electronico` (central, espejo en filial)

| Columna | Escribe | Lee |
|---|---|---|
| `factura_legal_id` → nullable | (sin cambio de escritor) | `FacturaLegalResolver.documentoElectronico`, `SifenService.reconstruirDEDesdeFactura` (debe tolerar null: si es NC/NR, reconstruir desde la nota) |
| `nota_credito_id` (nullable, FK `(id, sucursal_id)`) | `DocumentoElectronicoService.createFromNotaCredito` | `NotaCreditoResolver.documentoElectronico`; `SifenService.enviarLote` fallback; `EventosSifen` (por CDC, indirecto) |
| `nota_remision_id` (nullable, FK) | `DocumentoElectronicoService.createFromNotaRemision` | `NotaRemisionResolver.documentoElectronico`; idem |
| `tipo_documento` = `NOTA_CREDITO` / `NOTA_REMISION` | los dos `createFrom*` nuevos | `LoteDEResolver` / desktop lista de lotes (columna "Tipo"); `SifenService.reconstruirDE*` para elegir la ruta |

### 3.2 `financiero.nota_remision` (central-only)

| Columna | Escribe | Lee |
|---|---|---|
| `id`, `sucursal_id` (PK) | `NotaRemisionService.crear` | todo |
| `timbrado_detalle_id`, `numero_nota_remision` | `NotaRemisionService.crear` (D4) | `SifenService.construirDEDesdeNotaRemision` (`gTimb`), KuDE, lista desktop |
| `origen` (`TRANSFERENCIA`/`FACTURA`/`MANUAL`), `transferencia_id`, `factura_legal_id` | desktop → `saveNotaRemision` | `construirDEDesdeNotaRemision` (`gCamDEAsoc`), botón "NR" de transferencia (para deshabilitarlo si ya existe) |
| `fecha`, `motivo_emision` (nombre `TiMotivTras`), `responsable_emision` (nombre `TiRespEmiNR`, default `EMISOR_FACTURA`), `km_estimado`, `fecha_inicio_traslado`, `fecha_fin_traslado`, `fecha_estimada_factura` | desktop | `gCamNRE`, `gTransp.dIniTras/dFinTras`, KuDE |
| receptor: `cliente_id`, `receptor_nombre`, `receptor_ruc`, `receptor_direccion`, `receptor_departamento`, `receptor_codigo_ciudad`, `receptor_ciudad` | desktop (precarga por origen) | `construirDatosReceptorNR` (`gDatRec` con `dDirRec`, `cDepRec`, `cCiuRec`), KuDE |
| salida: `salida_direccion`, `salida_departamento`, `salida_codigo_ciudad`, `salida_ciudad` | desktop (precarga desde `TimbradoDetalle` de la sucursal) | `gCamSal`, KuDE |
| entrega: `entrega_direccion`, `entrega_departamento`, `entrega_codigo_ciudad`, `entrega_ciudad` | desktop (precarga desde sucursal destino / cliente) | `gCamEnt`, KuDE |
| transporte: `tipo_transporte` (`PROPIO`/`TERCERO`), `modalidad_transporte` (`TERRESTRE`…), `transportista_nombre`, `transportista_ruc`, `transportista_direccion` | desktop | `gTransp`, `gCamTrans`, KuDE |
| vehículo: `vehiculo_id`, `vehiculo_marca`, `vehiculo_matricula` | desktop (selector + snapshot) | `gVehTras`, KuDE |
| chofer: `chofer_persona_id`, `chofer_nombre`, `chofer_documento`, `chofer_direccion` | desktop | `gCamTrans.dNomChof/dNumIDChof/dDirChof`, KuDE |
| `activo`, `usuario_id`, `creado_en` | service | lista, cancelación |

### 3.3 `financiero.nota_remision_item`

| Columna | Escribe | Lee |
|---|---|---|
| `id`, `sucursal_id`, `nota_remision_id` (FK compuesta) | service | resolver `notaRemisionItemList` |
| `producto_id`, `presentacion_id`, `codigo`, `descripcion`, `cantidad`, `unidad_medida` | desktop / precarga desde transferencia o factura | `gCamItem` (`dCodInt`, `dDesProSer`, `cUniMed`, `dCantProSer`), KuDE |

### 3.4 `financiero.nota_credito`

| Columna | Escribe | Lee |
|---|---|---|
| `id`, `sucursal_id`, `timbrado_detalle_id`, `numero_nota_credito` | `NotaCreditoService.crear` (D4) | `gTimb`, KuDE, lista |
| `factura_legal_id` (+ `sucursal_id`, FK compuesta, **NOT NULL**) | service (desde el botón de la factura) | `gCamDEAsoc` (CDC vía DE de la factura), lista de facturas (badge "tiene NC") |
| `cliente_id`, `nombre`, `ruc`, `direccion` (snapshot) | service (copia de la factura) | `construirDatosReceptor` (reusa `SifenReceptorHelper` con el `Cliente`), KuDE |
| `motivo_emision` (`MotivoEmisionNotaCredito`), `descripcion_motivo` | desktop | `gCamNCDE.iMotEmi`; `descripcion_motivo` solo KuDE |
| `moneda_extranjera`, `tipo_cambio` (heredados) | service (copia de la factura) | `gOpeCom` (`cMoneOpe`, `dTiCam`), ítems (conversión), KuDE |
| `iva_parcial_0/5/10`, `total_parcial_0/5/10`, `descuento`, `total_final` | service (copia 1:1 en MVP; recalculados en NC parcial) | KuDE, lista; **no** `gTotSub` (los calcula jsifenlib) |
| `fecha`, `activo`, `usuario_id`, `creado_en` | service | lista, cancelación |

### 3.5 `financiero.nota_credito_item`

| Columna | Escribe | Lee |
|---|---|---|
| `id`, `sucursal_id`, `nota_credito_id` | service | resolver |
| `factura_legal_item_id` (+ `sucursal_id`), `producto_id`, `presentacion_id`, `descripcion`, `cantidad`, `unidad_medida`, `precio_unitario`, `total`, `iva` | service (copia de `factura_legal_item`, con cantidad editada en NC parcial) | `gCamItem` + `gValorItem` + `GCamIvaMapper`, KuDE |

### 3.6 Roles (`personas.role`, `MAIN_TO_ALL`, sin espejo)

| Rol | Escribe | Lee |
|---|---|---|
| `FACTURACION VER`, `FACTURACION NR EMITIR`, `FACTURACION NC EMITIR`, `FACTURACION ANULAR` | seed `V225.5` | `FacturacionSecurityService` (central), `ROLES.*` + `visibilityRoles` (desktop) |

### 3.7 Properties nuevas (central; sin variable de entorno nueva → no requiere aviso previo al líder)

| Property | Default | Escribe | Lee |
|---|---|---|---|
| `sifen.nre.chofer-en-propio` | `true` | `application.properties` | `SifenService.construirTransporteNRE` |
| `sifen.nre.info-fiscal` | `"Documento emitido como Nota de Remision Electronica conforme RG 41/2014."` | idem | `gOpeDE.dInfoFisc` |

---

## 4 · Plan por fases

Convención: una fase = un commit + push (paso 7). "Tests" = lo que corre en `./mvnw clean verify -B
-DskipFlyway=true` (unitarios con Mockito; sin contexto Spring, igual que `SifenEstadoResultadoTest`).

### FASE 0 — Infraestructura común (central + filial)

**0.A Central — migración `V225.5__documento_electronico_notas_y_roles.sql`** (aditiva)
```sql
ALTER TABLE financiero.documento_electronico ALTER COLUMN factura_legal_id DROP NOT NULL;
ALTER TABLE financiero.documento_electronico
    ADD COLUMN IF NOT EXISTS nota_credito_id  BIGINT NULL,
    ADD COLUMN IF NOT EXISTS nota_remision_id BIGINT NULL;
-- FKs compuestas se agregan en V226.5/V227.5 cuando existan las tablas
INSERT INTO personas.role (nombre, creado_en) SELECT r.nombre, now() FROM (VALUES
  ('FACTURACION VER'), ('FACTURACION NR EMITIR'), ('FACTURACION NC EMITIR'), ('FACTURACION ANULAR')
) AS r(nombre) WHERE NOT EXISTS (SELECT 1 FROM personas.role pr WHERE upper(pr.nombre) = r.nombre);
```
Retrocompatible: la versión anterior del JAR sigue insertando `factura_legal_id` siempre.

**0.B Central — código base**
- **Spike bloqueante (antes de escribir entidad o migración)**: determinar cómo persiste hoy
  `documento_electronico.factura_legal_id`, dado que la única anotación es `@JoinColumns(...,
  insertable=false, updatable=false)` (`DocumentoElectronico.java:50-55`), `CrudService.save()` es
  `repository.save` puro (`service/CrudService.java:47-50`) y la columna es `NOT NULL`. Método:
  `spring-boot:run` local con `spring.jpa.show-sql=true` y ejecutar `crearDocumentoElectronicoDesdeFactura`;
  leer el `INSERT`. Resultado esperado: o hay un mecanismo no visto (listener, converter) que se
  replica para `nota_*_id`, o la mutation está rota en producción y se corrige mapeando columnas
  planas `facturaLegalId`/`notaCreditoId`/`notaRemisionId` (`@Column`) junto a las relaciones de
  solo lectura. Hasta que esto no esté resuelto, no se toca 0.A ni 0.B (hallazgo A3).
- `domain/financiero/DocumentoElectronico`: relaciones `notaCredito`, `notaRemision` (`@OneToOne`,
  `@JoinColumns` compuestas, `insertable=false`) **más** las columnas planas según el spike.
- Portar a central `procesarLotesAtrasados` del filial (`filial SifenSchedulerService.java:354`) y
  agregarlo a `procesarLotesAutomaticamente` como PASO 0 (D8).
- `TipoDocumentoElectronico` (constantes `FACTURA`, `NOTA_CREDITO`, `NOTA_REMISION`).
- `DocumentoElectronicoService.createFromNotaRemision/createFromNotaCredito`;
  `findByNotaRemisionId`, `findByNotaCreditoId`.
- `SifenService`: extraer `construirDatosEmisor(TimbradoDetalle)` (hoy recibe `FacturaLegal`),
  `obtenerFechaFirmaSegura(LocalDateTime)`, `SifenTimbradoHelper.codigoEstablecimiento(Sucursal)`,
  `reconstruirDE(DocumentoElectronico)` que despache por `tipoDocumento`;
  `generarYEnviarSincrono(DocumentoElectronico)` = `crearLote` + `vincularDocumentosALote` + `enviarLote`.
- `FacturacionSecurityService` con las 4 constantes + `requireVer()`, `requireEmitirNr()`,
  `requireEmitirNc()`, `requireAnular()`.
- `service/sifen/SifenNotasValidator` (vacío por ahora; recibe las validaciones de 1.B/2.B).
- **Tests**: `SifenTimbradoHelperTest` (`"1"`→`"001"`, null→`"001"`), `FechaFirmaSeguraTest`
  (fecha futura se recorta, fecha pasada se respeta), `FacturacionSecurityServiceTest` (ADMIN bypass,
  rol exacto, sin rol → `GraphQLException`), `DocumentoElectronicoServiceCreateFromNotaTest`
  (setea `tipoDocumento`, `sucursalId`, FK correcta, `factura_legal_id` null).

**0.C Filial — PR aparte (`fix/sifen-espejo-documento-electronico-notas`, desde `develop`)**
- `V91.5__espejo_documento_electronico_notas.sql`:
  ```sql
  ALTER TABLE financiero.documento_electronico ALTER COLUMN factura_legal_id DROP NOT NULL;
  ALTER TABLE financiero.documento_electronico
      ADD COLUMN IF NOT EXISTS nota_credito_id BIGINT NULL,
      ADD COLUMN IF NOT EXISTS nota_remision_id BIGINT NULL;
  ```
- Entidad `DocumentoElectronico`: `@JoinColumn(name="factura_legal_id", nullable = true)` (solo el
  flag; sin mapear las columnas nuevas).
- Guarda del scheduler (D12) en los **tres** métodos: `DocumentoElectronicoRepository.findByEstadoAndSucursalIdAndFacturaLegalIsNotNullOrderByIdAsc`
  para `crearYEnviarLotes` (`:137`); para `consultarLotesPendientes` (`:235`) y
  `procesarLotesAtrasados` (`:365`), filtrar los lotes cuyos DE cumplan **todos**
  `sucursalId == sucursalPropia && facturaLegal != null` (`sucursalId` leído de
  `application.properties` como hoy hace el resto del filial).
- **Tests** (filial): `SifenSchedulerServiceGuardaTest` con Mockito, un caso por método: un DE
  `PENDIENTE` sin factura y otro de otra sucursal **no** entran al lote; un lote `EN_PROCESO` y otro
  `ERROR_ENVIO` cuyos DE no tienen factura **no** se consultan ni se reenvían; revertida la guarda,
  los tres casos fallan (regla "test que falla con el código viejo").
- **Migración `V91.7__particion_ids_documento_electronico_par.sql`** (D13): las secuencias de
  `documento_electronico`, `lote_de`, `evento_cancelacion_de`, `evento_nominacion_de` pasan a
  `INCREMENT BY 2` desde el próximo **par** (espejo del `V223.1` de central, misma técnica `DO $$`
  con `GREATEST(MAX(id), last_value)`).
- **Impacto**: sale a alpha ≤ 15 min tras merge a `develop`; a **6 filiales farmacia** por
  `release/beta`; a **18 de bodega** por `master`. Es prerrequisito del despliegue de central (§7).

**0.D Central — migración `V225.7__particion_ids_documento_electronico_impar.sql`** (D13): mismas
cuatro tablas, secuencias a impares + trigger `configuraciones.rechazar_id_de_filial` (ya existe
desde `V223.1`) sobre cada una. Va en un archivo aparte de `V225.5` para poder revertir la decisión
sin tocar el resto de la Fase 0. Se aplica **después** de que el filial esté en `V91.7` en el canal.
Tests: `N/A` (DDL puro); verificación = dry-run contra copia de la base + `SELECT nextval` par/impar
en cada nodo.

### FASE 1 — Nota de Remisión Electrónica

**1.A Central — dominio + migración `V226.5__nota_remision.sql`**
- Tablas `financiero.nota_remision` y `nota_remision_item` (§3.2, §3.3): `PRIMARY KEY (id, sucursal_id)`,
  `id BIGSERIAL`, FKs compuestas a `factura_legal`, `timbrado_detalle(id)` (PK simple),
  `operaciones.transferencia(id)`, `vehiculos.vehiculo(id)`, `personas.persona(id)`, `personas.cliente(id)`;
  `UNIQUE (timbrado_detalle_id, numero_nota_remision)`; índices por `sucursal_id, fecha`,
  `transferencia_id`, `factura_legal_id`.
- `ALTER TABLE financiero.documento_electronico ADD CONSTRAINT fk_de_nota_remision FOREIGN KEY
  (nota_remision_id, sucursal_id) REFERENCES financiero.nota_remision(id, sucursal_id)`.
- Enums Java + `.graphqls` **en el mismo commit**: `MotivoEmisionNotaRemision` (espejo de
  `TiMotivTras`, 15 valores), `ResponsableEmisionNr` (5), `TipoTransporteNr` (`PROPIO`,`TERCERO`),
  `ModalidadTransporteNr` (4), `OrigenNotaRemision` (3). Persistidos como `VARCHAR` con
  `@Enumerated(STRING)` (sin enum PG → sin `@TypeDef`, sin `CHECK`). `SchemaEnumsSincronizadosTest`
  los cubre automáticamente si el `.graphqls` los declara con el mismo nombre.
- Entidades `NotaRemision`, `NotaRemisionItem` (`@IdClass(EmbebedPrimaryKey)`), repos con
  `findMaxNumeroByTimbradoDetalleId`, `findBySucursalIdAndActivoTrue(pageable + filtros)`,
  `existsByTransferenciaIdAndActivoTrue`.
- `NotaRemisionService`: `crear(NotaRemision, List<Item>)` con: `seg.requireEmitirNr()`; validaciones
  (≥ 1 ítem; timbrado activo + `isElectronico`; receptor con nombre y RUC/CI; si `origen=TRANSFERENCIA`
  → transferencia existe, no `CANCELADA`, sin NR activa previa, receptor = RUC del timbrado; si
  `origen=FACTURA` → factura con DE `APROBADO`); `numero` por D4 dentro de la misma transacción;
  snapshots de vehículo/chofer; `anular(id, sucursalId, motivo)` = `requireAnular()` + ventana 168 h +
  `cancelarDocumentoElectronico(cdc)` + `activo=false`.
- **Tests**: `NotaRemisionServiceTest` (numeración arranca en 1 y sigue el MAX; rechaza sin ítems;
  rechaza transferencia con NR previa; motivo 7 exige RUC = emisor; anular fuera de 168 h falla),
  `NotaRemisionNumeracionConcurrenciaTest` (dos hilos con el lock simulado no repiten número —
  unitario sobre el service con repo mockeado que respeta el orden).

**1.B Central — `SifenService` para NR**
- `crearDocumentoElectronicoNotaRemision(NotaRemision)` → `construirDEDesdeNotaRemision` +
  `construirDatosItemsNR` + `construirTransporteNR` + `construirDatosReceptorNR` +
  `SifenNotasValidator.validarNRE(DE)` (port literal de `validarDECompletoNRE` de frc-efact) →
  CDC/XML/QR → `createFromNotaRemision` → `PENDIENTE`.
- Reglas fijas del port (§1.3): `dInfoFisc`; `PYG`; sin `gCamCond`; ítems sin `gValorItem`/`gCamIVA`;
  sin `gTotSub`; `dMarVeh` ≤ 10 (`abreviarMarca`); `cCiuSal`/`cCiuEnt` ≠ 0 (si falta → error de
  validación con mensaje, **no** fallback silencioso a Asunción como en la referencia);
  `dFecEm` reglas de motivo 1; `dKmR ≥ 1` si va `dFecEm`; chofer según property; `iRespFlete`
  derivado; `gCamDEAsoc` solo si hay factura con CDC.
- `generarYEnviarNotaRemision(id, sucursalId)` (D8) y `reenviarNotaRemision(id, sucursalId)`.
- **Tests**: `SifenServiceNotaRemisionTest` construye el bean con un `SifenConfig` de prueba y
  asserta sobre el objeto (sin llamar a SIFEN): `iTiDE=7`, `dInfoFisc` presente, `gTotSub==null`,
  ítems sin `gValorItem`, `gTransp.gCamTrans.dNomChof` presente/ausente según la property,
  `dMarVeh` truncado, motivo `TRASLADO_ENTRE_LOCALES` mapeado (el caso que la referencia tiene roto),
  `gCamDEAsoc` presente solo con factura. `SifenNotasValidatorTest`: cada regla rechaza su caso.
  **Test de XML**: `generarXml` con certificado de prueba **NO VERIFICADO** si es viable sin `.pfx`
  en CI (§10) — si no, se valida en local con el certificado real contra el ambiente `TEST` de SIFEN
  antes del PR.

**1.C Central — GraphQL (`graphql/financiero/NotaRemisionGraphQL.java`, `graphql/financiero/nota-remision.graphqls`)**
- Queries: `notaRemision(id, sucursalId)`, `notaRemisiones(page, size, sucursalId?, fechaInicio?,
  fechaFin?, estadoDE?, origen?, texto?)` (paginado estándar), `notaRemisionPorTransferencia(transferenciaId)`,
  `prellenarNotaRemision(origen, referenciaId, sucursalId)` (devuelve un `NotaRemisionPrellenada`
  con receptor/salida/entrega/vehículo/chofer/ítems calculados en backend: **el desktop no arma
  datos fiscales**), `imprimirNotaRemision(id, sucursalId, anchoMm, escpos): String`
  (base64 PDF; `escpos=true` devuelve error "no soportado" en MVP).
- Mutations: `saveNotaRemision(input, items)`, `generarYEnviarNotaRemision(id, sucursalId)`,
  `reenviarNotaRemision(id, sucursalId)`, `anularNotaRemision(id, sucursalId, motivo)`.
- Field resolvers: `NotaRemisionResolver` (`items`, `documentoElectronico`, `sucursal`, `vehiculo`,
  `chofer`, `transferencia`, `facturaLegal`).
- `EventosSifenGraphQL.inutilizarNumeros`: agregar parámetro `tipoDE: TipoDeSifen` (enum GraphQL nuevo
  `FACTURA_ELECTRONICA | NOTA_DE_CREDITO_ELECTRONICA | NOTA_DE_REMISION_ELECTRONICA`). **Criterio de
  aceptación** (hallazgo A5): en el `.graphqls` el argumento va **sin `!`** y el resolver hace
  `tipoDE == null ? TTiDE.FACTURA_ELECTRONICA : …` — el desktop actual no envía esa variable
  `[ev: desktop evento-inutilizacion-de/graphql/graphql-query.ts:245-265]` y hoy el resolver la
  hardcodea `[ev: EventosSifenGraphQL.java:205-206]`. Un `!` sin default rompería al cliente viejo.
  Al ser enum GraphQL nuevo sin enum Java pareado (se mapea a `TTiDE` de la librería), verificar que
  `SchemaEnumsSincronizadosTest` no lo exija: si lo hace, crear el enum Java espejo `TipoDeSifen`.
- **Tests**: `NotaRemisionGraphQLSeguridadTest` (patrón `PagoLegacyGraphQLSeguridadTest`): cada
  mutation llama a `seg.*` antes de tocar el service; query `notaRemisiones` sin rol VER → excepción.

**1.D Central — KuDE NR**
- `reports/nota-remision-kude.jrxml` portado de frc-efact (`SansSerif`), `KudeNotaService.generarPdfNotaRemision`
  (parámetros de §B.5 de la referencia; QR con `QRCodeImageGenerator`).
- **Validación local obligatoria**: compilar + `fillReport` con datos dummy (patrón
  `ReciboLiquidacionService`), adjuntar el PDF al PR.

**1.E Desktop — UI NR** (detalle en `PLAN-NR-NC-DESKTOP.md`): lista + diálogo de alta (3 orígenes) +
botón "Nota de remisión" en `list-transferencia` / `edit-transferencia` + menú "Notas de remisión"
bajo Financiero → Caja y Operativa + roles + impresión por `ImpresionService`.

**1.F Prueba de runtime (local, no alpha)**: central `spring-boot:run` con perfil `dev` y
`sifen.ambiente=TEST`, desktop `ng serve -c web`. Casos: NR manual → APROBADO en SIFEN test; NR desde
transferencia (motivo 7) → APROBADO; NR desde factura → APROBADO con `gCamDEAsoc`; NR con marca > 10
chars; anular NR → evento aprobado; inutilizar rango de NR. Registrar CDCs en la descripción del PR.

### FASE 2 — Nota de Crédito Electrónica

**2.A Central — dominio + migración `V227.5__nota_credito.sql`**
- Tablas `financiero.nota_credito`, `nota_credito_item` (§3.4, §3.5) con PK compuesta, FK compuesta a
  `factura_legal` **NOT NULL**, `UNIQUE (timbrado_detalle_id, numero_nota_credito)`, FK desde
  `documento_electronico.nota_credito_id`, y el `CHECK` de D3 (hallazgo B4: estaba en el diseño y no
  en ningún SQL):
  ```sql
  ALTER TABLE financiero.documento_electronico
      ADD CONSTRAINT ck_documento_electronico_un_origen CHECK (
          (CASE WHEN factura_legal_id  IS NOT NULL THEN 1 ELSE 0 END +
           CASE WHEN nota_credito_id   IS NOT NULL THEN 1 ELSE 0 END +
           CASE WHEN nota_remision_id  IS NOT NULL THEN 1 ELSE 0 END) = 1
      ) NOT VALID;
  ALTER TABLE financiero.documento_electronico VALIDATE CONSTRAINT ck_documento_electronico_un_origen;
  ```
  Aditivo: toda fila existente tiene solo `factura_legal_id`; el JAR viejo sigue cumpliéndolo.
  **No** va en el espejo del filial (el filial nunca escribe `nota_*_id`, y un `CHECK` ahí solo
  agregaría un modo de falla al apply worker).
- Enum `MotivoEmisionNotaCredito` (8 valores, espejo de `TiMotEmi`) + `.graphqls` mismo commit.
- `NotaCreditoService.crearDesdeFactura(facturaId, sucursalId, motivo, descripcion, [itemsParciales])`:
  `requireEmitirNc()`; factura `activo`, con DE `APROBADO` y CDC; hereda cliente/snapshot/moneda/
  cambio; copia ítems y totales 1:1 (MVP); numeración D4; `anular` = 168 h + evento + `activo=false`.
  Regla: **una factura puede tener N notas de crédito activas** mientras `Σ total ≤ total factura`
  (en MVP 1:1 eso significa como máximo una activa).
- **Tests**: `NotaCreditoServiceTest` (hereda moneda USD y cambio; rechaza factura sin DE aprobado;
  rechaza segunda NC total; numeración; snapshot de cliente; totales idénticos a la factura).

**2.B Central — `SifenService` para NC**: `construirDEDesdeNotaCredito` (port §A.4 de la
referencia; `gCamCond` CONTADO con `gPaConEIni` EFECTIVO y moneda/cambio de la NC; ítems con
`GCamIvaMapper`; `gCamDEAsoc(ELECTRONICO, CDC)` en el DE raíz; `gTotSub` + `aplicarFixTotalesIVA`) +
`SifenNotasValidator.validarNCE`. `generarYEnviarNotaCredito`, `reenviarNotaCredito`.
- **Tests**: `SifenServiceNotaCreditoTest`: `iTiDE=5`, `gCamDEAsocList` con el CDC de la factura,
  moneda extranjera → `dTiCam` con 6 decimales y `cMoneTiPag`, ítems con IVA 10/5/exento vía
  `GCamIvaMapper`, sin `gCamCuotas`.

**2.C Central — GraphQL (`NotaCreditoGraphQL`, `nota-credito.graphqls`)**: `notaCredito`,
`notaCreditos(page, size, filtros)`, `notaCreditosPorFactura(facturaId, sucursalId)`,
`imprimirNotaCredito(id, sucursalId, anchoMm, escpos)`; mutations `crearNotaCreditoDesdeFactura(input)`,
`generarYEnviarNotaCredito`, `reenviarNotaCredito`, `anularNotaCredito`. `FacturaLegal` gana el campo
**nuevo** `notasCredito: [NotaCredito]` (aditivo). Tests de seguridad como en 1.C.

**2.D Central — KuDE NC** (`reports/nota-credito-kude.jrxml`, `SansSerif`, validación local).

**2.E Central — integración con la cancelación**: en `cancelarFacturaLegal` (`FacturaLegalGraphQL.java:1348`),
antes de `sifenEventoService.cancelarDE`, validar `fechaRecepcionSifen + 48 h > now()`; si venció
devolver `"ERROR_PLAZO_NC: …"` (mantiene el contrato `String` actual). **Test que falla con el código
viejo**: factura aprobada hace 72 h → hoy llama a SIFEN; con el fix devuelve `ERROR_PLAZO_NC` sin
llamar.

**2.F Desktop — UI NC**: botón "Nota de crédito" en la lista de facturas (solo `esElectronica` y
`activo`), diálogo (motivo + descripción + vista de ítems de la factura de solo lectura; NC parcial
si se aprueba 2.G), lista "Notas de crédito", estado SIFEN, impresión, y en `onCancelarFactura` el
manejo de `ERROR_PLAZO_NC` → ofrecer "Emitir nota de crédito".

**2.G (fuera de esta entrega, decidido el 2026-09-17) NC parcial**: ítems editables (cantidad ≤ facturada, quitar ítems),
recálculo de parciales por IVA en backend (misma fórmula que `FacturaLegal`: 10 % → `total/11`,
5 % → `total/21`), validación de saldo `Σ NC ≤ factura`. Tests de redondeo.

**2.H Prueba de runtime**: NC total sobre factura en PYG → APROBADO; NC sobre factura USD →
APROBADO; NC sobre factura sin DE → rechazada localmente; cancelar factura de 72 h → `ERROR_PLAZO_NC`;
anular NC → evento aprobado.

### Fuera de alcance (anotado, no olvidado)

- Impacto contable de la NC en **cuenta corriente del cliente / CPC** (`Cliente.saldoActual`,
  `venta_credito`): la NC fiscal no toca saldos en este trabajo. _(✅ Decidido el 2026-09-17: solo
  fiscal; el impacto en CPC es issue aparte con Tesorería, skill `frc-financiero-expert`.)_
- Nota de Débito (`iTiDE=6`), autofactura, NC contra factura **impresa** (`TiTipDocAso.IMPRESO`).
- Ticket térmico de NC/NR; envío por email.
- Emisión de notas desde el filial / desde la PWA.
- Corregir `dEst` hardcodeado en la ruta de factura (issue aparte).
- Control por rol de las mutations SIFEN preexistentes (issue #177).
- Alinear la PK de `documento_electronico` del filial a compuesta `(id, sucursal_id)` como en
  central (`V77`), y marcar `documento_electronico` como bidireccional en `replication_table`
  (hallazgos A4/B3): issue aparte, con su propio dry-run.

---

## 5 · Migraciones y numeración

| Repo | Archivo | Contenido | Retrocompatible con el JAR anterior |
|---|---|---|---|
| central | `V225.5__documento_electronico_notas_y_roles.sql` | `DROP NOT NULL`, 2 columnas nullable, seed de 4 roles | Sí (el JAR viejo sigue escribiendo `factura_legal_id`; ignora las columnas nuevas) |
| central | `V226.5__nota_remision.sql` | 2 tablas nuevas + FK desde `documento_electronico` | Sí (tablas nuevas sin lector viejo) |
| central | `V225.7__particion_ids_documento_electronico_impar.sql` | secuencias a impares + trigger en 4 tablas (D13) | Sí (solo cambia qué ids genera el nodo) |
| central | `V227.5__nota_credito.sql` | 2 tablas nuevas + FK + `CHECK` un-origen | Sí |
| filial | `V91.5__espejo_documento_electronico_notas.sql` | `DROP NOT NULL` + 2 columnas nullable, **sin FK** | Sí |
| filial | `V91.7__particion_ids_documento_electronico_par.sql` | secuencias a pares en 4 tablas (D13) | Sí |

Última migración en `develop` de central: `V224.3`; en `master` de filial: `V90.7`
`[ev: ls db/migration en ambos]`. Sin `DROP`/`RENAME`/cambio de tipo. `DROP NOT NULL` no figura en
la tabla de permitidos/prohibidos del `CLAUDE.md`: se clasifica como **relajación aditiva** (la fila
nueva es válida para el esquema viejo y el nuevo) y se deja explícito en la descripción del PR.
Sufijos: `.5` según la regla del `CLAUDE.md`; las últimas siete migraciones de `develop` usan `.1`/`.3`
por slotting entre ramas (hallazgo B6) — se aclara en el PR que se retoma `.5` a propósito, y `.7`
para las de partición para que queden después de las de esquema del mismo entero. El filial usa
`.5`/`.7` en su historial reciente (`V88.5`, `V89.5`, `V90.5`, `V90.7`), consistente.

**Dry-run obligatorio (paso 10)**: restaurar un dump reciente de `bodega` y de `farmacia` en local y
correr `flyway:migrate` con las tres migraciones de central; en filial, contra un dump de una filial.
Es la única validación de Flyway que existe (el CI corre con `-DskipFlyway=true`).

---

## 6 · Tests por fase (resumen)

| Fase | Tests nuevos | Comando |
|---|---|---|
| 0 | `SifenTimbradoHelperTest`, `FechaFirmaSeguraTest`, `FacturacionSecurityServiceTest`, `DocumentoElectronicoServiceCreateFromNotaTest`; filial `SifenSchedulerServiceGuardaTest` | central `./mvnw clean verify -B -DskipFlyway=true`; filial `./mvnw clean verify -B` |
| 1 | `NotaRemisionServiceTest`, `NotaRemisionNumeracionConcurrenciaTest`, `SifenServiceNotaRemisionTest`, `SifenNotasValidatorTest`, `NotaRemisionGraphQLSeguridadTest`, `SchemaEnumsSincronizadosTest` (existente, cubre los 5 enums nuevos) | idem |
| 2 | `NotaCreditoServiceTest`, `SifenServiceNotaCreditoTest`, `NotaCreditoGraphQLSeguridadTest`, `CancelarFacturaLegalPlazoTest` (falla con el código viejo) | idem |
| desktop | N/A: sin batería en CI; gate = `npm run check` (AOT) al final de todas las fases + prueba manual en browser (`ng serve -c web`) | `npm run check` |

---

## 7 · Orden de PRs, canales y despliegue

**Dirección que manda**: los DE de NC/NR los escribe central en `documento_electronico`, que baja a
las filiales por `central_pub` (sin filtro) y `central_filialN_pub` (filtrada) → **el filial tiene
que estar actualizado antes de que central inserte la primera fila con `factura_legal_id NULL`**; si
no, el apply worker de todas las filiales del canal muere con "null value in column factura_legal_id
violates not-null" y la réplica se corta (mismo mecanismo que el incidente V192.5 / V90.7).
`[ev: V0 ALTER PUBLICATION central_pub ADD TABLE financiero.documento_electronico; V112 MAIN_TO_ALL; filial V34 NOT NULL]`

⚠️ **`documento_electronico` es bidireccional de facto** (hallazgo A4): `replication_table` la
cataloga como `MAIN_TO_ALL` sin el flag `replicate_central_to_branch_with_filter` `[ev: V112:52; V113:10-19]`,
pero cada filial la publica hacia central en `filialN_pub` con `REPLICA IDENTITY FULL`
`[ev: filial V50:39-42; V52]`. Para este plan no cambia el orden (las filas que suben del filial
siempre tienen `factura_legal_id`), pero sí el alcance de D13. Corregir el catálogo
(`UPDATE replication_table SET replicate_central_to_branch_with_filter = true WHERE table_name =
'financiero.documento_electronico'`) es una escritura de configuración sobre una tabla que lee
`LogicalReplicationService`: **issue aparte**, no en este PR.

1. **PR filial** (`fix/sifen-espejo-documento-electronico-notas` → `develop`): `V91.5` (espejo) +
   `V91.7` (partición a pares) + guarda del scheduler. Merge → alpha en ≤ 15 min. Promover a
   `release/beta` (6 filiales farmacia) y `master` (18 bodega) **antes** de desplegar central en
   esos canales. Nada escribe todavía en las columnas nuevas: `V91.5` es solo DDL; `V91.7` solo
   cambia la paridad de los ids que el filial genera de ahí en adelante (no choca con nada).
2. **PR central** (`feature/sifen-nota-remision-nota-credito` → `develop`; opcionalmente dos PRs:
   Fase 0+1 y Fase 2, cada uno < 400 líneas netas de código productivo — las plantillas `.jrxml` se
   cuentan aparte). Deploy manual por workflow `Deploy` (`alpha` sin reviewer; `farmacia`/`bodega`
   con 1 reviewer). **Precondición del deploy**: `SELECT is_nullable FROM information_schema.columns
   WHERE table_name='documento_electronico' AND column_name='factura_legal_id'` = `YES` en **todas**
   las filiales del canal (query del runbook de réplicas).
3. **PR desktop** (`feature/sifen-nota-remision-nota-credito` → `develop`): el cliente pide
   operaciones (`notaRemisiones`, `crearNotaCreditoDesdeFactura`) que el central de su canal tiene
   que tener → **cliente y backend van juntos por canal**; nunca promover el desktop a `beta` antes
   que el central de farmacia.
4. **Roles**: asignar `FACTURACION *` a los usuarios por la pantalla de usuarios **después** del
   deploy de central (el seed solo crea los roles).

Reinicios: central → `systemctl restart frc-<instancia>.service` (lo hace el workflow, health check
500 s + rollback); filial → se reinicia sola en cada host del canal; desktop → diálogo "Cerrar y
actualizar" a los 5 min.

**Rollback**: central a la versión anterior sigue funcionando con el esquema nuevo (D3). Las notas ya
emitidas quedan en la base y en SIFEN; con el JAR viejo no se ven en la UI (no hay lector) pero no
rompen nada. Filial: idem.

---

## 8 · Riesgos

| # | Riesgo | Mitigación en este plan |
|---|---|---|
| R1 | **Réplica cortada** por insertar DE con `factura_legal_id NULL` en una filial sin espejo | Orden §7 + precondición de deploy verificada por query |
| R2 | **Doble envío a SIFEN**: el scheduler del filial toma un DE de nota replicado en `PENDIENTE` | D8 (el DE nace y se envía en la misma operación → replica como `EN_LOTE`) + guarda D12 en filial |
| R3 | **Colisión de PK en `documento_electronico`/`lote_de`/`evento_*`** — **confirmado por las dos auditorías, no hipotético**: secuencias `BIGSERIAL` planas sin reparto, `V223.1` no las incluye, y central ya inserta DE con `sucursal_id` de cualquier filial vía `crearDocumentoElectronicoDesdeFactura`; el filial N escribe en el mismo espacio para su sucursal. Agravante: la PK del filial es simple (`id`) y la de central compuesta `(id, sucursal_id)`. NC/NR multiplican la frecuencia | **D13 / Fase 0.D + filial `V91.7`, prerrequisito de la Fase 1** (dejó de ser "a confirmar"; queda en §11 solo la confirmación de alcance porque toca 24 filiales). Si se decide NO incluirlo, el plan lo anota como riesgo aceptado con el `SKIP (lsn)` del runbook como único remedio |
| R11 | **Duplicado fiscal por rollback tras aceptación de SIFEN** (hallazgo B1): una sola transacción que englobe numeración + DE + envío revierte el número si el timeout llega después de que SIFEN aceptó; el reintento reusa `MAX+1` con otro CDC | D8 reescrito: tres transacciones separadas (T1 número+DE, T2 lote, T3 envío) y `consultarDE(cdc)` antes de todo reenvío |
| R12 | **Lote huérfano en `ERROR_ENVIO`** (hallazgo B2): central no tiene `procesarLotesAtrasados`; el DE queda `EN_LOTE` y nadie lo retoma | `reenviarNota*` habilitado para `EN_LOTE` + lote en error; se porta `procesarLotesAtrasados` a central (0.B); el desktop muestra "Reenviar" también en ese estado |
| R4 | SIFEN rechaza por un campo que la referencia no cubre (ej. `cCondNeg` omitido, chofer en propio) | Property `sifen.nre.chofer-en-propio`; validador local con mensajes explícitos; prueba en ambiente TEST antes del PR; **no** fallbacks silenciosos |
| R5 | El timbrado electrónico vigente **no habilita** NC/NR ante la SET | Verificar con el contador antes de la Fase 1.F; si no, solicitar ampliación del timbrado — no es un cambio de código |
| R6 | Tests de `generarXml` no viables en CI (certificado) | Los tests unitarios assertan sobre el **bean** antes de firmar; el XML se prueba en local |
| R7 | `.jrxml` roto en producción (no se compila en build) | Validación local con `fillReport` + PDF adjunto al PR (regla del repo) |
| R8 | Cambio de contrato: `documentoElectronico.facturaLegal` puede ser `null` para el desktop actual | El desktop solo lo lee vía `FacturaLegal.documentoElectronico` (nunca al revés) `[ev: desktop documento-electronico.model.ts / list-lote-de]`; la lista de lotes muestra `numeroDocumento` + `tipoDocumento` (String ya existente) |
| R9 | Numeración duplicada bajo concurrencia | Lock pesimista sobre `timbrado_detalle` + `UNIQUE` |
| R10 | Lectores de `DocumentoElectronico.getFacturaLegal()` con un DE de NC/NR (`facturaLegal == null`) | **Verificado en la auditoría** (hallazgos A-c1 / B7): los tres usos reales — `SifenService.java:955`, `:1346` (`reconstruirDEDesdeFactura`) y `SifenEventoService.java:543` (`nominarReceptor`) — ya son null-safe (lanzan `IllegalArgumentException` o loguean y retornan). `SifenEventoService.cancelarDE` **no** toca `FacturaLegal` (la versión anterior de esta tabla lo citaba mal; el `factura.setActivo(false)` vive en `FacturaLegalGraphQL.cancelarFacturaLegal:1367-1403` sobre una factura buscada por id, que NC/NR nunca invocan). Queda: `reconstruirDE` despacha por `tipoDocumento` (0.B) para que el fallback de `enviarLote` funcione con notas |

---

## 9 · Hallazgos de la auditoría del plan (paso 5)

Dos auditores independientes, con el plan, las skills, `gotchas.md` y el código real como insumo;
corrieron sin verse. Cada hallazgo dice qué se hizo con él en esta versión del plan.

### Eje A — Contrato y propagación

| # | Severidad | Hallazgo | Qué se hizo |
|---|---|---|---|
| A1 | ALTA | R3 (colisión de PK) es real y ya explotable hoy: secuencias planas, `V223.1` no cubre estas tablas, `crearDocumentoElectronicoDesdeFactura` acepta cualquier `sucursalId` | **Incorporado**: D13 + Fase 0.D + filial `V91.7`, prerrequisito de la Fase 1. Queda en §11 solo la confirmación de alcance |
| A2 | MEDIA-ALTA | La guarda del filial (D12) solo cubría `crearYEnviarLotes`; `consultarLotesPendientes` y `procesarLotesAtrasados` tampoco filtran hoy, y el scheduler del filial está habilitado por default | **Incorporado**: D12 y Fase 0.C reescritos con el predicado en los tres métodos y un test por método |
| A3 | MEDIA | No se sabe cómo persiste hoy `factura_legal_id` (mapping `insertable=false`); el plan repetía el patrón para las FK nuevas | **Incorporado**: spike bloqueante al inicio de 0.B, con método de verificación |
| A4 | MEDIA | `documento_electronico` es bidireccional de facto (filial `V50`/`V52`) aunque el catálogo diga `MAIN_TO_ALL` | **Incorporado** en §7 como advertencia; la corrección del catálogo va como issue aparte (es una escritura de configuración) |
| A5 | BAJA | `tipoDE` de `inutilizarNumeros` debe ir sin `!` y con default en el resolver, como criterio verificable | **Incorporado** en 1.C |
| A6 | BAJA | El modelo TS del desktop tipa `facturaLegal` como no opcional | **Incorporado** en el plan del cliente (1.5) |
| A-c1 | — | R10 citaba `SifenEventoService.cancelarDE` como si tocara la factura: falso | **Corregido** en R10 |

### Eje B — Reversibilidad y estado

| # | Severidad | Hallazgo | Qué se hizo |
|---|---|---|---|
| B1 | ALTA | Un `@Transactional` que englobe número + DE + envío puede duplicar el número fiscal si el timeout llega después de que SIFEN aceptó | **Incorporado**: D8 reescrito en tres transacciones; nuevo R11 |
| B2 | ALTA | Central no reintenta lotes `ERROR_ENVIO` (no tiene `procesarLotesAtrasados`); `reenviarNota*` no cubría `EN_LOTE` | **Incorporado**: D8 (`reenviar` para `EN_LOTE` + lote en error, `consultarDE` previo), 0.B porta `procesarLotesAtrasados`; nuevo R12; plan del cliente actualizado |
| B3 | ALTA | PK simple en filial vs compuesta en central; la partición no alinea la forma de la PK | **Incorporado** en D13 como nota; alinear la PK del filial queda en "Fuera de alcance" como issue aparte |
| B4 | MEDIA | El `CHECK` "exactamente una FK" de D3 no aparecía en ningún SQL | **Incorporado** en `V227.5` (2.A) con `NOT VALID` + `VALIDATE` |
| B5 | MEDIA | `procesarLotesAtrasados` del filial necesita el filtro "todos los DE tienen factura", no solo el de sucursal | **Incorporado** (mismo cambio que A2) |
| B6 | BAJA | Las últimas siete migraciones usan `.1`/`.3`; retomar `.5` puede confundir a un revisor | **Incorporado** en §5 (nota para el PR) |
| B7 | BAJA | Misma cita errónea de R10; los tres usos reales de `getFacturaLegal()` ya son null-safe | **Corregido** en R10 |

**Contradicciones entre auditores**: ninguna. Los dos convergen en que R3 debe decidirse ahora
(no "a confirmar") y en que la guarda del filial debe cubrir los tres métodos.

---

## 10 · Qué queda sin verificar y cómo se verificaría

1. **Cómo persiste central hoy `documento_electronico.factura_legal_id`** si la única anotación es
   `@JoinColumn(..., insertable=false, updatable=false)` (`DocumentoElectronico.java:53`). Verificar
   leyendo `DocumentoElectronicoService.save`/`CrudService` y el log SQL de un
   `crearDocumentoElectronicoDesdeFactura` en local. Condiciona cómo se mapean `nota_*_id`.
2. **`sifen.scheduler.enabled` en producción** (default `false` en código): confirmar con
   `actuator/env` o el `application.properties` de las instancias `:8081`/`:8082`. Si está apagado,
   el estado final de NC/NR se obtiene solo con el botón "Consultar" (D8 lo contempla).
3. **`cCondNeg` omitido y `iRespFlete` derivado**: probar contra SIFEN TEST en 1.F; si rechaza,
   restaurar los valores fijos de la referencia.
4. **Timbrado habilita NC y NR** (R5): consulta al contador / portal Marangatu.
5. **Viabilidad de `generarXml` en CI sin certificado** (R6).
6. **Uso de `documentoElectronico`/`facturaLegal` en mobile-pwa y mobile**: grep pendiente (repos no
   clonados en esta sesión). El cambio es aditivo, pero el eje A lo exige.
7. **Filial: qué filtra hoy `procesarLotesAtrasados`** (no se leyó línea a línea) — la guarda D12 se
   aplica a los tres métodos igual.
8. **Publicaciones reales de `documento_electronico` en cada filial** (`filialN_pub` vs
   `farmacia_filialN_pub`, gotcha de naming): antes del deploy, `SELECT * FROM pg_publication_tables
   WHERE tablename='documento_electronico'` en central y en una filial de cada red.

---

## 11 · Decisiones tomadas (2026-09-17, Gabriel)

Las seis preguntas abiertas del plan se resolvieron en sesión; todas con la opción recomendada.

| # | Decisión | Respuesta | Efecto en el plan |
|---|---|---|---|
| 1 | D1 — quién emite | **Solo central**; el filial recibe espejo DDL + guarda | Sin cambios: es el diseño base |
| 2 | D13 / Fase 0.D — partición impar/par de ids en `documento_electronico`, `lote_de`, `evento_cancelacion_de`, `evento_nominacion_de` | **Entra en esta entrega** (central `V225.7` + filial `V91.7`) | Prerrequisito de la Fase 1; el PR del filial lleva las dos migraciones |
| 3 | D6 — NC parcial | **Solo NC total** (copia 1:1 de la factura) | 2.G sale de la entrega; el diálogo de NC muestra los ítems de solo lectura |
| 4 | D7 — orígenes de NR | **Los tres** (transferencia, factura, manual) | Sin cambios: la Fase 1 incluye `prellenarNotaRemision` para los tres |
| 5 | D9 — roles | **Roles nuevos `FACTURACION VER / NR EMITIR / NC EMITIR / ANULAR`** | Seed en `V225.5`; `ROLES.*` nuevos en el desktop; `CREAR/EDITAR FACTURAS` siguen sin uso |
| 6 | NC y cuenta corriente (CPC) | **Solo fiscal** en esta entrega | Sigue en "Fuera de alcance"; issue aparte con Tesorería |

Con esto el plan queda **aprobado para implementar** (paso 6 del ciclo). Próximo paso: Fase 0 en el
orden de §7 (PR del filial primero), empezando por el spike de 0.B sobre la persistencia de
`factura_legal_id`.

---

## 12 · Entrega entre repos y prompt de arranque para el agente que implementa

Este plan cruza **tres repos** y cada uno lleva su propia rama y su propio archivo de plan. Ningún
repo se entiende solo: el orden de merges y despliegues lo fija la dirección de replicación (§7).

### 12.1 · Dónde vive cada pieza

| Repo | Rama de trabajo (sale de `develop`) | Archivo del plan en esa rama | Qué contiene |
|---|---|---|---|
| central `GabFrank/franco-system-backend-servidor` | `feature/sifen-nota-remision-nota-credito` | `docs/manuales-implementacion/sifen/PLAN-NOTA-REMISION-NOTA-CREDITO.md` (**este**) | Arquitectura, decisiones, Fases 0/1/2 de backend, migraciones, riesgos, auditoría, orden de despliegue |
| filial `GabFrank/franco-system-backend-filial` | `fix/sifen-espejo-documento-electronico-notas` | `docs/manuales-implementacion/sifen/PLAN-ESPEJO-NR-NC-FILIAL.md` | Fase 0.C/0.D del filial: `V91.5`, `V91.7`, guarda del scheduler, tests |
| desktop `GabFrank/frc-sistemas-integrados-angular` | `feature/sifen-nota-remision-nota-credito` | `docs/manuales-implementacion/sifen/PLAN-NR-NC-DESKTOP.md` | Fases 1 y 2 de UI |

Los tres archivos se **borran en el PR final de cada repo** (paso 11 del ciclo); lo que sobrevive
va a los docs de dominio.

### 12.2 · Entrelazado de entregas (qué se mergea cuándo)

```
ENTREGA A — Fase 0 (infraestructura)
  1. filial   commits V91.5 + V91.7 + guarda + tests ──► PR a develop ──► merge ──► alpha ≤15 min
  2. central  spike factura_legal_id → V225.5 → 0.B → V225.7 (0.D)  (commits en la rama; sin PR aún)
  ⚠ central NO se despliega en un canal hasta que TODAS las filiales de ese canal estén en V91.5
    (query de precondición en §7).

ENTREGA B — Fase 1 (Nota de Remisión)
  3. central  1.A → 1.B → 1.C → 1.D → prueba local contra SIFEN TEST (1.F) ──► PR a develop
              (Fase 0 + Fase 1 juntas; si supera ~400 líneas netas de código, dos PRs: 0 y 1)
              ──► merge ──► deploy alpha (workflow Deploy, sin reviewer)
  4. desktop  1.1 → 1.7 → npm run check ──► PR a develop ──► merge (SIEMPRE después del central)
              ──► auto-update alpha en ≤5 min

ENTREGA C — Fase 2 (Nota de Crédito)
  5. central  2.A → 2.E → prueba local (2.H) ──► PR ──► merge ──► deploy alpha
  6. desktop  2.1 → 2.6 → npm run check ──► PR ──► merge

PROMOCIÓN POR CANAL (alpha → farmacia/release-beta → bodega/master), siempre en este orden:
  filial (se propaga solo, sin gate humano) → central (Deploy con 1 reviewer) → desktop
  "Cliente y backend van juntos por canal": nunca promover el desktop antes que su central.
```

Reglas del entrelazado que no se negocian:

- **El PR del filial se mergea primero y se promueve primero.** Es la única pieza que se propaga
  sola (≤15 min, 6 filiales farmacia / 18 bodega según la rama). Su contenido es solo DDL aditivo
  y una guarda: no depende de nada de central.
- **Central no escribe una fila de nota hasta que el canal entero tenga `V91.5`.** Una sola filial
  atrasada corta la réplica de esa filial (R1).
- **Desktop nunca antes que central en el mismo canal** (incidente v4.1.0, 2026-08-19).
- **Nada se mergea para "probar en alpha"**: alpha es compartido y recibe trabajo terminado (§3.4
  del ciclo). La prueba de runtime es local: central `spring-boot:run` (perfil `dev`,
  `sifen.ambiente=TEST`), desktop `ng serve -c web`.
- **Un PR por repo**, no un PR por fase (salvo que el de central supere las 400 líneas netas).

### 12.3 · Prompt de arranque (copiar y pegar como primer mensaje al agente nuevo)

```
Sos el agente que implementa "Nota de Remisión y Nota de Crédito electrónicas SIFEN" en FRC
Comercial. El plan ya está escrito, auditado con dos ejes (paso 5 del ciclo) y aprobado por
Gabriel el 2026-09-17 (paso 6), con las seis decisiones abiertas ya tomadas. NO lo vuelvas a
planificar ni reabras esas decisiones: arrancás en el paso 7 (implementación por fases) del ciclo
de 12 pasos.

1. Cargá las skills, en este orden: frc-cicd (y leé entero
   frc-cicd/ciclo-implementacion-frc-comercial.md, §1 y §3), frc-central, frc-filial,
   frc-desktop y frc-efact-expert (de esta última solo domains/notas-cdr.md, sifen-core.md,
   transporte-remision.md y conventions/sifen-gotchas.md: frc-efact es la implementación de
   referencia, no un repo a tocar).

2. Leé los tres planes, en este orden, completos:
   - central, rama feature/sifen-nota-remision-nota-credito:
     docs/manuales-implementacion/sifen/PLAN-NOTA-REMISION-NOTA-CREDITO.md (plan maestro)
   - filial, rama fix/sifen-espejo-documento-electronico-notas:
     docs/manuales-implementacion/sifen/PLAN-ESPEJO-NR-NC-FILIAL.md
   - desktop, rama feature/sifen-nota-remision-nota-credito:
     docs/manuales-implementacion/sifen/PLAN-NR-NC-DESKTOP.md
   Las tres ramas ya existen y salen de develop. Trabajá sobre ellas; antes de empezar cada
   repo hacé git fetch origin develop y traé develop a la rama (merge, no rebase) para no probar
   una combinación que nunca va a existir en producción.

3. Orden de trabajo (§12.2 del plan maestro; no se altera):
   ENTREGA A: filial primero (V91.5, V91.7, guarda en los tres métodos del scheduler, test que
   falla sin la guarda) → PR a develop del filial. Después central Fase 0: EMPEZÁ POR EL SPIKE de
   0.B (cómo persiste hoy documento_electronico.factura_legal_id con el mapping insertable=false)
   y no toques entidad ni migración hasta tener la respuesta con evidencia.
   ENTREGA B: central Fase 1 (Nota de Remisión) → prueba local contra SIFEN TEST → PR central →
   desktop Fase 1 → npm run check → PR desktop.
   ENTREGA C: central Fase 2 (Nota de Crédito) → PR → desktop Fase 2 → PR.
   Promoción por canal siempre filial → central → desktop.

4. Reglas duras mientras codeás (están en el ciclo y en los CLAUDE.md; las más caras acá):
   - Nunca push ni merge a develop, release/beta o master; nunca mergeás vos un PR; nunca
     proponés mergear para destrabarte. Una fase = un commit + push a la rama de trabajo.
   - Migraciones con sufijo .5 (y .7 para las de partición), aditivas, nunca modificar una ya
     aplicada. Dry-run de Flyway contra una copia de la base real antes de cada PR (el CI no
     valida Flyway).
   - Un enum Java nuevo va con su .graphqls en el mismo commit (SchemaEnumsSincronizadosTest).
   - Toda mutation nueva empieza con FacturacionSecurityService.require*(...) (no hay
     @PreAuthorize en el repo; @AdminSecured está roto, issue #177).
   - GraphQL, no REST. Sin variables de entorno nuevas (el plan usa solo properties con default).
   - .jrxml solo con fontName="SansSerif", validados en local con fillReport antes de pushear.
   - Desktop: nada de funciones en el HTML; roles en ngOnInit; npm run check una sola vez al
     final, redirigido a archivo y leído entero.
   - Filial: lo que mergeás a develop sale solo a las filiales alpha en ≤15 min.
   - Auditoría del diff (paso 8: 3 ejes fijos + condicionales) y batería (paso 9) antes de cada
     PR; PR con las seis secciones (Qué resuelve / Cómo probarlo / Impacto en DB / Impacto en
     rollback / Riesgo / Nota de despliegue). CI verde sobre el SHA final (gh pr checks).

5. Cuando algo no se pueda cumplir, avisá en el momento y anotalo en el plan del repo (el plan
   es registro, un paso incumplido no se borra). Lo que el plan marca como NO VERIFICADO (§10)
   se verifica en el momento en que la fase lo necesita, y el resultado se escribe ahí.

6. Antes de la Fase 1.F (prueba contra SIFEN TEST) confirmá con Gabriel que el timbrado
   electrónico vigente habilita Nota de Crédito y Nota de Remisión ante la SET (R5): no es un
   problema de código y bloquea la prueba real.

Empezá por el punto 1 y reportá, antes de escribir código, un resumen de 10 líneas de lo que
entendiste del entrelazado entre repos y qué vas a hacer primero.
```

---

## 13 · Registro de implementación

### 13.1 · Spike 0.B — cómo persiste hoy `documento_electronico.factura_legal_id` (2026-09-17)

**Resultado: no persiste. La mutation `crearDocumentoElectronicoDesdeFactura` está rota en central,
y `vincularDocumentosALote` tampoco escribe el lote.** Hallazgo A3 cerrado.

Método: `spring-boot:run` local con perfil `dev` (puerto 8081, DB `bodega@5551`, los dos schedulers
de replicación en «Did not match» del reporte de condiciones) y
`--logging.level.org.hibernate.persister.entity=DEBUG`, que imprime el SQL estático de cada entidad
al arrancar. No hizo falta llamar a SIFEN ni tener certificado.

```
Static SQL for entity: com.franco.dev.domain.financiero.DocumentoElectronico
 Insert 0: insert into financiero.documento_electronico (activo, actualizado_en, cdc,
   codigo_respuesta_sifen, creado_en, estado, fecha_emision, fecha_recepcion_sifen,
   mensaje_respuesta_sifen, numero_documento, tipo_documento, url_qr, usuario_id,
   xml_firmado, xml_original, id, sucursal_id) values (...)
 Update 0: update financiero.documento_electronico set activo=?, ... where id=? and sucursal_id=?
```

Ni `factura_legal_id` ni `lote_de_id` aparecen en el INSERT **ni en el UPDATE**: las dos relaciones
están mapeadas solo con `@JoinColumns(insertable = false, updatable = false)`
(`DocumentoElectronico.java`). Como la columna es `NOT NULL` en central
(`V0__initial_schema.sql:2433`), cualquier INSERT desde central falla con
`null value in column "factura_legal_id"`.

Evidencia de datos, coherente: en `bodega@5551` las 534.741 filas de `documento_electronico`
pertenecen a sucursales de filiales (1, 3, 7, 9, 11, 12…) y **ninguna a la sucursal 0 (SERVIDOR)**.
Todas llegaron por replicación desde las filiales, donde la misma entidad sí mapea
`factura_legal_id` como columna escribible. Central nunca creó un DE propio.

**Consecuencia para el plan** (afecta 0.A, 0.B y D8, no las decisiones de §11):

1. Las entidades de central necesitan **columnas planas escribibles** —`facturaLegalId`,
   `notaCreditoId`, `notaRemisionId`, `loteDeId`— junto a las relaciones de solo lectura. Sin eso,
   NR y NC nacen sin FK y sin lote, y el T2 de D8 no vincula nada.
2. `vincularDocumentosALote` de central (`SifenService`) **no funciona hoy**: `setLoteDe(lote)` +
   `save` no escribe `lote_de_id`. Es prerrequisito del envío de notas, así que se arregla en 0.B
   con la misma columna plana. Para las facturas no cambia nada (ese camino lo ejecuta el filial).
3. La UNIQUE `(factura_legal_id, sucursal_id)` y el `CHECK` de D3 siguen igual.
4. `crearDocumentoElectronicoDesdeFactura` queda operativa de rebote. Se prueba en 1.F.

Queda **sin verificar**: si alguna vez se intentó usar esa mutation en producción (no hay filas de
central, así que o nunca se usó o siempre falló).

### 13.2 · Filial (Entrega A) — ver `filial/docs/manuales-implementacion/sifen/PLAN-ESPEJO-NR-NC-FILIAL.md` §7

Numeración real: **`V95.1`** (espejo) y **`V96.1`** (ids pares), no `V91.5`/`V91.7`: `V91.5` ya
existía (`espejo_formato_qr_pos`) y la mayor de `develop` era `V94.1`. En central, por el mismo
criterio, las migraciones de este trabajo arrancan en **`V225.1`** con sufijo `.1` (convención de
Franco) y un entero por migración: `V225.1` (espejo + roles), `V226.1` (ids impares), `V227.1`
(nota de remisión), `V228.1` (nota de crédito).

### 13.3 · Fase 0.A + primera mitad de 0.B (2026-09-17)

Commiteado en `feature/sifen-nota-remision-nota-credito`. Numeración `.1` (convención de Franco).

- **`V225.1__documento_electronico_notas_y_roles.sql`**: `factura_legal_id` a nullable, columnas
  `nota_credito_id` / `nota_remision_id` (sin FK todavía: van con las tablas, en `V227.1` / `V228.1`,
  igual que el `CHECK` de un solo origen) y seed idempotente de los cuatro roles `FACTURACION *`.
- **`DocumentoElectronico`**: nacen las cuatro columnas planas escribibles (`facturaLegalId`,
  `notaCreditoId`, `notaRemisionId`, `loteDeId`) junto a las relaciones de solo lectura, y
  `setFacturaLegal` / `setLoteDe` se escriben a mano para mantenerlas en sincronía. Esto **arregla
  de paso dos caminos rotos de central** (§13.1): crear un DE propio y vincularlo a un lote.
- **`TipoDocumentoElectronico`** (constantes `FACTURA` / `NOTA_CREDITO` / `NOTA_REMISION`), sin enum
  para no arrastrar `.graphqls` ni `CHECK`.
- **`FacturacionSecurityService`**: patrón `TesoreriaSecurityService`, con `requireVer`,
  `requireEmitirNr`, `requireEmitirNc`, `requireAnular` y bypass ADMIN.
- **`SifenTimbradoHelper`**: `codigoEstablecimiento(Sucursal)` (`%03d`, default `001`) y
  `fechaFirmaSegura(LocalDateTime)` (nunca adelantada respecto de la hora de Paraguay). La ruta de
  la factura no se toca.
- **Tests nuevos** (14): `FacturacionSecurityServiceTest` (6), `SifenTimbradoHelperTest` (5),
  `DocumentoElectronicoFkTest` (3, incluye que las cuatro columnas sean `insertable`/`updatable`).
  Batería completa: **702 tests, 0 fallas, BUILD SUCCESS**.

**Queda de 0.B para la fase siguiente**: portar `procesarLotesAtrasados` desde el filial,
`reconstruirDE` despachando por `tipoDocumento`, `generarYEnviarSincrono`, los
`DocumentoElectronicoService.createFromNota*` (necesitan las entidades de 1.A) y
`SifenNotasValidator`.

**Dry-run de `V225.1` (2026-09-17)**: copia completa de `bodega@5551` (14 GB, `pg_dump
--no-subscriptions`) restaurada en `central_dryrun_v225`, y central arrancado contra ella con perfil
`dev` (la copia se apunta desde `application-user-dev.properties`, que es personal y está fuera de
git; levantarlo sin perfil encendería los schedulers de replicación, que llegan a filiales reales).
Flyway: `Current version 224.3` → `Successfully applied 1 migration, now at version v225.1`
(107 ms), y la app levantó. Verificado después: las tres columnas nullable, los cuatro roles
sembrados, la UNIQUE `uk_documento_electronico_factura_legal` intacta y las 534.741 filas en su
lugar. El seed se volvió a correr a mano: sigue habiendo cuatro roles (idempotente). Copia borrada.
