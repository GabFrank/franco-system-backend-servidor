# Plan — Receptor extranjero B2F en el central y país global (#666)

Aprobado por Franco el 19/11/2026 (nominación de extranjeros y remisión al exterior, fuera de alcance).

Etapa 2 de filial #134. Etapa 1:
- filial #271, **mergeado en develop** el 19/11: `pais_id` local V116.1, factura PDV, solo si es nulo.
- desktop #771, abierto.

Ramas nuevas desde `develop`:
- central: `fix/sifen-receptor-extranjero-central`
- filial: `fix/persona-pais-conservar-local` (la de #271 ya se mergeó)
- desktop: `feat/persona-pais-admin`

## Diagnóstico (paso 3)

1. **Réplica.**
   - `central_pub` publica `personas.persona` sin lista de columnas (`V0:15124`), con REPLICA IDENTITY FULL en el
     central (`V0:5888`) y en la filial (`v32:77`).
   - Una lista de columnas que excluya `pais_id` no sirve: con identity FULL, PG exige que cubra todas las columnas.
   - El sync automático solo hace `ADD TABLE` de las tablas faltantes (`LogicalReplicationService:1805-1845`).
   - Si el central agrega `pais_id`, cada UPDATE de persona baja con `pais_id` NULL. Y toda filial sin la columna
     corta la suscripción entera por «missing replicated column».
2. **Medido en PG 16.13** (par pub/sub `zz`, identity FULL en los dos lados, trigger `ENABLE REPLICA` + `WHEN`):
   - un UPDATE del central con `pais_id` NULL conserva el local;
   - un UPDATE con otro país lo cambia;
   - los UPDATE de otros campos se siguen aplicando aunque el `pais_id` difiera (PG 16 busca por la PK);
   - un UPDATE local a NULL no lo toca el trigger.

   En PG ≤ 15, con identity FULL, la búsqueda compara todas las columnas: verificar PG ≥ 16 en la flota.
3. **Qué emite el central:**
   - facturas (`crearDocumentoElectronicoDesdeFactura`, superusuario);
   - notas de crédito (`SifenNotaCreditoBuilder:135-161`, desde el snapshot de la nota: siempre PRY, B2B PJ fijo);
   - notas de remisión (siempre PRY);
   - nominación (`SifenEventoService:571,725`).

   El helper del central es casi idéntico al del filial viejo (B2C/PRY fijos, `OTRO` → `0160`). No hay `ensayarDE`:
   los errores llegan como `RuntimeException` del resolver.
4. **`documento_electronico` es bidireccional** (`V50`, `V226.1`). El central tiene el `xml_original` de las facturas
   de filial, y `NotaCreditoService:122` ya lo usa (estado APROBADO). Pero la nominación no cambia ese XML.
5. **Persona en el central:**
   - sin país;
   - `savePersona` exige `ROLES_PERSONAS`;
   - `/api/personas` (`PersonaSyncController`) hace alta idempotente por documento y edición por id, sin control
     de rol;
   - `PersonaService.save` y `normalizarDocumento` cortan el documento en el guion (`AB-12345` → `AB`);
   - `ClienteGraphQL:219,247` y `SifenConsultaRucService:45` reducen a dígitos.
6. **Desktop:** el alta y edición de persona y cliente van al central (varias pantallas). El filial no tiene `pais`
   en GraphQL, y algunas queries de cliente van al filial.
7. **Migraciones:** última en develop `V286.3`, así que la nueva es `V287.1`. Confirmar antes del push: hay PRs
   abiertos con migraciones.

## Decisiones

- **Trigger en la filial** (`V117.1`): `BEFORE UPDATE ... FOR EACH ROW WHEN (NEW.pais_id IS NULL AND OLD.pais_id
  IS NOT NULL)`, `ENABLE REPLICA` (solo en el apply de la réplica). Conserva el país local. Corregir desde el central
  = mandar otro país, nunca NULL. Rollback: `DROP TRIGGER` + `DROP FUNCTION`, documentado.
- **País global**, desde la caja siempre «solo si es nulo»:
  1. `PersonaSyncRequest.paisId`: solo en el **alta**. En la edición por id se ignora.
  2. `POST /api/personas/{id}/pais`: `UPDATE ... SET pais_id=? WHERE id=? AND pais_id IS NULL` nativo. 400 si el país
     no existe. La filial lo llama **siempre** que asigna, también tras un alta, porque el alta idempotente devuelve
     la persona existente sin tocarla. Con un método tolerante (try/catch, boolean, sin exigir body); si falla,
     queda lo local.
  3. `backfillPaisPersonas` (superusuario) en cada central:
     - lee de solo lectura cada filial con el patrón de `ConvenioIntegridadService` pero con **timeout de 8 s**;
     - si `sucursal.ip` falla, toma el host de `pg_subscription.subconninfo`;
     - graba con UPDATE nativo por fila «solo si es nulo», con try/catch por fila (FK huérfana);
     - devuelve grabados, conflictos (países distintos entre filiales), países desconocidos, filiales sin conexión o
       sin la columna, y los ids tocados;
     - es idempotente;
     - no se da por terminado mientras queden filiales sin consultar (las apagadas, al volver).
- **Corrección de país:** solo en el admin del desktop contra el central (`savePersona`/`saveCliente`, que ya exigen
  roles; `paisId` explícito, también Paraguay).
- **NC:**
  - factura con nominación aprobada → helper con la persona actual;
  - si no → `gDatRec` del XML de la factura;
  - sin DE → como hoy.

  La **nominación de un receptor extranjero** queda fuera: sigue como hoy.
- **Remisión al exterior:** fuera de alcance (es exportación).
- **Helper del central:** port del filial (#271).
- **Documentos con letras:** no se cortan en el guion, no se reducen a dígitos, no se consultan en SET.

## Tabla de datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `persona.pais_id` (central, V287.1) | `savePersona` (`PersonaInput.paisId`) y `saveCliente` (`ClienteInput.paisId`) desde el admin del desktop; alta REST (`PersonaSyncRequest.paisId`); `POST /api/personas/{id}/pais` (filial); `backfillPaisPersonas` | `SifenReceptorHelper` (central); réplica → filiales; `Persona.pais` en GraphQL (admin del desktop, queries propias) |
| `persona.pais_id` (filial) | réplica (solo no nulo, por el trigger); `PersonaService.asignarPais` | `SifenReceptorHelper` (filial) |

## Fases y PRs

| Fase | Repo | Contenido | Tests (todos fallan con el código viejo) |
|---|---|---|---|
| F1 | filial | `V117.1` trigger | par pub/sub `zz` con los 5 casos medidos, incluido el UPDATE de otro campo |
| C1 | central | `V287.1`; `Persona.pais`; GraphQL `pais`/`paisId` en Persona y Cliente; alta REST con `paisId`; endpoint solo-si-nulo; documento con letras sin cortar | mapeo, endpoint (nulo, ya tiene, país inexistente), documento con guion |
| C1b | central | `backfillPaisPersonas` | JDBC simulado: conflicto, sin conexión, sin columna, FK, idempotencia |
| C2 | central | port del helper, `dDirRec`, NC (nominada / XML / sin DE), búsquedas con letras | helper (los del filial), NC B2F y nominada, pasaporte |
| F2 | filial | alta con `paisId` + llamada tolerante al endpoint | central sin el endpoint (404) → sigue la factura |
| D | desktop | selector de país en el alta y edición de persona y cliente (queries propias del admin) | check AOT + pantalla |

## Orden de despliegue (por canal: alpha → beta/farmacia → stable/bodega)

0. **Verificación previa bloqueante** en cada filial activa del canal, en solo lectura: PG ≥ 16, columna
   `personas.persona.pais_id` y trigger presente. Las apagadas (farmacia 5, bodega 25) se anotan: al volver tienen
   que actualizar el filial antes de prender la réplica.
1. Filial: etapa 1 (#271) + F1 en el canal, y esperar el autoupdate (≤15 min, verificado con el paso 0).
2. Central C1 + C1b + C2. El trigger protege lo local.
3. `backfillPaisPersonas` en ese central, hasta que no queden filiales sin consultar.
4. Filial F2 y desktop D (D recién con C1 en ese canal).

En alpha, central y filial comparten host: mergear F1, esperar el autoupdate y recién ahí desplegar el central.

## Pruebas de runtime (paso 9)

- Par `zz` (trigger).
- Central 8081 + filial 8082 + desktop:
  - alta de cliente extranjero en la factura → la persona nace con país en el central;
  - persona existente → endpoint;
  - backfill contra la filial local (también con la filial apagada → sin conexión);
  - NC sobre una factura B2F y sobre una nominada;
  - pasaporte con guion;
  - corrección en el admin → baja a la filial;
  - matriz de convivencia: central viejo + filial F2 (404 tolerado), filial sin la columna + central C1 (corte, para
    confirmar la verificación del paso 0).

## Sin verificar / riesgos

- PG de toda la flota (los docs dicen 16, sin cobertura total): lo cubre el paso 0.
- `sucursal.ip` desactualizada: host alternativo y listado de filiales no consultadas.
- Las reglas de negocio de SIFEN para B2F siguen sin verificar.
- Rollback de C1: la columna queda (aditiva); el JAR viejo la ignora. Una filial trabada por falta de columna se
  arregla aplicando V116.1 (el autoupdate).
