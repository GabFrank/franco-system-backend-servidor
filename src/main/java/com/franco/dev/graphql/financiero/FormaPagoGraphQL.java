package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.dto.FormaPagoEstadistica;
import com.franco.dev.graphql.financiero.input.FormaPagoInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class FormaPagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FormaPagoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private CobroDetalleService cobroDetalleService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Optional<FormaPago> formaPago(Long id) {
        return service.findById(id);
    }

    public List<FormaPago> formasPago(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    /**
     * Obtiene estadísticas de formas de pago para todas las sucursales
     */
    public List<FormaPagoEstadistica> formaPagoEstadisticas() {
        return cobroDetalleService.obtenerEstadisticasFormaPago();
    }

    /**
     * Obtiene estadísticas de formas de pago para una sucursal específica
     */
    public List<FormaPagoEstadistica> formaPagoEstadisticasPorSucursal(Long sucursalId) {
        return cobroDetalleService.obtenerEstadisticasFormaPagoPorSucursal(sucursalId);
    }

    /**
     * Obtiene estadísticas de formas de pago con filtros opcionales de fecha y
     * sucursal
     * 
     * @param inicio     Fecha de inicio en formato "yyyy-MM-dd HH:mm:ss" (opcional)
     * @param fin        Fecha de fin en formato "yyyy-MM-dd HH:mm:ss" (opcional)
     * @param sucursalId ID de la sucursal (opcional)
     */
    public List<FormaPagoEstadistica> formaPagoEstadisticasConFiltros(String inicio, String fin, Long sucursalId) {
        LocalDateTime fechaInicio = null;
        LocalDateTime fechaFin = null;

        if (inicio != null && !inicio.isEmpty()) {
            fechaInicio = LocalDateTime.parse(inicio, DATE_FORMATTER);
        }
        if (fin != null && !fin.isEmpty()) {
            fechaFin = LocalDateTime.parse(fin, DATE_FORMATTER);
        }

        return cobroDetalleService.obtenerEstadisticasFormaPagoConFiltros(fechaInicio, fechaFin, sucursalId);
    }

    public FormaPago saveFormaPago(FormaPagoInput input) {
        ModelMapper m = new ModelMapper();
        FormaPago e = m.map(input, FormaPago.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCuentaBancariaId() != null) {
            e.setCuentaBancaria(cuentaBancariaService.findById(input.getCuentaBancariaId()).orElse(null));
        }
        e = service.save(e);
        return e;
    }

    public Boolean deleteFormaPago(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countFormaPago() {
        return service.count();
    }

}
