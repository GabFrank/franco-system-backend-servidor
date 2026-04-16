package com.franco.dev.domain.activos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Persona;
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
@Table(name = "mueble", schema = "activos")
public class Mueble implements Identifiable<Long> {
    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_id")
    private Persona propietario;

    private String identificador;

    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "familia_id")
    private FamiliaMueble familia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_mueble_id")
    private TipoMueble tipoMueble;

    @Column(name = "consume_energia")
    private Boolean consumeEnergia;

    @Column(name = "consumo_valor")
    private String consumoValor;

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
    private com.franco.dev.domain.financiero.Moneda moneda;

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