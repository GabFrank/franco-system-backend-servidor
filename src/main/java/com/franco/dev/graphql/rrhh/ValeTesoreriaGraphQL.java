package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.graphql.financiero.PagoProveedorGraphQL;
import com.franco.dev.service.financiero.ValeTesoreriaService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import com.franco.dev.service.rrhh.dto.ValePendienteDto;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pago de vales desde el hub de egresos de la Caja Mayor ("Pagar Vale").
 *
 * <p>Seguridad: mismos roles que ya exigen las operaciones de vale en {@link ValeGraphQL}
 * ({@code requireVer} para listar, {@code APROBAR} para crear/pagar). Es paridad exacta con
 * {@code crearValeConfirmado}, que es lo que hacía el hub antes de este flujo: no amplía ni
 * restringe privilegios respecto de lo que la gente ya podía hacer.</p>
 */
@Component
@AllArgsConstructor
public class ValeTesoreriaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final ValeTesoreriaService service;
    private final RrhhSecurityService seg;

    /** Vales pagables (estado SOLICITADO) con su saldo. */
    public List<ValePendienteDto> valesPendientes() {
        seg.requireVer();
        return service.listarValesPendientes();
    }

    /** Alta de un vale listo para pagar: queda SOLICITADO, sin mover plata. */
    public Vale crearValeParaPago(ValeParaPagoWrapper input) {
        seg.requireAnyRole(seg.APROBAR);
        return service.crearValeParaPago(
                input.getFuncionarioId(), input.getMotivoId(), input.getMonedaId(),
                input.getMonto() != null ? BigDecimal.valueOf(input.getMonto()) : null,
                input.getEsAdelanto(), input.getObservacion(), seg.currentUsuario());
    }

    /** Paga N vales como un único evento consolidado (caja mayor / banco / cheque). */
    public Pago pagarValesMixto(List<ValeConLineasWrapper> pagos) {
        seg.requireAnyRole(seg.APROBAR);
        List<ValeTesoreriaService.ValeConLineas> ls = new ArrayList<>();
        for (ValeConLineasWrapper w : pagos) {
            ValeTesoreriaService.ValeConLineas v = new ValeTesoreriaService.ValeConLineas();
            v.setValeId(w.getValeId());
            v.setLineas(PagoProveedorGraphQL.mapLineas(w.getLineas()));
            ls.add(v);
        }
        return service.pagarValesMixto(ls, seg.currentUsuario());
    }

    @Data
    public static class ValeParaPagoWrapper {
        private Long funcionarioId;
        private Long motivoId;
        private Long monedaId;
        private Double monto;
        private Boolean esAdelanto;
        private String observacion;
    }

    @Data
    public static class ValeConLineasWrapper {
        private Long valeId;
        private List<PagoProveedorGraphQL.LineaPagoInputWrapper> lineas;
    }
}
