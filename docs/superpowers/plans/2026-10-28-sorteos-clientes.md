# Sorteos para clientes: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El central configura sorteos (mínimo, modo, vigencia, sucursales, premios, tarjeta sí/no). Cuando una venta del POS califica, se piden los datos del cliente, se imprimen tickets de participación para firmar, la participación queda registrada en el central y se puede reimprimir.

**Architecture:**
- Tablas de configuración en el central, replicadas MAIN_TO_ALL a espejos en la filial.
- La filial evalúa con una fórmula única (`SorteoCalculo`) que comparten la pre-consulta `sorteosAplicables` y `saveVenta`.
- La filial registra la participación (BRANCH_TO_MAIN) e imprime los tickets ESC/POS al final del comprobante.
- El desktop solo pregunta por el cliente y muestra el aviso.

**Tech Stack:**
- Central: Spring Boot 2.7 / Java 11 / GraphQL kickstart / Flyway.
- Filial: Spring Boot 2.7, JdbcTemplate, ESC/POS propio.
- Desktop: Angular 15 + apollo-angular + Electron.

**Spec:** `docs/superpowers/specs/2026-10-28-sorteos-clientes-design.md` (este repo). Leerlo antes de cada tarea.

**Repos (checkout principal, rama `feature/sorteos-clientes` en los tres):**
- C = `~/Documentos/angular/franco-system-backend-servidor`
- F = `~/Documentos/franco-system-backend-filial`
- D = `~/Documentos/angular/frc-sistemas-integrados-angular`

## Global Constraints

- Migraciones con sufijo `.3` (nunca `.5`): central `V260.3__sorteos_clientes.sql`, filial `V109.3__espejo_sorteos_clientes.sql`. Antes del PR, volver a verificar contra todas las ramas remotas.
- Los cambios van en el checkout principal, en `feature/sorteos-clientes`. Compilar y testear **C y F en un worktree aparte**: `git worktree add --detach <ruta>` + `git diff | git apply` + copiar los archivos nuevos. Borrarlo al terminar. `./mvnw` del central borra `target/` y mata el backend.
- No commitear `application.properties` (cambio local del usuario). No abrir PR. No levantar servidores.
- Las migraciones no se commitean mientras no haya PR (regla del usuario): quedan sin trackear en la rama. El resto del código sí se commitea, con commits convencionales, cada uno terminado en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Desktop: verificar con `npm run check` y Node ≥ 22.12 (`package.json` engines). Karma no corre.
- "Hoy" se calcula en zona -03 fija (`ZoneOffset.ofHours(-3)`), nunca en la zona de la JVM.
- Nada del sorteo puede impedir ni revertir una venta: toda lectura, registro e impresión del sorteo va en try/catch y loguea.
- Todo valor nuevo de un enum Java del central va también a su `.graphqls`; lo valida `SchemaEnumsSincronizadosTest`.
- Textos visibles en español rioplatense, como el resto del POS ("Elegí", "Cortalo").
- Orden de despliegue: F (en todas las filiales), después C, después D.

## Review Focus

1. **Total exactamente igual al mínimo:** califica (`>=`). Cubierto en el test de `SorteoCalculo` (Task F2).
2. **Venta a las 23:30 del último día de vigencia en una JVM con tzdb viejo (-04):** el sorteo todavía vale. Test con `Clock` fijo en `SorteoLector` (Task F2).
3. **Error de SQL al registrar la participación:** la venta se guarda igual y la transacción de la venta no queda abortada. Registro en `REQUIRES_NEW`, con test en F5.
4. **Desktop nuevo contra una filial vieja sin sorteos:** `sorteosAplicables` falla, la venta sigue como hoy y no se usa el documento `saveVentaSorteo`. Test manual documentado en D2.
5. **Cobro rápido con descuento:** la línea `descuento=true` del cobro se resta. Si el desktop manda el descuento con signo negativo, se usa su valor absoluto. Test en F2.

---

## FILIAL (F): se despliega primero

### Task F1: migración espejo + tabla de participaciones

**Files:**
- Create: `F/src/main/resources/db/migration/V109.3__espejo_sorteos_clientes.sql`

**Interfaces:**
- Produces: las tablas `operaciones.sorteo`, `operaciones.sorteo_sucursal`, `operaciones.sorteo_premio` (espejos) y `operaciones.sorteo_participacion` (propia).

- [ ] **Step 1: Escribir la migración**

Encabezado de comentario con el mismo formato que `V104.1__espejo_precio_especial_sucursal.sql`: qué es, "LADO SUBSCRIBER", y que va antes que C/V260.3 en cada filial. `CREATE TABLE IF NOT EXISTS`, todo nullable, PK `id`, sin FK, CHECK, UNIQUE ni seed. Columnas:

