package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.PreRegistroFuncionario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PreRegistroFuncionarioRepository extends HelperRepository<PreRegistroFuncionario, Long> {

    default Class<PreRegistroFuncionario> getEntityClass() {
        return PreRegistroFuncionario.class;
    }

    @Query("select u from PreRegistroFuncionario u " +
            "where UPPER(u.nombreCompleto) like %?1%")
    public List<PreRegistroFuncionario> findByNombre(String texto);

}
