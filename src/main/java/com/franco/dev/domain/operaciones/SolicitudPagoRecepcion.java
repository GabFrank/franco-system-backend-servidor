package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "solicitud_pago_recepcion", schema = "operaciones")
public class SolicitudPagoRecepcion implements Identifiable<Long> {

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
    @JoinColumn(name = "recepcion_mercaderia_id", nullable = false)
    private RecepcionMercaderia recepcionMercaderia;

    @Column(name = "monto_asignado")
    private Double montoAsignado;
} 