- `sorteo`: `id BIGSERIAL PK`, `nombre VARCHAR`, `descripcion VARCHAR`, `monto_minimo NUMERIC`, `modo_participacion VARCHAR`, `cantidad_fija INTEGER`, `tope_por_venta INTEGER`, `incluye_tarjeta BOOLEAN`, `fecha_desde DATE`, `fecha_hasta DATE`, `fecha_sorteo DATE`, `todas_sucursales BOOLEAN`, `activo BOOLEAN`, `usuario_id BIGINT`, `creado_en TIMESTAMP`.
- `sorteo_sucursal`: `id BIGSERIAL PK`, `sorteo_id BIGINT`, `sucursal_id BIGINT`.
- `sorteo_premio`: `id BIGSERIAL PK`, `sorteo_id BIGINT`, `orden INTEGER`, `descripcion VARCHAR`, `producto_id BIGINT`, `cantidad INTEGER`.
- `sorteo_participacion` (la escribe la filial): `id BIGSERIAL PK`, `sorteo_id BIGINT NOT NULL`, `venta_id BIGINT NOT NULL`, `sucursal_id BIGINT NOT NULL`, `cantidad INTEGER NOT NULL`, `cliente_id BIGINT`, `documento VARCHAR`, `nombre VARCHAR`, `codigo VARCHAR NOT NULL`, `usuario_id BIGINT`, `creado_en TIMESTAMP DEFAULT NOW()`, `UNIQUE (sorteo_id, venta_id)`, índice por `venta_id`.

- [ ] **Step 2: Verificar** con `psql` contra una base local de prueba de la filial, o con `./mvnw flyway:info` en el worktree. Esperado: corre dos veces sin error (idempotente).

### Task F2: lectura de sorteos y fórmula

**Files:**
- Create: `F/src/main/java/com/franco/dev/service/sorteo/SorteoFuente.java`
- Create: `F/src/main/java/com/franco/dev/service/sorteo/SorteoCalculo.java`
- Create: `F/src/main/java/com/franco/dev/service/sorteo/SorteoLector.java`
- Test: `F/src/test/java/com/franco/dev/service/sorteo/SorteoCalculoTest.java`
- Test: `F/src/test/java/com/franco/dev/service/sorteo/SorteoLectorTest.java`

**Interfaces:**
- Produces:
  - `SorteoFuente.vigentesCandidatos(long sucursalId): List<SorteoFuente.Sorteo>`. Hace JDBC (`NamedParameterJdbcTemplate`, mismo patrón que `PrecioEspecialFuente`) y trae `activo = true AND (todas_sucursales OR exists sorteo_sucursal)`, con sus premios ordenados por `orden`. Si un premio no tiene descripción, toma la de `productos.producto` (LEFT JOIN por `producto_id`). `Sorteo` es una clase inmutable con todos los campos de `sorteo` y `List<Premio> premios` (`descripcion`, `cantidad`).
  - `SorteoCalculo.montoComputable(double totalGs, List<CobroDetalleInput> detalles, Set<Long> formaPagoTarjetaIds, boolean incluyeTarjeta): double`. Calcula `max(0, totalGs − Σ|descuento|·cambio − (incluyeTarjeta ? 0 : Σ pago de tarjeta·cambio))`, con `cambio` null = 1. Los recargos (`aumento`) se ignoran.
  - `SorteoCalculo.tickets(String modo, double monto, double minimo, Integer cantidadFija, Integer tope): int`. Si `monto < minimo` → 0. `UNA_POR_VENTA` → 1. `POR_MULTIPLO` → `floor(monto/minimo)`, limitado por `tope` si no es null. `CANTIDAD_FIJA` → `max(1, cantidadFija)`. Un modo desconocido → 0.
  - `SorteoLector.aplicables(double totalGs, List<CobroDetalleInput> detalles): List<SorteoAplicable>`.
    - `SorteoAplicable {Long sorteoId; String nombre; String descripcion; Integer cantidadTickets; LocalDate fechaSorteo; LocalDate fechaDesde; LocalDate fechaHasta; List<String> premios}`. `premios` en texto, por ejemplo `"1 x Moto 110cc"`.
    - Filtra la vigencia en -03 con `Clock` inyectable, como `PrecioEspecialLector`.
    - Devuelve vacío si `sorteo.habilitado=false`, si falta `sucursalId` o si hay cualquier excepción (con log).
    - Los ids de forma de pago tarjeta salen de `FormaPagoService`, filtrando las que tienen `descripcion` que contiene `"TARJETA"`, el mismo criterio que `PdvCajaService`.

