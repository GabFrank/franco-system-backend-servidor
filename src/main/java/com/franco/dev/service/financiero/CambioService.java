package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.repository.financiero.CambioRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CambioService extends CrudService<Cambio, CambioRepository, Long> {

    private final CambioRepository repository;

    @Override
    public CambioRepository getRepository() {
        return repository;
    }

    public Cambio findLastByMonedaId(Long id){
        return repository.findLastByCambioId(id);
    }

    public List<Cambio> findByDate(String start, String end){
        return repository.findByDate(start, end);
    }

    @Override
    public Cambio save(Cambio entity) {
        Cambio e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}