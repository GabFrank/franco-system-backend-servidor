package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.service.financiero.MaletinTesoreriaService;
import com.franco.dev.service.financiero.MaletinTesoreriaService.ValorMaletinItem;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ingreso/egreso de maletín en la caja mayor + consulta del valor estimado dentro del maletín.
 */
@Component
@AllArgsConstructor
public class MaletinTesoreriaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final MaletinTesoreriaService service;
    private final TesoreriaSecurityService seg;
    private final com.franco.dev.service.financiero.AltaIdempotenteService altaIdempotente;

    public List<ValorMaletinItem> valorMaletin(Long maletinId) {
        seg.requireVer();
        return service.valorMaletin(maletinId);
    }

    public MovimientoCajaVirtual ingresarMaletinCajaMayor(Long cajaVirtualId, Long maletinId, Long monedaId,
                                                          Double monto, String descripcion,
                                                          String claveIdempotencia) {
        seg.requireGestionar();
        BigDecimal m = com.franco.dev.service.financiero.AltaIdempotenteService.monto(monto);
        com.franco.dev.domain.personas.Usuario usuario = seg.currentUsuario();
        return altaIdempotente.maletin(true, claveIdempotencia, huellaDe(cajaVirtualId, maletinId, monedaId, m, descripcion),
                usuario, () -> service.ingresarMaletin(cajaVirtualId, maletinId, monedaId, m, descripcion, usuario));
    }

    private static String huellaDe(Long cajaVirtualId, Long maletinId, Long monedaId, BigDecimal monto, String descripcion) {
        return new com.franco.dev.service.financiero.HuellaPedido().id(cajaVirtualId).id(maletinId).id(monedaId).numero(monto).texto(descripcion).calcular();
    }

    public List<MovimientoCajaVirtual> ingresarMaletinCierre(Long cajaVirtualId, Long maletinId,
                                                             List<Long> monedaIds, String descripcion) {
        seg.requireGestionar();
        return service.ingresarMaletinCierre(cajaVirtualId, maletinId, monedaIds, descripcion, seg.currentUsuario());
    }

    public MovimientoCajaVirtual egresarMaletinCajaMayor(Long cajaVirtualId, Long maletinId, Long monedaId,
                                                         Double monto, String descripcion,
                                                         String claveIdempotencia) {
        seg.requireGestionar();
        BigDecimal m = com.franco.dev.service.financiero.AltaIdempotenteService.monto(monto);
        com.franco.dev.domain.personas.Usuario usuario = seg.currentUsuario();
        return altaIdempotente.maletin(false, claveIdempotencia, huellaDe(cajaVirtualId, maletinId, monedaId, m, descripcion),
                usuario, () -> service.egresarMaletin(cajaVirtualId, maletinId, monedaId, m, descripcion, usuario));
    }
}
