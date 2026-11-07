# Plan — Tokens de servicio para bots (issue #579, fase 1)

Rama: `claude/busy-fermat-f08r04`, desde `develop` (`b9680f98`). Pieza única: **central**.
Skill de dominio cargada: `frc-central` (+ `frc-cicd`).

## Qué resuelve

Hoy un bot entra al GraphQL del central con el JWT de sesión de un usuario (BOT-LOOM, id 674). Ese
token:

- **no vence** (`JwtGenerator.generate` no setea `exp`);
- **no se puede revocar solo**: la única forma es rotar `APP_JWT_SECRET`, que desloguea a todos;
- **sigue sirviendo si el usuario se desactiva**: `JwtAuthenticationProvider.retrieveUser` valida
  la firma y no mira `usuario.activo` (el chequeo está solo en `/login`).

Esta fase agrega una **credencial de servicio** por bot: un token opaco, con vencimiento obligatorio,
revocable uno por uno, guardado como hash y ligado a un `usuario` existente. Como el request queda
autenticado con el nickname de ese usuario, **todos los chequeos de rol actuales**
(`TesoreriaSecurityService`, `InventarioSecurityService`, etc., que leen `personas.usuario_role`)
aplican sin cambios. Es la base sobre la que el MCP (fase 6) va a hablar con el central.

**No cambia** el login humano, `TokenController`, `JwtGenerator` ni el formato del JWT de sesión.

## Diseño

### Formato del token

`frcsvc_` + 43 caracteres base64url (32 bytes de `SecureRandom`). Se manda igual que hoy, con el
prefijo de header que ya exige el filtro:

```
Authorization: Token frcsvc_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

- En la base se guarda **solo el SHA-256 en hex** (64 caracteres) y los primeros 12 caracteres
  (`frcsvc_XXXXX`) como `prefijo` para identificarlo en listados y logs.
- El token en claro se devuelve **una sola vez**, en la respuesta de la mutation que lo crea.
- Con 256 bits aleatorios no hace falta hash lento (bcrypt): no hay diccionario que probar.
- Nunca se loguea el token; solo `prefijo` e `id`.

### Validación (camino de cada request)

`JwtAuthenticationProvider.retrieveUser`:

```
si token empieza con "frcsvc_"  -> TokenServicioValidator.validar(token)
si no                           -> JwtValidator.validate(token)   (sin cambios)
```

`TokenServicioValidator.validar` hace **una** consulta por `JdbcTemplate` (no JPA: corre dentro del
filtro de seguridad, antes de la sesión de Hibernate del request):

```sql
SELECT t.id, u.nickname
FROM personas.token_servicio t
JOIN personas.usuario u ON u.id = t.usuario_id
WHERE t.hash = ?
  AND t.revocado_en IS NULL
  AND t.expira_en > now()
  AND u.activo IS NOT FALSE
  AND upper(trim(u.nickname)) <> 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM personas.usuario_role ur
      JOIN personas.role r ON r.id = ur.role_id
      WHERE ur.usuario_id = u.id AND upper(trim(r.nombre)) = 'ADMIN')
