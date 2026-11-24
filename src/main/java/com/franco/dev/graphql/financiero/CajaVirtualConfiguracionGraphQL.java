package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtualConfiguracion;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
import com.franco.dev.graphql.financiero.dto.CajaVirtualOrdenes;
import com.franco.dev.repository.financiero.CajaVirtualConfiguracionRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.OrdenConfigurable;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@AllArgsConstructor
public class CajaVirtualConfiguracionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final CajaVirtualConfiguracionRepository repository;
    private final FormaPagoRepository formaPagoRepository;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final CajaVirtualService cajaVirtualService;
    private final TesoreriaSecurityService seg;

    public CajaVirtualConfiguracion cajaVirtualConfiguracion(Long cajaVirtualId) {
        seg.requireVer();
        seg.requireLecturaCaja(cajaVirtualId);
        return repository.findByCajaVirtualId(cajaVirtualId).orElse(null);
    }

    /**
     * Los órdenes de la caja, para los diálogos que se abren desde ella. Lectura de la caja (incluye el rol
     * acotado TESORERIA CAJA, que no ve el resto de la configuración): solo ordena, no expone saldos ni permisos.
     */
    public CajaVirtualOrdenes cajaVirtualOrdenes(Long cajaVirtualId) {
        seg.requireVerCaja();
        seg.requireLecturaCaja(cajaVirtualId);
        return CajaVirtualOrdenes.de(repository.findByCajaVirtualId(cajaVirtualId).orElse(null));
    }

    public CajaVirtualConfiguracion saveCajaVirtualConfiguracion(CajaVirtualConfiguracionInputWrapper input) {
        seg.requireGestionar();
        // Configurar la caja es modificarla: como saveCajaVirtual, solo su dueño (o ADMIN). Sin esto un GESTIONAR podía
        // ocultar las operaciones de una caja ajena y leer sus cuentas visibles (auditoría del diff, 2026-11-12).
        seg.requirePropietarioCaja(input.getCajaVirtualId());
        CajaVirtualConfiguracion c = repository.findByCajaVirtualId(input.getCajaVirtualId())
                .orElseGet(CajaVirtualConfiguracion::new);
        if (c.getCajaVirtual() == null) {
            c.setCajaVirtual(cajaVirtualService.findById(input.getCajaVirtualId())
                    .orElseThrow(() -> new GraphQLException("Caja mayor no encontrada")));
        }
        c.setMostrarCuentasPorPagar(input.getMostrarCuentasPorPagar());
        c.setMostrarCuentasPorCobrar(input.getMostrarCuentasPorCobrar());
        c.setOperacionesFinancierasHabilitado(input.getOperacionesFinancierasHabilitado() != null
                ? input.getOperacionesFinancierasHabilitado() : true);
        if (input.getFormasPagoVisiblesIds() != null) {
            Set<FormaPago> fps = new HashSet<>();
            for (Long id : input.getFormasPagoVisiblesIds()) {
                formaPagoRepository.findById(id).ifPresent(fps::add);
            }
            c.setFormasPagoVisibles(fps);
        }
        if (input.getCuentasBancariasVisiblesIds() != null) {
            Set<CuentaBancaria> cbs = new HashSet<>();
            for (Long id : input.getCuentasBancariasVisiblesIds()) {
                cuentaBancariaRepository.findById(id).ifPresent(cbs::add);
            }
            c.setCuentasBancariasVisibles(cbs);
        }
        // Semántica PATCH: solo pisar el orden si el input lo trae (no borrarlo en saves parciales).
        if (input.getCuentasBancariasOrden() != null) {
            c.setCuentasBancariasOrden(input.getCuentasBancariasOrden());
        }
        // Los demás órdenes, también PATCH, validados (ids enteros sin repetir; operaciones solo
        // valores del enum). La lista de ocultas ordena y oculta en el diálogo: nunca habilita una
        // operación que el rol no permite.
        if (input.getMonedasOrden() != null) {
            c.setMonedasOrden(OrdenConfigurable.validarIds(input.getMonedasOrden(), "Orden de monedas"));
        }
        if (input.getSucursalesOrden() != null) {
            c.setSucursalesOrden(OrdenConfigurable.validarIds(input.getSucursalesOrden(), "Orden de sucursales"));
        }
        if (input.getOperacionesFinancierasOrden() != null) {
            c.setOperacionesFinancierasOrden(OrdenConfigurable.validarEnum(input.getOperacionesFinancierasOrden(),
                    TipoOperacionFinanciera.class, "Orden de operaciones"));
        }
        if (input.getOperacionesFinancierasOcultas() != null) {
            c.setOperacionesFinancierasOcultas(OrdenConfigurable.validarEnum(input.getOperacionesFinancierasOcultas(),
                    TipoOperacionFinanciera.class, "Operaciones ocultas"));
        }
        return repository.save(c);
    }

    /** Wrapper del input (graphql-java-kickstart mapea por nombre de clase XxxInput). */
    @lombok.Data
    public static class CajaVirtualConfiguracionInputWrapper {
        private Long cajaVirtualId;
        private Boolean mostrarCuentasPorPagar;
        private Boolean mostrarCuentasPorCobrar;
        private Boolean operacionesFinancierasHabilitado;
        private List<Long> formasPagoVisiblesIds;
        private List<Long> cuentasBancariasVisiblesIds;
        private String cuentasBancariasOrden;
        private String monedasOrden;
        private String sucursalesOrden;
        private String operacionesFinancierasOrden;
        private String operacionesFinancierasOcultas;
    }
}
