package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.CategoriaDiferenciaRetiro;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Una moneda dentro de una verificación: qué declaró el PDV, qué contó tesorería y la
 * diferencia.
 *
 * La comparación es <b>por moneda</b> y nunca por total convertido. Un retiro puede cerrar en
 * el total y tener 100 R$ de menos con su equivalente de más en guaraníes: eso no es un retiro
 * correcto, es un cambio informal hecho en el camino.
 *
 * La categoría vive acá y no en la cabecera porque un mismo retiro puede ser FALTANTE en una
 * moneda y SOBRANTE en otra a la vez.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "retiro_verificacion_detalle", schema = "financiero")
public class RetiroVerificacionDetalle implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificacion_id", nullable = false)
    private RetiroVerificacion verificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = false)
    private Moneda moneda;

    /** Lo que dice el retiro_detalle del PDV. Se copia acá para que quede congelado. */
    @Column(name = "declarado", nullable = false)
    private BigDecimal declarado;

    /** Lo que contó tesorería. Es lo que se acredita en la caja mayor. */
    @Column(name = "contado", nullable = false)
    private BigDecimal contado;

    /** contado − declarado. Negativo = faltante, positivo = sobrante. */
    @Column(name = "diferencia", nullable = false)
    private BigDecimal diferencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 30)
    private CategoriaDiferenciaRetiro categoria;
}
