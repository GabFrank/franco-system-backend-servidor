-- Busqueda de lote por numero, tolerante a como lo escribe la gente (#429, fase 2, entrega 1).
--
-- Ante un reclamo el cliente trae una caja y lee el lote como puede: "3497", "03497", "03 497",
-- "lote 03-497". La clave de busqueda saca todo lo que no es letra o digito, pasa a mayusculas y
-- quita los ceros a la izquierda. Es SOLO para buscar: el numero guardado no cambia y la unicidad
-- del maestro (producto, numero_lote) sigue siendo la de LoteService.normalizarNumeroLote.
--
-- Un numero que es todo ceros ("000") no queda vacio: se compara sin quitar los ceros.
-- La misma regla vive en Java (BusquedaLoteService.claveBusqueda); el test lo cubre.
--
-- IMMUTABLE para poder indexarla. Solo central: la filial no busca lotes por numero.
-- Aditiva: el codigo viejo no la usa. Vuelta atras = dejarla, o
--   DROP INDEX IF EXISTS operaciones.idx_lote_clave_busqueda;
--   DROP FUNCTION IF EXISTS operaciones.clave_busqueda_lote(text);
CREATE OR REPLACE FUNCTION operaciones.clave_busqueda_lote(numero text)
    RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    SELECT COALESCE(
        NULLIF(ltrim(regexp_replace(upper(numero), '[^A-Z0-9]', '', 'g'), '0'), ''),
        NULLIF(regexp_replace(upper(numero), '[^A-Z0-9]', '', 'g'), ''))
$$;

CREATE INDEX IF NOT EXISTS idx_lote_clave_busqueda
    ON operaciones.lote (operaciones.clave_busqueda_lote(numero_lote));
