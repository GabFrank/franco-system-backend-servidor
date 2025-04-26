package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.repository.financiero.ConteoMonedaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ConteoMonedaService extends CrudService<ConteoMoneda, ConteoMonedaRepository, EmbebedPrimaryKey> {

    private final ConteoMonedaRepository repository;
    @Autowired
    private ConteoService conteoService;

    @Override
    public ConteoMonedaRepository getRepository() {
        return repository;
    }

//    public List<ConteoMoneda> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<ConteoMoneda> findByConteoId(Long id, Long sucId) {
        return repository.findByConteoIdAndSucursalId(id, sucId);
    }

    @Override
    public ConteoMoneda save(ConteoMoneda entity) {
        ConteoMoneda e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}