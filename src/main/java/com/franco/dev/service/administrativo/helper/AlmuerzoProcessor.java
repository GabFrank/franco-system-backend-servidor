package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AlmuerzoProcessor {

    private static final Logger log = LoggerFactory.getLogger(AlmuerzoProcessor.class);

    public void procesar(Jornada jornada, Marcacion marcacion) {
        if (marcacion.getTipo() == TipoMarcacion.ENTRADA) {
            handleEntrada(jornada, marcacion);
        } else if (marcacion.getTipo() == TipoMarcacion.SALIDA) {
            handleSalida(jornada, marcacion);
        }
    }

    private void handleEntrada(Jornada jornada, Marcacion marcacion) {
        if (jornada.getMarcacionEntrada() == null) {
            jornada.setMarcacionEntrada(marcacion);
        } else if (jornada.getMarcacionSalidaAlmuerzo() != null && jornada.getMarcacionEntradaAlmuerzo() == null) {
            jornada.setMarcacionEntradaAlmuerzo(marcacion);
        }
    }

    private void handleSalida(Jornada jornada, Marcacion marcacion) {
        if (jornada.getMarcacionEntrada() != null && jornada.getMarcacionSalidaAlmuerzo() == null) {
            if (Boolean.TRUE.equals(marcacion.getEsSalidaAlmuerzo())) {
                jornada.setMarcacionSalidaAlmuerzo(marcacion);
            } else {
                jornada.setMarcacionSalida(marcacion);
                jornada.setEstado(EstadoJornada.NORMAL);
            }
        } else if (jornada.getMarcacionEntradaAlmuerzo() != null && jornada.getMarcacionSalida() == null) {
            jornada.setMarcacionSalida(marcacion);
            jornada.setEstado(EstadoJornada.NORMAL);
        } else if (jornada.getMarcacionEntrada() == null) {
            log.error(
                    "Salida sin jornada con entrada previa. Jornada id={}, usuarioId={}, marcacionId={}",
                    jornada.getId(),
                    jornada.getUsuario() != null ? jornada.getUsuario().getId() : null,
                    marcacion.getId());
        }
    }
}
