package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.domain.rrhh.VacacionPeriodo;
import com.franco.dev.domain.rrhh.VacacionVenta;
import com.franco.dev.domain.rrhh.enums.VacacionPeriodoEstado;
import com.franco.dev.service.rrhh.VacacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class VacacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VacacionService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    public Optional<Vacacion> vacacion(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    public List<Vacacion> vacacionesPorFuncionario(Long funcionarioId) {
        seg.requireVer();
        return service.findByFuncionarioId(funcionarioId);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<Vacacion> vacacionesPage(int page, int size, Long funcionarioId, Integer anio) {
        seg.requireVer();
        return service.findPage(funcionarioId, anio, PageRequest.of(page, size));
    }

    public List<VacacionPeriodo> vacacionPeriodos(Long vacacionId) {
        seg.requireVer();
        return service.findPeriodos(vacacionId);
    }

    public List<VacacionPeriodo> vacacionPeriodosPorEstado(VacacionPeriodoEstado estado) {
        seg.requireVer();
        return service.findPeriodosPorEstado(estado);
    }

    public List<VacacionVenta> vacacionVentas(Long vacacionId) {
        seg.requireVer();
        return service.findVentas(vacacionId);
    }

    public Vacacion devengarVacacion(Long funcionarioId) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.devengar(funcionarioId);
    }

    public VacacionPeriodo programarPeriodoVacacion(Long vacacionId, String desde, String hasta,
                                                    VacacionPeriodoEstado estado, String observacion) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.programarPeriodo(
                vacacionId,
                stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null,
                stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null,
                estado, observacion);
    }

    public VacacionPeriodo aprobarPeriodoVacacion(Long periodoId, Long autorizadoPorId) {
        seg.requireAnyRole(seg.APROBAR);
        return service.aprobarPeriodo(periodoId, autorizadoPorId);
    }

    public VacacionPeriodo marcarGozadaVacacion(Long periodoId) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.marcarGozada(periodoId);
    }

    public VacacionVenta venderDiasVacacion(Long vacacionId, Integer dias, String observacion) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.venderDias(vacacionId, dias, observacion);
    }

    /** Autoriza la venta: recien ahi queda PENDIENTE y la liquidacion la paga. */
    public VacacionVenta aprobarVentaVacacion(Long ventaId, Long autorizadoPorId) {
        seg.requireAnyRole(seg.APROBAR);
        return service.aprobarVenta(ventaId, autorizadoPorId);
    }

    public VacacionVenta anularVentaVacacion(Long ventaId) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.anularVenta(ventaId);
    }
}
