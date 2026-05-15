package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class JornadaFactory {

    public Jornada crearNuevaJornada(Marcacion marcacion, LocalDate fechaJornada) {
        Jornada jornada = new Jornada();
        jornada.setUsuario(marcacion.getUsuario());
        jornada.setFecha(fechaJornada);
        jornada.setEstado(EstadoJornada.INCOMPLETO);
        jornada.setSucursalId(marcacion.getSucursalId());
        return jornada;
    }
}
