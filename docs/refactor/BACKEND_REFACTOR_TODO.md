# Lista de Tareas para la Refactorización del Módulo de Compras (Backend)

Este documento detalla las acciones necesarias para implementar la nueva arquitectura del módulo de compras en el backend. Las tareas están organizadas en fases para una ejecución ordenada.

---

### Fase 1: Creación de Nuevas Entidades y Repositorios

El primer paso es construir la base del nuevo modelo creando todas las nuevas clases de dominio y sus correspondientes interfaces de repositorio.

-   [ ] **1.1. Entidad `ProcesoEtapa`:**
    -   Crear `ProcesoEtapa.java` con sus atributos (`pedido`, `tipo_etapa`, `estado_etapa`, fechas, usuarios).
    -   Crear la interfaz `ProcesoEtapaRepository.java` extendiendo `JpaRepository`.

-   [ ] **1.2. Entidad `PedidoItemDistribucion`:**
    -   Crear `PedidoItemDistribucion.java`.
    -   Crear `PedidoItemDistribucionRepository.java`.

-   [ ] **1.3. Entidades de Recepción de Mercadería:**
    -   Crear `RecepcionMercaderia.java`.
    -   Crear `RecepcionMercaderiaItem.java`.
    -   Crear `RecepcionMercaderiaNota.java` (tabla de unión).
    -   Crear `RecepcionCostoAdicional.java`.
    -   Crear los repositorios para cada una: `RecepcionMercaderiaRepository`, `RecepcionMercaderiaItemRepository`, etc.

-   [ ] **1.4. Entidades de Devolución:**
    -   Crear `Devolucion.java`.
    -   Crear `DevolucionItem.java`.
    -   Crear `DevolucionRepository.java` y `DevolucionItemRepository.java`.

-   [ ] **1.5. Entidades de Pago Flexible:**
    -   Crear `SolicitudPagoRecepcion.java` (tabla de unión).
    -   Crear `SolicitudPagoRecepcionRepository.java`.

-   [ ] **1.6. Enums:**
    -   Definir todos los nuevos enums para los estados de las entidades (`ProcesoEtapaTipo`, `ProcesoEtapaEstado`, `RecepcionMercaderiaEstado`, etc.).

---

### Fase 2: Refactorización de Entidades Existentes

Modificar las entidades actuales para alinearlas con la nueva estructura, eliminando responsabilidades y campos obsoletos.

-   [ ] **2.1. Refactorizar `Pedido.java`:**
    -   Eliminar **todos** los campos de seguimiento de etapas (e.g., `usuario_creacion_id`, `fecha_inicio_creacion`, `progreso_creacion`, y todos los equivalentes para `recepcion_nota`, `recepcion_mercaderia`, y `solicitud_pago`).
    -   Asegurar que las relaciones clave (`proveedor`, `moneda`) se mantienen.

-   [ ] **2.2. Refactorizar `PedidoItem.java`:**
    -   Eliminar todos los campos relacionados con etapas posteriores a la creación (e.g., `nota_recepcion_id`, `precioUnitarioRecepcionNota`, `cantidadRecepcionNota`, `motivoModificacionRecepcionNota`, etc.).
    -   Añadir los nuevos campos: `vencimiento_esperado` y `observacion`.

-   [ ] **2.3. Refactorizar `NotaRecepcion.java`:**
    -   Añadir `moneda_id` y `cotizacion`.
    -   Asegurar que el `estado` (enum) se actualice a la nueva versión más descriptiva.
    -   Eliminar la relación con `NotaRecepcionAgrupada`.

-   [ ] **2.4. Refactorizar `NotaRecepcionItem.java` (si existe, o crearla):**
    -   Asegurar que contiene los campos de la propuesta final: `pedido_item_id` (nullable), `es_bonificacion`, `vencimiento_en_nota`, `observacion`.

-   [ ] **2.5. Refactorizar `SolicitudPago.java`:**
    -   Eliminar `referencia_id` y `tipo`.
    -   Añadir la relación `M..N` con `RecepcionMercaderia` a través de la nueva tabla `SolicitudPagoRecepcion`.

---

### Fase 3: Implementación de la Lógica de Negocio (Services)

Crear o modificar las clases de servicio para implementar los nuevos flujos de trabajo.

