# Especificación Técnica Detallada: Refactorización del Módulo de Compras (Backend)

Este documento es la guía técnica definitiva para la refactorización del backend. Detalla cada cambio a nivel de entidad y campo, sirviendo como la fuente de verdad para la implementación.

---

## 1. Entidades a Eliminar

Las siguientes entidades se vuelven obsoletas en el nuevo modelo y deben ser eliminadas por completo del proyecto (clase, repositorio y servicio).

-   **`NotaRecepcionAgrupada`**
    -   **Justificación:** Reemplazada por la entidad `RecepcionMercaderia`, que modela de forma más precisa un evento de recepción física de una o más notas.
-   **`PedidoItemSucursal`**
    -   **Justificación:** Su responsabilidad se divide y es reemplazada por `PedidoItemDistribucion` (para la planificación) y `RecepcionMercaderiaItem` (para la ejecución).

---

## 2. Entidades Existentes a Modificar

A continuación se detalla campo por campo los cambios en las entidades que se conservan.

### 2.1. `Pedido`
- **Propósito Refactorizado:** Simplificar para que solo contenga la información de cabecera del pedido. Toda la lógica de seguimiento de etapas se mueve a `ProcesoEtapa`.
- **Campos a ELIMINAR:**
    -   `usuarioCreacion`, `fechaInicioCreacion`, `fechaFinCreacion`, `progresoCreacion`: Reemplazado por un registro en `ProcesoEtapa`.
    -   `usuarioRecepcionNota`, `fechaInicioRecepcionNota`, `fechaFinRecepcionNota`, `progresoRecepcionNota`: Reemplazado por un registro en `ProcesoEtapa`.
    -   `usuarioRecepcionMercaderia`, `fechaInicioRecepcionMercaderia`, `fechaFinRecepcionMercaderia`, `progresoRecepcionMercaderia`: Reemplazado por un registro en `ProcesoEtapa`.
    -   `usuarioSolicitudPago`, `fechaInicioSolicitudPago`, `fechaFinSolicitudPago`, `progresoSolicitudPago`: Reemplazado por un registro en `ProcesoEtapa`.
    -   `necesidad`: Asumido fuera de alcance o manejado por otro módulo.

### 2.2. `PedidoItem`
- **Propósito Refactorizado:** Representar el ítem *originalmente solicitado*. Se convierte en una entidad prácticamente inmutable una vez creado el pedido.
- **Campos a ELIMINAR:**
    -   `notaRecepcion`: La relación ahora es `PedidoItem -> NotaRecepcionItem`.
    -   Todos los campos con sufijos `RecepcionNota` y `RecepcionProducto` (e.g., `precioUnitarioRecepcionNota`, `cantidadRecepcionProducto`, `motivoModificacionRecepcionNota`, etc.): Todas estas responsabilidades se mueven a `NotaRecepcionItem` y/o `RecepcionMercaderiaItem`.
    -   `cancelado`: El estado se maneja en entidades posteriores. Un ítem de pedido no se cancela, simplemente no se recibe.
- **Campos a AÑADIR:**
    -   `vencimiento_esperado` (`LocalDate`): Para registrar la fecha de vencimiento que se espera del proveedor.
    -   `observacion` (`String`): Campo de texto libre para notas durante la creación del pedido.

### 2.3. `NotaRecepcion`
- **Propósito Refactorizado:** Representar la cabecera de la factura/nota del proveedor.
- **Cambios:**
    -   **ELIMINAR** `notaRecepcionAgrupada`: Reemplazada por la relación `RecepcionMercaderia -> RecepcionMercaderiaNota`.
    -   **AÑADIR** `moneda` (`@ManyToOne Moneda`): Para registrar la moneda en la que se emitió la factura.
    -   **AÑADIR** `cotizacion` (`Double`): Tasa de cambio del día de la emisión de la nota.
    -   **MODIFICAR** `estado` (`Enum`): Utilizar el nuevo `NotaRecepcionEstado` con valores más descriptivos.

### 2.4. `SolicitudPago`
- **Propósito Refactorizado:** Permitir pagos flexibles que agrupen una o más recepciones.
- **Campos a ELIMINAR:**
    -   `tipo`: Ya no es necesario, el modelo es único.
    -   `referencia_id`: Reemplazado por una relación `M..N` con `RecepcionMercaderia` a través de una tabla de unión.

---

## 3. Nuevas Entidades a Crear

A continuación se define la estructura completa de cada nueva entidad.

