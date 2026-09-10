package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.ProveedorServicio;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "terminal_pos", schema = "financiero")
public class TerminalPos implements Identifiable<Long> {

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

    private String descripcion;

    private String codigo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_bancaria_id", nullable = true)
    private CuentaBancaria cuentaBancaria;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @Column(name = "porcentaje_comision")
    private java.math.BigDecimal porcentajeComision;

    @Column(name = "minutos_acreditacion")
    private Integer minutosAcreditacion;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_servicio_id", nullable = true)
    private ProveedorServicio proveedorServicio;

    /**
     * Formato del modelo de aparato que es esta terminal: de aca sale el tipo (MAQUINA / WEB /
     * API), el patron y el mapeo.
     * <p>
     * NULL = sin configurar. El dia del corte lo estan TODAS las terminales de las 24 sucursales
     * --no hay backfill, la asignacion se completa a mano por SQL-- y el desktop bloquea la venta
     * con tarjeta mientras siga asi. Por eso ese SQL es prerrequisito de liberar la version del
     * desktop, no una tarea posterior.
     * <p>
     * Aca la FK SI existe en la base (V221.5): las dos tablas se escriben desde el mismo ABM, en la
     * misma base, sin carrera posible. En el espejo del filial no, porque alla bajan por streams de
     * replicacion distintos y sin orden garantizado entre ellos.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "formato_terminal_pos_id", nullable = true)
    private FormatoTerminalPos formatoTerminalPos;

    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
