package com.franco.dev.domain.administrativo;

import com.franco.dev.domain.administrativo.enums.AccionMarcacionPendiente;
import lombok.Data;

@Data
public class EstadoMarcacionUsuario {

    private Jornada jornadaRelevante;
    private AccionMarcacionPendiente accionPendiente;
    private boolean puedeMarcarEntrada;
    private boolean puedeMarcarSalida;
    private boolean puedeMarcarSalidaAlmuerzo;
    private boolean puedeMarcarEntradaAlmuerzo;
    private boolean estaEnJornada;
}
