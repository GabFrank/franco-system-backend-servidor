package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuración de visibilidad de una caja mayor: qué formas de pago mostrar,
 * si se ven los resúmenes de cuentas por pagar/cobrar, y si están habilitadas
 * las operaciones financieras (reemplaza el feature-flag CN9).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "caja_virtual_configuracion", schema = "financiero")
public class CajaVirtualConfiguracion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_virtual_id", nullable = false, unique = true)
    private CajaVirtual cajaVirtual;

    @Column(name = "mostrar_cuentas_por_pagar")
    private Boolean mostrarCuentasPorPagar;

    @Column(name = "mostrar_cuentas_por_cobrar")
    private Boolean mostrarCuentasPorCobrar;

    @Column(name = "operaciones_financieras_habilitado")
    private Boolean operacionesFinancierasHabilitado;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(schema = "financiero", name = "caja_virtual_config_forma_pago",
            joinColumns = @JoinColumn(name = "configuracion_id"),
            inverseJoinColumns = @JoinColumn(name = "forma_pago_id"))
    private Set<FormaPago> formasPagoVisibles = new HashSet<>();

    /** Cuentas bancarias cuyo saldo se muestra en el sidebar del dashboard de la caja. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(schema = "financiero", name = "caja_virtual_config_cuenta_bancaria",
            joinColumns = @JoinColumn(name = "configuracion_id"),
            inverseJoinColumns = @JoinColumn(name = "cuenta_bancaria_id"))
    private Set<CuentaBancaria> cuentasBancariasVisibles = new HashSet<>();

    /** Orden de las cards de cuenta bancaria (JSON array de IDs). Null = por id ascendente. */
    @Column(name = "cuentas_bancarias_orden")
    private String cuentasBancariasOrden;

    /** JSON array de ids de moneda: orden de las cards de saldo (issue #540). Null = por id de moneda. */
    @Column(name = "monedas_orden")
    private String monedasOrden;

    /** JSON array de ids de sucursal: orden en los diálogos de la caja (desk #653). Null = por nombre. */
    @Column(name = "sucursales_orden")
    private String sucursalesOrden;

    /**
     * JSON array de {@code TipoOperacionFinanciera}: orden del diálogo de operaciones (issue #603).
     * Solo ordena: los permisos siguen en el servicio.
     */
    @Column(name = "operaciones_financieras_orden")
    private String operacionesFinancierasOrden;

    /** JSON array de {@code TipoOperacionFinanciera} que el diálogo no muestra. Oculta, nunca habilita. */
    @Column(name = "operaciones_financieras_ocultas")
    private String operacionesFinancierasOcultas;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
