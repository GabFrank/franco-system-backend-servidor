# Plan — crédito por convenio de la otra empresa (bodega ↔ farmacia)

Rama: `feature/convenio-otra-empresa` en el central y en la PWA (los dos desde `develop`, 2026-11-13).

## Qué se quiere

Un funcionario puede tener convenio (venta a crédito) en la bodega y en la farmacia. Hoy la PWA le
muestra solo el de la instancia en la que entró. La idea es que en «Mis finanzas» vea también el
saldo de la otra empresa.

## Decisiones (tomadas con Franco el 2026-11-13)

1. **Se cruza por CI (`persona.documento`), no por id.** Los ids de `persona` y `cliente` son de
   cada instancia: los de la bodega van muy por delante de los de la farmacia y no coinciden.
2. **Pregunta el central, no la PWA.** La PWA sigue hablando con un solo central. El token de la
   bodega no vale en la farmacia, y al revés: si preguntara la PWA, el funcionario necesitaría
   usuario y sesión en las dos.
3. **El central remoto se consulta con un usuario técnico**, cuyo token va en la configuración del
   servidor (no en el repo, que es público). Los tokens del central no vencen (`JwtGenerator` no
   pone `exp`) y se invalidan solo si se rota `APP_JWT_SECRET` de la instancia remota.
4. **Solo con queries que la versión estable ya tiene.** Del lado remoto se usan
   `personaPorDocumento`, `clientePorPersonaId` y `ventaCreditoPorCliente(estado: ABIERTO)`, que
   existen igual en `master` y en `develop` (verificado 2026-11-13). Así la farmacia, que se
   actualiza primero (beta), ya ve la bodega aunque la bodega siga en la versión anterior. La
   bodega ve la farmacia cuando se actualiza ella. **El lado remoto no cambia.**
5. **No se usa `clientePorPersonaDocumento`**: por debajo llama a `buscarOCrearClientePorDocumento`,
   que crea el cliente y consulta SIFEN. Mirar un saldo no puede escribir en la otra base.
6. **Alcance: solo el resumen** (límite, utilizado, disponible) de la otra empresa, en Mis
   finanzas. Sin la lista de convenios ni el detalle de ventas remotas, y sin tocar el resumen de
   Inicio. Se puede sumar después.

## Diseño

### Central

- Configuración nueva, con default vacío. **Sin las variables, la función queda apagada y el
  arranque no cambia**: no hay que crear nada en los servidores antes de desplegar.

  | Propiedad | Variable | Ejemplo |
  |---|---|---|
  | `convenio.remoto.url` | `CONVENIO_REMOTO_URL` | `http://localhost:8081/graphql` |
  | `convenio.remoto.token` | `CONVENIO_REMOTO_TOKEN` | token del usuario técnico del otro central |
  | `convenio.remoto.nombre` | `CONVENIO_REMOTO_NOMBRE` | `Bodega` |

- `service/financiero/ConvenioRemotoService` (nuevo):
  1. Usuario de la sesión por el nickname del `SecurityContext`, igual que
     `RrhhSecurityService.currentUsuario()`, y de ahí `persona.documento`.
  2. Manda el CI **tal cual está guardado, solo con `trim`**. Vacío → `null`. **No se normaliza a
     dígitos**: `personaPorDocumento` busca por igualdad exacta, y un RUC con DV (`1234567-8`)
     normalizado da `12345678`, que puede ser el CI de otra persona en la otra base.
     `persona.documento` es `UNIQUE` (`V0`), así que el texto exacto identifica a una sola
     persona. El costo es que un CI escrito distinto en cada base (`7.460.508` y `7460508`) no se
     encuentra: la sección no aparece, pero nunca muestra a otra persona.
  3. Tres POST al central remoto con `Authorization: Token <token>`, en este orden:
     `personaPorDocumento(texto)` → `clientePorPersonaId(id)` → `ventaCreditoPorCliente(id, ABIERTO)`,
     pidiendo solo `id`, `credito` y `valorTotal`. Usa un **`RestTemplate` propio con connect 2 s y
     read 5 s**, no el bean compartido (10 s y 30 s, que usan el proxy a filiales y la impresión):
     un remoto caído no puede dejar la pantalla medio minuto esperando. El servicio no es
     `@Transactional`, para no retener una conexión de la base durante el HTTP. El token no se
     loguea: los logs llevan la URL y el mensaje, nunca el `HttpEntity`.
  4. Devuelve `ConvenioRemoto { empresa, limite, utilizado }`. `utilizado` es la suma de
     `valorTotal` de los convenios abiertos, la misma cuenta que hace hoy la PWA
     (`resumirCredito`). `saldo_total` no sirve: está `NULL` en las 78 873 filas de
     `venta_credito` de la bodega local (2026-11-13).
