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

    // ----- Consulta -----

    public List<LiquidacionSueldo> misRecibosMobile(Long usuarioId) {
        return service.misRecibos(usuarioId);
    }

    public List<Vale> misValesMobile(Long usuarioId) {
        return service.misVales(usuarioId);
    }

    public List<Vacacion> misVacacionesMobile(Long usuarioId) {
        return service.misVacaciones(usuarioId);
    }

    public List<Jornada> misMarcacionesMobile(Long usuarioId) {
        return service.misMarcaciones(usuarioId);
    }

    public ResumenRrhhMobileDto miResumenRrhhMobile(Long usuarioId) {
        return service.miResumen(usuarioId);
    }

    public List<Vale> valesPendientesAprobacionMobile() {
        return service.valesPendientesAprobacion();
    }

    public List<VacacionPeriodo> vacacionesPendientesAprobacionMobile() {
        return service.vacacionesPendientesAprobacion();
    }

    // ----- Solicitudes / aprobaciones -----

    public Vale solicitarValeMobile(Long usuarioId, Long motivoId, BigDecimal monto, Boolean esAdelanto) {
        return service.solicitarVale(usuarioId, motivoId, monto, esAdelanto);
    }

    public VacacionPeriodo solicitarVacacionMobile(Long usuarioId, String desde, String hasta) {
        return service.solicitarVacacion(usuarioId, parseFecha(desde), parseFecha(hasta));
    }

    public VacacionPeriodo aprobarVacacionMobile(Long periodoId, Long aprobadorUsuarioId) {
        return service.aprobarVacacion(periodoId, aprobadorUsuarioId);
    }

    private LocalDate parseFecha(String s) {
        if (s == null || s.isBlank()) return null;
        return stringToDate(s) != null ? stringToDate(s).toLocalDate() : null;
    }
}
