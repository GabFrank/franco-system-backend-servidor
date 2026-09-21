# PLAN — Buscador de compras: encontrar números de la descripción

Rama: `fix/compras-buscador-producto-numerico` en **central** y **desktop** (mismo nombre).
Este archivo es temporal: se borra en el PR final (ciclo §1 paso 11).

## Problema

En Gestión de Compras, el campo «Código de barra» de los ítems (y el diálogo «Buscar Producto»)
no encuentra un producto por un número que aparece en su **descripción**. La lista de productos
sí lo encuentra.

Caso real (bodega): producto **7108** «ARCOR HELADO MOGUL EXTREME TUBITO FRUTILLA ACIDA Y CREMA
50 GR **1014218**». Escribir `1014218` → «Sin resultados». Reproducido en local sobre `develop`.

## Causa (verificada)

Compras trata todo término de 3+ dígitos como código de barras y saltea la descripción, dos veces:

1. **Central** — `BuscadorProductoInteligenteService.agregarCoincidenciasTexto()` (línea 138):
   `if (!pareceTextoDescriptivo(texto) && pareceCodigoBarras(texto)) return;`. Sin letras, nunca
   llega a Lucene.
2. **Desktop** — `BuscadorComprasService.buscarProductoConFiltros()` (fallback cuando la búsqueda
   inteligente devuelve 0 o falla): manda `texto: esCodigo ? null : termino`, así que
   `searchProductoWithFilters` también ignora la descripción.

La lista de productos usa `ProductoService.findWithFilters()` con `texto`, que une coincidencias
de código + Lucene sin ese corte. Por eso ella sí encuentra el 7108.

## Descartado en el análisis (con evidencia)

- **«Tampoco busca por los últimos dígitos del código de barras»**: no se reproduce. Local
  (`0142186`, `42186`) y producción bodega (confirmado por el usuario el 2026-09-21: «al escribir
  0142186 filtra por el producto») encuentran el 7108. Bodega corre central `4.11.0`, que incluye
  `7ef9367b` (coincidencia parcial de código, 2026-07-26); la consulta de `CodigoRepository`
  corrida en solo lectura sobre la base de bodega devuelve `7108`. Nada que cambiar.

## Fases

### Fase 1 — central: la búsqueda inteligente también busca en la descripción

- `BuscadorProductoInteligenteService.agregarCoincidenciasTexto()`: quitar el corte. El texto
  corre siempre; el orden no cambia porque se agrega **después** de exacto → código parcial → id,
  y el `LinkedHashMap` deduplica por producto.
- Quitar `pareceTextoDescriptivo()` si queda sin uso.
- **Test nuevo** `BuscadorProductoInteligenteServiceTest` (Mockito, sin contexto Spring):
  1. `1014218` sin coincidencia de código → devuelve el producto que Lucene encuentra por
     descripción, con `tipoCoincidencia = TEXTO`. **Tiene que fallar con el código viejo**
     (se verifica revirtiendo el fix).
  2. Un código que coincide exacto y también aparece en una descripción de otro producto → el
     exacto queda **primero** (`CODIGO_EXACTO`), el de texto después.
  3. Mismo producto por código y por descripción → aparece una sola vez, como código.
  4. Con `productoSearchEnabled=false` (fallback SQL): `1014218` llama a
     `findIdsByDescripcionLike` y devuelve el producto. También falla con el código viejo.
  Setup: `@InjectMocks` + mocks de los 5 colaboradores; `productoSearchEnabled` es `@Value`
  sin setter → `ReflectionTestUtils.setField`.
  El «ruido» con un código inexistente **no** se testea acá: con Lucene mockeado el test no
  probaría nada. Queda en la prueba manual (caso 4) y como decisión aceptada (paridad con la
  lista de productos).
- Build: `./mvnw clean verify -B -DskipFlyway=true` leído del log.
- Commit `fix(productos): buscar numeros de la descripcion en el buscador de compras` + push.

### Fase 2 — desktop: el fallback de compras manda el texto como la lista de productos

- `BuscadorComprasService.buscarProductoConFiltros()`: enviar `texto: termino`, `codigo: null`
  siempre. Con `texto`, `findWithFilters` ya une código parcial (`codigoSearchService` sobre el
  mismo texto) + descripción: es exactamente lo que hace `list-producto`.
- `tipoCoincidencia` del mapeo: queda `TEXTO` (el valor es solo informativo en el diálogo).
- Gate: `npm run check` al final, leído del log.
- Commit `fix(compras): buscar numeros de la descripcion en el fallback del buscador` + push.

## Datos nuevos

Ninguno: no hay columna, campo GraphQL, enum ni clave nueva. Tabla escritor/lector: N/A porque no
nace ningún dato.

