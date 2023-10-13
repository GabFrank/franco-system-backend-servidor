package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.graphql.productos.input.CostoPorProductoInput;
import com.franco.dev.service.productos.CostosPorProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CostoPorProductoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CostosPorProductoService service;

//    public CostosPorSucursal costoPorProductoLastPorProductoId(Long proId, Long sucId) {return service.findLastByProductoIdAndSucursalId(proId, sucId);}
    public Page<CostoPorProducto> costosPorProductoId(Long id, int page, int size) {
        Pageable pageable = PageRequest.of(page,size);
        return service.findByProductoId(id, pageable);
    }

    public CostoPorProducto saveCostoPorProducto(CostoPorProductoInput input){
        if(input.getId()==null) input.setCreadoEn(LocalDateTime.now());
        ModelMapper m = new ModelMapper();
        CostoPorProducto e = m.map(input, CostoPorProducto.class);
        return service.save(e);
    }

    public Boolean deleteCostoPorProducto(Long id){
        return service.deleteById(id);
    }

}
