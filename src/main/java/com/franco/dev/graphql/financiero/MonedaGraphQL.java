package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.dto.PreciosEnMonedaDto;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.service.financiero.ConversionPrecioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.hibernate.exception.ConstraintViolationException;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MonedaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(MonedaGraphQL.class);

    @Autowired
    private TesoreriaSecurityService seg;

    @Autowired
    private MonedaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;


    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private ConversionPrecioService conversionPrecioService;

    public Optional<Moneda> moneda(Long id) {
        return service.findById(id);
    }

    /** Kiosco de precios de la PWA. Ver ConversionPrecioService. */
    public List<PreciosEnMonedaDto> convertirPreciosMobile(List<Double> montosGs) {
        return conversionPrecioService.convertir(montosGs);
    }

    /** El kiosco con precios en otra moneda (V260.5): montos en monedaOrigenId (null = guaranies). */
    public List<PreciosEnMonedaDto> convertirPreciosDesdeMobile(List<Double> montos, Long monedaOrigenId) {
        Moneda origen = monedaOrigenId != null ? service.findById(monedaOrigenId).orElse(null) : null;
        if (monedaOrigenId != null && origen == null) return new java.util.ArrayList<>();
        return conversionPrecioService.convertir(montos, origen);
    }

    public List<Moneda> monedas(int page, int size) {
        return service.findAll2();
    }


    /**
     * Reemplazo completo del registro, salvo dos campos que se conservan:
     * <ul>
     *   <li>{@code principal}: la moneda de la empresa no se cambia desde el ABM. Pasarla a otra
     *   moneda cambia la base de todos los modulos y se hace solo con el procedimiento de corte.
     *   Una moneda nueva nace no principal.</li>
     *   <li>{@code codigoIso}, cuando el input no lo trae: un cliente anterior a V258.5 no conoce el
     *   campo y lo borraria al guardar cualquier otro cambio.</li>
     * </ul>
     */
    public Moneda saveMoneda(MonedaInput input) {
        seg.requireGestionar();
        ModelMapper m = new ModelMapper();
        Moneda e = m.map(input, Moneda.class);
        Moneda actual = input.getId() != null ? service.findById(input.getId()).orElse(null) : null;
        e.setPrincipal(actual != null ? actual.getPrincipal() : Boolean.FALSE);
        if (input.getCodigoIso() == null) {
            e.setCodigoIso(actual != null ? actual.getCodigoIso() : null);
        } else {
            String iso = input.getCodigoIso().trim().toUpperCase(java.util.Locale.ROOT);
            if (iso.isEmpty()) {
                e.setCodigoIso(null);
            } else if (!iso.matches("[A-Z]{3}")) {
                throw new graphql.GraphQLException("El codigo ISO tiene que ser de tres letras (PYG, USD, BRL).");
            } else {
                // El indice unico lo rechazaria igual, pero con un error de Hibernate ilegible.
                Moneda otra = service.porCodigoIso(iso);
                if (otra != null && !otra.getId().equals(input.getId())) {
                    throw new graphql.GraphQLException("El codigo ISO " + iso + " ya lo tiene " + otra.getDenominacion() + ".");
                }
                e.setCodigoIso(iso);
            }
        }
        // El ABM del desktop no lee el usuario y a veces no trae el pais: sin id se conserva el guardado
        // (findById(null) tiraba NPE y editar una moneda fallaba).
        e.setUsuario(input.getUsuarioId() != null
                ? usuarioService.findById(input.getUsuarioId()).orElse(null)
                : (actual != null ? actual.getUsuario() : null));
        e.setPais(input.getPaisId() != null
                ? paisService.findById(input.getPaisId()).orElse(null)
                : (actual != null ? actual.getPais() : null));
        try {
            return service.save(e);
        } catch (DataIntegrityViolationException ex) {
            // La base la rechazo al confirmar (CrudService.save es transaccional): el detalle va al log y
            // el usuario ve que paso, no el error de Hibernate (#602).
            log.warn("Alta/edicion de moneda rechazada por la base: id={} denominacion={} restriccion={} causa={}",
                    e.getId(), e.getDenominacion(), restriccion(ex), ex.getMostSpecificCause().getMessage());
            throw new graphql.GraphQLException(mensajeRechazo(ex, e.getCodigoIso()));
        }
    }

    /** El mensaje para el usuario segun la restriccion que rechazo el guardado. */
    static String mensajeRechazo(DataIntegrityViolationException ex, String iso) {
        String r = restriccion(ex).toLowerCase(java.util.Locale.ROOT);
        if (r.contains("moneda_pkey")) {
            return "No se pudo guardar la moneda: la numeracion de monedas de la base quedo atrasada. Avisa a soporte.";
        }
        if (r.contains("uq_moneda_codigo_iso")) {
            return "El codigo ISO " + (iso != null ? iso + " " : "") + "ya lo tiene otra moneda.";
        }
        if (r.contains("uq_moneda_principal")) {
            return "Ya hay otra moneda principal.";
        }
        return "No se pudo guardar la moneda: la base la rechazo. El detalle quedo en el registro del servidor.";
    }

    /** Nombre de la restriccion; si Hibernate no lo trae, el texto de la causa (PostgreSQL lo nombra ahi). */
    private static String restriccion(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException && ((ConstraintViolationException) t).getConstraintName() != null) {
                return ((ConstraintViolationException) t).getConstraintName();
            }
        }
        Throwable causa = ex.getMostSpecificCause();
        return String.valueOf(causa != null ? causa.getMessage() : ex.getMessage());
    }

    public List<Moneda> monedasSearch(String texto) {
        return service.findByAll(texto);
    }

    public Boolean deleteMoneda(Long id) {
        seg.requireGestionar();
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countMoneda() {
        return service.count();
    }


}
