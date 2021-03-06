package com.franco.dev.rabbit.dto;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.rabbit.RabbitEntity;
import com.franco.dev.rabbit.enums.TipoAccion;
import com.franco.dev.rabbit.enums.TipoEntidad;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RabbitDto<T> implements Serializable {

    private TipoAccion tipoAccion;
    private TipoEntidad tipoEntidad;
    private T entidad;
    private Long idSucursalOrigen;

    public RabbitDto(T entidad, TipoAccion tipoAccion, TipoEntidad tipoEntidad, Long idSucursalOrigen){
        this.entidad = entidad;
        this.tipoAccion = tipoAccion;
        this.tipoEntidad = tipoEntidad;
        this.idSucursalOrigen = idSucursalOrigen;
    }

    public RabbitDto(T entidad, TipoAccion tipoAccion, TipoEntidad tipoEntidad){
        this.entidad = entidad;
        this.tipoAccion = tipoAccion;
        this.tipoEntidad = tipoEntidad;
    }
}
