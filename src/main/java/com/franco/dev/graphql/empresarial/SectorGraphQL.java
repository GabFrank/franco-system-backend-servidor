package com.franco.dev.graphql.empresarial;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.empresarial.input.SectorInput;
import com.franco.dev.service.empresarial.SectorService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SectorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private com.franco.dev.service.financiero.TesoreriaSecurityService seg;

    @Autowired
    private SectorService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private Environment env;


    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Sector> sector(Long id) {return service.findById(id);}

    public List<Sector> sectores(Long id){
        return service.findBySucursalId(id);
    }

    public List<Sector> sectoresSearch(String texto){
        return service.findByAll(texto);
    }

    public Sector saveSector(SectorInput input){
        seg.requireAnyRole(com.franco.dev.service.operaciones.InventarioSecurityService.ROLES_INVENTARIO);
        ModelMapper m = new ModelMapper();
        Sector e = m.map(input, Sector.class);
        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public Boolean deleteSector(Long id){
        seg.requireAnyRole(com.franco.dev.service.operaciones.InventarioSecurityService.ROLES_INVENTARIO);
        Sector sector = service.findById(id).orElse(null);
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countSector(){
        return service.count();
    }
}
