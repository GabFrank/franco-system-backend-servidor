-- #590: tipo_match era VARCHAR(20) y APRENDIDO_DESCRIPCION tiene 21 caracteres: la factura reconocida por la
-- descripción aprendida no se podía guardar. Solo se agranda (en PostgreSQL no reescribe la tabla y el backend
-- anterior sigue funcionando igual).
ALTER TABLE operaciones.documento_recibido_item
    ALTER COLUMN tipo_match TYPE VARCHAR(40);
