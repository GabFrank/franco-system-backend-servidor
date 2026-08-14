-- =====================================================================
-- RRHH — Separa el aguinaldo devengado del proyectado.
-- =====================================================================
-- El calculo contaba 12 meses siempre que el funcionario hubiera ingresado
-- antes del anio en curso: nunca miraba la fecha actual. Calculado en julio
-- mostraba el aguinaldo completo como si ya estuviera ganado, cuando lo
-- devengado a esa altura es 7/12.
--
-- Ahora monto_calculado guarda lo DEVENGADO a la fecha de calculo, y
-- monto_proyectado lo que se va a deber en diciembre si nada cambia (util
-- para prever la caja: es uno de los egresos mas grandes del anio).
--
-- Aditiva. Backfill: para las filas ya calculadas el valor viejo ERA la
-- proyeccion a fin de anio, asi que se copia a monto_proyectado tal cual.
-- Idempotente.
-- =====================================================================

ALTER TABLE rrhh.aguinaldo
    ADD COLUMN IF NOT EXISTS monto_proyectado NUMERIC(18,2) NOT NULL DEFAULT 0;

ALTER TABLE rrhh.aguinaldo
    ADD COLUMN IF NOT EXISTS meses_proyectados INTEGER NOT NULL DEFAULT 0;

UPDATE rrhh.aguinaldo
SET monto_proyectado = monto_calculado,
    meses_proyectados = meses_trabajados
WHERE monto_proyectado = 0;
