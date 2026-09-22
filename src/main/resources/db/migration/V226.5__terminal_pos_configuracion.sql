-- =====================================================================
-- terminal_pos: configuracion por aparato
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La configuracion general del modulo (V222.5) vale para toda la empresa: si el registro es
-- obligatorio, cuanta diferencia de monto se tolera, cuanto vive el QR de captura. Lo que no cubre
-- es que un aparato puntual necesite otra cosa.
--
-- 1. `carga_manual_permitida`: hay terminales donde tipear el cupon a mano es aceptable y otras
--    donde no --porque el cajero tiene el lector al lado y tipear es la puerta de entrada al
--    error--. Es por aparato, no por empresa.
--
-- 2. `campos_obligatorios`: que campos no se pueden dejar vacios al registrar la venta de ESTE
--    aparato. Hoy eso se deduce del `mapeo` del formato, que es del modelo; esto permite APRETARLO
--    en una terminal puntual sin tocar el formato que comparten las demas.
--
-- ⚠️ EL INTERRUPTOR NO PUEDE APAGAR EL ULTIMO CAMINO
--
-- Restriccion de §5.3.d del plan, y el motivo por el que este item no es solo dos columnas. El tipo
-- del formato ya cierra caminos: WEB no ofrece camara, MAQUINA no ofrece lector. La carga a mano es
-- lo que hace SEGURO cerrar el camino equivocado --es la salida universal-- asi que un interruptor
-- que la apague puede dejar a una caja sin ninguna forma de cobrar con tarjeta, sin que nadie
-- avise: venta PENDIENTE y caja que no cierra.
--
-- La validacion vive en central, que es quien escribe, y no en un CHECK: depende de si la terminal
-- tiene formato y de que tipo es, o sea de OTRA columna y de OTRA tabla. Un CHECK no puede mirar
-- eso, y un trigger daria un error de Postgres en vez de una frase.
--
-- Y `campos_obligatorios` solo puede APRETAR: la lista tiene que contener todos los que el mapeo
-- del formato ya declara obligatorios. Si pudiera aflojarlos, la configuracion por POS seria una
-- forma de saltear la validacion del formato desde una pantalla que parece menor.
--
-- ⚠️ ORDEN DE DESPLIEGUE --- EL ESPEJO DEL FILIAL (V100.5) VA ANTES QUE ESTA
--
-- Mismo caso que V224.5, por el mismo motivo: son columnas nuevas sobre financiero.terminal_pos,
-- que es MAIN_TO_ALL y ya esta viva replicando con publicacion de fila completa (prattrs IS NULL).
-- Si central va primero, la siguiente escritura manda columnas que la filial no tiene y el apply
-- worker se detiene.
--
-- EL DEFAULT DE carga_manual_permitida ES NULL, NO true
--
-- NULL significa "lo que diga la configuracion general". Poner true seria decidir por todas las
-- terminales de la flota en una migracion, y ademas dejaria sin significado la herencia.
-- =====================================================================

ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS carga_manual_permitida BOOLEAN NULL;

ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS campos_obligatorios TEXT NULL;

COMMENT ON COLUMN financiero.terminal_pos.carga_manual_permitida IS
    'NULL = hereda la configuracion general. true/false = decidido para este aparato. No puede dejar a la terminal sin ningun camino para cobrar: lo valida TerminalPosService.';

COMMENT ON COLUMN financiero.terminal_pos.campos_obligatorios IS
    'JSON array con los campos que no se pueden dejar vacios al registrar la venta de este aparato. NULL = se deduce del mapeo del formato. Solo puede apretar: tiene que contener a los que el mapeo ya declara obligatorios.';
