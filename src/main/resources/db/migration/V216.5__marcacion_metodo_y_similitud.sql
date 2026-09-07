-- Con que metodo se registro cada marcacion, y con cuanta confianza.
--
-- La marcacion guardaba buena evidencia de DONDE (latitud, longitud, precision_gps,
-- distancia_sucursal) y ninguna de COMO se identifico a la persona. Con el kiosco de
-- marcacion facial 1:N eso deja de ser un detalle: un falso positivo registra asistencia
-- a nombre de quien no estuvo, y sin estas columnas es indistinguible de un olvido.
--
-- El margen contra el segundo candidato es el mas importante de los tres. Una similitud
-- de 0,71 contra un segundo de 0,45 identifica; la misma 0,71 contra un 0,69 es una
-- moneda al aire. El numero absoluto no distingue los dos casos; el margen si, y permite
-- fijar el umbral con evidencia de esta poblacion --estas caras, estas camaras, esta
-- luz-- en vez de copiarlo de un paper.
--
-- Aditiva: ningun DROP ni RENAME. Las tres columnas son opcionales, asi que el desktop
-- --que usa saveMarcacion y no las manda-- sigue funcionando sin cambios.
--
-- ATENCION -- ESTA MIGRACION TIENE QUE CORRER TAMBIEN EN LAS FILIALES.
-- `administrativo.marcacion` se replica en las dos direcciones: sube por BRANCH_TO_MAIN
-- (ver V112) y baja por las publicaciones `central_filialN_pub`, que no llevan lista de
-- columnas. Un publisher manda todas las columnas de la tabla: si el central gana una que
-- la filial no tiene, la replicacion hacia esa filial se corta con "logical replication
-- target relation is missing replicated column". Las columnas extra del lado del
-- suscriptor no molestan; las que faltan, si.
--
-- Y ojo con el orden: sin `out-of-order` en Flyway, una filial que ya paso el
-- installed_rank de este numero saltea la migracion en silencio y el corte aparece
-- despues, en runtime.

ALTER TABLE administrativo.marcacion
    ADD COLUMN IF NOT EXISTS metodo_registro VARCHAR(30);

-- La similitud del match aceptado, 0..1. Null cuando no hubo rostro (metodo MANUAL).
ALTER TABLE administrativo.marcacion
    ADD COLUMN IF NOT EXISTS similitud_facial REAL;

-- Cuanto le saco al segundo candidato. Null cuando no hubo segundo contra quien comparar
-- --un solo enrolado-- o cuando el metodo no fue 1:N. No se rellena con 0 ni con 1:
-- inventar la medicion es peor que no tenerla.
ALTER TABLE administrativo.marcacion
    ADD COLUMN IF NOT EXISTS margen_segundo_candidato REAL;

COMMENT ON COLUMN administrativo.marcacion.metodo_registro IS
    'MANUAL | FACIAL_1A1 | FACIAL_1AN_KIOSCO. Texto y no enum de Postgres: la tabla se replica a las filiales y un tipo nuevo habria que crearlo en cada una.';
COMMENT ON COLUMN administrativo.marcacion.margen_segundo_candidato IS
    'similitud del mejor menos la del segundo. Es el dato que dice si el 1:N fue solido o una moneda al aire.';
