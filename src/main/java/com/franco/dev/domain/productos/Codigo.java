package com.franco.dev.domain.productos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.AssociationInverseSide;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Indexed
@Table(name = "codigo", schema = "productos")
public class Codigo implements Identifiable<Long> {

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

    @KeywordField(name = "codigo", normalizer = "lowercase")
    private String codigo;

    @GenericField
    private Boolean activo;

    private Boolean principal;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id", nullable = true)
    @AssociationInverseSide(inversePath = @ObjectPath(@PropertyValue(propertyName = "codigos")))
    private Presentacion presentacion;

    @GenericField(name = "productoId")
    @IndexingDependency(derivedFrom = @ObjectPath({
            @PropertyValue(propertyName = "presentacion"),
            @PropertyValue(propertyName = "producto"),
            @PropertyValue(propertyName = "id")
    }))
    public Long getProductoIdParaIndice() {
        return presentacion != null && presentacion.getProducto() != null
                ? presentacion.getProducto().getId()
                : null;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



