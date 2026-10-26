-- =====================================================================
-- Historial del envio manual de facturas por correo
-- =====================================================================
-- Una fila por cada envio que pide un usuario desde "Enviar por correo".
-- Guarda quien lo pidio y a que direccion, y de aca sale el limite de
-- envios por factura y por usuario (FacturaCorreoService.enviarManual).
--
--   ENVIANDO  anotado antes de enviar
--   ENVIADO   el servidor de correo acepto el mensaje
--   ERROR     fallo el envio
--
-- Aditivo e idempotente. Central-only: NO se registra en
-- configuraciones.replication_table, asi que no entra en ninguna publicacion.
-- Sin FK a factura_legal: llega por replicacion y un DELETE replicado no
-- puede chocar con esta tabla (mismo criterio que V240.1).
-- =====================================================================

CREATE TABLE IF NOT EXISTS financiero.factura_legal_correo_manual (
    id                bigserial PRIMARY KEY,
    factura_legal_id  bigint NOT NULL,
    sucursal_id       bigint NOT NULL,
    email             varchar(255) NOT NULL,
    usuario_id        bigint NOT NULL,
    estado            varchar(10) NOT NULL,
    error             varchar(500),
    creado_en         timestamp NOT NULL DEFAULT now(),
    CONSTRAINT ck_factura_legal_correo_manual_estado CHECK (estado IN ('ENVIANDO', 'ENVIADO', 'ERROR'))
);

CREATE INDEX IF NOT EXISTS idx_factura_legal_correo_manual_factura
    ON financiero.factura_legal_correo_manual (factura_legal_id, sucursal_id, creado_en);

CREATE INDEX IF NOT EXISTS idx_factura_legal_correo_manual_usuario
    ON financiero.factura_legal_correo_manual (usuario_id, creado_en);
