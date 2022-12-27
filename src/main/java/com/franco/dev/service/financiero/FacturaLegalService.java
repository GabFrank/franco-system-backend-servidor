package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Service
@AllArgsConstructor
public class FacturaLegalService extends CrudService<FacturaLegal, FacturaLegalRepository, EmbebedPrimaryKey> {

    private final FacturaLegalRepository repository;

    @Override
    public FacturaLegalRepository getRepository() {
        return repository;
    }

    public List<FacturaLegal> findByCajaId(Long id){
        return repository.findByCajaId(id);
    }

    public List<FacturaLegal> findByAll(String fechaInicio, String fechaFin, List<Long> sucId, String ruc, String nombre, Boolean iva5, Boolean iva10){
        LocalDateTime inicio = toDate(fechaInicio);
        LocalDateTime fin = toDate(fechaFin);
        return repository.findByCreadoEnBetweenAndSucursalId(inicio, fin, sucId, nombre, ruc);
    }

    @Override
    public FacturaLegal save(FacturaLegal entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        FacturaLegal e = super.save(entity);
        return e;
    }
}