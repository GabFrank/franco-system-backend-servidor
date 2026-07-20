package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.financiero.EnteCuota;
import com.franco.dev.graphql.financiero.dto.CuotasDetalleCalculado;
import com.franco.dev.graphql.financiero.input.CuotaDetalleInput;
import com.franco.dev.graphql.financiero.input.EnteCuotaInput;
import com.franco.dev.service.financiero.ActivoFinancieroSyncService;
import com.franco.dev.service.financiero.EnteCuotaService;
import com.franco.dev.service.financiero.EnteFinancieroService;
import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class EnteCuotaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EnteCuotaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EnteFinancieroService enteFinancieroService;

    @Autowired
    private ActivoFinancieroSyncService activoFinancieroSyncService;

    public Optional<EnteCuota> enteCuota(Long id) {
        return service.findById(id);
    }

    public List<EnteCuota> enteCuotas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countEnteCuota() {
        return service.count();
    }

    public List<EnteCuota> enteCuotasByEnteFinanciero(Long enteFinancieroId) {
        return service.findByEnteFinancieroId(enteFinancieroId);
    }

    public List<EnteCuota> enteCuotasByEnteId(Long enteId) {
        Optional<EnteFinanciero> financiero = enteFinancieroService.findByEnteId(enteId);
        if (financiero.isEmpty()) {
            return Collections.emptyList();
        }
        return service.findByEnteFinancieroId(financiero.get().getId());
    }

    public CustomPage<EnteCuota> enteCuotaSearchPage(Long enteFinancieroId, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<EnteCuota> pageResult = service.findAllByEnteFinancieroId(enteFinancieroId, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public CuotasDetalleCalculado calcularCuotasDetalle(
            Integer cantidadCuotas,
            Integer cantidadCuotasPagadas,
            Double montoTotal,
            Double montoYaPagado,
            List<CuotaDetalleInput> cuotasDetalle
    ) {
        return activoFinancieroSyncService.calcularCuotasDetalleCompleto(
                cantidadCuotas,
                cantidadCuotasPagadas,
                montoTotal != null ? BigDecimal.valueOf(montoTotal) : BigDecimal.ZERO,
                montoYaPagado != null ? BigDecimal.valueOf(montoYaPagado) : BigDecimal.ZERO,
                cuotasDetalle
        );
    }

    public EnteCuota saveEnteCuota(EnteCuotaInput input) {
        EnteCuota e = new EnteCuota();
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new EnteCuota());
        }
        e.setNumeroCuota(input.getNumeroCuota());
        e.setMonto(input.getMonto());
        e.setPagado(input.getPagado());
        if (input.getEnteFinancieroId() != null) {
            e.setEnteFinanciero(enteFinancieroService.findById(input.getEnteFinancieroId()).orElse(null));
        }
        if (input.getFechaVencimiento() != null && !input.getFechaVencimiento().isEmpty()) {
            LocalDate fecha = DateUtils.stringToDate(input.getFechaVencimiento()).toLocalDate();
            e.setFechaVencimiento(fecha);
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar la cuota del ente: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteEnteCuota(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar la cuota del ente: " + err.getMessage());
        }
    }
}
