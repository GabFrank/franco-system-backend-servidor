package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Vehiculo;
import com.franco.dev.graphql.activos.input.VehiculoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.activos.ModeloService;
import com.franco.dev.service.activos.TipoCombustibleService;
import com.franco.dev.service.activos.TipoVehiculoService;
import com.franco.dev.service.activos.VehiculoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
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

    @Autowired
    private TipoCombustibleService tipoCombustibleService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private MonedaService monedaService;

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
        Vehiculo e = new Vehiculo();
        e.setId(input.getId());
        e.setChapa(input.getChapa());
        e.setColor(input.getColor());
        e.setAnho(input.getAnho());
        e.setDocumentacion(input.getDocumentacion());
        e.setRefrigerado(input.getRefrigerado());
        e.setNuevo(input.getNuevo());
        e.setPrimerKilometraje(input.getPrimerKilometraje());
        e.setCapacidadKg(input.getCapacidadKg());
        e.setCapacidadPasajeros(input.getCapacidadPasajeros());
        e.setImagenesVehiculo(input.getImagenesVehiculo());
        e.setImagenesDocumentos(input.getImagenesDocumentos());
        e.setIdentificadorInterno(input.getIdentificadorInterno());
        e.setChasis(input.getChasis());
        e.setAireAcondicionado(input.getAireAcondicionado());
        e.setValorEstimado(input.getValorEstimado());
        e.setValorEstimadoPyg(input.getValorEstimadoPyg());
        e.setValorEstimadoBrl(input.getValorEstimadoBrl());
        e.setMantenimientoMotorIntervalo(input.getMantenimientoMotorIntervalo());
        e.setMantenimientoCajaIntervalo(input.getMantenimientoCajaIntervalo());
        e.setSituacionPago(input.getSituacionPago());
        e.setMontoTotal(input.getMontoTotal());
        e.setMontoYaPagado(input.getMontoYaPagado());
        e.setCantidadCuotas(input.getCantidadCuotas());
        e.setCantidadCuotasPagadas(input.getCantidadCuotasPagadas());
        e.setDiaVencimiento(input.getDiaVencimiento());
        if (input.getModeloId() != null)
            e.setModelo(modeloService.findById(input.getModeloId()).orElse(null));
        if (input.getTipoVehiculoId() != null)
            e.setTipoVehiculo(tipoVehiculoService.findById(input.getTipoVehiculoId()).orElse(null));
        if (input.getPropietarioId() != null)
            e.setPropietario(personaService.findById(input.getPropietarioId()).orElse(null));
        if (input.getTipoCombustibleId() != null)
            e.setTipoCombustible(tipoCombustibleService.findById(input.getTipoCombustibleId()).orElse(null));
        if (input.getProveedorId() != null)
            e.setProveedor(personaService.findById(input.getProveedorId()).orElse(null));
        if (input.getMonedaId() != null)
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
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
