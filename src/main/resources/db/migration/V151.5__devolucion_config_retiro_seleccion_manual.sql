-- Modulo Devoluciones: preferencia para permitir verificar cajas en el retiro
-- manualmente (sin escanear el QR). Aditiva. Default TRUE = liberar (permite
-- seleccion manual). Poner en FALSE = bloquear (solo por escaneo).

ALTER TABLE operaciones.devolucion_configuracion
    ADD COLUMN IF NOT EXISTS retiro_permitir_seleccion_manual BOOLEAN NOT NULL DEFAULT TRUE;
