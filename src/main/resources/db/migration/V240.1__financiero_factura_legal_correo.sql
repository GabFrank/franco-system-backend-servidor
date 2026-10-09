-- =====================================================================
-- Envio del KuDE de la factura electronica al correo del cliente
-- =====================================================================
-- Una fila por factura que ya se intento enviar. Es lo que evita mandar dos
-- veces la misma factura: el proceso solo toma facturas sin fila, o con fila
-- en ERROR que todavia tiene reintentos.
--
--   ENVIADO  el servidor de correo acepto el mensaje
--   ERROR    fallo el envio o la generacion del PDF; se reintenta
--   OMITIDO  el correo guardado del cliente no es una direccion valida
--
-- Aditivo e idempotente. Central-only: NO se registra en
-- configuraciones.replication_table, asi que no entra en ninguna publicacion.
-- =====================================================================

CREATE TABLE IF NOT EXISTS financiero.factura_legal_correo (
    id                 bigserial PRIMARY KEY,
    factura_legal_id   bigint NOT NULL,
    sucursal_id        bigint NOT NULL,
    email              varchar(255),
    estado             varchar(10) NOT NULL,
    intentos           integer NOT NULL DEFAULT 1,
    ultimo_error       varchar(500),
    creado_en          timestamp NOT NULL DEFAULT now(),
    ultimo_intento_en  timestamp NOT NULL DEFAULT now(),
    enviado_en         timestamp,
    CONSTRAINT uk_factura_legal_correo UNIQUE (factura_legal_id, sucursal_id),
    CONSTRAINT ck_factura_legal_correo_estado CHECK (estado IN ('ENVIADO', 'ERROR', 'OMITIDO'))
);
