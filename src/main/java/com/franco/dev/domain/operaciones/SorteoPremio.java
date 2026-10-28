package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.productos.Producto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

/** Premio de un {@link Sorteo}: descripcion libre y/o producto del sistema, con cantidad. MAIN_TO_ALL. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sorteo_premio", schema = "operaciones")
public class SorteoPremio implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sorteo_id", nullable = false)
    private Sorteo sorteo;

    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Column(name = "descripcion")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad = 1;
}
