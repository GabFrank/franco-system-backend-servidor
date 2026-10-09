package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.MovimientoCajaVirtualInput;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class MovimientoCajaVirtualGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final MovimientoCajaVirtualService service;
    private final CajaVirtualService cajaVirtualService;
    private final MonedaService monedaService;
    private final UsuarioService usuarioService;
    private final TesoreriaSecurityService seg;
    private final com.franco.dev.service.financiero.MovimientosCajaEnLoteService loteService;

    public MovimientoCajaVirtual movimientoCajaVirtual(Long id) {
        seg.requireVer();
        return service.findById(id).orElse(null);
    }

    public Page<MovimientoCajaVirtual> movimientosCajaVirtual(Long cajaVirtualId, int page, int size) {
        seg.requireVer();
        seg.requireLecturaCaja(cajaVirtualId);
        Pageable pageable = PageRequest.of(page, size);
        return service.findByCajaVirtualId(cajaVirtualId, pageable);
    }

    public Page<MovimientoCajaVirtual> movimientosCajaVirtualPorFecha(Long cajaVirtualId, String inicio, String fin, int page, int size) {
        seg.requireVer();
        seg.requireLecturaCaja(cajaVirtualId);
        Pageable pageable = PageRequest.of(page, size);
        return service.findByCajaVirtualIdAndFecha(cajaVirtualId, inicio, fin, pageable);
    }

    /**
     * Movimientos filtrados por fecha/tipo/moneda, con opción de ocultar anulados (para el dashboard).
     * El filtro por moneda es lo que activan las cards de saldo al hacer click.
     */
    public Page<MovimientoCajaVirtual> movimientosCajaVirtualFilter(Long cajaVirtualId, String desde, String fin,
                                                                    com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento tipo,
                                                                    Long monedaId,
                                                                    Boolean soloActivos, int page, int size) {
        seg.requireVer();
        seg.requireLecturaCaja(cajaVirtualId);
        Pageable pageable = PageRequest.of(page, size);
        return service.filter(cajaVirtualId, desde, fin, tipo, monedaId, soloActivos != null && soloActivos, pageable);
    }

    public MovimientoCajaVirtual saveMovimientoCajaVirtual(MovimientoCajaVirtualInput input) {
        seg.requireGestionar();
        MovimientoCajaVirtual entity = new MovimientoCajaVirtual();

        CajaVirtual cajaVirtual = cajaVirtualService.findById(input.getCajaVirtualId())
                .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada: " + input.getCajaVirtualId()));
        entity.setCajaVirtual(cajaVirtual);
        entity.setTipoMovimiento(input.getTipoMovimiento());
        entity.setCantidad(input.getCantidad());
        entity.setDescripcion(input.getDescripcion());
        entity.setReferenciaId(input.getReferenciaId());
        entity.setActivo(input.getActivo() != null ? input.getActivo() : true);

        if (input.getMonedaId() != null) {
            Moneda moneda = monedaService.findById(input.getMonedaId()).orElse(null);
            entity.setMoneda(moneda);
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCajaOrigenId() != null) {
            entity.setCajaOrigen(cajaVirtualService.findById(input.getCajaOrigenId()).orElse(null));
        }
        if (input.getCajaDestinoId() != null) {
            entity.setCajaDestino(cajaVirtualService.findById(input.getCajaDestinoId()).orElse(null));
        }

        return service.registrarMovimiento(entity);
    }

    public MovimientoCajaVirtual anularMovimientoCajaVirtual(Long id, String motivo) {
        seg.requireGestionar();
        return service.anularMovimiento(id, motivo, seg.currentUsuario());
    }

    /**
     * Ingreso, egreso o ajuste en varias monedas, en un solo pedido: entra todo o no entra nada
     * (issue #376). No se carga nada acá: el servicio resuelve todo dentro de su transacción. El
     * movimiento queda a nombre del usuario de la sesión.
     */
    public Boolean registrarMovimientosCajaVirtual(Long cajaVirtualId,
                                                   com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento tipoMovimiento,
                                                   java.util.List<com.franco.dev.graphql.financiero.input.MontoCajaVirtualInput> montos,
                                                   String descripcion, String claveIdempotencia) {
        seg.requireGestionar();
        return loteService.registrarMovimientos(cajaVirtualId, tipoMovimiento, aMontos(montos), descripcion,
                seg.currentUsuario(), claveIdempotencia);
    }

    /** Transferencia entre dos cajas en varias monedas, en un solo pedido: todo o nada (issue #376). */
    public Boolean realizarTransferenciasCajaVirtual(Long origenId, Long destinoId,
                                                     java.util.List<com.franco.dev.graphql.financiero.input.MontoCajaVirtualInput> montos,
                                                     String descripcion, String claveIdempotencia) {
        seg.requireGestionar();
        return loteService.transferir(origenId, destinoId, aMontos(montos), descripcion,
                seg.currentUsuario(), claveIdempotencia);
    }

    private static java.util.List<com.franco.dev.service.financiero.MovimientosCajaEnLoteService.Monto> aMontos(
            java.util.List<com.franco.dev.graphql.financiero.input.MontoCajaVirtualInput> montos) {
        if (montos == null) return null;
        java.util.List<com.franco.dev.service.financiero.MovimientosCajaEnLoteService.Monto> salida = new java.util.ArrayList<>();
        for (com.franco.dev.graphql.financiero.input.MontoCajaVirtualInput m : montos) {
            salida.add(m == null ? null
                    : new com.franco.dev.service.financiero.MovimientosCajaEnLoteService.Monto(m.getMonedaId(), m.getCantidad()));
        }
        return salida;
    }

    public Boolean realizarTransferenciaCajaVirtual(Long origenId, Long destinoId, Double cantidad,
                                                     Long monedaId, String descripcion, Long usuarioId) {
        seg.requireGestionar();
        Moneda moneda = monedaId != null ? monedaService.findById(monedaId).orElse(null) : null;
        Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        return service.realizarTransferencia(origenId, destinoId, cantidad, moneda, descripcion, usuario);
    }
}
