-- Create enum type for PagoDetalleCuotaEstado
CREATE TYPE operaciones.pago_detalle_cuota_estado AS ENUM ('PENDIENTE', 'PAGO_PARCIAL', 'PAGADO', 'CANCELADO');

-- Create table pago_detalle_cuota
CREATE TABLE operaciones.pago_detalle_cuota (
    id bigserial NOT NULL,
    pago_detalle_id int8 NOT NULL,
    referencia_id numeric NULL,
    numero_cuota numeric NOT NULL,
    fecha_vencimiento timestamp NULL,
    estado operaciones.pago_detalle_cuota_estado NOT NULL DEFAULT 'PENDIENTE',
    total_pagado numeric DEFAULT 0 NULL,
    total_final numeric NOT NULL,
    creado_en timestamp DEFAULT now() NULL,
    usuario_id int8 NULL,
    CONSTRAINT pago_detalle_cuota_pk PRIMARY KEY (id),
    CONSTRAINT pago_detalle_cuota_pago_detalle_fk FOREIGN KEY (pago_detalle_id) REFERENCES operaciones.pago_detalle(id) ON DELETE CASCADE,
    CONSTRAINT pago_detalle_cuota_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL
);

-- Modify cheque table to replace pago_detalle_id with pago_detalle_cuota_id
ALTER TABLE financiero.cheque 
    DROP CONSTRAINT IF EXISTS cheque_pago_detalle_fk,
    DROP COLUMN IF EXISTS pago_detalle_id,
    ADD COLUMN pago_detalle_cuota_id int8 NULL,
    ADD CONSTRAINT cheque_pago_detalle_cuota_fk FOREIGN KEY (pago_detalle_cuota_id) REFERENCES operaciones.pago_detalle_cuota(id) ON DELETE SET NULL; 
