
# Propuesta de Refactorización del Módulo de Compras (Versión Final Definitiva)

## 1. Resumen del Enfoque

La estrategia principal es desacoplar las etapas del proceso de compra en entidades distintas y bien definidas. Esto permite simplificar cada entidad, mejorar la trazabilidad, clarificar la lógica de negocio y gestionar de forma robusta los estados, las excepciones y los casos complejos como bonificaciones, costos adicionales y devoluciones.

## 2. Diagrama de Entidades Propuesto (Versión Final)

```mermaid
graph TD
    subgraph "1. Planificación"
        P(Pedido) -->|contiene| PI(PedidoItem)
        PI -->|se distribuye en| PID(PedidoItemDistribucion)
    end

    subgraph "2. Conciliación"
        NR(NotaRecepcion) -->|lista| NRI(NotaRecepcionItem)
        PI -.->|se concilia con| NRI
    end

    subgraph "3. Realidad Física: Recepción"
        RM("Recepción Mercadería")
        RM -->|agrupa| RMN(RecepcionMercaderiaNota)
        RMN -->|apunta a| NR
        RM -->|detalla| RMI(RecepcionMercaderiaItem)
        RM -->|puede tener| RCA(RecepcionCostoAdicional)
        
        PID -.->|planifica (opcional)| RMI
        NRI -->|justifica| RMI
        
        RMI -- "Genera (+)" --> MS(MovimientoStock)
        RMI -- "Genera/Actualiza" --> CPP(CostoPorProducto)
    end

    subgraph "4. Realidad Física: Devolución"
        DEV(Devolucion) -->|detalla| DI(DevolucionItem)
        DI -- "Genera (-)" --> MS
    end
    
    subgraph "5. Proceso de Pago"
        SP(SolicitudPago)
        RM -->|se incluye en| SPR(SolicitudPagoRecepcion)
        SP -->|agrupa via| SPR
        DEV -.->|puede generar crédito para| SP
    end

    subgraph "Control Transversal"
        P -->|es gestionado por| PE(ProcesoEtapa)
    end

    %% Styling
    classDef default fill:#272727,stroke:#666,stroke-width:2px,color:#fff;
    classDef process fill:#384B5A,stroke:#666,stroke-width:2px,color:#fff;
    class P,PI,PID,NR,NRI,RM,RMN,RMI,RCA,SP,SPR,PE,MS,CPP,DEV,DI process
```

## 3. Detalle de Entidades (Versión Final)

---

### **Entidades de Creación de Pedido (Inmutables post-creación)**

#### 1. `Pedido`
- **Propósito:** Cabecera del pedido.
- **Atributos Clave:** `id`, `proveedor_id`, `moneda_id`, `estado`, `usuario_creacion_id`.

#### 2. `PedidoItem`
- **Propósito:** Ítem solicitado originalmente. **Inmutable**.
- **Atributos Clave:** `id`, `pedido_id`, `producto_id`, `cantidad_solicitada`, `precio_unitario_solicitado`, `vencimiento_esperado`, `observacion`.

#### 3. `PedidoItemDistribucion`
- **Propósito:** Planificación de la entrega de un `PedidoItem` a sucursales.
- **Atributos Clave:** `id`, `pedido_item_id`, `sucursal_entrega_id`, `cantidad_asignada`.

---

### **Entidad de Control**

#### 4. `ProcesoEtapa`
- **Propósito:** Control y auditoría de cada etapa del pedido.
- **Atributos Clave:** `id`, `pedido_id`, `tipo_etapa`, `estado_etapa`, `fecha_inicio`, `fecha_fin`, `usuario_inicio_id`.

---

### **Entidades de Recepción de Nota**

#### 5. `NotaRecepcion`
- **Propósito:** Cabecera de la nota fiscal/común del proveedor.
- **Atributos Clave:** `id`, `proveedor_id`, `pedido_id` (Opcional), `numero`, `timbrado`, `fecha`, `moneda_id`, `cotizacion`, `estado`.

#### 6. `NotaRecepcionItem`
- **Propósito:** Ítem tal como aparece en la nota del proveedor.
- **Atributos Clave:** `id`, `nota_recepcion_id`, `pedido_item_id` (Nullable), `producto_id`, `cantidad_en_nota`, `precio_unitario_en_nota`, `es_bonificacion` (boolean), `vencimiento_en_nota`, `observacion`.

