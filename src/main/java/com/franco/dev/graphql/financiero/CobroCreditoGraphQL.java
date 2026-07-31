package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoCliente;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.repository.financiero.MovimientoClienteRepository;
import com.franco.dev.service.financiero.CobroCreditoService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@AllArgsConstructor
public class CobroCreditoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final CobroCreditoService service;
    private final MovimientoClienteRepository movimientoClienteRepository;
    private final TesoreriaSecurityService seg;

    public Page<MovimientoCliente> movimientosCliente(Long clienteId, int page, int size) {
        seg.requireVer();
        return movimientoClienteRepository.findByClienteIdOrderByCreadoEnDesc(clienteId, PageRequest.of(page, size));
    }

    public VentaCreditoCuota cobrarCuotaCreditoBanco(Long cuotaId, Long sucursalId, Double montoCobrar,
                                                     Long cuentaBancariaId, Double montoBanco, Double cotizacion) {
        seg.requireCobrarCpc();
        return service.cobrarCuotaBanco(cuotaId, sucursalId,
                BigDecimal.valueOf(montoCobrar), cuentaBancariaId,
                montoBanco != null ? BigDecimal.valueOf(montoBanco) : null,
                cotizacion != null ? BigDecimal.valueOf(cotizacion) : null,
                seg.currentUsuario());
    }
}
