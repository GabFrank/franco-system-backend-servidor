package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class TimbradoDetalleService extends CrudService<TimbradoDetalle, TimbradoDetalleRepository> {

    private final TimbradoDetalleRepository repository;

    @Override
    public TimbradoDetalleRepository getRepository() {
        return repository;
    }

    public List<TimbradoDetalle> findByTimbradoId(Long id){
        return repository.findByTimbradoId(id);
    }

    @Override
    public TimbradoDetalle save(TimbradoDetalle entity) {
        TimbradoDetalle e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}