---

### **Entidades de Recepción de Mercadería**

#### 7. `RecepcionMercaderia`
- **Propósito:** Evento único de recepción física de mercadería.
- **Atributos Clave:** `id`, `sucursal_recepcion_id`, `proveedor_id`, `fecha`, `moneda_id`, `cotizacion`, `estado`, `usuario_id`.

#### 8. `RecepcionMercaderiaNota` (Tabla de Unión)
- **Propósito:** Vincula las `NotaRecepcion` que se procesan en un evento de `RecepcionMercaderia`.
- **Atributos Clave:** `recepcion_mercaderia_id`, `nota_recepcion_id`.

#### 9. `RecepcionMercaderiaItem` (Corazón de la Operación)
- **Propósito:** Registra la cantidad de un producto físicamente verificado y recibido/rechazado.
- **Atributos Clave:** `id`, `recepcion_mercaderia_id`, `nota_recepcion_item_id` (FK), `pedido_item_distribucion_id` (Nullable), `producto_id`, `sucursal_entrega_id`, `cantidad_recibida`, `cantidad_rechazada`, `es_bonificacion`, `vencimiento_recibido`, `lote`, `motivo_rechazo`, `observacion`.
- **Lógica:** Dispara la creación de `MovimientoStock` y `CostoPorProducto`.

#### 10. `RecepcionCostoAdicional` (Nueva)
- **Propósito:** Registra costos adicionales (flete, impuestos) asociados a una recepción.
- **Atributos Clave:** `id`, `recepcion_mercaderia_id`, `descripcion`, `monto`, `moneda_id`.
- **Lógica:** El monto se prorratea entre los `RecepcionMercaderiaItem` para calcular el costo final real.

---

### **Entidades de Devolución Post-Recepción**

#### 11. `Devolucion` (Nueva)
- **Propósito:** Modela la devolución de productos a un proveedor después de una recepción.
- **Atributos Clave:** `id`, `proveedor_id`, `sucursal_origen_id`, `fecha`, `motivo`, `estado`, `usuario_id`.

#### 12. `DevolucionItem` (Nueva)
- **Propósito:** Detalla un producto específico que se está devolviendo.
- **Atributos Clave:** `id`, `devolucion_id`, `recepcion_mercaderia_item_id` (Para trazabilidad), `producto_id`, `cantidad`, `lote`.
- **Lógica:** Genera un `MovimientoStock` de salida y puede generar una nota de crédito.

---

### **Entidades de Pago**

#### 13. `SolicitudPago`
- **Propósito:** Solicitud formal de pago, que puede agrupar varias recepciones.
- **Atributos Clave:** `id`, `proveedor_id`, `monto_total`, `moneda_id`, `forma_pago_id`, `estado`.

#### 14. `SolicitudPagoRecepcion` (Nueva - Tabla de Unión)
- **Propósito:** Permite que una `SolicitudPago` incluya múltiples `RecepcionMercaderia`, haciendo la relación M..N.
- **Atributos Clave:** `solicitud_pago_id`, `recepcion_mercaderia_id`.

## 4. Flujo de Trabajo con la Estructura Final

El flujo principal se mantiene, pero ahora enriquecido:

1.  **Planificación:** Se crea el `Pedido` y sus `PedidoItem` / `PedidoItemDistribucion`.
2.  **Conciliación:** Se registran `NotaRecepcion` y sus `NotaRecepcionItem`, y se concilian con el pedido original. Se registran bonificaciones.
3.  **Recepción:** Se inicia una `RecepcionMercaderia`, se verifican los `RecepcionMercaderiaItem` (registrando lote, vencimiento, etc.), y se añaden los `RecepcionCostoAdicional`. Al finalizar, se actualiza el stock y los costos.
4.  **Devolución (Si ocurre):** Se crea una `Devolucion` con sus `DevolucionItem` para revertir el stock de productos específicos.
5.  **Pago:** Se crea una `SolicitudPago` y se le asocian una o más `RecepcionMercaderia` a través de la tabla `SolicitudPagoRecepcion`. El monto a pagar considerará las recepciones y las devoluciones (notas de crédito) asociadas.

Este diseño final es significativamente más robusto y está preparado para manejar las complejidades operativas de un sistema de compras real. 