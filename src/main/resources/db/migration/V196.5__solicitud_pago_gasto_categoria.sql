-- Gasto consolidado en SolicitudPago (sin PreGasto para gastos de tesorería):
-- la categoría del gasto (tipo_gasto) vive en la solicitud. Descripción = observaciones,
-- beneficiario = proveedor (opcional), vencimiento = fecha_pago_propuesta (ya existentes).
-- Aditiva y retrocompatible.

ALTER TABLE operaciones.solicitud_pago
    ADD COLUMN IF NOT EXISTS tipo_gasto_id bigint;

ALTER TABLE operaciones.solicitud_pago
    ADD CONSTRAINT solicitud_pago_tipo_gasto_fk
    FOREIGN KEY (tipo_gasto_id) REFERENCES financiero.tipo_gasto(id);
