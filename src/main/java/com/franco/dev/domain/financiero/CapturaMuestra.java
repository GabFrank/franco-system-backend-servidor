package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * La foto de cupon con la que se configuro un formato.
 *
 * <p><b>Antes no se guardaba nada y era a proposito</b>: la muestra vivia en memoria y los bytes
 * del JPEG se descartaban apenas corria el OCR. El hueco se ve recien al usarlo --no se puede
 * volver a mirar el cupon que produjo el mapa-- y ademas es el corpus que la etapa 6 necesita para
 * proponer el patron solo.
 *
 * <p>La imagen va al disco y acá queda la referencia. {@link #ancho} y {@link #alto} no son
 * decoracion: las regiones estan normalizadas 0..1, asi que son lo que permite dibujarlas encima de
 * la foto sin volver a abrir el archivo.
 *
 * <p><b>Central-only.</b> No entra en la publicacion: a las filiales les baja el resultado --las
 * regiones-- y no la evidencia.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "captura_muestra", schema = "financiero")
public class CapturaMuestra implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "formato_terminal_pos_id")
    private FormatoTerminalPos formatoTerminalPos;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    private String token;

    /** Relativa al directorio configurado, nunca absoluta: la instancia se mueve de equipo. */
    @Column(name = "ruta_imagen")
    private String rutaImagen;

    private Integer ancho;

    private Integer alto;

    @Column(name = "texto_ocr")
    private String textoOcr;

    @Column(name = "ms_ocr")
    private Integer msOcr;

    @Column(name = "usuario_id")
    private Long usuarioId;
}
