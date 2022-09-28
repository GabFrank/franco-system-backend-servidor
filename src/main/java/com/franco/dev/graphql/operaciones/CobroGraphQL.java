package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.graphql.financiero.MovimientoCajaGraphQL;
import com.franco.dev.graphql.financiero.input.MovimientoCajaInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CobroGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CobroService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private CobroDetalleGraphQL cobroDetalleGraphQL;

    @Autowired
    private MovimientoCajaGraphQL movimientoCajaGraphQL;

    public Optional<Cobro> cobro(Long id, Long sucId) {
        return service.findById(new EmbebedPrimaryKey(id, sucId));
    }

    public List<Cobro> cobros(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Cobro saveCobro(CobroInput input, List<CobroDetalleInput> cobroDetalleList, Long cajaId) {
        ModelMapper m = new ModelMapper();
        Cobro e = m.map(input, Cobro.class);
        if (e.getUsuario() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        Cobro cobro = service.save(e);
        if (cobro != null) {
            for (CobroDetalleInput c : cobroDetalleList) {
                c.setCobroId(cobro.getId());
                CobroDetalle cobroDetalle = cobroDetalleGraphQL.saveCobroDetalle(c);
                if (cobroDetalle.getDescuento() != true && cobroDetalle.getFormaPago().getDescripcion().toUpperCase().contains("EFECTIVO")) {
                    MovimientoCajaInput movimientoCajaInput = new MovimientoCajaInput();
                    movimientoCajaInput.setMonedaId(c.getMonedaId());
                    movimientoCajaInput.setCantidad(c.getValor());
                    movimientoCajaInput.setActivo(true);
                    movimientoCajaInput.setPdvCajaId(cajaId);
                    movimientoCajaInput.setReferencia(cobro.getId());
                    movimientoCajaInput.setTipoMovimiento(PdvCajaTipoMovimiento.VENTA);
                    movimientoCajaInput.setUsuarioId(cobro.getUsuario().getId());
                    movimientoCajaGraphQL.saveMovimientoCaja(movimientoCajaInput);
                }

            }
            return cobro;
        } else {
            return null;
        }

    }

    public Boolean deleteCobro(Long id, Long sucId) {
        List<CobroDetalle> cobroDetalleList = cobroDetalleGraphQL.cobroDetallePorCobroId(id, sucId);
        for (CobroDetalle c : cobroDetalleList) {
            movimientoCajaGraphQL.desactivarByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.VENTA, c.getId(), sucId);
            return true;
        }
        return false;
    }

    public Long countCobro() {
        return service.count();
    }

}
