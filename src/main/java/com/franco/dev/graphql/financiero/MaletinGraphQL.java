package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.config.multitenant.TenantContext;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.graphql.financiero.input.MaletinInput;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class MaletinGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MaletinService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Maletin> maletin(Long id, Long sucId) {
        return
                multiTenantService.compartir("filial" + sucId + "_bkp", (params) -> service.findById(id), id);
    }

    public List<Maletin> searchMaletin(String texto, Long sucId) {
        if(sucId!=null){
            return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> service.searchByAll(texto), texto);
        } else {
            List<List<Maletin>> result = new ArrayList<>();
            for(String key: TenantContext.getAllTenantKeys()){
                List<Maletin> res = multiTenantService.compartir(key, (params) -> service.searchByAll(texto), texto);
                if(res!=null){
                    result.add(res);
                }
            }
            return result.stream()
                    .flatMap(page -> page.stream())
                    .sorted(Comparator.comparing(Maletin::getDescripcion))
                    .collect(Collectors.toList());
        }
    }

    public List<Maletin> maletines(int page, int size) {
//        Pageable pageable = PageRequest.of(page,size);
        List<List<Maletin>> results = new ArrayList<>();
        for (String key : TenantContext.getAllTenantKeys()) {
            List<Maletin> res = multiTenantService.compartir(key, (params) -> service.findAll2());
            if (res != null) {
                results.add(res);
            }
        }
        return results.stream()
                .flatMap(m -> m.stream())
                .sorted(Comparator.comparing(Maletin::getDescripcion))
//                .limit(pageable.getPageSize()) // Limit the number of items based on pageable size
                .collect(Collectors.toList());
    }

    public Maletin saveMaletin(MaletinInput input) {
        ModelMapper m = new ModelMapper();
        Maletin e = m.map(input, Maletin.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return multiTenantService.compartir("filial" + input.getSucursalId() + "_bkp", (Maletin s) -> service.save(s), e);
    }

    public Maletin maletinPorDescripcion(String texto) {
        return service.findByDescripcion(texto);
    }

    public Maletin maletinPorDescripcionPorSucursal(String texto, Long sucId) {
//        return propagacionService.maletinPorDescripcionPorSucursal(texto, sucId);
        return null;
    }

    public Boolean deleteMaletin(Long id, Long sucId) {
        return multiTenantService.compartir("filial" + sucId + "_bkp", (Long s) -> service.deleteById(s), id);
    }

    public Long countMaletin() {
        return service.count();
    }


}