### 3.1. `ProcesoEtapa`
- **Propósito:** Registra el avance, estado, fechas y usuarios para cada etapa principal de un pedido.
- **Tabla:** `proceso_etapa`
- **Campos:**
    -   `id`: `Long`, Clave primaria (`@Id @GeneratedValue`).
    -   `pedido`: `Pedido`, El pedido al que pertenece esta etapa (`@ManyToOne @JoinColumn(name="pedido_id")`).
    -   `tipoEtapa`: `ProcesoEtapaTipo` (`Enum`), ej: `CREACION`, `RECEPCION_NOTA` (`@Enumerated(EnumType.STRING)`).
    -   `estadoEtapa`: `ProcesoEtapaEstado` (`Enum`), ej: `PENDIENTE`, `EN_PROCESO` (`@Enumerated(EnumType.STRING)`).
    -   `fechaInicio`: `LocalDateTime`, Cuándo se inició la etapa.
    -   `fechaFin`: `LocalDateTime`, Cuándo se finalizó la etapa.
    -   `usuarioInicio`: `Usuario`, Usuario que inició la etapa (`@ManyToOne @JoinColumn(name="usuario_inicio_id")`).
    -   `creadoEn`: `LocalDateTime`, Timestamp de creación del registro.

### 3.2. `PedidoItemDistribucion`
- **Propósito:** Define la planificación de cómo se distribuye un `PedidoItem` entre sucursales.
- **Tabla:** `pedido_item_distribucion`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `pedidoItem`: `PedidoItem`, El ítem de pedido que se está distribuyendo (`@ManyToOne @JoinColumn(name="pedido_item_id")`).
    -   `sucursalEntrega`: `Sucursal`, La sucursal donde se debe entregar esta parte (`@ManyToOne @JoinColumn(name="sucursal_entrega_id")`).
    -   `cantidadAsignada`: `Double`, La cantidad destinada a esta sucursal.

### 3.3. `NotaRecepcionItem`
- **Propósito:** Modela un ítem de producto tal como figura en la nota del proveedor.
- **Tabla:** `nota_recepcion_item`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `notaRecepcion`: `NotaRecepcion`, La nota a la que pertenece este ítem (`@ManyToOne @JoinColumn(name="nota_recepcion_id")`).
    -   `pedidoItem`: `PedidoItem`, El ítem de pedido original (nullable si es un ítem no solicitado) (`@ManyToOne @JoinColumn(name="pedido_item_id")`).
    -   `producto`: `Producto`, El producto facturado (`@ManyToOne @JoinColumn(name="producto_id")`).
    -   `cantidadEnNota`: `Double`, Cantidad que figura en la nota.
    -   `precioUnitarioEnNota`: `Double`, Precio que figura en la nota.
    -   `esBonificacion`: `Boolean`, `true` si es un ítem bonificado (costo 0).
    -   `vencimientoEnNota`: `LocalDate`, Fecha de vencimiento indicada en la nota.
    -   `observacion`: `String`, Observaciones específicas de este ítem en la nota.

### 3.4. `RecepcionMercaderia`
- **Propósito:** Modela un evento único de recepción física de mercadería. Es el contenedor principal para el acto de recibir productos.
- **Tabla:** `recepcion_mercaderia`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `proveedor`: `Proveedor`, El proveedor que realiza la entrega (`@ManyToOne @JoinColumn(name="proveedor_id")`).
    -   `sucursalRecepcion`: `Sucursal`, La sucursal física donde se realiza la recepción (`@ManyToOne @JoinColumn(name="sucursal_recepcion_id")`).
    -   `fecha`: `LocalDateTime`, Fecha y hora del evento de recepción.
    -   `moneda`: `Moneda`, Moneda usada para los costos de esta recepción (`@ManyToOne @JoinColumn(name="moneda_id")`).
    -   `cotizacion`: `Double`, Tasa de cambio del día de la recepción.
    -   `estado`: `RecepcionMercaderiaEstado` (`Enum`), ej: `EN_PROCESO`, `FINALIZADA` (`@Enumerated(EnumType.STRING)`).
    -   `usuario`: `Usuario`, Usuario responsable de la recepción (`@ManyToOne @JoinColumn(name="usuario_id")`).

### 3.5. `RecepcionMercaderiaNota`
- **Propósito:** Tabla de unión para la relación `M..N` entre `RecepcionMercaderia` y `NotaRecepcion`.
- **Tabla:** `recepcion_mercaderia_nota`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `recepcionMercaderia`: `RecepcionMercaderia`, El evento de recepción (`@ManyToOne @JoinColumn(name="recepcion_mercaderia_id")`).
    -   `notaRecepcion`: `NotaRecepcion`, La nota que se está procesando en la recepción (`@ManyToOne @JoinColumn(name="nota_recepcion_id")`).

