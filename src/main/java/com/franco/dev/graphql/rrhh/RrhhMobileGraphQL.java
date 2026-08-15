package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.domain.rrhh.VacacionPeriodo;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.service.rrhh.RrhhMobileService;
import com.franco.dev.service.rrhh.dto.ResumenRrhhMobileDto;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class RrhhMobileGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private RrhhMobileService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    /**
     * Id del usuario autenticado (del SecurityContext). Los endpoints "mis*" y
     * "solicitar*" IGNORAN el usuarioId que manda el cliente y usan este, para que
     * nadie pueda leer/operar a nombre de otro empleado (el parametro se mantiene en
     * la firma por compatibilidad con el cliente mobile, pero no se confia en el).
     */
    private Long miId() {
        com.franco.dev.domain.personas.Usuario u = seg.currentUsuario();
        if (u == null) throw new graphql.GraphQLException("No autenticado");
        return u.getId();
    }

    // ----- Consulta -----

    public List<LiquidacionSueldo> misRecibosMobile(Long usuarioId, Integer page, Integer size) {
        if (page == null && size == null) {
            return service.misRecibos(miId());
        }
        return service.misRecibos(miId(), page, size);
    }

    public List<Vale> misValesMobile(Long usuarioId, Integer page, Integer size) {
        if (page == null && size == null) {
            return service.misVales(miId());
        }
        return service.misVales(miId(), page, size);
    }

    public List<Vacacion> misVacacionesMobile(Long usuarioId) {
        return service.misVacaciones(miId());
    }

    /**
     * ⚠️ `page` y `size` son opcionales para no romper a los clientes que ya
     * llaman sin ellos, pero el mobile SIEMPRE los manda: sin paginar, esto
     * devuelve una fila por cada dia trabajado desde que el funcionario
     * entro.
     */
    public List<Jornada> misMarcacionesMobile(Long usuarioId, Integer page, Integer size) {
        if (page == null && size == null) {
            return service.misMarcaciones(miId());
        }
        return service.misMarcaciones(miId(), page, size);
    }

    public ResumenRrhhMobileDto miResumenRrhhMobile(Long usuarioId) {
        return service.miResumen(miId());
    }

    public List<Vale> valesPendientesAprobacionMobile() {
        seg.requireAnyRole(seg.APROBAR);
        return service.valesPendientesAprobacion();
    }

    public List<VacacionPeriodo> vacacionesPendientesAprobacionMobile() {
        seg.requireAnyRole(seg.APROBAR);
        return service.vacacionesPendientesAprobacion();
    }

    // ----- Solicitudes / aprobaciones -----

    public Vale solicitarValeMobile(Long usuarioId, Long motivoId, BigDecimal monto, Boolean esAdelanto) {
        return service.solicitarVale(miId(), motivoId, monto, esAdelanto);
    }

    public VacacionPeriodo solicitarVacacionMobile(Long usuarioId, String desde, String hasta) {
        return service.solicitarVacacion(miId(), parseFecha(desde), parseFecha(hasta));
    }

    public VacacionPeriodo aprobarVacacionMobile(Long periodoId, Long aprobadorUsuarioId) {
        seg.requireAnyRole(seg.APROBAR);
        return service.aprobarVacacion(periodoId, miId());
    }

    private LocalDate parseFecha(String s) {
        if (s == null || s.isBlank()) return null;
        return stringToDate(s) != null ? stringToDate(s).toLocalDate() : null;
    }
}
