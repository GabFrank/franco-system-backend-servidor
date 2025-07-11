-- ===============================================
-- MIGRACIÓN FASE 1-4: NUEVAS ENTIDADES REFACTOR
-- ===============================================
-- Este script crea todas las nuevas entidades para el sistema refactorizado
-- de gestión de pedidos/compras con separación entre recepción documental 
-- y recepción física

-- ===============================================
-- 1. PROCESO ETAPA - Seguimiento granular de etapas
-- ===============================================

CREATE TYPE operaciones.proceso_etapa_tipo AS ENUM (
    'CREACION',
    'RECEPCION_NOTA', 
    'RECEPCION_MERCADERIA',
    'SOLICITUD_PAGO'
);

CREATE TYPE operaciones.proceso_etapa_estado AS ENUM (
    'PENDIENTE',
    'EN_PROGRESO',
    'COMPLETADA',
    'OMITIDA',
    'CANCELADA'
);

CREATE TABLE operaciones.proceso_etapa (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    tipo_etapa operaciones.proceso_etapa_tipo NOT NULL,
    estado_etapa operaciones.proceso_etapa_estado NOT NULL DEFAULT 'PENDIENTE',
    fecha_inicio TIMESTAMP,
    fecha_fin TIMESTAMP,
    usuario_inicio_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_proceso_etapa_pedido FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id),
    CONSTRAINT fk_proceso_etapa_usuario FOREIGN KEY (usuario_inicio_id) REFERENCES personas.usuario(id),
    CONSTRAINT uk_proceso_etapa_pedido_tipo UNIQUE (pedido_id, tipo_etapa)
);

CREATE INDEX idx_proceso_etapa_pedido ON operaciones.proceso_etapa(pedido_id);
CREATE INDEX idx_proceso_etapa_tipo ON operaciones.proceso_etapa(tipo_etapa);
CREATE INDEX idx_proceso_etapa_estado ON operaciones.proceso_etapa(estado_etapa);

-- ===============================================
-- 2. RECEPCION MERCADERIA - Recepción física real
-- ===============================================

CREATE TYPE operaciones.recepcion_mercaderia_estado AS ENUM (
    'PENDIENTE',
    'EN_PROCESO', 
    'FINALIZADA',
    'CANCELADA'
);

CREATE TABLE operaciones.recepcion_mercaderia (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT NOT NULL,
    sucursal_recepcion_id BIGINT NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    moneda_id BIGINT NOT NULL,
    cotizacion DECIMAL(10,4) DEFAULT 1.0,
    estado operaciones.recepcion_mercaderia_estado NOT NULL DEFAULT 'PENDIENTE',
    usuario_id BIGINT NOT NULL,
    
    CONSTRAINT fk_recepcion_mercaderia_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id),
    CONSTRAINT fk_recepcion_mercaderia_sucursal FOREIGN KEY (sucursal_recepcion_id) REFERENCES empresarial.sucursal(id),
    CONSTRAINT fk_recepcion_mercaderia_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id),
    CONSTRAINT fk_recepcion_mercaderia_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

CREATE INDEX idx_recepcion_mercaderia_proveedor ON operaciones.recepcion_mercaderia(proveedor_id);
CREATE INDEX idx_recepcion_mercaderia_sucursal ON operaciones.recepcion_mercaderia(sucursal_recepcion_id);
CREATE INDEX idx_recepcion_mercaderia_fecha ON operaciones.recepcion_mercaderia(fecha);
CREATE INDEX idx_recepcion_mercaderia_estado ON operaciones.recepcion_mercaderia(estado);

-- ===============================================
-- 3. RECEPCION MERCADERIA ITEM - Items de recepción física
-- ===============================================

CREATE TABLE operaciones.recepcion_mercaderia_item (
    id BIGSERIAL PRIMARY KEY,
    recepcion_mercaderia_id BIGINT NOT NULL,
    nota_recepcion_item_id BIGINT,
    producto_id BIGINT NOT NULL,
    sucursal_entrega_id BIGINT NOT NULL,
    cantidad_recibida DECIMAL(10,2) NOT NULL,
    vencimiento_recibido DATE,
    lote VARCHAR(100),
    es_bonificacion BOOLEAN DEFAULT FALSE,
    observaciones TEXT,
    
    CONSTRAINT fk_recepcion_item_recepcion FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id),
    CONSTRAINT fk_recepcion_item_nota_item FOREIGN KEY (nota_recepcion_item_id) REFERENCES operaciones.nota_recepcion_item(id),
    CONSTRAINT fk_recepcion_item_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id),
    CONSTRAINT fk_recepcion_item_sucursal FOREIGN KEY (sucursal_entrega_id) REFERENCES empresarial.sucursal(id)
);

