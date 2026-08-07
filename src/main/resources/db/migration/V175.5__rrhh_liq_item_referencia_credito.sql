-- Cobro del credito por convenio en planilla (mensual + finiquito).
-- Los items de liquidacion que cobran una cuota de venta a credito
-- (referencia_tipo = 'CREDITO_CONVENIO_CUOTA') necesitan guardar:
--   - referencia_sucursal_id: la PK de venta_credito_cuota es compuesta (id + sucursal_id).
--   - referencia_estado_previo: estado del VentaCredito antes de cobrarlo, para revertir
--     el FINALIZADO al anular la liquidacion.
-- Aditivo y nullable. Solo tablas del schema rrhh (central-only, no replicado).

ALTER TABLE rrhh.liquidacion_item
  ADD COLUMN IF NOT EXISTS referencia_sucursal_id BIGINT,
  ADD COLUMN IF NOT EXISTS referencia_estado_previo VARCHAR(30);

ALTER TABLE rrhh.liquidacion_final_item
  ADD COLUMN IF NOT EXISTS referencia_sucursal_id BIGINT,
  ADD COLUMN IF NOT EXISTS referencia_estado_previo VARCHAR(30);
