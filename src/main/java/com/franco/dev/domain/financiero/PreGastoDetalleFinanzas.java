package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pre_gasto_detalle_finanzas", schema = "financiero")
public class PreGastoDetalleFinanzas implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "pre_gasto_id", referencedColumnName = "id"),
            @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id")
    })
    private PreGasto preGasto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id")
    private Moneda moneda;

    @Column(name = "forma_pago")
    private String formaPago;

    @Column(name = "monto")
    private BigDecimal monto;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
