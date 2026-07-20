package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Mueble;
import com.franco.dev.repository.activos.MuebleRepository;
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
public class MuebleService extends CrudService<Mueble, MuebleRepository, Long> {

    private final MuebleRepository repository;
    private final EnteService enteService;

    public MuebleService(MuebleRepository repository, @Lazy EnteService enteService) {
        this.repository = repository;
        this.enteService = enteService;
    }

    @Override
    public MuebleRepository getRepository() {
        return repository;
    }

    public List<Mueble> findByAll(String texto) {
        if (texto == null) texto = "";
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase());
    }

    public Page<Mueble> findByAllWithPage(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable);
    }

    public List<Mueble> findByFamiliaId(Long familiaId) {
        return repository.findByFamiliaId(familiaId);
    }

    public List<Mueble> findByTipoMuebleId(Long tipoMuebleId) {
        return repository.findByTipoMuebleId(tipoMuebleId);
    }

    public List<Mueble> findByPropietarioId(Long propietarioId) {
        return repository.findByPropietarioId(propietarioId);
    }
    @Override
    public Mueble save(Mueble entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        normalizeFinancialData(entity);
        Mueble e = super.save(entity);
        if (e != null) {
            String descripcion = e.getDescripcion() != null ? e.getDescripcion() : (e.getIdentificador() != null ? e.getIdentificador() : "Mueble #" + e.getId());
            enteService.ensureEnteForReferencia(TipoEnte.MUEBLE, e.getId(), descripcion, e.getUsuario());
        }
        return e;
    }

    @Override
    public Boolean deleteById(Long id) {
        enteService.findByTipoEnteAndReferenciaId(TipoEnte.MUEBLE, id).ifPresent(ente -> {
            enteService.deleteById(ente.getId());
        });
        return super.deleteById(id);
    }

    private void normalizeFinancialData(Mueble entity) {
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
            } else if (situacion.equals("PAGANDO")
                    && com.franco.dev.service.activos.util.ActivoPagoNormalizer.debeMarcarComoPagado(
                            entity.getSituacionPago(), entity.getMontoTotal(), entity.getMontoYaPagado(),
                            entity.getCantidadCuotas())) {
                entity.setSituacionPago("PAGADO");
                if (entity.getCantidadCuotas() != null && entity.getCantidadCuotas() > 0) {
                    entity.setCantidadCuotasPagadas(entity.getCantidadCuotas());
                }
            }
        }
    }
}
