package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.graphql.financiero.input.FacturaLegalItemInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
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
public class FacturaLegalItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FacturaLegalItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private VentaItemService ventaItemService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<FacturaLegalItem> facturaLegalItem(Long id) {
        return service.findById(id);
    }

    public List<FacturaLegalItem> facturaLegalItens(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Unsecured
    public FacturaLegalItem saveFacturaLegalItem(FacturaLegalItemInput input, Long sucId) {
        ModelMapper m = new ModelMapper();
        FacturaLegalItem e = m.map(input, FacturaLegalItem.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getFacturaLegalId() != null)
            e.setFacturaLegal(facturaLegalService.findById(input.getFacturaLegalId()).orElse(null));
        if (input.getVentaItemId() != null)
            e.setVentaItem(ventaItemService.findById(input.getVentaItemId()).orElse(null));
        e = service.save(e);
        propagacionService.propagarEntidad(e, TipoEntidad.FACTURA_ITEM, sucId);
        return e;
    }

    public Boolean deleteFacturaLegalItem(Long id) {
        return service.deleteById(id);
    }

    public Long countFacturaLegalItem() {
        return service.count();
    }


}