-   [ ] **3.1. Servicio de Pedido:**
    -   Modificar `PedidoService.finalizarCreacion()` para que, en lugar de cambiar el estado del pedido, cree el primer `ProcesoEtapa` (`CREACION`, `FINALIZADA`) y el siguiente (`RECEPCION_NOTA`, `PENDIENTE`).

-   [ ] **3.2. Servicio de Recepción de Mercadería (`RecepcionMercaderiaService`):**
    -   **Crear `finalizarRecepcion(Long recepcionId)`:** Esta es la función más crítica.
        -   Debe iterar sobre todos los `RecepcionMercaderiaItem` de la recepción.
        -   Para cada ítem:
            -   Generar el `MovimientoStock` correspondiente (entrada positiva).
            -   Calcular el costo final (considerando el precio en nota, costos adicionales prorrateados y la cotización).
            -   Actualizar o crear el `CostoPorProducto`.
            -   Omitir ítems bonificados del cálculo de costo promedio.
        -   Cambiar el estado de `RecepcionMercaderia` a `FINALIZADA`.
        -   Actualizar el `ProcesoEtapa` correspondiente del pedido a `FINALIZADA`.

-   [ ] **3.3. Servicio de Devolución (`DevolucionService`):**
    -   **Crear `confirmarDevolucion(Long devolucionId)`:**
        -   Debe iterar sobre los `DevolucionItem`.
        -   Generar el `MovimientoStock` de salida (negativo).
        -   Ajustar las cuentas por pagar o generar una nota de crédito.

-   [ ] **3.4. Servicio de Pago (`SolicitudPagoService`):**
    -   Modificar el método de creación para que acepte una lista de `recepcion_mercaderia_id` y las vincule a través de `SolicitudPagoRecepcion`.
    -   Implementar la lógica para calcular el `monto_total` basado en las recepciones asociadas, menos las devoluciones si aplica.

---

### Fase 4: Adaptación de la API (GraphQL)

Exponer la nueva estructura y lógica a través de la API.

-   [ ] **4.1. Actualizar `pedido.graphqls` (o archivo correspondiente):**
    -   Modificar los tipos `Pedido` y `PedidoItem` para reflejar su nueva estructura simplificada.
    -   Añadir los nuevos tipos: `ProcesoEtapa`, `RecepcionMercaderia`, `RecepcionMercaderiaItem`, `Devolucion`, etc.
    -   Añadir queries para obtener los nuevos objetos (`recepcionById`, `devolucionesPorProveedor`, etc.).
    -   Crear/modificar mutations para los nuevos flujos: `finalizarRecepcion`, `crearDevolucion`, `crearSolicitudPagoConsolidada`.

---

### Fase 5: Migración de Base de Datos

Planificar y ejecutar la migración de datos para no perder información histórica. **Esta fase es crítica y debe ser probada exhaustivamente en un entorno de staging.**

-   [ ] **5.1. Script de Alteración de Esquema:**
    -   Escribir un script (Flyway/Liquibase) que:
        -   Cree las nuevas tablas (`proceso_etapa`, `recepcion_mercaderia`, etc.).
        -   Añada las nuevas columnas a las tablas existentes (`cotizacion` en `nota_recepcion`).
        -   **No eliminar las columnas antiguas todavía.** Mantenerlas temporalmente para la migración de datos.

-   [ ] **5.2. Script de Migración de Datos:**
    -   Escribir un script SQL o una rutina Java que lea los datos de las columnas antiguas y los inserte en las nuevas tablas.
    -   **Ejemplo:** Para cada `Pedido` en la base de datos, leer sus campos `fecha_inicio_creacion`, `usuario_creacion_id`, etc., y crear un registro correspondiente en la tabla `proceso_etapa`. Repetir para todas las etapas.

-   [ ] **5.3. Script de Limpieza de Esquema:**
    -   Una vez que los datos han sido migrados y validados, crear un segundo script para eliminar las columnas antiguas de `pedido` y `pedido_item`.

---

### Fase 6: Limpieza de Código Obsoleto

Eliminar las clases que ya no son necesarias.

-   [ ] **6.1. Eliminar Entidades:** Borrar `NotaRecepcionAgrupada.java` y `PedidoItemSucursal.java`.
-   [ ] **6.2. Eliminar Repositorios y Servicios:** Borrar los componentes asociados a las entidades eliminadas.
-   [ ] **6.3. Revisar y eliminar lógica de negocio antigua** que ahora es manejada por los nuevos servicios. 