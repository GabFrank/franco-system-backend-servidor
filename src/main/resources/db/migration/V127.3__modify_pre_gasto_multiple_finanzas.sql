-- Agregar nuevos campos a pre_gasto
ALTER TABLE financiero.pre_gasto ADD COLUMN IF NOT EXISTS beneficiario_proveedor_id BIGINT REFERENCES personas.proveedor(id);
ALTER TABLE financiero.pre_gasto ADD COLUMN IF NOT EXISTS beneficiario_persona_id BIGINT REFERENCES personas.persona(id);
ALTER TABLE financiero.pre_gasto ADD COLUMN IF NOT EXISTS fecha_vencimiento TIMESTAMP WITH TIME ZONE;
ALTER TABLE financiero.pre_gasto ADD COLUMN IF NOT EXISTS nivel_urgencia VARCHAR(50);
ALTER TABLE financiero.pre_gasto ADD COLUMN IF NOT EXISTS observaciones VARCHAR(1000);

-- Crear tabla detalle de financiación (múltiples monedas y formas de pago)
CREATE TABLE IF NOT EXISTS financiero.pre_gasto_detalle_finanzas (
    id BIGSERIAL PRIMARY KEY,
    pre_gasto_id BIGINT NOT NULL,
    sucursal_id BIGINT NOT NULL,
    moneda_id BIGINT REFERENCES financiero.moneda(id),
    forma_pago VARCHAR(100),
    monto NUMERIC(15,2),
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (pre_gasto_id, sucursal_id) REFERENCES financiero.pre_gasto(id, sucursal_id) ON DELETE CASCADE
);

-- Indices para la nueva tabla y campos
CREATE INDEX IF NOT EXISTS idx_pre_gasto_detalle_finanzas_fk ON financiero.pre_gasto_detalle_finanzas(pre_gasto_id, sucursal_id);
