-- Opción B: SolicitudPago es la obligación de pago unificada (compra / gasto / rrhh).
-- Activa el enum TipoSolicitudPago (ya existente en Java) como columna, y permite
-- solicitudes sin proveedor (un gasto puede no tener beneficiario proveedor).
-- Aditiva y retrocompatible: las filas existentes quedan como COMPRA.

ALTER TABLE operaciones.solicitud_pago ADD COLUMN IF NOT EXISTS tipo varchar;
UPDATE operaciones.solicitud_pago SET tipo = 'COMPRA' WHERE tipo IS NULL;

ALTER TABLE operaciones.solicitud_pago ALTER COLUMN proveedor_id DROP NOT NULL;
