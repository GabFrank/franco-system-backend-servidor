package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.enums.FuncionarioDocumentoTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Metadata del legajo digital del funcionario. El binario se guarda en disco
 * (ImageService.getImagePath() + rrhh/documentos) y se lee como base64.
 * Espejo de Gourmet (funcionario_documentos). Aditivo, central-only.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "funcionario_documento", schema = "rrhh")
public class FuncionarioDocumento implements Identifiable<Long> {

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
    @JoinColumn(name = "funcionario_id", nullable = true)
    private Funcionario funcionario;

    @Enumerated(EnumType.STRING)
    private FuncionarioDocumentoTipo tipo;

    @Column(name = "nombre_archivo")
    private String nombreArchivo;

    @Column(name = "ruta_relativa")
    private String rutaRelativa;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "fecha_subida")
    private LocalDateTime fechaSubida;

    private LocalDate vencimiento;

    private String observacion;

    private Boolean anulado;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
