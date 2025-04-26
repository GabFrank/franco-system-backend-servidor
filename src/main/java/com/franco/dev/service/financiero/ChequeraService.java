package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.repository.financiero.ChequeraRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ChequeraService extends CrudService<Chequera, ChequeraRepository, Long> {
    private final ChequeraRepository repository;

    @Override
    public ChequeraRepository getRepository() {
        return repository;
    }

    @Override
    public Chequera save(Chequera entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    public List<Chequera> findByAll(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto);
    }
} 