CREATE INDEX idx_recepcion_item_recepcion ON operaciones.recepcion_mercaderia_item(recepcion_mercaderia_id);
CREATE INDEX idx_recepcion_item_producto ON operaciones.recepcion_mercaderia_item(producto_id);
CREATE INDEX idx_recepcion_item_sucursal ON operaciones.recepcion_mercaderia_item(sucursal_entrega_id);

-- ===============================================
-- 4. RECEPCION COSTO ADICIONAL - Costos adicionales (flete, impuestos)
-- ===============================================

CREATE TABLE operaciones.recepcion_costo_adicional (
    id BIGSERIAL PRIMARY KEY,
    recepcion_mercaderia_id BIGINT NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    monto DECIMAL(15,2) NOT NULL,
    moneda_id BIGINT NOT NULL,
    
    CONSTRAINT fk_costo_adicional_recepcion FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id),
    CONSTRAINT fk_costo_adicional_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id)
);

CREATE INDEX idx_costo_adicional_recepcion ON operaciones.recepcion_costo_adicional(recepcion_mercaderia_id);

-- ===============================================
-- 5. RECEPCION MERCADERIA NOTA - Relación M:N recepciones-notas
-- ===============================================

CREATE TABLE operaciones.recepcion_mercaderia_nota (
    id BIGSERIAL PRIMARY KEY,
    recepcion_mercaderia_id BIGINT NOT NULL,
    nota_recepcion_id BIGINT NOT NULL,
    
    CONSTRAINT fk_recepcion_nota_recepcion FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id),
    CONSTRAINT fk_recepcion_nota_nota FOREIGN KEY (nota_recepcion_id) REFERENCES operaciones.nota_recepcion(id),
    CONSTRAINT uk_recepcion_nota UNIQUE (recepcion_mercaderia_id, nota_recepcion_id)
);

CREATE INDEX idx_recepcion_nota_recepcion ON operaciones.recepcion_mercaderia_nota(recepcion_mercaderia_id);
CREATE INDEX idx_recepcion_nota_nota ON operaciones.recepcion_mercaderia_nota(nota_recepcion_id);

-- ===============================================
-- 6. DEVOLUCION - Devoluciones post-recepción
-- ===============================================

CREATE TYPE operaciones.devolucion_estado AS ENUM (
    'PENDIENTE',
    'CONFIRMADA',
    'CANCELADA'
);

CREATE TABLE operaciones.devolucion (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT NOT NULL,
    sucursal_origen_id BIGINT NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo TEXT,
    estado operaciones.devolucion_estado NOT NULL DEFAULT 'PENDIENTE',
    usuario_id BIGINT NOT NULL,
    
    CONSTRAINT fk_devolucion_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id),
    CONSTRAINT fk_devolucion_sucursal FOREIGN KEY (sucursal_origen_id) REFERENCES empresarial.sucursal(id),
    CONSTRAINT fk_devolucion_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

CREATE INDEX idx_devolucion_proveedor ON operaciones.devolucion(proveedor_id);
CREATE INDEX idx_devolucion_sucursal ON operaciones.devolucion(sucursal_origen_id);
CREATE INDEX idx_devolucion_fecha ON operaciones.devolucion(fecha);
CREATE INDEX idx_devolucion_estado ON operaciones.devolucion(estado);

-- ===============================================
-- 7. DEVOLUCION ITEM - Items específicos de devolución
-- ===============================================

CREATE TABLE operaciones.devolucion_item (
    id BIGSERIAL PRIMARY KEY,
    devolucion_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    recepcion_mercaderia_item_id BIGINT,
    cantidad DECIMAL(10,2) NOT NULL,
    motivo TEXT,
    lote VARCHAR(100),
    
    CONSTRAINT fk_devolucion_item_devolucion FOREIGN KEY (devolucion_id) REFERENCES operaciones.devolucion(id),
    CONSTRAINT fk_devolucion_item_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id),
    CONSTRAINT fk_devolucion_item_recepcion_item FOREIGN KEY (recepcion_mercaderia_item_id) REFERENCES operaciones.recepcion_mercaderia_item(id)
);

