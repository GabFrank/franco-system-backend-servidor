package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Inmueble;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.graphql.activos.input.InmuebleInput;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.activos.InmuebleService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class InmuebleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InmuebleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private CiudadService ciudadService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private EnteService enteService;

    @Autowired
    private ProveedorService proveedorService;

    public Optional<Inmueble> inmueble(Long id) {
        return service.findById(id);
    }

    public List<Inmueble> inmuebles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countInmueble() {
        return service.count();
    }

    public List<Inmueble> inmuebleSearch(String texto) {
        return service.findByAll(texto);
    }

    public CustomPage<Inmueble> inmuebleSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<Inmueble> pageResult = service.findByAllWithPage(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public Inmueble saveInmueble(InmuebleInput input) {
        Inmueble e = new Inmueble();
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new Inmueble());
        }
        e.setNombreAsignado(toUpperCase(input.getNombreAsignado()));
        e.setDireccion(toUpperCase(input.getDireccion()));
        e.setGoogleMapsUrl(input.getGoogleMapsUrl());
        e.setCodigoCatastral(toUpperCase(input.getCodigoCatastral()));
        e.setValorTasacion(input.getValorTasacion());
        e.setValorTasacionPyg(input.getValorTasacionPyg());
        e.setValorTasacionBrl(input.getValorTasacionBrl());

        if (input.getPropietarioId() != null) {
            e.setPropietario(personaService.findById(input.getPropietarioId()).orElse(null));
        }
        if (input.getPaisId() != null) {
            e.setPais(paisService.findById(input.getPaisId()).orElse(null));
        }
        if (input.getCiudadId() != null) {
            e.setCiudad(ciudadService.findById(input.getCiudadId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        
        e.setSituacionPago(toUpperCase(input.getSituacionPago()));
        e.setMontoTotal(input.getMontoTotal());
        e.setMontoYaPagado(input.getMontoYaPagado());
        e.setCantidadCuotas(input.getCantidadCuotas());
        e.setCantidadCuotasPagadas(input.getCantidadCuotasPagadas());
        e.setDiaVencimiento(input.getDiaVencimiento());

        if ("PAGADO".equalsIgnoreCase(input.getSituacionPago())) {
            e.setCantidadCuotasPagadas(input.getCantidadCuotas() != null ? input.getCantidadCuotas() : 0);
            e.setMontoYaPagado(input.getMontoTotal() != null ? input.getMontoTotal() : java.math.BigDecimal.ZERO);
        } else if ("PAGANDO".equalsIgnoreCase(input.getSituacionPago())) {
            boolean hasMonto = input.getMontoTotal() != null && input.getMontoTotal().compareTo(java.math.BigDecimal.ZERO) > 0;
            boolean hasCuotas = input.getCantidadCuotas() != null && input.getCantidadCuotas() > 0;
            
            boolean isMontoPagado = hasMonto && input.getMontoYaPagado() != null && input.getMontoYaPagado().compareTo(input.getMontoTotal()) >= 0;
            boolean isCuotasPagadas = hasCuotas && input.getCantidadCuotasPagadas() != null && input.getCantidadCuotasPagadas() >= input.getCantidadCuotas();
            
            boolean isFullyPaid = false;
            if (hasMonto && hasCuotas) {
                isFullyPaid = isMontoPagado && isCuotasPagadas;
            } else if (hasMonto) {
                isFullyPaid = isMontoPagado;
            } else if (hasCuotas) {
                isFullyPaid = isCuotasPagadas;
            }

            if (isFullyPaid) {
                e.setSituacionPago("PAGADO");
                if (hasMonto) e.setMontoYaPagado(input.getMontoTotal());
                if (hasCuotas) e.setCantidadCuotasPagadas(input.getCantidadCuotas());
            }
        }

        if (input.getProveedorId() != null) {
            e.setProveedor(proveedorService.findById(input.getProveedorId())
                    .orElseThrow(() -> new GraphQLException("El proveedor seleccionado no es valido.")));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }

        try {
            e = service.save(e);
            enteService.ensureEnteForReferencia(TipoEnte.INMUEBLE, e.getId(), e.getNombreAsignado(), e.getUsuario());
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el inmueble: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteInmueble(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el inmueble: " + err.getMessage());
        }
    }

    private String toUpperCase(String value) {
        return value != null ? value.toUpperCase() : null;
    }
}
