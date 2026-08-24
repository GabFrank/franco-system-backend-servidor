package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.service.rrhh.LiquidacionSueldoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class LiquidacionSueldoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private LiquidacionSueldoService service;

    @Autowired
    private com.franco.dev.service.rrhh.ReciboLiquidacionService reciboLiquidacionService;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    public Optional<LiquidacionSueldo> liquidacionSueldo(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    /** Recibo de sueldo. anchoMm null = PDF A4; 58/80 = ticket; escpos=true = payload ESC/POS. */
    public String imprimirReciboLiquidacion(Long id, Integer anchoMm, Boolean escpos) {
        return reciboLiquidacionService.generarBase64(id, anchoMm, Boolean.TRUE.equals(escpos));
    }

    public List<LiquidacionSueldo> liquidacionesPorFuncionario(Long funcionarioId) {
        seg.requireVer();
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<LiquidacionSueldo> liquidacionesPorPeriodo(String periodo) {
        seg.requireVer();
        return service.findByPeriodo(periodo);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<LiquidacionSueldo> liquidacionesPage(int page, int size, Long funcionarioId, String funcionarioNombre,
                                                     String periodo, LiquidacionSueldoEstado estado) {
        seg.requireVer();
        return service.findPage(funcionarioId, funcionarioNombre, periodo, estado, PageRequest.of(page, size));
    }

    public List<LiquidacionItem> liquidacionItems(Long liquidacionId) {
        seg.requireVer();
        return service.findItems(liquidacionId);
    }

    public LiquidacionSueldo generarLiquidacionBorrador(Long funcionarioId, String periodo, Long monedaId) {
        seg.requireAnyRole(seg.LIQUIDAR);
        return service.generarBorrador(funcionarioId, periodo, monedaId);
    }

    public LiquidacionItem agregarItemLiquidacion(Long liquidacionId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo) {
        seg.requireAnyRole(seg.LIQUIDAR);
        return service.agregarItemManual(liquidacionId, descripcion, monto, tipo);
    }

    public Boolean eliminarItemLiquidacion(Long itemId) {
        seg.requireAnyRole(seg.LIQUIDAR);
        return service.eliminarItem(itemId);
    }

    public LiquidacionItem editarItemLiquidacion(Long itemId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo, Long usuarioId) {
        seg.requireAnyRole(seg.LIQUIDAR);
        return service.editarItem(itemId, descripcion, monto, tipo, usuarioId);
    }

    public LiquidacionSueldo aprobarLiquidacion(Long id, Long aprobadoPorId) {
        seg.requireAnyRole(seg.APROBAR);
        return service.aprobar(id, aprobadoPorId);
    }

    public LiquidacionSueldo volverBorradorLiquidacion(Long id) {
        seg.requireAnyRole(seg.LIQUIDAR, seg.APROBAR);
        return service.volverBorrador(id);
    }

    public LiquidacionSueldo pagarLiquidacion(Long id, Long cajaVirtualId) {
        seg.requireAnyRole(seg.PAGAR);
        return service.pagar(id, cajaVirtualId);
    }

    public LiquidacionSueldo anularLiquidacion(Long id) {
        seg.requireAnyRole(seg.PAGAR);
        return service.anular(id);
    }

    public Integer generarLiquidacionesMes(String periodo, Long monedaId) {
        seg.requireAnyRole(seg.LIQUIDAR);
        return service.generarMes(periodo, monedaId);
    }

    /** Genera borradores para una lista de funcionarios (vacia/null = todos los activos). */
    public Integer generarLiquidacionesLote(List<Integer> funcionarioIds, String periodo, Long monedaId) {
        seg.requireAnyRole(seg.LIQUIDAR);
        // [Int] de GraphQL llega como List<Integer>; kickstart no convierte los elementos.
        List<Long> ids = funcionarioIds == null ? null
                : funcionarioIds.stream().map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        return service.generarLote(ids, periodo, monedaId);
    }
}
