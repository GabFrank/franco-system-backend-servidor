CREATE TABLE operaciones.recepcion_mercaderia_item_variacion (
    id BIGINT PRIMARY KEY,
    recepcion_mercaderia_item_id BIGINT NOT NULL,
    presentacion_id BIGINT,
    cantidad DOUBLE PRECISION,
    vencimiento TIMESTAMP,
    lote VARCHAR(255),
    rechazado BOOLEAN DEFAULT FALSE,
    motivo_rechazo VARCHAR(255),
    CONSTRAINT fk_recepcion_mercaderia_item
        FOREIGN KEY (recepcion_mercaderia_item_id)
        REFERENCES operaciones.recepcion_mercaderia_item(id),
    CONSTRAINT fk_presentacion
        FOREIGN KEY (presentacion_id)
        REFERENCES productos.presentacion(id)
);
