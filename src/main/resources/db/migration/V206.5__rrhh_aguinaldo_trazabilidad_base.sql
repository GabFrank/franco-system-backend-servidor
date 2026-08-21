-- Trazabilidad del recalculo de aguinaldo.
--
-- calcularAguinaldosAnio() es idempotente por (funcionario, anio): al correrlo PISA
-- monto_calculado de los que estan en CALCULADO. Hasta ahora eso no dejaba ningun rastro:
-- si la formula nueva resultara equivocada, el monto viejo no se puede recuperar -- el
-- rollback del JAR no rollbackea un dato ya escrito.
--
-- monto_anterior guarda lo que habia antes de cada recalculo. Es la unica forma de
-- deshacer uno.
--
-- Los otros tres campos existen para poder explicar un monto sin reconstruirlo a mano
-- cuando un funcionario lo reclama: de que fuente salio, sobre cuantos meses, y cuando.

ALTER TABLE rrhh.aguinaldo
    ADD COLUMN IF NOT EXISTS origen_base VARCHAR(20),
    ADD COLUMN IF NOT EXISTS meses_con_liquidacion INTEGER,
    ADD COLUMN IF NOT EXISTS monto_anterior NUMERIC,
    ADD COLUMN IF NOT EXISTS recalculado_en TIMESTAMP;

COMMENT ON COLUMN rrhh.aguinaldo.origen_base IS
    'PERCIBIDO = suma de liquidaciones del anio; SUELDO_ACTUAL = fallback sin liquidaciones.';
COMMENT ON COLUMN rrhh.aguinaldo.monto_anterior IS
    'monto_calculado previo al ultimo recalculo. Permite deshacer un recalculo equivocado.';

-- Nullable a proposito: las filas historicas no tienen de donde sacar estos valores y
-- ponerles algo inventado seria peor que dejarlas vacias. Un origen_base NULL significa
-- "calculado antes de que esto existiera".
