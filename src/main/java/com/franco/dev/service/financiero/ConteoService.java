package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.repository.financiero.ConteoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ConteoService extends CrudService<Conteo, ConteoRepository, EmbebedPrimaryKey> {

    private final ConteoRepository repository;

    @Override
    public ConteoRepository getRepository() {
        return repository;
    }

//    public List<Conteo> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public List<Conteo> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByAll(texto);
//    }

    public Double getTotalPorMoneda(Long conteoId, Long monedaId, Long sucId) {
        return repository.getTotalPorMoneda(conteoId, monedaId, sucId);
    }

    public Conteo findByIdAndSucursalId(long id, long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    @Override
    public Conteo save(Conteo entity) {
        Conteo e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}