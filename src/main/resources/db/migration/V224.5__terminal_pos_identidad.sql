-- =====================================================================
-- terminal_pos: donde esta la maquina, y cual es
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La tabla no tiene sucursal_id. Con 24 sucursales y un proveedor que entrega 30 maquinas, no hay
-- forma de saber que aparato esta en que local. Y tampoco hay donde guardar el identificador
-- propio de la maquina, el que viene de fabrica y el que el propio cupon imprime.
--
-- Caso de uso: la maquina JF798SJJ del proveedor X esta en la sucursal Y, cobra a la cuenta Z, y su
-- cupon se lee con el formato W.
--
-- `codigo` NO sirve para eso: es la etiqueta interna que el negocio le pega al aparato para que el
-- cajero la escanee con el lector (scan-terminal-pos-dialog), y hoy esta vacia en las dos
-- terminales que existen. Son dos identificadores con dos vidas distintas.
--
-- POR QUE IMPORTA QUE `serie` EXISTA
--
-- El `mapeo` del formato ya declara `terminal` como campo canonico, o sea que el cupon ya imprime
-- el identificador del aparato. Hoy no hay contra que cotejarlo. Con `serie` cargada, el cupon dice
-- solo de que maquina salio: si trae JF798SJJ y esa maquina esta registrada en otra sucursal, el
-- sistema lo puede cantar.
--
-- ⚠️ ORDEN DE DESPLIEGUE --- EL ESPEJO DEL FILIAL (V98.5) VA ANTES QUE ESTA
--
-- Y no alcanza con que este MERGEADO: tiene que estar DESPLEGADO en toda la flota del canal.
--
-- financiero.terminal_pos es MAIN_TO_ALL y **ya esta viva replicando**, con publicacion de fila
-- completa --verificado: en central, pg_publication_rel.prattrs IS NULL para esta tabla--. Eso
-- significa que cada UPDATE manda la tupla entera, columnas nuevas incluidas. Si central agrega las
-- columnas primero, la siguiente escritura sobre terminal_pos --por ejemplo el SQL de asignacion de
-- formato-- manda dos columnas que la filial no tiene y el apply worker SE DETIENE. Es el mecanismo
-- del incidente de tipo_dispositivo del 2026-08-20.
--
-- Esto CONTRADICE el "central primero" general de la entrega, y es a proposito. La regla corta:
--
--   tabla nueva MAIN_TO_ALL              -> si se invierte, solo se pierde un REFRESH (medido)
--   COLUMNA nueva sobre MAIN_TO_ALL viva -> filial primero, SIN EXCEPCION: invertirlo corta
--   columna nueva sobre BRANCH_TO_MAIN   -> central primero, sin excepcion
--
-- ESTE ES EL LADO PUBLISHER
--
-- Por eso aca SI van la FK y los unicos: central es el unico lado que escribe esta tabla y el ABM
-- los necesita. En el espejo del filial no van, deliberadamente --alla una restriccion que este
-- lado no comparta seria un corte de replicacion, no un error de validacion--.
-- =====================================================================

ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL;

ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS serie VARCHAR(60) NULL;

-- Nullable a proposito y sin backfill: las filas existentes no tienen sucursal y NO SE PUEDE
-- ADIVINAR cual es. Un NOT NULL sobre datos existentes es justo lo que este repo prohibe hacer en
-- un solo paso. Se completa a mano desde el ABM.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'terminal_pos_sucursal_fk'
    ) THEN
        ALTER TABLE financiero.terminal_pos
            ADD CONSTRAINT terminal_pos_sucursal_fk
            FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal (id);
    END IF;
END $$;

-- ── Unicidad de la serie: DOS indices, no uno ───────────────────────────────────────────────
--
-- ⚠️ El diseno original era UNIQUE (proveedor_servicio_id, serie) WHERE serie IS NOT NULL, y eso
-- NO PROTEGE NADA en el caso real. Medido contra Postgres dentro de BEGIN; ... ROLLBACK;:
--
--   proveedor NULL + 'JF798SJJ'  ->  INSERT 0 1
--   proveedor NULL + 'JF798SJJ'  ->  INSERT 0 1     <- el duplicado ENTRO
--   proveedor 7    + 'AB123'     ->  rechazado correctamente
--
-- Postgres no compara NULLs como iguales: cuando la primera columna de la tupla es NULL la
-- unicidad nunca se evalua. Y no es hipotetico --las dos terminales que existen hoy tienen el
-- proveedor en NULL--, asi que el caso roto era exactamente el caso actual.
--
-- Mismo patron que V221.5 ya usa para formato_terminal_pos.
--
-- Los indices van sobre la columna cruda y no sobre UPPER(serie) porque TerminalPosService
-- normaliza la serie --trim + mayusculas-- antes de comparar y antes de guardar. Asi lo que el
-- indice compara es exactamente lo que el service compara, y no hay forma de que uno acepte lo que
-- el otro rechaza. (El de `codigo`, mas abajo, SI va sobre UPPER: ese campo no se normaliza al
-- guardar y la busqueda existente ya es findByCodigoIgnoreCase.)
CREATE UNIQUE INDEX IF NOT EXISTS uq_terminal_pos_proveedor_serie
    ON financiero.terminal_pos (proveedor_servicio_id, serie)
    WHERE proveedor_servicio_id IS NOT NULL AND serie IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_terminal_pos_serie_comodin
    ON financiero.terminal_pos (serie)
    WHERE proveedor_servicio_id IS NULL AND serie IS NOT NULL;

-- Los dos irian con CONCURRENTLY si la tabla creciera. Hoy tiene 2 filas y es catalogo, no
-- transaccional, asi que el lock es instantaneo. Se anota por disciplina, no por riesgo.

-- ── De paso, el `codigo` duplicado ──────────────────────────────────────────────────────────
--
-- Hueco encontrado al revisar esta seccion: `codigo` es lo que el cajero escanea para elegir la
-- terminal, y scan-terminal-pos-dialog hace onFilter(...) y se queda con resultados[0]. Dos
-- terminales con el mismo codigo y el cajero cobra contra la maquina equivocada, sin ningun aviso.
--
-- Hoy el campo esta VACIO en las dos terminales que existen, asi que el indice no puede fallar al
-- crearse: es el momento mas barato que va a haber para cerrarlo. Parcial sobre no-vacio, porque
-- "sin codigo" es el estado normal de una terminal que no se escanea.
CREATE UNIQUE INDEX IF NOT EXISTS uq_terminal_pos_codigo
    ON financiero.terminal_pos (UPPER(codigo))
    WHERE codigo IS NOT NULL AND codigo <> '';

CREATE INDEX IF NOT EXISTS idx_terminal_pos_sucursal
    ON financiero.terminal_pos (sucursal_id);

COMMENT ON COLUMN financiero.terminal_pos.sucursal_id IS
    'En que sucursal esta fisicamente el aparato. NULL en las filas viejas: no se puede adivinar, se completa a mano. La replicacion NO se filtra por esta columna; el caso de uso es de listado, no de aislamiento.';

COMMENT ON COLUMN financiero.terminal_pos.serie IS
    'Identificador propio de la maquina, el que viene de fabrica y el que el cupon imprime. Distinto de `codigo`, que es la etiqueta interna que el cajero escanea.';
