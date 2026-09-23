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

    /** Los que se ofrecen en el select de item manual. Ver el repositorio. */
    public java.util.List<LiquidacionConcepto> findParaItemManual() {
        return repository.findByActivoTrueAndEsCalculadoAutoFalseOrderByEsHaberDescDescripcionAsc();
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
        // Default true, igual que la columna: un cliente viejo que no manda el campo no
        // puede reventar el insert. Es la red, no el camino normal: el ABM del desktop
        // (list-liquidacion-concepto) pide el valor explicitamente en el alta, para que
        // nadie entre a la base remunerativa del aguinaldo y del IPS por omision.
        if (entity.getEsRemunerativo() == null) entity.setEsRemunerativo(true);
        if (entity.getCodigo() != null) entity.setCodigo(entity.getCodigo().toUpperCase());
        return super.save(entity);
    }
}
