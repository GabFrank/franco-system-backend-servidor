package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.financiero.VueltoEgresoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Vueltos de los egresos de la caja mayor. La regla vive en {@link VueltoEgresoService}. */
@Component
@AllArgsConstructor
public class VueltoEgresoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final VueltoEgresoService service;
    private final TesoreriaSecurityService seg;

    public Page<VueltoEgresoService.EgresoParaVuelto> egresosParaVuelto(Long cajaVirtualId, String texto,
                                                                         Integer page, Integer size) {
        seg.requireVerCaja();
        return service.egresosParaVuelto(cajaVirtualId, texto, page != null ? page : 0, size != null ? size : 10);
    }

    public MovimientoCajaVirtual registrarVueltoEgreso(Long egresoId, Long monedaId, Double monto, String descripcion,
                                                       String claveIdempotencia) {
        seg.requireOperarCaja();
        return service.registrarVuelto(egresoId, monedaId, monto, descripcion, seg.currentUsuario(), claveIdempotencia);
    }
}
