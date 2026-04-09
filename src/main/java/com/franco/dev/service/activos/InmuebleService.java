package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Inmueble;
import com.franco.dev.repository.activos.InmuebleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import org.springframework.context.annotation.Lazy;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InmuebleService extends CrudService<Inmueble, InmuebleRepository, Long> {

    private final InmuebleRepository repository;
    private final EnteService enteService;

    public InmuebleService(InmuebleRepository repository, @Lazy EnteService enteService) {
        this.repository = repository;
        this.enteService = enteService;
    }

    @Override
    public InmuebleRepository getRepository() {
        return repository;
    }

    public List<Inmueble> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public Page<Inmueble> findByAllWithPage(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable);
    }

    public List<Inmueble> findByPropietarioId(Long propietarioId) {
        return repository.findByPropietarioId(propietarioId);
    }

    public List<Inmueble> findByPaisId(Long paisId) {
        return repository.findByPaisId(paisId);
    }

    public List<Inmueble> findByCiudadId(Long ciudadId) {
        return repository.findByCiudadId(ciudadId);
    }
    @Override
    public Inmueble save(Inmueble entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        normalizeFinancialData(entity);
        Inmueble e = super.save(entity);
        if (e != null) {
            enteService.ensureEnteForReferencia(TipoEnte.INMUEBLE, e.getId(), e.getUsuario());
        }
        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        enteService.findByTipoEnteAndReferenciaId(TipoEnte.INMUEBLE, id).ifPresent(ente -> {
            enteService.deleteById(ente.getId());
        });
        return super.deleteById(id);
    }

    private void normalizeFinancialData(Inmueble entity) {
        if (entity.getSituacionPago() != null) {
            String situacion = entity.getSituacionPago().toUpperCase();
            if (situacion.equals("PAGADO") || situacion.equals("DADO") || 
                situacion.equals("GANADO") || situacion.equals("COMODATO")) {
                
                if (entity.getMontoTotal() != null) {
                    entity.setMontoYaPagado(entity.getMontoTotal());
                }
                if (entity.getCantidadCuotas() != null) {
                    entity.setCantidadCuotasPagadas(entity.getCantidadCuotas());
                } else {
                    entity.setCantidadCuotas(0);
                    entity.setCantidadCuotasPagadas(0);
                }
            } else if (situacion.equals("PAGANDO")) {
                java.math.BigDecimal total = entity.getMontoTotal() != null ? entity.getMontoTotal() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal pagado = entity.getMontoYaPagado() != null ? entity.getMontoYaPagado() : java.math.BigDecimal.ZERO;
                Integer cuotasTotal = entity.getCantidadCuotas() != null ? entity.getCantidadCuotas() : 0;
                Integer cuotasPagadas = entity.getCantidadCuotasPagadas() != null ? entity.getCantidadCuotasPagadas() : 0;

                if (total.compareTo(java.math.BigDecimal.ZERO) > 0 && pagado.compareTo(total) >= 0 && cuotasPagadas >= cuotasTotal) {
                    entity.setSituacionPago("PAGADO");
                }
            }
        }
    }
}
