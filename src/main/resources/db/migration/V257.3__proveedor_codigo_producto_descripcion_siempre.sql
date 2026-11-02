-- =====================================================================
-- Compras: lo aprendido por un código del proveedor también guarda la
-- descripción, para sugerir el producto en un renglón sin código (factura
-- de papel o electrónica) del mismo proveedor.
--
-- * Completa descripcion_normalizada en los vínculos por código que ya
--   existen, a partir de descripcion_proveedor. Misma regla que
--   DescripcionProveedor.normalizar: sin tildes, mayúsculas, solo letras y
--   números con un espacio entre palabras, hasta 500 caracteres.
-- * Índice para buscar por proveedor + descripción en todos los vínculos.
--
-- El índice único por descripción sigue valiendo solo para los vínculos sin
-- código: dos códigos distintos pueden tener la misma descripción (el central
-- entonces no preselecciona ninguno).
--
-- Aditivo e idempotente. Central-only: no entra a configuraciones.replication_table.
-- =====================================================================

UPDATE productos.proveedor_codigo_producto
SET descripcion_normalizada = NULLIF(RTRIM(LEFT(BTRIM(REGEXP_REPLACE(
        UPPER(TRANSLATE(descripcion_proveedor,
            'áéíóúüñÁÉÍÓÚÜÑàèìòùÀÈÌÒÙâêîôûÂÊÎÔÛäëïöÄËÏÖçÇ',
            'aeiouunAEIOUUNaeiouAEIOUaeiouAEIOUaeioAEIOcC')),
        '[^A-Z0-9]+', ' ', 'g')), 500)), '')
WHERE descripcion_normalizada IS NULL
  AND codigo_proveedor IS NOT NULL
  AND descripcion_proveedor IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_proveedor_codigo_producto_descripcion
    ON productos.proveedor_codigo_producto (proveedor_id, descripcion_normalizada)
    WHERE descripcion_normalizada IS NOT NULL;
