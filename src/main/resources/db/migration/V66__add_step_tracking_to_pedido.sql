-- Migration V66: Add step tracking columns to pedido table
-- Following the same pattern as pedido_item with step-specific columns

-- Step: Creacion (Datos del pedido)
ALTER TABLE operaciones.pedido ADD COLUMN usuario_creacion_id int8 NULL;
ALTER TABLE operaciones.pedido ADD COLUMN fecha_inicio_creacion timestamp NULL;
ALTER TABLE operaciones.pedido ADD COLUMN fecha_fin_creacion timestamp NULL;
ALTER TABLE operaciones.pedido ADD COLUMN progreso_creacion int4 DEFAULT 0;

-- Step: Recepcion Nota
ALTER TABLE operaciones.pedido ADD COLUMN usuario_recepcion_nota_id int8 NULL;
ALTER TABLE operaciones.pedido ADD COLUMN fecha_inicio_recepcion_nota timestamp NULL;
ALTER TABLE operaciones.pedido ADD COLUMN fecha_fin_recepcion_nota timestamp NULL;
ALTER TABLE operaciones.pedido ADD COLUMN progreso_recepcion_nota int4 DEFAULT 0;

-- Step: Recepcion Mercaderia
ALTER TABLE operaciones.pedido ADD COLUMN usuario_recepcion_mercaderia_id int8 NULL;
ALTER TABLE operaciones.pedido ADD COLUMN fecha_inicio_recepcion_mercaderia timestamp NULL;
ALTER TABLE operaciones.pedido ADD COLUMN fecha_fin_recepcion_mercaderia timestamp NULL;
ALTER TABLE operaciones.pedido ADD COLUMN progreso_recepcion_mercaderia int4 DEFAULT 0;

-- Step: Solicitud Pago
ALTER TABLE operaciones.pedido ADD COLUMN usuario_solicitud_pago_id int8 NULL;
ALTER TABLE operaciones.pedido ADD COLUMN fecha_inicio_solicitud_pago timestamp NULL;
ALTER TABLE operaciones.pedido ADD COLUMN fecha_fin_solicitud_pago timestamp NULL;
ALTER TABLE operaciones.pedido ADD COLUMN progreso_solicitud_pago int4 DEFAULT 0;

-- Add foreign key constraints
ALTER TABLE operaciones.pedido ADD CONSTRAINT pedido_usuario_creacion_fk 
    FOREIGN KEY (usuario_creacion_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE operaciones.pedido ADD CONSTRAINT pedido_usuario_recepcion_nota_fk 
    FOREIGN KEY (usuario_recepcion_nota_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE operaciones.pedido ADD CONSTRAINT pedido_usuario_recepcion_mercaderia_fk 
    FOREIGN KEY (usuario_recepcion_mercaderia_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE operaciones.pedido ADD CONSTRAINT pedido_usuario_solicitud_pago_fk 
    FOREIGN KEY (usuario_solicitud_pago_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- Add comments for documentation
COMMENT ON COLUMN operaciones.pedido.usuario_creacion_id IS 'Usuario responsible for datos del pedido step';
COMMENT ON COLUMN operaciones.pedido.fecha_inicio_creacion IS 'When user started working on datos del pedido step';
COMMENT ON COLUMN operaciones.pedido.fecha_fin_creacion IS 'When datos del pedido step was completed';
COMMENT ON COLUMN operaciones.pedido.progreso_creacion IS 'Progress percentage (0-100) for datos del pedido step';

COMMENT ON COLUMN operaciones.pedido.usuario_recepcion_nota_id IS 'Usuario responsible for recepcion nota step';
COMMENT ON COLUMN operaciones.pedido.fecha_inicio_recepcion_nota IS 'When user started working on recepcion nota step';
COMMENT ON COLUMN operaciones.pedido.fecha_fin_recepcion_nota IS 'When recepcion nota step was completed';
COMMENT ON COLUMN operaciones.pedido.progreso_recepcion_nota IS 'Progress percentage (0-100) for recepcion nota step';

COMMENT ON COLUMN operaciones.pedido.usuario_recepcion_mercaderia_id IS 'Usuario responsible for recepcion mercaderia step';
COMMENT ON COLUMN operaciones.pedido.fecha_inicio_recepcion_mercaderia IS 'When user started working on recepcion mercaderia step';
COMMENT ON COLUMN operaciones.pedido.fecha_fin_recepcion_mercaderia IS 'When recepcion mercaderia step was completed';
COMMENT ON COLUMN operaciones.pedido.progreso_recepcion_mercaderia IS 'Progress percentage (0-100) for recepcion mercaderia step';

COMMENT ON COLUMN operaciones.pedido.usuario_solicitud_pago_id IS 'Usuario responsible for solicitud pago step';
COMMENT ON COLUMN operaciones.pedido.fecha_inicio_solicitud_pago IS 'When user started working on solicitud pago step';
COMMENT ON COLUMN operaciones.pedido.fecha_fin_solicitud_pago IS 'When solicitud pago step was completed';
COMMENT ON COLUMN operaciones.pedido.progreso_solicitud_pago IS 'Progress percentage (0-100) for solicitud pago step'; 