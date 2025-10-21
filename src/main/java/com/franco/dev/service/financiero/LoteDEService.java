package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import lombok.AllArgsConstructor;
import com.franco.dev.service.CrudService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import com.franco.dev.domain.financiero.LoteDE;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.repository.financiero.LoteDERepository;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class LoteDEService extends CrudService<LoteDE, LoteDERepository, EmbebedPrimaryKey> {

    private final LoteDERepository repository;

    @Override
    public LoteDERepository getRepository() {
        return repository;
    }

    public Page<LoteDE> findByEstadoOrFechaProcesadoBetween(
        EstadoLoteDE estado, 
        String fechaInicio, 
        String fechaFin, 
        int page, 
        int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime inicio = fechaInicio != null ? stringToDate(fechaInicio) : null;
        LocalDateTime fin = fechaFin != null ? stringToDate(fechaFin) : null;
        return repository.findByEstadoOrFechaProcesadoBetween(estado, inicio, fin, pageable);
    }

    public List<LoteDE> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAllByOrderByIdAsc(pageable);
    }

    public List<LoteDE> findLotesAntiguosProcesados(LocalDateTime fechaLimite) {
        return repository.findLotesAntiguosProcesados(fechaLimite);
    }

    public List<LoteDE> findByEstado(EstadoLoteDE estado) {
        return repository.findByEstado(estado);
    }

    public Optional<LoteDE> findByProtocolo(String protocolo) {
        return repository.findByProtocolo(protocolo);
    }

    public Optional<LoteDE> findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }
    
}