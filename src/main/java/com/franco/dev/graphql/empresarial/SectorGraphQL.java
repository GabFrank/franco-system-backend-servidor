package com.franco.dev.graphql.empresarial;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Sector;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.empresarial.input.SectorInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.SectorService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
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
    private SectorService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private Environment env;

    @Autowired
    private PropagacionService propagacionService;

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
        ModelMapper m = new ModelMapper();
        Sector e = m.map(input, Sector.class);
        e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.SECTOR, input.getSucursalId());
        multiTenantService.compartir("filial"+input.getSucursalId()+"_bkp", (Sector s) -> service.save(s), e, false);
        return e;
    }

    public Boolean deleteSector(Long id){
        Sector sector = service.findById(id).orElse(null);
        Boolean ok = service.deleteById(id);
        if(ok){
            multiTenantService.compartir("filial"+sector.getSucursal().getId()+"_bkp", (Sector s) -> service.delete(sector), sector, false);
        }
        return ok;
    }

    public Long countSector(){
        return service.count();
    }
}
