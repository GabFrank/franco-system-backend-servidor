package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class RetiroService extends CrudService<Retiro, RetiroRepository, EmbebedPrimaryKey> {

    private final RetiroRepository repository;

    @Override
    public RetiroRepository getRepository() {
        return repository;
    }

//    public List<Retiro> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public List<Retiro> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return repository.findByAll(texto);
//    }

    public List<Retiro> findByCajaSalidaId(Long id){
        return repository.findByCajaSalidaId(id);
    }

    public List<Retiro> filterRetiros(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId, Pageable pageable){
        return repository.findByAll(id, cajaId, sucId, responsableId, cajeroId, pageable);
    }

    public Page<Retiro> filterRetirosPage(Long id, Long cajaId, Long sucId, Long responsableId, Long cajeroId, Pageable pageable){
        return repository.findByAllPage(id, cajaId, sucId, responsableId, cajeroId, pageable);
    }

    public Retiro findByIdAndSucursalId(Long id, Long sucId){
        return repository.findByIdAndSucursalId(id, sucId);
    }

    @Override
    public Retiro save(Retiro entity) {
        Retiro e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}