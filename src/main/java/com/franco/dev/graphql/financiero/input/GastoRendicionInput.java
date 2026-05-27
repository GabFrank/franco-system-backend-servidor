package com.franco.dev.graphql.financiero.input;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class GastoRendicionInput {
    private Long id;
    private Long preGastoId;
    private Long sucursalId;
    private Long tipoGastoId;
    private BigDecimal montoTotal;
    private String fotoFacturaUrl;
    private String fotoProductoUrl;
    private Long enteId;
    private Long gasolineraId;
    private BigDecimal kmActual;
    private BigDecimal litros;
    private BigDecimal precioPorLitro;
    private String ubicacionProvisoria;
    private List<Long> funcionariosComensalesIds;
    private String establecimientoAlimentacion;
    private Long usuarioId;
}
