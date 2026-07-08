package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.Feriado;
import com.franco.dev.repository.rrhh.FeriadoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class FeriadoService extends CrudService<Feriado, FeriadoRepository, Long> {

    private final FeriadoRepository repository;

    @Override
    public FeriadoRepository getRepository() {
        return repository;
    }

    public Optional<Feriado> findByFecha(LocalDate fecha) {
        if (fecha == null) return Optional.empty();
        return repository.findByFecha(fecha);
    }

    public List<Feriado> findByFechaBetween(LocalDate desde, LocalDate hasta) {
        return repository.findByFechaBetweenOrderByFechaAsc(desde, hasta);
    }

    public List<Feriado> findActivos() {
        return repository.findByActivoTrueOrderByFechaAsc();
    }

    public boolean esFeriado(LocalDate fecha) {
        return findByFecha(fecha).map(f -> Boolean.TRUE.equals(f.getActivo())).orElse(false);
    }

    @Override
    public Feriado save(Feriado entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getEsNacional() == null) entity.setEsNacional(true);
        if (entity.getRecargoPorcentaje() == null) entity.setRecargoPorcentaje(new BigDecimal("100"));
        if (entity.getDescripcion() != null) entity.setDescripcion(entity.getDescripcion().toUpperCase());
        return super.save(entity);
    }
}
