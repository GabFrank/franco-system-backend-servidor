package com.franco.dev.domain.equipos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "equipo_financiero", schema = "equipos")
public class EquipoFinanciero implements Identifiable<Long> {
    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id", nullable = false, unique = true)
    private Equipo equipo;

    private BigDecimal costo;

    @Column(name = "valor_tasacion")
    private BigDecimal valorTasacion;

    @Column(name = "valor_tasacion_pyg")
    private BigDecimal valorTasacionPyg;

    @Column(name = "valor_tasacion_brl")
    private BigDecimal valorTasacionBrl;

    @Column(name = "situacion_pago")
    private String situacionPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id")
    private Moneda moneda;

    @Column(name = "monto_total")
    private BigDecimal montoTotal;

    @Column(name = "monto_ya_pagado")
    private BigDecimal montoYaPagado;

    @Column(name = "cantidad_cuotas")
    private Integer cantidadCuotas;

    @Column(name = "cantidad_cuotas_pagadas")
    private Integer cantidadCuotasPagadas;

    @Column(name = "dia_vencimiento")
    private Integer diaVencimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