- [ ] **Step 1: Escribir los tests que fallan.**
  - `SorteoCalculoTest`:
    - `igualAlMinimoCalifica`: `tickets("UNA_POR_VENTA",100000,100000,null,null)==1`.
    - `porMultiplo`: `tickets("POR_MULTIPLO",250000,100000,null,null)==2`.
    - `porMultiploConTope`: `tickets("POR_MULTIPLO",900000,100000,null,3)==3`.
    - `cantidadFija`: `tickets("CANTIDAD_FIJA",150000,100000,4,null)==4`.
    - `debajoDelMinimo`: `tickets(...,99999,100000,...)==0` en los tres modos.
    - `descuentoSeResta`: total 120000 con un detalle `descuento=true, valor=30000` → `montoComputable==90000`. Con `valor=-30000` da lo mismo.
    - `recargoNoSuma`: un detalle `aumento=true, valor=10000` no cambia el monto.
    - `tarjetaExcluida`: total 200000, pago tarjeta 150000 y efectivo 50000, con `incluyeTarjeta=false` → 50000. Con `true` → 200000.
    - `cambioNullEsUno`.
  - `SorteoLectorTest`:
    - `fueraDeVigenciaNoAplica`.
    - `ultimoDiaA2330Menos03Aplica`: `Clock.fixed` en `2026-11-01T02:30:00Z` con `fecha_hasta=2026-10-31` → aplica.
    - `deshabilitadoDevuelveVacio`.
    - `excepcionDeFuenteDevuelveVacio`.
    - `desdeYHastaNullSonAbiertos`.

- [ ] **Step 2:** En el worktree, correr `./mvnw -q test -Dtest='Sorteo*Test'`. Esperado: FAIL por compilación.
- [ ] **Step 3:** Implementar las tres clases con las firmas de arriba.
- [ ] **Step 4:** Volver a correr `./mvnw -q test -Dtest='Sorteo*Test'`. Esperado: PASS.
- [ ] **Step 5:** Commit: `feat(sorteos): lectura de sorteos vigentes y calculo de tickets`.

### Task F3: query `sorteosAplicables`

**Files:**
- Create: `F/src/main/resources/graphql/operaciones/sorteo.graphqls`
- Create: `F/src/main/java/com/franco/dev/graphql/operaciones/SorteoGraphQL.java`
- Create: `F/src/main/java/com/franco/dev/graphql/operaciones/input/SorteoEvaluacionInput.java`
- Create: `F/src/main/java/com/franco/dev/graphql/operaciones/input/SorteoClienteInput.java`

**Interfaces:**
- Consumes: `SorteoLector.aplicables` (F2).
- Produces:
  - Tipos de schema:
    - `type SorteoAplicable { sorteoId: ID, nombre: String, descripcion: String, cantidadTickets: Int, fechaSorteo: Date, fechaDesde: Date, fechaHasta: Date, premios: [String] }`
    - `input SorteoEvaluacionInput { totalGs: Float!, cobroDetalleList: [CobroDetalleInput] }`
    - `input SorteoClienteInput { clienteId: ID, documento: String, nombre: String! }`
    - `type SorteoTicketInfo { nombreSorteo: String, cantidad: Int }`
    - `extend type Query { sorteosAplicables(input: SorteoEvaluacionInput!): [SorteoAplicable] }`
  - Java: `SorteoGraphQL.sorteosAplicables(SorteoEvaluacionInput): List<SorteoAplicable>`.

- [ ] **Step 1:** Escribir el schema, los inputs (Lombok `@Data`) y el resolver (delega en el lector).
- [ ] **Step 2:** En el worktree, correr `./mvnw -q -DskipTests package` y después `./mvnw -q test -Dtest='*GraphQL*Test'`. Esperado: compila y el schema arranca sin errores (si existe, el test de carga del schema pasa).
- [ ] **Step 3:** Commit: `feat(sorteos): query sorteosAplicables`.

### Task F4: renderer del ticket de participación

**Files:**
- Create: `F/src/main/java/com/franco/dev/service/impresion/dto/SorteoTicketDto.java`
- Modify: `F/src/main/java/com/franco/dev/service/impresion/ImpresionService.java`, al lado de `printSenaCupon`.
- Test: `F/src/test/java/com/franco/dev/service/impresion/SorteoTicketRenderTest.java`

**Interfaces:**
- Produces:
  - `SorteoTicketDto {String nombreSorteo; String descripcion; List<String> premios; LocalDate fechaDesde, fechaHasta, fechaSorteo; String sucursal; Long ventaId; LocalDateTime fecha; Double totalGs; String codigo; int numero; int total; String clienteNombre; String clienteDocumento; boolean reimpresion}`.
  - `ImpresionService.printSorteoTickets(List<SorteoTicketDto> tickets, String printerName, String local, OutputStream destino): Boolean`.
    - Usa un solo stream para todos los tickets, con `cut(FULL)` entre cada uno y `feed(1)` inicial (ver el comentario de `printSenaCupon`).
    - Si `destino != null` escribe ahí y no busca impresora. Nunca lanza: devuelve false.
    - Contenido en el orden del spec, sección "Ticket de participación". Si es reimpresión, va `"REIMPRESION"` arriba.
    - El código se imprime como `codigo + "-" + numero + "/" + total`.
    - La línea de firma es literal: `"Firma: ______________"`.
    - El pie es literal: `"Deposite este ticket firmado en la urna"`.

