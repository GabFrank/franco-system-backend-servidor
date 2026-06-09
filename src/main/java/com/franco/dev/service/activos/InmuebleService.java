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
            String descripcion = e.getNombreAsignado() != null ? e.getNombreAsignado() : (e.getDireccion() != null ? e.getDireccion() : "Inmueble #" + e.getId());
            enteService.ensureEnteForReferencia(TipoEnte.INMUEBLE, e.getId(), descripcion, e.getUsuario());
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
