# Fotos de producto: un tamaño por tipo de pantalla

Issue [#263](https://github.com/GabFrank/franco-system-backend-servidor/issues/263).

La foto de un producto se sirve en tres tamaños. Cada pantalla pide el que dibuja: las
listas no bajan el original (364 KB de media, hasta 8,7 MB) y las vistas grandes no
estiran la miniatura.

## Campos

| Tipo | Campo | Qué devuelve | Para qué |
|---|---|---|---|
| `Producto`, `ProductoSaldoDto`, `ProductoVencidoView` | `imagenPrincipalMiniatura` | 250×250 (~9 KB) | íconos, avatares, cards, listas |
| `Producto`, `Presentacion` | `imagenPrincipalMediana` | lado mayor de hasta 800 px | previsualizaciones y fotos grandes |
| `Producto`, `ProductoSaldoDto`, `ProductoVencidoView` | `imagenPrincipal` | el original | compatibilidad con clientes ya instalados |
| `Presentacion` | `imagenPrincipal` | la miniatura, o un PNG gris si no hay foto | sin cambios |

Todos son `String` con un data URI (`data:image/jpg;base64,…`). Los campos nuevos y los
`imagenPrincipal` de producto, saldo y vencimiento devuelven **`null` cuando no hay
foto**. `Presentacion.imagenPrincipal` es el único que conserva el PNG gris de relleno.

**Un cliente nuevo no debería pedir `imagenPrincipal` de `Producto`**: es el original.

## Cómo se resuelve

Una sola regla, en `FotoProductoService`. Los cuatro resolvers delegan ahí.

```
miniatura: presentaciones/thumbnails/{id}.jpg -> mediana -> original
mediana:   presentaciones/medianas/{id}.jpg   -> original
original:  presentaciones/{id}.jpg -> archivo de imagen_master que exista -> null
```

- La foto de un **producto** es la de su presentación principal. Sin presentación
  principal, la que el producto tenga propia (`imagen_master` tipo `PRODUCTO`).
- La foto de un **vencimiento** es la de la presentación vencida y, si no tiene, la del
  producto.
- **La foto vigente es la del directorio de presentaciones**, que es donde escribe
  `saveImagenPresentacion`. `imagen_master` solo se consulta cuando ahí no hay archivo.
- **Leer no escribe.** Ningún campo de foto copia archivos ni inserta filas. Antes,
  `Producto.imagenPrincipal` migraba a `imagen_master` durante la lectura; eso dejó filas
  huérfanas, duplicadas y copias viejas, que ahora no afectan porque no se leen primero.

La API propia de `imagen_master` —`ImagenMasterService`, las queries y mutations de
`imagenMaster.graphqls` y los REST `/api/imagenes` y `/api/imagen-migration`— **se
borró**: ningún cliente la usó nunca, y era la que migraba al leer. De `imagen_master`
quedan la tabla, la entidad y el repositorio, solo para ese respaldo de lectura.

## La imagen mediana

Se guarda en `presentaciones/medianas/{id}.jpg` (JPEG, sin agrandar: una foto que ya mide
800 px o menos no genera archivo y se sirve el original).

- **Al subir una foto** (`saveImagenPresentacion`) se genera junto con la miniatura. Si la
  foto nueva es chica, se borra la mediana anterior.
- **Para las fotos ya subidas**, correr una vez después de desplegar:

  ```graphql
  mutation { generarImagenesMedianas { generadas salteadas fallidas } }
  ```

  Se puede repetir: saltea las que ya existen y regenera las que quedaron más viejas que
  su original. `fallidas` cuenta los archivos que no se pudieron leer como imagen.

Hasta correrla, las vistas grandes reciben el original: se ven bien, pero pesan.

## Filial

El filial expone `Producto.imagenPrincipalMiniatura`, `Producto.imagenPrincipalMediana` y
`Presentacion.imagenPrincipalMediana` con la misma regla sobre sus directorios locales
(sin `imagen_master`), porque el escritorio manda las mismas consultas a los dos backends.
**Se despliega antes que los clientes**: un cliente nuevo contra un backend sin estos
campos falla la consulta.
