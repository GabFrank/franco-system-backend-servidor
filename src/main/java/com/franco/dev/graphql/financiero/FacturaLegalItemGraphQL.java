package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.productos.Producto;
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

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<FacturaLegalItem> facturaLegalItem(Long id, Long sucId) {
        return service.findById(new EmbebedPrimaryKey(id, sucId));
    }

    public List<FacturaLegalItem> facturaLegalItens(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    @Unsecured
    public FacturaLegalItem saveFacturaLegalItem(FacturaLegalItemInput input, Long sucId) {
        ModelMapper m = new ModelMapper();
        FacturaLegalItem e = m.map(input, FacturaLegalItem.class);
        if (input.getUsuarioId() != null) e.setUsuario(multiTenantService.compartir("default", (params) -> usuarioService.findById(input.getUsuarioId()).orElse(null), input.getUsuarioId()));
        if (input.getFacturaLegalId() != null)
            e.setFacturaLegal(multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> facturaLegalService.findByIdAndSucursalId(input.getFacturaLegalId(), sucId), input.getFacturaLegalId(), sucId));
        if (input.getVentaItemId() != null)
            e.setVentaItem(multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> ventaItemService.findByIdAndSucursalId(input.getVentaItemId(), sucId), input.getVentaItemId(), sucId));
//        e = service.save(e);
        multiTenantService.compartir("filial"+sucId+"_bkp", (FacturaLegalItem s) -> service.save(s), e);
//        propagacionService.propagarEntidad(e, TipoEntidad.FACTURA_ITEM, sucId);
        return e;
    }

    public Boolean deleteFacturaLegalItem(Long id, Long sucId) {
        Boolean ok = service.deleteById(new EmbebedPrimaryKey(id, sucId));
        if(ok) multiTenantService.compartir(null, (EmbebedPrimaryKey s) -> service.deleteById(s), new EmbebedPrimaryKey(id, sucId));
        return ok;
    }

    public Long countFacturaLegalItem() {
        return service.count();
    }


}
