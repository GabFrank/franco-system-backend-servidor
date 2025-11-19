package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class TimbradoInput {
    private Long id;

    private String razonSocial;

    private String ruc;

    private String numero;

    private String fechaInicio;

    private String fechaFin;

    private Boolean isElectronico;

    private String csc;

    private String cscId;

    private String email;

    private String tipoSociedad;
    
    private String domicilioFiscalDepartamento;
    
    private String domicilioFiscalCiudad;
    
    private String domicilioFiscalCodigoCiudad;
    
    private String domicilioFiscalLocalidad;
    
    private String domicilioFiscalBarrio;
    
    private String domicilioFiscalDireccion;
    
    private String telefono;
    
    private String codActividadEconomicaPrincipal;
    
    private String descActividadEconomicaPrincipal;
    
    private String listCodigoActividadEconomicaSecundaria;
    
    private String listDescripcionActividadEconomicaSecundaria;

    private Boolean activo;

    private Long usuarioId;

    private String creadoEn;
}