- [ ] **Step 1:** Escribir el test que falla. Renderiza 2 tickets a un `ByteArrayOutputStream` y verifica:
  - el resultado es `true`;
  - los bytes decodificados (ISO-8859-1) contienen `"Ticket 1 de 2"`, `"Ticket 2 de 2"`, el código, `"Firma:"` y el nombre;
  - hay dos secuencias de corte `GS V`.
  Si `logo.png` no existe en el test, el renderer lo omite sin fallar.
- [ ] **Step 2:** Correr el test. Esperado: FAIL.
- [ ] **Step 3:** Implementar `printSorteoTickets`.
- [ ] **Step 4:** Correr el test. Esperado: PASS.
- [ ] **Step 5:** Commit: `feat(sorteos): ticket de participacion ESC/POS`.

### Task F5: registrar e imprimir en `saveVenta`

**Files:**
- Create: `F/src/main/java/com/franco/dev/service/sorteo/SorteoParticipacionService.java`
- Modify: `F/src/main/java/com/franco/dev/graphql/operaciones/VentaGraphQL.java`
  - `saveVenta`: firma con el argumento nuevo al final. El bloque del sorteo va después del `switch (ruta)` y antes de `if (ticketCliente != null ...)`.
- Modify: `F/src/main/java/com/franco/dev/domain/operaciones/Venta.java`: agregar `@Transient List<SorteoTicketInfo> sorteoTickets`, junto a `ticketEscpos`.
- Modify: `F/src/main/resources/graphql/operaciones/venta.graphqls`:
  - `saveVenta(..., imprimirEnCliente: Boolean, sorteoCliente: SorteoClienteInput)`;
  - `sorteoTickets: [SorteoTicketInfo]` en `type Venta`.
- Test: `F/src/test/java/com/franco/dev/service/sorteo/SorteoParticipacionServiceTest.java`

**Interfaces:**
- Consumes: `SorteoLector` (F2), `printSorteoTickets` (F4), `SorteoClienteInput` (F3).
- Produces:
  - `SorteoParticipacionService.registrar(Venta venta, List<CobroDetalleInput> detalles, SorteoClienteInput cliente, Long usuarioId): List<Participacion>`.
    - Devuelve vacío si `cliente == null`, si `venta.getDelivery() != null` o si no hay aplicables.
    - Inserta por JDBC dentro de `TransactionTemplate` con `PROPAGATION_REQUIRES_NEW`, con `ON CONFLICT (sorteo_id, venta_id) DO NOTHING`.
    - `codigo = "S" + sorteoId + "-" + sucursalId + "-" + ventaId`.
    - `Participacion {sorteoId, nombreSorteo, codigo, cantidad, ...datos para el DTO}`.
  - `SorteoParticipacionService.ticketsDeVenta(Long ventaId, boolean reimpresion): List<SorteoTicketDto>`. Lee las participaciones de la venta y los datos del sorteo, y expande `cantidad` en N DTOs.
  - `VentaGraphQL.saveVenta(..., Boolean imprimirEnCliente, SorteoClienteInput sorteoCliente)`. Pasos después del `switch`, dentro de su propio try/catch:
    1. `registrar`.
    2. Si hay participaciones: `printSorteoTickets(ticketsDeVenta(id,false), printerName, local, ticketCliente)`. Así quedan al final del mismo stream en modo FRONTEND, y en `SIN_FACTURA`/`FACTURA_SILENCIOSA` salen solos.
    3. `venta.setSorteoTickets(...)`.

- [ ] **Step 1:** Escribir los tests que fallan, con mocks de `SorteoLector` y `JdbcTemplate`:
  - `sinClienteNoRegistra`;
  - `deliveryNoRegistra`;
  - `registraUnaFilaPorSorteoConCantidadYCodigo`;
  - `errorDeSqlDevuelveVacioYNoLanza`;
  - `ticketsDeVentaExpandeCantidad`: cantidad 3 → 3 DTOs numerados 1..3, con `total=3`.
