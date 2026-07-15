# CLAUDE.md -- frc-comercial/central

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`franco-dev-systems` (artifactId), server principal del producto **Franco Systems**. Corre en HQ y es el origen de la replicacion logica de PostgreSQL hacia las filiales (`frc-comercial/filial`). Repo git independiente: `GabFrank/franco-system-backend-servidor`.

Stack: **Spring Boot 2.7.18 / Java 11 / PostgreSQL** + **GraphQL** (`graphql-java-kickstart`). Package root `com.franco.dev`. JAR final: `frc-central-server.jar` (configurado via `<jar.finalName>`) -- **no cambiar** este nombre, los servicios systemd y los scripts de deploy lo esperan literal.

## Build & Run

```bash
./mvnw spring-boot:run                    # Run con profile activo
./mvnw clean package                      # Build
./mvnw test                               # Tests
./mvnw clean verify -B -DskipFlyway=true  # CI build (sin Flyway)
```

Maven Wrapper: 3.6.3. CI usa JDK 11 (Temurin). El pom.xml declara `<java.version>11</java.version>` (source/target 11).

## Project Structure

```
src/main/java/com/franco/dev/
  domain/          -- 256 entidades JPA (19 modulos de dominio)
  service/         -- 192 servicios (25 subdirectorios)
  repository/      -- 166 repositorios Spring Data JPA
  graphql/         -- 390 clases GraphQL (71 resolvers)
  security/        -- 19 clases JWT/auth
  config/          -- Configuracion general + multitenant (20 clases)
  fmc/             -- Firebase Cloud Messaging
  service/sifen/   -- Integracion factura electronica (SIFEN)
  scheduler/       -- Tareas programadas (@Scheduled)

src/main/resources/
  graphql/         -- 152 archivos .graphqls (schema por dominio)
  db/migration/    -- 166 migraciones Flyway (V0..V118)
  application.properties
  application-dev.properties
  application-ci.properties
  application-user-dev.properties  (ignorado por git, overrides locales)

.github/
  workflows/       -- ci.yml, release.yml, deploy.yml
  scripts/         -- deploy.sh (deploy + rollback automatico)
  settings.xml     -- Maven settings para GitHub Packages

docs/              -- Manuales, changelogs, utilitarios
```

### Modulos de dominio (bajo `domain/`)

`administrativo`, `configuracion`, `empresarial`, `financiero`, `general`, `login`, `operaciones`, `pdv`, `personas`, `productos`, `reportes`, `transferencias` y otros.

## API: GraphQL (NO REST)

- **Schema root:** `src/main/resources/graphql/schema.graphqls`
- **152 archivos .graphqls** organizados por dominio (`financiero/`, `personas/`, `productos/`, `operaciones/`, `general/`, `configuracion/`, `administrativo/`, `empresarial/`, `replication/`, `print/`)
- **71 resolvers** en `com.franco.dev.graphql.*`
- GraphiQL habilitado en desarrollo
- Endpoints nuevos van en `graphql/` (resolvers + schema), **no en `controller/`**
- Existe una carpeta `controller 2/` paralela a `controller/` -- no es typo, no borrar sin investigar

## Database: PostgreSQL + Flyway

- **Flyway 5.2.3**, 166 migraciones (V0..V118)
- Config: `baseline-on-migrate=true`, `out-of-order=true`, `ignore-missing-migrations=true`
- DDL: `hibernate.ddl-auto=none` (solo migraciones, nunca auto-DDL)
- Multi-tenancy basada en schemas (20 clases en `config/multitenant/`)
- Puerto default local: 5551

### Reglas criticas de migraciones Flyway

Una migracion mal hecha puede dejar el sistema inoperativo. **El rollback automatico NO la revierte** (Flyway no hace down migrations; rollback de JAR no rollback de DB).

| Permitido | Prohibido |
|---|---|
| `CREATE TABLE` | `DROP TABLE` |
| `ALTER TABLE ADD COLUMN` (nullable o con default) | `ALTER TABLE DROP COLUMN` |
| `CREATE INDEX` | `ALTER TABLE RENAME COLUMN` |
| `ALTER TABLE ALTER COLUMN SET DEFAULT` | `ALTER TABLE ALTER COLUMN TYPE` (cambio de tipo) |
| `INSERT INTO` (datos de referencia) | `DELETE FROM` / `TRUNCATE` |

**Eliminar o renombrar columnas:** estrategia de 2 versiones. Version N: crear columna nueva, codigo deja de usar la vieja. Version N+1 (solo cuando N esta estable en produccion): eliminar la vieja.

**Naming:** `V{numero}__{descripcion_con_underscores}.sql`. Numeracion secuencial y unica. **Nunca modificar una migracion ya aplicada** (Flyway compara checksums). Si falla una migracion, corregirla directamente en el mismo archivo VXX (no crear una nueva para arreglar la anterior).

