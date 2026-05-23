package com.franco.dev.graphql.operaciones.input;

import lombok.Data;
import java.util.List;

@Data
public class HojaRutaInput {
    private Long id;
    private Long vehiculoId;
    private Long choferId;
    private String fechaSalida;
    private String fechaLlegada;
    private Double kmSalida;
    private Double kmLlegada;
    private String estado;
    private List<Long> acompanantesIds;
}
