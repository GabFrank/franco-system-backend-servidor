package com.franco.dev.domain.productos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import com.franco.dev.config.search.ProductoAnalysisConfigurer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Indexed
@TypeDef(
        name = "tipo_conservacion",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "producto", schema = "productos")
public class Producto implements Identifiable<Long> {

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
    private Boolean propagado;

    @FullTextField(analyzer = ProductoAnalysisConfigurer.PRODUCTO_ANALYZER)
    private String descripcion;

    @FullTextField(analyzer = ProductoAnalysisConfigurer.PRODUCTO_ANALYZER)
    private String descripcionFactura;
    private Integer iva;
    private Integer unidadPorCaja;
    private Integer unidadPorCajaSecundaria;
    private Boolean balanza;
    private Boolean garantia;
    private Integer tiempoGarantia;
    private Boolean ingrediente;
    private Boolean combo;
    private Boolean stock;
    private Boolean cambiable;
    private Boolean promocion;
    private Boolean vencimiento;
    private Integer diasVencimiento;
    private String observacion;
    private String imagenes;
    @GenericField
    private Boolean isEnvase;

    @GenericField
    private Boolean activo;
    private Boolean lote;



    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_conservacion")
    @Type( type = "tipo_conservacion")
    private TipoConservacion tipoConservacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_familia_id", nullable = true)
    @IndexedEmbedded(includePaths = {"id", "familia.id"})
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    private Subfamilia subfamilia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "envase_id", nullable = true)
    private Producto envase;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    public String toString(){
        String texto = "Id: " + this.id + "/n" + "Descripcion: " + this.descripcion;
        return texto;
    }
}



