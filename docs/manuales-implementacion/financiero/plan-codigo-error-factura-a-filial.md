# Plan — código en el error de `saveFacturaLegalToFilial` (#425)

Rama `fix/factura-legal-filial-codigo-error` en central y desktop, desde `develop`.

## Problema

`FacturaLegalGraphQL.saveFacturaLegalToFilial` devuelve cualquier fallo como un mismo
`RuntimeException("Error al crear factura en servidor filial: …")`. El cliente no distingue un
rechazo (la factura no se emitió: se puede corregir y reintentar) de un envío sin confirmar (corte,
5xx, respuesta ilegible: el filial pudo emitirla, y reintentar da otra factura con otro número).
El desktop (#489, issue desktop #471) lo adivina por el texto del mensaje.

## Qué cambia del issue original

- **Punto 2 (filial) ya está hecho**: `FacturaLegalController` responde 400 ante `GraphQLException`
  en `develop`, `release/beta` y `master` (`5a36efe`). N/A para filial.
- **Error de conexión partido en dos**: `ConnectException`, `NoRouteToHostException` y
  `UnknownHostException` dicen que el pedido nunca salió, así que son `FILIAL_RECHAZO`. Un
  `SocketTimeoutException` puede ser de conexión o de lectura y con `SimpleClientHttpRequestFactory`
  solo los separa el texto: va siempre a `FILIAL_SIN_CONFIRMAR`, que es el lado seguro.
- Lo que gana el cliente con la conexión rechazada es solo «puerto cerrado con host vivo» (filial
  caído con la PC prendida). Un filial inalcanzable de verdad (red cortada, PC apagada) da corte de
  conexión a los 10 s, y eso sigue como `FILIAL_SIN_CONFIRMAR`.
- **Punto 4 (consulta de existencia)**: fuera de este trabajo, queda como issue aparte.

## Contrato

Todo error de `saveFacturaLegalToFilial` sale como `GraphqlErrorException` con:

- `message`: el mismo texto de hoy, `"Error al crear factura en servidor filial: " + causa`. Como el
  `GraphqlExceptionHandler` desanida los `GraphQLError`, llega **sin** el prefijo
  `Exception while fetching data (/data) : ` (igual que en `SolicitudPagoGraphQL`). El desktop ya lo
  saca con `limpiarMensajeGraphQL`, así que ve el mismo texto.
- `extensions.classification`: `DataFetchingException`, el mismo de hoy (`.errorClassification`).
- Sin `path` ni `locations`: hoy nadie los lee (el único lector de `extensions` en el desktop es
  `claveIdempotencia.ts`, que solo mira `ValidationError`).
- `extensions.codigo`:

| Código | Significado | Casos |
|---|---|---|
| `FILIAL_RECHAZO` | la factura no se emitió | sucursal inexistente, sin IP, error al armar el pedido (`mapearRequest` entra al mismo `try` que la serialización), 4xx del filial, no se pudo conectar (`ConnectException`, `NoRouteToHostException`, `UnknownHostException`) |
| `FILIAL_SIN_CONFIRMAR` | pudo haberse emitido | 5xx del filial, cualquier otro `RestClientException` (corte por tiempo, conexión cortada, cuerpo del 201 que no se deserializa), respuesta que no es 201 o 201 sin cuerpo, cualquier excepción no clasificada |

## Fases

### Fase 1 — central

- `FacturaLegalFilialService`: una excepción propia, `EnvioAFilialException` (clase anidada
  pública, `RuntimeException` con `boolean sinConfirmar`), lanzada en cada punto con el **mismo
  mensaje** de hoy. Las causas de conexión se miran recorriendo `getCause()`.
- `FrancoSystemsApplication.getResTemplate`: `factory.setOutputStreaming(true)` explícito. Es el
  default, pero la garantía «`ConnectException` = no salió» depende de él: sin streaming,
  HttpURLConnection reintenta el POST por dentro.
- `FacturaLegalGraphQL.saveFacturaLegalToFilial`: el `catch` arma un `GraphqlErrorException` con
  el mensaje de hoy y `extensions.codigo` (`FILIAL_SIN_CONFIRMAR` si no es `EnvioAFilialException`),
  y un `log.error` con la causa en lugar del `printStackTrace`.
- Sin cambios en `.graphqls`, enums, migraciones ni seguridad.
- Tests:
  - servicio (Mockito sobre `RestTemplate`): un caso por fila de la tabla, con el `message`
    **exacto** (el desktop viejo depende de esos bytes), incluida una cadena
    `ResourceAccessException → ConnectException` anidada;
  - sockets reales con el `RestTemplate` del bean: puerto cerrado → `FILIAL_RECHAZO`; servidor que
    lee el pedido y cierra → `FILIAL_SIN_CONFIRMAR` y una sola conexión recibida;
  - resolver, sin referenciar la clase nueva: `GraphqlErrorException` con `extensions.codigo`, y
    pasado por `GraphqlExceptionHandler.processErrors` → `toSpecification()` con el mensaje sin
    prefijo y el código;
  - revertir el fix y comprobar que el test del resolver falla por comportamiento.

### Fase 2 — desktop

- `graphqlErrorUtils.esEnvioAFilialSinConfirmar`, en este orden:
  1. algún error con `extensions.codigo` `FILIAL_SIN_CONFIRMAR` → true; con `FILIAL_RECHAZO` → false
     (otros códigos se ignoran);
  2. sin código, mensaje con el prefijo `Error al crear factura en servidor filial: ` (central viejo)
     → el regex de hoy;
  3. cualquier otro error → true. Cubre lo que el `catch` del resolver no ve (el commit del
     `@Transactional` que falla después del 201, un `Error`): el lado seguro es un aviso de más.
- `alFallarElEnvioAFilial` no cambia: un `FILIAL_RECHAZO` es un arreglo de errores, o sea, un
  rechazo según `esRechazoDelServidor`, y libera el botón.
- Tests Karma en `graphqlErrorUtils.spec.ts` y `add-factura-legal-dialog.component.spec.ts`: código
  manda sobre el texto en los dos sentidos, y sin código sigue el texto.
- Doc: `docs/manuales-implementacion/financiero/factura-a-filial-sin-confirmar.md`.

## Datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `extensions.codigo` del error | `FacturaLegalGraphQL.saveFacturaLegalToFilial` (central) | `esEnvioAFilialSinConfirmar` → `AddFacturaLegalDialogComponent.alFallarElEnvioAFilial` (desktop) |

## Compatibilidad

- Desktop nuevo con central viejo: sin código, usa el texto (como hoy).
- Desktop viejo con central nuevo: el mensaje limpio es el mismo. Con la conexión rechazada, el
  desktop viejo sigue diciendo «sin confirmar» (por el texto), y el nuevo libera el botón.
- Ningún otro cliente usa la mutation (grep en mobile-pwa y mobile: cero).
- Orden: central → desktop (§3.2). No toca tablas ni replicación.

## Pruebas por pantalla (local)

Central propio, filial local con `sifen.enabled=false` y desktop `ng serve -c web`; Lista de
facturas → Adicionar, sucursal de prueba:

1. Rechazo del filial (timbrado sin teléfono, como en #489): un aviso, botón habilitado, código `FILIAL_RECHAZO`.
2. Filial apagado (conexión rechazada): ahora un solo aviso y botón habilitado (`FILIAL_RECHAZO`).
3. 500 del filial: stub HTTP local en el puerto de la sucursal → «No se pudo confirmar…».
4. Corte a los 30 s: stub que tarda más de 30 s → «No se pudo confirmar…».
5. 201 con cuerpo ilegible: stub → «No se pudo confirmar…».
6. Todo arriba: «Guardado con éxito», una sola factura.

El puerto de la sucursal se cambia por SQL para los stubs y se restaura al final.

## Auditoría del plan (paso 5)

Eje A (contrato): sin altos. Incorporados: test por el `GraphqlExceptionHandler` y mirar el JSON en
Network; conservar `classification`; `log.error`; reconocer solo los dos códigos.
Eje B (estado): ningún `FILIAL_RECHAZO` con factura guardada (el `@Transactional` del filial
deshace los 400; sin reintento interno del POST con streaming). Incorporados: error sin código ni
prefijo → sin confirmar; `setOutputStreaming(true)` explícito con test de sockets; mensajes exactos
en los tests; `mapearRequest` dentro del `try`; alcance real de la conexión rechazada.
Descartado: quitar el `@Transactional` del resolver (`mapearCliente` lee relaciones lazy; el punto 3
del desktop ya cubre el caso).

## Sin verificar

- El cambio de prefijo del mensaje en clientes viejos anteriores a `limpiarMensajeGraphQL`: se ve
  mejor (sin el prefijo), no peor.
