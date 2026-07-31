package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtualConfiguracion;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.repository.financiero.CajaVirtualConfiguracionRepository;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.service.financiero.CajaVirtualService;
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
    private final CajaVirtualService cajaVirtualService;
    private final TesoreriaSecurityService seg;

    public CajaVirtualConfiguracion cajaVirtualConfiguracion(Long cajaVirtualId) {
        seg.requireVer();
        return repository.findByCajaVirtualId(cajaVirtualId).orElse(null);
    }

    public CajaVirtualConfiguracion saveCajaVirtualConfiguracion(CajaVirtualConfiguracionInputWrapper input) {
        seg.requireGestionar();
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
    }
}
