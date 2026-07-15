-- Backfill del vinculo inverso gasto -> pre_gasto.
-- El retiro (PreGastoService.ejecutarRetiro) ahora setea gasto.pre_gasto_id /
-- pre_gasto_sucursal_id, pero los gastos historicos quedaron con esas columnas
-- en NULL (el vinculo solo se guardaba en pre_gasto.gasto_caja_registro_id).
-- Se reconstruye el vinculo faltante a partir de esa referencia para que
-- completar() y la navegacion gasto -> solicitud funcionen sobre datos previos.
UPDATE financiero.gasto g
SET pre_gasto_id = p.id,
    pre_gasto_sucursal_id = p.sucursal_id
FROM financiero.pre_gasto p
WHERE p.gasto_caja_registro_id = g.id
  AND p.sucursal_id = g.sucursal_id
  AND g.pre_gasto_id IS NULL;
