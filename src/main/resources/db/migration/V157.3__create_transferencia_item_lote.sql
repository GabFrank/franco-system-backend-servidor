-- Control de lotes - Asignacion manual de lotes en transferencias.
--
-- Hasta ahora el desglose por lote de una transferencia lo resolvia SIEMPRE el sistema por FEFO
-- (MovimientoStockService.desglosarTransferenciaPorLote -> LoteFefoService.asignar). Eso es
-- correcto por defecto, pero el operador no tiene forma de decir "de este lote no, mandame el
-- otro" cuando la realidad del deposito no coincide con el orden teorico.
--
-- Esta tabla guarda esa decision manual. NO reemplaza al ledger operaciones.movimiento_stock_lote:
-- es la INTENCION del operador, y el ledger sigue siendo la fuente de verdad de lo que realmente
-- se movio. El desglose lee esta tabla y, si no encuentra nada, cae a FEFO exactamente como antes.
-- Por eso toda transferencia existente sigue comportandose igual.
--
-- Por que una tabla hija y no una columna lote_id en transferencia_item:
-- la cantidad de un item puede legitimamente cubrirse con varios lotes (transferir 100 cuando
-- ningun lote solo alcanza). Mismo patron que operaciones.recepcion_mercaderia_item_variacion,
-- que ya resuelve el caso espejo en la entrada por compra.
--
-- REPLICACION: esta tabla NO se registra en configuraciones.replication_table, igual que su padre
-- operaciones.transferencia_item, que tampoco esta replicada ni aparece en ninguna publicacion.
-- Las transferencias se administran en el central. Registrarla romperia esa simetria.

CREATE TABLE IF NOT EXISTS operaciones.transferencia_item_lote (
    -- BIGSERIAL simple: la tabla no se replica, asi que no hace falta el esquema par/impar de ids
    -- que si usa operaciones.movimiento_stock_lote para convivir con las filiales.
    id                     BIGSERIAL PRIMARY KEY,
    transferencia_item_id  BIGINT NOT NULL,
    lote_id                BIGINT NOT NULL,
    -- Desnormalizado a proposito, igual que en el ledger: el numero de lote es inmutable (es parte
    -- de la identidad del lote) y deja la fila legible sin resolver el join.
    numero_lote            VARCHAR(100) NOT NULL,
    cantidad               NUMERIC(15, 4) NOT NULL,
    -- PRE_TRANSFERENCIA: lo que se pidio al crear el item.
    -- PREPARACION: lo que realmente se preparo. Pisa a la anterior sin borrarla, para no perder
    -- el rastro de que se habia pedido originalmente.
    etapa                  VARCHAR(30) NOT NULL,
    usuario_id             BIGINT,
    creado_en              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_til_item_etapa_lote UNIQUE (transferencia_item_id, etapa, lote_id),
    CONSTRAINT ck_til_etapa CHECK (etapa IN ('PRE_TRANSFERENCIA', 'PREPARACION')),
    CONSTRAINT ck_til_cantidad CHECK (cantidad > 0),
    -- ON DELETE CASCADE: si se borra el item de la transferencia, su asignacion no tiene sentido
    -- por si sola. Es el mismo criterio que usa fk_msl_movimiento en el ledger.
    CONSTRAINT fk_til_transferencia_item FOREIGN KEY (transferencia_item_id)
        REFERENCES operaciones.transferencia_item(id) ON DELETE CASCADE,
    CONSTRAINT fk_til_lote    FOREIGN KEY (lote_id)    REFERENCES operaciones.lote(id),
    CONSTRAINT fk_til_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_til_item ON operaciones.transferencia_item_lote (transferencia_item_id);
CREATE INDEX IF NOT EXISTS idx_til_lote ON operaciones.transferencia_item_lote (lote_id);

COMMENT ON TABLE operaciones.transferencia_item_lote IS
    'Asignacion manual de lotes a un item de transferencia. Es la intencion del operador; el desglose real vive en movimiento_stock_lote. Sin filas para un item, el desglose cae a FEFO.';
