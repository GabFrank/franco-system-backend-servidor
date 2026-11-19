-- Estado del proveedor: un inactivo deja de aparecer en los buscadores (compras, gastos, solicitud de pago, PWA).
-- personas.proveedor no se replica a las filiales: la columna vive solo en el central.
ALTER TABLE personas.proveedor ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
