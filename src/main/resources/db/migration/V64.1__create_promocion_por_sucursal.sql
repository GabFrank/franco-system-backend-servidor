CREATE TABLE productos.promocion_por_sucursal (
    id BIGSERIAL NOT NULL,
    precio_sucursal_id INT8 NULL,
    activo BOOL DEFAULT TRUE,
    es_promocion bool DEFAULT FALSE,
    tipo_promocion VARCHAR,
    sucursal_id INT8 NULL,
    usuario_id INT8 NULL,
    creado_en TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT promocion_por_sucursal_pkey PRIMARY KEY (id),
    CONSTRAINT promocion_por_sucursal_fk FOREIGN KEY (precio_sucursal_id) REFERENCES productos.precio_por_sucursal(id) ON DELETE SET NULL,
    CONSTRAINT promocion_por_sucursal_fk_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL,
    CONSTRAINT promocion_por_sucursal_fk_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL
);  