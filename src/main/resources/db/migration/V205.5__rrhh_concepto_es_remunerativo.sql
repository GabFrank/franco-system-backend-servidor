-- Que conceptos entran a la BASE REMUNERATIVA (aguinaldo, IPS del finiquito,
-- indemnizacion) deja de estar implicito en "todo item HABER" y pasa a ser un dato del
-- catalogo, editable sin deploy.
--
-- El problema que resuelve: total_haberes es un saco. Incluye el propio AGUINALDO
-- (LiquidacionSueldoService lo emite como item HABER), asi que calcular el aguinaldo
-- sumando total_haberes mete el aguinaldo del anio dentro de la base del aguinaldo del
-- anio. Lo mismo con VIATICO y REINTEGRO, que compensan un gasto o devuelven plata
-- adelantada: no retribuyen trabajo.
--
-- DEFAULT TRUE a proposito: cualquier codigo que hoy exista y no este en la lista de
-- abajo sigue contando igual que antes, asi que la migracion no cambia ningun monto por
-- si sola. Los tres UPDATE de abajo si lo hacen, y esa es la correccion buscada.
--
-- OJO al reverso de ese default: un concepto nuevo cargado sin marcar es_remunerativo =
-- false entra a la base sin que nadie lo note. Por eso el ABM pide el valor en el alta en
-- vez de dejarlo al default.
--
-- rrhh.* no esta en central_pub ni en configuraciones.replication_table: esta tabla no
-- replica a las filiales.

ALTER TABLE rrhh.liquidacion_concepto
    ADD COLUMN IF NOT EXISTS es_remunerativo BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN rrhh.liquidacion_concepto.es_remunerativo IS
    'Si el concepto suma a la base remunerativa (aguinaldo, IPS del finiquito, indemnizacion).';

UPDATE rrhh.liquidacion_concepto SET es_remunerativo = false
 WHERE codigo IN ('AGUINALDO', 'VIATICO', 'REINTEGRO');

-- Las comisiones no existian como concepto de haber: el unico COMISION del sistema es
-- PenalizacionTipo.COMISION_DESCUENTO, que es un descuento. Sin este concepto, una
-- comision solo puede cargarse como BONO_MANUAL y pierde su etiqueta en el recibo.
INSERT INTO rrhh.liquidacion_concepto (codigo, descripcion, es_haber, es_calculado_auto, es_remunerativo, activo, creado_en)
SELECT 'COMISION', 'COMISION', true, false, true, true, now()
WHERE NOT EXISTS (SELECT 1 FROM rrhh.liquidacion_concepto WHERE codigo = 'COMISION');
