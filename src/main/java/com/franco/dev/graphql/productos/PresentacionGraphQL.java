package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.graphql.productos.input.PresentacionInput;
import com.franco.dev.graphql.productos.input.TipoPrecioInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.productos.TipoPrecioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.franco.dev.service.utils.ImageService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Component
public class PresentacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    public static final Logger log = Logger.getLogger(String.valueOf(PresentacionGraphQL.class));

    @Autowired
    private PresentacionService service;

    @Autowired
    private ImageService imageService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Presentacion> presentacion(Long id) {return service.findById(id);}

    public List<Presentacion> presentacionSearch(String texto) {return service.findByAll(texto);}

    public List<Presentacion> presentaciones(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Presentacion savePresentacion(PresentacionInput input) throws GraphQLException{
        ModelMapper m = new ModelMapper();
        Boolean isPrincipalInUse = false;
        if(input.getPrincipal()){
            List<Presentacion> presentacionList = service.findByProductoId(input.getProductoId());
            for(Presentacion p : presentacionList){
                if(p.getPrincipal()){
                    if(!p.getId().equals(input.getId())){
                        throw new GraphQLException("Ya existe una presentación principal");
                    }
                }
            }
        }

        Presentacion e = m.map(input, Presentacion.class);
        e =  service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.PRESENTACION);
        multiTenantService.compartir(null, (Presentacion s) -> service.save(s), e);
        return e;
    }

    public Presentacion updatePresentacion(Long id, PresentacionInput input){
        ModelMapper m = new ModelMapper();
        Presentacion p = service.getOne(id);
        p = m.map(input, Presentacion.class);
        return service.save(p);
    }

    public List<Presentacion> presentacionesPorProductoId(Long id){
        return service.findByProductoId(id);
    }

    public Boolean deletePresentacion(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;
    }

    public Long countPresentacion(){
        return service.count();
    }

    public Boolean saveImagenPresentacion(String image, String filename) throws IOException {
        Boolean ok = imageService.saveImageToPath(image, filename, imageService.getImagePresentaciones(), imageService.getImagePresentacionesThumb(), true);
        if(ok){
            propagacionService.propagarImagen(image, filename, TipoEntidad.PRESENTACION);
        }
        return ok;
    }

}
