# Compras móvil: factura en papel leída por IA — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cargar compras en el celular desde una foto de factura en papel (OpenAI lee cabecera e ítems) reutilizando el flujo de la factura electrónica.

**Architecture:** La foto termina en `documento_recibido` (origen FOTO) + renglones; un paso de confirmación de cabecera la habilita y desde ahí corren los mismos `armarBorrador`/`armarFactura`/`crearCompraDesdeFactura`/`cargarNota` del QR. OpenAI se llama solo desde el central, con la clave cifrada en `configuracion_sistema` y editable desde el desktop.

**Tech Stack:** Spring Boot 2.7 / Java 11 / GraphQL kickstart / Flyway (central); Angular 20 + Vitest (PWA); Angular 15 + Material (desktop); OpenAI Chat Completions con imágenes y `json_schema`.

**Spec:** `docs/superpowers/specs/2026-10-24-compras-factura-papel-ia-design.md` (este mismo repo).

## Global Constraints

- Central: worktree `~/Documentos/angular/wt-central-factura-papel`, rama `feature/compras-factura-papel-ia`. **Nunca `./mvnw` en el checkout principal** (auto-clean mata el backend en marcha). No levantar servidores.
- PWA y desktop: rama `feature/compras-factura-papel-ia` desde `origin/develop` en su checkout. Chequear `git branch --show-current` en el mismo comando que cada commit (nunca commitear en `develop`).
- No pushear ni abrir PR hasta que el usuario lo pida.
- Migración única: `src/main/resources/db/migration/V256.3__compras_factura_papel_ia.sql` (sufijo `.3`, nunca `.5`). Solo DDL permitido por CLAUDE.md (DROP NOT NULL, ADD COLUMN, CREATE TABLE/INDEX, INSERT de semillas).
- No tocar `application.properties` ni `application-dev.properties` (el primero es local con `user-dev`).
- La clave de OpenAI nunca sale del central: ninguna query la devuelve (solo `claveCargada` y `claveFinal` = últimos 4).
- Cifrado: `AES/GCM/NoPadding`, IV 12 bytes aleatorio, tag 128, clave = SHA-256(`CONFIG_MASTER_KEY`), guardado `Base64(iv || cifrado)`. Leída con `@Value("${CONFIG_MASTER_KEY:}")`.
- Modelo por defecto `gpt-4o`; timeout `@Value("${app.openai.timeout-ms:90000}")`; `max_tokens` 8000; `temperature` 0.
- Fotos: hasta **5** por factura; la PWA las reduce a **2400 px** de lado mayor, JPEG **0,85**; el central rechaza una foto de más de **4 MB** decodificada.
- Textos visibles en español rioplatense-paraguayo como el resto del módulo («Sacá», «Elegí»).
- Rol de compras: `ComprasSecurityService.exigirCompras()`. Rol de administración: `ADMIN` chequeado con `RoleService` (no usar `@AdminSecured`, roto por issue #177).

## Review Focus

1. **Foto borrosa o que no es una factura** → mensaje claro «No se pudo leer una factura en la foto…», sin crear documento. Test en Task 7 (`leerSinFacturaNoCreaDocumento`).
2. **La misma factura en papel dos veces, o en papel después de cargada por QR** → bloqueo con el pedido/nota donde ya está. Test en Task 7 (`cabeceraDuplicadaSeRechaza`) + índice único en Task 2.
3. **RUC leído con un dígito mal** → DV no cierra: aviso y el comprador elige el proveedor; nunca se asigna otro proveedor por un RUC inválido. Test en Task 5 y Task 7 (`rucConDvInvalidoNoAsignaProveedor`).
4. **Cantidades manuscritas mal leídas** → suma de renglones ≠ total: aviso visible en la confirmación y en `avisos` de la factura. Test en Task 5 (`totalesNoCuadranMasDeUnoPorCiento`).
5. **Clave de OpenAI sin cargar o `CONFIG_MASTER_KEY` ausente** → «La lectura de facturas en papel no está configurada: pedile a un administrador…», y la factura electrónica sigue andando. Test en Task 3 (`sinMasterKeyNoGuardaNiLee`) y Task 7 (`sinClaveAvisaClaro`).

---

## Central

### Task 1: Un solo método que vincula producto ↔ proveedor

**Files:**
- Modify: `src/main/java/com/franco/dev/service/operaciones/NotaRecepcionService.java:441-535` (`crearVinculosProductoProveedor`)
- Modify: `src/main/java/com/franco/dev/service/operaciones/PedidoService.java` (`finalizarRecepcionNotas`, ~línea 250)
- Modify: `src/main/java/com/franco/dev/service/operaciones/compras/ComprasMobileService.java` (borrar `vincularProductosConProveedor` ~2218 y usar el de `NotaRecepcionService` en sus 4 llamadas)
- Test: `src/test/java/com/franco/dev/service/operaciones/VinculoProductoProveedorTest.java`

**Interfaces:**
- Produces: `public void NotaRecepcionService.vincularProductosConProveedor(NotaRecepcion nota, Usuario usuario)` — crea el par si no existe, reactiva (y limpia motivo) si estaba inactivo, un producto una vez; `usuario` null → el de la nota, si no el del pedido.

- [ ] **Step 1: Test que falla** (Mockito, `@ExtendWith(MockitoExtension.class)`): `creaVinculoQueNoExiste` (repo devuelve null → `save` con producto, proveedor del pedido, pedido, activo=true), `reactivaVinculoInactivo` (activo=false + motivo → activo=true, motivo null), `noRepiteProducto` (dos ítems mismo producto → un `save`), `notaSinProveedorNoHaceNada`.
- [ ] **Step 2:** `./mvnw -q test -Dtest=VinculoProductoProveedorTest` → FAIL (método no existe).
- [ ] **Step 3:** Renombrar `crearVinculosProductoProveedor(NotaRecepcion)` a `vincularProductosConProveedor(NotaRecepcion, Usuario)` público; sus dos llamadas internas pasan `null`. `PedidoService.finalizarRecepcionNotas` lo llama para cada nota después de pasarlas a CONCILIADA (antes de `finalizarEtapa`). Borrar la copia de `ComprasMobileService` y llamar `notaRecepcionService.vincularProductosConProveedor(nota, usuario)`. En `ComprasMobileService.finalizar` y `finalizarSiNoQuedaNadaAMano` sacar el bucle propio (ya lo hace `finalizarRecepcionNotas`).
- [ ] **Step 4:** `./mvnw -q test -Dtest='VinculoProductoProveedorTest,ConciliacionAutomaticaTest'` → PASS.
- [ ] **Step 5:** Commit `refactor(compras): un solo vinculo producto-proveedor y el finalizar del desktop lo crea`.

### Task 2: Migración V256.3 y entidades

**Files:**
- Create: `src/main/resources/db/migration/V256.3__compras_factura_papel_ia.sql` (contenido exacto: sección «Central → Migración» del spec)
- Create: `src/main/java/com/franco/dev/domain/empresarial/ConfiguracionSistema.java`, `src/main/java/com/franco/dev/repository/empresarial/ConfiguracionSistemaRepository.java` (traer de `origin/feat/factura-import-ia`)
- Create: `src/main/java/com/franco/dev/domain/operaciones/DocumentoRecibidoFoto.java`, `src/main/java/com/franco/dev/repository/operaciones/DocumentoRecibidoFotoRepository.java`
- Modify: `DocumentoRecibido.java` (+ `origen` String default `"SIFEN"`, `cabeceraConfirmada` Boolean default true, `iaModelo`, `iaTokensEntrada`, `iaTokensSalida`, `iaRespuesta`, `iaDudosos`), `DocumentoRecibidoItem.java` (+ `iaDudosos`), `ProveedorCodigoProducto.java` (+ `descripcionNormalizada`), `ProveedorCodigoProductoRepository.java`
- Test: `src/test/java/com/franco/dev/service/operaciones/compras/MigracionFacturaPapelTest.java`

**Interfaces:**
- Produces: constantes `DocumentoRecibido.ORIGEN_SIFEN = "SIFEN"`, `ORIGEN_FOTO = "FOTO"`; `List<DocumentoRecibidoFoto> DocumentoRecibidoFotoRepository.findByDocumentoRecibidoIdOrderByOrdenAsc(Long)`; `Optional<ProveedorCodigoProducto> ProveedorCodigoProductoRepository.findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(Long, String)`; `Optional<DocumentoRecibido> DocumentoRecibidoRepository.findFirstByRucEmisorAndTimbradoAndEstablecimientoAndPuntoExpedicionAndNumero(String, Integer, String, String, String)`.

- [ ] **Step 1: Test que falla** `MigracionFacturaPapelTest` (lee el `.sql` del classpath como texto): contiene `ALTER COLUMN cdc DROP NOT NULL`, `uk_documento_recibido_numero`, `documento_recibido_foto`, `configuracion_sistema`, `'gpt-4o'`; **no** contiene `DROP TABLE`, `DROP COLUMN`, `RENAME`, `ALTER COLUMN .* TYPE`.
- [ ] **Step 2:** correrlo → FAIL (archivo no existe).
- [ ] **Step 3:** Escribir la migración y las entidades. `imagen` como `byte[]` con `@Column(columnDefinition = "bytea")` y `@Basic(fetch = LAZY)`.
- [ ] **Step 4:** Probar la migración contra bodega3 sin dejar rastro: `PGPASSWORD=… psql -h localhost -p 5551 -U franco -d bodega3 -v ON_ERROR_STOP=1 -c 'BEGIN' -f src/main/resources/db/migration/V256.3__compras_factura_papel_ia.sql -c 'ROLLBACK'` → sin ERROR (sacar usuario/clave de `application-user-dev.properties` del checkout principal; si no hay acceso, anotarlo y seguir). Y `./mvnw -q test -Dtest=MigracionFacturaPapelTest` → PASS.
- [ ] **Step 5:** Commit `feat(compras): migracion V256.3 para facturas en papel e IA`.

### Task 3: Configuración de IA cifrada + GraphQL de administración

**Files:**
- Create: `src/main/java/com/franco/dev/service/empresarial/ConfiguracionSistemaService.java` (reescrito, no el de la rama vieja), `src/main/java/com/franco/dev/service/empresarial/CifradoConfiguracion.java`
- Create: `src/main/java/com/franco/dev/graphql/empresarial/ConfiguracionIaGraphQL.java`, `src/main/resources/graphql/empresarial/configuracion-ia.graphqls`
- Test: `src/test/java/com/franco/dev/service/empresarial/CifradoConfiguracionTest.java`, `ConfiguracionSistemaServiceTest.java`

**Interfaces:**
- Produces: `CifradoConfiguracion(String masterKey)` con `String cifrar(String)`, `String descifrar(String)`, `boolean disponible()`; `ConfiguracionSistemaService`: `Optional<String> valor(String clave)` (descifra si `encriptado`), `void guardar(String clave, String valor)`, `ConfiguracionIa configuracionIa()`, `void exigirAdmin()`; claves `CLAVE_OPENAI_API_KEY = "openai.api_key"`, `CLAVE_OPENAI_MODELO = "openai.modelo"`, `CLAVE_OPENAI_PROMPT = "openai.prompt_adicional"`.
- Schema:
```graphql
type ConfiguracionIa { claveCargada: Boolean! claveFinal: String modelo: String promptAdicional: String cifradoDisponible: Boolean! }
extend type Query { configuracionIa: ConfiguracionIa! }
extend type Mutation {
  guardarConfiguracionIa(apiKey: String, modelo: String, promptAdicional: String): ConfiguracionIa!  # apiKey null = no cambia, "" = borra
  probarConexionIa: String!
}
```

- [ ] **Step 1: Tests que fallan:** `idaYVueltaDevuelveLoMismo`, `dosCifradosDelMismoTextoSonDistintos`, `otraMasterKeyNoDescifra` (lanza `IllegalStateException`), `sinMasterKeyNoGuardaNiLee` (`disponible()==false`; `guardar("openai.api_key", "sk-x")` lanza `GraphQLException` con «CONFIG_MASTER_KEY»), `configuracionIaNuncaDevuelveLaClave` (`claveFinal` = `"…AbCd"` para `sk-…AbCd`), `exigirAdminRechazaSinRol`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='CifradoConfiguracionTest,ConfiguracionSistemaServiceTest'` → FAIL.
- [ ] **Step 3:** Implementar. `probarConexionIa` hace `GET {base}/v1/models/{modelo}` con la clave y devuelve «Conexión OK con {modelo}» o el error de OpenAI truncado a 300 caracteres. Las tres operaciones llaman `exigirAdmin()`.
- [ ] **Step 4:** tests → PASS.
- [ ] **Step 5:** Commit `feat(configuracion): clave de OpenAI cifrada y editable por ADMIN`.

### Task 4: Lector de facturas con OpenAI

**Files:**
- Create: `src/main/java/com/franco/dev/service/ia/OpenAiVisionService.java` (traer de `origin/feat/factura-import-ia` y adaptar), `src/main/java/com/franco/dev/service/ia/FacturaIaLeida.java`
- Test: `src/test/java/com/franco/dev/service/ia/OpenAiVisionServiceTest.java` (traer los casos de la rama vieja que sigan aplicando)

**Interfaces:**
- Consumes: `ConfiguracionSistemaService.valor(...)` (Task 3).
- Produces: `FacturaIaLeida OpenAiVisionService.leer(List<byte[]> jpegs) throws IOException` — una llamada por página y `mergeResultados`. `FacturaIaLeida` (Jackson): `emisorRuc, emisorNombre, receptorRuc, receptorNombre, timbrado, establecimiento, puntoExpedicion, numero, fechaEmision (yyyy-MM-dd), condicion ("CONTADO"|"CREDITO"|null), moneda, totalGeneral (BigDecimal), List<String> dudosos, String error, List<Item> items`; `Item`: `codigoProveedor, codigoBarras, descripcion, unidadMedida, cantidad, precioUnitario, descuento, totalItem, tasaIva (Integer), List<String> dudosos`; y `modelo, tokensEntrada, tokensSalida, respuestaCruda`.

- [ ] **Step 1: Tests que fallan:** `cuerpoPideJsonSchemaEstricto` (el body tiene `response_format.type=json_schema`, `strict=true`, `temperature=0`, una `image_url` por llamada), `montosConPuntoDeMilesSeNormalizan` (`"264.408"` PYG → 264408), `respuestaCortadaDaErrorEntendible` (`finish_reason=length` → IOException con «imagen más nítida»), `paginasSeFusionan` (cabecera de la 1.ª, total de la última, ítems concatenados, tokens sumados), `numeroCompletoSeParte` (`"001-002-0001234"` → 001/002/0001234), `sinClaveAvisaClaro` (IOException «no está configurada»).
- [ ] **Step 2:** `./mvnw -q test -Dtest=OpenAiVisionServiceTest` → FAIL.
- [ ] **Step 3:** Adaptar el prompt de la rama vieja: mantener las reglas de emisor vs cliente, números paraguayos y cantidad manuscrita = total/precio; agregar `receptorRuc`, número partido en tres, `condicion`, `tasaIva`, `codigoBarras` y «lista en `dudosos` los nombres de campo que no pudiste leer con seguridad». El `json_schema` declara todos los campos como `["tipo","null"]` y `additionalProperties:false`.
- [ ] **Step 4:** tests → PASS.
- [ ] **Step 5:** Commit `feat(compras): lector de facturas en papel con OpenAI`.

### Task 5: Validaciones de la factura en papel

**Files:**
- Create: `src/main/java/com/franco/dev/service/operaciones/compras/FacturaPapelReglas.java` (estática, sin Spring)
- Test: `src/test/java/com/franco/dev/service/operaciones/compras/FacturaPapelReglasTest.java`

**Interfaces:**
- Produces: `static boolean rucValido(String rucConDv)` (módulo 11 de la SET, base máxima 11; acepta `"80012345-6"`), `static String sinDv(String)`, `static boolean numeroValido(String est, String pto, String num)` (`\d{3}`, `\d{3}`, `\d{7}`), `static boolean timbradoValido(Integer)` (8 dígitos), `static boolean totalesCuadran(double total, List<Double> totalesItems)` (diferencia ≤ max(1 % del total, 1 Gs × cantidad de ítems)), `static String rellenar(String, int)` (ceros a la izquierda).

- [ ] **Step 1: Tests que fallan** con valores reales: `rucDeGenfiEsValido` (tomar RUC+DV de un proveedor de bodega3 con `SELECT documento FROM personas.persona … LIMIT 3` y cablearlos), `rucConUnDigitoCambiadoNoEsValido`, `numeroConCerosFaltantesSeRellena` (`"1","1","1234"` → `rellenar` → válido), `timbradoDeSieteDigitosNoEsValido`, `totalesCuadranConRedondeo`, `totalesNoCuadranMasDeUnoPorCiento`.
- [ ] **Step 2:** → FAIL. **Step 3:** implementar. **Step 4:** → PASS.
- [ ] **Step 5:** Commit `feat(compras): reglas de validacion de factura en papel`.

### Task 6: Lo aprendido también por descripción

**Files:**
- Modify: `ComprasMobileService.java` — `aprender(...)` (~2047) y `resolverProducto(...)` (~1785); `confirmarFactura`/`cargarNota` (~427) deja de exigir `codigoProveedor` para aprender
- Create: `src/main/java/com/franco/dev/service/operaciones/compras/DescripcionProveedor.java`
- Test: `src/test/java/com/franco/dev/service/operaciones/compras/DescripcionProveedorTest.java`

**Interfaces:**
- Produces: `static String DescripcionProveedor.normalizar(String)` → mayúsculas, sin tildes, solo `[A-Z0-9]` y un espacio entre palabras, recortado a 500; null/blank → null. Nuevo `TipoMatch.APRENDIDO_DESCRIPCION` (enum interno de `ComprasMobileService`, ~1755; en el schema `tipoMatch` es `String`, no se toca) con etiqueta «Aprendido por descripción» donde se arma `tipoMatchEtiqueta`. Donde hoy se compara contra `APRENDIDO` (p. ej. `reconocidoPorCodigo`), tratarlo igual.
- Regla: con código del proveedor se aprende y busca por código (como hoy). **Sin código**, por `(proveedor, descripcion_normalizada)` con `codigo_proveedor` null. Lo aprendido por descripción **solo vincula si coincide exacto normalizado**; nunca por parecido.

- [ ] **Step 1: Tests que fallan:** `normalizaTildesYSignos` (`"Jabón  LUX 125g."` → `"JABON LUX 125G"`), `blancoEsNull`, `recortaA500`.
- [ ] **Step 2:** → FAIL. **Step 3:** implementar `normalizar`; en `resolverProducto`, si no hay código o no hubo aprendido por código, buscar por descripción normalizada (misma regla de presentación inactiva); `aprender` guarda por código si hay, si no por descripción. **Step 4:** `./mvnw -q test -Dtest='DescripcionProveedorTest,CruceFacturaTest,AlertasRenglonTest'` → PASS.
- [ ] **Step 5:** Commit `feat(compras): aprender productos por descripcion cuando la factura no trae codigo`.

### Task 7: FacturaFotoService + GraphQL de la PWA

**Files:**
- Create: `src/main/java/com/franco/dev/service/operaciones/compras/FacturaFotoService.java`, `src/main/java/com/franco/dev/graphql/operaciones/compras/FacturaFotoGraphQL.java` (+ DTOs `CabeceraFacturaFotoMobile`, `CabeceraFacturaFotoInput` junto a los DTOs móviles existentes)
- Modify: `src/main/resources/graphql/operaciones/compras-mobile.graphqls`; `ComprasMobileService.java` (extraer y exponer el armado)
- Test: `src/test/java/com/franco/dev/service/operaciones/compras/FacturaFotoServiceTest.java` (Mockito)

**Interfaces:**
- Consumes: Tasks 2–6.
- Extraer en `ComprasMobileService` (sin cambiar el comportamiento del QR; `leerFactura` y `leerFacturaNuevaCompra` pasan a usarlos):
  - `FacturaProveedorMobile abrirEnPedido(DocumentoRecibido doc, Pedido pedido)` — lo que hoy sigue a `documentoRecibidoService.obtener(...)` en `leerFactura` (nota ya cargada, `exigirQueNoEspereOtroPedido`, receptor, set pedido/proveedor/usuario, `armarFactura`).
  - `FacturaProveedorMobile abrirNuevaCompra(DocumentoRecibido doc, Proveedor proveedor)` — ídem para `leerFacturaNuevaCompra`.
  - Guardia `exigirCabeceraConfirmada(doc)` al inicio de `cargarNota` y `crearCompraDesdeFactura`: «Confirmá los datos de la factura en papel antes de seguir.»
  - `armarFactura`/`armarBorrador`: `cdc` null permitido; `FacturaProveedorMobile.origen`; si origen FOTO, aviso con los `ia_dudosos` y, si `!totalesCuadran`, aviso «La suma de los renglones (X) no da el total de la factura (Y): revisá cantidades y precios con el papel».
- Produces (FacturaFotoService):
  - `CabeceraFacturaFotoMobile leer(List<String> fotosDataUri, Long pedidoId)` — `exigirCompras`; 1–5 fotos, ≤4 MB c/u; `OpenAiVisionService.leer`; `error` o 0 ítems → GraphQLException «No se pudo leer una factura en la foto. Sacala de frente, con buena luz y que entre entera.» (sin crear nada); crea `DocumentoRecibido` origen FOTO, `cabeceraConfirmada=false`, `tipoDocumento=1`, campos `ia_*`, renglones (`orden` 1..n, `precio_unitario` con IVA, `afectacion_iva` 1 si tasa>0 si no 3), fotos; propone proveedor: pedido → el del pedido; si no, `proveedorPorRuc` solo si `rucValido`.
  - `FacturaProveedorMobile confirmarCabecera(CabeceraFacturaFotoInput in)` — valida con `FacturaPapelReglas` (número y timbrado inválidos bloquean; RUC inválido no bloquea si se eligió proveedor), duplicado por `findFirstByRucEmisor…` (otro documento con nota → «Esta factura ya está cargada: nota N del pedido P») y por `FacturaRepetida` contra las notas del proveedor, proveedor obligatorio; en pedido, si el RUC leído es válido y es de otro proveedor → mismo mensaje que la electrónica; guarda, `cabeceraConfirmada=true`, y delega en `abrirEnPedido`/`abrirNuevaCompra`.
  - `String foto(Long documentoId, int orden)` → data URI.
- Schema (agregar; `FacturaProveedorMobile.cdc` pasa de `String!` a `String` y suma `origen: String`):
```graphql
type CabeceraFacturaFotoMobile {
  documentoId: ID! pedidoId: ID cantidadFotos: Int!
  emisorRuc: String emisorNombre: String rucValido: Boolean!
  proveedorId: ID proveedorNombre: String
  receptorRuc: String receptorEsEmpresa: Boolean
  timbrado: Int establecimiento: String puntoExpedicion: String numero: String
  fechaEmision: String condicion: String moneda: String
  totalGeneral: Float sumaRenglones: Float totalesCuadran: Boolean!
  cantidadRenglones: Int! dudosos: [String!]! avisos: [String!]!
}
input CabeceraFacturaFotoInput {
  documentoId: ID! proveedorId: ID! timbrado: Int! establecimiento: String! puntoExpedicion: String! numero: String!
  fechaEmision: String! condicion: String totalGeneral: Float!
}
extend type Query { fotoFacturaMobile(documentoId: ID!, orden: Int!): String }
extend type Mutation {
  leerFacturaFotoMobile(fotos: [String!]!, pedidoId: ID): CabeceraFacturaFotoMobile!
  confirmarCabeceraFacturaFotoMobile(input: CabeceraFacturaFotoInput!): FacturaProveedorMobile!
}
```

- [ ] **Step 1: Tests que fallan:** `leerSinFacturaNoCreaDocumento`, `leerCreaDocumentoFotoSinConfirmar` (origen FOTO, cabeceraConfirmada false, 3 renglones, 2 fotos), `rucConDvInvalidoNoAsignaProveedor`, `cabeceraDuplicadaSeRechaza`, `confirmarEnPedidoDeOtroProveedorSeRechaza`, `confirmarDelegaEnAbrirNuevaCompra`, `masDeCincoFotosSeRechaza`, `sinClaveAvisaClaro` (el lector lanza «no está configurada» → GraphQLException con ese texto), y en `ConciliacionAutomaticaTest` o un test nuevo: `crearCompraConCabeceraSinConfirmarSeRechaza`.
- [ ] **Step 2:** → FAIL. **Step 3:** implementar. **Step 4:** `./mvnw -q test -Dtest='FacturaFotoServiceTest,*Compra*Test,*Factura*Test,KudeQrTest'` y luego `./mvnw -q test` completo → PASS.
- [ ] **Step 5:** Commit `feat(compras): factura en papel por foto en compras movil`.

### Task 8: Deploy de CONFIG_MASTER_KEY y documentación

**Files:**
- Modify: `.github/workflows/deploy.yml` (paso nuevo junto al de mail, ~187-210), `.github/scripts/deploy.sh` (~74-84: aceptar `CONFIG_MASTER_KEY` en la lista de claves que se pisan)
- Modify: `CLAUDE.md` (sección corta «Facturas en papel con IA»: dónde se configura, `CONFIG_MASTER_KEY`, central-only, costo por tokens en `documento_recibido`)

- [ ] **Step 1:** Paso del workflow: si `secrets.CONFIG_MASTER_KEY` está vacío → `echo "CONFIG_MASTER_KEY secret not set. Skipping."`; si no, escribirlo con el mismo mecanismo que `GMAIL_*`.
- [ ] **Step 2:** `bash -n .github/scripts/deploy.sh` → sin salida; `python3 -c "import yaml,sys;yaml.safe_load(open('.github/workflows/deploy.yml'))"` → sin error.
- [ ] **Step 3:** Commit `ci(deploy): escribir CONFIG_MASTER_KEY en el .env` + `docs(compras): facturas en papel con IA`.

---

## PWA (`~/Documentos/angular/frc-mobile-pwa`)

Comandos con Node 20: `source ~/.nvm/nvm.sh && nvm use 20 >/dev/null && <cmd>`.

### Task 9: Datos y servicio

**Files:**
- Modify: `src/app/core/dispositivo/imagen.ts` (`fotoADataUri(archivo, opciones?: { ladoMaximo?: number; calidad?: number })`, defaults actuales 1600/0,75 sin cambiar)
- Modify: `src/app/domains/compras/compras.model.ts` (`FacturaProveedor.cdc: string | null`, `origen: 'SIFEN' | 'FOTO'`; tipos `CabeceraFacturaFoto`, `CabeceraFacturaFotoInput` = schema de Task 7)
- Modify: `src/app/pages/operaciones/compras/compras.service.ts` (+ `leerFacturaFoto(fotos: string[], pedidoId: number | null)`, `confirmarCabeceraFoto(input)`, `fotoFactura(documentoId, orden)`) y sus documentos GraphQL donde viven los demás
- Modify: `src/app/pages/operaciones/compras/compras-reglas.ts` (+ `numeroFacturaCompleto(est, pto, num): string | null` que rellena con ceros y devuelve null si no da 3-3-7; `MAX_FOTOS_FACTURA = 5`; `OPCIONES_FOTO_FACTURA = { ladoMaximo: 2400, calidad: 0.85 }`)
- Test: `src/app/pages/operaciones/compras/compras-reglas.spec.ts`

- [ ] **Step 1: Tests que fallan:** `numeroFacturaCompleto('1','1','1234') === '001-001-0001234'`, `numeroFacturaCompleto('1','1','123456789') === null`, `MAX_FOTOS_FACTURA === 5`.
- [ ] **Step 2:** `npx ng test --watch=false` → FAIL. **Step 3:** implementar. **Step 4:** → PASS y `npx ng build` sin errores (el `cdc` opcional obliga a revisar sus usos en `factura-proveedor.page.ts`: mostrar «Factura en papel» cuando `origen === 'FOTO'`).
- [ ] **Step 5:** Commit `feat(compras): modelo y servicio de factura en papel`.

### Task 10: Elegir electrónica o papel + pantalla de confirmación

**Files:**
- Create: `src/app/pages/operaciones/compras/elegir-tipo-factura.component.ts` (bottom sheet con «Factura electrónica (QR)» y «Factura en papel (foto)»), `src/app/pages/operaciones/compras/factura-foto.page.ts`
- Modify: `compras-lista.page.ts` (`escanear()` abre la hoja), `compra-pedido.page.ts` (el botón de cargar factura abre la hoja con `pedidoId`), `compras.routes.ts` (rutas `foto` y `:pedidoId/foto`, **antes** de `:pedidoId`)
- Modify: `docs/PLAN_TESTEO_MANUAL.md` (bloque **92 · Factura en papel**: casos de Review Focus 1–5 + nueva compra feliz + pedido existente), `docs/modulos/compras.md`
- Test: `src/app/pruebas/compras-factura-foto.spec.ts`

**Comportamiento:** «Factura en papel» abre `<input type="file" accept="image/*" capture="environment" multiple>` (oculto, disparado por el botón); se pueden agregar hojas hasta 5 con miniaturas y quitar; «Leer factura» muestra «Leyendo factura… (puede tardar hasta un minuto)» con el botón deshabilitado; con la respuesta navega a `foto`/`:pedidoId/foto` con `documentoId`. `factura-foto.page` muestra la foto (zoom al tocar), y el formulario: proveedor (buscador de proveedores existente si `proveedorId` null o para cambiarlo; fijo y no editable en modo pedido), RUC leído con aviso si `!rucValido`, timbrado, establecimiento, punto, número, fecha, condición, total; campos en `dudosos` con borde amarillo y «Revisá este dato»; `avisos` arriba; «Continuar» → `confirmarCabeceraFoto` → `nueva/:documentoId` o `:pedidoId/factura/:documentoId` con `replaceUrl`.

- [ ] **Step 1: Tests que fallan** (TestBed, servicio mockeado): `conTresFotosLlamaLeerConTres`, `noDejaMasDeCinco`, `campoDudosoSeMarca`, `continuarSinProveedorQuedaDeshabilitado`, `confirmarNavegaANuevaCompra`, `enPedidoElProveedorNoSeEdita`.
- [ ] **Step 2:** `npx ng test --watch=false` → FAIL. **Step 3:** implementar con los componentes del design-system de la PWA (`frc-seccion`, botones `matButton`) como `gastos-rendicion.page.ts`. **Step 4:** tests → PASS; `npm run build` → OK.
- [ ] **Step 5:** Commit `feat(compras): cargar factura en papel con foto desde el celular`.

---

## Desktop (`~/Documentos/angular/frc-sistemas-integrados-angular`)

### Task 11: Configuración → Inteligencia artificial

**Files:**
- Create: `src/app/modules/configuracion/inteligencia-artificial/inteligencia-artificial.component.{ts,html,scss}`, `src/app/modules/configuracion/inteligencia-artificial/graphql/{configuracionIa,guardarConfiguracionIa,probarConexionIa}.ts` (patrón de `impresoras/graphql`)
- Modify: `configuracion.module.ts` (declarations), `side-mini-variant.component.ts` (ítem `{ name: 'Inteligencia artificial', icon: 'smart_toy', action: 'inteligencia-artificial', visibilityRoles: [ROLES.ADMIN] }` en Configuración + `case` con `openTabIfAuthorized(ROLES.ADMIN, …)`)

**Comportamiento:** muestra «Clave cargada (…AbCd)» o «Sin clave»; campo de clave tipo password vacío (solo se manda si se escribió), botón «Borrar clave» con `DialogosService.confirmar`; modelo (texto, default `gpt-4o`); prompt adicional (textarea); «Guardar» y «Probar conexión» (muestra el texto devuelto). Si `cifradoDisponible` es false: banner «Falta CONFIG_MASTER_KEY en el servidor: no se puede guardar la clave».

- [ ] **Step 1:** Implementar. **Step 2:** `npm run check` → build de producción OK (Karma está roto: no usar `npm test`).
- [ ] **Step 3:** Commit `feat(configuracion): pantalla de inteligencia artificial para la clave de OpenAI`.

---

## Cierre

- [ ] Archivar `feat/factura-import-ia`: **no borrarla**; anotar en la descripción del PR del central que lo reutilizado sale de ahí y que el resto queda descartado (confirmar con Gabriel).
- [ ] Prueba manual del bloque 92 en Chrome contra el central de la rama (lo levanta Franco) con una factura en papel real.
- [ ] Recordatorio para el PR: avisar al líder técnico de `CONFIG_MASTER_KEY` y cargar el secret antes del deploy; la migración `V256.3` va incluida en el PR.
