-- Retira el tipo CAJA_CHICA de las cajas virtuales (issue #220).
--
-- El backend nunca le dio semantica: todo el motor de dinero trabaja por id de caja. La unica
-- diferencia vivia en el desktop (los dialogos de RRHH ocultaban las cajas chicas y limite_gs era un
-- aviso). Desde ahora toda caja es CAJA_MAYOR.
--
-- Aditiva y reversible:
--   * No se quita el valor 'CAJA_CHICA' del enum financiero.caja_virtual_tipo ni la columna limite_gs
--     (estrategia de 2 versiones).
--   * tipo_original guarda el tipo anterior de las cajas convertidas. Para volver atras:
--       UPDATE financiero.caja_virtual SET tipo = tipo_original::financiero.caja_virtual_tipo
--        WHERE tipo_original IS NOT NULL;
--
-- Central-only: financiero.caja_virtual no esta en ninguna publicacion (el filial no la tiene).

ALTER TABLE financiero.caja_virtual ADD COLUMN IF NOT EXISTS tipo_original varchar(20);

COMMENT ON COLUMN financiero.caja_virtual.tipo_original IS
    'Tipo que tenia la caja antes de retirar CAJA_CHICA (V274.5, issue #220). NULL = no se convirtio.';

DO $$
DECLARE
    a_convertir integer;
    copiadas    integer;
    convertidas integer;
BEGIN
    SELECT count(*) INTO a_convertir FROM financiero.caja_virtual WHERE tipo = 'CAJA_CHICA';

    UPDATE financiero.caja_virtual
       SET tipo_original = tipo::text
     WHERE tipo = 'CAJA_CHICA';
    GET DIAGNOSTICS copiadas = ROW_COUNT;

    UPDATE financiero.caja_virtual
       SET tipo = 'CAJA_MAYOR'
     WHERE tipo = 'CAJA_CHICA';
    GET DIAGNOSTICS convertidas = ROW_COUNT;

    -- Cada caja convertida tiene que tener su tipo anterior guardado. Si no, se aborta toda la
    -- migracion y la base queda como estaba.
    IF convertidas <> a_convertir OR copiadas <> a_convertir THEN
        RAISE EXCEPTION 'V274.5: se esperaban % cajas CAJA_CHICA y se copiaron % / convirtieron %',
            a_convertir, copiadas, convertidas;
    END IF;

    RAISE NOTICE 'V274.5: % cajas CAJA_CHICA pasadas a CAJA_MAYOR (tipo_original guardado)', convertidas;
END $$;
