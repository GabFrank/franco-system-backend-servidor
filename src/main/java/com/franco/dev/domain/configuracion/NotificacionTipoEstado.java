package com.franco.dev.domain.configuracion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * El interruptor de un tipo de notificacion.
 *
 * <p>
 * ⚠️ <b>Fila ausente = activo.</b> Un tipo nuevo no nace apagado por
 * accidente: lo que se apaga se apaga explicitamente, y el motivo queda
 * escrito en la fila.
 *
 * <p>
 * Es distinto de {@code NotificacionTipoRole}, que dice <i>a quien</i> le
 * llega. Esto dice si sale.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "notificacion_tipo_estado", schema = "configuraciones")
public class NotificacionTipoEstado implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_notificacion", nullable = false, unique = true)
    private String tipoNotificacion;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /** Por que se apago, y desde cuando. Se lee en la configuracion. */
    @Column(name = "motivo")
    private String motivo;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