- Resolver: `convenioOtraEmpresa: ConvenioRemoto` en `venta-credito.graphqls`, **sin argumentos**.
  El CI sale siempre de la persona de la sesión, nunca de un parámetro: si no, cualquier usuario
  autenticado podría leer el crédito de cualquier CI de la otra empresa.

Qué devuelve en cada caso. «No hay» y «no pude preguntar» son respuestas distintas:

| Caso | Respuesta | La PWA |
|---|---|---|
| Sin configurar (falta URL o token) | `null` | no muestra la sección |
| Usuario sin persona o persona sin CI | `null` | no la muestra |
| El CI no existe en el otro central, o no es cliente ahí | `null` | no la muestra |
| Error de red, HTTP o `errors` de GraphQL del remoto | error GraphQL «No se pudo consultar el crédito en …» | muestra el error |
| Dos `cliente` para la misma persona en el remoto | `findByPersonaId` lanza, el remoto devuelve `errors` → error | muestra el error, **nunca el crédito de otra persona** |

Un CI duplicado en el remoto no puede pasar: `persona_un_documento UNIQUE (documento)`.

### PWA

- Operación `convenio-otra-empresa.ts` en `graphql/financiero/venta-credito/`, con el alias `data:`.
- `MisFinanzasService.convenioOtraEmpresa()`.
- `mis-finanzas.page.ts`: debajo del resumen propio, una tarjeta «Crédito en {empresa}» con límite,
  utilizado y disponible. Reusa `resumirCredito`, o su forma, para que las dos tarjetas cuenten
  igual. Tres estados: cargando, `null` → no se muestra, error → «No se pudo consultar el crédito
  de la otra empresa». El texto es fijo porque, cuando falla, no hay respuesta de la que sacar el
  nombre. Si falla la otra empresa, la propia carga igual.
- La capa de datos usa `errorPolicy: 'all'` (`core/graphql/gql-base.ts`): un error llega con el
  campo en `null`. El servicio tiene que mirar los `errors` antes de leer `data`, o un fallo se ve
  como «sin convenio».
- Mis finanzas hoy muestra los montos sin el «ojo» de Inicio. La tarjeta nueva sigue la misma
  regla que la pantalla donde está.
- Si el central es anterior y no tiene el campo, la consulta falla y la tarjeta muestra el error.
  Por eso el central se despliega antes que la PWA en cada canal (ver «Orden»).

## Tabla de datos nuevos

| Dato | Lo escribe | Lo lee |
|---|---|---|
| `CONVENIO_REMOTO_URL` / `_TOKEN` / `_NOMBRE` | quien opera el servidor, a mano en `/opt/frc-backend-central/<instancia>/.env` (fase 3) | `ConvenioRemotoService` |
| campo `convenioOtraEmpresa` | `ConvenioRemotoGraphQL` (central) | `mis-finanzas.page.ts` (PWA) |

No hay migración ni tablas nuevas: N/A para Flyway porque no se persiste nada. N/A para el filial
porque no participa. N/A para el desktop porque no muestra convenios del funcionario.

## Fases

### Fase 1 — central: servicio y query (`feat(convenio): ...`)

- `ConvenioRemotoService`, `ConvenioRemotoGraphQL`, el tipo en `venta-credito.graphqls` y las tres
  propiedades vacías en `application.properties`.
- Tests unitarios (`ConvenioRemotoServiceTest`, `RestTemplate` mockeado):
  sin configurar → `null` sin llamar al remoto; sin CI → `null`; CI con puntos o guion → lo
  manda igual (con `trim`); persona no encontrada → `null`; sin cliente → `null`; suma de
  abiertos; `errors` del remoto (incluye cliente duplicado) → excepción; error HTTP o timeout →
  excepción; manda el header `Authorization: Token`.
- Batería: `./mvnw clean verify -B -DskipFlyway=true`.

### Fase 2 — PWA: la tarjeta en Mis finanzas (`feat(mis-finanzas): ...`)

- Operación, método del servicio, tarjeta y estados.
- Tests vitest: sección oculta con `null`, valores con respuesta, error con la falla del remoto
  sin romper el resumen propio, y `data: { convenioOtraEmpresa: null }` **con** `errors` → error,
  no sección oculta.
- Bloque nuevo en `docs/PLAN_TESTEO_MANUAL.md` y nota en `docs/modulos/mis-rrhh-y-finanzas.md`.
- Gate: `npm run build` y `npm test`.

### Fase 3 — configuración en los servidores (sin código; la hacen Franco o Gabriel)

- Usuario técnico en cada instancia (bodega y farmacia), sin roles; token por `/login`.
- **Desactivar al usuario técnico no revoca el token**: `JwtValidator` solo mira la firma, no la
  base. Los cortes reales son sacar `CONVENIO_REMOTO_TOKEN` del `.env` de la instancia que
  consulta, o rotar `APP_JWT_SECRET` de la remota, que cierra la sesión de todos (y con
  `app.jwt.legacy=true` no corta un token firmado con la clave histórica).
