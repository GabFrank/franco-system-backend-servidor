package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "retiro_detalle", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class RetiroDetalle implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Column(name = "sucursal_id")
    private Long sucursalId;

    private Double cantidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retiro_id", insertable = false, updatable = false)
    @JoinColumn(name = "sucursal_id", insertable = false, updatable = false)
    private Retiro retiro;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    private Double cambio;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



