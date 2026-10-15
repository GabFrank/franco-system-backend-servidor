-- El modulo de compras paso (V73.5 / V74.5) del modelo monolitico Compra al modelo por etapas
-- (pedido + nota_recepcion + recepcion_mercaderia + solicitud_pago). Las tablas compra y compra_item
-- quedaron sin uso, igual que nota_recepcion.compra_id, cuya FK apuntaba por error a
-- nota_recepcion_item. El codigo que las mapeaba se borro en el release anterior (fase 1 de #188):
-- esta migracion sale recien cuando ese release esta estable, porque un JAR previo lee compra_id.
--
-- Nada se borra si tiene datos o si otro objeto depende de el: se avisa con WARNING y la migracion
-- sigue, para que una base con algo inesperado no impida arrancar el JAR.
DO $$
DECLARE
    v_filas bigint;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion' AND column_name = 'compra_id') THEN
        SELECT count(*) INTO v_filas FROM operaciones.nota_recepcion WHERE compra_id IS NOT NULL;
        IF v_filas = 0 THEN
            ALTER TABLE operaciones.nota_recepcion DROP CONSTRAINT IF EXISTS nota_recepcion_compra_fk;
            ALTER TABLE operaciones.nota_recepcion DROP COLUMN compra_id;
        ELSE
            RAISE WARNING 'V241.5: nota_recepcion.compra_id tiene % filas con valor, no se borra', v_filas;
        END IF;
    END IF;

    IF to_regclass('operaciones.compra_item') IS NOT NULL THEN
        SELECT count(*) INTO v_filas FROM operaciones.compra_item;
        IF v_filas = 0 THEN
            BEGIN
                DROP TABLE operaciones.compra_item;
            EXCEPTION WHEN dependent_objects_still_exist THEN
                RAISE WARNING 'V241.5: operaciones.compra_item tiene dependencias, no se borra';
            END;
        ELSE
            RAISE WARNING 'V241.5: operaciones.compra_item tiene % filas, no se borra', v_filas;
        END IF;
    END IF;

    IF to_regclass('operaciones.compra') IS NOT NULL THEN
        SELECT count(*) INTO v_filas FROM operaciones.compra;
        IF v_filas = 0 THEN
            BEGIN
                DROP TABLE operaciones.compra;
            EXCEPTION WHEN dependent_objects_still_exist THEN
                RAISE WARNING 'V241.5: operaciones.compra tiene dependencias, no se borra';
            END;
        ELSE
            RAISE WARNING 'V241.5: operaciones.compra tiene % filas, no se borra', v_filas;
        END IF;
    END IF;

    -- Los tipos solo se pueden borrar si ya no queda ninguna columna que los use.
    BEGIN
        DROP TYPE IF EXISTS operaciones.compra_item_estado;
    EXCEPTION WHEN dependent_objects_still_exist THEN
        RAISE WARNING 'V241.5: el tipo operaciones.compra_item_estado sigue en uso, no se borra';
    END;
    BEGIN
        DROP TYPE IF EXISTS operaciones.compra_estado;
    EXCEPTION WHEN dependent_objects_still_exist THEN
        RAISE WARNING 'V241.5: el tipo operaciones.compra_estado sigue en uso, no se borra';
    END;
    BEGIN
        DROP TYPE IF EXISTS operaciones.compra_tipo_boleta;
    EXCEPTION WHEN dependent_objects_still_exist THEN
        RAISE WARNING 'V241.5: el tipo operaciones.compra_tipo_boleta sigue en uso, no se borra';
    END;
END $$;
