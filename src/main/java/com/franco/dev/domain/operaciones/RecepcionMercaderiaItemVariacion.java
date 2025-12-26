package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDefs({
    @TypeDef(name = "motivo_rechazo_fisico", typeClass = PostgreSQLEnumType.class)
})
@Table(name = "recepcion_mercaderia_item_variacion", schema = "operaciones")
public class RecepcionMercaderiaItemVariacion implements Serializable, Identifiable<Long> {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recepcion_mercaderia_item_id", nullable = false)
    private RecepcionMercaderiaItem recepcionMercaderiaItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private Presentacion presentacion;

    private Double cantidad;

    private LocalDateTime vencimiento;

    @Column(name = "lote")
    private String lote;

    private Boolean rechazado = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo")
    @Type(type = "motivo_rechazo_fisico")
    private MotivoRechazoFisico motivoRechazo;

}
