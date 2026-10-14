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

    @Autowired
    private com.franco.dev.service.financiero.AnulacionPagoRrhhService anulacionPagoRrhhService;

    // Rol acotado RRHH SUCURSAL: ve, genera y edita solo lo de los funcionarios de sus sucursales. Lo que
    // sigue con seg.requireVer()/requireAnyRole no lo acepta: aprobar, volver a borrador, pagar por el
    // atajo, anular y los listados por período.
    @Autowired
    private com.franco.dev.service.rrhh.RrhhAlcanceService alcance;

    public Optional<LiquidacionSueldo> liquidacionSueldo(Long id) {
        alcance.requireVerLiquidacion(id);
        return service.findById(id);
    }

    /** Buscador de funcionarios del rol acotado: solo los de las sucursales a su cargo. */
    public List<com.franco.dev.domain.personas.Funcionario> funcionariosACargo(String texto, Boolean soloActivos) {
        seg.requireAnyRole(seg.SUCURSAL);
        return alcance.buscarFuncionarios(texto, !Boolean.FALSE.equals(soloActivos));
    }

    /** Recibo de sueldo. anchoMm null = PDF A4; 58/80 = ticket; escpos=true = payload ESC/POS. */
    public String imprimirReciboLiquidacion(Long id, Integer anchoMm, Boolean escpos) {
        // Rol RRHH, o autoservicio: el funcionario baja el recibo de su propia liquidacion
        // pagada (la PWA lo pide con usuarios sin rol). El rechazo no distingue "no existe",
        // "es de otro" ni "no esta pagada", para no confirmar ids.
        boolean acotadoEnAlcance = seg.hasAnyRole(seg.SUCURSAL) && alcance.liquidacionEnAlcance(id);
        if (!seg.hasAnyRole(seg.TODOS) && !acotadoEnAlcance
                && !service.esReciboPagadoDe(id, seg.currentPersonaId())) {
            throw new graphql.GraphQLException("No autorizado: el recibo no está disponible para este usuario.");
        }
        return reciboLiquidacionService.generarBase64(id, anchoMm, Boolean.TRUE.equals(escpos));
    }

    public List<LiquidacionSueldo> liquidacionesPorFuncionario(Long funcionarioId) {
        alcance.requireVer();
        if (alcance.veAcotado()) alcance.exigirFuncionario(funcionarioId);
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<LiquidacionSueldo> liquidacionesPorPeriodo(String periodo) {
        seg.requireVer();
        return service.findByPeriodo(periodo);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<LiquidacionSueldo> liquidacionesPage(int page, int size, Long funcionarioId, String funcionarioNombre,
                                                     String periodo, LiquidacionSueldoEstado estado) {
        alcance.requireVer();
        if (alcance.veAcotado()) {
            // Vacío es «ninguna»: se corta acá, nunca llega una lista vacía a la consulta.
            List<Long> sucursales = alcance.sucursales();
            if (sucursales.isEmpty()) return Page.empty(PageRequest.of(page, size));
            return service.findPageEnSucursales(sucursales, funcionarioId, funcionarioNombre, periodo, estado,
                    PageRequest.of(page, size));
        }
        return service.findPage(funcionarioId, funcionarioNombre, periodo, estado, PageRequest.of(page, size));
    }

    public List<LiquidacionItem> liquidacionItems(Long liquidacionId) {
        alcance.requireVerLiquidacion(liquidacionId);
        return service.findItems(liquidacionId);
    }

    public LiquidacionSueldo generarLiquidacionBorrador(Long funcionarioId, String periodo, Long monedaId) {
        alcance.requireLiquidar();
        if (alcance.liquidaAcotado()) alcance.exigirFuncionario(funcionarioId);
        return service.generarBorrador(funcionarioId, periodo, monedaId);
    }

    public LiquidacionItem agregarItemLiquidacion(Long liquidacionId, String descripcion, BigDecimal monto,
                                                  LiquidacionItemTipo tipo, Long liquidacionConceptoId) {
        alcance.requireLiquidar();
        if (alcance.liquidaAcotado()) alcance.exigirLiquidacion(liquidacionId);
        return service.agregarItemManual(liquidacionId, descripcion, monto, tipo, liquidacionConceptoId);
    }

    public Boolean eliminarItemLiquidacion(Long itemId) {
        alcance.requireLiquidar();
        if (alcance.liquidaAcotado()) alcance.exigirItemDeLiquidacion(itemId);
        return service.eliminarItem(itemId);
    }

    public LiquidacionItem editarItemLiquidacion(Long itemId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo, Long usuarioId) {
        alcance.requireLiquidar();
        if (alcance.liquidaAcotado()) alcance.exigirItemDeLiquidacion(itemId);
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
        // Pagada desde tesoreria: se anula junto con su pago (pide ademas el rol de tesoreria).
        return anulacionPagoRrhhService.anularLiquidacion(id);
    }

    public Integer generarLiquidacionesMes(String periodo, Long monedaId) {
        seg.requireAnyRole(seg.LIQUIDAR);
        return service.generarMes(periodo, monedaId);
    }

    /** Genera borradores para una lista de funcionarios (vacia/null = todos los activos). */
    public Integer generarLiquidacionesLote(List<Integer> funcionarioIds, String periodo, Long monedaId) {
        alcance.requireLiquidar();
        // [Int] de GraphQL llega como List<Integer>; kickstart no convierte los elementos.
        List<Long> ids = funcionarioIds == null ? null
                : funcionarioIds.stream().map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        if (alcance.liquidaAcotado()) {
            if (ids == null || ids.isEmpty()) {
                // «Todos» son los activos de sus sucursales. Sin ninguno no se llama al servicio: para él
                // una lista vacía significa toda la empresa.
                ids = alcance.funcionariosActivosIds();
                if (ids.isEmpty()) return 0;
            } else {
                // Uno fuera de alcance rechaza el pedido entero: el lote se traga los errores por
                // funcionario y el rechazo pasaría en silencio.
                for (Long id : ids) alcance.exigirFuncionario(id);
            }
        }
        return service.generarLote(ids, periodo, monedaId);
    }
}
