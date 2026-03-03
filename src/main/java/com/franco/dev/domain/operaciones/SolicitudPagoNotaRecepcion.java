package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "solicitud_pago_nota_recepcion", schema = "operaciones")
public class SolicitudPagoNotaRecepcion implements Identifiable<Long> {

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
    @JoinColumn(name = "nota_recepcion_id", nullable = false)
    private NotaRecepcion notaRecepcion;

    @Column(name = "monto_incluido", nullable = false)
    private Double montoIncluido;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}