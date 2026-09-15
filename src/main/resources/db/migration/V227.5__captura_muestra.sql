-- =====================================================================
-- captura_muestra: las fotos de cupon con las que se configuro un formato
-- =====================================================================
-- QUE CAMBIA Y POR QUE
--
-- Hasta aca la muestra era deliberadamente efimera: vivia en un Map en memoria de central, los
-- bytes del JPEG se descartaban apenas corria el OCR, y un reinicio se las llevaba. El motivo era
-- bueno --una muestra sirve los minutos que dura configurar un formato-- pero dejo un hueco que se
-- ve recien al usarlo: NO SE PUEDE VOLVER A MIRAR EL CUPON. Ni para entender por que una region
-- quedo donde quedo, ni para comparar el mapa contra el ticket que lo produjo, ni para derivar de
-- nuevo sin volver a la caja con el telefono.
--
-- Esta tabla guarda la muestra. La imagen va al disco (`frc.captura-muestra.ruta-imagenes`,
-- default `muestras/AAAA/MM/<id>.jpg`) y la fila guarda el resto: que formato, cuando, que leyo el
-- OCR, cuanto tardo y el tamano en pixeles --que es lo que hace falta para poder dibujar las
-- regiones encima sin volver a abrir el archivo--.
--
-- ES EL CORPUS, NO SOLO UNA COMODIDAD
--
-- El plan de la etapa 6 (el asistente que propone el patron) necesita N cupones reales del mismo
-- modelo. Esta tabla es ese corpus, y se llena solo con el uso normal del ABM.
--
-- CENTRAL-ONLY, NO SE REPLICA
--
-- No entra en `configuraciones.replication_table` a proposito. El ABM de formatos es de central; a
-- las filiales les baja el resultado --las regiones, que son MAIN_TO_ALL-- y no la evidencia. Son
-- fotos: replicarlas costaria ancho de banda en 24 filiales para algo que solo se mira acá.
--
-- SE PURGA
--
-- Una foto de cupon son ~200 KB y esto crece con cada derivacion. `CapturaMuestraPurgaScheduler`
-- borra fila y archivo pasados `frc.captura-muestra.dias-retencion` dias (default 180).

CREATE TABLE IF NOT EXISTS financiero.captura_muestra (
    id                       BIGSERIAL PRIMARY KEY,
    formato_terminal_pos_id  BIGINT       NOT NULL REFERENCES financiero.formato_terminal_pos (id),
    creado_en                TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- El token de la captura. Se guarda para poder rastrear una muestra desde los logs; no se usa
    -- para leerla --vence en 20 minutos y la fila vive meses--.
    token                    VARCHAR(64),
    -- Ruta relativa al directorio configurado. Relativa y no absoluta: el directorio cambia entre
    -- el equipo de desarrollo y el servidor, y una ruta absoluta guardada haria que las fotos
    -- dejaran de encontrarse al mover la instancia.
    ruta_imagen              VARCHAR(500),
    -- Tamano en pixeles de la foto. Las regiones estan normalizadas 0..1, asi que esto es lo que
    -- permite dibujarlas encima de la imagen sin volver a abrirla.
    ancho                    INTEGER,
    alto                     INTEGER,
    texto_ocr                TEXT,
    ms_ocr                   INTEGER,
    usuario_id               BIGINT
);

-- El acceso real: las muestras de un formato, la mas nueva primero.
CREATE INDEX IF NOT EXISTS idx_captura_muestra_formato
    ON financiero.captura_muestra (formato_terminal_pos_id, creado_en DESC);

-- Para la purga por antiguedad.
CREATE INDEX IF NOT EXISTS idx_captura_muestra_creado_en
    ON financiero.captura_muestra (creado_en);
