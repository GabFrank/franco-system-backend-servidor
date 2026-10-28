# Plan — Gráfico de convenios (ventas a crédito) por sucursal

Rama: `feature/grafico-convenios-sucursal` en **central** y **desktop**. Filial, mobile y mobile-pwa: N/A
(el filial no cambia: el central solo lee su base).

## Qué resuelve

Hoy la revisión de convenios por sucursal se hace a mano con dos queries sobre
`financiero.venta_credito`: una cuenta por sucursal en un rango de `creado_en`, otra lista los de una
sucursal para revisar abiertas y canceladas. La pantalla nueva junta las dos y agrega la comparación
contra la base de cada filial, para detectar convenios que no llegaron al central por réplica.

Decisiones tomadas con Franco (2026-10-28):

- «Canceladas» = estado `CANCELADO` (anuladas). Se muestran también `FINALIZADO` y el resto, por separado.
- Integridad = **central contra cada filial, en vivo**.
- Período = **rango de fechas libre** (desde/hasta, días completos), sin precarga por mes.
- Filtros: rango de fechas, estado de la venta a crédito, sucursales (multi-select con «Todas»).

## Contrato GraphQL nuevo (central)

```graphql
convenioIntegridadPorSucursal(fechaInicio: String!, fechaFin: String!,
                              estado: EstadoVentaCredito, sucIds: [ID]): [ConvenioSucursalIntegridad!]!

type ConvenioSucursalIntegridad {
  sucursalId: ID!  sucursalNombre: String
  central: ConvenioConteo!          # siempre
  filial: ConvenioConteo            # null si no se pudo consultar
  estadoIntegridad: String!         # OK | DIFERENCIA | SIN_CONEXION | SIN_IP
  error: String                     # motivo de SIN_CONEXION / SIN_IP
  faltanEnCentral: Int!  sobranEnCentral: Int!  estadoDistinto: Int!
  idsFaltanEnCentral: [ID!]!  idsEstadoDistinto: [ID!]!   # hasta 50 c/u, para el detalle
  ultimoCentral: String  ultimoFilial: String              # creado_en más reciente en el rango
}
type ConvenioConteo { total: Int! abiertas: Int! finalizadas: Int! canceladas: Int! otras: Int! }
```

Reglas:

- Fechas `yyyy-MM-dd`; rango `[inicio 00:00, fin+1 día 00:00)`, mismo criterio en central y filial.
- `estado` filtra **los conteos**. La comparación (faltan / sobran / estado distinto) se hace siempre
  sobre todos los estados: con el filtro aplicado, un convenio ABIERTO en la filial y CANCELADO en el
  central aparecería como «faltante», que es falso.
- Conteo = todas las filas de `venta_credito` del rango (no se exige `venta.estado = CONCLUIDA` como la
  query manual 1: una venta anulada deja su crédito en CANCELADO y esa fila es justamente la que se
  quiere ver).
- Sucursales: activas, sin la 0 (SERVIDOR). `sucIds` vacío = todas. Sin IP → `SIN_IP`, sin consultar.
- Filial: conexión JDBC de solo lectura a `ip:puerto/<base filial>` (mismo origen de IP, puerto, base
  y credenciales que ya usa `LogicalReplicationService`), con timeout de conexión y de socket cortos
  y la sesión en `default_transaction_read_only`. Se consultan en paralelo (pool acotado); una filial
  caída no frena a las demás (`SIN_CONEXION` con el mensaje).
- Autorización: primera línea del resolver exige rol `ADMIN` (patrón `RrhhSecurityService.requireAnyRole`),
  igual que el menú «Gráficos» del desktop. Abre conexiones a las filiales: no queda solo con login.

## Ajustes por la auditoría del plan (paso 5, 2026-10-28)

