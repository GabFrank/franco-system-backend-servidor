-- =====================================================================
-- costo_por_producto: el costo tambien en la moneda de costeo (modulo COSTOS)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- El costo medio se calcula en guaranies. Una empresa que compra en dolares necesita llevarlo en
-- dolares: ponderar en guaranies y convertir despues revalua todo el stock a la cotizacion del
-- dia. Las columnas de hoy NO cambian de significado (siguen en la moneda de la empresa: de ahi
-- copia el PDV venta_item.costo_unitario y salen lucro, devoluciones y reportes). El costo en la
-- moneda de costeo va en estas cuatro columnas nuevas, que escribe CostosPorProductoService junto
-- con las de siempre. Con COSTOS = moneda de la empresa (sin configurar) son copia.
--
-- SIN UPDATE MASIVO: las filas viejas quedan en NULL. El punto de partida de la primera compra
-- con otra moneda de costeo es el costo de la empresa convertido a la cotizacion de compras de
-- vigente_desde (CosteoMoneda).
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL ESPEJO DEL FILIAL (V109.5) VA ANTES
--
-- costo_por_producto es MAIN_TO_ALL. La primera fila que el central escriba con estas columnas
-- deja en crash-loop ("missing replicated column") a la filial que no las tenga. Antes de
-- desplegar este JAR, cada filial del canal tiene que tener 109.5 en flyway_schema_history.
--
-- ESTE ES EL LADO PUBLISHER. Nullable y sin default: agregar la columna no reescribe la tabla.
-- =====================================================================
ALTER TABLE productos.costo_por_producto ADD COLUMN IF NOT EXISTS moneda_costeo_id BIGINT;
ALTER TABLE productos.costo_por_producto ADD COLUMN IF NOT EXISTS costo_medio_costeo NUMERIC;
ALTER TABLE productos.costo_por_producto ADD COLUMN IF NOT EXISTS ultimo_precio_compra_costeo NUMERIC;
ALTER TABLE productos.costo_por_producto ADD COLUMN IF NOT EXISTS cotizacion_costeo NUMERIC;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'costo_por_producto_moneda_costeo_fk') THEN
        -- NOT VALID: no recorre la tabla (todas las filas estan en NULL); rige para las nuevas.
        ALTER TABLE productos.costo_por_producto
            ADD CONSTRAINT costo_por_producto_moneda_costeo_fk
            FOREIGN KEY (moneda_costeo_id) REFERENCES financiero.moneda (id) NOT VALID;
    END IF;
END $$;

COMMENT ON COLUMN productos.costo_por_producto.moneda_costeo_id IS
    'Moneda de costeo (modulo COSTOS) de las columnas *_costeo. NULL = fila anterior a la moneda por modulo.';
COMMENT ON COLUMN productos.costo_por_producto.costo_medio_costeo IS
    'Costo medio ponderado en la moneda de costeo. costo_medio sigue en la moneda de la empresa.';
COMMENT ON COLUMN productos.costo_por_producto.ultimo_precio_compra_costeo IS
    'Ultimo precio de compra en la moneda de costeo. ultimo_precio_compra sigue en la moneda de la empresa.';
COMMENT ON COLUMN productos.costo_por_producto.cotizacion_costeo IS
    'Guaranies por unidad de la moneda de costeo con que se calculo la fila (1 si es el guarani).';