- [ ] **Step 2:** Correr `./mvnw -q test -Dtest='SorteoParticipacionServiceTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar el servicio y el cambio en `saveVenta`.
- [ ] **Step 4:** Correr `./mvnw -q test`. Esperado: toda la suite de la filial en PASS.
- [ ] **Step 5:** Commit: `feat(sorteos): registrar participacion e imprimir tickets al guardar la venta`.

### Task F6: reimpresión

**Files:**
- Modify: `F/.../graphql/impresion/TicketEscposTipo.java`: agregar `SORTEO` (id = venta).
- Modify: `F/src/main/resources/graphql/print/ticket-escpos.graphqls`: agregar `SORTEO` al enum.
- Modify: `F/.../graphql/impresion/TicketEscposGraphQL.java`: `case SORTEO` → `printSorteoTickets(ticketsDeVenta(id,true), null, local, out)`. Si no hay participaciones, `GraphQLException("La venta " + id + " no tiene tickets de sorteo")`.
- Modify: `F/.../graphql/operaciones/VentaGraphQL.java` y `venta.graphqls`:
  - `reimprimirSorteo(ventaId: ID!, printerName: String, local: String): Boolean!`, para imprimir por la impresora del filial.
- Modify: `F/.../graphql/operaciones/resolver/VentaResolver.java`: campo `tieneSorteo(Venta): Boolean` (existe una participación con ese `venta_id`). Se agrega `tieneSorteo: Boolean` a `type Venta`.
- Test: extender `F/src/test/java/com/franco/dev/graphql/impresion/TicketEscposGraphQLTest.java` con:
  - `sorteoReimprimeConLeyenda`: los bytes contienen `"REIMPRESION"`;
  - `sorteoSinParticipacionesLanza`.

- [ ] **Step 1:** Escribir los tests.
- [ ] **Step 2:** Correrlos. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Correr `./mvnw -q test`. Esperado: PASS.
- [ ] **Step 5:** Commit: `feat(sorteos): reimpresion de tickets de sorteo`.

---

## CENTRAL (C)

### Task C1: migración, entidades y enum

**Files:**
- Create: `C/src/main/resources/db/migration/V260.3__sorteos_clientes.sql`
- Create: `C/src/main/java/com/franco/dev/domain/operaciones/Sorteo.java`, `SorteoSucursal.java`, `SorteoPremio.java`
- Create: `C/src/main/java/com/franco/dev/domain/operaciones/enums/ModoParticipacionSorteo.java` (`UNA_POR_VENTA, POR_MULTIPLO, CANTIDAD_FIJA`)
- Create: `C/src/main/java/com/franco/dev/repository/operaciones/SorteoRepository.java`
- Create: `C/src/main/resources/graphql/operaciones/sorteo.graphqls` (por ahora solo el enum, que necesita `SchemaEnumsSincronizadosTest`)

**Interfaces:**
- Produces:
  - Entidad `Sorteo`, con id `AssignedIdentityGenerator` como `PrecioEspecialSucursal`.
    - Campos del spec. `@Enumerated(STRING) modoParticipacion`.
    - `@OneToMany(mappedBy="sorteo", cascade=ALL, orphanRemoval=true) List<SorteoPremio> premios` y `List<SorteoSucursal> sucursales`.
    - `@ManyToOne(LAZY) Usuario usuario` + `getUsuarioNickname()`.
  - `SorteoRepository extends JpaRepository<Sorteo, Long>`:
    - `filtrar(String texto, boolean soloVigentes, LocalDate hoy, Pageable)`;
    - `existsByNombreIgnoreCaseAndActivoTrueAndIdNot(...)` para validar que el nombre no esté repetido.

- [ ] **Step 1:** Escribir la migración: encabezado como `V232.1` (orden de despliegue, `copy_data=false`), `SET LOCAL lock_timeout='5s'`.
  - Las 3 tablas MAIN_TO_ALL con FKs.
  - CHECKs:
    - `monto_minimo > 0`;
    - rango de fechas;
    - `modo_participacion IN ('UNA_POR_VENTA','POR_MULTIPLO','CANTIDAD_FIJA')`;
    - `cantidad_fija >= 1` si el modo es `CANTIDAD_FIJA`;
    - `tope_por_venta IS NULL OR tope_por_venta >= 1`;
    - `sorteo_sucursal.sucursal_id <> 0`.
  - `incluye_tarjeta BOOLEAN NOT NULL DEFAULT TRUE`, `todas_sucursales BOOLEAN NOT NULL DEFAULT FALSE`, `activo BOOLEAN NOT NULL DEFAULT TRUE`.
  - `sorteo_participacion` con `PRIMARY KEY (id, sucursal_id)` y sin FK, porque llega por replicación.
  - INSERT en `configuraciones.replication_table`:
    - las 3 tablas como `MAIN_TO_ALL`, con `replicate_central_to_branch_with_filter=false`;
    - `operaciones.sorteo_participacion` como `BRANCH_TO_MAIN`, también con `false`;
    - todo con `ON CONFLICT (table_name) DO NOTHING`.
- [ ] **Step 2:** Escribir las entidades, el enum en Java y en el `.graphqls`, y el repositorio.
- [ ] **Step 3:** En el worktree del central, correr `./mvnw -q test -Dtest='SchemaEnumsSincronizadosTest'`. Esperado: PASS.
- [ ] **Step 4:** Commit, sin la migración: `feat(sorteos): entidades y enum de sorteos`.

### Task C2: `SorteoService`

**Files:**
- Create: `C/src/main/java/com/franco/dev/service/operaciones/SorteoService.java`
- Create: `C/src/main/java/com/franco/dev/graphql/operaciones/input/SorteoInput.java`, `SorteoPremioInput.java`
- Test: `C/src/test/java/com/franco/dev/service/operaciones/SorteoServiceTest.java`, con el estilo de `PrecioEspecialSucursalServiceTest` (mocks a mano).

**Interfaces:**
- Produces:
  - `SorteoInput {Long id; String nombre; String descripcion; Double montoMinimo; String modoParticipacion; Integer cantidadFija; Integer topePorVenta; Boolean incluyeTarjeta; String fechaDesde, fechaHasta, fechaSorteo; Boolean todasSucursales; List<Long> sucursalIds; List<SorteoPremioInput> premios}`.
  - `SorteoPremioInput {String descripcion; Long productoId; Integer cantidad}`.
  - Métodos de `SorteoService`:
    - `guardar(SorteoInput, Usuario): Sorteo`. Crea si `id == null`, si no edita.
    - `cortar(Long id, Usuario): Sorteo`. Es idempotente, como `PrecioEspecialSucursalService.cortar`.
    - `filtrar(String texto, Boolean soloVigentes, int page, int size): Page<Sorteo>`.
    - `tieneParticipaciones(Long sorteoId): boolean`, por JDBC: `select exists(... sorteo_participacion where sorteo_id=?)`.
  - Validaciones, todas con `GraphQLException` y textos en español:
    - nombre obligatorio;
    - `montoMinimo > 0`;
    - modo válido;
    - `cantidadFija >= 1` si el modo es `CANTIDAD_FIJA`;
    - `tope >= 1` si viene;
    - `hasta >= desde`;
    - al menos una sucursal si `!todasSucursales`, nunca la 0;
    - al menos un premio con descripción o producto, y `cantidad >= 1`;
    - nombre único entre los activos.
    - Si se edita con participaciones y cambia `montoMinimo`, `modo`, `cantidadFija`, `tope`, `incluyeTarjeta`, `todasSucursales` o `sucursalIds`, se rechaza con: `"El sorteo ya tiene participaciones: no se pueden cambiar el monto, el modo ni las sucursales. Cortalo y creá uno nuevo."`
    - Editar un sorteo cortado: `"El sorteo está cortado: creá uno nuevo."`
  - `desde` null se guarda como hoy (-03), igual que en el diálogo de precio especial.

- [ ] **Step 1:** Escribir los tests que fallan:
  - `creaConPremiosYSucursales`;
  - `rechazaMinimoCero`;
  - `rechazaSucursalCentral`;
  - `rechazaSinPremios`;
  - `rechazaRangoInvertido`;
  - `rechazaNombreDuplicadoActivo`;
  - `editarConParticipacionesCambiandoMinimoFalla`;
  - `editarConParticipacionesCambiandoNombreYPremiosAnda`;
  - `cortarEsIdempotente`;
  - `cantidadFijaRequerida`.
- [ ] **Step 2:** En el worktree, correr `./mvnw -q test -Dtest='SorteoServiceTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Volver a correr. Esperado: PASS.
- [ ] **Step 5:** Commit: `feat(sorteos): alta, edicion y corte de sorteos`.

