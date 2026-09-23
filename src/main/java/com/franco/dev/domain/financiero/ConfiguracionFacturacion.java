package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.ModoFacturacion;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Politica de facturacion automatica del filial (issue filial #127), V231.1.
 * <p>
 * {@code sucursal} NULL es la politica global; con valor, el override de esa sucursal. Se replica
 * MAIN_TO_ALL: el filial la lee en cada venta ({@code ConfiguracionFacturacionLector}) y, si no hay
 * fila, usa su property {@code facturaCountDown}. Se escribe solo desde aca.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_facturacion", schema = "financiero")
public class ConfiguracionFacturacion implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo", nullable = false, length = 20)
    private ModoFacturacion modo = ModoFacturacion.INTERVALO;

    /** Solo INTERVALO: ventas sin factura entre dos facturadas. */
    @Column(name = "ventas_sin_factura", nullable = false)
    private Integer ventasSinFactura = 0;

    /** false = "Venta + Ticket" y delivery facturan siempre (lo historico); true = decide la politica. */
    @Column(name = "venta_ticket_respeta_politica", nullable = false)
    private Boolean ventaTicketRespetaPolitica = false;

    /**
     * false = el filial ignora la fila (la sucursal sigue a la global, o a su property) pero conserva
     * sus valores. Desactivar NO es kill switch: el kill switch es el DELETE.
     */
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    /**
     * Lo unico del autor que expone el .graphqls. El tipo Usuario completo trae el campo password
     * y cualquier rol de tesoreria podria pedirlo a traves de esta configuracion.
     */
    public String getUsuarioNickname() {
        return usuario != null ? usuario.getNickname() : null;
    }
}
