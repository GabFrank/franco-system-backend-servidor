-- Enum tipo de local para sucursal
CREATE TYPE empresarial.tipo_local AS ENUM ('VENTA', 'DEPOSITO', 'ADMINISTRATIVO', 'VIRTUAL');

-- Agregar campos a sucursal
ALTER TABLE empresarial.sucursal
    ADD COLUMN tipo_local empresarial.tipo_local DEFAULT 'VENTA',
    ADD COLUMN manejo_stock BOOLEAN DEFAULT TRUE;

-- Migrar datos existentes: sucursales marcadas como depósito
UPDATE empresarial.sucursal SET tipo_local = 'DEPOSITO' WHERE deposito = TRUE;
