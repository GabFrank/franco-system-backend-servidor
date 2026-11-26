-- Anulacion de cheques con motivo de una lista, observacion, fecha y usuario (desktop #807).
-- Aditiva y nullable: los cheques anulados antes quedan con NULL y motivo_anulacion (texto) se sigue escribiendo.
-- Central-only: financiero.cheque no esta en ninguna publicacion (V278.5), sin espejo en filial.
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS motivo_anulacion_tipo varchar(30);
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS observacion_anulacion text;
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS fecha_anulacion timestamp;
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS usuario_anulacion_id int8;

-- Los valores de MotivoAnulacionCheque: uno nuevo redefine este CHECK.
ALTER TABLE financiero.cheque DROP CONSTRAINT IF EXISTS cheque_motivo_anulacion_tipo_check;
ALTER TABLE financiero.cheque ADD CONSTRAINT cheque_motivo_anulacion_tipo_check CHECK (motivo_anulacion_tipo IN (
    'EFECTIVIZACION', 'FIRMA_NO_CONFORME', 'MALA_IMPRESION', 'DEVOLUCION',
    'PROVEEDOR_INCORRECTO', 'FECHA_INCORRECTA', 'MONTO_INCORRECTO', 'OTRO'));

ALTER TABLE financiero.cheque DROP CONSTRAINT IF EXISTS cheque_usuario_anulacion_fk;
ALTER TABLE financiero.cheque ADD CONSTRAINT cheque_usuario_anulacion_fk
    FOREIGN KEY (usuario_anulacion_id) REFERENCES personas.usuario (id) ON DELETE SET NULL;
