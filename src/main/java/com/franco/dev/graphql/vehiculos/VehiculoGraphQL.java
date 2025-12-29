package com.franco.dev.graphql.vehiculos;

import com.franco.dev.domain.vehiculos.Vehiculo;
import com.franco.dev.graphql.vehiculos.input.VehiculoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.vehiculos.ModeloService;
import com.franco.dev.service.vehiculos.TipoVehiculoService;
import com.franco.dev.service.vehiculos.VehiculoService;
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
public class VehiculoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VehiculoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ModeloService modeloService;

    @Autowired
    private TipoVehiculoService tipoVehiculoService;

    public Optional<Vehiculo> vehiculo(Long id) {
        return service.findById(id);
    }

    public List<Vehiculo> vehiculos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Vehiculo> vehiculoSearch(String texto) {
        return service.findByAll(texto);
    }

    public List<Vehiculo> vehiculoSearchWithPage(String texto, int page, int size) {
        return service.findByAllWithPage(texto, page, size).getContent();
    }

    public Vehiculo vehiculoByChapa(String chapa) {
        return service.findByChapa(chapa);
    }

    public List<Vehiculo> vehiculosByMarca(Long marcaId) {
        return service.findByMarcaId(marcaId);
    }

    public List<Vehiculo> vehiculosByModelo(Long modeloId) {
        return service.findByModeloId(modeloId);
    }

    public List<Vehiculo> vehiculosByTipoVehiculo(Long tipoVehiculoId) {
        return service.findByTipoVehiculoId(tipoVehiculoId);
    }

    public Vehiculo saveVehiculo(VehiculoInput input) {
        ModelMapper m = new ModelMapper();
        Vehiculo e = m.map(input, Vehiculo.class);
        if(input.getModeloId() != null) e.setModelo(modeloService.findById(input.getModeloId()).orElse(null));
        if(input.getTipoVehiculoId() != null) e.setTipoVehiculo(tipoVehiculoService.findById(input.getTipoVehiculoId()).orElse(null));
        if(input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getFechaAdquisicion() != null) e.setFechaAdquisicion(input.getFechaAdquisicion());
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el vehículo: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteVehiculo(Long id) {
        try {
            Boolean ok = service.deleteById(id);
            return ok;
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el vehículo: " + err.getMessage());
        }
    }

    public Long countVehiculo() {
        return service.count();
    }
}

