-- Control de lotes - Fase 1 (Compras).
--
-- operaciones.movimiento_stock_lote es el LEDGER hijo de operaciones.movimiento_stock: desglosa
-- cada movimiento agregado en las cantidades que corresponden a cada numero de lote / vencimiento.
-- NO es una tabla de saldos. El stock por lote se deriva sumando este ledger, exactamente igual
-- que el stock normal se deriva de SUM(movimiento_stock.cantidad) -- ver el comentario de
-- desactivacion en la entidad StockPorProductoSucursal (stock derivado desde 2026-01-30).
--
-- Por que ledger y no una tabla con cantidad_disponible:
--   1) Un contador replicado bidireccionalmente sufre lost updates (el central suma por compra y
--      la filial resta por venta sobre la misma fila). Los conflictos de replicacion se saltean,
--      asi que el saldo quedaria desviado en silencio y de forma permanente. Cada fila de un
--      ledger es un INSERT con PK propia: nada se pisa.
--   2) Las rutas que deshacen una recepcion (cancelarVerificacion, resetearVerificacion,
--      deshacerVerificacionPorProducto) BORRAN fisicamente el movimiento_stock. Con
--      ON DELETE CASCADE la reversion por lote sale gratis, sin logica duplicada en 3 lugares.
--
-- INVARIANTE: SUM(hijas.cantidad) = padre.cantidad. cantidad va SIEMPRE en unidades base,
-- igual que movimiento_stock.cantidad (el frontend ya convierte de presentacion a unidad base).
--
-- ORDEN DE DESPLIEGUE: la filial debe tener su tabla creada (filial V81.3) ANTES de que se
-- registre esta tabla en configuraciones.replication_table, sino el subscriber de la filial rompe.

CREATE TABLE IF NOT EXISTS operaciones.movimiento_stock_lote (
    -- id NO es serial: el central lo asigna en Java con el esquema par/impar (ver abajo).
    id                  BIGINT NOT NULL,
    sucursal_id         BIGINT NOT NULL,
    movimiento_stock_id BIGINT NOT NULL,
    producto_id         BIGINT NOT NULL,
    -- Informativo: en que presentacion se recibio. La cantidad va igual en unidades base.
    presentacion_id     BIGINT,
    numero_lote         VARCHAR(100) NOT NULL,
    -- NULL cuando el producto no maneja vencimiento.
    fecha_vencimiento   DATE,
    -- Positivo para entradas (COMPRA), negativo para salidas (VENTA).
    cantidad            NUMERIC(15,4) NOT NULL,
    -- id de recepcion_mercaderia_item / venta_item segun el origen.
    referencia          BIGINT,
    estado              BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id          BIGINT,
    creado_en           TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, sucursal_id),
    -- La pieza clave: al borrarse el movimiento agregado, el desglose por lote desaparece solo.
    CONSTRAINT fk_msl_movimiento
        FOREIGN KEY (movimiento_stock_id, sucursal_id)
        REFERENCES operaciones.movimiento_stock (id, sucursal_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_msl_producto     FOREIGN KEY (producto_id)     REFERENCES productos.producto(id),
    CONSTRAINT fk_msl_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id),
    CONSTRAINT fk_msl_usuario      FOREIGN KEY (usuario_id)      REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_msl_producto_sucursal
    ON operaciones.movimiento_stock_lote (producto_id, sucursal_id) WHERE estado = TRUE;
CREATE INDEX IF NOT EXISTS idx_msl_vencimiento
    ON operaciones.movimiento_stock_lote (fecha_vencimiento) WHERE estado = TRUE;
CREATE INDEX IF NOT EXISTS idx_msl_movimiento
    ON operaciones.movimiento_stock_lote (movimiento_stock_id, sucursal_id);

-- Necesario para que la replicacion logica pueda identificar filas en UPDATE/DELETE.
ALTER TABLE operaciones.movimiento_stock_lote REPLICA IDENTITY FULL;

COMMENT ON TABLE operaciones.movimiento_stock_lote IS
    'Ledger hijo de movimiento_stock: desglose de cada movimiento por numero de lote y vencimiento. El stock por lote se deriva sumando esta tabla (ver vista v_stock_lote).';

-- ============================================================================
-- CRITICO: esquema de IDs par/impar para evitar colisiones de PK bajo replicacion.
--
-- Central y filial escriben en esta misma tabla con el mismo sucursal_id. Se replica el mismo
-- esquema que ya usa operaciones.movimiento_stock:
--   - Central  -> IDs IMPARES (MovimientoStockLoteService.save, espejo de MovimientoStockService).
--   - Filial   -> IDs PARES   (secuencia con INCREMENT BY 2 arrancando en par, filial V81.3).
--
-- Un BIGSERIAL plano en ambos lados colisiona apenas la filial empiece a descontar por venta.
-- Por eso esta tabla NO tiene secuencia en el central: el id se asigna siempre desde Java.
-- ============================================================================

-- ============================================================================
-- Vista de stock por lote. No se replica (cada base la calcula sobre sus propias filas),
-- lo que permite a la filial consultar local sin latencia de red.
-- FEFO = ORDER BY fecha_vencimiento ASC NULLS LAST sobre esta vista.
-- ============================================================================
CREATE OR REPLACE VIEW operaciones.v_stock_lote AS
SELECT producto_id,
       sucursal_id,
       numero_lote,
       fecha_vencimiento,
       SUM(cantidad) AS cantidad_disponible
FROM operaciones.movimiento_stock_lote
WHERE estado = TRUE
GROUP BY producto_id, sucursal_id, numero_lote, fecha_vencimiento
HAVING SUM(cantidad) <> 0;
