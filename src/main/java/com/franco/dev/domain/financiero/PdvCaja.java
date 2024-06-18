package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.EmbeddedEntity;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pdv_caja", schema = "financiero")
@TypeDef(
        name = "pdv_caja_estado",
        typeClass = PostgreSQLEnumType.class
)
@IdClass(EmbebedPrimaryKey.class)
public class PdvCaja extends EmbeddedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    private String descripcion;
    private String observacion;
    private Boolean activo;
    private Boolean tuvoProblema;

    @Column(name = "fecha_apertura")
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "pdv_caja_estado")
    private PdvCajaEstado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maletin_id", nullable = true)
    private Maletin maletin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "conteo_apertura_id", referencedColumnName = "id"))
    })
    private Conteo conteoApertura;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "conteo_cierre_id", referencedColumnName = "id"))
    })
    private Conteo conteoCierre;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean verificado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificado_por_id", nullable = true)
    private Usuario verificadoPor;

}



