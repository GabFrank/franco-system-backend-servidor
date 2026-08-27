package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.RetiroCaso;
import com.franco.dev.domain.financiero.RetiroVerificacionDetalle;
import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.enums.CategoriaDiferenciaRetiro;
import com.franco.dev.domain.financiero.enums.EstadoCasoRetiro;
import com.franco.dev.domain.financiero.enums.VeredictoCasoRetiro;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import com.franco.dev.service.financiero.RetiroVerificacionService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
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
    private final UsuarioService usuarioService;
    private final PersonaService personaService;
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
        RetiroCaso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new GraphQLException("Caso no encontrado: " + casoId));
        Usuario asignado = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));

        // El que contó no puede investigarse a sí mismo: quien verifica también puede ser el
        // problema, y esa es justamente una de las hipótesis que el caso deja abiertas.
        Usuario conto = caso.getVerificacion() != null ? caso.getVerificacion().getUsuario() : null;
        if (conto != null && conto.getId().equals(usuarioId)) {
            throw new GraphQLException("El caso no puede asignarse a quien hizo la verificación");
        }

        caso.setAsignadoA(asignado);
        caso.setEstado(EstadoCasoRetiro.EN_INVESTIGACION);
        return casoRepository.save(caso);
    }

    /**
     * Devuelve el caso a ABIERTO. Sin esto, tomarlo por error lo deja trabado a nombre de uno
     * para siempre.
     */
    public RetiroCaso soltarRetiroCaso(Long casoId) {
        seg.requireGestionar();
        RetiroCaso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new GraphQLException("Caso no encontrado: " + casoId));
        if (caso.getEstado() == EstadoCasoRetiro.RESUELTO) {
            throw new GraphQLException("El caso ya está resuelto");
        }
        caso.setEstado(EstadoCasoRetiro.ABIERTO);
        caso.setAsignadoA(null);
        return casoRepository.save(caso);
    }

    /**
     * Cierra el caso con un veredicto tipado.
     *
     * El veredicto es obligatorio porque es la única parte del cierre que se puede contar
     * después: cuántos faltantes tuvo una sucursal, cuántas veces contó mal el mismo receptor.
     * El informe explica; el veredicto clasifica.
     *
     * Cuando se determinó que contó mal tesorería, lo acreditado en la caja mayor quedó
     * equivocado, así que se ofrece anular la verificación en el mismo acto — cerrar el caso
     * dejando la caja con el monto errado sería documentar el problema y conservarlo.
     */
    public RetiroCaso resolverRetiroCaso(Long casoId, VeredictoCasoRetiro veredicto, String resolucion,
                                         Long responsablePersonaId, Long reintegroRetiroId,
                                         Boolean anularVerificacion) {
        seg.requireGestionar();
        RetiroCaso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new GraphQLException("Caso no encontrado: " + casoId));
        if (caso.getEstado() == EstadoCasoRetiro.RESUELTO) {
            throw new GraphQLException("El caso ya está resuelto");
        }

        // Cierra el que investigó, no cualquiera con el rol. Si no, "tomar" no significa nada
        // y el informe lo puede firmar alguien que no habló con nadie. El ADMIN pasa por encima
        // para destrabar casos de gente que ya no está.
        Usuario actual = seg.currentUsuario();
        Usuario asignado = caso.getAsignadoA();
        boolean esMio = asignado != null && actual != null && asignado.getId().equals(actual.getId());
        if (!esMio && !seg.esSuperusuario()) {
            throw new GraphQLException(asignado != null
                    ? "El caso lo está investigando " + nombreDe(asignado) + ". Que lo cierre esa persona, o que lo suelte."
                    : "Tomá el caso antes de resolverlo");
        }

        if (veredicto == null) {
            throw new GraphQLException("Falta el veredicto: sin él el caso no se puede clasificar");
        }
        VeredictoCasoRetiro v = veredicto;

        // Un responsable sin nombre no sirve para nada: el veredicto que apunta a un lado tiene
        // que decir a quién. Los otros dos veredictos existen justamente porque no hay a quién.
        boolean exigeResponsable = v == VeredictoCasoRetiro.FALTANTE_PDV
                || v == VeredictoCasoRetiro.SOBRANTE_PDV
                || v == VeredictoCasoRetiro.ERROR_DE_CONTEO_TESORERIA;
        if (exigeResponsable && responsablePersonaId == null) {
            throw new GraphQLException("Este veredicto necesita un responsable identificado");
        }
        if (v == VeredictoCasoRetiro.REINTEGRADO && reintegroRetiroId == null) {
            throw new GraphQLException("Indicá el retiro por el que se repuso la diferencia");
        }
        validarSigno(caso, v);
        if (Boolean.TRUE.equals(anularVerificacion) && v != VeredictoCasoRetiro.ERROR_DE_CONTEO_TESORERIA) {
            throw new GraphQLException("Solo se anula la verificación cuando el error fue del conteo de tesorería");
        }

        caso.setEstado(EstadoCasoRetiro.RESUELTO);
        caso.setVeredicto(v);
        caso.setResolucion(resolucion != null ? resolucion.toUpperCase() : null);
        caso.setReintegroRetiroId(v == VeredictoCasoRetiro.REINTEGRADO ? reintegroRetiroId : null);
        // El reintegro se busca en la misma sucursal del caso: un retiro de otra filial no
        // repone el faltante de esta.
        caso.setReintegroSucursalId(v == VeredictoCasoRetiro.REINTEGRADO ? caso.getSucursalId() : null);
        caso.setResponsablePersona(responsablePersonaId != null
                ? personaService.findById(responsablePersonaId).orElse(null) : null);
        caso.setResueltoPor(seg.currentUsuario());
        caso.setResueltoEn(LocalDateTime.now());
        RetiroCaso guardado = casoRepository.save(caso);

        // Después de guardar: anular() ve el caso ya RESUELTO y lo deja intacto en vez de
        // cerrarlo por su cuenta, con lo cual el veredicto del investigador sobrevive.
        if (Boolean.TRUE.equals(anularVerificacion) && caso.getVerificacion() != null) {
            service.anular(caso.getVerificacion().getId(),
                    "ERROR DE CONTEO - CASO " + caso.getId(), seg.currentUsuario());
        }
        return guardado;
    }

    /**
     * El veredicto tiene que coincidir con el signo de lo que se contó.
     *
     * "Vino menos" y "vino más" se miden siempre <b>desde el sobre</b> (contado − declarado), no
     * desde la caja del cajero, donde el mismo hecho se ve al revés: si al sobre le faltaron 10,
     * a esa caja le sobran 10. Sin este control, la mitad de los casos termina clasificada con el
     * signo invertido y el veredicto deja de servir para contar nada, que es su único propósito.
     *
     * Un retiro puede faltar en una moneda y sobrar en otra: alcanza con que exista una moneda
     * del signo elegido.
     */
    private void validarSigno(RetiroCaso caso, VeredictoCasoRetiro v) {
        if (v != VeredictoCasoRetiro.FALTANTE_PDV && v != VeredictoCasoRetiro.SOBRANTE_PDV) return;
        if (caso.getVerificacion() == null) return;

        boolean hayFaltante = false, haySobrante = false;
        for (RetiroVerificacionDetalle d : caso.getVerificacion().getDetalles()) {
            if (d.getDiferencia() == null) continue;
            int signo = d.getDiferencia().compareTo(java.math.BigDecimal.ZERO);
            if (signo < 0) hayFaltante = true;
            if (signo > 0) haySobrante = true;
        }

        if (v == VeredictoCasoRetiro.FALTANTE_PDV && !hayFaltante) {
            throw new GraphQLException(haySobrante
                    ? "El conteo dice que vino de más, no de menos. Revisá el veredicto."
                    : "Este retiro no tiene faltante registrado");
        }
        if (v == VeredictoCasoRetiro.SOBRANTE_PDV && !haySobrante) {
            throw new GraphQLException(hayFaltante
                    ? "El conteo dice que vino de menos, no de más. Si la plata quedó en la caja del cajero, igual al sobre le faltó."
                    : "Este retiro no tiene sobrante registrado");
        }
    }

    /** Nombre legible del usuario, para que el rechazo diga a quién reclamarle. */
    private String nombreDe(Usuario u) {
        if (u == null) return "otra persona";
        if (u.getPersona() != null && u.getPersona().getNombre() != null) return u.getPersona().getNombre();
        return u.getNickname() != null ? u.getNickname() : "otra persona";
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