```

- Sin fila → `BadCredentialsException` → 401, igual que un JWT inválido.
- Error de base (`DataAccessException`: pool agotado, base caída) → se envuelve en
  `InternalAuthenticationServiceException`. Falla cerrado **y** queda logueado como problema del
  token de servicio, en vez de salir como un 500 genérico.
- Con fila → `JwtUserDetails(nickname, password=null, token=null, sin authorities)`. El token en
  claro **no** se guarda en el `SecurityContext`, para que no aparezca en logs ni volcados.
- La condición de superusuario se evalúa **en cada request**, no solo al emitir: si a un usuario de
  servicio le dan el rol `ADMIN` después, sus tokens dejan de valer en vez de convertirse en llave
  maestra. Mismo criterio de comparación que `TesoreriaSecurityService.hasAnyRole` (sin mayúsculas
  ni espacios).
- `ultimo_uso_en` se actualiza **sin esperar locks**:
  `UPDATE personas.token_servicio SET ultimo_uso_en = now() WHERE id = (SELECT id FROM
  personas.token_servicio WHERE id = ? AND (ultimo_uso_en IS NULL OR ultimo_uso_en < now() -
  interval '5 minutes') FOR UPDATE SKIP LOCKED)`. Escribe a lo sumo cada 5 min por token. Si la
  fila está tomada (revocación en curso) no escribe y sigue. Si falla, se loguea y el request
  sigue: no es parte de la autorización.
- `u.activo IS NOT FALSE`: mismo criterio que el resto del repo para flags con NULL histórico.
- `AbstractUserDetailsAuthenticationProvider` usa `NullUserCache` (nadie lo cambia en
  `SecurityConfig`), así que `retrieveUser` corre en cada request y **la revocación es
  inmediata**.

### Dónde vale el token

El filtro autentica `/graphql/**`, `/api/**` y `/notification/**`
(`JwtAuthenticationTokenFilter.java:40-42`), y el provider no ve la ruta. Un token de servicio vale
en las tres, **igual que un JWT de sesión**. Se acepta en esta fase: lo que el bot puede hacer en
cada ruta lo acotan los roles de su usuario. `/subscriptions/**` y `/ws/gps/**` son `permitAll`
(`SecurityConfig.java:63`): ahí no autentica nada, ni el JWT ni el token nuevo (hueco aparte, #579
hallazgo 7).

### El usuario de servicio no conserva login humano

`UsuarioService.save` le pone la clave `"123"` a todo usuario creado sin clave
(`UsuarioService.java:186-187`), y `/login` solo rechaza usuarios inactivos
(`TokenController.java:49`). Un usuario de servicio con un token `frcsvc_` podría además loguearse
por `/login` y sacar el JWT eterno que esta fase quiere reemplazar.

Por eso, **al emitir un token**, `crear` reemplaza la clave del usuario por un valor aleatorio de
48 caracteres que no se devuelve ni se guarda en otro lado. Eso cierra el login humano de ese
usuario sin tocar `TokenController`. La respuesta y la documentación lo dicen explícitamente. No
invalida un JWT que ese usuario **ya** tenga emitido (no vencen). Por eso la recomendación
operativa es **un usuario nuevo por bot**, que nunca tuvo JWT, en vez de reutilizar BOT-LOOM (674).

### Reglas de emisión

Mutation `crearTokenServicio(usuarioId, nombre, diasVigencia)`:

- Solo superusuario: `TesoreriaSecurityService.requireSuperusuario()` como primera línea.
- `diasVigencia`: 1..365, default 90. Fuera de rango → error.
- `nombre`: obligatorio, 3..80 caracteres (ej. `loom-operaciones-bodega`).
- El usuario tiene que existir y estar activo.
- **Se rechaza** si el usuario es superusuario (nickname `ADMIN` comparado sin mayúsculas ni
  espacios, o rol `ADMIN`): un bot no lleva llave maestra. Como un token nunca es de un
  superusuario, un token de servicio tampoco puede emitir otros tokens.
- Reemplaza la clave del usuario por una aleatoria (ver arriba). Los roles mínimos se le asignan al usuario de servicio con las pantallas que ya
  existen.
- `creado_por` = usuario de la sesión (`requireUsuarioDeSesion`), nunca un id del cliente.

Mutation `revocarTokenServicio(id)`: solo superusuario; setea `revocado_en` y `revocado_por`. Es
idempotente (revocar uno revocado devuelve `true` y no cambia la fecha).

Query `tokensServicio(usuarioId)`: solo superusuario; nunca devuelve `hash`.

## Migración — `V267.5__token_servicio.sql`

Aditiva e idempotente. **Central-only**: no se registra en `configuraciones.replication_table`, así
que no entra en ninguna publicación (mismo criterio que `V243.1`). Las publicaciones se arman con
`FOR TABLE` y lista explícita (`LogicalReplicationService.java:173,198,236`), y el sync de
publicaciones solo agrega lo que está en `replication_table`
(`LogicalReplicationService.java:1802-1825`). **Regla: `token_servicio` no se registra nunca en
`replication_table`**; los hashes no tienen que salir del central.

```sql
CREATE TABLE IF NOT EXISTS personas.token_servicio (
    id             bigserial PRIMARY KEY,
    usuario_id     bigint NOT NULL REFERENCES personas.usuario (id) ON DELETE CASCADE,
    nombre         varchar(80) NOT NULL,
    prefijo        varchar(12) NOT NULL,
    hash           char(64) NOT NULL,
    creado_por     bigint REFERENCES personas.usuario (id) ON DELETE SET NULL,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    expira_en      timestamptz NOT NULL,
    revocado_en    timestamptz,
    revocado_por   bigint REFERENCES personas.usuario (id) ON DELETE SET NULL,
    ultimo_uso_en  timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_token_servicio_hash ON personas.token_servicio (hash);
CREATE INDEX IF NOT EXISTS idx_token_servicio_usuario ON personas.token_servicio (usuario_id);
```

- `.5`, número libre: el más alto en `develop` es `V266.3`.
- `ON DELETE CASCADE` en `usuario_id`: sin él, `deleteUsuario` (`UsuarioGraphQL.java:137`) fallaría
  por FK para cualquier usuario que alguna vez tuvo un token, incluso revocado. Un token sin usuario
  no sirve, así que se borra con él. `creado_por` y `revocado_por` quedan en `NULL`.
- La versión anterior del backend ignora la tabla: el rollback del JAR es seguro y la tabla queda
  sin uso. La tabla no se puede dropear (regla Flyway). Si hiciera falta apagar todo, el plan de
  rollback es `UPDATE personas.token_servicio SET revocado_en = now() WHERE revocado_en IS NULL`.
- No toca datos existentes. No necesita variables de entorno ni carpetas nuevas.
- Mapeo Java: `LocalDateTime`, como `Usuario.creadoEn`, para que el escalar `Date` serialice igual
  que el resto.

## Datos nuevos — quién escribe y quién lee

| Dato | Escribe | Lee |
|---|---|---|
| `token_servicio` (fila) | `TokenServicioService.crear` (mutation `crearTokenServicio`) | `TokenServicioValidator.validar` (cada request con `frcsvc_`); `tokensServicio` |
| `revocado_en` / `revocado_por` | `TokenServicioService.revocar` | `validar` (filtro `IS NULL`); `tokensServicio` |
| `ultimo_uso_en` | `TokenServicioValidator.validar` (throttle 5 min) | `tokensServicio` (para detectar tokens muertos) |
| `expira_en` | `crear` | `validar`; `tokensServicio` |
| `prefijo` | `crear` | `tokensServicio`; logs de `validar` |

## GraphQL — `graphql/personas/token-servicio.graphqls`

```graphql
type TokenServicio {
    id: ID!
    usuario: Usuario
    nombre: String!
    prefijo: String!
    creadoPor: Usuario
    creadoEn: Date
    expiraEn: Date
    revocadoEn: Date
    revocadoPor: Usuario
    ultimoUsoEn: Date
    vigente: Boolean!
}

type TokenServicioCreado {
    tokenServicio: TokenServicio!
    "El token en claro. Se muestra una sola vez: no se puede volver a consultar."
    token: String!
}

extend type Query {
    tokensServicio(usuarioId: ID): [TokenServicio!]!
}

extend type Mutation {
    crearTokenServicio(usuarioId: ID!, nombre: String!, diasVigencia: Int): TokenServicioCreado!
    revocarTokenServicio(id: ID!): Boolean!
}
```

Solo tipos y operaciones **nuevos**: ningún campo existente cambia, no hay enums. Desktop, PWA y
mobile no se enteran. No hay UI en esta fase: se administra por GraphiQL / `curl` con un token de
ADMIN. Una pantalla en el desktop queda para después (anotado en Huecos).

## Fases de implementación

Cada fase es un commit + push.

1. **Migración + entidad + repositorio + servicio de emisión.** `V267.5`, `TokenServicio` (JPA,
   para emitir, listar y revocar), `TokenServicioRepository`, `TokenServicioService`
   (generación, hash, reglas de emisión, revocación).
   - Tests (`TokenServicioServiceTest`, Mockito): formato y largo del token; el hash guardado no es
     el token y es su SHA-256; `diasVigencia` fuera de rango; rechazo de usuario inactivo,
     inexistente y superusuario (por nickname y por rol); revocar es idempotente; las mutations
     llaman a `requireSuperusuario` antes de cualquier otra cosa.
2. **Validación en el filtro.** `TokenServicioValidator` + bifurcación en
   `JwtAuthenticationProvider.retrieveUser` (`startsWith("frcsvc_")`, sensible a mayúsculas).
   - Tests unitarios (`TokenServicioValidatorTest`, `JwtAuthenticationProviderTest`; el provider se
     construye con su constructor de `UsuarioService`): token sin fila →
     `BadCredentialsException`; `DataAccessException` → `InternalAuthenticationServiceException`;
     token válido → `JwtUserDetails` con el nickname y **sin** el token en claro; un JWT común
     sigue yendo por `JwtValidator` (regresión); un `frcsvc_` inválido nunca cae a `JwtValidator`;
     el `UPDATE` de `ultimo_uso_en` que falla no corta el request.
   - Test de la cadena HTTP, estilo `SecurityConfigTest`: `Authorization: Token frcsvc_x` con el
     validador devolviendo vacío → 401.
   - **Test del SQL contra PostgreSQL real**, porque con el `JdbcTemplate` mockeado las condiciones
     de seguridad (revocado, vencido, inactivo, superusuario) pasarían aunque el SQL estuviera mal.
     `TokenServicioSqlIT`, apagado por `@EnabledIfSystemProperty(named = "it.tokenservicio")` como
     los otros `*IT`. Se corre en esta sesión contra un PG 16 local con el esquema mínimo
     (`personas.usuario`, `role`, `usuario_role`) y la migración `V267.5` aplicada. Casos: vigente
     → fila; revocado, vencido, usuario inactivo, nickname `admin` en minúscula, rol `ADMIN`
     otorgado **después** de emitir → sin fila; usuario con `activo` NULL → fila; borrar el usuario
     borra sus tokens.
3. **Resolver + `.graphqls`.** `TokenServicioGraphQL` (query + 2 mutations).
   - Tests: `SchemaEnumsSincronizadosTest` sigue verde (no hay enums nuevos); el resolver delega en
     el servicio.
4. **Documentación.** Sección en `CLAUDE.md` del central («Tokens de servicio para bots») con cómo
   emitir, revocar y rotar; el plan se borra en el PR final.

Build rápido después de cada fase: `./mvnw -q compile`. Batería al final:
`./mvnw clean verify -B -DskipFlyway=true`.

## Verificación de runtime (paso 9)

En local contra una copia de alpha (`spring-boot:run` con profile `dev`):

1. `curl` con un token sin crear → 401.
2. Crear token con ADMIN → la respuesta trae `frcsvc_...`.
3. `curl` con ese token a una query protegida por rol que el usuario tiene → 200; a una que no
   tiene → error de rol.
4. Revocar → mismo `curl` → 401.
5. Desactivar el usuario → 401 con un token vigente.
6. `POST /login` con el usuario de servicio y la clave `123` → falla (la clave se reemplazó al
   emitir).
7. Darle el rol `ADMIN` al usuario de servicio → su token da 401.
8. Login humano con usuario dev y una query cualquiera → sigue igual.

## Riesgos y cómo se cubren

- **Toca `security/`.** `CLAUDE.md` pide leer `REPORTE_VULNERABILIDADES.md` antes; ese archivo vive
  fuera de este repo (`../../`) y **no está disponible en esta sesión**. Queda anotado como paso
  incumplido. No se tocan `TokenController` ni `JwtGenerator` (lista de archivos vedados).
- **Un bug en la bifurcación podría dejar pasar tokens.** La rama nueva solo se toma con el prefijo
  `frcsvc_`, que un JWT nunca tiene (un JWT empieza con `eyJ`). Cualquier excepción en `validar`
  termina en 401, nunca en autenticado. Tests de regresión del JWT común.
- **Carga en la base:** una consulta indexada por request de bot, más un `UPDATE` cada 5 min por
  token. Despreciable frente al tráfico de las cajas.
- **Clave histórica `Graphql`:** si una instancia sigue aceptándola, cualquiera firma un JWT de
  sesión válido. No lo resuelve esta fase y no se puede verificar desde acá (es config del host).
  Queda en los pendientes de #579.

## Qué queda sin verificar

- Flyway: el CI corre con `-DskipFlyway=true`. Se verifica en local contra una copia de alpha
  (paso 10). Si en esta sesión no hay base disponible, queda **incumplido** y se anota en el PR.
- Que los roles de BOT-LOOM en bodega sean los mínimos: es dato de producción, fuera del código.

## Huecos (para después)

- Pantalla de tokens de servicio en el desktop (Configuración > Usuarios).
- Vencimiento del JWT de sesión humano y chequeo de `usuario.activo` en el JWT común (impacta
  desktop, PWA y filial; no entra acá).
- Las suscripciones GraphQL no pasan por el aspecto de seguridad (#579, hallazgo 7).

## Registro de pasos del ciclo

| Paso | Estado |
|---|---|
| 1 · Rama | Hecho: `claude/busy-fermat-f08r04` desde `origin/develop` (`b9680f98`). El nombre lo fija la sesión, no la convención `feature/*` |
| 2 · Skill | Hecho: `frc-central`, `frc-cicd` |
| 3 · Análisis | Hecho: código de `security/`, los 6 `*SecurityService`, `TransferenciaGraphQL`, `V243.1` |
| 4 · Plan | Este archivo |
| 5 · Auditoría del plan | Hecho: 2 auditores sin verse (eje A contrato/propagación, eje B reversibilidad/estado). Hallazgos abajo |
| 6 · Presentar y commitear | Hecho: aprobado por el usuario el 2026-11-07 |

## Auditoría del plan (paso 5)

Cada hallazgo se verificó contra el código antes de aplicarlo.

| # | Eje | Sev. | Hallazgo | Qué se hizo |
|---|---|---|---|---|
| B-A | B | Alta | El usuario de servicio conserva login humano: clave `"123"` por defecto (`UsuarioService.java:186-187`) y `/login` solo mira `activo` (`TokenController.java:49`). Revocar el token no cierra ese camino | Verificado. `crear` reemplaza la clave por una aleatoria; recomendación de usuario nuevo por bot |
| B-B | B | Alta | El chequeo de superusuario solo al emitir: un rol `ADMIN` otorgado después convierte el token en llave maestra. Comparación de nickname sin normalizar | Verificado (`TesoreriaSecurityService.java:154-158`). La condición pasa al SQL de cada request, normalizada |
| B-C | B | Media | `DataAccessException` no es `AuthenticationException`: sale como 500 sin identificar | Verificado. Se envuelve en `InternalAuthenticationServiceException` + test |
| B-D | B | Media | El `UPDATE` de `ultimo_uso_en` puede esperar un lock y colgar el request | Aplicado `FOR UPDATE SKIP LOCKED` |
| B-E | B | Media | Con `JdbcTemplate` mockeado los tests no prueban las condiciones del SQL | Se agrega `TokenServicioSqlIT` contra PG real (hay PG 16 en la sesión) + test de la cadena HTTP |
| B-F | B | Baja | `JwtUserDetails` guardaría el token en claro | Se pasa `null` |
| A-1 | A | Media | FK sin `ON DELETE` rompe `deleteUsuario` para usuarios con tokens | Verificado (`UsuarioGraphQL.java:137-144`). `CASCADE` / `SET NULL` |
| A-5 | A | Media | El token valdría también en `/api/**` y `/notification/**` | Verificado (`JwtAuthenticationTokenFilter.java:40-42`). Documentado y aceptado: lo acotan los roles |
| A-2 | A | Baja | La afirmación de central-only no citaba código | Citadas las líneas de `LogicalReplicationService`; regla de no registrar en `replication_table` |
| A-3 | A | Baja | Tipo Java de los timestamps | `LocalDateTime`, como el resto |
| A-4 | A | Baja | Comparación del prefijo | `startsWith` sensible a mayúsculas |
| A-6 | A | Baja | Cambio de nickname | Sin cambio: el SQL lee el nickname vigente en cada request |
| A-7, B-H | A/B | Baja | Sin variables nuevas; la tabla no se puede dropear en rollback | Documentado (rollback = revocar todos por `UPDATE`) |
| B-G | B | Baja | El resolver depende de que `requireSuperusuario` sea la primera línea | Ya cubierto por el test de la fase 1 |

Sin contradicciones entre los dos ejes.
