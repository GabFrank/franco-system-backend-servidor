-- =====================================================================
-- formato_terminal_pos_region: el mapa que convierte cajas del OCR en campos
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Hasta esta entrega el OCR guardaba el texto leido y nada mas: captura_cupon.texto_ocr se llenaba,
-- `campos` nunca. Ni `patron` ni `mapeo` del formato tenian un solo consumidor. O sea que el cajero
-- sacaba la foto, veia el texto en pantalla y lo transcribia a mano igual: el OCR era una lupa, no
-- un extractor.
--
-- Esta tabla es el mapa. Cada fila dice: en el formato F, el campo C se encuentra anclado a la
-- etiqueta E, en la posicion P respecto de ella, y tiene que ser del tipo T.
--
-- ANCLADO A LA ETIQUETA, NO A COORDENADAS ABSOLUTAS
--
-- La regla mas importante del diseno, y la que es facil perder al implementar. Un mapa por
-- coordenadas se rompe el dia que el proveedor agrega una linea al ticket --y se rompen TODOS los
-- mapas de ese modelo a la vez, sin que nadie entienda por que--. Anclado a la etiqueta sobrevive:
-- si "AUT:" se corrio 20px para abajo, el valor sigue estando a su derecha.
--
-- La geometria (x1..y2, normalizada 0..1) esta igual, pero como PISTA para acotar el
-- reconocimiento, no como verdad para asignar. La geometria achica el trabajo --reconocer 6 cajas
-- en vez de 26 baja `rec` de 3.841 a ~900 ms--, la etiqueta decide de quien es cada caja.
--
-- EL MAPA ES UN INTERPRETE, NO UNA TIJERA
--
-- Una sola pasada de OCR produce cajas con texto, coordenadas y confianza; el mapa ASIGNA cada caja
-- a un campo. No se recorta campo por campo: eso serian N inferencias en vez de una, y el tiempo
-- escalaria con la cantidad de campos.
--
-- CUELGAN DEL FORMATO, NO DE LA TERMINAL
--
-- El doc de dominio decia "por POS" porque se escribio antes de la etapa 3. Ahora que el formato es
-- del modelo de aparato, dos cajas con la misma maquinita comparten el mapa en vez de dibujarlo dos
-- veces --que era el problema real que "por POS" venia a resolver--.
--
-- ⚠️ ORDEN DE DESPLIEGUE: el espejo del filial (V99.5) va ANTES que esta. Es tabla nueva
-- MAIN_TO_ALL, asi que si se invierte el costo es acotado --el REFRESH PUBLICATION falla entero y
-- NO toca la suscripcion, medido el 2026-09-11 entre los clusters locales-- pero no hay motivo
-- para invertirlo.
--
-- ESTE ES EL LADO PUBLISHER
--
-- Aca van las restricciones: central es el unico lado que escribe y el ABM las necesita. En el
-- espejo del filial solo la PK, deliberadamente: un CHECK alla que este lado no comparta seria una
-- forma de cortar la replicacion, no una proteccion.
-- =====================================================================

