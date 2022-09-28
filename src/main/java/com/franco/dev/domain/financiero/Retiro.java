package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.domain.personas.Funcionario;
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
@Table(name = "retiro", schema = "financiero")
@TypeDef(
        name = "estado_retiro",
        typeClass = PostgreSQLEnumType.class
)
@IdClass(EmbebedPrimaryKey.class)
public class Retiro extends EmbeddedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    private String observacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "estado_retiro")
    private EstadoRetiro estado;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id", nullable = true)
    private Funcionario responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "caja_salida_id", referencedColumnName = "id"))
    })
    private PdvCaja cajaSalida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "caja_entrada_id", referencedColumnName = "id"))
    })
    private PdvCaja cajaEntrada;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