## Overrides locales: NO tocar `application-dev.properties`

La clase `com.franco.dev.config.UserDevPropertiesEnvironmentPostProcessor` (registrada en `META-INF/spring.factories`) carga `application-user-dev.properties` con prioridad maxima cuando el profile `dev` esta activo. Este archivo esta ignorado por git.

**Siempre usar `application-user-dev.properties` para cambios locales** (paths, credenciales DB, IPs). Pushear cambios a `application-dev.properties` rompe el dev de otros y genera release tags con paths basura.

## Security

- JWT-based stateless authentication
- `SecurityConfig.java` configura HttpSecurity, JWT filter, CORS
- `SecurityGraphQLAspect` para seguridad declarativa en resolvers (`@AdminSecured`, `@Unsecured`)
- Endpoints protegidos: `/graphql/**`, `/subscriptions/**`
- Endpoints publicos: `/public/**`, `/login`
- CSRF deshabilitado, CORS habilitado

> **Vulnerabilidades conocidas:** Plaintext passwords en `security/TokenController.java`, password dentro de claims JWT en `security/jwt/JwtGenerator.java`. Ver `../../REPORTE_VULNERABILIDADES.md` antes de tocar `security/`.

## Features especiales

- **Multi-tenancy:** Schema-based via Hibernate (`config/multitenant/`)
- **SIFEN:** Factura electronica (jsifenlib 0.2.4-frc.13, fork custom en GitHub Packages)
- **Firebase:** Push notifications (Firebase Admin SDK 9.1.1)
- **Google Drive:** Upload/storage de imagenes
- **Reportes:** JasperReports 6.20.0, iTextPDF, Apache POI, ZXing (QR/barcode)
- **Async:** `@EnableAsync` + `@EnableScheduling`

## CI/CD

### Branches

- Tres branches long-lived: `develop` (alpha) -> `release/beta` (beta) -> `master` (stable)
- **Este repo usa `master`, no `main`**, y **`release/beta` long-lived**. Ambas protegidas con `enforce_admins=true` -- siempre PR, no push directo.
- Branch naming: `feature/modulo-descripcion`, `fix/modulo-descripcion`, `refactor/modulo-descripcion`, `chore/descripcion`, `hotfix/descripcion`. Minusculas, guiones, sin acentos ni espacios.
- `feature/*`, `fix/*`, etc. salen siempre de `develop`. **`hotfix/*` sale de `master`.**

### Releases automaticos

- `semantic-release` lee commits convencionales: `feat:` -> minor, `fix:` -> patch, `feat!:` o `BREAKING CHANGE:` -> major. `chore:`/`refactor:`/`ci:`/`docs:`/`test:`/`perf:` no liberan.
- **Promocion `release/beta -> master`: merge commit, NO squash.** El squash colapsa los `feat:`/`fix:` originales y semantic-release calcula mal el bump.
- Push a cualquiera de las 3 branches dispara release. **Nunca pushear sin confirmacion explicita del usuario.**
- Workflow Release tiene concurrency group (serializa runs) y fetch de git notes antes de semantic-release.

### Deploys

- Deploys son **manuales** via GitHub Actions `workflow_dispatch` (`Deploy` workflow), parametrizados por `version` e `instance`.
- Tres targets: `alpha` (172.25.1.200:8083), `farmacia` (beta, :8082), `bodega` (production, :8081)
- Production (`bodega`) requiere aprobacion en GitHub Environments.
- Health check post-deploy: `GET /actuator/health` (timeout 120s, interval 5s). Si falla: **rollback automatico** a version anterior.
- SSH key: `~/.ssh/frc-deploy`, user: `deploy`

### Hotfix flow (urgencia en produccion)

1. `git checkout master && git pull` -> branch desde **master** (no develop)
2. `git checkout -b hotfix/descripcion`
3. Fix + commit `fix(modulo): ...` + push
4. PR `hotfix/* -> master`, CI verde, merge -> semantic-release genera version de produccion
5. Deploy manual a produccion
6. **Inmediatamente despues: PR `master -> develop`** para que `develop` tenga el fix. **Nunca dejar un hotfix solo en `master`.**

## Pull Requests

- **Tamano**: idealmente menos de 400 lineas de cambio neto. Una responsabilidad por PR.
- **Descripcion del PR debe incluir**: que resuelve, como probarlo, impacto en DB (si aplica), impacto en rollback (si aplica), riesgo (bajo/medio/alto).
- **Sin commits "WIP"** al mergear.
- Revisar **impacto cross-proyecto**: si cambias un endpoint GraphQL que el desktop/mobile usan, verificar que no se rompen.

### Checklist para PR con cambio de DB

