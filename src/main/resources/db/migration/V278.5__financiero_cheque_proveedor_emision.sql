-- Cheques: proveedor, fecha de emision siempre completa, lote de emision y bandera del cheque al dia EMITIDO
-- (issue #601, desktop #708).
--
--   proveedor_id           a quien se le emitio (null = beneficiario libre o cheque anterior)
--   lote_id                id del primer cheque de una emision en cuotas (emitirCheques): un reenvio con la misma
--                          clave devuelve los mismos N aunque los numeros no sean consecutivos
--   fecha_entrega_backfill marca las filas cuya fecha de emision la puso esta migracion (= creado_en)
--
-- El tablero filtra por fecha de emision (coalesce(fecha_entrega, creado_en)): de ahi el indice.
--
-- La bandera cheque.al_dia_emitido va en configuracion_sistema y NO en configuracion_general: configuracion_general
-- esta en central_pub (se replica a las filiales) y una columna nueva sin su espejo cortaria esa replicacion.
-- Apagada = el cheque al dia se debita al emitirse, como siempre. Se prende a mano, por canal, con el central y el
-- desktop de esta version ya instalados; antes de volver a un central anterior, ver reconciliar-saldo-reservado.sql.
--
-- Aditiva. Central-only: financiero.cheque y configuracion_sistema no estan en ninguna publicacion.
-- Reversible: UPDATE financiero.cheque SET fecha_entrega = NULL WHERE fecha_entrega_backfill;

ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS proveedor_id bigint REFERENCES personas.proveedor(id);
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS lote_id bigint;
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS fecha_entrega_backfill boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS ix_cheque_proveedor ON financiero.cheque (proveedor_id) WHERE proveedor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_cheque_lote ON financiero.cheque (lote_id) WHERE lote_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_cheque_fecha_emision ON financiero.cheque ((coalesce(fecha_entrega, creado_en)));

COMMENT ON COLUMN financiero.cheque.proveedor_id IS 'Proveedor al que se emitio (V278.5, issue #601). Null = beneficiario libre.';
COMMENT ON COLUMN financiero.cheque.lote_id IS 'Id del primer cheque de su emision en cuotas (V278.5). Null = emitido solo.';
COMMENT ON COLUMN financiero.cheque.fecha_entrega_backfill IS 'fecha_entrega la completo V278.5 con creado_en.';

DO $$
DECLARE
    sin_fecha  integer;
    completadas integer;
BEGIN
    SELECT count(*) INTO sin_fecha FROM financiero.cheque WHERE fecha_entrega IS NULL AND creado_en IS NOT NULL;

    UPDATE financiero.cheque
       SET fecha_entrega = creado_en, fecha_entrega_backfill = true
     WHERE fecha_entrega IS NULL AND creado_en IS NOT NULL;
    GET DIAGNOSTICS completadas = ROW_COUNT;

    IF completadas <> sin_fecha THEN
        RAISE EXCEPTION 'V278.5: se esperaban % cheques sin fecha de emision y se completaron %', sin_fecha, completadas;
    END IF;
    RAISE NOTICE 'V278.5: % cheques con la fecha de emision completada desde creado_en', completadas;
END $$;

INSERT INTO empresarial.configuracion_sistema (clave, valor, descripcion, encriptado)
VALUES ('cheque.al_dia_emitido', 'false',
        'true = el cheque al dia queda EMITIDO (reserva el monto) hasta confirmar su cobro; false = se debita al '
        || 'emitirse, como siempre. Prender solo con central y desktop de la version 2026-11 (issue #601).',
        false)
ON CONFLICT (clave) DO NOTHING;