| # | Hallazgo | Eje | Qué se hace |
|---|---|---|---|
| 1 | El helper `createRemoteJdbcTemplate` no tiene timeouts: el default de pgjdbc es infinito, así que una filial caída cuelga el hilo del request | B alta | El servicio arma su propia conexión con `connectTimeout=3`, `loginTimeout=3`, `socketTimeout=20`, `statement_timeout=15s` y `ApplicationName`. Una conexión por consulta, cerrada con try-with-resources |
| 2 | El paralelismo tiene que ser acotado y propio | B alta | `ExecutorService` fijo de 6 hilos, propio del servicio, más un tope global de espera (25 s). No se usa `parallelStream` |
| 3 | Doble click en Filtrar = N corridas contra todas las filiales | B alta | En el servicio, una corrida a la vez: si hay otra en curso responde «consulta en curso». En el desktop, el filtrado no corta la corrida en curso (`switchMapSinCortar`, como el resto) |
| 4 | Comparar solo por `id` da falsos faltantes (la PK es `(id, sucursal_id)`) | A media / B alta | Las dos consultas filtran `sucursal_id = :suc` y la comparación usa la clave `(id, sucursal_id)`. Se agregan tests: mismo id en otra sucursal, filas de otra sucursal en la filial, estado NULL (va a «otras») y filial vacía |
| 5 | Solo lectura | B media | `setReadOnly(true)` en la conexión, `default_transaction_read_only=on` y únicamente `SELECT` con parámetros |
| 6 | Fechas | B media | El desktop manda el string `yyyy-MM-dd` sin pasar por `Date` (por el corrimiento de horario de verano ya visto). El central hace `LocalDate.parse(...)`: inicio `atStartOfDay()`, fin `plusDays(1)`. `creado_en` es `timestamp` sin zona |
| 7 | La credencial de las filiales es un supuesto | A media | Se usan las mismas que `LogicalReplicationService`. **Franco confirmó (2026-10-28) que las filiales de bodega usan el mismo usuario y clave.** Si alguna la rechaza, sale `SIN_CONEXION` con «autenticación rechazada» |
| 8 | El mensaje de error puede filtrar host o usuario | A baja | `error` lleva solo la categoría: «sin respuesta (timeout)», «autenticación rechazada», «conexión rechazada» o «error de consulta». El detalle va al log |
| 9 | Puerto y base | A baja | Se usa `Sucursal.getPuerto()` (Postgres), no `puertoServidor`. La base sale del mismo origen que `getBranchDbName()` (`configuraciones.local`, «general» por defecto) |
| 10 | Desktop nuevo contra central viejo | A/B baja | Si la query falla, la pantalla muestra el error y la tarjeta del tablero no se rompe, porque el tablero usa un mock |
| 11 | Farmacia: la filial 5 está apagada y la 6 (DEPOSITO) no tiene IP | A baja | Es lo esperado: aparecen como `SIN_CONEXION` o `SIN_IP`. No se filtran a mano |

La tabla es chica: 1.301 filas y 248 kB en la filial 24 local. Leer el rango completo no le pesa a la filial, así que no se pone tope de días.

## Fases

### Fase 1 — central: query + servicio + test

- `graphql/grafico/convenio-integridad.graphqls` (o extensión de `grafico.graphqls`), DTOs
  `ConvenioSucursalIntegridad` y `ConvenioConteo`.
- `service/grafico/ConvenioIntegridadService`: query al central, query a cada filial, comparación.
  La comparación es una función pura (`comparar(filasCentral, filasFilial, estado)`).
- Resolver en `GraficoGraphQL` con el chequeo de rol.
- Test JUnit de la comparación: faltantes, sobrantes, estado distinto, filtro de estado que no altera
  la comparación, tope de 50 ids, filial null.
- Gate: `./mvnw clean verify -B -DskipFlyway=true` (copia fuera de `dev-frc` si jdtls pisa `target/`).

### Fase 2 — desktop: pantalla en «Gráficos»

- `grafico/convenio-sucursal/`: filtros (sucursales multi con «Todos» reusando
  `GraficoFiltroSucursalesMulti`, rango de fechas, estado, Filtrar, Limpiar).
- Gráfico de barras apiladas por sucursal (abiertas / finalizadas / canceladas / otras, del central)
  en `frc-grafico-shell`.
- Tabla de integridad por sucursal: central vs filial (total y por estado), faltan, sobran, estado
  distinto, último convenio de cada lado y un chip de estado (OK / DIFERENCIA / SIN CONEXIÓN / SIN IP);
  los ids de la diferencia en el tooltip de la celda.
- GQL `convenio-integridad-por-sucursal.gql.ts`, método en `GraficoService`, tarjeta en el tablero
  `graficos` (con su mock) y caso en `onChartClick`.
- Gate: `npm run check`.

Orden de PRs: central primero (el desktop llama a la query nueva). No hay migración ni réplica.

## Datos nuevos

No se persiste nada. Los campos nuevos son de la respuesta GraphQL:

