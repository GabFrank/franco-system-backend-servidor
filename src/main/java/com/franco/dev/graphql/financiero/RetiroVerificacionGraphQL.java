package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.RetiroCaso;
import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.enums.CategoriaDiferenciaRetiro;
import com.franco.dev.domain.financiero.enums.EstadoCasoRetiro;
import com.franco.dev.domain.financiero.enums.VeredictoCasoRetiro;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import com.franco.dev.service.financiero.RetiroCasoService;
import com.franco.dev.service.financiero.RetiroVerificacionService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class RetiroVerificacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final RetiroVerificacionService service;
    private final RetiroVerificacionRepository verificacionRepository;
    private final RetiroCasoRepository casoRepository;
    private final RetiroCasoService casoService;
    private final TesoreriaSecurityService seg;

    public RetiroVerificacion verificacionDeRetiro(Long retiroId, Long sucursalId) {
        seg.requireVer();
        return verificacionRepository.findVigente(retiroId, sucursalId).orElse(null);
    }

    public Page<RetiroCaso> retiroCasos(EstadoCasoRetiro estado, Long sucursalId, Long retiroId,
                                       String desde, String hasta, Boolean soloMios,
                                       int page, int size) {
        seg.requireVer();
        Long usuarioId = Boolean.TRUE.equals(soloMios) && seg.currentUsuario() != null
                ? seg.currentUsuario().getId() : null;
        return casoRepository.filter(
                estado != null ? estado.name() : null,
                sucursalId, retiroId,
                parseFechaInicio(desde), parseFechaFin(hasta),
                usuarioId,
                PageRequest.of(page, size));
    }

    /**
     * El rango se toma por día completo, de 00:00:00 a 23:59:59.
     *
     * El front manda la fecha con la hora en que se tocó el filtro. Sin normalizar, filtrar
     * "desde hoy" a las 14:38 dejaría afuera un caso abierto hoy a las 13:39 — y el operador
     * no tendría forma de entender por qué no aparece.
     */
    private LocalDateTime parseFechaInicio(String f) {
        LocalDateTime d = parseFechaCruda(f);
        return d != null ? d.toLocalDate().atStartOfDay() : null;
    }

    private LocalDateTime parseFechaFin(String f) {
        LocalDateTime d = parseFechaCruda(f);
        return d != null ? d.toLocalDate().atTime(23, 59, 59) : null;
    }

    private LocalDateTime parseFechaCruda(String f) {
        if (f == null || f.trim().isEmpty()) return null;
        return com.franco.dev.utilitarios.DateUtils.stringToDate(f);
    }

    public Integer retiroCasosAbiertos() {
        seg.requireVer();
        return (int) casoRepository.countByEstado(EstadoCasoRetiro.ABIERTO);
    }

    /**
     * Verifica un retiro y lo acredita. El ACL de caja lo aplica {@code TesoreriaService.registrar},
     * que es el choke point por donde pasa toda la plata — no hace falta duplicarlo acá.
     */
    public RetiroVerificacion verificarRetiro(Long retiroId, Long sucursalId, Long cajaVirtualId,
                                              List<Map<String, Object>> conteos, Boolean rapida,
                                              String observacion) {
        seg.requireGestionar();
        return service.verificar(retiroId, sucursalId, cajaVirtualId,
                mapearConteos(conteos), Boolean.TRUE.equals(rapida), observacion, seg.currentUsuario());
    }

    /** Mismo rol que verificar: el que contó mal puede corregirlo en el momento. */
    public RetiroVerificacion anularVerificacionRetiro(Long verificacionId, String motivo) {
        seg.requireGestionar();
        return service.anular(verificacionId, motivo, seg.currentUsuario());
    }

    public RetiroCaso asignarRetiroCaso(Long casoId, Long usuarioId) {
        seg.requireGestionar();
        return casoService.asignar(casoId, usuarioId, seg.esSuperusuario());
    }

    /** Devuelve el caso a ABIERTO: se tomó por error o no corresponde. */
    public RetiroCaso soltarRetiroCaso(Long casoId) {
        seg.requireGestionar();
        return casoService.soltar(casoId);
    }

    /**
     * Cierra el caso con un veredicto y, si se pide, anula la verificación en la misma transacción
     * (ver {@link RetiroCasoService#resolver}).
     */
    public RetiroCaso resolverRetiroCaso(Long casoId, VeredictoCasoRetiro veredicto, String resolucion,
                                         Long responsablePersonaId, Long reintegroRetiroId,
                                         Boolean anularVerificacion) {
        seg.requireGestionar();
        return casoService.resolver(casoId, veredicto, resolucion, responsablePersonaId, reintegroRetiroId,
                anularVerificacion, seg.currentUsuario(), seg.esSuperusuario());
    }

    private List<RetiroVerificacionService.ConteoMoneda> mapearConteos(List<Map<String, Object>> conteos) {
        List<RetiroVerificacionService.ConteoMoneda> res = new ArrayList<>();
        if (conteos == null) return res;
        for (Map<String, Object> m : conteos) {
            RetiroVerificacionService.ConteoMoneda c = new RetiroVerificacionService.ConteoMoneda();
            Object monedaId = m.get("monedaId");
            if (monedaId != null) c.setMonedaId(Long.valueOf(monedaId.toString()));
            Object contado = m.get("contado");
            if (contado != null) c.setContado(new BigDecimal(contado.toString()));
            Object categoria = m.get("categoria");
            if (categoria != null) c.setCategoria(CategoriaDiferenciaRetiro.valueOf(categoria.toString()));
            res.add(c);
        }
        return res;
    }
}
