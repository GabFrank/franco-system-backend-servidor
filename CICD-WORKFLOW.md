# CI/CD Workflow - FRC Central Server

> **NOTA:** Este repo usa `master` (no `main`). Los workflows escuchan ambas ramas.

## Flujo General

```
feature/* --PR--> develop (alpha) --merge--> release/next (beta) --merge--> master (stable)
                     |                           |                             |
               semantic-release            semantic-release              semantic-release
               v3.2.0-alpha.1             v3.2.0-beta.1                   v3.2.0
                     |                           |                             |
                Deploy manual              Deploy manual                Deploy manual
              (instance: alpha)         (instance: farmacia)          (instance: bodega)
```

## 1. Desarrollo (Alpha)

1. Crear rama desde `develop`:
   ```
   git checkout develop && git pull
   git checkout -b feature/agregar-reporte-ventas
   ```
2. Hacer commits con prefijos convencionales:
   - `feat: agregar endpoint de reporte de ventas` -- genera bump **minor** (3.1.0 -> 3.2.0)
   - `fix: corregir calculo de IVA en factura` -- genera bump **patch** (3.1.0 -> 3.1.1)
   - `chore:`, `ci:`, `docs:` -- **NO generan release**
3. Crear **PR** hacia `develop` (nunca push directo, `enforce_admins=true`).
4. Al mergear el PR, `semantic-release` genera una release alpha (ej: `v3.2.0-alpha.1`).
5. Eliminar la rama feature despues del merge.

## 2. Promocion a Beta

1. Mergear `develop` en `release/next`:
   ```
   git checkout release/next && git pull
   git merge develop
   git push
   ```
2. `semantic-release` genera una release beta (ej: `v3.2.0-beta.1`).

## 3. Promocion a Stable (Produccion)

> **IMPORTANTE: Usar MERGE COMMIT, NO squash.**
>
> Si se hace squash con mensaje `chore: merge release/next into master`, semantic-release
> solo ve un commit `chore:` y hace un patch bump (o ninguno). Se debe usar **merge commit**
> para que semantic-release vea los commits originales `feat:` y `fix:` y calcule
> correctamente el bump de version.

1. Crear PR de `release/next` hacia `master`.
2. Mergear con **"Create a merge commit"** (NO "Squash and merge").
3. `semantic-release` genera la release estable (ej: `v3.2.0`).

## 4. Deploy (Manual)

El deploy se ejecuta manualmente via **GitHub Actions > Deploy** (`workflow_dispatch`):

| Parametro  | Descripcion                              |
|------------|------------------------------------------|
| `version`  | Version a desplegar (ej: `3.2.0-alpha.1`)|
| `instance` | `alpha`, `farmacia`, o `bodega`          |

Instancias y su entorno:

| Instancia   | Canal   | Entorno GitHub |
|-------------|---------|----------------|
| `alpha`     | Alpha   | alpha          |
| `farmacia`  | Beta    | beta           |
| `bodega`    | Stable  | production     |

El workflow descarga el JAR desde GitHub Releases, lo sube al servidor via SSH/SCP, y ejecuta el script de deploy.

## 5. Hotfix en Beta

Si hay un bug critico en beta:

1. Crear rama desde `release/next`:
   ```
   git checkout release/next && git pull
   git checkout -b fix/corregir-timeout-conexion
   ```
2. Crear PR hacia `release/next` con prefijo `fix:`.
3. Al mergear, se genera nueva release beta.

## Prefijos de Commits

| Prefijo  | Bump    | Ejemplo                                    |
|----------|---------|--------------------------------------------|
| `feat:`  | minor   | `feat: agregar modulo de inventario`       |
| `fix:`   | patch   | `fix: corregir validacion de RUC`          |
| `chore:` | ninguno | `chore: actualizar dependencias`           |
| `ci:`    | ninguno | `ci: agregar step de cache en workflow`     |
| `docs:`  | ninguno | `docs: documentar API de reportes`         |

## Proteccion de Ramas

- `master` y `develop`: `enforce_admins=true`, requieren PR, no push directo.
- Siempre usar PRs, incluso siendo administrador.
