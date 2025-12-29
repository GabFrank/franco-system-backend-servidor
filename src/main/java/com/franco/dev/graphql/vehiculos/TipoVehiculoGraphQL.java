package com.franco.dev.graphql.vehiculos;

import com.franco.dev.domain.vehiculos.TipoVehiculo;
import com.franco.dev.graphql.vehiculos.input.TipoVehiculoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.vehiculos.TipoVehiculoService;
import graphql.GraphQLException;
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
public class TipoVehiculoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoVehiculoService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<TipoVehiculo> tipoVehiculo(Long id) {
        return service.findById(id);
    }

    public List<TipoVehiculo> tiposVehiculo(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<TipoVehiculo> tipoVehiculoSearch(String texto) {
        return service.findByAll(texto);
    }

    public TipoVehiculo saveTipoVehiculo(TipoVehiculoInput input) {
        ModelMapper m = new ModelMapper();
        TipoVehiculo e = m.map(input, TipoVehiculo.class);
        if(input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el tipo de vehículo: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteTipoVehiculo(Long id) {
        try {
            Boolean ok = service.deleteById(id);
            return ok;
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el tipo de vehículo: " + err.getMessage());
        }
    }

    public Long countTipoVehiculo() {
        return service.count();
    }
}

