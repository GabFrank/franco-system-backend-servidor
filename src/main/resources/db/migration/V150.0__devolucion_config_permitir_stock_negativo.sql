-- Modulo Devoluciones: preferencia para permitir separar/devolver con stock negativo.
-- Aditiva. Default TRUE = no bloquea por stock insuficiente al separar (conducta
-- pedida por negocio). Poner en FALSE recupera el bloqueo duro anterior.

ALTER TABLE operaciones.devolucion_configuracion
    ADD COLUMN IF NOT EXISTS permitir_stock_negativo BOOLEAN NOT NULL DEFAULT TRUE;
