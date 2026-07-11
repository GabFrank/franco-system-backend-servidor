-- Registro unico de impresoras (termicas y normales) administrable desde la app.
-- Reemplaza la config fragmentada (config-backup.json del desktop, argumentos GraphQL,
-- hardcodes y columnas muertas de punto_de_venta).
-- Origen de escritura: servidor central (administrativo). Se replica MAIN->ALL a las
-- filiales via central_pub, de modo que cada host ve el registro completo (necesario
-- para el routing: la impresora "sabe" en que sucursal/host esta conectada).

CREATE TABLE IF NOT EXISTS empresarial.impresora (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    es_predeterminada BOOLEAN DEFAULT FALSE,
    -- Host donde esta conectada fisicamente la impresora (routing filial <-> central)
    sucursal_id BIGINT,
    -- TERMICA | NORMAL | ETIQUETA
    tipo VARCHAR(20) NOT NULL,
    -- TICKET | FACTURA | REPORTE | ETIQUETA | COMANDA
    uso VARCHAR(20),
    -- CUPS | USB | RED | BLUETOOTH
    conexion VARCHAR(20) NOT NULL,
    -- Nombre de cola CUPS (conexion CUPS/USB/BLUETOOTH)
    cola_cups VARCHAR(255),
    -- Direccion IP y puerto (conexion RED / inalambrica), puerto RAW/JetDirect por defecto 9100
    ip VARCHAR(60),
    puerto INTEGER,
    -- MM_48 | MM_58 | MM_72 | MM_80 | A4 | CARTA | CUSTOM
    perfil_papel VARCHAR(20),
    -- Columnas de caracteres para termicas (derivado del perfil, editable)
    columnas INTEGER,
    -- Ancho/alto en mm para hoja/custom (impresoras normales)
    ancho_mm INTEGER,
    alto_mm INTEGER,
    -- EPSON | STAR | BEMATECH | HP | ...
    marca VARCHAR(60),
    -- Code-page ESC/POS
    codepage VARCHAR(40),
    creado_en TIMESTAMP,
    usuario_id BIGINT,
    CONSTRAINT fk_impresora_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id),
    CONSTRAINT fk_impresora_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_impresora_sucursal_id ON empresarial.impresora(sucursal_id);

-- Sumar la tabla a la publicacion central_pub (direccion MAIN_TO_ALL, sin filtro por
-- sucursal_id) para que TODOS los hosts reciban el registro completo de impresoras.
-- Patron identico a V119__add_replication_test_to_publications.sql.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'central_pub') THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = 'central_pub' AND tablename = 'impresora'
        ) THEN
            EXECUTE 'ALTER PUBLICATION central_pub ADD TABLE empresarial.impresora';
            RAISE NOTICE 'Added empresarial.impresora to central_pub';
        END IF;
    END IF;
END $$;
