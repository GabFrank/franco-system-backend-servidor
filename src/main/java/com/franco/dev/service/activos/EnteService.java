package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.activos.EnteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.service.financiero.PreGastoService;
import com.franco.dev.service.financiero.dto.EnteFinancialSummaryDTO;
import org.springframework.context.annotation.Lazy;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Optional;

@Service
public class EnteService extends CrudService<Ente, EnteRepository, Long> {

    private final EnteRepository repository;
    private final PreGastoService preGastoService;
    private final EnteSucursalService enteSucursalService;

    public EnteService(EnteRepository repository, @Lazy PreGastoService preGastoService, @Lazy EnteSucursalService enteSucursalService) {
        this.repository = repository;
        this.preGastoService = preGastoService;
        this.enteSucursalService = enteSucursalService;
    }

    @Override
    public EnteRepository getRepository() {
        return repository;
    }

    public List<Ente> findByTipoEnte(TipoEnte tipoEnte) {
        return repository.findByTipoEnte(tipoEnte);
    }

    public Optional<Ente> findByTipoEnteAndReferenciaId(TipoEnte tipoEnte, Long referenciaId) {
        return repository.findByTipoEnteAndReferenciaId(tipoEnte, referenciaId);
    }

    public List<Ente> findAllActivos() {
        return repository.findAllActivos();
    }

    public Page<Ente> findAllWithFilters(String texto, Long sucursalId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Ente> res = repository.findAllWithFilters(texto, sucursalId, pageable);
        res.getContent().forEach(ente -> {
            EnteFinancialSummaryDTO summary = preGastoService.getFinancialSummary(ente.getId());
            if (summary != null) {
                ente.setDescripcion(summary.getDescripcion());
                ente.setMontoTotal(summary.getMontoTotal() != null ? summary.getMontoTotal().doubleValue() : null);
                ente.setMontoYaPagado(summary.getMontoYaPagado() != null ? summary.getMontoYaPagado().doubleValue() : null);
                ente.setMontoPendiente(summary.getMontoPendiente() != null ? summary.getMontoPendiente().doubleValue() : null);
                ente.setCuotasTotales(summary.getCuotasTotales());
                ente.setCuotasPagadas(summary.getCuotasPagadas());
                ente.setCuotasFaltantes(summary.getCuotasFaltantes());
                ente.setDiaVencimiento(summary.getDiaVencimiento());
                ente.setDiasParaVencer(summary.getDiasParaVencer());
                ente.setEstadoCuota(summary.getEstadoCuota());
                ente.setSituacionPago(summary.getSituacionPago());
                ente.setMonedaSimbolo(summary.getMonedaSimbolo());
                ente.setProveedorNombre(summary.getProveedorNombre());
            }
            List<EnteSucursal> asignaciones = enteSucursalService.findByEnteId(ente.getId());
            if (asignaciones != null && !asignaciones.isEmpty()) {
                ente.setSucursalesConcatenadas(asignaciones.stream()
                        .map(a -> a.getSucursal().getNombre())
                        .collect(Collectors.joining(", ")));
                ente.setSucursalIds(asignaciones.stream()
                        .map(a -> a.getSucursal().getId())
                        .collect(Collectors.toList()));
            } else {
                ente.setSucursalesConcatenadas("Sin sucursal");
            }
        });
        return res;
    }

    @Override
    public Ente save(Ente entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getActivo() == null) {
            entity.setActivo(true);
        }
        return super.save(entity);
    }

    public Ente ensureEnteForReferencia(TipoEnte tipoEnte, Long referenciaId, Usuario usuario) {
        if (tipoEnte == null || referenciaId == null) {
            return null;
        }

        Ente ente = repository.findByTipoEnteAndReferenciaId(tipoEnte, referenciaId).orElseGet(Ente::new);
        ente.setTipoEnte(tipoEnte);
        ente.setReferenciaId(referenciaId);
        ente.setActivo(true);
        if (usuario != null) {
            ente.setUsuario(usuario);
        }
        return save(ente);
    }
}
