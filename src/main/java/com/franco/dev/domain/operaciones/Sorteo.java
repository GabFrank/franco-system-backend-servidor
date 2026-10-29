package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.operaciones.enums.ModoParticipacionSorteo;
import com.franco.dev.domain.personas.Usuario;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sorteo para clientes. Lo escribe solo el central; baja MAIN_TO_ALL y cada filial lo evalua al
 * cobrar una venta. El central nunca lo evalua.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sorteo", schema = "operaciones")
public class Sorteo implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "monto_minimo", nullable = false)
    private BigDecimal montoMinimo;

    @Column(name = "incluye_tarjeta", nullable = false)
    private Boolean incluyeTarjeta = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_participacion", nullable = false)
    private ModoParticipacionSorteo modoParticipacion;

    @Column(name = "cantidad_fija")
    private Integer cantidadFija;

    @Column(name = "tope_por_venta")
    private Integer topePorVenta;

    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Column(name = "fecha_sorteo")
    private LocalDate fechaSorteo;

    @Column(name = "todas_sucursales", nullable = false)
    private Boolean todasSucursales = false;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "sorteo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, id ASC")
    private List<SorteoPremio> premios = new ArrayList<>();

    @OneToMany(mappedBy = "sorteo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SorteoSucursal> sucursales = new ArrayList<>();

    /** Solo el nickname: el tipo Usuario completo trae password. */
    public String getUsuarioNickname() {
        return usuario != null ? usuario.getNickname() : null;
    }
}
