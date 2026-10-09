# Plan: exigir rol en el central al cancelar una venta o una factura (issue #340)

Rama: `fix/ventas-control-de-rol-al-cancelar` (central y desktop, mismo nombre). Sale de `develop`.

## Problema

`VentaGraphQL.cancelarVenta` y `FacturaLegalGraphQL.cancelarFacturaLegal` no validan rol en el
central: el rol `CANCELACION DE VENTA` solo lo mira el desktop, y solo en el PDV
(`utilitarios-dialog`). Las pantallas del backoffice (lista de ventas, ventas de una caja, lista de
facturas) no miran ningun rol propio para la accion.

`cancelarVenta` alterna: una venta `CANCELADA` vuelve a `CONCLUIDA`. El chequeo cubre las dos
direcciones porque es la misma mutation.

## Decision (Franco, 2026-10-09)

Las dos mutations exigen `CANCELACION DE VENTA` o `ADMIN`. No hay rol aparte para el backoffice.

Medido en la base local (copia de bodega, usuarios activos): 26 con `CANCELACION DE VENTA`, 31 con
`ADMIN`, 36 con alguno. De los 29 que abren la lista de ventas del backoffice, 1 no tiene ninguno
de los dos; de los que tienen `ANALISIS DE CAJA`, ninguno.

## Fases

### Central · fase 1 — chequeo de rol en los dos resolvers

- `TesoreriaSecurityService`: constante `CANCELACION_DE_VENTA = "CANCELACION DE VENTA"` (el nombre
  del rol en `personas.role`, con espacios) y `requireCancelarVenta()` =
  `requireAnyRole(CANCELACION_DE_VENTA)`. `ADMIN` (rol o nickname) ya pasa por `hasAnyRole`.
- `VentaGraphQL.cancelarVenta`: `seg.requireCancelarVenta()` como primera linea.
- `FacturaLegalGraphQL.cancelarFacturaLegal`: `seg.requireCancelarVenta()` como primera linea,
  **antes** del `try`. Adentro el `catch (Exception)` lo convertiria en el string `"ERROR: ..."`,
  y tiene que salir como error de GraphQL igual que el resto de los chequeos de rol.
- El chequeo va en el resolver, no en `VentaService.cancelarVenta`: el servicio lo llaman tambien
  los dos caminos de `cancelarFacturaLegal`, que ya pasaron el chequeo.
- Sin migracion, sin cambio de `.graphqls`, sin variable de entorno. El rol ya existe (id 2).

Tests (`CancelarVentaYFacturaRolTest`), con el `TesoreriaSecurityService` **real** y un usuario en
el `SecurityContext` (patron de `CambioGraphQLControlDeRolTest`), para que un error en el nombre
del rol se vea:

1. sin el rol, `cancelarVenta` lanza `GraphQLException` y no toca `VentaService` (ni lee la venta);
2. sin el rol tampoco se reactiva una venta `CANCELADA` (es la misma mutation);
3. sin el rol, `cancelarFacturaLegal` lanza (no devuelve string) y no toca `FacturaLegalService`,
   `VentaService`, `SifenEventoService` ni `DocumentoElectronicoService`;
4. con el rol `CANCELACION DE VENTA` los dos pasan y llegan al servicio;
5. con el rol `ADMIN`, sin el de cancelacion, tambien.

Verificacion del test: revertir las dos lineas de los resolvers y comprobar que 1, 2 y 3 fallan.

### Desktop · fase 1 — esconder la accion y decir por que se rechazo (PR aparte)

- `list-venta` y `generic-list-venta`: el item «Cancelar / Habilitar» del menu solo se muestra con
  `CANCELACION DE VENTA` o `ADMIN`.
- `list-venta-credito`: idem para «Cancelar» (llama a la misma mutation).
- `list-factura-legal`: idem para «Cancelar factura».
- El permiso se calcula una vez en `ngOnInit` a una propiedad (no funcion en el HTML).
- En esas pantallas, un rechazo «No autorizado» del central se muestra como tal. Hoy el handler lo
  trata como «sin respuesta»: avisa que no se pudo confirmar y relee la venta.
- No cambia ninguna operacion GraphQL.

## Orden y compatibilidad

- Los dos PRs funcionan cada uno sin el otro, pero **conviene que el desktop llegue antes o junto
  con el central en cada canal**.
- Desktop viejo contra central nuevo, usuario sin el rol en el backoffice: la venta no se toca,
  pero el mensaje engaña. Sale «No autorizado» 3 s y encima «No se pudo confirmar si la venta se
  llego a cancelar», como si fuera un problema de red; en facturas, «Error al cancelar la factura».
  El PDV ya exige el rol, asi que ahi no cambia nada.
- Desktop nuevo contra central viejo: solo esconde un boton.
- PWA y mobile: declaran `cancelarVenta` pero ninguna pantalla la llama. N/A.
- Filial: su `cancelarVenta` ya rechaza siempre. N/A.

## Tabla de datos nuevos

Ninguno: no nace campo, columna ni clave.

## Rollback

Volver al JAR anterior deja las mutations como estaban. No hay estado que deshacer.

## Nota de despliegue

Antes de desplegar en cada instancia (bodega, farmacia), listar los usuarios activos que hoy abren
la lista de ventas (`ANALISIS DE CAJA` / `ANALISIS DE VENTA`) y no tienen ni `CANCELACION DE VENTA`
ni `ADMIN`: pierden la accion. Decidir con el negocio si se les da el rol.

El rol no lo crea ninguna migracion: es un dato. Confirmar que existe en `personas.role` de cada
instancia (alpha, farmacia, bodega) con ese nombre exacto.

## Auditoria del plan (paso 5, 2026-10-09)

| Eje | Hallazgo | Que se hizo |
|---|---|---|
| A | `list-venta-credito` tambien llama a `cancelarVenta` y el plan no la listaba | sumada a la fase del desktop |
| A | con desktop viejo, el rechazo se muestra como «no se pudo confirmar» | descrito en «Orden y compatibilidad»; el desktop pasa a mostrar el motivo |
| A | conviene desktop antes o junto con el central | anotado en «Orden y compatibilidad» |
| A | el rol es un dato, no una migracion; farmacia sin medir | anotado en «Nota de despliegue» |
| B | mockear el servicio de seguridad no detecta un nombre de rol mal escrito | los tests usan el servicio real |
| B | faltaban los casos de reactivacion y de `ADMIN` | agregados (2 y 5) |
| B | grafo de beans seguido: `TesoreriaSecurityService` no llega a los dos resolvers, sin ciclo | sin cambio; se confirma al arrancar el central local (el repo usa `lazy-initialization`, asi que tambien con la primera cancelacion) |

Los dos ejes no se contradicen.

## Sin verificar

- Los numeros de farmacia (solo se midio la copia local de bodega).
- Prueba de runtime: central local + desktop web con un usuario sin el rol (PRUEBANR 601) y con uno
  que lo tenga. Se hace antes del PR.
- Una sesion cuyo token lo emitio el filial (el PDV): el central la resuelve por el `nickname` del
  token. Se prueba en runtime cancelando desde el PDV.

## Fuera de alcance

`cancelarVentaTarjetaPorVentaId`, `cancelarVentaItens` y `cancelarVentaCredito` tienen el mismo
patron. Van en otro trabajo.
