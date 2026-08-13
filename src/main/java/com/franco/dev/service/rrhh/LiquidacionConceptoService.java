package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionConcepto;
import com.franco.dev.repository.rrhh.LiquidacionConceptoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@AllArgsConstructor
public class LiquidacionConceptoService extends CrudService<LiquidacionConcepto, LiquidacionConceptoRepository, Long> {

    private final LiquidacionConceptoRepository repository;

    @Override
    public LiquidacionConceptoRepository getRepository() {
        return repository;
    }

    public Optional<LiquidacionConcepto> findByCodigo(String codigo) {
        if (codigo == null) return Optional.empty();
        return repository.findByCodigo(codigo.toUpperCase());
    }

    @Override
    public LiquidacionConcepto save(LiquidacionConcepto entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getEsHaber() == null) entity.setEsHaber(true);
        if (entity.getEsCalculadoAuto() == null) entity.setEsCalculadoAuto(false);
        if (entity.getCodigo() != null) entity.setCodigo(entity.getCodigo().toUpperCase());
        return super.save(entity);
    }
}
