package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.MetodoVerificacion;
import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import com.franco.dev.domain.operaciones.enums.MotivoVerificacionManual;
import lombok.Data;
import java.util.List;

@Data
public class RecepcionMercaderiaItemInput {
    private Long id;
    private Long recepcionMercaderiaId;
    private Long notaRecepcionItemId;
    private Long notaRecepcionItemDistribucionId;
    private Long productoId;
    private Long presentacionRecibidaId;
    private Long sucursalEntregaId;
    private Long usuarioId;
    private Double cantidadRecibida;
    private Double cantidadRechazada;
    private String vencimientoRecibido;
    /** Opcional. Si viene, pisa el calculo automatico de la fecha de retiro del lote. */
    private String fechaRetiro;
    private String lote;
    private Boolean esBonificacion;
    private MotivoRechazoFisico motivoRechazo;
    private String observaciones;
    private String creadoEn;
    private MetodoVerificacion metodoVerificacion;
    private MotivoVerificacionManual motivoVerificacionManual;
    private List<RecepcionMercaderiaItemVariacionInput> variaciones;
} 