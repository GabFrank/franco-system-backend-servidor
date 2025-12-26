-- V102.5: Add sequence for recepcion_mercaderia_item_variacion
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item_variacion') THEN
        -- Crear secuencia para recepcion_mercaderia_item_variacion
        IF NOT EXISTS (SELECT 1 FROM pg_sequences WHERE schemaname = 'operaciones' AND sequencename = 'recepcion_mercaderia_item_variacion_id_seq') THEN
            CREATE SEQUENCE operaciones.recepcion_mercaderia_item_variacion_id_seq
                INCREMENT 1
                START 1
                MINVALUE 1
                MAXVALUE 9223372036854775807
                CACHE 1;
        END IF;
        
        -- Asignar la secuencia a la columna id
        ALTER TABLE operaciones.recepcion_mercaderia_item_variacion 
            ALTER COLUMN id SET DEFAULT nextval('operaciones.recepcion_mercaderia_item_variacion_id_seq'::regclass);
        
        -- Asignar la secuencia como propietaria de la columna
        ALTER SEQUENCE operaciones.recepcion_mercaderia_item_variacion_id_seq OWNED BY operaciones.recepcion_mercaderia_item_variacion.id;
    END IF;
END $$;
