-- Reconciliar cuenta_bancaria.saldo_reservado con los cheques que de verdad reservan (issue #601).
--
-- CUANDO: antes de volver a un central anterior a la version 2026-11 con la bandera cheque.al_dia_emitido prendida
-- (o despues, si ya se volvio). El central anterior no conoce el cheque al dia EMITIDO: al cobrarlo o anularlo no
-- libera su reserva y saldo_reservado queda inflado. Tambien sirve si BancoLedgerService loguea
-- "Saldo reservado de la cuenta ... quedaba en ...".
--
-- ORDEN:
--   1. Apagar la bandera:  UPDATE empresarial.configuracion_sistema SET valor = 'false' WHERE clave = 'cheque.al_dia_emitido';
--   2. Correr la PARTE A (solo lectura) y revisar.
--   3. Correr la PARTE B dentro de la transaccion; si el conteo no es el esperado, ROLLBACK.
--   4. Recien despues, el rollback del JAR.
--
-- Regla: lo reservado de una cuenta = suma del total de sus cheques DIFERIDO y EMITIDO (no anulados, no cobrados).

-- ── PARTE A — solo lectura: que cambiaria ─────────────────────────────────────────────────────────────────
SELECT cb.id, cb.numero, cb.saldo_reservado AS reservado_actual,
       coalesce(p.pendiente, 0)            AS reservado_segun_cheques,
       coalesce(p.pendiente, 0) - coalesce(cb.saldo_reservado, 0) AS ajuste,
       coalesce(p.emitidos, 0)             AS cheques_emitidos
  FROM financiero.cuenta_bancaria cb
  LEFT JOIN (SELECT cuenta_bancaria_id,
                    sum(total) AS pendiente,
                    count(*) FILTER (WHERE estado = 'EMITIDO') AS emitidos
               FROM financiero.cheque
              WHERE estado IN ('DIFERIDO', 'EMITIDO')
              GROUP BY cuenta_bancaria_id) p ON p.cuenta_bancaria_id = cb.id
 WHERE coalesce(cb.saldo_reservado, 0) <> coalesce(p.pendiente, 0) OR coalesce(p.emitidos, 0) > 0
 ORDER BY cb.id;

-- ── PARTE B — corrige (en transaccion) ────────────────────────────────────────────────────────────────────
BEGIN;

-- B1. Los EMITIDO pasan a DIFERIDO con diferido = true: el central anterior los cobra y libera bien.
--     Su fecha de pago ya es la de emision, asi que aparecen como vencidos hoy en el tablero viejo.
DO $$
DECLARE esperados integer; hechos integer;
BEGIN
    SELECT count(*) INTO esperados FROM financiero.cheque WHERE estado = 'EMITIDO';
    UPDATE financiero.cheque SET estado = 'DIFERIDO', diferido = true WHERE estado = 'EMITIDO';
    GET DIAGNOSTICS hechos = ROW_COUNT;
    IF hechos <> esperados THEN RAISE EXCEPTION 'emitidos: % de %', hechos, esperados; END IF;
    RAISE NOTICE '% cheques EMITIDO pasados a DIFERIDO', hechos;
END $$;

-- B2. saldo_reservado = lo que reservan los cheques pendientes.
DO $$
DECLARE esperadas integer; hechas integer;
BEGIN
    SELECT count(*) INTO esperadas
      FROM financiero.cuenta_bancaria cb
      LEFT JOIN (SELECT cuenta_bancaria_id, sum(total) AS pendiente FROM financiero.cheque
                  WHERE estado IN ('DIFERIDO', 'EMITIDO') GROUP BY cuenta_bancaria_id) p ON p.cuenta_bancaria_id = cb.id
     WHERE coalesce(cb.saldo_reservado, 0) <> coalesce(p.pendiente, 0);

    UPDATE financiero.cuenta_bancaria cb
       SET saldo_reservado = coalesce(p.pendiente, 0)
      FROM (SELECT cb2.id, (SELECT sum(c.total) FROM financiero.cheque c
                             WHERE c.cuenta_bancaria_id = cb2.id AND c.estado IN ('DIFERIDO', 'EMITIDO')) AS pendiente
              FROM financiero.cuenta_bancaria cb2) p
     WHERE p.id = cb.id AND coalesce(cb.saldo_reservado, 0) <> coalesce(p.pendiente, 0);
    GET DIAGNOSTICS hechas = ROW_COUNT;
    IF hechas <> esperadas THEN RAISE EXCEPTION 'cuentas: % de %', hechas, esperadas; END IF;
    RAISE NOTICE '% cuentas con el saldo reservado corregido', hechas;
END $$;

-- Revisar la PARTE A otra vez (no deberia devolver filas) y recien entonces:
-- COMMIT;   (o ROLLBACK; si algo no cierra)
