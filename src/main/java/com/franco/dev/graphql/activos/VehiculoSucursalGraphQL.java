package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.VehiculoSucursal;
import com.franco.dev.graphql.activos.input.VehiculoSucursalInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.activos.VehiculoService;
import com.franco.dev.service.activos.VehiculoSucursalService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class VehiculoSucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VehiculoSucursalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private VehiculoService vehiculoService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private FuncionarioService funcionarioService;

    public Optional<VehiculoSucursal> vehiculoSucursal(Long id) {
        return service.findById(id);
    }

    public List<VehiculoSucursal> vehiculosSucursal(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<VehiculoSucursal> vehiculosSucursalByVehiculo(Long vehiculoId) {
        return service.findByVehiculoId(vehiculoId);
    }

    public List<VehiculoSucursal> vehiculosSucursalBySucursal(Long sucursalId) {
        return service.findBySucursalId(sucursalId);
    }

    public CustomPage<VehiculoSucursal> vehiculosSucursalSearchPage(Long sucursalId, Long responsableId, Integer page,
            Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<VehiculoSucursal> pageResult = service.findBySucursalAndResponsable(sucursalId, responsableId, pageable);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public VehiculoSucursal saveVehiculoSucursal(VehiculoSucursalInput input) {
        ModelMapper m = new ModelMapper();
        VehiculoSucursal e = m.map(input, VehiculoSucursal.class);
        if (input.getVehiculoId() != null)
            e.setVehiculo(vehiculoService.findById(input.getVehiculoId()).orElse(null));
        if (input.getSucursalId() != null)
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if (input.getResponsableId() != null)
            e.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar la asignación de vehículo a sucursal: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteVehiculoSucursal(Long id) {
        try {
            Boolean ok = service.deleteById(id);
            return ok;
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar la asignación de vehículo a sucursal: " + err.getMessage());
        }
    }

    public Long countVehiculoSucursal() {
        return service.count();
    }
}
