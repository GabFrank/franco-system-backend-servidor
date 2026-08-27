-- Completa el catalogo rrhh.liquidacion_concepto, que existe desde V154.0 con backend
-- entero (entidad, repositorio, servicio, resolver y schema) pero nunca se cablearon a
-- nada: ningun servicio ni pantalla lo consultaba.
--
-- Dos grupos:
--
--   1. Codigos que el motor YA emite y que faltaban en la tabla. Sin ellos,
--      ReciboLiquidacionService.operacion() no los puede resolver por catalogo y hay que
--      dejar el switch hardcodeado -- que es la duplicacion que este cambio elimina.
--      Van con es_calculado_auto = true: no se ofrecen en el select de item manual.
--
--   2. Conceptos para carga MANUAL. Hoy el usuario solo elige HABER o DESCUENTO y el
--      backend estampa HABER_MANUAL / DESCUENTO_MANUAL, que en el recibo caen al default
--      y salen como "AJUSTE" para todo. Con el catalogo, la operacion elegida define la
--      etiqueta y el signo.
--
-- El seed del grupo 2 es un punto de partida, no una lista cerrada: la tabla es
-- administrable, asi que agregar o desactivar conceptos no requiere deploy. Ajustar con
-- RRHH y desactivar (activo = false) lo que no aplique -- no borrar, porque los items ya
-- emitidos referencian el codigo.

INSERT INTO rrhh.liquidacion_concepto (codigo, descripcion, es_haber, es_calculado_auto, activo, creado_en)
SELECT v.codigo, v.descripcion, v.es_haber, v.es_calculado_auto, true, now()
FROM (VALUES
    -- 1. Emitidos por el motor, faltaban en la tabla.
    ('JUSTIFICATIVO_DESCUENTO', 'DESCUENTO POR JUSTIFICATIVO', false, true),
    ('CREDITO_CONVENIO_CUOTA',  'CUOTA DE COMPRA A CREDITO',   false, true),
    -- Los dos genericos historicos: los items ya emitidos los referencian, asi que
    -- tienen que resolver por catalogo igual que el resto.
    ('HABER_MANUAL',            'AJUSTE (HABER)',              true,  false),
    ('DESCUENTO_MANUAL',        'AJUSTE (DESCUENTO)',          false, false),

    -- 2. Conceptos de carga manual.
    ('BONIFICACION',            'BONIFICACION',                true,  false),
    ('VIATICO',                 'VIATICO',                     true,  false),
    ('REINTEGRO',               'REINTEGRO',                   true,  false),
    ('DESCUENTO_JUDICIAL',      'DESCUENTO JUDICIAL',          false, false),
    ('FALTANTE_CAJA',           'FALTANTE DE CAJA',            false, false)
) AS v(codigo, descripcion, es_haber, es_calculado_auto)
WHERE NOT EXISTS (
    SELECT 1 FROM rrhh.liquidacion_concepto c WHERE c.codigo = v.codigo
);
