package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Configuracion del modulo de devoluciones (fila unica id=1).
 * Fuente de verdad de las preferencias que hoy estaban hardcodeadas; la leen
 * tanto el backend como el desktop. Los defaults reproducen la conducta previa.
 *
 * Usado en:
 * - Desktop: Si (dialogo de configuracion + dashboard/retiro/aviso de compras)
 * - Mobile: No (por ahora)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "devolucion_configuracion", schema = "operaciones")
public class DevolucionConfiguracion implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    /** Fila unica. Siempre id = 1. */
    @Id
    @Column(name = "id")
    private Long id;

    // ---- Dashboard / umbrales ----
    @Column(name = "dias_estancada")
    private Integer diasEstancada;

    @Column(name = "dias_estancada_urgente")
    private Integer diasEstancadaUrgente;

    @Column(name = "ranking_top_n")
    private Integer rankingTopN;

    /** MES_ACTUAL | ULTIMOS_30 | MES_ANTERIOR */
    @Column(name = "dashboard_rango_default")
    private String dashboardRangoDefault;

    // ---- Impresion ----
    @Column(name = "ticket_ancho_mm")
    private Integer ticketAnchoMm;

    @Column(name = "impresora_ticket")
    private String impresoraTicket;

    @Column(name = "comprobante_titulo")
    private String comprobanteTitulo;

    @Column(name = "comprobante_texto_firma")
    private String comprobanteTextoFirma;

    // ---- Merma / gasto ----
    /** Si true, solo genera gasto de merma para items cuyo motivo tiene generaGasto. */
    @Column(name = "merma_respeta_motivo")
    private Boolean mermaRespetaMotivo;

    @Column(name = "tipo_gasto_merma")
    private String tipoGastoMerma;

    // ---- Alerta de compras ----
    @Column(name = "alerta_incluye_retirado")
    private Boolean alertaIncluyeRetirado;

    @Column(name = "alerta_bloqueante")
    private Boolean alertaBloqueante;

    // ---- Stock ----
    /** Si true, permite separar/devolver aunque el stock quede negativo. */
    @Column(name = "permitir_stock_negativo")
    private Boolean permitirStockNegativo;

    // ---- Retiro ----
    /** Si true, permite verificar cajas en el retiro manualmente (sin escanear). */
    @Column(name = "retiro_permitir_seleccion_manual")
    private Boolean retiroPermitirSeleccionManual;
}