| Dato | Escribe | Lee |
|---|---|---|
| `ConvenioSucursalIntegridad.*` / `ConvenioConteo.*` | `ConvenioIntegridadService` (central) | `ConvenioSucursalComponent` (desktop) |

## Pruebas de runtime

- Central propio en 8081 (perfil `dev`) contra `bodega@5551` + desktop `ng serve`.
- Sucursal 24 apunta a `localhost:5551/general`: comparación real de punta a punta. Crear por SQL en
  `general` un convenio que no esté en `bodega` (faltante) y cambiar el estado de otro (estado distinto);
  verificar los dos en la tabla; restaurar al final.
- Sucursal 25 / 999 sin IP → `SIN_IP`. Sucursal con IP inalcanzable → `SIN_CONEXION` sin colgar la
  pantalla: probar apuntando una sucursal de prueba a una IP muerta en la base local (y restaurar).
- **No** seleccionar en local las sucursales con IP real `172.25.1.x`: son filiales de producción.
  Es solo lectura, pero la prueba se hace con la 24.
- Filtro de estado, multi-select («Todos» y selección parcial), rango sin datos, usuario sin ADMIN
  (el central rechaza).

## Sin verificar / riesgos

- Que en producción la base de las filiales acepte la conexión desde el central con las credenciales
  del central: lo asume `LogicalReplicationService`, que hoy se usa solo a pedido.
- Tiempo total con ~20 filiales: paralelo con timeout corto; si una filial tarda, la pantalla espera
  como mucho ese timeout.
- Hora de corte: el rango es por días completos. Si el convenio corta a una hora puntual del 29, hace
  falta agregar hora al filtro (no pedido).

## Registro de ejecución (2026-10-28)

**Pruebas de runtime** (central de la rama en 8081, perfil dev con Flyway apagado porque la base
local no tenía las migraciones 258.3/259.3 de develop; desktop `ng serve -c web` en 127.0.0.1:4200):

| Caso | Cómo | Resultado |
|---|---|---|
| Sucursal sana | 24 contra `general` local | OK, 9 = 9 |
| Faltante, sobrante y estado distinto | En `bodega`: 1338 → CANCELADO, 1339 fuera de rango, fila 999001 nueva; se restauraron al final | 1339 / 999001 / 1338 detectados |
| Filtro de estado | ABIERTO y Canceladas | Cambian los conteos y la comparación sigue igual |
| Sin IP | 25 y 999 | SIN IP |
| IP muerta / puerto cerrado | 25 → `10.255.255.1`, 999 → `localhost:5999` | SIN CONEXIÓN a los ~3 s: «Sin respuesta (timeout)» y «Conexión rechazada» |
| IP con caracteres de URL | 25 → valor con `/` y `?` | SIN IP, no se conecta |
| Sin ADMIN | `PRUEBANR` | Rechazado |
| Dos corridas a la vez | API + pantalla | La segunda recibe «consulta en curso» y la pantalla no queda trabada |
| Fechas inválidas | Rango invertido y formato | Mensaje claro |
| Pantalla | Tarjeta del tablero, multi-select, calendario, Limpiar | OK |

- **Bug encontrado en la prueba:** pgjdbc informa el `connectTimeout` sin `SocketTimeoutException`, y
  la pantalla decía «Conexión rechazada». Se corrigió y se agregó un test con la excepción real.
- **Incidente de prueba:** una corrida con «Todas las sucursales» salió a las filiales reales de
  bodega. Fue un SELECT por filial, de solo lectura, sin escrituras.
- **No probado por pantalla:** que la vista se limpie cuando la consulta falla. La extensión de Chrome
  se desconectó; queda para la prueba de Franco.

**Auditoría del diff (paso 8):**

- **Fijo 2:** N/A. No hay migraciones, ni tablas, ni enums tocados.
- **Fijo 1:** el rol es la primera línea, no hay inyección SQL y no se filtran datos en la respuesta.
  Se agregó validación de host, puerto y base antes de conectar, y la cancelación de las consultas
  pendientes.
- **Fijo 3:** el contrato coincide entre schema, Java y desktop. Con un central viejo, el desktop
  avisa el error y el tablero no se rompe.
- **Cambios por la auditoría:**
  - un hilo por filial (24);
  - `estadoIntegridad` nunca queda null;
  - la vista se limpia si la consulta falla.
- **Descartado:** mover la función del gráfico a otro archivo. Es solo prolijidad.
