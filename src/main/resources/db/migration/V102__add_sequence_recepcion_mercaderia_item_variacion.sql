-- Crear secuencia para recepcion_mercaderia_item_variacion
CREATE SEQUENCE operaciones.recepcion_mercaderia_item_variacion_id_seq
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1;

-- Asignar la secuencia a la columna id
ALTER TABLE operaciones.recepcion_mercaderia_item_variacion 
    ALTER COLUMN id SET DEFAULT nextval('operaciones.recepcion_mercaderia_item_variacion_id_seq'::regclass);

-- Asignar la secuencia como propietaria de la columna
ALTER SEQUENCE operaciones.recepcion_mercaderia_item_variacion_id_seq OWNED BY operaciones.recepcion_mercaderia_item_variacion.id;
