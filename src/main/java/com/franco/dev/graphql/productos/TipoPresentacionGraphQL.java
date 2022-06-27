package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.domain.productos.TipoPresentacion;
import com.franco.dev.graphql.productos.input.TipoPrecioInput;
import com.franco.dev.graphql.productos.input.TipoPresentacionInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.productos.TipoPrecioService;
import com.franco.dev.service.productos.TipoPresentacionService;
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
public class TipoPresentacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoPresentacionService service;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<TipoPresentacion> tipoPresentacion(Long id) {return service.findById(id);}

    public List<TipoPresentacion> tipoPresentacionSearch(String texto) {return service.findByAll(texto);}

    public List<TipoPresentacion> tiposPresentacion(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public TipoPresentacion saveTipoPresentacion(TipoPresentacionInput input){
        ModelMapper m = new ModelMapper();
        TipoPresentacion e = m.map(input, TipoPresentacion.class);
        e = service.save(e);
        propagacionService.propagarEntidad(e, TipoEntidad.TIPO_PRESENTACION);
        return e;
    }

    public TipoPresentacion updateTipoPresentacion(Long id, TipoPresentacionInput input){
        ModelMapper m = new ModelMapper();
        TipoPresentacion p = service.getOne(id);
        p = m.map(input, TipoPresentacion.class);
        return service.save(p);
    }

    public Boolean deleteTipoPresentacion(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) propagacionService.eliminarEntidad(id, TipoEntidad.TIPO_PRESENTACION);
        return ok;
    }

    public Long countTipoPresentacion(){
        return service.count();
    }
}
