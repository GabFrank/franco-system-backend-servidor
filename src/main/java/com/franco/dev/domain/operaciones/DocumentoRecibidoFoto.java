package com.franco.dev.domain.operaciones;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/** Una hoja fotografiada de una factura en papel. */
@Data
@NoArgsConstructor
@Entity
@Table(name = "documento_recibido_foto", schema = "operaciones")
public class DocumentoRecibidoFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_recibido_id", nullable = false)
    private DocumentoRecibido documentoRecibido;

    private Integer orden;
    private String mime;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "bytea")
    private byte[] imagen;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
