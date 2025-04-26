package com.franco.dev.repository.personas;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FuncionarioRepository extends HelperRepository<Funcionario, Long> {

    default Class<Funcionario> getEntityClass() {
        return Funcionario.class;
    }

    public Funcionario findByPersonaId(Long id);

    @Query("select u from Funcionario u " +
            "join u.persona p " +
            "where CAST(u.id as text) like %?1% or UPPER(p.nombre) like %?1%")
    public List<Funcionario> findByIdOrPersonaNombre(String texto);

    @Query("select u from Funcionario u " +
            "join u.persona p " +
            "left join u.sucursal s where " +
            "((:id) is null or u.id = (:id) ) and " +
            "((:nombre) is null or UPPER(p.nombre) like %:nombre%) and " +
            "((:sucursalList) is null or s.id IN (:sucursalList)) " +
            "order by u.id")
    public Page<Funcionario> findAllWithFilterAndPage(
            @Param("id") Long id,
            @Param("nombre") String nombre,
            @Param("sucursalList") List<Long> sucursalList,
            Pageable pageable);
}
