package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "solicitud_pago_detalle", schema = "operaciones")
public class SolicitudPagoDetalle implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(
            name = "assigned-identity",
            strategy = "com.franco.dev.config.AssignedIdentityGenerator"
    )
    @GeneratedValue(
            generator = "assigned-identity",
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_pago_id", nullable = false)
    private SolicitudPago solicitudPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = false)
    private Moneda moneda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pago_id", nullable = false)
    private FormaPago formaPago;

    @Column(nullable = false)
    private Double valor;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(length = 4000)
    private String observacion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    private Integer orden;

    @Column(name = "cotizacion")
    private Double cotizacion;

    @Column(name = "fecha_emision_cheque")
    private LocalDate fechaEmisionCheque;

    @Column(name = "portador", length = 255)
    private String portador;

    @Column(name = "nominal")
    private Boolean nominal = true;

    @Column(name = "diferido")
    private Boolean diferido = true;
}
