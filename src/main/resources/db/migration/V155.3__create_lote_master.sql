-- Control de lotes - Maestro de lotes.
--
-- operaciones.lote convierte al lote en una ENTIDAD en vez de texto repetido en cada fila del
-- ledger operaciones.movimiento_stock_lote. Es el patron de stock.lot en Odoo y de batch a nivel
-- material en SAP: la unicidad es (producto, numero_lote), porque el numero de lote lo asigna el
-- fabricante por producto (GS1 identifica el lote siempre junto al GTIN).
--
-- Que resuelve:
--   1) La fecha de vencimiento deja de estar desnormalizada en cada movimiento. Dos recepciones
--      del mismo lote con fechas tipeadas distinto ya no producen dos lotes distintos en la vista.
--   2) Da lugar a los datos del lote: proveedor, fabricacion, fecha de retiro.
--   3) Habilita el bloqueo por recall via la columna estado, sin tocar el stock fisico ni mentir
--      sobre la existencia.
--
-- ORDEN DE DESPLIEGUE: la filial debe tener su tabla creada (filial V82.3) ANTES de que se
-- registre operaciones.lote en configuraciones.replication_table (V156.3).

CREATE TABLE IF NOT EXISTS operaciones.lote (
    -- BIGSERIAL simple: a diferencia de movimiento_stock_lote, aca NO hace falta el esquema
    -- par/impar porque la filial nunca escribe lotes. El maestro se administra solo en el central
    -- y baja completo por replicacion MAIN_TO_ALL.
    id                BIGSERIAL PRIMARY KEY,
    producto_id       BIGINT NOT NULL,
    numero_lote       VARCHAR(100) NOT NULL,
    fecha_vencimiento DATE,
    -- FEFO ordena por esta fecha, no por el vencimiento: se calcula restando
    -- producto.dias_vencimiento al vencimiento, para sacar la mercaderia antes de que venza.
    fecha_retiro      DATE,
    fecha_fabricacion DATE,
    proveedor_id      BIGINT,
    -- LIBERADO | CUARENTENA | BLOQUEADO. Solo los LIBERADO entran en FEFO y se pueden vender.
    estado            VARCHAR(20) NOT NULL DEFAULT 'LIBERADO',
    observacion       VARCHAR(500),
    usuario_id        BIGINT,
    creado_en         TIMESTAMP NOT NULL DEFAULT NOW(),
    actualizado_en    TIMESTAMP,
    CONSTRAINT uq_lote_producto_numero UNIQUE (producto_id, numero_lote),
    CONSTRAINT ck_lote_estado CHECK (estado IN ('LIBERADO', 'CUARENTENA', 'BLOQUEADO')),
    CONSTRAINT fk_lote_producto  FOREIGN KEY (producto_id) REFERENCES productos.producto(id),
    CONSTRAINT fk_lote_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id),
    CONSTRAINT fk_lote_usuario   FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_lote_producto ON operaciones.lote (producto_id);
CREATE INDEX IF NOT EXISTS idx_lote_retiro   ON operaciones.lote (fecha_retiro) WHERE estado = 'LIBERADO';

ALTER TABLE operaciones.lote REPLICA IDENTITY FULL;

COMMENT ON TABLE operaciones.lote IS
    'Maestro de lotes: unicidad (producto, numero_lote). Fuente de verdad de vencimiento, retiro y estado. El saldo por lote se deriva del ledger movimiento_stock_lote.';

-- ============================================================================
-- Vinculo del ledger con el maestro.
--
-- En el CENTRAL la FK si existe: el lote y el movimiento se crean en la misma transaccion de
-- finalizacion de recepcion, asi que no hay riesgo de orden.
--
-- En la FILIAL (V82.3) el mismo vinculo va SIN foreign key a proposito, porque operaciones.lote
-- viaja por central_pub y el ledger por central_filialX_pub: son publicaciones distintas y no hay
-- garantia de orden entre ellas.
-- ============================================================================
ALTER TABLE operaciones.movimiento_stock_lote ADD COLUMN IF NOT EXISTS lote_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_msl_lote ON operaciones.movimiento_stock_lote (lote_id);