CREATE INDEX idx_devolucion_item_devolucion ON operaciones.devolucion_item(devolucion_id);
CREATE INDEX idx_devolucion_item_producto ON operaciones.devolucion_item(producto_id);

-- ===============================================
-- 8. PEDIDO ITEM DISTRIBUCION - Distribución planificada entre sucursales
-- ===============================================

CREATE TABLE operaciones.pedido_item_distribucion (
    id BIGSERIAL PRIMARY KEY,
    pedido_item_id BIGINT NOT NULL,
    sucursal_entrega_id BIGINT NOT NULL,
    cantidad_asignada DECIMAL(10,2) NOT NULL,
    
    CONSTRAINT fk_distribucion_pedido_item FOREIGN KEY (pedido_item_id) REFERENCES operaciones.pedido_item(id),
    CONSTRAINT fk_distribucion_sucursal FOREIGN KEY (sucursal_entrega_id) REFERENCES empresarial.sucursal(id),
    CONSTRAINT uk_distribucion_item_sucursal UNIQUE (pedido_item_id, sucursal_entrega_id)
);

CREATE INDEX idx_distribucion_pedido_item ON operaciones.pedido_item_distribucion(pedido_item_id);
CREATE INDEX idx_distribucion_sucursal ON operaciones.pedido_item_distribucion(sucursal_entrega_id);

-- ===============================================
-- 9. SOLICITUD PAGO RECEPCION - Relación M:N pagos-recepciones
-- ===============================================

CREATE TABLE operaciones.solicitud_pago_recepcion (
    id BIGSERIAL PRIMARY KEY,
    solicitud_pago_id BIGINT NOT NULL,
    recepcion_mercaderia_id BIGINT NOT NULL,
    monto_asignado DECIMAL(15,2),
    
    CONSTRAINT fk_pago_recepcion_solicitud FOREIGN KEY (solicitud_pago_id) REFERENCES operaciones.solicitud_pago(id),
    CONSTRAINT fk_pago_recepcion_recepcion FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id),
    CONSTRAINT uk_pago_recepcion UNIQUE (solicitud_pago_id, recepcion_mercaderia_id)
);

CREATE INDEX idx_pago_recepcion_solicitud ON operaciones.solicitud_pago_recepcion(solicitud_pago_id);
CREATE INDEX idx_pago_recepcion_recepcion ON operaciones.solicitud_pago_recepcion(recepcion_mercaderia_id);

-- ===============================================
-- 10. COMENTARIOS Y CONFIGURACIÓN ADICIONAL
-- ===============================================

-- Comentarios en tablas para documentación
COMMENT ON TABLE operaciones.proceso_etapa IS 'Seguimiento granular de etapas del proceso de pedidos/compras';
COMMENT ON TABLE operaciones.recepcion_mercaderia IS 'Recepción física real de mercadería (separada de notas documentales)';
COMMENT ON TABLE operaciones.recepcion_mercaderia_item IS 'Items específicos recibidos físicamente con trazabilidad';
COMMENT ON TABLE operaciones.recepcion_costo_adicional IS 'Costos adicionales como flete, impuestos, etc.';
COMMENT ON TABLE operaciones.devolucion IS 'Devoluciones post-recepción con validación de stock';
COMMENT ON TABLE operaciones.solicitud_pago_recepcion IS 'Relación flexible entre pagos y múltiples recepciones';

-- ===============================================
-- 11. DATOS INICIALES SI ES NECESARIO
-- ===============================================

-- Crear etapa inicial para pedidos existentes (si los hubiera)
-- Como el usuario confirmó que no hay datos previos, esto está comentado
-- INSERT INTO operaciones.proceso_etapa (pedido_id, tipo_etapa, estado_etapa)
-- SELECT id, 'CREACION', 'COMPLETADA' FROM operaciones.pedido WHERE estado IN ('ACTIVO', 'EN_RECEPCION_NOTA');

-- ===============================================
-- MIGRACIÓN COMPLETADA
-- ===============================================
-- Este script crea todas las estructuras necesarias para el sistema
-- refactorizado con separación clara de responsabilidades:
-- - Seguimiento granular de etapas
-- - Recepción física vs documental
-- - Devoluciones post-recepción  
-- - Pagos flexibles múltiples recepciones
-- - Distribución planificada entre sucursales
-- =============================================== 