### Task C3: GraphQL y listado de participaciones

**Files:**
- Modify: `C/src/main/resources/graphql/operaciones/sorteo.graphqls`
- Create: `C/src/main/java/com/franco/dev/graphql/operaciones/SorteoGraphQL.java`
- Create: `C/src/main/java/com/franco/dev/service/operaciones/SorteoParticipacionConsulta.java`
- Test: `C/src/test/java/com/franco/dev/graphql/operaciones/SorteoGraphQLSeguridadTest.java`, con el patrón de `PrecioEspecialSucursalGraphQLSeguridadTest`.

**Interfaces:**
- Consumes: `SorteoService` (C2), `PrecioSecurityService.requireGestionar()` / `currentUsuario()`.
- Produces el schema:
  - Tipos:
    - `type Sorteo { id, nombre, descripcion, montoMinimo, modoParticipacion: ModoParticipacionSorteo, cantidadFija, topePorVenta, incluyeTarjeta, fechaDesde: Date, fechaHasta: Date, fechaSorteo: Date, todasSucursales, activo, sucursales: [Sucursal], premios: [SorteoPremio], creadoEn: Date, usuarioNickname, tieneParticipaciones: Boolean }`
    - `type SorteoPremio { id, orden, descripcion, producto: Producto, cantidad }`
    - `SorteoPage`, con la misma forma que `PrecioEspecialSucursalPage`.
    - `type SorteoParticipacion { id, sucursalId, sucursalNombre, ventaId, codigo, cantidad, clienteNombre, documento, anulada: Boolean, creadoEn: Date }`
    - `SorteoParticipacionPage`.
    - `type SorteoResumenSucursal { sucursalId, sucursalNombre, participaciones: Int, tickets: Int }`
  - Queries:
    - `filterSorteos(texto, soloVigentes, page=0, size=15)`;
    - `sorteo(id)`;
    - `sorteoParticipaciones(sorteoId!, sucursalId, codigo, page=0, size=20)`;
    - `sorteoResumen(sorteoId!): [SorteoResumenSucursal]`.
  - Mutations:
    - `saveSorteo(input: SorteoInput!): Sorteo!`;
    - `cortarSorteo(id!): Sorteo!`.
  - Las escrituras exigen `seg.requireGestionar()`. En las queries, `page`/`size` se acotan como en `filterPreciosEspeciales` (máx. 100).
  - `SorteoParticipacionConsulta` usa JDBC con `LEFT JOIN operaciones.venta v ON v.id = p.venta_id AND v.sucursal_id = p.sucursal_id`:
    - `anulada = (v.id IS NULL OR v.estado = 'CANCELADA')`;
    - la búsqueda por `codigo` es por prefijo, sin distinguir mayúsculas. Así un código impreso `S3-2-1501-2/3` encuentra `S3-2-1501`: se corta en el último `-` si el sufijo tiene `/`.

