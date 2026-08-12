-- Config de caja mayor (caja_virtual_configuracion): qué cuentas bancarias se muestran
-- en el sidebar del dashboard y en qué orden. Aditiva.
-- Portado del modelo de frc-gourmet (caja_mayor_config_cuentas_bancarias + cuentas_bancarias_orden).

-- Cuentas bancarias visibles (M:M), análoga a caja_virtual_config_forma_pago.
CREATE TABLE IF NOT EXISTS financiero.caja_virtual_config_cuenta_bancaria (
    configuracion_id   bigint NOT NULL REFERENCES financiero.caja_virtual_configuracion(id),
    cuenta_bancaria_id bigint NOT NULL REFERENCES financiero.cuenta_bancaria(id),
    PRIMARY KEY (configuracion_id, cuenta_bancaria_id)
);

-- Orden persistido de las cards (JSON array de IDs de cuenta bancaria).
ALTER TABLE financiero.caja_virtual_configuracion
    ADD COLUMN IF NOT EXISTS cuentas_bancarias_orden text;
