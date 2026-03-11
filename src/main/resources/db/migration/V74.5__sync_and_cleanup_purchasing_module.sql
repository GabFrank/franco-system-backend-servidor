-- =================================================================
-- MIGRACIÓN V74: SINCRONIZACIÓN Y LIMPIEZA DEL MÓDULO DE COMPRAS
-- =================================================================
-- Este script alinea las tablas existentes (Pedido, PedidoItem) con el
-- modelo de datos refactorizado y elimina las tablas obsoletas.

-- =================================================================
-- 1. MODIFICACIONES EN LA TABLA 'pedido'
-- =================================================================

-- Eliminar columnas de seguimiento de etapas, ahora manejadas por 'proceso_etapa'
ALTER TABLE operaciones.pedido
    DROP COLUMN IF EXISTS usuario_creacion_id,
    DROP COLUMN IF EXISTS fecha_inicio_creacion,
    DROP COLUMN IF EXISTS fecha_fin_creacion,
    DROP COLUMN IF EXISTS progreso_creacion,
    DROP COLUMN IF EXISTS usuario_recepcion_nota_id,
    DROP COLUMN IF EXISTS fecha_inicio_recepcion_nota,
    DROP COLUMN IF EXISTS fecha_fin_recepcion_nota,
    DROP COLUMN IF EXISTS progreso_recepcion_nota,
    DROP COLUMN IF EXISTS usuario_recepcion_mercaderia_id,
    DROP COLUMN IF EXISTS fecha_inicio_recepcion_mercaderia,
    DROP COLUMN IF EXISTS fecha_fin_recepcion_mercaderia,
    DROP COLUMN IF EXISTS progreso_recepcion_mercaderia,
    DROP COLUMN IF EXISTS usuario_solicitud_pago_id,
    DROP COLUMN IF EXISTS fecha_inicio_solicitud_pago,
    DROP COLUMN IF EXISTS fecha_fin_solicitud_pago,
    DROP COLUMN IF EXISTS progreso_solicitud_pago;

-- Eliminar otras columnas obsoletas
ALTER TABLE operaciones.pedido
    DROP COLUMN IF EXISTS necesidad_id,
    DROP COLUMN IF EXISTS fecha_de_entrega,
    DROP COLUMN IF EXISTS descuento,
    DROP COLUMN IF EXISTS cantidad_notas,
    DROP COLUMN IF EXISTS cod_interno_proveedor,
    DROP COLUMN IF EXISTS sucursal_id;

-- =================================================================
-- 2. MODIFICACIONES EN LA TABLA 'pedido_item'
-- =================================================================

-- Renombrar columnas para reflejar que son de la solicitud original
ALTER TABLE operaciones.pedido_item
    RENAME COLUMN cantidad_creacion TO cantidad_solicitada;
ALTER TABLE operaciones.pedido_item
    RENAME COLUMN precio_unitario_creacion TO precio_unitario_solicitado;
ALTER TABLE operaciones.pedido_item
    RENAME COLUMN vencimiento_creacion TO vencimiento_esperado;

-- Eliminar todas las columnas relacionadas con etapas posteriores
ALTER TABLE operaciones.pedido_item
    DROP COLUMN IF EXISTS descuento_unitario_creacion,
    DROP COLUMN IF EXISTS bonificacion,
    DROP COLUMN IF EXISTS bonificacion_detalle,
    DROP COLUMN IF EXISTS frio,
    DROP COLUMN IF EXISTS nota_pedido_id,
    DROP COLUMN IF EXISTS sucursal_id,
    DROP COLUMN IF EXISTS precio_unitario_recepcion_nota,
    DROP COLUMN IF EXISTS descuento_unitario_recepcion_nota,
    DROP COLUMN IF EXISTS vencimiento_recepcion_nota,
    DROP COLUMN IF EXISTS presentacion_recepcion_nota_id,
    DROP COLUMN IF EXISTS cantidad_recepcion_nota,
    DROP COLUMN IF EXISTS precio_unitario_recepcion_producto,
    DROP COLUMN IF EXISTS descuento_unitario_recepcion_producto,
    DROP COLUMN IF EXISTS vencimiento_recepcion_producto,
    DROP COLUMN IF EXISTS presentacion_recepcion_producto_id,
    DROP COLUMN IF EXISTS cantidad_recepcion_producto,
    DROP COLUMN IF EXISTS obs_creacion,
    DROP COLUMN IF EXISTS obs_recepcion_nota,
    DROP COLUMN IF EXISTS obs_recepcion_producto,
    DROP COLUMN IF EXISTS autorizacion_recepcion_nota,
    DROP COLUMN IF EXISTS autorizacion_recepcion_producto,
    DROP COLUMN IF EXISTS autorizado_por_recepcion_nota_id,
    DROP COLUMN IF EXISTS autorizado_por_recepcion_producto_id,
    DROP COLUMN IF EXISTS usuario_recepcion_nota_id,
    DROP COLUMN IF EXISTS usuario_recepcion_producto_id,
    DROP COLUMN IF EXISTS motivo_modificacion_recepcion_nota,
    DROP COLUMN IF EXISTS motivo_modificacion_recepcion_producto,
    DROP COLUMN IF EXISTS cancelado,
    DROP COLUMN IF EXISTS verificado_recepcion_nota,
    DROP COLUMN IF EXISTS verificado_recepcion_producto,
    DROP COLUMN IF EXISTS motivo_rechazo_recepcion_nota,
    DROP COLUMN IF EXISTS motivo_rechazo_recepcion_producto,
    DROP COLUMN IF EXISTS nota_recepcion_id;

-- =================================================================
-- 3. ELIMINACIÓN DE TABLAS OBSOLETAS
-- =================================================================

-- Primero, eliminar la restricción de clave foránea en 'nota_recepcion' que apunta a la tabla obsoleta.
ALTER TABLE operaciones.nota_recepcion
    DROP CONSTRAINT IF EXISTS nota_recepcion_nota_recepcion_agrupada_fk;

-- Ahora, eliminar las tablas obsoletas.
DROP TABLE IF EXISTS operaciones.nota_recepcion_agrupada;
DROP TABLE IF EXISTS operaciones.pedido_item_sucursal;

COMMENT ON TABLE operaciones.pedido IS 'Refactorizado: Las columnas de seguimiento de etapas han sido movidas a operaciones.proceso_etapa.';
COMMENT ON TABLE operaciones.pedido_item IS 'Refactorizado: Los campos ahora solo representan la solicitud inicial. La lógica de recepción fue movida a nota_recepcion_item y recepcion_mercaderia_item.';

-- =================================================================
-- MIGRACIÓN COMPLETADA
-- ================================================================= 