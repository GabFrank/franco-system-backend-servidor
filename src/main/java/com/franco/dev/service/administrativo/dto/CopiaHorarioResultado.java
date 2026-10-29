package com.franco.dev.service.administrativo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Resultado de copiar un horario a varios funcionarios ("Asignar a otros", #523). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CopiaHorarioResultado {
    private int copiados;
    /** Uno por funcionario al que no se le pudo copiar, con el motivo. */
    private List<String> errores = new ArrayList<>();
}
