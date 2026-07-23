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

    public Optional<LiquidacionSueldo> liquidacionSueldo(Long id) {
        return service.findById(id);
    }

    /** Recibo de sueldo en PDF (base64) de la liquidación. */
    public String imprimirReciboLiquidacion(Long id) {
        return reciboLiquidacionService.generarBase64(id);
    }

    public List<LiquidacionSueldo> liquidacionesPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<LiquidacionSueldo> liquidacionesPorPeriodo(String periodo) {
        return service.findByPeriodo(periodo);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<LiquidacionSueldo> liquidacionesPage(int page, int size, Long funcionarioId, String periodo,
                                                     LiquidacionSueldoEstado estado) {
        return service.findPage(funcionarioId, periodo, estado, PageRequest.of(page, size));
    }

    public List<LiquidacionItem> liquidacionItems(Long liquidacionId) {
        return service.findItems(liquidacionId);
    }

    public LiquidacionSueldo generarLiquidacionBorrador(Long funcionarioId, String periodo, Long monedaId) {
        return service.generarBorrador(funcionarioId, periodo, monedaId);
    }

    public LiquidacionItem agregarItemLiquidacion(Long liquidacionId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo) {
        return service.agregarItemManual(liquidacionId, descripcion, monto, tipo);
    }

    public Boolean eliminarItemLiquidacion(Long itemId) {
        return service.eliminarItem(itemId);
    }

    public LiquidacionSueldo aprobarLiquidacion(Long id, Long aprobadoPorId) {
        return service.aprobar(id, aprobadoPorId);
    }

    public LiquidacionSueldo volverBorradorLiquidacion(Long id) {
        return service.volverBorrador(id);
    }

    public LiquidacionSueldo pagarLiquidacion(Long id, Long cajaVirtualId) {
        return service.pagar(id, cajaVirtualId);
    }

    public LiquidacionSueldo anularLiquidacion(Long id) {
        return service.anular(id);
    }

    public Integer generarLiquidacionesMes(String periodo, Long monedaId) {
        return service.generarMes(periodo, monedaId);
    }

    /** Genera borradores para una lista de funcionarios (vacia/null = todos los activos). */
    public Integer generarLiquidacionesLote(List<Integer> funcionarioIds, String periodo, Long monedaId) {
        // [Int] de GraphQL llega como List<Integer>; kickstart no convierte los elementos.
        List<Long> ids = funcionarioIds == null ? null
                : funcionarioIds.stream().map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        return service.generarLote(ids, periodo, monedaId);
    }
}