-- numero_lote se mantiene desnormalizado en el ledger: es inmutable (parte de la identidad del
-- lote), nunca diverge, y deja las filas legibles aunque el maestro llegue con retraso a la filial.
--
-- fecha_vencimiento queda DEPRECADA en el ledger: se deja de escribir y de leer, la fuente de
-- verdad pasa a ser operaciones.lote. No se elimina aca por la regla de 2 versiones de Flyway.
COMMENT ON COLUMN operaciones.movimiento_stock_lote.fecha_vencimiento IS
    'DEPRECADO desde V155.3: la fecha de vencimiento vive en operaciones.lote. No escribir ni leer.';

-- ============================================================================
-- Backfill: una fila de lote por cada (producto, numero_lote) distinto que ya exista en el ledger.
-- Se toma la fecha_vencimiento que tenga el movimiento, y se calcula la fecha de retiro con los
-- dias configurados en el producto (mismo criterio que usara LoteService de aca en adelante).
-- ============================================================================
INSERT INTO operaciones.lote (producto_id, numero_lote, fecha_vencimiento, fecha_retiro, creado_en)
SELECT DISTINCT ON (msl.producto_id, msl.numero_lote)
       msl.producto_id,
       msl.numero_lote,
       msl.fecha_vencimiento,
       CASE
           WHEN msl.fecha_vencimiento IS NOT NULL AND COALESCE(p.dias_vencimiento, 0) > 0
           THEN msl.fecha_vencimiento - (p.dias_vencimiento::int || ' days')::interval
           ELSE NULL
       END::date,
       NOW()
FROM operaciones.movimiento_stock_lote msl
JOIN productos.producto p ON p.id = msl.producto_id
WHERE msl.lote_id IS NULL
ORDER BY msl.producto_id, msl.numero_lote, msl.fecha_vencimiento NULLS LAST
ON CONFLICT (producto_id, numero_lote) DO NOTHING;

UPDATE operaciones.movimiento_stock_lote msl
SET lote_id = l.id
FROM operaciones.lote l
WHERE msl.lote_id IS NULL
  AND l.producto_id = msl.producto_id
  AND l.numero_lote = msl.numero_lote;

-- ============================================================================
-- Vista de stock por lote, ahora enriquecida con el maestro.
-- El saldo se sigue derivando del ledger: el modelo no cambia, solo se resuelven los datos del lote.
-- FEFO = ORDER BY COALESCE(fecha_retiro, fecha_vencimiento) ASC NULLS LAST sobre esta vista,
-- filtrando estado = 'LIBERADO'.
--
-- Se hace DROP + CREATE y no CREATE OR REPLACE porque cambia la lista de columnas de la vista
-- (se agregan lote_id, fecha_retiro y estado), y CREATE OR REPLACE VIEW no lo permite.
-- Es seguro: una vista es derivada, no contiene datos.
-- ============================================================================
DROP VIEW IF EXISTS operaciones.v_stock_lote;

CREATE VIEW operaciones.v_stock_lote AS
SELECT msl.producto_id,
       msl.sucursal_id,
       msl.lote_id,
       msl.numero_lote,
       l.fecha_vencimiento,
       l.fecha_retiro,
       l.estado,
       SUM(msl.cantidad) AS cantidad_disponible
FROM operaciones.movimiento_stock_lote msl
LEFT JOIN operaciones.lote l ON l.id = msl.lote_id
WHERE msl.estado = TRUE
GROUP BY msl.producto_id, msl.sucursal_id, msl.lote_id, msl.numero_lote,
         l.fecha_vencimiento, l.fecha_retiro, l.estado
HAVING SUM(msl.cantidad) <> 0;
