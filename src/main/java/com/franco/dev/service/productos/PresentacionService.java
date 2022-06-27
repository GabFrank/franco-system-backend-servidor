package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.repository.productos.PresentacionRepository;
import com.franco.dev.repository.productos.TipoPrecioRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PresentacionService extends CrudService<Presentacion, PresentacionRepository> {

    private final PresentacionRepository repository;

    @Override
    public PresentacionRepository getRepository() {
        return repository;
    }

    public List<Presentacion> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase());
    }

    public List<Presentacion> findByProductoId(Long id){
        return repository.findByProductoId(id);
    }

    @Override
    public Presentacion save(Presentacion entity){
        if(entity.getId()==null) entity.setCreadoEn(LocalDateTime.now());
        Presentacion e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    public Presentacion findByPrincipalAndProductoId(Boolean principal, Long id){
        return repository.findByPrincipalAndProductoId(principal, id);
    }
}
