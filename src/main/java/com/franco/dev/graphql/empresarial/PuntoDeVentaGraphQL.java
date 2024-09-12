package com.franco.dev.graphql.empresarial;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.PuntoDeVenta;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.empresarial.input.PuntoDeVentaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.PuntoDeVentaService;
import com.franco.dev.service.empresarial.SucursalService;
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
public class PuntoDeVentaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PuntoDeVentaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<PuntoDeVenta> puntoDeVenta(Long id) {
        return service.findById(id);
    }

    public List<PuntoDeVenta> puntoDeVentas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<PuntoDeVenta> puntoDeVentaPorSucursalId(Long id) {
        return service.findBySucursalId(id);
    }


    public PuntoDeVenta savePuntoDeVenta(PuntoDeVentaInput input) {
        ModelMapper m = new ModelMapper();
        PuntoDeVenta e = m.map(input, PuntoDeVenta.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public Boolean deletePuntoDeVenta(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countPuntoDeVenta() {
        return service.count();
    }


}
