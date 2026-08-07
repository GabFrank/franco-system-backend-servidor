-- Marca un gasto de cajero como cancelado. Solo el central escribe esta columna
-- (mutation cancelarGasto); la filial la recibe por replicacion y la usa para
-- descontar el gasto de su propio generarBalance.
--
-- Nullable a proposito: NULL = no cancelado, y asi los gastos historicos no
-- necesitan backfill. Por eso el chequeo es Boolean.TRUE.equals(...) en Java y
-- IS NOT TRUE en SQL, nunca "= false".
ALTER TABLE financiero.gasto ADD COLUMN IF NOT EXISTS cancelado BOOLEAN;
