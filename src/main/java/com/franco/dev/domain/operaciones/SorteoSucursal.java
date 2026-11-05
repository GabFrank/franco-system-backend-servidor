package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

/** Sucursal donde rige un {@link Sorteo} con todasSucursales=false. MAIN_TO_ALL. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sorteo_sucursal", schema = "operaciones")
public class SorteoSucursal implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sorteo_id", nullable = false)
    private Sorteo sorteo;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;
}
