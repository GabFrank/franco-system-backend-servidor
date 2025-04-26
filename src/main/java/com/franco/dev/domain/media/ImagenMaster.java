package com.franco.dev.domain.media;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "imagen_master", schema = "media")
@TypeDef(name = "tipo_referencia", typeClass = org.hibernate.type.EnumType.class)
public class ImagenMaster implements Identifiable<Long> {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_referencia")
    @Type(type = "tipo_referencia")
    private TipoReferencia tipoReferencia;

    @Column(name = "referencia_id")
    private Long referenciaId;

    private String nombre;

    private String url;

    private Boolean principal;

    private String checksum;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "extension")
    private String extension;
} 