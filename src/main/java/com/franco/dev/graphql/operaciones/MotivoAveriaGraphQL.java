package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.MotivoAveria;
import com.franco.dev.graphql.operaciones.input.MotivoAveriaInput;
import com.franco.dev.service.operaciones.MotivoAveriaService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MotivoAveriaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MotivoAveriaService service;

    public MotivoAveria motivoAveria(Long id) {
        return service.findById(id).orElse(null);
    }

    public List<MotivoAveria> motivosAveria() {
        return service.findAll2();
    }

    public List<MotivoAveria> motivosAveriaActivos() {
        return service.findAllActivos();
    }

    public MotivoAveria saveMotivoAveria(MotivoAveriaInput input) {
        ModelMapper mapper = new ModelMapper();
        MotivoAveria entity = mapper.map(input, MotivoAveria.class);
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getGeneraGasto() == null) entity.setGeneraGasto(false);
        if (entity.getAplicaProveedor() == null) entity.setAplicaProveedor(true);
        return service.save(entity);
    }

    public Boolean deleteMotivoAveria(Long id) {
        return service.deleteById(id);
    }
}
