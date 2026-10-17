# Plan: errores al guardar con constraint único (#434)

Rama: `fix/graphql-errores-al-guardar-con-constraint` (central). Sale de `origin/develop` @ `03743e38`.

## Problema

Siete resolvers `save*` atrapan la excepción de `service.save(e)` y miran si `ex.getMessage()`
contiene el nombre de un constraint único para traducirla a un mensaje de negocio.

| Resolver | Constraint | Qué hace con el resto |
|---|---|---|
| `personas/ProveedorGraphQL.saveProveedor` | `proveedor_un` | **se la traga**: devuelve el proveedor sin guardar (id null) |
| `financiero/CajaCategoriaObservacionGraphQL` | `caja_categoria_observacion_unique` | `GraphQLException("No se pudo guardar")` |
| `financiero/CajaSubCategoriaObservacionGraphQL` | `caja_subcategoria_observacion_unique` | idem |
| `financiero/CajaMotivoObservacionGraphQL` | `caja_motivo_observacion_unique` | idem |
| `operaciones/CategoriaObservacionGraphQL` | `categoria_observacion_unique` | idem |
| `operaciones/SubcategoriaObservacionGraphQL` | `subcategoria_observacion_unique` | idem |
| `operaciones/MotivoObservacionGraphQL` | `motivo_observacion_unique` | idem |

Los tres defectos:

1. **Proveedor**: cualquier error que no sea el duplicado devuelve la entidad sin guardar. El
   desktop muestra «Guardado con éxito» y después «Error al guardar el proveedor».
2. **Los siete**: el nombre del constraint suele estar en la causa raíz (`PSQLException`), no en
   el mensaje del wrapper de Spring. Si no está en el mensaje de afuera, el duplicado no se
   reconoce. Y con un mensaje null, `contains` tira `NullPointerException`.
3. **Los seis de observaciones**: el resto de los errores se reemplaza por un «No se pudo guardar»
   genérico y el motivo se pierde.

`ProveedorServicioGraphQL.saveProveedorServicio` ya lo hace bien: recorre las causas buscando el
constraint, se protege del null y relanza la excepción original.

## Cambio

- Nuevo `graphql/exceptions/ConstraintViolada.java` con un solo método estático
  `boolean en(Throwable ex, String constraint)`: recorre `ex` y sus causas buscando el nombre en
  el mensaje, con protección contra null.
- Los siete resolvers, y `ProveedorServicioGraphQL`, pasan a:
  ```java
  } catch (RuntimeException ex) {
      if (ConstraintViolada.en(ex, "proveedor_un")) throw new GraphQLException("Esta persona ya es un proveedor");
      throw ex;
  }
  ```
  El mensaje de negocio de cada uno no cambia. El resto de los errores se relanza tal cual: el
  `GraphqlExceptionHandler` lo devuelve como error GraphQL y el desktop lo muestra en su aviso
  «Ups! Algo salió mal en operacion: …».
- Se sacan los `err.printStackTrace()`: el error relanzado ya lo loguea graphql-java.

Sin migración, sin cambios en `.graphqls`, sin cambios en el desktop ni la PWA.

## Tabla de datos nuevos

N/A: no nace ningún campo, columna ni clave.

## Fases

Una sola fase (un commit `fix(graphql): …` + push):

- helper + 8 resolvers + tests.

## Tests (paso 7 y 9)

- `ConstraintVioladaTest`: lo encuentra en el mensaje de afuera, en una causa anidada, no lo
  encuentra si no está, y no rompe con mensajes null.
- `ProveedorGraphQLSaveTest` (Mockito, estilo `ConteoGraphQLSaveConteoTest`):
  - un error cualquiera del save **se relanza** (hoy devuelve la entidad sin guardar → falla con
    el código viejo);
  - el duplicado con el constraint solo en la causa da «Esta persona ya es un proveedor» (hoy
    falla con el código viejo: no lo reconoce);
  - el guardado ok devuelve lo que guardó el servicio.
- Un test parametrizado de los seis de observaciones: duplicado en la causa → mensaje de negocio,
  otro error → se relanza el original.
- Revertir el fix y comprobar que los tests de proveedor y observaciones fallan.
- Batería: `./mvnw clean verify -B -DskipFlyway=true`.

## Prueba por pantalla (paso 9)

Central local 8081 (perfil `dev`) + desktop `ng serve -c web` en un puerto propio, usuario MR:

1. Proveedor: alta con una persona que ya es proveedor → «Esta persona ya es un proveedor»; alta
   normal → guarda; edición normal → guarda; un error forzado que no sea el duplicado → el aviso
   muestra el motivo y no sale «Guardado con éxito».
2. Las seis pantallas de observaciones (caja: categoría, subcategoría, motivo; operaciones:
   categoría, subcategoría, motivo): guardar un duplicado → mensaje «Ya existe…»; guardar uno
   nuevo → guarda; un error que no sea el duplicado → el motivo real.
3. Restaurar los datos de prueba por SQL.

El error que no es duplicado se fuerza en la base local (por ejemplo, un `CHECK` temporal sobre
la tabla que se borra al terminar). Si alguna pantalla no se encuentra en el menú, se busca antes
de reportarla.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | `contains` da falsos positivos: `categoria_observacion_unique` está dentro de `caja_categoria_observacion_unique` y de `subcategoria_observacion_unique`. Hoy no muerde (ninguna de estas entidades tiene cascadas), pero un cascade futuro reportaría el duplicado con el mensaje de otra | El helper busca el nombre como palabra entera (sin letra, dígito ni `_` a los costados). Caso en el test |
| A | Un ciclo de causas haría que el recorrido no termine | Tope de profundidad en el helper |
| A | Desktop: proveedor tiene handler de `error`; los seis diálogos de observaciones no, quedan abiertos con el aviso (igual que hoy con «No se pudo guardar»). PWA no usa estas mutations; `frc-mobile` declara `saveProveedor` pero nadie lo llama. Filial ya relanza y no tiene las observaciones | Sin cambios |
| A / B | En un error que no es el duplicado, el usuario ve el texto técnico del servidor (excepción de Spring/Hibernate) en vez de «No se pudo guardar». Es lo que pide la issue (el motivo real) y ya pasa en `ProveedorServicioGraphQL` | Aceptado. Endurecer el handler para todos los resolvers queda fuera de este fix |
| B | Ninguna excepción del save llega con el dato persistido: los services solo setean `creadoEn` y llaman a `super.save`, sin listeners ni lógica post-commit. Relanzar no genera duplicados por reintento | Sin cambios |
| B | Con la falla en el insert, Spring ya pone `constraint [x]` en el mensaje de afuera y el código viejo lo reconocía. El caso que falla es el flush en el commit (`TransactionSystemException` → `RollbackException` → Hibernate → `PSQLException`) | Los tests usan esa cadena. La prueba por pantalla del duplicado no demuestra el recorrido de causas: lo demuestra el test |
| B | El test de relanzamiento tiene que usar `assertSame`; el parametrizado debe incluir el constraint y el mensaje de cada resolver | Así se escriben |
| B | `ProveedorGraphQL` con `personaId` inexistente falla antes del `try` (preexistente, ya llegaba como error) | Sin cambios |

## Qué queda sin verificar

- Grep en desktop, PWA y mobile: ningún cliente depende de «No se pudo guardar» ni de los
  mensajes «Ya existe…» (hecho en el paso 5).
