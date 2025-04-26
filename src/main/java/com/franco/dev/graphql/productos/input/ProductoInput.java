package com.franco.dev.graphql.productos.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.rabbit.RabbitEntity;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

import static com.franco.dev.utilitarios.DateUtils.dateToString;

@Data
public class ProductoInput extends RabbitEntity {

    private Long id;
    private Boolean propagado;
    private String descripcion;
    private String descripcionFactura;
    private Integer iva;
    private Integer unidadPorCaja;
    private Integer unidadPorCajaSecundaria;
    private Boolean balanza;
    private Boolean garantia;
    private Integer tiempoGarantia;
    private Boolean ingredientes;
    private Boolean combo;
    private Boolean stock;
    private Boolean cambiable;
    private Boolean isEnvase;
    private Boolean promocion;
    private Boolean vencimiento;
    private Integer diasVencimiento;
    private Long usuarioId;
    private String imagenes;
    private TipoConservacion tipoConservacion;
    private Long subfamiliaId;
    private Long envaseId;
    private Boolean activo;
    private String creadoEn;

    public ProductoInput converHashMapToInput(Producto obj){
        ObjectMapper mapper = new ObjectMapper();
        ProductoInput input = mapper.convertValue(obj, ProductoInput.class);
        if(obj.getCreadoEn()!=null) input.setCreadoEn(dateToString(obj.getCreadoEn()));
        return input;
    }

}
