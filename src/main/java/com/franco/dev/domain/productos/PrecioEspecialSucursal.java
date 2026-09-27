package com.franco.dev.domain.productos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Precio especial de un {@link PrecioPorSucursal} en una sucursal, con vigencia opcional.
 * Lo escribe solo el central; baja MAIN_TO_ALL y cada filial lo aplica al devolver el precio.
 * El central nunca lo aplica: la ficha del producto muestra siempre el precio global.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "precio_especial_sucursal", schema = "productos")
public class PrecioEspecialSucursal implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "precio_id", nullable = false)
    private PrecioPorSucursal precioPorSucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @Column(name = "precio", nullable = false)
    private Double precio;

    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    /** Solo el nickname: el tipo Usuario completo trae password. */
    public String getUsuarioNickname() {
        return usuario != null ? usuario.getNickname() : null;
    }
}
