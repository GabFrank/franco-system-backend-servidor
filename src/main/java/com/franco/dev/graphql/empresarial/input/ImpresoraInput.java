package com.franco.dev.graphql.empresarial.input;

import com.franco.dev.domain.empresarial.enums.PerfilPapel;
import com.franco.dev.domain.empresarial.enums.TipoConexion;
import com.franco.dev.domain.empresarial.enums.TipoImpresora;
import com.franco.dev.domain.empresarial.enums.UsoImpresora;
import lombok.Data;

@Data
public class ImpresoraInput {
    private Long id;
    private String nombre;
    private Boolean activo;
    private Boolean esPredeterminada;
    private Long sucursalId;
    private TipoImpresora tipo;
    private UsoImpresora uso;
    private TipoConexion conexion;
    private String colaCups;
    private String ip;
    private Integer puerto;
    private PerfilPapel perfilPapel;
    private Integer columnas;
    private Integer anchoMm;
    private Integer altoMm;
    private String marca;
    private String codepage;
    private Boolean compartidaEnCentral;
    private Long usuarioId;
}
