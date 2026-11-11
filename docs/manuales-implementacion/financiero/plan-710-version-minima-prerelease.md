# Plan desktop#710 (lado central): la versión mínima del escritorio admite prerelease

Issue: GabFrank/frc-sistemas-integrados-angular#710. Rama `fix/version-minima-prerelease` (desde `develop` 785d1da6).
El plan completo, con el lado desktop, está en el repo del desktop:
`docs/manuales-implementacion/financiero/plan-710-version-minima-prerelease.md`.

## Qué cambia acá

`VersionClienteGraphQL.setVersionMinimaDesktop` acepta `X.Y.Z` con prerelease opcional (`4.2.0-alpha.345`), la guarda
en minúsculas y responde «La version tiene que tener la forma 4.12.0 o 4.12.0-alpha.345.» si no cumple. Hoy solo acepta
`X.Y.Z`, y el desktop compara con semver: en alpha la única mínima posible, `4.2.0`, bloquea a todas las alpha.

- Sin migración ni cambio de GraphQL (misma clave `cliente.desktop.version_minima`).
- Este PR va **antes** que el del desktop: un central viejo rechaza la prerelease.
- `ControlVersionMoneda.menor` (frente a `moneda.desktop.version-minima-requerida`, hoy sin configurar) partía por
  puntos y leía `4.2.0-alpha.345` como `4.2.0.345`: se corrige comparando solo la base (una alpha de 4.2.0 cuenta como
  4.2.0).
- Minúsculas con `Locale.ROOT`, largo ≤ 64.

## Test

`VersionClienteGraphQLTest` (unitario): prerelease aceptada y en minúsculas; `X.Y.Z` y vacío como hoy; formatos
inválidos rechazados. `ControlVersionMonedaTest`: `menor` con prerelease en los dos lados.
