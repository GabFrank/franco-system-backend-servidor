package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.enums.PagoDetalleEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "pago_detalle_estado",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "pago_detalle", schema = "operaciones")
public class PagoDetalle implements Identifiable<Long> {

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
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = false)
    private Moneda moneda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pago_id", nullable = false)
    private FormaPago formaPago;

    @Column(name = "total", nullable = false)
    private Double total;

    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "caja_id", referencedColumnName = "id"),
            @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id")
    })
    private PdvCaja caja;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "fecha_programado")
    private LocalDateTime fechaProgramado;
    
    @Column(name = "plazo")
    private Boolean plazo;
    
    @Column(name = "cuotas")
    private Integer cuotas;
    
    @Enumerated(EnumType.STRING)
    @Type(type = "pago_detalle_estado")
    @Column(name = "estado", nullable = false)
    private PagoDetalleEstado estado = PagoDetalleEstado.ABIERTO;
}

