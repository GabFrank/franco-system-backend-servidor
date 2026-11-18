# Plan #605: el precio y el código principal de una presentación con su propio id

Issue: GabFrank/franco-system-backend-servidor#605. Rama: `fix/precio-principal-sin-select-asterisco` (desde
`develop` d3ddcf7b). Pieza: **central** solamente.

## Problema

Dos consultas nativas hacen `select *` sobre un join con `presentacion`:

```sql
-- PrecioPorSucursalRepository.findPrincipalByPresentacionId
select * from productos.presentacion p
  left outer join productos.precio_por_sucursal c on c.presentacion_id = p.id
  where c.principal = true and p.id = ?1 limit 1
-- CodigoRepository.findPrincipalByPresentacionId: igual, con productos.codigo
```

El resultado trae dos columnas con el mismo nombre (`id`, `activo`, `principal`, `creado_en`, `usuario_id`) y
Hibernate mapea las de `presentacion`, que vienen primero. El `PrecioPorSucursal` (y el `Codigo`) que se devuelve
tiene **el id de la presentación**: `presentacion(id: 5) { precioPrincipal { id } }` → `5` en vez de `10` (alpha,
11/11/2026; también 4/9/154). El importe y el texto del código vienen bien (columnas que solo existen en esas tablas).

Quién lo sufre:
- **PWA (kiosco):** buscaba la moneda del precio por ese id y mostraba en guaraníes un precio en dólares. Mitigado del
  lado PWA (frc-mobile-pwa#142, PR #143: toma el principal de `precios` con el mismo importe).
- **Cualquier otro cliente** que use `precioPrincipal.id`/`activo` o `codigoPrincipal.id`/`activo`. Hoy: ninguno lee
  el id del código; el escritorio lee solo `precioPrincipal.precio`.
- El **filial** ya lo esquivó en `PresentacionResolver.precioPrincipal` (toma el principal de la lista, con un
  comentario sobre este mismo bug). Sus consultas de repositorio siguen con `select *`, pero solo las usan un reporte
  (importe) y `codigoPrincipal` (texto): fuera de alcance, anotado.

## Enfoque

`select c.*` en las dos consultas, con **el mismo join, el mismo `where` y el mismo `limit 1`**: devuelve la **misma
fila** que hoy (hay presentaciones con dos principales —67 en alpha, 55 con importes distintos— y no se quiere cambiar
cuál se elige), pero con sus propias columnas.

**Descartado:**
- Pasar a JPQL (`findFirstByPresentacionIdAndPrincipalTrue`) u ordenar por id: podría elegir otro principal en las
  presentaciones con duplicados y cambiar el importe que ven escritorio, PWA y reportes.
- Copiar el arreglo del filial (resolver que recorre la lista): más cambio, y el filial además aplica el precio
  especial, que el central no tiene.

## Fase única

- `PrecioPorSucursalRepository` y `CodigoRepository`: `select c.*`.
- **Test** `PrincipalDePresentacionQueriesIT` (patrón `ProductoStockPorSignoQueriesIT`: `SpringBootTest` sin web,
  Flyway apagado, `@Transactional` —lo sembrado se revierte y la réplica lógica no ve nada sin commit—, perfiles
  `dev,user-dev`, réplica apagada también por propiedad, se pide con `-Dit.principalDePresentacion=true`):
  - siembra por INSERT nativo con ids explícitos impares (`9996050xx`, no tocan secuencias): un producto, una
    presentación con `activo=false, principal=false`, un precio y un código principales con `activo=true,
    principal=true`, y un segundo precio no principal; `em.flush()` + `em.clear()`;
  - verifica que las dos consultas devuelven el **id, `activo` y `principal` del precio/código**, no de la
    presentación, y que el no principal no se elige. Falla con el código viejo (id y `activo` de la presentación).
  - Corre contra la **bodega local** (`localhost:5551`, la del `application-user-dev.properties`), no contra el alpha.

## Datos nuevos

Ninguno: sin migración, sin cambio de GraphQL.

## Contrato y canales (eje A)

Mismo tipo y mismos campos; solo pasan a tener el valor correcto. Clientes: escritorio (lee `.precio`), PWA (desde
#143 no depende del id; con este arreglo también acertaría con el viejo `precioEscogido`), impresión del central
(`ImpresionService` usa el importe y el texto). No toca el filial.

## Pruebas de runtime

Central de la rama por jar en el **8084** contra la **bodega local** (`localhost:5551/bodega`), perfil `dev`,
`spring.flyway.enabled=false` (no migra la base local), `--replication.sync.enabled=false
--replication.refresh.enabled=false` separados por espacio (memoria: un central local con la réplica prendida tocó
filiales reales). **No contra el alpha:** sus jobs (SIFEN, correo de facturas) correrían en paralelo con los del alpha.
1. Presentaciones de la base local con precio y código principales: `presentacion(id) { precioPrincipal { id precio }
   codigoPrincipal { id codigo } }` → el id coincide con el de `precio_por_sucursal`/`codigo`, no con el de la
   presentación.
2. **Mismo importe que hoy:** para presentaciones con dos principales, el `precio` que devuelve el jar es el mismo que da
   la consulta vieja (`select * … limit 1`) en psql contra la misma base.

## Resultado de las pruebas (11/11/2026)

**IT** `PrincipalDePresentacionQueriesIT` contra la bodega local: 2/2 en verde con `select c.*`; con el `select *`
viejo, 2/2 fallan con `expected: <999605003> but was: <999605001>` (el id de la presentación).

**Runtime:** jar de la rama contra la bodega local (perfil `dev`, Flyway apagado, réplica, SIFEN, correo, cotización,
acreditación POS, poller de retiros y Firebase apagados). Quedó en el **8081**, no en el 8084: el
`application-user-dev.properties` fija `server.port` con prioridad sobre la línea de comandos (solo pisa eso). Consulta
autenticada con la sesión de la PWA local (MR):

| Presentación | Antes (`select *`) | Ahora: precio | Ahora: código | Importe = consulta vieja |
|---|---|---|---|---|
| 4 | id 4 (presentación) | id 4, 2.000 | id 3 | sí |
| 68 | id 68 | id 366, 28.000 | id 437 | sí |
| 152 (principales 80.000 y 78.000) | id 152 | id 441, 80.000 | — | sí |
| 160 (156.000 y 155.000) | id 160 | id 450, 156.000 | — | sí |
| 193 (55.000 y 53.000) | id 193 | id 475, 55.000 | id 500 | sí |
| 229 (30.000 y 29.000) | id 229 | id 523, 30.000 | — | sí |

`activo`/`principal` llegan en `true` (los del precio/código). **Por pantalla:** buscador de la PWA, AMSTEL LAGER LATA:
el pack (193) muestra el código 7896045504848 y «₲ 55.000», el mismo importe de antes.

**Base local:** le faltaban migraciones `.5` de develop (V258.5, V259.5, V260.5, V267.5); la entidad ya mapea
`precio_por_sucursal.moneda_id` (V260.5) y la consulta no corría. Se agregó a mano solo esa columna con su FK `NOT
VALID` (sentencias idempotentes de V260.5, sin fila en el historial de Flyway: un central de develop la vuelve a aplicar
sin error).

## Auditoría del diff (paso 8)

Fijo 1 (permisos): sin cambios de resolvers ni roles. Fijo 2 (migraciones): N/A, no hay. Fijo 3 (operaciones/contrato):
mismo tipo, mismos campos, mismo join/where/limit; solo cambian las columnas que se leen. Condicionales: no toca tablas
replicadas ni canales. El IT no corre en CI (`@EnabledIfSystemProperty`), igual que sus vecinos.

## Auditoría (paso 5, 11/11/2026)

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | Sin `order by`, la fila elegida entre dos principales no está garantizada (antes tampoco) | Sin cambio de join/where/limit; la prueba 2 compara el importe con la consulta vieja |
| A2 | Llamadores: además `RemitoRetiroService`, `TicketRetiroService`, `DevolucionService`, `ProductoService` | Todos leen solo `.precio`/`.codigo`: sin efecto |
| A3 | Mapeo de Hibernate con `c.*` | Todas las columnas del entity están; `usuario_id` pasa a ser el real |
| A4 | Clientes | Escritorio lee `.precio`; PWA no compara el id desde #143 |
| B1 | Rollback | Nadie guarda ni reenvía esos ids: seguro |
| B2 | Sembrado del IT | INSERT nativo, ids explícitos `9996050xx`, `flush`/`clear`, rollback |
| B3 | El IT tiene que fallar con el viejo | Presentación con `activo`/`principal` en falso e ids distintos |
| B4 | Base del IT y del runtime | Bodega local, no alpha; réplica apagada explícita |

## Sin verificar

- Qué fila elige `limit 1` sin `order by` en otra base (bodega, farmacia): el arreglo no la cambia.
