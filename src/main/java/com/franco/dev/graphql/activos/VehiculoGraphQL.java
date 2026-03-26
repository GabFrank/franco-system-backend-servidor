package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Vehiculo;
import com.franco.dev.graphql.activos.input.VehiculoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.activos.ModeloService;
import com.franco.dev.service.activos.TipoVehiculoService;
import com.franco.dev.service.activos.VehiculoService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

    /**
     * Búsqueda paginada con PageInfo (getContent/getTotalElements/etc) para UI con
     * paginator.
     * Mantiene vehiculoSearchWithPage() por compatibilidad (retorna List).
     */
    public CustomPage<Vehiculo> vehiculoSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        org.springframework.data.domain.Page<Vehiculo> pageResult = service.findByAllWithPage(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
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
        if (input.getModeloId() != null)
            e.setModelo(modeloService.findById(input.getModeloId()).orElse(null));
        if (input.getTipoVehiculoId() != null)
            e.setTipoVehiculo(tipoVehiculoService.findById(input.getTipoVehiculoId()).orElse(null));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getFechaAdquisicion() != null && !input.getFechaAdquisicion().isEmpty()) {
            LocalDate fecha = DateUtils.stringToDate(input.getFechaAdquisicion()).toLocalDate();
            e.setFechaAdquisicion(fecha);
        }
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
