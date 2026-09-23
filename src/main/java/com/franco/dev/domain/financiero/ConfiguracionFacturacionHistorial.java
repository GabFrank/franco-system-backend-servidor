package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.AccionConfiguracionFacturacion;
import com.franco.dev.domain.financiero.enums.ModoFacturacion;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Una fila por cada cambio de la politica de facturacion (V231.1). Solo central, no se replica.
 * Guarda los valores despues del cambio; en ELIMINAR, los que tenia la fila al borrarse.
 * {@code configuracionId} es plano, sin relacion: la fila sobrevive al borrado de la configuracion.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_facturacion_historial", schema = "financiero")
public class ConfiguracionFacturacionHistorial implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "configuracion_id", nullable = false)
    private Long configuracionId;

    /** null = la politica global. */
    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 20)
    private AccionConfiguracionFacturacion accion;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo", nullable = false, length = 20)
    private ModoFacturacion modo;

    @Column(name = "ventas_sin_factura", nullable = false)
    private Integer ventasSinFactura;

    @Column(name = "venta_ticket_respeta_politica", nullable = false)
    private Boolean ventaTicketRespetaPolitica;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    /** Lo unico del autor que expone el .graphqls: el tipo Usuario completo trae el password. */
    public String getUsuarioNickname() {
        return usuario != null ? usuario.getNickname() : null;
    }
}
