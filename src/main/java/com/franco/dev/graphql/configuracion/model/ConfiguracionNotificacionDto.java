package com.franco.dev.graphql.configuracion.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfiguracionNotificacionDto {
    private String tipo;
    private String descripcion;
    private Boolean habilitado;
    private Boolean esObligatorio;
}