## Contrato y propagación (eje A)

- **GraphQL**: sin cambios de schema. `buscarProductoInteligente` y `searchProductoWithFilters`
  mantienen firma y tipos; solo cambia qué filas devuelve.
- **Consumidores** de `buscarProductoInteligente` / `productoProveedorBusquedaInteligente`: solo
  `desktop` compras (`buscador-compras.service.ts`). `git grep` en `frc-mobile` y
  `frc-mobile-pwa` (develop): 0 usos. El panel «productos del proveedor» usa el mismo
  `buscar()` (`ProductoProveedorService.java:88`): también va a encontrar números de la
  descripción (mismo beneficio, filtrado por proveedor igual que hoy).
- El diálogo `ComprasSearchProductoDialogComponent` también se abre desde `add-edit-item-dialog`,
  `edit-nota-recepcion-item-dialog` y `list-compra` (filtro por producto): los cuatro reciben el
  fix, sin cambios propios.
- Prueba post-merge: con desktop y central **del mismo canal**; si no, con un desktop viejo solo
  se ejercita el camino principal y no el fallback nuevo.
- **Filial**: N/A — no tiene estos resolvers ni se toca nada replicado.
- **Migración**: N/A — no hay cambio de esquema.
- **Orden de PRs**: independientes. El fix de central solo arregla el camino principal; el de
  desktop solo el fallback. Cualquiera puede llegar primero sin romper al otro.

## Reversibilidad (eje B)

- Sin migración ni datos tocados: el rollback es volver al JAR / versión de desktop anterior.
- Desktop viejo + central nuevo: el camino principal ya encuentra la descripción. Desktop nuevo +
  central viejo: el fallback (cuando el principal da 0) encuentra la descripción.

## Riesgos conocidos

- **Escaneo de un código que no existe**: hoy da «Sin resultados» y el usuario crea el producto
  con «+». Con el fix también corre Lucene; si el modo ESTRICTO no encuentra nada pasa al
  TOLERANTE (subsecuencia + fuzzy). Un código de 13 dígitos casi nunca es subsecuencia de una
  descripción, pero puede aparecer ruido. Es el mismo comportamiento que ya tiene la lista de
  productos. **A verificar en la prueba manual** con un código inexistente.
- **Costo por escaneo**: una consulta Lucene más por término numérico (índice en memoria/disco
  local, milisegundos). El diálogo «Buscar Producto» no tiene `debounce`
  (`compras-search-producto-dialog.component.ts:113-116`, solo `distinctUntilChanged` +
  `switchMap`): consulta por cada tecla, ya hoy. No se cambia en este fix; se mide en la prueba. La coincidencia exacta sigue autoseleccionando: `encontrarCoincidenciaExacta`
  exige igualdad y los resultados de texto no la cumplen.
- **Números cortos** (`500`, `250`): ahora traen productos con «500 GR», «250 ML», etc. además
  de los códigos que contengan esos dígitos. Es lo que hace la lista de productos.

## Prueba manual (local: central :8081 perfil dev + desktop `ng serve -c web`)

En Compras → pedido → pestaña Ítems → campo «Código de barra» + Enter:

1. `1014218` → aparece 7108 (antes: «Sin resultados»).
2. `0142186` y `42186` → sigue apareciendo 7108 (regresión del código parcial).
3. `7790580142186` (código completo) → abre directo el ítem del 7108, sin diálogo (regresión de la
   coincidencia exacta).
4. Un código inexistente de 13 dígitos → anotar qué muestra (riesgo 1).
5. Texto normal `mogul` → sin cambios.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | El panel del proveedor no pasa por `buscar()` | **Descartado**: `ProductoProveedorService.java:88` lo llama |
| A | `list-compra` y `edit-nota-recepcion-item-dialog` no usan el buscador | **Descartado**: abren `ComprasSearchProductoDialogComponent`, que sí. Se agregó al plan como afectados |
| A | Probar post-merge con desktop y central del mismo canal | Aceptado, en Contrato |
| B | Rollback / combos de versiones | Confirma el plan: sin riesgo |
| B | Fallback SQL (`productoSearchEnabled=false`) sin test | Aceptado: test 4 |
| B | Diálogo sin `debounce` | Aceptado: en Riesgos y en la prueba |
| B | Test automático del ruido con código inexistente | Parcial: con Lucene mockeado no prueba nada; queda en la prueba manual |

## Qué queda sin verificar

- Datos de producción con el fix (la base local es de 2021). Se verifica en alpha/bodega después
  del merge, con el mismo caso 7108.
- Tiempo de respuesta con el catálogo real de bodega.
