package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.EmbeddedEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "cobro", schema = "operaciones")
@IdClass(EmbebedPrimaryKey.class)
public class Cobro extends EmbeddedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "total_gs")
    private Double totalGs;

}