CREATE TABLE IF NOT EXISTS financiero.formato_terminal_pos_region (
    id                        BIGSERIAL     NOT NULL,
    formato_terminal_pos_id   BIGINT        NOT NULL,
    -- El nombre del campo destino, EN camelCase y tal como aparece como clave del `mapeo` del
    -- formato: codigoAutorizacion, numeroBoleta, monto, terminal, identificadorTransaccion, moneda,
    -- o una clave libre (que termina en venta_tarjeta.datos_extra).
    --
    -- Tiene que coincidir con el mapeo: una region para un campo que el mapeo no produce no solo es
    -- peso muerto, ademas ACOTA el reconocimiento a una zona para nada. Lo valida el service, que
    -- puede leer el mapeo y dar una frase.
    campo                     VARCHAR(40)   NOT NULL,
    -- La etiqueta impresa que ancla la region: "AUT:", "MONTO", "TERMINAL". Es lo que se busca en
    -- el texto ya reconocido. NULL = sin etiqueta a la vista: la region se resuelve solo por
    -- geometria, y por lo tanto es la fragil ante un cambio de largo del ticket.
    etiqueta                  VARCHAR(120)  NULL,
    -- Donde esta el valor respecto de la etiqueta: DERECHA | ABAJO | DENTRO.
    posicion                  VARCHAR(20)   NULL,
    -- TEXTO | NUMERO | FECHA. Un campo declarado NUMERO rechaza un "0i64" del OCR gratis, que es el
    -- tipo de error que ni Java ni Python evitan solos.
    tipo                      VARCHAR(20)   NULL,
    obligatorio               BOOLEAN       NOT NULL DEFAULT false,
    -- Pista geometrica normalizada 0..1. NULL = sin pista, se resuelve solo por etiqueta.
    x1                        NUMERIC(6,5)  NULL,
    y1                        NUMERIC(6,5)  NULL,
    x2                        NUMERIC(6,5)  NULL,
    y2                        NUMERIC(6,5)  NULL,
    -- DERIVADA (salio de un cupon de muestra) | MANUAL (la corrigio una persona).
    --
    -- No es decorativo: decide si la derivacion la puede pisar. Una region MANUAL es el artefacto
    -- mas caro del modulo --alguien miro un cupon y la arreglo-- y una corrida de derivacion no
    -- puede llevarsela puesta.
    origen                    VARCHAR(20)   NOT NULL DEFAULT 'DERIVADA',
    orden                     INTEGER       NOT NULL DEFAULT 0,
    creado_en                 TIMESTAMP     NULL DEFAULT NOW(),
    CONSTRAINT formato_terminal_pos_region_pkey PRIMARY KEY (id),
    CONSTRAINT formato_terminal_pos_region_formato_fk
        FOREIGN KEY (formato_terminal_pos_id)
        REFERENCES financiero.formato_terminal_pos (id),
    CONSTRAINT formato_terminal_pos_region_posicion_check
        CHECK (posicion IS NULL OR posicion IN ('DERECHA', 'ABAJO', 'DENTRO')),
    CONSTRAINT formato_terminal_pos_region_tipo_check
        CHECK (tipo IS NULL OR tipo IN ('TEXTO', 'NUMERO', 'FECHA')),
    CONSTRAINT formato_terminal_pos_region_origen_check
        CHECK (origen IN ('DERIVADA', 'MANUAL')),
    -- Las coordenadas van las cuatro o ninguna. Media caja no es una pista: es una region que
    -- acota el reconocimiento a un rectangulo abierto, y el campo desaparece.
    CONSTRAINT formato_terminal_pos_region_caja_check
        CHECK ((x1 IS NULL AND y1 IS NULL AND x2 IS NULL AND y2 IS NULL)
            OR (x1 IS NOT NULL AND y1 IS NOT NULL AND x2 IS NOT NULL AND y2 IS NOT NULL
                AND x1 >= 0 AND y1 >= 0 AND x2 <= 1 AND y2 <= 1
                AND x1 < x2 AND y1 < y2))
);

-- Un campo, una region por formato. Dos regiones para `monto` en el mismo formato no es una
-- configuracion mas rica: es una ambiguedad que se resuelve por orden de lectura, o sea al azar.
CREATE UNIQUE INDEX IF NOT EXISTS uq_formato_terminal_pos_region_campo
    ON financiero.formato_terminal_pos_region (formato_terminal_pos_id, campo);

CREATE INDEX IF NOT EXISTS idx_formato_terminal_pos_region_formato
    ON financiero.formato_terminal_pos_region (formato_terminal_pos_id);

COMMENT ON TABLE financiero.formato_terminal_pos_region IS
    'El mapa de un formato: que caja del OCR es que campo. Anclado a la etiqueta impresa, no a coordenadas absolutas. Se administra en central y baja a las filiales.';
COMMENT ON COLUMN financiero.formato_terminal_pos_region.campo IS
    'Clave del mapeo del formato, en camelCase: codigoAutorizacion, numeroBoleta, monto, terminal, identificadorTransaccion, moneda, o una clave libre que cae en venta_tarjeta.datos_extra.';
COMMENT ON COLUMN financiero.formato_terminal_pos_region.etiqueta IS
    'La etiqueta impresa que ancla la region. Es lo que sobrevive a que el proveedor agregue una linea al ticket; las coordenadas no.';
COMMENT ON COLUMN financiero.formato_terminal_pos_region.origen IS
    'DERIVADA | MANUAL. Una region MANUAL no la pisa una corrida de derivacion: es una correccion que alguien hizo mirando un cupon.';

-- ── Replicacion ─────────────────────────────────────────────────────────────────────────────
--
-- MAIN_TO_ALL como formato_terminal_pos (V221.5): el mapa se administra en central y baja a todas
-- las filiales, que solo lo leen para asignar las cajas del OCR a campos. El filial nunca escribe
-- esta tabla, asi que no lleva replicate_central_to_branch_with_filter.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('financiero.formato_terminal_pos_region', 'MAIN_TO_ALL', 'Mapa de campos del formato de terminal POS', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;
