package com.franco.dev.graphql.grafico.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Convenios de una sucursal en el central comparados contra la base de esa filial. */
@Data
public class ConvenioSucursalIntegridad {
    public static final String OK = "OK";
    public static final String DIFERENCIA = "DIFERENCIA";
    public static final String SIN_CONEXION = "SIN_CONEXION";
    public static final String SIN_IP = "SIN_IP";

    private Long sucursalId;
    private String sucursalNombre;
    private ConvenioConteo central;
    /** null si la filial no se pudo consultar. */
    private ConvenioConteo filial;
    private String estadoIntegridad;
    private String error;
    private int faltanEnCentral;
    private int sobranEnCentral;
    private int estadoDistinto;
    private List<Long> idsFaltanEnCentral = new ArrayList<>();
    private List<Long> idsSobranEnCentral = new ArrayList<>();
    private List<Long> idsEstadoDistinto = new ArrayList<>();
    private String ultimoCentral;
    private String ultimoFilial;
}