### 3.6. `RecepcionMercaderiaItem`
- **Propósito:** El corazón de la operación. Registra la cantidad de un producto que fue físicamente verificado y recibido o rechazado.
- **Tabla:** `recepcion_mercaderia_item`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `recepcionMercaderia`: `RecepcionMercaderia`, El evento de recepción al que pertenece (`@ManyToOne @JoinColumn(name="recepcion_mercaderia_id")`).
    -   `notaRecepcionItem`: `NotaRecepcionItem`, El ítem de la factura que se está recibiendo (`@ManyToOne @JoinColumn(name="nota_recepcion_item_id")`).
    -   `pedidoItemDistribucion`: `PedidoItemDistribucion`, El plan de entrega original (nullable si es un ítem no planificado) (`@ManyToOne @JoinColumn(name="pedido_item_distribucion_id")`).
    -   `producto`: `Producto`, El producto físico recibido (`@ManyToOne @JoinColumn(name="producto_id")`).
    -   `sucursalEntrega`: `Sucursal`, La sucursal final donde se stockeará el producto (`@ManyToOne @JoinColumn(name="sucursal_entrega_id")`).
    -   `cantidadRecibida`: `Double`, Cantidad físicamente contada y aceptada.
    -   `cantidadRechazada`: `Double`, Cantidad físicamente contada y rechazada.
    -   `esBonificacion`: `Boolean`, `true` si es bonificado (copiado de `NotaRecepcionItem`).
    -   `vencimientoRecibido`: `LocalDate`, La fecha de vencimiento real del producto recibido.
    -   `lote`: `String`, El número de lote del producto recibido.
    -   `motivoRechazo`: `String`, Justificación en caso de `cantidadRechazada > 0`.
    -   `observacion`: `String`, Notas adicionales sobre la recepción de este ítem específico.

### 3.7. `RecepcionCostoAdicional`
- **Propósito:** Registra costos adicionales (flete, impuestos, aduanas) que aplican a toda una recepción.
- **Tabla:** `recepcion_costo_adicional`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `recepcionMercaderia`: `RecepcionMercaderia`, La recepción a la que se le aplica este costo (`@ManyToOne @JoinColumn(name="recepcion_mercaderia_id")`).
    -   `descripcion`: `String`, Ej: "Flete Internacional", "Gastos de Despacho".
    -   `monto`: `Double`, El valor del costo adicional.
    -   `moneda`: `Moneda`, La moneda en la que se pagó el costo (`@ManyToOne @JoinColumn(name="moneda_id")`).

### 3.8. `Devolucion`
- **Propósito:** Modela la devolución de productos a un proveedor *después* de una recepción.
- **Tabla:** `devolucion`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `proveedor`: `Proveedor`, Proveedor al que se le devuelve (`@ManyToOne @JoinColumn(name="proveedor_id")`).
    -   `sucursalOrigen`: `Sucursal`, Sucursal desde donde sale la mercadería (`@ManyToOne @JoinColumn(name="sucursal_origen_id")`).
    -   `fecha`: `LocalDateTime`, Fecha y hora de la devolución.
    -   `motivo`: `String`, Motivo general de la devolución.
    -   `estado`: `DevolucionEstado` (`Enum`), ej: `EN_PROCESO`, `FINALIZADA` (`@Enumerated(EnumType.STRING)`).
    -   `usuario`: `Usuario`, Usuario que gestiona la devolución (`@ManyToOne @JoinColumn(name="usuario_id")`).

### 3.9. `DevolucionItem`
- **Propósito:** Detalla un producto específico que se está devolviendo.
- **Tabla:** `devolucion_item`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `devolucion`: `Devolucion`, La devolución a la que pertenece este ítem (`@ManyToOne @JoinColumn(name="devolucion_id")`).
    -   `recepcionMercaderiaItem`: `RecepcionMercaderiaItem`, Vínculo al ítem que se recibió originalmente para trazabilidad (`@ManyToOne @JoinColumn(name="recepcion_mercaderia_item_id")`).
    -   `producto`: `Producto`, El producto que se devuelve (`@ManyToOne @JoinColumn(name="producto_id")`).
    -   `cantidad`: `Double`, La cantidad que se devuelve.
    -   `lote`: `String`, El lote del producto que se devuelve, para trazabilidad de stock.

### 3.10. `SolicitudPagoRecepcion`
- **Propósito:** Tabla de unión para la relación `M..N` entre `SolicitudPago` y `RecepcionMercaderia`.
- **Tabla:** `solicitud_pago_recepcion`
- **Campos:**
    -   `id`: `Long`, Clave primaria.
    -   `solicitudPago`: `SolicitudPago`, La solicitud de pago (`@ManyToOne @JoinColumn(name="solicitud_pago_id")`).
    -   `recepcionMercaderia`: `RecepcionMercaderia`, La recepción que se incluye en el pago (`@ManyToOne @JoinColumn(name="recepcion_mercaderia_id")`).

---

## 4. Nuevos Enums a Crear

-   **`ProcesoEtapaTipo`**
    -   `CREACION`
    -   `RECEPCION_NOTA`
    -   `RECEPCION_MERCADERIA`
    -   `PAGO`
-   **`ProcesoEtapaEstado`**
    -   `PENDIENTE`
    -   `EN_PROCESO`
    -   `FINALIZADA`
    -   `OMITIDA`
-   **`NotaRecepcionEstado`**
    -   `PENDIENTE_CONCILIACION`
    -   `CONCILIADA`
    -   `EN_RECEPCION`
    -   `RECEPCION_PARCIAL`
    -   `RECEPCION_COMPLETA`
    -   `CERRADA`
-   **`RecepcionMercaderiaEstado`**
    -   `EN_PROCESO`
    -   `FINALIZADA`
    -   `CANCELADA`
-   **`DevolucionEstado`**
    -   `EN_PROCESO`
    -   `FINALIZADA`
    -   `CANCELADA` 