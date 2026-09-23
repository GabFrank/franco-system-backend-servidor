-- Solicitante: el funcionario de la sucursal destino que pidio los productos.
-- No es ninguno de los usuarios del flujo (creacion, preparacion, transporte, recepcion):
-- esos son quienes operan la transferencia, este es quien la origino como pedido.
-- Nullable porque las transferencias ya existentes no lo tienen; la obligatoriedad se
-- aplica al avanzar de PRE_TRANSFERENCIA_CREACION, no a nivel de esquema.

ALTER TABLE operaciones.transferencia
    ADD COLUMN IF NOT EXISTS solicitante_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_transferencia_solicitante'
    ) THEN
        ALTER TABLE operaciones.transferencia
            ADD CONSTRAINT fk_transferencia_solicitante
            FOREIGN KEY (solicitante_id) REFERENCES personas.usuario(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_transferencia_solicitante
    ON operaciones.transferencia (solicitante_id);
