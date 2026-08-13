package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.TipoJustificativo;
import com.franco.dev.domain.rrhh.enums.DescuentoJustificativo;
import com.franco.dev.repository.rrhh.TipoJustificativoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TipoJustificativoService extends CrudService<TipoJustificativo, TipoJustificativoRepository, Long> {

    private final TipoJustificativoRepository repository;

    @Override
    public TipoJustificativoRepository getRepository() {
        return repository;
    }

    public List<TipoJustificativo> findActivos() {
        return repository.findByActivoTrueOrderByNombreAsc();
    }

    /** Busca un tipo por nombre. Lo usan los modulos que generan justificativos (vacaciones). */
    public TipoJustificativo findByNombre(String nombre) {
        return nombre != null ? repository.findFirstByNombreIgnoreCase(nombre) : null;
    }

    public Page<TipoJustificativo> findPage(String nombre, Boolean activo, Pageable pageable) {
        return repository.findPage(nombre, activo, pageable);
    }

    @Override
    public TipoJustificativo save(TipoJustificativo entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getNombre() != null) entity.setNombre(entity.getNombre().toUpperCase());
        if (entity.getActivo() == null) entity.setActivo(true);
        if (entity.getEvitaPenalizacion() == null) entity.setEvitaPenalizacion(true);
        if (entity.getDescuentaSalario() == null) entity.setDescuentaSalario(DescuentoJustificativo.NO);
        if (entity.getRequiereDocumento() == null) entity.setRequiereDocumento(false);
        if (entity.getGeneradoPorSistema() == null) entity.setGeneradoPorSistema(false);
        return super.save(entity);
    }
}