- Variables en el `.env` de cada instancia. `deploy.sh` solo reescribe las claves de correo, así
  que las demás sobreviven a los deploys.
- El detalle (usuario, cómo se emite y se rota el token) va a `dev-frc/notas-seguridad`, **no** a
  este repo ni a los PRs: el repo es público.

## Orden de despliegue

1. PR central → `develop` → `release/beta`. Deploy a farmacia + `.env` de farmacia apuntando a la
   bodega. Desde acá, la farmacia ya puede consultar la bodega.
2. PR PWA → `develop` → `release/beta` (sirve `farmacia.app` y `beta.app`). **Después** del
   central de farmacia. La PWA publica sola al mergear y el deploy del central es manual: nada
   impide el orden inverso, así que el PR de la PWA lleva el check «central de la instancia
   desplegado con el campo». Si llega antes, la tarjeta muestra el error, no rompe la pantalla.
3. Cuando sale a `master`: deploy a bodega + `.env` de bodega apuntando a la farmacia, y después la
   PWA de prod.

No hay replicación involucrada: nada viaja entre central y filiales.

## Pruebas de runtime

- Dos centrales locales: el de la rama en el 8081 y otro en el 8084, los dos sobre `bodega@5551`.
  El 8081 apunta al 8084 con el token de un usuario local. Como la base es la misma, la otra
  empresa tiene que mostrar los mismos números que el resumen propio: es el control.
- PWA local contra el 8081 con el usuario `MR` y con un funcionario con convenios abiertos (sale de
  la base). Casos: con convenio, sin cliente (sección oculta), central remoto apagado (error en la
  tarjeta, el resumen propio carga) y token inválido (error).
- Además, un jar de `master` en otro puerto como remoto, para probar el contrato contra el código
  que corre la bodega. El alpha no sirve para eso: corre `develop`.

## Auditoría del plan (paso 5, 2026-11-13) — aprobado por Franco con el camino operativo para el token

Dos auditores sin verse: eje A (contrato y propagación) y eje B (reversibilidad, estado y
seguridad).

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Normalizar el CI a dígitos puede cruzar a dos personas (RUC con DV) | B, media-alta | Se manda el CI exacto con `trim` |
| `RestTemplate` compartido con 10 s y 30 s: un remoto caído cuelga la pantalla y retiene hilos | B, media | `RestTemplate` propio con 2 s y 5 s, servicio sin `@Transactional` |
| El token técnico da acceso a todo el GraphQL remoto (roles rotos, #177) y no vence | A y B, media | **Lo decide Franco** (ver abajo). Se documentó cómo se revoca |
| El error no puede nombrar la empresa | A, media | Texto fijo |
| `errorPolicy: 'all'` convierte un error en `null` | A, media | El servicio mira los `errors`, con test |
| PWA antes que el central → error visible | A y B, baja | Check en el PR de la PWA. No se detecta «campo inexistente» para ocultarlo: el orden alcanza |
| ¿`saldoTotal` en vez de `valorTotal`? | B, baja | `saldo_total` está `NULL` en todas las filas: se queda `valorTotal` |
| Cliente duplicado en el remoto | A, baja | Fila en la tabla y test |
| «El alpha corre código estable» era falso | A, baja | Se prueba con un jar de `master` |
| Cuenta compartida vinculada a la persona de un funcionario ve su crédito | B, media | Sin cambio: es lo mismo que pasa hoy con Mis finanzas |

Se descartó el breaker («si falló, no reintentar por 60 s»): con 2 s y 5 s no hace falta todavía.

**Decisión pendiente (Franco):** el usuario técnico puede leer y ejecutar todo el GraphQL de la
otra instancia, porque hoy cualquier usuario autenticado puede (#177). Lo que se puede hacer sin
tocar el remoto es operativo: un usuario técnico por dirección, token solo en el `.env` y nunca en
logs. La alternativa de raíz es un endpoint dedicado con un secreto de un solo uso, pero exige
actualizar el remoto y perder la ventaja de que la farmacia vea la bodega antes.

## Sin verificar

- **Formato del CI en la farmacia.** En la bodega local: 5969 personas, 12 con caracteres que no
  son dígitos (`7.460.508`, `7168390 ` con espacio), 7 vacías y 4 CI que se repiten al normalizar.
  Si en una base el CI está con puntos y en la otra sin ellos, la búsqueda exacta no lo encuentra
  y la sección no se muestra. Se verifica mirando la base de farmacia: no tuve permiso para
  entrar por ssh a la VM y no se forzó.
- **Red entre instancias.** Según la skill del central, bodega (8081) y farmacia (8082) corren en
  la misma VM, así que `http://localhost:<puerto>/graphql` debería alcanzar. No se miró en el
  servidor.
- **Login del usuario técnico**: si `/login` exige usuario activo o algún rol. Se ve en la fase 3.
