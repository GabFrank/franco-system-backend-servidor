-- Campo de texto libre para los firmantes autorizados de la chequera.
-- Aditivo, nullable.
ALTER TABLE financiero.chequera ADD COLUMN IF NOT EXISTS firmantes text;
