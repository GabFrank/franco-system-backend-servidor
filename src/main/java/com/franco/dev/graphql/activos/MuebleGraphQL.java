package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Mueble;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.graphql.activos.input.MuebleInput;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.activos.FamiliaMuebleService;
import com.franco.dev.service.activos.MuebleService;
import com.franco.dev.service.activos.TipoMuebleService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.financiero.MonedaService;
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
public class MuebleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MuebleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private FamiliaMuebleService familiaMuebleService;

    @Autowired
    private TipoMuebleService tipoMuebleService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private EnteService enteService;

    public Optional<Mueble> mueble(Long id) {
        return service.findById(id);
    }

    public List<Mueble> muebles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countMueble() {
        return service.count();
    }

    public List<Mueble> muebleSearch(String texto) {
        return service.findByAll(texto);
    }

    public CustomPage<Mueble> muebleSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<Mueble> pageResult = service.findByAllWithPage(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public List<Mueble> mueblesByFamilia(Long familiaId) {
        return service.findByFamiliaId(familiaId);
    }

    public Mueble saveMueble(MuebleInput input) {
        Mueble e = new Mueble();
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new Mueble());
        }
        e.setIdentificador(input.getIdentificador());
        e.setDescripcion(input.getDescripcion());
        e.setConsumeEnergia(input.getConsumeEnergia());
        e.setConsumoValor(input.getConsumoValor());
        e.setValorTasacion(input.getValorTasacion());
        e.setValorTasacionPyg(input.getValorTasacionPyg());
        e.setValorTasacionBrl(input.getValorTasacionBrl());
        e.setSituacionPago(input.getSituacionPago());
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

        if (input.getPropietarioId() != null) {
            e.setPropietario(personaService.findById(input.getPropietarioId()).orElse(null));
        }
        if (input.getFamiliaId() != null) {
            e.setFamilia(familiaMuebleService.findById(input.getFamiliaId()).orElse(null));
        }
        if (input.getTipoMuebleId() != null) {
            e.setTipoMueble(tipoMuebleService.findById(input.getTipoMuebleId()).orElse(null));
        }
        if (input.getProveedorId() != null) {
            e.setProveedor(personaService.findById(input.getProveedorId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
            enteService.ensureEnteForReferencia(TipoEnte.MUEBLE, e.getId(), e.getDescripcion(), e.getUsuario());
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el mueble: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteMueble(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el mueble: " + err.getMessage());
        }
    }
}
