package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Quien cancelo o reactivo una venta, y cuando (V274.1). Solo existe en el central: ahi se cancela.
 * {@code estado} es el estado en que quedo la venta.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "venta_cancelacion", schema = "operaciones")
@TypeDef(name = "venta_estado", typeClass = PostgreSQLEnumType.class)
public class VentaCancelacion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "venta_estado")
    private VentaEstado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // En Java: Hibernate inserta NULL explicito y el DEFAULT now() de la base no se aplicaria
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    public VentaCancelacion(Venta venta, Usuario usuario) {
        this.ventaId = venta.getId();
        this.sucursalId = venta.getSucursalId();
        this.estado = venta.getEstado();
        this.usuario = usuario;
        this.creadoEn = LocalDateTime.now();
    }
}
