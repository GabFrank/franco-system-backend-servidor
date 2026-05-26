package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.operaciones.enums.EstadoImport;
import com.franco.dev.domain.operaciones.enums.OrigenImport;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "factura_proveedor_import", schema = "operaciones")
public class FacturaProveedorImport implements Identifiable<Long>, Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", length = 20, nullable = false)
    private OrigenImport origen;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 30, nullable = false)
    private EstadoImport estado;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imagen_master_id", nullable = true)
    private ImagenMaster imagenMaster;

    @Column(name = "json_crudo", columnDefinition = "jsonb")
    @Type(type = "com.vladmihalcea.hibernate.type.json.JsonBinaryType")
    private String jsonCrudo;

    @Column(name = "json_validado", columnDefinition = "jsonb")
    @Type(type = "com.vladmihalcea.hibernate.type.json.JsonBinaryType")
    private String jsonValidado;

    @Column(name = "tokens_prompt")
    private Integer tokensPrompt;

    @Column(name = "tokens_respuesta")
    private Integer tokensRespuesta;

    @Column(name = "modelo_ia", length = 50)
    private String modeloIa;

    @Column(name = "error_mensaje", columnDefinition = "TEXT")
    private String errorMensaje;

    /** FK soft a operaciones.pedido — no se modela JPA para evitar fetch sobre pedido pesado. */
    @Column(name = "pedido_id")
    private Long pedidoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en", nullable = false)
    private LocalDateTime modificadoEn;

    @PrePersist
    public void prePersist() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
        if (modificadoEn == null) modificadoEn = creadoEn;
    }

    @PreUpdate
    public void preUpdate() {
        modificadoEn = LocalDateTime.now();
    }
}