- [ ] Migracion versionada (`V{n}__...sql`) con numero unico
- [ ] Probada localmente con `./mvnw clean verify`
- [ ] Es **retrocompatible** con la version anterior del backend
- [ ] No hace `DROP`/`RENAME`/cambio de tipo sin la estrategia de 2 versiones
- [ ] La descripcion del PR documenta el impacto en DB y el plan de rollback

## Lo que NUNCA hacer

1. Push directo a `master`, `release/beta` o `develop` -- siempre via PR
2. `git push --force` a ramas compartidas
3. Modificar migraciones de Flyway ya aplicadas
4. `DROP TABLE/COLUMN` sin la estrategia de 2 versiones
5. Commitear secretos (`.env`, keystores, tokens, passwords, certificados `.pfx`)
6. Squash merge en PRs -- usar **merge commit**
7. Deploy a produccion sin aprobacion
8. Deploy los viernes (durante el periodo de adopcion)
9. Saltear el CI con `--no-verify` -- si falla, corregir, no buscar bypass
10. Cambiar nombre de artefactos (`frc-central-server.jar`) sin coordinar con el equipo y actualizar los scripts de deploy / servicios systemd primero

## Otros cambios con riesgo de rollback

### Variables de entorno nuevas

Si tu codigo necesita una variable nueva, **avisar al lider tecnico ANTES de crear el PR**. Tiene que crearla en los servidores antes de que el codigo se despliegue, sino la aplicacion falla al arrancar.

### Carpetas locales en el server

Si tu codigo espera que exista una carpeta en disco, crearla programaticamente con `Files.createDirectories()` o `File.mkdirs()`. **No asumir** que el servidor la tiene.

### Cambios en la API GraphQL

1. **No eliminar campos existentes de golpe.** Agregar el campo nuevo, mantener el viejo.
2. Recien cuando todos los clientes (desktop + mobile) esten actualizados, eliminar el campo viejo.
3. Si es inevitable, marcarlo como breaking change: `feat!: cambiar respuesta de /productos`. Esto sube MAJOR e implica que filiales + desktop + mobile tienen que actualizarse coordinadamente.

## Convenciones

- **GraphQL, no REST.** Endpoints nuevos van en `graphql/` (resolvers + schema), no `controller/`.
- **Idioma de dominio:** espanol (`razon_social`, `numero_factura`). Identificadores genericos en ingles.
- **Package root:** `com.franco.dev` (compartido con `filial`). No mezclar con `com.frcefact` (otro proyecto independiente, `frc-efact`).
- **JAR finalName:** `frc-central-server.jar` -- no renombrar.

## Referencias relacionadas

- [CICD-WORKFLOW.md](CICD-WORKFLOW.md) -- Flujo CI/CD detallado con ejemplos de comandos
- [../../REPORTE_VULNERABILIDADES.md](../../REPORTE_VULNERABILIDADES.md) -- Auditoria de seguridad
- [../../CLAUDE.md](../../CLAUDE.md) -- Mapa cross-project del workspace
- [../filial/CLAUDE.md](../filial/CLAUDE.md) -- Server filial, mismo mecanismo de overrides, replica desde este central
- [../../cicd-implementation/guia-desarrollo-cicd.md](../../cicd-implementation/guia-desarrollo-cicd.md) -- Guia consolidada de CI/CD

## Automated Issue Resolution (Claude Code Action)

Este repo esta configurado para resolucion automatizada de issues via Jira + Claude Code.

### Branch naming
Crear desde `develop`: `auto/{jira-key}-{slug}`
- `{jira-key}`: Jira key en minusculas (ej: `frc-42`)
- `{slug}`: max 40 chars, minusculas, solo hyphens, del titulo del issue
- Ejemplo: `auto/frc-42-fix-validacion-ruc`

### Commit format
`fix(scope): descripcion en minusculas` o `feat(scope): descripcion`
- Scope: modulo afectado (ej: `clientes`, `ventas`, `auth`)
- Max 72 chars en subject
- Referenciar Jira key en el body del commit

### Preflight: correr tests antes de abrir PR
`./mvnw clean verify -B -DskipFlyway=true`

Si los tests fallan, NO abrir PR — comentar en el issue explicando el fallo.

### PR rules
- SIEMPRE draft: nunca PR ready-for-review
- Target: `develop`
- Titulo: Conventional Commits con Jira key (ej: `fix(clientes): validacion RUC [FRC-42]`)
- Body: que cambio, como testear, impacto DB, riesgo rollback
- NUNCA mergear — requiere review humano
- NUNCA push directo a `master`, `release/beta`, o `develop`

### Archivos que NO tocar
- Secretos, `.env`, keystores, certificados
- Migraciones Flyway ya aplicadas
- `DROP TABLE`, `DROP COLUMN`, `RENAME COLUMN` sin estrategia 2 versiones
- Nombre de artefacto `frc-central-server.jar`
- Codigo de auth en `security/TokenController.java` o `security/jwt/JwtGenerator.java` (ver REPORTE_VULNERABILIDADES.md)
