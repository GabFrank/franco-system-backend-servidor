-- Renglón de la factura descartado a mano (no se carga en la compra). Solo aditiva.
ALTER TABLE operaciones.documento_recibido_item
    ADD COLUMN IF NOT EXISTS descartado boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS motivo_descarte text,
    ADD COLUMN IF NOT EXISTS descartado_por bigint REFERENCES personas.usuario (id),
    ADD COLUMN IF NOT EXISTS descartado_en timestamp;
