package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuracion del cobro del PDV: una sola fila, MAIN_TO_ALL (V251.1). La escribe
 * {@code saveConfiguracionCobro}; el cobro del desktop la lee de la copia replicada en el filial.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_cobro", schema = "financiero")
public class ConfiguracionCobro implements Identifiable<Long> {

    /** El 20% que estaba clavado en el boton de aumento del desktop. */
    public static final BigDecimal TOPE_DEFAULT = new BigDecimal("20");

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aumento_con_tope", nullable = false)
    private Boolean aumentoConTope = true;

    @Column(name = "aumento_tope_porcentaje", nullable = false)
    private BigDecimal aumentoTopePorcentaje = TOPE_DEFAULT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;
}
