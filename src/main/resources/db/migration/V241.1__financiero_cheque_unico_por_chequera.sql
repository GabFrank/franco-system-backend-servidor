-- Un cheque por numero y chequera (issue #376). La aplicacion ya no repite numeros (lock de la chequera,
-- relectura y correlativo que no retrocede); este indice es el respaldo. Incluye los anulados: un cheque
-- anulado sigue ocupando su numero.
--
-- Esta migracion NO PUEDE FALLAR: una migracion que falla tumba el arranque del central. Si hay numeros
-- repetidos el CREATE UNIQUE INDEX da unique_violation; se captura, queda un WARNING en el log y el
-- arranque sigue sin el indice. El central avisa al arrancar si falta (ChequeUnicoVerificador).
DO $$
BEGIN
    CREATE UNIQUE INDEX IF NOT EXISTS uq_cheque_chequera_numero
        ON financiero.cheque (chequera_id, numero);
EXCEPTION
    WHEN unique_violation THEN
        RAISE WARNING 'uq_cheque_chequera_numero NO se creo: hay cheques con el mismo numero en una chequera. Corregirlos y crear el indice a mano.';
END
$$;
