-- El central asignaba el id de movimiento_stock y de movimiento_stock_lote con
-- MAX(id) + 1 por sucursal. Dos transacciones simultaneas leian el mismo maximo,
-- calculaban el mismo id y la segunda chocaba con la clave primaria: el movimiento
-- no se guardaba (issue #153).
--
-- Desde esta migracion el id sale de una secuencia, que Postgres reparte de forma
-- atomica. Se conserva el reparto con los filiales: el central genera impares
-- (INCREMENT BY 2) y cada filial pares.
--
-- La secuencia es una sola para todas las sucursales, asi que el id deja de ser
-- correlativo dentro de cada sucursal. La clave sigue siendo (id, sucursal_id).

-- movimiento_stock_lote nunca tuvo secuencia en el central.
CREATE SEQUENCE IF NOT EXISTS operaciones.movimiento_stock_lote_id_seq
    INCREMENT BY 2
    START WITH 1
    OWNED BY operaciones.movimiento_stock_lote.id;

-- movimiento_stock_id_seq existe desde V0 pero quedo sin uso en V23, muy por
-- detras de los ids reales. Las dos arrancan en el primer impar por encima del
-- mayor impar usado en cualquier sucursal (en la tabla o en la secuencia).
DO $$
DECLARE
    t       record;
    maximo  bigint;
    proximo bigint;
BEGIN
    FOR t IN SELECT * FROM (VALUES
            ('operaciones.movimiento_stock_id_seq', 'operaciones.movimiento_stock'),
            ('operaciones.movimiento_stock_lote_id_seq', 'operaciones.movimiento_stock_lote')
        ) AS v(secuencia, tabla)
    LOOP
        EXECUTE format('SELECT GREATEST(COALESCE((SELECT MAX(id) FROM %s WHERE id %% 2 = 1), 0), (SELECT last_value FROM %s))',
                       t.tabla, t.secuencia) INTO maximo;
        proximo := maximo + 1;
        IF proximo % 2 = 0 THEN
            proximo := proximo + 1;
        END IF;
        EXECUTE format('ALTER SEQUENCE %s INCREMENT BY 2', t.secuencia);
        PERFORM setval(t.secuencia::regclass, proximo, false);
    END LOOP;
END;
$$;
