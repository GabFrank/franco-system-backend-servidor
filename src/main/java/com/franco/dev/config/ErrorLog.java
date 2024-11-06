package com.franco.dev.config;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "error_log", schema = "configuraciones")
@TypeDef(
        name = "tipo_error",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "nivel_error",
        typeClass = PostgreSQLEnumType.class
)
@IdClass(EmbebedPrimaryKey.class)
public class ErrorLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    private TipoError tipo;
    private String mensaje;
    private NivelError nivel;
    private LocalDateTime fecha_primera_ocurrencia;
    private LocalDateTime fecha_ultima_ocurrencia;
    private Integer cantidad_ocurrencias;

    public ErrorLog(TipoError tipo, String mensaje, NivelError nivel) {
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.nivel = nivel;
    }
}
