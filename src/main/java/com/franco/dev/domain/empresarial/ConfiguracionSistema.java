package com.franco.dev.domain.empresarial;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_sistema", schema = "empresarial")
public class ConfiguracionSistema implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 100)
    private String clave;

    @Column(columnDefinition = "TEXT")
    private String valor;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Boolean encriptado = Boolean.FALSE;

    @Column(name = "modificado_en", nullable = false)
    private LocalDateTime modificadoEn;
}
