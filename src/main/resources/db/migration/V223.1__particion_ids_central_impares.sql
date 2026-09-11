-- Reparto de ids entre el central y los filiales en las tablas que el filial
-- replica al central: el central genera impares y cada filial genera pares
-- (migracion espejo en el filial). Si los dos lados generan en el mismo espacio,
-- el INSERT replicado del filial choca con la clave primaria y la suscripcion
-- filial -> central se corta entera (issue #287).
--
-- Es el esquema que ya usan marcacion, movimiento_stock y movimiento_stock_lote.
-- Aca se completa para las tablas que quedaron afuera.

-- 1. Las secuencias del central pasan a generar solo impares, siguiendo desde el
--    mayor valor ya usado (en la tabla o en la secuencia).
DO $$
DECLARE
    t       record;
    maximo  bigint;
    proximo bigint;
BEGIN
    FOR t IN SELECT * FROM (VALUES
            ('configuraciones.inicio_sesion_id_seq', 'configuraciones.inicio_sesion'),
            ('financiero.venta_tarjeta_id_seq', 'financiero.venta_tarjeta'),
            ('financiero.maletin_id_seq', 'financiero.maletin'),
            ('financiero.movimiento_personas_id_seq', 'financiero.movimiento_personas')
        ) AS v(secuencia, tabla)
    LOOP
        IF to_regclass(t.secuencia) IS NULL OR to_regclass(t.tabla) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format('SELECT GREATEST(COALESCE((SELECT MAX(id) FROM %s), 0), (SELECT last_value FROM %s))',
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

-- 2. La base rechaza cualquier INSERT local con id par, venga del codigo, de un
--    id que manda un cliente o de un script. Los triggers comunes NO se disparan
--    en los workers de replicacion (corren con session_replication_role =
--    replica), asi que las filas que llegan del filial pasan igual.
--    Para una reparacion manual que copie filas del filial a mano, correr antes
--    SET session_replication_role = replica en esa sesion.
CREATE OR REPLACE FUNCTION configuraciones.rechazar_id_de_filial() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.id % 2 = 0 THEN
        RAISE EXCEPTION 'El central no puede insertar en %.% con id par (%): los ids pares los genera el filial y chocarian al replicarse',
            TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

--    marcacion, movimiento_stock y movimiento_stock_lote ya usaban el reparto,
--    pero nada impedia que un id par entrara por otro camino (por ejemplo
--    saveMovimientoStock con un id que manda el cliente): tambien van aca.
DO $$
DECLARE
    tabla text;
BEGIN
    FOREACH tabla IN ARRAY ARRAY[
            'configuraciones.inicio_sesion',
            'financiero.gasto',
            'financiero.venta_tarjeta',
            'financiero.maletin',
            'financiero.movimiento_personas',
            'administrativo.marcacion',
            'operaciones.movimiento_stock',
            'operaciones.movimiento_stock_lote'
        ]
    LOOP
        IF to_regclass(tabla) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format('DROP TRIGGER IF EXISTS rechazar_id_de_filial ON %s', tabla);
        EXECUTE format('CREATE TRIGGER rechazar_id_de_filial BEFORE INSERT ON %s '
                           || 'FOR EACH ROW EXECUTE FUNCTION configuraciones.rechazar_id_de_filial()', tabla);
    END LOOP;
END;
$$;
