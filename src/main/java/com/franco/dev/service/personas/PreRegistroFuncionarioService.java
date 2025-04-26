package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.PreRegistroFuncionario;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.repository.personas.PreRegistroFuncionarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PreRegistroFuncionarioService extends CrudService<PreRegistroFuncionario, PreRegistroFuncionarioRepository, Long> {

    private final PreRegistroFuncionarioRepository repository;


    @Override
    public PreRegistroFuncionarioRepository getRepository() {
        return repository;
    }

    public List<PreRegistroFuncionario> findByNombre(String texto) { return repository.findByNombre(texto.toUpperCase());}

    @Override
    public PreRegistroFuncionario save(PreRegistroFuncionario entity) {
        if(entity.getNombreCompleto()!=null) entity.setNombreCompleto(entity.getNombreCompleto().toUpperCase());
        if(entity.getApodo()!=null) entity.setApodo(entity.getApodo().toUpperCase());
        if(entity.getNombreContactoEmergencia()!=null) entity.setNombreContactoEmergencia(entity.getNombreContactoEmergencia().toUpperCase());
        if(entity.getId()==null){
            entity.setCreadoEn(LocalDateTime.now());
            entity.setVerificado(false);
        }
        PreRegistroFuncionario e = repository.save(entity);
        return e;
    }
}
