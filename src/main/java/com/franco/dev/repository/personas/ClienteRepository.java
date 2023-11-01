package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ClienteRepository extends HelperRepository<Cliente, Long> {

    default Class<Cliente> getEntityClass() {
        return Cliente.class;
    }

    Cliente findByPersonaId(Long id);

    @Query("select c from Cliente c " +
            "left join c.persona per " +
            " where CAST(per.id as text) like %?1% or UPPER(per.nombre) like %?1% or UPPER(per.apodo) like %?1% or UPPER(per.documento) like %?1%")
    List<Cliente> findByPersona(String texto);

    @Query("select c from Cliente c " +
            "left join c.persona per " +
            " where " +
            "(:texto is null or (CAST(per.id as text) like %:texto% or " +
            "UPPER(per.nombre) like %:texto% or " +
            "UPPER(per.apodo) like %:texto% or " +
            "UPPER(per.documento) like %:texto%)) and " +
            "(c.tipo = :tipoCliente or cast(:tipoCliente as com.franco.dev.domain.personas.enums.TipoCliente) is null) " +
            "group by per.nombre, c.id " +
            "order by per.nombre asc")
    Page<Cliente> findByAll(String texto, TipoCliente tipoCliente, Pageable page);
}