- [ ] **Step 1:** Escribir el test de seguridad que falla, `saveSinRolFalla` / `cortarSinRolFalla`, y un test de la consulta, `codigoConSufijoDeTicketEncuentraParticipacion`, sobre la función pura que normaliza el código.
- [ ] **Step 2:** En el worktree, correr `./mvnw -q test -Dtest='Sorteo*'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** En el worktree, correr `./mvnw -q clean verify -B -DskipFlyway=true`. Esperado: BUILD SUCCESS.
- [ ] **Step 5:** Commit: `feat(sorteos): API GraphQL de sorteos y participaciones`.

---

## DESKTOP (D)

### Task D1: pantalla "Sorteos" (modo servidor)

**Files:**
- Create: `D/src/app/modules/operaciones/sorteo/`
  - `sorteo.model.ts`
  - `sorteo.util.ts` (+ `sorteo.util.spec.ts`, aunque no corra)
  - `graphql/graphql-query.ts`
  - `graphql/sorteo.gql.ts`
  - `sorteo.service.ts`
  - `list-sorteo/`
  - `sorteo-dialog/`
  - `sorteo-participaciones-dialog/`
- Modify: `D/src/app/modules/operaciones/operaciones.module.ts` (declaraciones).
- Modify: `D/src/app/shared/components/side-mini-variant/side-mini-variant.component.ts`:
  - entrada "Sorteos" al lado de "Promociones" (~l. 729), con `requiresServerMode: true`;
  - `case "list-sorteo"` con `openTabIfAuthorized([ROLES.CREAR_PRECIOS, ROLES.EDITAR_PRECIOS], ListSorteoComponent, "Sorteos")`.
- Modify: `D/src/app/shared/widgets/search-bar-dialog/search-bar.service.ts`: entrada al buscador.

**Interfaces:**
- Consumes: el schema de C3.
- Produces:
  - `sorteo.util.ts`:
    - `estadoSorteo(s): 'PROGRAMADO'|'VIGENTE'|'FINALIZADO'|'CORTADO'`;
    - `ayudaTickets(modo, minimo, cantidadFija, tope): string`. Por ejemplo, para `POR_MULTIPLO` con mínimo 100.000: `"Compra de 250.000 → 2 tickets"`.

- [ ] **Step 1:** Modelo, documentos GQL (clases `Query`/`Mutation`, como `precio-especial.gql.ts`) y `SorteoService` sobre `GenericCrudService`, como `precio-especial.service.ts`, con `onSaveCustom(..., {avisarExito:true})`.
- [ ] **Step 2:** Listado:
  - columnas: Nombre, Mínimo, Modo, Vigencia, Sucursales, Estado;
  - filtros de texto y "Solo vigentes";
  - acciones: Nuevo, Editar, Cortar (con confirmación) y Participaciones.
- [ ] **Step 3:** Diálogo de alta/edición:
  - campos del spec;
  - el selector de sucursales copia el de `precio-especial-dialog`, con un checkbox "Todas las sucursales" que lo deshabilita;
  - tabla de premios con agregar y quitar; el producto se elige con el buscador de productos existente;
  - la ayuda de tickets se ve en vivo;
  - si `tieneParticipaciones`, se deshabilitan mínimo, modo, cantidades, tarjeta y sucursales, con la leyenda del spec.
- [ ] **Step 4:** Diálogo de participaciones:
  - resumen por sucursal arriba;
  - tabla paginada;
  - filtro por sucursal;
  - campo "Validar código" que busca por código y resalta si está anulada.
- [ ] **Step 5:** Con Node 22, correr `npm run check` en D. Esperado: el build de producción termina sin errores.
- [ ] **Step 6:** Commit: `feat(sorteos): pantalla de sorteos en el central`.

### Task D2: POS: pedir el cliente, guardar y avisar

**Files:**
- Create: `D/src/app/modules/pdv/comercial/venta-touch/sorteo-cliente-dialog/` (componente del diálogo).
- Create: `D/src/app/modules/pdv/comercial/venta-touch/sorteo-pos.service.ts`
- Modify: `D/src/app/modules/operaciones/venta/graphql/graphql-query.ts`. Se agregan dos documentos nuevos, `saveVentaSorteo` y `saveVentaClienteSorteo`: son copias de `saveVenta` y `saveVentaCliente` con `$sorteoCliente: SorteoClienteInput`, que piden `sorteoTickets { nombreSorteo cantidad }`. **Los documentos existentes no se tocan**, porque las filiales viejas no conocen el argumento.
- Modify: `D/src/app/modules/operaciones/venta/venta.service.ts`: parámetro opcional `sorteoCliente?` en `onSaveVenta`. Si viene, se usan los documentos nuevos.
- Modify: `D/src/app/modules/pdv/comercial/venta-touch/venta-touch.service.ts` (pasa el parámetro).
- Modify: `D/src/app/modules/pdv/comercial/venta-touch/venta-touch.component.ts`, `onSaveVenta` (~l. 1751).
- Modify: `D/src/app/modules/pdv/pdv.module.ts` (declaración).

**Interfaces:**
- Consumes: `sorteosAplicables` y `saveVenta(sorteoCliente)` de F3/F5; `ClienteService.onConsultaRuc` y la búsqueda de cliente por documento que ya usa `add-factura-legal-dialog`.
- Produces:
  - `SorteoPosService.resolverParticipante(venta, cobro, ventaCreditoInput): Observable<SorteoClienteInput | null>`. Pasos:
    1. Si `isDelivery`, devuelve null.
    2. Llama a `sorteosAplicables` con `servidor=false` y `{totalGs: venta.totalGs, cobroDetalleList}`. Si hay error o viene vacío, devuelve null sin avisar.
    3. Si la venta ya tiene un cliente distinto del 0 (`venta.cliente?.id`) o hay `ventaCreditoInput?.clienteId`, devuelve `{clienteId, nombre, documento}` sin diálogo.
    4. Si no, abre `SorteoClienteDialogComponent` (`disableClose: true`), con:
       - el texto `"Esta compra participa del sorteo <nombre> (<N> tickets)."`;
       - el campo RUC/CI y el botón **Buscar**: primero busca el cliente en la base y, si no está, consulta la SET;
       - si no lo encuentra, se habilita el campo Nombre para escribirlo a mano;
       - los botones **Confirmar** (exige nombre) y **No participa** (devuelve null).

- [ ] **Step 1:** Servicio y diálogo.
- [ ] **Step 2:** En `venta-touch.onSaveVenta`, dentro del `new Observable`, antes de armar `guardado$`:
  - se encadena `resolverParticipante(...)` y después se arma `guardado$` con `sorteoCliente`;
  - el `catch` que existe tiene que seguir cubriendo el armado.
  Esto no se cancela con el `.subscribe().unsubscribe()` de `onTicketClick`, porque la suscripción interna es la que guarda y queda dentro del constructor. Mantenerla así.
- [ ] **Step 3:** En el bloque post-guardado (dentro del `try` existente, ~l. 1810), si `res?.sorteoTickets?.length` muestra el aviso con `notificacion$` de duración 8 y el texto `"Se imprimieron N tickets del sorteo X. El cliente debe firmarlos y depositarlos en la urna."`, una línea por sorteo.
- [ ] **Step 4:** Correr `npm run check`. Esperado: OK.
- [ ] **Step 5:** Documentar en el commit la prueba manual del Review Focus 4: con una filial sin F3, el cobro funciona igual y no aparece ningún diálogo.
- [ ] **Step 6:** Commit: `feat(sorteos): pedir cliente e imprimir tickets de sorteo en el POS`.

### Task D3: reimpresión en "Últimas ventas"

**Files:**
- Modify: `D/src/app/modules/operaciones/venta/ultimas-ventas-dialog/ultimas-ventas-dialog.component.html` y `.ts`.
- Modify: `D/src/app/modules/operaciones/venta/venta.service.ts`: `onReimprimirSorteo(id, servidor=true)`, con el mismo patrón que `onReimprimirVenta`:
  - en modo "imprimir desde esta PC" usa `impresionPos.imprimirTicket("SORTEO", id, "La reimpresión del ticket de sorteo")`;
  - si no, la mutation `reimprimirSorteo`.
- Modify: el documento de la query que llena "Últimas ventas", para pedir `tieneSorteo`.

- [ ] **Step 1:** Botón "Reimprimir ticket de sorteo" (icono `confirmation_number`, tooltip), con `*ngIf="venta.tieneSorteo"`, al lado de "Reimprimir venta". Maneja el error igual que `reimpresionVenta`.
- [ ] **Step 2:** Correr `npm run check`. Esperado: OK.
- [ ] **Step 3:** Commit: `feat(sorteos): reimprimir ticket de sorteo desde ultimas ventas`.

---

## Cierre

- [ ] Volver a verificar los números de migración contra todas las ramas remotas de C y F.
- [ ] Borrar los worktrees de compilación.
- [ ] Informar en qué rama quedó cada repo y que las migraciones quedaron sin commitear.
