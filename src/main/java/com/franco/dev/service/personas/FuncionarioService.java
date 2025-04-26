package com.franco.dev.service.personas;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.repository.personas.VendedorRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class FuncionarioService extends CrudService<Funcionario, FuncionarioRepository, Long> {

    private final FuncionarioRepository repository;

    @Override
    public FuncionarioRepository getRepository() {
        return repository;
    }

    public Page<Funcionario> findAllWithPage(Long id, String nombre, List<Long> sucursalList, Pageable pageable){
        return repository.findAllWithFilterAndPage(id, nombre, sucursalList, pageable);
    }

    public Funcionario findByPersonaId(Long id){
        return repository.findByPersonaId(id);
    }

    public List<Funcionario> findByPersonaNombre(String texto) { return repository.findByIdOrPersonaNombre(texto.toUpperCase());}

    @Override
    public Funcionario save(Funcionario entity) {
        if(entity.getId()==null){
            entity.setCreadoEn(LocalDateTime.now());
            entity.setActivo(true);
        }
        Funcionario e = repository.save(entity);
        return e;
    }
}
