package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.MovimientoCajaVirtualInput;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
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

    public MovimientoCajaVirtual movimientoCajaVirtual(Long id) {
        return service.findById(id).orElse(null);
    }

    public Page<MovimientoCajaVirtual> movimientosCajaVirtual(Long cajaVirtualId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findByCajaVirtualId(cajaVirtualId, pageable);
    }

    public Page<MovimientoCajaVirtual> movimientosCajaVirtualPorFecha(Long cajaVirtualId, String inicio, String fin, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findByCajaVirtualIdAndFecha(cajaVirtualId, inicio, fin, pageable);
    }

    public MovimientoCajaVirtual saveMovimientoCajaVirtual(MovimientoCajaVirtualInput input) {
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

    public Boolean realizarTransferenciaCajaVirtual(Long origenId, Long destinoId, Double cantidad,
                                                     Long monedaId, String descripcion, Long usuarioId) {
        Moneda moneda = monedaId != null ? monedaService.findById(monedaId).orElse(null) : null;
        Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        return service.realizarTransferencia(origenId, destinoId, cantidad, moneda, descripcion, usuario);
    }
}
